package com.plurali.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

const val ABSTAIN = "abstain"
const val VETO = "veto"

/** Tempo em que o autor ainda pode editar uma mensagem enviada. */
const val MESSAGE_EDIT_WINDOW_MS = 60_000L

/** visibility: "all" (inclusive visitantes), "members" (só quem tem login), "private" (só o membro e o sistema). */
data class FieldDef(val id: String, val name: String, val visibility: String = "all")

/** Senha guardada como hash PBKDF2 (nunca em texto). */
data class Secret(val salt: String, val hash: String)

data class WishItem(
    val id: String,
    val kind: String, // "buy" (comprar) ou "do" (fazer)
    val text: String,
    val note: String = "",
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

data class Member(
    val id: String,
    val name: String,
    val pronouns: String = "",
    val role: String = "",
    val color: Long = 0xFF7E57C2,
    val description: String = "",
    val avatar: String? = null,
    val birthday: String = "",
    val archived: Boolean = false,
    val fields: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val password: Secret? = null,
    val wishlist: List<WishItem> = emptyList(),
    val comfort: String = "",   // como me acalmar
    val intro: String = "",     // apresentação para companions (pessoas de fora)
    val likes: String = "",
    val dislikes: String = "",
    val banner: String? = null,
) {
    /** Papéis separados por vírgula ou barra: "Protetor, Host". */
    fun roles(): List<String> = role.split(',', '/', ';').map { it.trim() }.filter { it.isNotEmpty() }
}

data class CustomFront(
    val id: String,
    val name: String,
    val color: Long = 0xFF78909C,
    val description: String = "",
)

data class MemberGroup(
    val id: String,
    val name: String,
    val emoji: String = "",
    val color: Long = 0xFF5C6BC0,
    val description: String = "",
    val parentId: String? = null,
    val memberIds: List<String> = emptyList(),
)

data class FrontEntry(
    val id: String,
    val subjectId: String,
    val custom: Boolean,
    val start: Long,
    val end: Long?,
    val status: String = "",
    val comment: String = "",
)

/** Conversa: direta (2 pessoas) ou grupo. */
data class Chat(
    val id: String,
    val name: String,
    val memberIds: List<String>,
    val group: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val readBy: Map<String, Long> = emptyMap(), // membro -> última leitura
)

data class ChatMessage(
    val id: String,
    val chatId: String,
    val authorId: String?, // null = anônimo (mensagens antigas do quadro)
    val text: String,
    val time: Long,
    val editedAt: Long? = null,
    val image: String? = null,
    val reactions: Map<String, String> = emptyMap(), // membro -> emoji
    val pinned: Boolean = false,
) {
    fun canEdit(now: Long = System.currentTimeMillis()) = now - time < MESSAGE_EDIT_WINDOW_MS
}

/** Aviso do mural da tela inicial. */
data class Notice(
    val id: String,
    val authorId: String?,
    val title: String,
    val text: String,
    val time: Long,
    val pinned: Boolean = false,
    val color: Long = 0xFFD6AC5E,
)

data class Note(
    val id: String,
    val memberId: String,
    val title: String,
    val body: String,
    val time: Long,
)

data class PollOption(val id: String, val name: String)
data class PollVote(val memberId: String, val optionId: String, val comment: String = "")

data class Poll(
    val id: String,
    val title: String,
    val description: String = "",
    val options: List<PollOption> = emptyList(),
    val votes: List<PollVote> = emptyList(),
    val allowAbstain: Boolean = true,
    val allowVeto: Boolean = false,
    val endTime: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun isOpen(now: Long = System.currentTimeMillis()) = endTime == null || endTime > now
    fun optionName(id: String) = when (id) {
        ABSTAIN -> "Abstenção"
        VETO -> "Veto"
        else -> options.find { it.id == id }?.name ?: "?"
    }
    fun leader(): PollOption? = options.maxByOrNull { o -> votes.count { it.optionId == o.id } }
        ?.takeIf { o -> votes.any { it.optionId == o.id } }
}

/** Evento do calendário. */
data class CalEvent(
    val id: String,
    val title: String,
    val description: String = "",
    val start: Long,
    val end: Long? = null,
    val allDay: Boolean = false,
    val memberIds: List<String> = emptyList(),
    val roles: List<String> = emptyList(),
    val color: Long = 0xFFD6AC5E,
    val repeat: String = "none", // none, weekly, monthly, yearly
    val remindMin: Int? = null,  // minutos antes; null = sem lembrete
)

/** Lembrete de horário fixo (remédio, rotina...). days: 1=segunda ... 7=domingo. */
data class Reminder(
    val id: String,
    val title: String,
    val text: String = "",
    val hour: Int,
    val minute: Int,
    val days: List<Int> = (1..7).toList(),
    val memberIds: List<String> = emptyList(),
    val enabled: Boolean = true,
)

/** Timer automático de front: avisa quando alguém entra (afterMin=0) ou fica X minutos no front. */
data class FrontTimer(
    val id: String,
    val subjectId: String?, // null = qualquer pessoa
    val afterMin: Int,
    val message: String = "",
    val enabled: Boolean = true,
)

data class SafetyContact(val id: String, val name: String, val phone: String, val note: String = "")

data class SafetyPlan(
    val info: String = "",
    val grounding: String = DEFAULT_GROUNDING,
    val steps: String = "",
    val contacts: List<SafetyContact> = DEFAULT_CONTACTS,
)

val DEFAULT_GROUNDING = """
Respire devagar: puxe o ar contando até 4, segure até 4, solte contando até 6.

Olhe em volta e encontre:
• 5 coisas que você pode ver
• 4 coisas que você pode tocar
• 3 coisas que você pode ouvir
• 2 cheiros
• 1 gosto

Pés no chão. Você está aqui, agora, e está em segurança.
""".trimIndent()

val DEFAULT_CONTACTS = listOf(
    SafetyContact("cvv", "CVV — apoio emocional", "188", "24 horas, gratuito, também em cvv.org.br"),
    SafetyContact("samu", "SAMU", "192", "Emergência médica"),
    SafetyContact("policia", "Polícia", "190", ""),
)

/** Cartão do mural de boas-vindas (para quem está chegando pela primeira vez). Mostrados em ordem. */
/**
 * Item do mural de boas-vindas: texto e/ou foto (planta da casa, família, pets…).
 * size = largura numa grade de 6 colunas: 2 = pequeno (1/3), 3 = médio (1/2), 4 = grande (2/3), 6 = largura toda.
 */
data class WelcomeCard(
    val id: String, val title: String, val text: String, val color: Long = 0xFFD6AC5E,
    val image: String? = null, val size: Int = 6,
)

val SUGGESTED_WELCOME = listOf(
    WelcomeCard("w1", "Oi. Você está em segurança.",
        "Se você não sabe direito onde está, quem é ou o que está acontecendo, tudo bem. Respire devagar. Ninguém aqui vai te machucar.", 0xFF81C784),
    WelcomeCard("w2", "Você faz parte de um sistema",
        "Este corpo é compartilhado por várias pessoas. Às vezes alguém novo aparece — talvez seja você. Isso é normal pra gente, e você é bem-vindo(a).", 0xFF9575CD),
    WelcomeCard("w3", "Onde você está",
        "(Escreva aqui: cidade, casa, com quem vocês moram, se há animais, o que costuma ter por perto.)", 0xFF64B5F6),
    WelcomeCard("w4", "Que dia é hoje",
        "A data de hoje aparece no Plano de segurança. O tempo pode ter passado desde a última coisa de que você lembra.", 0xFFFFB74D),
    WelcomeCard("w5", "Quem pode te ajudar",
        "Na aba Membros você vê quem faz parte do sistema. No Plano de segurança estão as pessoas de confiança e como falar com elas.", 0xFFF06292),
    WelcomeCard("w6", "O que fazer agora",
        "Não precisa fazer nada. Quando quiser, toque em “Sou novo aqui” para se apresentar. Você pode escolher seu nome e contar só o que quiser.", 0xFFD6AC5E),
)

/** Relação entre membros: "a <type> b" (ex.: Ana protege Bruno). */
data class Relation(val id: String, val a: String, val b: String, val type: String, val note: String = "")

/** Projeto Firebase usado para enviar dados aos companions (uid/refreshToken = sessão anônima deste sistema). */
data class CloudConfig(val dbUrl: String, val apiKey: String, val uid: String? = null, val refreshToken: String? = null)

/** Projeto Firebase do Plurali (já vem configurado; não precisa digitar nada). */
object FirebaseProject {
    const val DB_URL = "https://plurali-8ffe7-default-rtdb.firebaseio.com"
    const val API_KEY = "AIzaSyAyTB0dtdXwb2d6T93WqqYV9uU7qsEPXl8"
}

/** O que um companion pode ver. memberIds = null → todos os membros ativos. */
data class CompanionPerms(
    val front: Boolean = true,
    val frontStatus: Boolean = false,
    val customFronts: Boolean = true,
    val notices: Boolean = true,
    val calendar: Boolean = true,
    val people: Boolean = true,
    val memberIds: List<String>? = null,
    val avatars: Boolean = true,
    val pronouns: Boolean = true,
    val intro: Boolean = true,
    val roles: Boolean = false,
    val description: Boolean = false,
    val comfort: Boolean = true,
    val likes: Boolean = false,
    val birthday: Boolean = true,
    val wishlist: Boolean = false,
    val banner: Boolean = true,
    val groups: Boolean = false,
    val relations: Boolean = false,
    val fieldIds: List<String> = emptyList(),
    val history: Boolean = false,
    val safety: Boolean = false,
    val body: Boolean = false,
)

/** Uma pessoa de fora do sistema (parceria, amizade, família...) com seu próprio convite e permissões. */
data class CompanionProfile(
    val id: String,
    val name: String,
    val about: String = "", // quem é essa pessoa (para os membros saberem)
    val shareId: String,
    val key: String,
    val perms: CompanionPerms = CompanionPerms(),
    val createdAt: Long = System.currentTimeMillis(),
)

/** Item de dados do corpo (seção "geral", "saude", "contatos"...). */
data class BodyEntry(val id: String, val section: String, val label: String, val value: String)

/** Dados sensíveis criptografados (AES-GCM com chave derivada da senha). */
data class Vault(val salt: String, val iv: String, val data: String)

/** Backup automático criptografado no Firebase. O código é a única forma de restaurar em outro aparelho. */
data class CloudBackup(val code: String, val enabled: Boolean = true, val freq: String = "6h", val wifiOnly: Boolean = true)

/** Aparência de um perfil: chave "system", "guest" ou o id do membro. */
data class Appearance(
    val theme: String = "classic",
    val mode: String = "dark",           // só no tema clássico: dark | light | system
    val accent: Long? = null,            // null = cor do tema
    val textScale: Float = 1f,
    val titleFont: String = "theme",     // theme | sans | serif | cursive | mono
    val bodyFont: String = "sans",
    val cardShape: String = "theme",     // theme ou um id de CARD_SHAPES
    val avatarShape: String = "theme",   // theme ou um id de AVATAR_SHAPES
    val strip: String = "theme",         // cor do membro nos cartões: theme | left | top | border | glow | corner | none
    val border: String = "theme",        // contorno dos cartões: theme | none | solid | dashed | dotted | double | sketch | glow
    val decor: Boolean = true,           // enfeite embaixo da barra de título
    val headerMarks: Boolean = true,     // enfeite nos títulos de seção
    val pattern: Float = 1f,             // intensidade dos desenhos de fundo (0 = sem)
    val animate: Boolean = false,
    val wallpaper: String? = null,       // imagem de fundo (wallpapers/...)
    val wallpaperDim: Float = 0.55f,
    val followFront: Boolean = false,
    // cores próprias (null = cor do tema)
    val bg: Long? = null,                // fundo
    val bg2: Long? = null,               // segunda cor do fundo (degradê)
    val card: Long? = null,              // cartões e caixas
    val bars: Long? = null,              // barra de cima e de baixo
    val text: Long? = null,              // texto (null = automático, para dar leitura)
    val pronoun: Long? = null,           // pronomes e detalhes
    val chip: Long? = null,              // seleção, etiquetas e filtros
    val patternColor: Long? = null,      // desenhos do fundo e enfeites
    val memberTint: Float = 0f,          // quanto o cartão de cada membro pega a cor dele (0 = nada)    // (só no perfil Sistema) usar a aparência de quem está no front
)

data class AppData(
    val systemName: String = "",
    val systemDescription: String = "",
    val adminPassword: Secret? = null,
    val adminRecovery: Secret? = null,
    val members: List<Member> = emptyList(),
    val pendingMembers: List<Member> = emptyList(),
    val customFronts: List<CustomFront> = emptyList(),
    val groups: List<MemberGroup> = emptyList(),
    val fields: List<FieldDef> = emptyList(),
    val fronts: List<FrontEntry> = emptyList(),
    val chats: List<Chat> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val notices: List<Notice> = emptyList(),
    val welcome: List<WelcomeCard> = emptyList(),
    val notes: List<Note> = emptyList(),
    val polls: List<Poll> = emptyList(),
    val events: List<CalEvent> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val timers: List<FrontTimer> = emptyList(),
    val relations: List<Relation> = emptyList(),
    val safety: SafetyPlan = SafetyPlan(),
    val body: List<BodyEntry> = emptyList(),
    val vault: Vault? = null,
    val cloud: CloudConfig? = null,
    val companions: List<CompanionProfile> = emptyList(),
    val cloudBackup: CloudBackup? = null,
    val appearance: Map<String, Appearance> = emptyMap(),
) {
    val loginsEnabled get() = adminPassword != null
}

/** Algo que pode estar no front: um membro ou um custom front. */
data class Subject(
    val id: String,
    val name: String,
    val color: Long,
    val avatar: String?,
    val custom: Boolean,
    val pronouns: String = "",
)

fun Member.toSubject() = Subject(id, name, color, avatar, false, pronouns)
fun CustomFront.toSubject() = Subject(id, name, color, null, true)

fun AppData.subject(id: String?): Subject? {
    if (id == null) return null
    members.find { it.id == id }?.let { return it.toSubject() }
    customFronts.find { it.id == id }?.let { return it.toSubject() }
    return null
}

fun AppData.currentFronts() = fronts.filter { it.end == null }.sortedBy { it.start }

fun AppData.allRoles(): List<String> =
    (members.flatMap { it.roles() } + events.flatMap { it.roles })
        .distinctBy { it.lowercase() }.sortedBy { it.lowercase() }

fun AppData.chatTitle(c: Chat, me: String?): String = when {
    c.group -> c.name
    else -> c.memberIds.filter { it != me }.mapNotNull { id -> members.find { it.id == id }?.name }
        .joinToString(", ").ifBlank { c.name.ifBlank { "Conversa" } }
}

fun AppData.unreadIn(c: Chat, me: String?): Int {
    if (me == null) return 0
    val last = c.readBy[me] ?: 0L
    return messages.count { it.chatId == c.id && it.time > last && it.authorId != me }
}

fun AppData.unreadFor(memberId: String): Int =
    chats.filter { memberId in it.memberIds }.sumOf { unreadIn(it, memberId) }

fun AppData.descendants(groupId: String): Set<String> {
    val out = mutableSetOf<String>()
    fun walk(id: String) {
        groups.filter { it.parentId == id }.forEach { if (out.add(it.id)) walk(it.id) }
    }
    walk(groupId)
    return out
}

fun AppData.groupMemberIdsDeep(groupId: String): Set<String> {
    val ids = descendants(groupId) + groupId
    return groups.filter { it.id in ids }.flatMap { it.memberIds }.toSet()
}

/** Grupos em ordem de árvore, com profundidade. */
fun AppData.groupTree(): List<Pair<MemberGroup, Int>> {
    val out = mutableListOf<Pair<MemberGroup, Int>>()
    val seen = mutableSetOf<String>()
    fun walk(parent: String?, depth: Int) {
        groups.filter { it.parentId == parent }.sortedBy { it.name.lowercase() }.forEach {
            if (seen.add(it.id)) {
                out += it to depth
                walk(it.id, depth + 1)
            }
        }
    }
    walk(null, 0)
    groups.filter { it.id !in seen }.forEach {
        if (seen.add(it.id)) {
            out += it to 0
            walk(it.id, 1)
        }
    }
    return out
}

// ---------------- calendário (compartilhado com os lembretes) ----------------

private val zone: ZoneId get() = ZoneId.systemDefault()
fun Long.toDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

/** Aniversários dos membros viram eventos anuais (dd/mm ou dd/mm/aaaa). */
fun AppData.birthdayEvents(): List<CalEvent> = members.filter { !it.archived }.mapNotNull { m ->
    val p = m.birthday.trim().split('/', '-', '.').mapNotNull { it.trim().toIntOrNull() }
    if (p.size < 2) return@mapNotNull null
    val year = p.getOrNull(2)?.let { if (it < 100) 2000 + it else it } ?: 2000
    val date = runCatching { LocalDate.of(year, p[1], p[0]) }.getOrNull() ?: return@mapNotNull null
    CalEvent(
        "bday-${m.id}", "Aniversário de ${m.name}", start = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        allDay = true, memberIds = listOf(m.id), color = m.color, repeat = "yearly",
    )
}

fun CalEvent.occursOn(day: LocalDate): Boolean {
    val sd = start.toDate()
    if (day < sd) return false
    return when (repeat) {
        "weekly" -> ChronoUnit.DAYS.between(sd, day) % 7 == 0L
        "monthly" -> day.dayOfMonth == sd.dayOfMonth
        "yearly" -> day.month == sd.month && day.dayOfMonth == sd.dayOfMonth
        else -> day <= (end?.toDate()?.takeIf { it >= sd } ?: sd)
    }
}
