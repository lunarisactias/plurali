package com.plurali.companion

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.plurali.shared.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Um sistema que esta pessoa acompanha (pode acompanhar mais de um). */
data class Followed(
    val invite: Invite,
    val snapshot: Snapshot? = null,
    val lastFetch: Long = 0L,
    val error: String? = null,
    val revoked: Boolean = false,
    val frontKey: String? = null,
    val updated: Long = 0L, // carimbo do servidor da última versão baixada
) {
    val id get() = invite.shareId
}

object Follows {
    private lateinit var sp: SharedPreferences
    private var appCtx: Context? = null
    private val _list = MutableStateFlow<List<Followed>>(emptyList())
    val list: StateFlow<List<Followed>> get() = _list

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.getSharedPreferences("companion", Context.MODE_PRIVATE)
        appCtx = ctx.applicationContext
        _list.value = runCatching {
            val a = JSONArray(sp.getString("follows", "[]"))
            (0 until a.length()).map { a.getJSONObject(it) }.mapNotNull { o ->
                val inv = Invite.decode(o.getString("invite")) ?: return@mapNotNull null
                Followed(
                    inv,
                    o.optString("snapshot").takeIf { it.isNotBlank() }?.let { runCatching { Snapshot.fromJson(it) }.getOrNull() },
                    o.optLong("lastFetch"),
                    o.optString("error").takeIf { it.isNotBlank() },
                    o.optBoolean("revoked"),
                    o.optString("frontKey").takeIf { o.has("frontKey") },
                    o.optLong("updated"),
                )
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    private fun save(l: List<Followed>) {
        _list.value = l
        val a = JSONArray()
        l.forEach { f ->
            a.put(JSONObject().put("invite", f.invite.encode()).put("snapshot", f.snapshot?.toJson() ?: "")
                .put("lastFetch", f.lastFetch).put("error", f.error ?: "").put("revoked", f.revoked).put("updated", f.updated)
                .apply { if (f.frontKey != null) put("frontKey", f.frontKey) })
        }
        sp.edit().putString("follows", a.toString()).apply()
    }

    fun add(inv: Invite) { save(_list.value.filterNot { it.id == inv.shareId } + Followed(inv)); appCtx?.let { Push.restartOwn(it) } }
    fun remove(id: String) { save(_list.value.filterNot { it.id == id }); appCtx?.let { Push.restartOwn(it) } }
    @Synchronized
    fun replace(f: Followed) = save(_list.value.map { if (it.id == f.id) f else it })

    /** Sessão anônima por projeto Firebase. */
    fun tokens(inv: Invite): TokenStore {
        val k = (inv.dbUrl + inv.apiKey).hashCode().toString()
        return object : TokenStore {
            override var uid: String?
                get() = sp.getString("uid_$k", null)
                set(v) { sp.edit().putString("uid_$k", v).apply() }
            override var refreshToken: String?
                get() = sp.getString("rt_$k", null)
                set(v) { sp.edit().putString("rt_$k", v).apply() }
        }
    }

    var selected: String?
        get() = sp.getString("selected", null)
        set(v) { sp.edit().putString("selected", v).apply() }
}

/** Busca os dados de todos os sistemas e avisa se o front mudou. */
object Poller {
    private val clients = mutableMapOf<String, FirebaseRest>()

    @Synchronized
    fun fetchAll(ctx: Context, notify: Boolean) {
        Follows.list.value.forEach { f -> fetch(ctx, f, notify) }
    }

    fun fetch(ctx: Context, f: Followed, notify: Boolean): Followed {
        val inv = f.invite
        val fb = clients.getOrPut(inv.dbUrl + inv.apiKey) { FirebaseRest(inv.dbUrl, inv.apiKey, Follows.tokens(inv)) }
        val now = System.currentTimeMillis()
        val nf = try {
            // primeiro só o carimbo (poucos bytes); baixa tudo só se mudou
            val stamp = fb.get("shares/${inv.shareId}/updated")?.trim()?.toLongOrNull()
            val raw = if (stamp != null && stamp == f.updated && f.snapshot != null && !f.revoked) "" else fb.get("shares/${inv.shareId}")
            if (raw == "") f.copy(lastFetch = now, error = null)
            else if (raw == null) f.copy(revoked = true, error = null, lastFetch = now)
            else {
                val data = JSONObject(raw).optString("data")
                val plain = ShareCrypto.open(data, inv.key) ?: error("Não consegui abrir os dados (convite desatualizado?)")
                val snap = Snapshot.fromJson(plain)
                fetchMedia(fb, snap)
                val key = snap.frontKey()
                if (notify && snap.front != null && f.frontKey != null && f.frontKey != key) notifyFront(ctx, f.id, snap)
                f.copy(snapshot = snap, frontKey = key, lastFetch = now, error = null, revoked = false, updated = JSONObject(raw).optLong("updated"))
            }
        } catch (e: Exception) {
            f.copy(error = e.message ?: e.javaClass.simpleName, lastFetch = now)
        }
        Follows.replace(nf)
        // conta ao sistema o endereço de aviso deste celular (uma vez, ou quando mudar)
        if (nf.snapshot != null && !nf.revoked) runCatching { publishPush(ctx, nf) }
        return nf
    }

    /** Baixa só as fotos que ainda não estão guardadas (cada foto é baixada uma vez). */
    private fun fetchMedia(fb: FirebaseRest, s: Snapshot) {
        val key = s.mediaKey ?: return
        for (id in s.mediaIds()) {
            if (MediaCache.has(id)) continue
            runCatching {
                val raw = fb.get(Media.path(id)) ?: return@runCatching
                val bytes = ShareCrypto.openBytes(JSONObject(raw).optString("data"), key) ?: return@runCatching
                MediaCache.put(id, bytes)
            }
        }
    }

    /** Apaga do celular as fotos que nenhum sistema acompanhado usa mais. */
    fun cleanMedia() {
        val used = Follows.list.value.flatMap { it.snapshot?.mediaIds().orEmpty() }.toSet()
        MediaCache.keepOnly(used)
    }

    // ---------------- avisos instantâneos (ntfy) ----------------

    private fun client(inv: Invite) = clients.getOrPut(inv.dbUrl + inv.apiKey) { FirebaseRest(inv.dbUrl, inv.apiKey, Follows.tokens(inv)) }

    /**
     * A vaga deste companion para falar com o sistema (shares/in-…-n): guarda os recados e o endereço de aviso,
     * criptografados com a chave do convite. Retorna (caminho, conteúdo atual) ou null se não houver vaga.
     */
    private fun mySlot(fb: FirebaseRest, inv: Invite): Pair<String, JSONObject?>? {
        fb.token()
        val uid = fb.uid ?: error("sem sessão")
        for (slot in 0 until Inbox.SLOTS) {
            val path = Inbox.path(inv.shareId, inv.key, slot)
            val raw = fb.get(path)
            if (raw != null && JSONObject(raw).optString("owner") != uid) continue // vaga de outra instalação
            return path to raw?.let { JSONObject(it) }
        }
        return null
    }

    private fun writeSlot(fb: FirebaseRest, inv: Invite, path: String, msgs: List<Inbox.Msg>, push: String?) {
        fb.put(
            path,
            JSONObject().put("owner", fb.uid).put("v", 1).put("kind", "inbox")
                .put("updated", JSONObject().put(".sv", "timestamp"))
                .put("data", ShareCrypto.seal(Inbox.encode(msgs), inv.key))
                .apply { if (push != null) put("push", ShareCrypto.seal(push, inv.key)) }.toString(),
        )
    }

    private fun publishedKey(f: Followed) = "pushSent_" + f.id

    /**
     * O endereço que este celular publica para um sistema: na conexão própria, o canal calculado do convite
     * (o Plurali já sabe qual é); pelo app ntfy, o endereço que o app ntfy criou.
     */
    private fun myAddress(ctx: Context, f: Followed): String? =
        if (Push.mode(ctx) == Push.OWN) Push.urlFor(f.invite.shareId, f.invite.key) else Push.endpoint(ctx)

    /** Envia o endereço de aviso deste celular para o sistema (se mudou desde a última vez). */
    @Synchronized
    fun publishPush(ctx: Context, f: Followed, force: Boolean = false) {
        val mine = myAddress(ctx, f) ?: ""
        val sp = ctx.getSharedPreferences("companion", Context.MODE_PRIVATE)
        if (!force && sp.getString(publishedKey(f), null) == mine) return
        val fb = client(f.invite)
        val (path, cur) = mySlot(fb, f.invite) ?: return
        val msgs = cur?.let { ShareCrypto.open(it.optString("data"), f.invite.key) }?.let { Inbox.decode(it) }.orEmpty()
        if (cur == null && mine.isEmpty()) { sp.edit().putString(publishedKey(f), mine).apply(); return }
        writeSlot(fb, f.invite, path, msgs, mine.ifEmpty { null })
        android.util.Log.i("PluraliPush", "endereço de aviso publicado")
        sp.edit().putString(publishedKey(f), mine).apply()
        Push.send(f.snapshot?.push) // o Plurali confere na hora e já passa a avisar este celular
    }

    /** O endereço deste celular mudou: conta para todos os sistemas e ajusta a frequência de conferência. */
    fun pushChanged(ctx: Context) {
        schedule(ctx, replace = true)
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "publish-push", androidx.work.ExistingWorkPolicy.REPLACE,
            androidx.work.OneTimeWorkRequestBuilder<PublishWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build(),
        )
    }

    /** Chegou um aviso: busca agora. */
    fun now(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "poll-now", androidx.work.ExistingWorkPolicy.REPLACE,
            androidx.work.OneTimeWorkRequestBuilder<PollWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build(),
        )
    }

    private fun notifyFront(ctx: Context, id: String, s: Snapshot) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val names = s.front.orEmpty().joinToString(", ") { it.name }
        val pi = PendingIntent.getActivity(
            ctx, id.hashCode(),
            Intent(ctx, MainActivity::class.java).putExtra("system", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CH_FRONT)
            .setSmallIcon(R.drawable.ic_stat_companion)
            .setContentTitle("${s.systemName}: o front mudou")
            .setContentText(if (names.isBlank()) "Ninguém registrado no front agora" else "Agora: $names")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id.hashCode(), n) }
    }

    /** Manda um recado para o sistema (fica num caminho do próprio companion, criptografado). null = deu certo. */
    @Synchronized
    fun sendMessage(ctx: Context, f: Followed, text: String): String? = try {
        val inv = f.invite
        val fb = client(inv)
        val slot = mySlot(fb, inv)
        if (slot == null) "Não há espaço para recados. Peça um convite novo ao sistema."
        else {
            val (path, cur) = slot
            val old = cur?.let { ShareCrypto.open(it.optString("data"), inv.key) }?.let { Inbox.decode(it) }.orEmpty()
            val list = (old + Inbox.Msg(java.util.UUID.randomUUID().toString(), text, System.currentTimeMillis())).takeLast(50)
            val mine = myAddress(ctx, f)
            writeSlot(fb, inv, path, list, mine)
            ctx.getSharedPreferences("companion", Context.MODE_PRIVATE).edit().putString(publishedKey(f), mine ?: "").apply()
            Push.send(f.snapshot?.push) // o recado chega na hora se o Plurali tiver avisos instantâneos
            null
        }
    } catch (e: Exception) {
        e.message ?: e.javaClass.simpleName
    }

    const val CH_FRONT = "front"

    /** Com avisos instantâneos, a conferência periódica vira só uma garantia (a cada 6 h); sem eles, a cada 15 min. */
    fun schedule(ctx: Context, replace: Boolean = false) {
        val minutes = if (Push.active(ctx)) 6 * 60L else 15L
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "poll", if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PollWorker>(minutes, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }
}

class PollWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        CompanionApp.ensure(applicationContext)
        Poller.fetchAll(applicationContext, notify = true)
        Poller.cleanMedia()
        return Result.success()
    }
}

class PublishWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        CompanionApp.ensure(applicationContext)
        var ok = true
        Follows.list.value.filter { it.snapshot != null && !it.revoked }.forEach { f ->
            runCatching { Poller.publishPush(applicationContext, f) }.onFailure { ok = false }
        }
        return if (ok || runAttemptCount >= 5) Result.success() else Result.retry()
    }
}

/** Fotos dos sistemas acompanhados, guardadas no celular (baixadas uma vez só). */
object MediaCache {
    private lateinit var dir: java.io.File
    private val mem = android.util.LruCache<String, android.graphics.Bitmap>(64)
    /** Muda quando chega uma foto nova (para a tela redesenhar). */
    var version by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    fun init(ctx: Context) { if (!::dir.isInitialized) dir = java.io.File(ctx.filesDir, "media").apply { mkdirs() } }
    private fun file(id: String) = java.io.File(dir, "$id.jpg")
    fun has(id: String) = file(id).exists()
    fun put(id: String, bytes: ByteArray) {
        val tmp = java.io.File(dir, "$id.tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(file(id))
        version++
    }
    fun keepOnly(ids: Set<String>) {
        dir.listFiles()?.forEach { f -> if (f.name.removeSuffix(".jpg") !in ids) f.delete() }
    }

    /** Foto de um pacote: "m:<id>" (guardada à parte) ou base64 (Plurali antigo). */
    fun bitmap(ref: String?): android.graphics.Bitmap? {
        if (ref == null) return null
        Media.idOf(ref)?.let { id ->
            mem.get(id)?.let { return it }
            return runCatching { android.graphics.BitmapFactory.decodeFile(file(id).path) }.getOrNull()?.also { mem.put(id, it) }
        }
        return runCatching {
            val b = android.util.Base64.decode(ref, android.util.Base64.NO_WRAP)
            android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)
        }.getOrNull()
    }
}

class CompanionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ensure(this)
        Poller.schedule(this)
    }

    companion object {
        fun ensure(ctx: Context) {
            Follows.init(ctx.applicationContext)
            MediaCache.init(ctx.applicationContext)
            Push.onPing = { c -> Poller.now(c) }
            Push.onEndpoint = { c -> Poller.pushChanged(c) }
            Push.notifIcon = R.drawable.ic_stat_companion
            Push.openApp = { c -> Intent(c, MainActivity::class.java) }
            Push.extraTopics = { _ -> Follows.list.value.map { Push.topicFor(it.invite.shareId, it.invite.key) } }
            if (Build.VERSION.SDK_INT >= 26) {
                ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
                    NotificationChannel(Poller.CH_FRONT, "Mudanças de front", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Avisa quando o front de um sistema que você acompanha muda"
                    }
                )
            }
        }
    }
}
