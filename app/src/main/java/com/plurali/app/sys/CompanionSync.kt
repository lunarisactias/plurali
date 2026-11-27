package com.plurali.app.sys

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.plurali.app.data.*
import com.plurali.shared.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Envia para cada companion só o que o perfil dele permite, criptografado com a chave dele.
 * Caminho no Firebase: shares/<shareId> = { owner, updated, data }.
 */
object CompanionSync {
    private const val WORK = "companion-sync"
    private var client: FirebaseRest? = null
    private var clientKey = ""

    /** Sessão anônima guardada nos dados do sistema (vai junto no backup). */
    private object Tokens : TokenStore {
        override var uid: String?
            get() = Store.data.value.cloud?.uid
            set(v) { Store.update(notify = false) { d -> d.copy(cloud = d.cloud?.copy(uid = v)) } }
        override var refreshToken: String?
            get() = Store.data.value.cloud?.refreshToken
            set(v) { Store.update(notify = false) { d -> d.copy(cloud = d.cloud?.copy(refreshToken = v)) } }
    }

    /** Esquece a sessão em memória (depois de restaurar um backup, que traz outra sessão anônima). */
    fun resetClient() { client = null; clientKey = "" }

    fun client(c: CloudConfig): FirebaseRest {
        val k = c.dbUrl + "|" + c.apiKey
        if (client == null || clientKey != k) { client = FirebaseRest(c.dbUrl, c.apiKey, Tokens); clientKey = k }
        return client!!
    }

    /** Pede um envio (espera 3 s para juntar alterações; precisa de internet; tenta de novo se falhar). */
    fun request(ctx: Context) {
        val d = Store.data.value
        if (d.cloud == null || (d.companions.isEmpty() && Prefs.pendingDeletes().isEmpty() && Prefs.mediaUploaded().isEmpty())) return
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            WORK, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CompanionSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(3, TimeUnit.SECONDS)
                .build(),
        )
    }

    /** Envia agora. Retorna null se deu certo, ou a mensagem de erro. */
    @Synchronized
    fun syncNow(ctx: Context, force: Boolean = false): String? {
        val d = Store.data.value
        val cloud = d.cloud ?: return "Firebase não configurado"
        return try {
            val fb = client(cloud)
            fb.token()
            val uid = fb.uid ?: error("sem sessão")
            Prefs.pendingDeletes().forEach { sid ->
                runCatching { fb.delete("shares/$sid") }.onSuccess { Prefs.removePendingDelete(sid) }
            }
            val dest = ShareCrypto.sha256(cloud.dbUrl + "|" + uid).take(16)
            if (Prefs.mediaDest() != dest) { Prefs.setMediaUploaded(emptySet()); Prefs.setMediaDest(dest) }
            if (d.companions.isNotEmpty()) ensureMediaKey()
            val cur = Store.data.value
            val key = cur.mediaKey

            // 1) monta o pacote de cada companion, juntando as fotos usadas (uma vez só, mesmo que vários usem)
            val media = mutableMapOf<String, ByteArray>()
            val snaps = cur.companions.map { c -> c to build(ctx, cur, c, media) }

            // 2) envia só as fotos novas
            val uploaded = Prefs.mediaUploaded().toMutableSet()
            if (key != null) for ((id, bytes) in media) {
                if (!force && id in uploaded) continue
                fb.put(
                    Media.path(id),
                    JSONObject().put("owner", uid).put("v", 1).put("kind", "media")
                        .put("data", ShareCrypto.sealBytes(bytes, key)).toString(),
                )
                uploaded += id
                Prefs.setMediaUploaded(uploaded)
            }

            // 3) envia os pacotes que mudaram e avisa o companion na hora (se ele tiver avisos instantâneos)
            for ((c, snap) in snaps) {
                // inclui o destino: se o projeto Firebase mudar, tudo é reenviado
                val hash = ShareCrypto.sha256(snap.copy(generatedAt = 0).toJson() + c.key + c.shareId + cloud.dbUrl + uid)
                if (force || Prefs.companionHash(c.id) != hash) {
                    val body = JSONObject()
                        .put("owner", uid)
                        .put("v", 2)
                        .put("updated", JSONObject().put(".sv", "timestamp"))
                        .put("data", ShareCrypto.seal(snap.toJson(), c.key))
                    fb.put("shares/${c.shareId}", body.toString())
                    Prefs.setCompanionSynced(c.id, hash, System.currentTimeMillis())
                    // o canal calculado do convite (conexão própria do companion) e, se for outro, o endereço publicado (app ntfy)
                    val derived = Push.urlFor(c.shareId, c.key)
                    Push.send(derived)
                    CompanionInbox.pushFor(fb, c)?.takeIf { it != derived }?.let { Push.send(it) }
                }
            }

            // 4) apaga as fotos que ninguém usa mais (inclusive as da chave antiga, depois de tirar alguém)
            for (id in uploaded.toList()) {
                if (id in media) continue
                runCatching { fb.delete(Media.path(id)) }.onSuccess { uploaded -= id; Prefs.setMediaUploaded(uploaded) }
            }
            Prefs.putCompanionError(null)
            null
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            Prefs.putCompanionError(msg)
            msg
        }
    }

    /** Testa a configuração: login anônimo + gravar e apagar um item de teste. */
    fun test(cfg: CloudConfig): String? = try {
        val fb = FirebaseRest(cfg.dbUrl, cfg.apiKey, object : TokenStore {
            override var uid: String? = null
            override var refreshToken: String? = null
        })
        fb.token()
        val path = "shares/teste-${fb.uid}"
        fb.put(path, JSONObject().put("owner", fb.uid).put("data", "teste").toString())
        fb.delete(path)
        null
    } catch (e: Exception) {
        e.message ?: e.javaClass.simpleName
    }

    // ---------------- montagem do que o companion vê ----------------

    /** Miniaturas já prontas (arquivo + largura + data de modificação -> JPEG), para não refazer a cada envio. */
    private val thumbCache = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()

    private fun thumbBytes(ctx: Context, path: String, width: Int, quality: Int): ByteArray? = runCatching {
        val f = File(ctx.filesDir, path)
        if (!f.exists()) return null
        val ck = "$path|$width|$quality|${f.lastModified()}|${f.length()}"
        thumbCache[ck]?.let { return it }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var ss = 1
        while (max(o.outWidth, o.outHeight) / (ss * 2) >= width * 2) ss *= 2
        val b = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = ss }) ?: return null
        val w = minOf(width, b.width)
        val sc = Bitmap.createScaledBitmap(b, w, max(1, w * b.height / max(1, b.width)), true)
        val out = ByteArrayOutputStream()
        sc.compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray().also { thumbCache[ck] = it }
    }.getOrNull()

    /**
     * Foto para o pacote: vira uma referência "m:<id>"; o JPEG vai à parte (uma vez por sistema, não por companion).
     * [media] junta os JPEGs usados, para o envio.
     */
    private fun mediaRef(ctx: Context, path: String?, key: String, media: MutableMap<String, ByteArray>?, width: Int = 128, quality: Int = 70): String? {
        if (path == null) return null
        val bytes = thumbBytes(ctx, path, width, quality) ?: return null
        val id = Media.idFor(bytes, key)
        media?.put(id, bytes)
        return Media.ref(id)
    }

    /** Chave das fotos do sistema (cria se ainda não existe ou se foi trocada ao tirar um companion). */
    private fun ensureMediaKey(): String {
        Store.data.value.mediaKey?.let { return it }
        val k = ShareCrypto.newKey()
        Store.update(notify = false) { if (it.mediaKey == null) it.copy(mediaKey = k) else it }
        return Store.data.value.mediaKey ?: k
    }

    /** [media] != null: envio de verdade (junta as fotos). null: só uma prévia na tela (não grava nada). */
    fun build(ctx: Context, d: AppData, c: CompanionProfile, media: MutableMap<String, ByteArray>? = null): Snapshot {
        val mk = d.mediaKey ?: "previa"
        fun thumb(path: String?, width: Int = 128, quality: Int = 70) = mediaRef(ctx, path, mk, media, width, quality)
        val p = c.perms
        val visible: Set<String> = (p.memberIds?.toSet() ?: d.members.filter { !it.archived }.map { it.id }.toSet())
            .filter { id -> d.members.any { it.id == id && !it.archived } }.toSet()
        val hidden = "Alguém do sistema"
        fun nameOf(id: String?): String? = d.members.find { it.id == id && it.id in visible }?.name

        val front = if (!p.front) null else d.currentFronts().mapNotNull { e ->
            val m = d.members.find { it.id == e.subjectId }
            val cf = d.customFronts.find { it.id == e.subjectId }
            when {
                m != null && m.id in visible -> SnapFront(
                    m.id, m.name, if (p.pronouns) m.pronouns else "", m.color,
                    if (p.avatars) thumb(m.avatar) else null, false, e.start, if (p.frontStatus) e.status else "",
                )
                m != null -> SnapFront(null, hidden, "", 0xFF78909C, null, false, e.start)
                cf != null && p.customFronts -> SnapFront(null, cf.name, "", cf.color, null, true, e.start, if (p.frontStatus) e.status else "")
                else -> null
            }
        }.distinctBy { it.personId ?: (it.name + it.since) }

        val notices = if (!p.notices) null else d.notices.sortedWith(compareBy<Notice>({ !it.pinned }, { -it.time })).map {
            SnapNotice(it.title, com.plurali.app.ui.Rich.strip(it.text), nameOf(it.authorId) ?: "Sistema", it.time, it.pinned, it.color)
        }

        val events: List<SnapEvent>? = if (!p.calendar) null else {
            val out = mutableListOf<SnapEvent>()
            for (e in d.events) {
                val names: List<String> = e.memberIds.mapNotNull { id -> nameOf(id) }
                if (e.memberIds.isNotEmpty() && names.isEmpty() && e.roles.isEmpty()) continue // só de pessoas escondidas
                val who: String = (names + e.roles).joinToString(", ").ifBlank { "Para todos" }
                out.add(SnapEvent(e.id, e.title, e.description, e.start, e.end, e.allDay, e.repeat, e.color, who, e.memberIds.filter { it in visible }))
            }
            if (p.birthday) {
                for (e in d.birthdayEvents()) {
                    val mid = e.memberIds.firstOrNull() ?: continue
                    if (mid !in visible) continue
                    out.add(SnapEvent(e.id, e.title, "", e.start, null, true, "yearly", e.color, nameOf(mid) ?: "", listOf(mid)))
                }
            }
            out
        }

        val people = if (!p.people) null else d.members.filter { it.id in visible }.sortedBy { it.name.lowercase() }.map { m ->
            SnapPerson(
                id = m.id, name = m.name,
                pronouns = if (p.pronouns) m.pronouns else "",
                role = if (p.roles) m.role else "",
                color = m.color,
                avatar = if (p.avatars) thumb(m.avatar) else null,
                description = if (p.description) m.description else "",
                comfort = if (p.comfort) m.comfort else "",
                likes = if (p.likes) m.likes else "",
                dislikes = if (p.likes) m.dislikes else "",
                birthday = if (p.birthday) m.birthday else "",
                fields = d.fields.filter { it.id in p.fieldIds && it.visibility != "private" }
                    .mapNotNull { f -> m.fields[f.id]?.takeIf { it.isNotBlank() }?.let { SnapField(f.name, it) } },
                intro = if (p.intro) m.intro else "",
                wishlist = if (!p.wishlist) null else m.wishlist.sortedWith(compareBy({ it.done }, { -it.createdAt }))
                    .map { SnapWish(it.kind, it.text, it.note, it.done) },
                banner = if (p.banner) m.banner?.let { thumb(it, width = 540, quality = 60) } else null,
                groups = if (!p.groups) emptyList() else d.groups.filter { m.id in it.memberIds }
                    .map { (it.emoji + " " + it.name).trim() }.sorted(),
                relations = if (!p.relations) emptyList() else d.relations.filter { it.a == m.id || it.b == m.id }.mapNotNull { r ->
                    val other = if (r.a == m.id) r.b else r.a
                    val on = nameOf(other) ?: return@mapNotNull null // a outra pessoa precisa estar liberada
                    val an = if (r.a == m.id) m.name else on
                    val bn = if (r.a == m.id) on else m.name
                    SnapRelation(other, an + " " + r.type.replaceFirstChar { it.lowercase() } + " " + bn, r.note)
                },
            )
        }

        val history = if (!p.history) null else {
            val since = System.currentTimeMillis() - 14L * 86_400_000
            d.fronts.filter { (it.end ?: Long.MAX_VALUE) > since }.sortedByDescending { it.start }.mapNotNull { e ->
                val m = d.members.find { it.id == e.subjectId }
                val cf = d.customFronts.find { it.id == e.subjectId }
                when {
                    m != null && m.id in visible -> SnapHistory(m.name, m.color, e.start, e.end, m.id)
                    m != null -> SnapHistory(hidden, 0xFF78909C, e.start, e.end)
                    cf != null && p.customFronts -> SnapHistory(cf.name, cf.color, e.start, e.end)
                    else -> null
                }
            }
        }

        val safety = if (!p.safety) null else d.safety.let { s ->
            SnapSafety(s.info, s.grounding, s.steps, s.contacts.map { SnapContact(it.name, it.phone, it.note) })
        }
        val body = if (!p.body) null else d.body.filter { it.value.isNotBlank() }.map { SnapBody(it.section, it.label, it.value) }

        // o companion segue o visual de quem está no front (se a pessoa escolheu um)
        val look = d.currentFronts().firstNotNullOfOrNull { d.appearance[it.subjectId] }?.copy(wallpaper = null)
        return Snapshot(
            d.systemName.ifBlank { "Sistema" }, c.name, System.currentTimeMillis(), front, notices, events, people, safety, body, history,
            canMessage = p.messages, appearance = look?.let { AppearanceJson.encode(it).toString() },
            mediaKey = d.mediaKey, push = Push.endpoint(ctx),
        )
    }
}

class CompanionSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        PluraliApplication.ensure(applicationContext)
        return if (CompanionSync.syncNow(applicationContext) == null) Result.success()
        else if (runAttemptCount < 5) Result.retry() else Result.failure()
    }
}

/**
 * Busca os recados que os companions mandaram e o endereço de aviso (ntfy) de cada um.
 * Com avisos instantâneos ligados no Plurali, o companion avisa na hora e esta conferência periódica
 * vira só uma garantia (a cada 12 h); sem eles, confere a cada 1 h. Também roda ao abrir a tela de companions.
 */
object CompanionInbox {
    private const val WORK = "companion-inbox"
    private const val NOW = "companion-inbox-now"

    /** Quem precisa ser conferido: quem pode mandar recado, ou quem ainda não contou o endereço de aviso. */
    private fun targets(d: AppData) = d.companions.filter { it.perms.messages || Prefs.companionPush(it.id) == null }

    fun schedule(ctx: Context, replace: Boolean = false) {
        val wm = WorkManager.getInstance(ctx)
        if (targets(Store.data.value).isEmpty()) { wm.cancelUniqueWork(WORK); return }
        val hours = if (Push.active(ctx)) 12L else 1L
        wm.enqueueUniquePeriodicWork(
            WORK, if (replace) androidx.work.ExistingPeriodicWorkPolicy.UPDATE else androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            androidx.work.PeriodicWorkRequestBuilder<CompanionInboxWorker>(hours, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    /**
     * Endereço de aviso (ntfy) do companion, para avisar na hora que algo mudou.
     * Usa o que já se sabe, mas confere de novo no Firebase de tempos em tempos (no máximo a cada 2 min se ainda
     * não se sabe, a cada 10 min se já se sabe), sem esperar a conferência de recados: assim o aviso funciona
     * logo depois que o companion liga os avisos instantâneos (ou troca de endereço, ao reinstalar ou mudar o jeito
     * de receber), mesmo com eles desligados no Plurali.
     */
    fun pushFor(fb: FirebaseRest, c: CompanionProfile): String? {
        val known = Prefs.companionPush(c.id)
        val wait = if (known == null) 2 * 60_000L else 10 * 60_000L
        val now = System.currentTimeMillis()
        if (now - Prefs.companionPushChecked(c.id) < wait) return known
        Prefs.setCompanionPushChecked(c.id, now)
        val found = newestPush(c) { slot -> runCatching { fb.get(Inbox.path(c.shareId, c.key, slot)) }.getOrNull() }
        if (found != known) Prefs.setCompanionPush(c.id, found)
        android.util.Log.i("PluraliPush", "endereço de aviso do companion: " + (if (found == null) "não publicado" else if (found == known) "o mesmo" else "novo"))
        return found
    }

    /**
     * O endereço mais recente entre as vagas do companion (se ele reinstalou o app, pode haver uma vaga antiga
     * com um endereço que não funciona mais).
     */
    fun newestPush(c: CompanionProfile, read: (Int) -> String?): String? {
        var best: String? = null
        var bestAt = Long.MIN_VALUE
        for (slot in 0 until Inbox.SLOTS) {
            val raw = read(slot) ?: break
            val o = runCatching { JSONObject(raw) }.getOrNull() ?: continue
            val p = o.optString("push").takeIf { it.isNotBlank() }?.let { ShareCrypto.open(it, c.key) } ?: continue
            val at = o.optLong("updated", 0L)
            if (at >= bestAt) { best = p; bestAt = at }
        }
        return best
    }

    /** Um companion avisou (ntfy): confere agora. */
    fun now(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            NOW, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CompanionInboxWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build(),
        )
    }

    /** Retorna quantos recados novos chegaram. */
    @Synchronized
    fun fetch(ctx: Context): Int {
        val d = Store.data.value
        val cloud = d.cloud ?: return 0
        val fb = CompanionSync.client(cloud)
        val known = d.companionMessages.map { it.id }.toSet()
        val fresh = mutableListOf<CompanionMessage>()
        var newPush = false
        for (c in targets(d)) {
            val raws = mutableListOf<String>()
            for (slot in 0 until Inbox.SLOTS) {
                val raw = runCatching { fb.get(Inbox.path(c.shareId, c.key, slot)) }.getOrNull() ?: break
                raws += raw
                val o = JSONObject(raw)
                if (!c.perms.messages) continue
                val plain = ShareCrypto.open(o.optString("data"), c.key) ?: continue
                Inbox.decode(plain).forEach { m ->
                    val id = c.id + ":" + m.id
                    if (id !in known) fresh += CompanionMessage(id, c.id, m.text, m.time)
                }
            }
            val push = CompanionInbox.newestPush(c) { raws.getOrNull(it) }
            Prefs.setCompanionPushChecked(c.id, System.currentTimeMillis())
            if (push != null && push != Prefs.companionPush(c.id)) {
                Prefs.setCompanionPush(c.id, push)
                newPush = true
            }
        }
        if (newPush) schedule(ctx, replace = true) // talvez nem precise mais conferir alguns
        if (fresh.isNotEmpty()) {
            Store.addCompanionMessages(fresh)
            fresh.groupBy { it.companionId }.forEach { (cid, l) ->
                val name = d.companions.find { it.id == cid }?.name ?: "Companion"
                Notifier.post(ctx, ("cmsg:$cid").hashCode(), Notifier.CH_MSG,
                    if (l.size == 1) "Recado de $name" else "${l.size} recados de $name", l.last().text, "companions")
            }
        }
        return fresh.size
    }
}

class CompanionInboxWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        PluraliApplication.ensure(applicationContext)
        runCatching { CompanionInbox.fetch(applicationContext) }
        return Result.success()
    }
}
