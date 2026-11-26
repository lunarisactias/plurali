package com.plurali.app.data

import org.json.JSONArray
import org.json.JSONObject

object Json {
    private fun JSONObject.s(k: String) = if (has(k) && !isNull(k)) getString(k) else ""
    private fun JSONObject.sN(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null
    private fun JSONObject.lN(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null
    private fun <T> JSONObject.arr(k: String, f: (JSONObject) -> T): List<T> {
        val a = optJSONArray(k) ?: return emptyList()
        return (0 until a.length()).map { f(a.getJSONObject(it)) }
    }
    private fun JSONObject.strs(k: String): List<String> {
        val a = optJSONArray(k) ?: return emptyList()
        return (0 until a.length()).map { a.getString(it) }
    }
    private fun JSONObject.smap(k: String): Map<String, String> {
        val o = optJSONObject(k) ?: return emptyMap()
        return o.keys().asSequence().associateWith { o.getString(it) }
    }
    private fun JSONObject.lmap(k: String): Map<String, Long> {
        val o = optJSONObject(k) ?: return emptyMap()
        return o.keys().asSequence().associateWith { o.getLong(it) }
    }
    private fun <T> List<T>.ja(f: (T) -> Any): JSONArray = JSONArray().also { a -> forEach { a.put(f(it)) } }
    private fun JSONObject.putN(k: String, v: Any?): JSONObject = put(k, v ?: JSONObject.NULL)
    private fun Map<String, *>.jo(): JSONObject = JSONObject().also { o -> forEach { (k, v) -> o.put(k, v) } }

    private fun secret(s: Secret?): Any = s?.let { JSONObject().put("salt", it.salt).put("hash", it.hash) } ?: JSONObject.NULL
    private fun JSONObject.secret(k: String): Secret? = optJSONObject(k)?.let { Secret(it.s("salt"), it.s("hash")) }

    fun encode(d: AppData): JSONObject = JSONObject()
        .put("format", "plurali")
        .put("version", 2)
        .put("systemName", d.systemName)
        .put("systemDescription", d.systemDescription)
        .put("adminPassword", secret(d.adminPassword))
        .put("adminRecovery", secret(d.adminRecovery))
        .put("fields", d.fields.ja { JSONObject().put("id", it.id).put("name", it.name).put("visibility", it.visibility) })
        .put("members", d.members.ja { encodeMember(it) })
        .put("pendingMembers", d.pendingMembers.ja { encodeMember(it) })
        .put("reminders", d.reminders.ja { r ->
            JSONObject().put("id", r.id).put("title", r.title).put("text", r.text).put("hour", r.hour).put("minute", r.minute)
                .put("days", r.days.ja { it }).put("memberIds", r.memberIds.ja { it }).put("enabled", r.enabled)
        })
        .put("timers", d.timers.ja { t ->
            JSONObject().putN("subjectId", t.subjectId).put("id", t.id).put("afterMin", t.afterMin)
                .put("message", t.message).put("enabled", t.enabled)
        })
        .put("relations", d.relations.ja { r ->
            JSONObject().put("id", r.id).put("a", r.a).put("b", r.b).put("type", r.type).put("note", r.note)
        })
        .put("safety", JSONObject().put("info", d.safety.info).put("grounding", d.safety.grounding).put("steps", d.safety.steps)
            .put("contacts", d.safety.contacts.ja { c ->
                JSONObject().put("id", c.id).put("name", c.name).put("phone", c.phone).put("note", c.note)
            }))
        .put("customFronts", d.customFronts.ja { c ->
            JSONObject().put("id", c.id).put("name", c.name).put("color", c.color).put("description", c.description)
        })
        .put("groups", d.groups.ja { g ->
            JSONObject().put("id", g.id).put("name", g.name).put("emoji", g.emoji).put("color", g.color)
                .put("description", g.description).putN("parentId", g.parentId)
                .put("memberIds", g.memberIds.ja { it })
        })
        .put("fronts", d.fronts.ja { e ->
            JSONObject().put("id", e.id).put("subjectId", e.subjectId).put("custom", e.custom)
                .put("start", e.start).putN("end", e.end).put("status", e.status).put("comment", e.comment)
        })
        .put("chats", d.chats.ja { c ->
            JSONObject().put("id", c.id).put("name", c.name).put("memberIds", c.memberIds.ja { it })
                .put("group", c.group).put("createdAt", c.createdAt).put("readBy", c.readBy.jo())
        })
        .put("messages", d.messages.ja { m ->
            JSONObject().put("id", m.id).put("chatId", m.chatId).putN("authorId", m.authorId)
                .put("text", m.text).put("time", m.time).putN("editedAt", m.editedAt)
                .putN("image", m.image).put("reactions", m.reactions.jo()).put("pinned", m.pinned)
                .putN("audio", m.audio).put("audioMs", m.audioMs)
        })
        .put("notices", d.notices.ja { n ->
            JSONObject().put("id", n.id).putN("authorId", n.authorId).put("title", n.title).put("text", n.text)
                .put("time", n.time).put("pinned", n.pinned).put("color", n.color)
        })
        .put("welcome", d.welcome.ja { w ->
            JSONObject().put("id", w.id).put("title", w.title).put("text", w.text).put("color", w.color).putN("image", w.image).put("size", w.size).put("align", w.align).putN("voice", w.voice).put("voiceMs", w.voiceMs)
        })
        .put("notes", d.notes.ja { n ->
            JSONObject().put("id", n.id).put("memberId", n.memberId).put("title", n.title)
                .put("body", n.body).put("time", n.time)
        })
        .put("polls", d.polls.ja { p ->
            JSONObject().put("id", p.id).put("title", p.title).put("description", p.description)
                .put("options", p.options.ja { JSONObject().put("id", it.id).put("name", it.name) })
                .put("votes", p.votes.ja {
                    JSONObject().put("memberId", it.memberId).put("optionId", it.optionId).put("comment", it.comment)
                })
                .put("allowAbstain", p.allowAbstain).put("allowVeto", p.allowVeto)
                .putN("endTime", p.endTime).put("createdAt", p.createdAt)
        })
        .put("events", d.events.ja { e ->
            JSONObject().put("id", e.id).put("title", e.title).put("description", e.description)
                .put("start", e.start).putN("end", e.end).put("allDay", e.allDay)
                .put("memberIds", e.memberIds.ja { it }).put("roles", e.roles.ja { it })
                .put("color", e.color).put("repeat", e.repeat).putN("remindMin", e.remindMin)
        })
        .put("body", d.body.ja { b ->
            JSONObject().put("id", b.id).put("section", b.section).put("label", b.label).put("value", b.value)
        })
        .put("vault", d.vault?.let { JSONObject().put("salt", it.salt).put("iv", it.iv).put("data", it.data) } ?: JSONObject.NULL)
        .put("cloud", d.cloud?.let {
            JSONObject().put("dbUrl", it.dbUrl).put("apiKey", it.apiKey).putN("uid", it.uid).putN("refreshToken", it.refreshToken)
        } ?: JSONObject.NULL)
        .put("companions", d.companions.ja { c ->
            val p = c.perms
            JSONObject().put("id", c.id).put("name", c.name).put("about", c.about).put("shareId", c.shareId).put("key", c.key).put("createdAt", c.createdAt)
                .put("perms", JSONObject()
                    .put("messages", p.messages)
                    .put("front", p.front).put("frontStatus", p.frontStatus).put("customFronts", p.customFronts)
                    .put("notices", p.notices).put("calendar", p.calendar).put("people", p.people)
                    .put("memberIds", p.memberIds?.ja { it } ?: JSONObject.NULL)
                    .put("avatars", p.avatars).put("pronouns", p.pronouns).put("roles", p.roles).put("description", p.description)
                    .put("comfort", p.comfort).put("likes", p.likes).put("birthday", p.birthday).put("wishlist", p.wishlist)
                    .put("banner", p.banner).put("groups", p.groups).put("relations", p.relations)
                    .put("fieldIds", p.fieldIds.ja { it })
                    .put("history", p.history).put("safety", p.safety).put("body", p.body).put("intro", p.intro))
        })
        .put("cloudBackup", d.cloudBackup?.let { JSONObject().put("code", it.code).put("enabled", it.enabled).put("freq", it.freq).put("wifiOnly", it.wifiOnly).put("target", it.target) } ?: JSONObject.NULL)
        .put("appearance", JSONObject().also { ap -> d.appearance.forEach { (k, a) -> ap.put(k, encodeAppearance(a)) } })
        .put("frontNotes", d.frontNotes.ja { n ->
            JSONObject().put("id", n.id).putN("authorId", n.authorId).put("text", n.text).put("createdAt", n.createdAt)
                .putN("targetId", n.targetId).putN("doneById", n.doneById).putN("doneAt", n.doneAt)
        })
        .put("meds", d.meds.ja { m ->
            JSONObject().put("id", m.id).put("name", m.name).put("dose", m.dose).put("times", m.times.ja { it })
                .put("days", m.days.toList().ja { it }).put("notes", m.notes).put("active", m.active).put("color", m.color)
        })
        .put("medTakes", d.medTakes.ja { t ->
            JSONObject().put("id", t.id).put("medId", t.medId).put("slot", t.slot).put("takenAt", t.takenAt)
                .putN("byId", t.byId).put("byName", t.byName).put("skipped", t.skipped)
        })
        .put("tasks", d.tasks.ja { t ->
            JSONObject().put("id", t.id).put("title", t.title).put("notes", t.notes).putN("assigneeId", t.assigneeId).putN("due", t.due)
                .put("repeat", t.repeat).putN("doneAt", t.doneAt).putN("doneById", t.doneById).put("createdAt", t.createdAt).putN("createdById", t.createdById)
        })
        .put("diary", d.diary.ja { e ->
            JSONObject().put("id", e.id).putN("authorId", e.authorId).put("time", e.time).put("text", e.text).put("images", e.images.ja { it })
        })
        .put("companionMessages", d.companionMessages.ja { m ->
            JSONObject().put("id", m.id).put("companionId", m.companionId).put("text", m.text).put("time", m.time).put("read", m.read)
        })
        .put("lastSeen", d.lastSeen.jo())
        .put("mediaKey", d.mediaKey ?: JSONObject.NULL)

    private fun encodeMember(m: Member): JSONObject =
        JSONObject().put("id", m.id).put("name", m.name).put("pronouns", m.pronouns)
            .put("role", m.role).put("color", m.color).put("description", m.description)
            .putN("avatar", m.avatar).put("birthday", m.birthday).put("archived", m.archived)
            .put("fields", m.fields.jo()).put("createdAt", m.createdAt)
            .put("password", secret(m.password))
            .put("comfort", m.comfort).put("intro", m.intro).put("likes", m.likes).put("dislikes", m.dislikes).putN("banner", m.banner)
            .put("wishlist", m.wishlist.ja { w ->
                JSONObject().put("id", w.id).put("kind", w.kind).put("text", w.text).put("note", w.note)
                    .put("done", w.done).put("createdAt", w.createdAt)
            })

    private fun decodeMember(it: JSONObject) = Member(
        id = it.s("id"), name = it.s("name"), pronouns = it.s("pronouns"), role = it.s("role"),
        color = it.optLong("color", 0xFF7E57C2), description = it.s("description"),
        avatar = it.sN("avatar"), birthday = it.s("birthday"), archived = it.optBoolean("archived"),
        fields = it.smap("fields"), createdAt = it.optLong("createdAt", System.currentTimeMillis()),
        password = it.secret("password"),
        wishlist = it.arr("wishlist") { w ->
            WishItem(w.s("id"), w.s("kind").ifBlank { "buy" }, w.s("text"), w.s("note"), w.optBoolean("done"), w.optLong("createdAt"))
        },
        comfort = it.s("comfort"), intro = it.s("intro"), likes = it.s("likes"), dislikes = it.s("dislikes"), banner = it.sN("banner"),
    )

    private fun JSONObject.ints(k: String): List<Int>? {
        val a = optJSONArray(k) ?: return null
        return (0 until a.length()).map { a.getInt(it) }
    }

    fun decode(text: String): AppData = decode(JSONObject(text))

    fun decode(o: JSONObject): AppData {
        var chats = o.arr("chats") {
            Chat(it.s("id"), it.s("name"), it.strs("memberIds"), it.optBoolean("group"), it.optLong("createdAt"), it.lmap("readBy"))
        }
        var messages = o.arr("messages") {
            ChatMessage(
                it.s("id"), it.s("chatId"), it.sN("authorId"), it.s("text"), it.optLong("time"), it.lN("editedAt"),
                image = it.sN("image"), reactions = it.smap("reactions"), pinned = it.optBoolean("pinned"),
                audio = it.sN("audio"), audioMs = it.optLong("audioMs"),
            )
        }
        // migração: quadro de mensagens antigo (versão 1) -> conversas diretas
        if (!o.has("chats") && o.has("board")) {
            val ch = mutableListOf<Chat>()
            val ms = mutableListOf<ChatMessage>()
            o.arr("board") { it }.forEach { b ->
                val to = b.s("toId")
                val from = b.sN("fromId")
                val ids = listOfNotNull(from, to).distinct().sorted()
                val name = if (from == null) "Mensagens anônimas" else ""
                val c = ch.find { it.memberIds == ids && it.name == name }
                    ?: Chat(newId(), name, ids, group = false, createdAt = b.optLong("time")).also { ch += it }
                ms += ChatMessage(b.s("id"), c.id, from, listOf(b.s("title"), b.s("body")).filter { it.isNotBlank() }.joinToString("\n"), b.optLong("time"))
            }
            chats = ch
            messages = ms
        }
        return AppData(
            systemName = o.s("systemName"),
            systemDescription = o.s("systemDescription"),
            adminPassword = o.secret("adminPassword"),
            adminRecovery = o.secret("adminRecovery"),
            fields = o.arr("fields") { FieldDef(it.s("id"), it.s("name"), it.s("visibility").ifBlank { "all" }) },
            members = o.arr("members") { decodeMember(it) },
            pendingMembers = o.arr("pendingMembers") { decodeMember(it) },
            reminders = o.arr("reminders") {
                Reminder(
                    it.s("id"), it.s("title"), it.s("text"), it.optInt("hour"), it.optInt("minute"),
                    it.ints("days") ?: (1..7).toList(), it.strs("memberIds"), it.optBoolean("enabled", true),
                )
            },
            timers = o.arr("timers") {
                FrontTimer(it.s("id"), it.sN("subjectId"), it.optInt("afterMin"), it.s("message"), it.optBoolean("enabled", true))
            },
            relations = o.arr("relations") { Relation(it.s("id"), it.s("a"), it.s("b"), it.s("type"), it.s("note")) },
            safety = o.optJSONObject("safety")?.let { s ->
                SafetyPlan(
                    info = s.s("info"), grounding = s.s("grounding"), steps = s.s("steps"),
                    contacts = s.arr("contacts") { SafetyContact(it.s("id"), it.s("name"), it.s("phone"), it.s("note")) },
                )
            } ?: SafetyPlan(),
            customFronts = o.arr("customFronts") {
                CustomFront(it.s("id"), it.s("name"), it.optLong("color", 0xFF78909C), it.s("description"))
            },
            groups = o.arr("groups") {
                MemberGroup(
                    it.s("id"), it.s("name"), it.s("emoji"), it.optLong("color", 0xFF5C6BC0),
                    it.s("description"), it.sN("parentId"), it.strs("memberIds"),
                )
            },
            fronts = o.arr("fronts") {
                FrontEntry(
                    it.s("id"), it.s("subjectId"), it.optBoolean("custom"), it.getLong("start"),
                    it.lN("end"), it.s("status"), it.s("comment"),
                )
            },
            chats = chats,
            messages = messages,
            notices = o.arr("notices") {
                Notice(it.s("id"), it.sN("authorId"), it.s("title"), it.s("text"), it.optLong("time"), it.optBoolean("pinned"), it.optLong("color", 0xFFD6AC5E))
            },
            welcome = o.arr("welcome") {
                WelcomeCard(it.s("id"), it.s("title"), it.s("text"), it.optLong("color", 0xFFD6AC5E), it.sN("image"), it.optInt("size", 6).takeIf { s -> s in listOf(2, 3, 4, 6) } ?: 6, it.s("align").ifBlank { "start" }, it.sN("voice"), it.optLong("voiceMs", 0))
            },
            notes = o.arr("notes") { Note(it.s("id"), it.s("memberId"), it.s("title"), it.s("body"), it.optLong("time")) },
            polls = o.arr("polls") { p ->
                Poll(
                    id = p.s("id"), title = p.s("title"), description = p.s("description"),
                    options = p.arr("options") { PollOption(it.s("id"), it.s("name")) },
                    votes = p.arr("votes") { PollVote(it.s("memberId"), it.s("optionId"), it.s("comment")) },
                    allowAbstain = p.optBoolean("allowAbstain", true), allowVeto = p.optBoolean("allowVeto"),
                    endTime = p.lN("endTime"), createdAt = p.optLong("createdAt"),
                )
            },
            events = o.arr("events") {
                CalEvent(
                    it.s("id"), it.s("title"), it.s("description"), it.getLong("start"), it.lN("end"), it.optBoolean("allDay"),
                    it.strs("memberIds"), it.strs("roles"), it.optLong("color", 0xFFD6AC5E), it.s("repeat").ifBlank { "none" },
                    if (it.has("remindMin") && !it.isNull("remindMin")) it.getInt("remindMin") else null,
                )
            },
            body = o.arr("body") { BodyEntry(it.s("id"), it.s("section"), it.s("label"), it.s("value")) },
            vault = o.optJSONObject("vault")?.let { Vault(it.s("salt"), it.s("iv"), it.s("data")) },
            cloud = o.optJSONObject("cloud")?.let { CloudConfig(it.s("dbUrl"), it.s("apiKey"), it.sN("uid"), it.sN("refreshToken")) },
            companions = o.arr("companions") { c ->
                val p = c.optJSONObject("perms") ?: JSONObject()
                val d = CompanionPerms()
                CompanionProfile(
                    id = c.s("id"), name = c.s("name"), about = c.s("about"), shareId = c.s("shareId"), key = c.s("key"),
                    perms = CompanionPerms(
                        messages = p.optBoolean("messages", d.messages),
                        front = p.optBoolean("front", d.front), frontStatus = p.optBoolean("frontStatus", d.frontStatus),
                        customFronts = p.optBoolean("customFronts", d.customFronts), notices = p.optBoolean("notices", d.notices),
                        calendar = p.optBoolean("calendar", d.calendar), people = p.optBoolean("people", d.people),
                        memberIds = if (p.has("memberIds") && !p.isNull("memberIds")) p.strs("memberIds") else null,
                        avatars = p.optBoolean("avatars", d.avatars), pronouns = p.optBoolean("pronouns", d.pronouns),
                        roles = p.optBoolean("roles", d.roles), description = p.optBoolean("description", d.description),
                        comfort = p.optBoolean("comfort", d.comfort), likes = p.optBoolean("likes", d.likes),
                        birthday = p.optBoolean("birthday", d.birthday), wishlist = p.optBoolean("wishlist", d.wishlist),
                        banner = p.optBoolean("banner", d.banner), groups = p.optBoolean("groups", d.groups),
                        relations = p.optBoolean("relations", d.relations),
                        fieldIds = p.strs("fieldIds"),
                        history = p.optBoolean("history", d.history), safety = p.optBoolean("safety", d.safety),
                        body = p.optBoolean("body", d.body), intro = p.optBoolean("intro", d.intro),
                    ),
                    createdAt = c.optLong("createdAt"),
                )
            },
            cloudBackup = o.optJSONObject("cloudBackup")?.let {
                CloudBackup(it.s("code"), it.optBoolean("enabled", true), it.s("freq").ifBlank { "6h" }, it.optBoolean("wifiOnly", true), it.s("target").ifBlank { "firebase" }) // sem o campo = backup antigo no Firebase
            }?.takeIf { it.code.isNotBlank() },
            appearance = o.optJSONObject("appearance")?.let { ap -> ap.keys().asSequence().associateWith { decodeAppearance(ap.getJSONObject(it)) } } ?: emptyMap(),
            frontNotes = o.arr("frontNotes") { FrontNote(it.s("id"), it.sN("authorId"), it.s("text"), it.optLong("createdAt"), it.sN("targetId"), it.sN("doneById"), it.lN("doneAt")) },
            meds = o.arr("meds") {
                Medication(it.s("id"), it.s("name"), it.s("dose"), it.optJSONArray("times")?.let { a -> (0 until a.length()).map { i -> a.getInt(i) } } ?: listOf(480),
                    it.optJSONArray("days")?.let { a -> (0 until a.length()).map { i -> a.getInt(i) }.toSet() } ?: (1..7).toSet(),
                    it.s("notes"), it.optBoolean("active", true), it.optLong("color", 0xFF4DB6AC))
            },
            medTakes = o.arr("medTakes") { MedTake(it.s("id"), it.s("medId"), it.optLong("slot"), it.optLong("takenAt"), it.sN("byId"), it.s("byName"), it.optBoolean("skipped")) },
            tasks = o.arr("tasks") {
                TaskItem(it.s("id"), it.s("title"), it.s("notes"), it.sN("assigneeId"), it.lN("due"), it.s("repeat").ifBlank { "none" },
                    it.lN("doneAt"), it.sN("doneById"), it.optLong("createdAt"), it.sN("createdById"))
            },
            diary = o.arr("diary") { DiaryEntry(it.s("id"), it.sN("authorId"), it.optLong("time"), it.s("text"), it.strs("images")) },
            companionMessages = o.arr("companionMessages") { CompanionMessage(it.s("id"), it.s("companionId"), it.s("text"), it.optLong("time"), it.optBoolean("read")) },
            lastSeen = o.lmap("lastSeen"),
            mediaKey = o.optString("mediaKey").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    fun encodeAppearance(a: Appearance): JSONObject = AppearanceJson.encode(a)
    fun decodeAppearance(o: JSONObject): Appearance = AppearanceJson.decode(o)

    // ---- dados sensíveis (lista de BodyEntry) ----
    fun encodeEntries(l: List<BodyEntry>): String = l.ja { b ->
        JSONObject().put("id", b.id).put("section", b.section).put("label", b.label).put("value", b.value)
    }.toString()

    fun decodeEntries(s: String): List<BodyEntry> {
        val a = JSONArray(s)
        return (0 until a.length()).map { a.getJSONObject(it) }.map { BodyEntry(it.s("id"), it.s("section"), it.s("label"), it.s("value")) }
    }
}
