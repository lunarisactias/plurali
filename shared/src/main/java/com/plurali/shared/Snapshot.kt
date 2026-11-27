package com.plurali.shared

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/*
 * O que o companion recebe. Cada lista é null quando o sistema NÃO compartilhou aquela parte.
 */

data class SnapField(val name: String, val value: String)

/** Item da wishlist: kind "buy" (quer comprar) ou "do" (quer fazer). */
data class SnapWish(val kind: String, val text: String, val note: String, val done: Boolean)

data class SnapPerson(
    val id: String,
    val name: String,
    val pronouns: String = "",
    val role: String = "",
    val color: Long = 0xFF7E57C2,
    val avatar: String? = null, // "m:<id>" (foto guardada à parte) ou, em versões antigas, jpeg em base64
    val description: String = "",
    val comfort: String = "",
    val likes: String = "",
    val dislikes: String = "",
    val birthday: String = "",
    val fields: List<SnapField> = emptyList(),
    val wishlist: List<SnapWish>? = null, // null = não compartilhada
    val intro: String = "", // apresentação escrita para pessoas de fora
    val banner: String? = null, // capa: "m:<id>" ou jpeg em base64
    val groups: List<String> = emptyList(),
    val relations: List<SnapRelation> = emptyList(),
    val frame: String? = null, // moldura do perfil
)

/** Relação entre membros, já escrita como frase ("Ana protege Bruno"). otherId = a outra pessoa, se estiver visível. */
data class SnapRelation(val otherId: String?, val text: String, val note: String)

data class SnapFront(
    val personId: String?,
    val name: String,
    val pronouns: String,
    val color: Long,
    val avatar: String?,
    val custom: Boolean,
    val since: Long,
    val status: String = "",
    val frame: String? = null,
)

data class SnapNotice(val title: String, val text: String, val author: String, val time: Long, val pinned: Boolean, val color: Long)

data class SnapEvent(
    val id: String,
    val title: String,
    val description: String,
    val start: Long,
    val end: Long?,
    val allDay: Boolean,
    val repeat: String,
    val color: Long,
    val who: String,
    val personIds: List<String> = emptyList(),
) {
    fun occursOn(day: LocalDate): Boolean {
        val zone = ZoneId.systemDefault()
        val sd = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        if (day < sd) return false
        return when (repeat) {
            "weekly" -> ChronoUnit.DAYS.between(sd, day) % 7 == 0L
            "monthly" -> day.dayOfMonth == sd.dayOfMonth
            "yearly" -> day.month == sd.month && day.dayOfMonth == sd.dayOfMonth
            else -> day <= (end?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }?.takeIf { it >= sd } ?: sd)
        }
    }
}

data class SnapContact(val name: String, val phone: String, val note: String)
data class SnapSafety(val info: String, val grounding: String, val steps: String, val contacts: List<SnapContact>)
data class SnapBody(val section: String, val label: String, val value: String)
data class SnapHistory(val name: String, val color: Long, val start: Long, val end: Long?, val personId: String? = null)

data class Snapshot(
    val systemName: String,
    val profileName: String,
    val generatedAt: Long,
    val front: List<SnapFront>? = null,
    val notices: List<SnapNotice>? = null,
    val events: List<SnapEvent>? = null,
    val people: List<SnapPerson>? = null,
    val safety: SnapSafety? = null,
    val body: List<SnapBody>? = null,
    val history: List<SnapHistory>? = null,
    val canMessage: Boolean = false,   // o companion pode mandar recados para o sistema
    val appearance: String? = null,    // tema de quem está no front (JSON da aparência), para o companion seguir
    val mediaKey: String? = null,      // chave das fotos (guardadas uma vez por sistema em shares/m-<id>)
    val push: String? = null,          // endereço ntfy do Plurali: o companion avisa por ele quando manda recado
) {
    /** Todas as fotos (referências "m:<id>") que este pacote usa. */
    fun mediaIds(): Set<String> = buildSet {
        front?.forEach { Media.idOf(it.avatar)?.let(::add) }
        people?.forEach { p -> Media.idOf(p.avatar)?.let(::add); Media.idOf(p.banner)?.let(::add) }
    }

    /** Chave usada para perceber que o front mudou. */
    fun frontKey(): String = front?.map { it.name }?.sorted()?.joinToString("|") ?: ""

    fun toJson(): String {
        val o = JSONObject().put("v", 2).put("systemName", systemName).put("profileName", profileName).put("generatedAt", generatedAt)
        front?.let { l ->
            o.put("front", JSONArray(l.map { f ->
                JSONObject().put("personId", f.personId ?: JSONObject.NULL).put("name", f.name).put("pronouns", f.pronouns)
                    .put("color", f.color).put("avatar", f.avatar ?: JSONObject.NULL).put("custom", f.custom)
                    .put("since", f.since).put("status", f.status).put("frame", f.frame ?: JSONObject.NULL)
            }))
        }
        notices?.let { l ->
            o.put("notices", JSONArray(l.map { n ->
                JSONObject().put("title", n.title).put("text", n.text).put("author", n.author).put("time", n.time)
                    .put("pinned", n.pinned).put("color", n.color)
            }))
        }
        events?.let { l ->
            o.put("events", JSONArray(l.map { e ->
                JSONObject().put("id", e.id).put("title", e.title).put("description", e.description).put("start", e.start)
                    .put("end", e.end ?: JSONObject.NULL).put("allDay", e.allDay).put("repeat", e.repeat)
                    .put("color", e.color).put("who", e.who).put("personIds", JSONArray(e.personIds))
            }))
        }
        people?.let { l ->
            o.put("people", JSONArray(l.map { p ->
                JSONObject().put("id", p.id).put("name", p.name).put("pronouns", p.pronouns).put("role", p.role)
                    .put("color", p.color).put("avatar", p.avatar ?: JSONObject.NULL).put("description", p.description)
                    .put("comfort", p.comfort).put("likes", p.likes).put("dislikes", p.dislikes).put("birthday", p.birthday)
                    .put("intro", p.intro).put("banner", p.banner ?: JSONObject.NULL).put("groups", JSONArray(p.groups))
                    .put("relations", JSONArray(p.relations.map { r -> JSONObject().put("otherId", r.otherId ?: JSONObject.NULL).put("text", r.text).put("note", r.note) }))
                    .put("fields", JSONArray(p.fields.map { JSONObject().put("name", it.name).put("value", it.value) }))
                    .put("frame", p.frame ?: JSONObject.NULL)
                    .apply {
                        p.wishlist?.let { w ->
                            put("wishlist", JSONArray(w.map {
                                JSONObject().put("kind", it.kind).put("text", it.text).put("note", it.note).put("done", it.done)
                            }))
                        }
                    }
            }))
        }
        safety?.let { s ->
            o.put("safety", JSONObject().put("info", s.info).put("grounding", s.grounding).put("steps", s.steps)
                .put("contacts", JSONArray(s.contacts.map { JSONObject().put("name", it.name).put("phone", it.phone).put("note", it.note) })))
        }
        body?.let { l ->
            o.put("body", JSONArray(l.map { JSONObject().put("section", it.section).put("label", it.label).put("value", it.value) }))
        }
        o.put("canMessage", canMessage)
        appearance?.let { o.put("appearance", it) }
        mediaKey?.let { o.put("mediaKey", it) }
        push?.let { o.put("push", it) }
        history?.let { l ->
            o.put("history", JSONArray(l.map { h ->
                JSONObject().put("name", h.name).put("color", h.color).put("start", h.start).put("end", h.end ?: JSONObject.NULL).put("personId", h.personId ?: JSONObject.NULL)
            }))
        }
        return o.toString()
    }

    companion object {
        private fun JSONObject.s(k: String) = if (has(k) && !isNull(k)) getString(k) else ""
        private fun JSONObject.sN(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null
        private fun JSONObject.lN(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null
        private fun JSONObject.strs(k: String): List<String> = optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        private fun <T> JSONObject.list(k: String, f: (JSONObject) -> T): List<T>? {
            val a = optJSONArray(k) ?: return null
            return (0 until a.length()).map { f(a.getJSONObject(it)) }
        }

        fun fromJson(text: String): Snapshot {
            val o = JSONObject(text)
            return Snapshot(
                systemName = o.s("systemName"),
                profileName = o.s("profileName"),
                generatedAt = o.optLong("generatedAt"),
                front = o.list("front") {
                    SnapFront(it.sN("personId"), it.s("name"), it.s("pronouns"), it.optLong("color"), it.sN("avatar"),
                        it.optBoolean("custom"), it.optLong("since"), it.s("status"), it.sN("frame"))
                },
                notices = o.list("notices") {
                    SnapNotice(it.s("title"), it.s("text"), it.s("author"), it.optLong("time"), it.optBoolean("pinned"), it.optLong("color"))
                },
                events = o.list("events") {
                    SnapEvent(it.s("id"), it.s("title"), it.s("description"), it.optLong("start"), it.lN("end"),
                        it.optBoolean("allDay"), it.s("repeat").ifBlank { "none" }, it.optLong("color"), it.s("who"), it.strs("personIds"))
                },
                people = o.list("people") { p ->
                    SnapPerson(
                        p.s("id"), p.s("name"), p.s("pronouns"), p.s("role"), p.optLong("color"), p.sN("avatar"),
                        p.s("description"), p.s("comfort"), p.s("likes"), p.s("dislikes"), p.s("birthday"),
                        p.list("fields") { SnapField(it.s("name"), it.s("value")) } ?: emptyList(),
                        p.list("wishlist") { SnapWish(it.s("kind"), it.s("text"), it.s("note"), it.optBoolean("done")) },
                        p.s("intro"), p.sN("banner"), p.strs("groups"),
                        p.list("relations") { SnapRelation(it.sN("otherId"), it.s("text"), it.s("note")) } ?: emptyList(),
                        p.sN("frame"),
                    )
                },
                safety = o.optJSONObject("safety")?.let { s ->
                    SnapSafety(s.s("info"), s.s("grounding"), s.s("steps"),
                        s.list("contacts") { SnapContact(it.s("name"), it.s("phone"), it.s("note")) } ?: emptyList())
                },
                body = o.list("body") { SnapBody(it.s("section"), it.s("label"), it.s("value")) },
                history = o.list("history") { SnapHistory(it.s("name"), it.optLong("color"), it.optLong("start"), it.lN("end"), it.sN("personId")) },
                canMessage = o.optBoolean("canMessage"),
                appearance = o.sN("appearance"),
                mediaKey = o.sN("mediaKey"),
                push = o.sN("push"),
            )
        }
    }
}

/**
 * Recados do companion para o sistema. Ficam num caminho próprio do companion
 * (shares/in-<hash>-<n>, dono = o companion), criptografados com a mesma chave do convite.
 * n = 0, 1, 2…: se o companion reinstalar o app (outra conta anônima), usa o próximo número.
 */
object Inbox {
    const val SLOTS = 4
    fun path(shareId: String, key: String, slot: Int) =
        "shares/in-" + ShareCrypto.sha256("plurali-inbox|$shareId|$key").take(32) + "-" + slot

    data class Msg(val id: String, val text: String, val time: Long)

    fun encode(l: List<Msg>): String = org.json.JSONArray(l.map { org.json.JSONObject().put("id", it.id).put("text", it.text).put("time", it.time) }).toString()
    fun decode(s: String): List<Msg> = runCatching {
        val a = org.json.JSONArray(s)
        (0 until a.length()).map { a.getJSONObject(it) }.map { Msg(it.getString("id"), it.optString("text"), it.optLong("time")) }
    }.getOrDefault(emptyList())
}

/**
 * Fotos do pacote, guardadas uma vez por sistema (e não uma cópia por companion):
 * shares/m-<id> = { owner, kind: "media", data: base64(AES-GCM do JPEG) }, com a chave de fotos do sistema,
 * que vai dentro do pacote de cada companion. Ao tirar um companion, o Plurali troca a chave e reenvia as fotos.
 */
object Media {
    const val PREFIX = "m:"
    fun path(id: String) = "shares/m-$id"
    fun ref(id: String) = PREFIX + id
    fun idOf(ref: String?): String? = ref?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.takeIf { it.matches(Regex("[0-9a-f]{32}")) }
    /** O mesmo JPEG com a mesma chave sempre dá o mesmo id (não reenvia o que não mudou). */
    fun idFor(jpeg: ByteArray, key: String) = ShareCrypto.sha256("plurali-media|$key|" + ShareCrypto.sha256(jpeg)).take(32)
}
