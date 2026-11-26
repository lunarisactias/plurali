package com.plurali.app.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.updateAndGet
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Todos os dados ficam num único arquivo JSON no armazenamento interno do app.
 * Cada alteração é gravada em disco (escrita atômica + cópia da versão anterior).
 */
object Store {
    lateinit var filesDir: File
        private set
    private lateinit var file: File
    private lateinit var bak: File
    private var keepBackup = true
    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> get() = _data
    private val io = Executors.newSingleThreadExecutor()

    /** Chamado depois de cada alteração (notificação fixa, widget, alarmes...). */
    @Volatile var onChange: ((AppData) -> Unit)? = null

    fun init(ctx: Context) {
        if (::file.isInitialized) return
        filesDir = ctx.filesDir
        file = File(filesDir, "data.json")
        bak = File(filesDir, "data.bak.json")
        _data.value = try {
            if (file.exists()) Json.decode(file.readText()) else AppData()
        } catch (e: Exception) {
            keepBackup = false // não sobrescrever o backup bom com o arquivo corrompido
            runCatching { Json.decode(bak.readText()) }.getOrElse { AppData() }
        }
        io.execute { cleanupAvatars() }
    }

    fun now() = System.currentTimeMillis()

    fun update(f: (AppData) -> AppData) = update(true, f)

    /** notify=false: grava sem disparar efeitos (usado para guardar o token do Firebase sem criar um loop de envio). */
    fun update(notify: Boolean, f: (AppData) -> AppData) {
        val nd = _data.updateAndGet(f)
        val text = Json.encode(nd).toString()
        io.execute { write(text) }
        if (notify) runCatching { onChange?.invoke(nd) }
    }

    // ---------- companions ----------
    fun setCloud(c: CloudConfig?) = update { it.copy(cloud = c) }
    fun setCloudBackup(b: CloudBackup?) = update { it.copy(cloudBackup = b) }
    fun setAppearance(key: String, a: Appearance) = update { d -> d.copy(appearance = d.appearance + (key to a)) }
    fun resetAppearance(key: String) = update { d -> d.copy(appearance = d.appearance - key) }
    fun upsertCompanion(c: CompanionProfile) = update { it.copy(companions = it.companions.upsert(c) { x -> x.id }) }
    fun deleteCompanion(id: String) {
        data.value.companions.find { it.id == id }?.let { Prefs.addPendingDelete(it.shareId) }
        update { d -> d.copy(companions = d.companions.filterNot { it.id == id }) }
    }

    private fun write(text: String) {
        try {
            if (file.exists() && keepBackup) file.copyTo(bak, overwrite = true)
            keepBackup = true
            val tmp = File(filesDir, "data.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (_: Exception) {
        }
    }

    private fun cleanupAvatars() {
        val d = _data.value
        val used = (d.members + d.pendingMembers).flatMap { listOfNotNull(it.avatar, it.banner) }.toSet()
        listOf("avatars", "banners").forEach { dir ->
            File(filesDir, dir).listFiles()?.forEach { f ->
                if ("$dir/${f.name}" !in used && now() - f.lastModified() > 86_400_000) f.delete()
            }
        }
    }

    private fun <T> List<T>.upsert(item: T, id: (T) -> String): List<T> {
        val i = indexOfFirst { id(it) == id(item) }
        return if (i < 0) this + item else toMutableList().also { it[i] = item }
    }

    // ---------- sistema ----------
    fun setSystem(name: String, description: String) =
        update { it.copy(systemName = name, systemDescription = description) }

    // ---------- front ----------
    fun addFront(id: String, custom: Boolean) = update { d ->
        if (d.fronts.any { it.end == null && it.subjectId == id }) d
        else d.copy(fronts = d.fronts + FrontEntry(newId(), id, custom, now(), null))
    }

    fun removeFromFront(subjectId: String) = update { d ->
        val t = now()
        d.copy(fronts = d.fronts.map { if (it.end == null && it.subjectId == subjectId) it.copy(end = t) else it })
    }

    fun endFront(entryId: String) = update { d ->
        val t = now()
        d.copy(fronts = d.fronts.map { if (it.id == entryId && it.end == null) it.copy(end = t) else it })
    }

    fun clearFront() = update { d ->
        val t = now()
        d.copy(fronts = d.fronts.map { if (it.end == null) it.copy(end = t) else it })
    }

    fun switchFront(id: String, custom: Boolean) = update { d ->
        val t = now()
        val already = d.fronts.any { it.end == null && it.subjectId == id }
        val ended = d.fronts.map { if (it.end == null && it.subjectId != id) it.copy(end = t) else it }
        d.copy(fronts = if (already) ended else ended + FrontEntry(newId(), id, custom, t, null))
    }

    fun upsertEntry(e: FrontEntry) = update { it.copy(fronts = it.fronts.upsert(e) { x -> x.id }) }
    fun deleteEntry(id: String) = update { d -> d.copy(fronts = d.fronts.filterNot { it.id == id }) }

    // ---------- membros ----------
    fun upsertMember(m: Member) = update { it.copy(members = it.members.upsert(m) { x -> x.id }) }

    fun setMemberGroups(memberId: String, groupIds: Set<String>) = update { d ->
        d.copy(groups = d.groups.map { g ->
            val has = memberId in g.memberIds
            val want = g.id in groupIds
            when {
                want && !has -> g.copy(memberIds = g.memberIds + memberId)
                !want && has -> g.copy(memberIds = g.memberIds - memberId)
                else -> g
            }
        })
    }

    fun deleteMember(id: String) = update { d ->
        d.copy(
            members = d.members.filterNot { it.id == id },
            fronts = d.fronts.filterNot { it.subjectId == id },
            notes = d.notes.filterNot { it.memberId == id },
            groups = d.groups.map { it.copy(memberIds = it.memberIds - id) },
            polls = d.polls.map { p -> p.copy(votes = p.votes.filterNot { it.memberId == id }) },
            events = d.events.map { it.copy(memberIds = it.memberIds - id) },
            relations = d.relations.filterNot { it.a == id || it.b == id },
            timers = d.timers.filterNot { it.subjectId == id },
            reminders = d.reminders.map { it.copy(memberIds = it.memberIds - id) },
        )
    }

    // ---------- novos membros (pedido feito sem login) ----------
    fun addPending(m: Member) = update { it.copy(pendingMembers = it.pendingMembers + m) }
    fun approvePending(id: String) = update { d ->
        val m = d.pendingMembers.find { it.id == id } ?: return@update d
        d.copy(pendingMembers = d.pendingMembers.filterNot { it.id == id }, members = d.members + m.copy(createdAt = now()))
    }
    fun rejectPending(id: String) = update { d -> d.copy(pendingMembers = d.pendingMembers.filterNot { it.id == id }) }

    // ---------- relações ----------
    fun upsertRelation(r: Relation) = update { it.copy(relations = it.relations.upsert(r) { x -> x.id }) }
    fun deleteRelation(id: String) = update { d -> d.copy(relations = d.relations.filterNot { it.id == id }) }

    // ---------- lembretes e timers ----------
    fun upsertReminder(r: Reminder) = update { it.copy(reminders = it.reminders.upsert(r) { x -> x.id }) }
    fun deleteReminder(id: String) = update { d -> d.copy(reminders = d.reminders.filterNot { it.id == id }) }
    fun upsertTimer(t: FrontTimer) = update { it.copy(timers = it.timers.upsert(t) { x -> x.id }) }
    fun deleteTimer(id: String) = update { d -> d.copy(timers = d.timers.filterNot { it.id == id }) }

    // ---------- plano de segurança ----------
    fun setSafety(p: SafetyPlan) = update { it.copy(safety = p) }

    // ---------- recuperação da senha do sistema ----------
    fun setAdminRecovery(s: Secret?) = update { it.copy(adminRecovery = s) }

    // ---------- custom fronts ----------
    fun upsertCustomFront(c: CustomFront) = update { it.copy(customFronts = it.customFronts.upsert(c) { x -> x.id }) }
    fun deleteCustomFront(id: String) = update { d ->
        d.copy(customFronts = d.customFronts.filterNot { it.id == id }, fronts = d.fronts.filterNot { it.subjectId == id })
    }

    // ---------- grupos ----------
    fun upsertGroup(g: MemberGroup) = update { it.copy(groups = it.groups.upsert(g) { x -> x.id }) }
    fun deleteGroup(id: String) = update { d ->
        val parent = d.groups.find { it.id == id }?.parentId
        d.copy(groups = d.groups.filterNot { it.id == id }.map { if (it.parentId == id) it.copy(parentId = parent) else it })
    }

    // ---------- campos personalizados ----------
    fun upsertField(f: FieldDef) = update { it.copy(fields = it.fields.upsert(f) { x -> x.id }) }
    fun deleteField(id: String) = update { d ->
        d.copy(fields = d.fields.filterNot { it.id == id }, members = d.members.map { it.copy(fields = it.fields - id) })
    }
    fun moveField(id: String, delta: Int) = update { d ->
        val l = d.fields.toMutableList()
        val i = l.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in l.indices) d else {
            val x = l[i]; l[i] = l[j]; l[j] = x
            d.copy(fields = l)
        }
    }

    // ---------- conversas (diretas e grupos) ----------
    /** Abre (ou cria) a conversa direta entre duas pessoas. */
    fun directChat(a: String, b: String): String {
        val ids = listOf(a, b).distinct().sorted()
        data.value.chats.find { !it.group && it.memberIds.sorted() == ids && it.name.isBlank() }?.let { return it.id }
        val c = Chat(newId(), "", ids, group = false)
        update { it.copy(chats = it.chats + c) }
        return c.id
    }

    fun upsertChat(c: Chat) = update { it.copy(chats = it.chats.upsert(c) { x -> x.id }) }

    /** Reação: tocar no mesmo emoji de novo tira a reação. */
    fun react(msgId: String, memberId: String, emoji: String) = update { d ->
        d.copy(messages = d.messages.map { m ->
            if (m.id != msgId) m
            else if (m.reactions[memberId] == emoji) m.copy(reactions = m.reactions - memberId)
            else m.copy(reactions = m.reactions + (memberId to emoji))
        })
    }

    fun pinMessage(msgId: String, pinned: Boolean) = update { d ->
        d.copy(messages = d.messages.map { if (it.id == msgId) it.copy(pinned = pinned) else it })
    }

    fun sendChatMessage(chatId: String, authorId: String?, text: String, image: String? = null) = update { d ->
        val t = now()
        d.copy(
            messages = d.messages + ChatMessage(newId(), chatId, authorId, text, t, image = image),
            chats = d.chats.map { c ->
                if (c.id == chatId && authorId != null) c.copy(readBy = c.readBy + (authorId to t)) else c
            },
        )
    }

    /** Só o autor, e só no primeiro minuto. Mensagens nunca podem ser apagadas. */
    fun editChatMessage(id: String, authorId: String?, text: String): Boolean {
        val m = data.value.messages.find { it.id == id } ?: return false
        if (m.authorId != authorId || !m.canEdit()) return false
        update { d -> d.copy(messages = d.messages.map { if (it.id == id) it.copy(text = text, editedAt = now()) else it }) }
        return true
    }

    fun markChatRead(chatId: String, memberId: String) = update { d ->
        d.copy(chats = d.chats.map { if (it.id == chatId) it.copy(readBy = it.readBy + (memberId to now())) else it })
    }

    // ---------- mural de boas-vindas ----------
    fun setWelcome(l: List<WelcomeCard>) = update { it.copy(welcome = l) }
    fun upsertWelcome(w: WelcomeCard) = update { it.copy(welcome = it.welcome.upsert(w) { x -> x.id }) }
    fun deleteWelcome(id: String) = update { d -> d.copy(welcome = d.welcome.filterNot { it.id == id }) }
    fun moveWelcome(id: String, delta: Int) = update { d ->
        val l = d.welcome.toMutableList()
        val i = l.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in l.indices) d else { val x = l[i]; l[i] = l[j]; l[j] = x; d.copy(welcome = l) }
    }

    // ---------- mural de avisos ----------
    fun upsertNotice(n: Notice) = update { it.copy(notices = it.notices.upsert(n) { x -> x.id }) }
    fun deleteNotice(id: String) = update { d -> d.copy(notices = d.notices.filterNot { it.id == id }) }

    // ---------- wishlist ----------
    fun upsertWish(memberId: String, w: WishItem) = update { d ->
        d.copy(members = d.members.map { m -> if (m.id == memberId) m.copy(wishlist = m.wishlist.upsert(w) { x -> x.id }) else m })
    }
    fun deleteWish(memberId: String, wishId: String) = update { d ->
        d.copy(members = d.members.map { m -> if (m.id == memberId) m.copy(wishlist = m.wishlist.filterNot { it.id == wishId }) else m })
    }

    // ---------- calendário ----------
    fun upsertEvent(e: CalEvent) = update { it.copy(events = it.events.upsert(e) { x -> x.id }) }
    fun deleteEvent(id: String) = update { d -> d.copy(events = d.events.filterNot { it.id == id }) }

    // ---------- corpo ----------
    fun upsertBody(b: BodyEntry) = update { it.copy(body = it.body.upsert(b) { x -> x.id }) }
    fun deleteBody(id: String) = update { d -> d.copy(body = d.body.filterNot { it.id == id }) }
    fun setVault(v: Vault?) = update { it.copy(vault = v) }

    // ---------- senhas ----------
    fun setAdminPassword(s: Secret?) = update { it.copy(adminPassword = s) }
    fun setMemberPassword(memberId: String, s: Secret?) = update { d ->
        d.copy(members = d.members.map { if (it.id == memberId) it.copy(password = s) else it })
    }

    // ---------- notas ----------
    fun upsertNote(n: Note) = update { it.copy(notes = it.notes.upsert(n) { x -> x.id }) }
    fun deleteNote(id: String) = update { d -> d.copy(notes = d.notes.filterNot { it.id == id }) }

    // ---------- enquetes ----------
    fun upsertPoll(p: Poll) = update { d ->
        val valid = p.options.map { it.id }.toSet() + ABSTAIN + VETO
        d.copy(polls = d.polls.upsert(p.copy(votes = p.votes.filter { it.optionId in valid })) { x -> x.id })
    }
    fun deletePoll(id: String) = update { d -> d.copy(polls = d.polls.filterNot { it.id == id }) }
    fun vote(pollId: String, memberId: String, optionId: String, comment: String) = update { d ->
        d.copy(polls = d.polls.map { p ->
            if (p.id != pollId) p
            else p.copy(votes = p.votes.filterNot { it.memberId == memberId } + PollVote(memberId, optionId, comment))
        })
    }
    fun removeVote(pollId: String, memberId: String) = update { d ->
        d.copy(polls = d.polls.map { p ->
            if (p.id != pollId) p else p.copy(votes = p.votes.filterNot { it.memberId == memberId })
        })
    }
    fun setPollEnd(pollId: String, end: Long?) = update { d ->
        d.copy(polls = d.polls.map { if (it.id == pollId) it.copy(endTime = end) else it })
    }

    // ---------- backup ----------
    private val fileDirs = setOf("avatars", "banners", "chat", "wallpapers", "welcome")

    /** Todos os arquivos (fotos) usados pelos dados: avatares, capas e imagens das conversas. */
    private fun AppData.referencedFiles(): Set<String> =
        ((members + pendingMembers).flatMap { listOfNotNull(it.avatar, it.banner) } + messages.mapNotNull { it.image } +
            appearance.values.mapNotNull { it.wallpaper } + welcome.mapNotNull { it.image }).toSet()

    private fun safePath(p: String): Boolean {
        val parts = p.split('/')
        return parts.size == 2 && parts[0] in fileDirs && parts[1].isNotBlank() && !parts[1].contains("..") && !parts[1].contains('\\')
    }

    fun exportJson(compact: Boolean = false): String {
        val d = _data.value
        val o = Json.encode(d)
        val files = JSONObject()
        d.referencedFiles().forEach { p ->
            val f = File(filesDir, p)
            if (safePath(p) && f.exists()) files.put(p, Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
        }
        o.put("files", files)
        return if (compact) o.toString() else o.toString(1)
    }

    fun importJson(text: String) {
        val o = JSONObject(text)
        var d0 = Json.decode(o)
        fileDirs.forEach { File(filesDir, it).mkdirs() }
        // formato novo: "files" = { "avatars/x.jpg": base64, ... }
        o.optJSONObject("files")?.let { files ->
            files.keys().forEach { p ->
                if (safePath(p)) runCatching { File(filesDir, p).writeBytes(Base64.decode(files.getString(p), Base64.DEFAULT)) }
            }
        }
        // formato antigo: "avatars" = { memberId: base64 }
        o.optJSONObject("avatars")?.let { av ->
            d0 = d0.copy(members = d0.members.map { m ->
                val b = av.optString(m.id, "").takeIf { it.isNotEmpty() } ?: return@map m
                val name = "avatars/${m.id}_${now()}.jpg"
                File(filesDir, name).writeBytes(Base64.decode(b, Base64.DEFAULT))
                m.copy(avatar = name)
            })
        }
        fun ok(p: String?) = p?.takeIf { File(filesDir, it).exists() }
        update {
            d0.copy(
                members = d0.members.map { m -> m.copy(avatar = ok(m.avatar), banner = ok(m.banner)) },
                messages = d0.messages.map { m -> m.copy(image = ok(m.image)) },
            )
        }
    }
}
