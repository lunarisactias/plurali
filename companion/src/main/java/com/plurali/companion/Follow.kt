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
    private val _list = MutableStateFlow<List<Followed>>(emptyList())
    val list: StateFlow<List<Followed>> get() = _list

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.getSharedPreferences("companion", Context.MODE_PRIVATE)
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

    fun add(inv: Invite) = save(_list.value.filterNot { it.id == inv.shareId } + Followed(inv))
    fun remove(id: String) = save(_list.value.filterNot { it.id == id })
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
                val key = snap.frontKey()
                if (notify && snap.front != null && f.frontKey != null && f.frontKey != key) notifyFront(ctx, f.id, snap)
                f.copy(snapshot = snap, frontKey = key, lastFetch = now, error = null, revoked = false, updated = JSONObject(raw).optLong("updated"))
            }
        } catch (e: Exception) {
            f.copy(error = e.message ?: e.javaClass.simpleName, lastFetch = now)
        }
        Follows.replace(nf)
        return nf
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
    fun sendMessage(f: Followed, text: String): String? = try {
        val inv = f.invite
        val fb = clients.getOrPut(inv.dbUrl + inv.apiKey) { FirebaseRest(inv.dbUrl, inv.apiKey, Follows.tokens(inv)) }
        fb.token()
        val uid = fb.uid ?: error("sem sessão")
        var sent = false
        for (slot in 0 until Inbox.SLOTS) {
            val path = Inbox.path(inv.shareId, inv.key, slot)
            val raw = fb.get(path)
            if (raw != null && JSONObject(raw).optString("owner") != uid) continue // vaga de outra instalação
            val old = raw?.let { ShareCrypto.open(JSONObject(it).optString("data"), inv.key) }?.let { Inbox.decode(it) }.orEmpty()
            val list = (old + Inbox.Msg(java.util.UUID.randomUUID().toString(), text, System.currentTimeMillis())).takeLast(50)
            fb.put(
                path,
                JSONObject().put("owner", uid).put("v", 1).put("kind", "inbox")
                    .put("updated", JSONObject().put(".sv", "timestamp"))
                    .put("data", ShareCrypto.seal(Inbox.encode(list), inv.key)).toString(),
            )
            sent = true
            break
        }
        if (sent) null else "Não há espaço para recados. Peça um convite novo ao sistema."
    } catch (e: Exception) {
        e.message ?: e.javaClass.simpleName
    }

    const val CH_FRONT = "front"

    fun schedule(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "poll", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PollWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }
}

class PollWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        CompanionApp.ensure(applicationContext)
        Poller.fetchAll(applicationContext, notify = true)
        return Result.success()
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
