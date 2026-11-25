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
        if (d.cloud == null || (d.companions.isEmpty() && Prefs.pendingDeletes().isEmpty())) return
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
            d.companions.forEach { c ->
                val snap = build(ctx, d, c)
                // inclui o destino: se o projeto Firebase mudar, tudo é reenviado
                val hash = ShareCrypto.sha256(snap.copy(generatedAt = 0).toJson() + c.key + c.shareId + cloud.dbUrl + uid)
                if (force || Prefs.companionHash(c.id) != hash) {
                    val body = JSONObject()
                        .put("owner", uid)
                        .put("v", 1)
                        .put("updated", JSONObject().put(".sv", "timestamp"))
                        .put("data", ShareCrypto.seal(snap.toJson(), c.key))
                    fb.put("shares/${c.shareId}", body.toString())
                    Prefs.setCompanionSynced(c.id, hash, System.currentTimeMillis())
                }
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

    private fun thumb(ctx: Context, path: String?, width: Int = 128, quality: Int = 70): String? = runCatching {
        if (path == null) return null
        val f = File(ctx.filesDir, path)
        if (!f.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var ss = 1
        while (max(o.outWidth, o.outHeight) / (ss * 2) >= width * 2) ss *= 2
        val b = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = ss }) ?: return null
        val w = minOf(width, b.width)
        val sc = Bitmap.createScaledBitmap(b, w, max(1, w * b.height / max(1, b.width)), true)
        val out = ByteArrayOutputStream()
        sc.compress(Bitmap.CompressFormat.JPEG, quality, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    fun build(ctx: Context, d: AppData, c: CompanionProfile): Snapshot {
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
                    if (p.avatars) thumb(ctx, m.avatar) else null, false, e.start, if (p.frontStatus) e.status else "",
                )
                m != null -> SnapFront(null, hidden, "", 0xFF78909C, null, false, e.start)
                cf != null && p.customFronts -> SnapFront(null, cf.name, "", cf.color, null, true, e.start, if (p.frontStatus) e.status else "")
                else -> null
            }
        }.distinctBy { it.personId ?: (it.name + it.since) }

        val notices = if (!p.notices) null else d.notices.sortedWith(compareBy<Notice>({ !it.pinned }, { -it.time })).map {
            SnapNotice(it.title, it.text, nameOf(it.authorId) ?: "Sistema", it.time, it.pinned, it.color)
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
                avatar = if (p.avatars) thumb(ctx, m.avatar) else null,
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
                banner = if (p.banner) m.banner?.let { thumb(ctx, it, width = 540, quality = 60) } else null,
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

        return Snapshot(d.systemName.ifBlank { "Sistema" }, c.name, System.currentTimeMillis(), front, notices, events, people, safety, body, history)
    }
}

class CompanionSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        PluraliApplication.ensure(applicationContext)
        return if (CompanionSync.syncNow(applicationContext) == null) Result.success()
        else if (runAttemptCount < 5) Result.retry() else Result.failure()
    }
}
