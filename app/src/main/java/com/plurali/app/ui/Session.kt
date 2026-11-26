package com.plurali.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.plurali.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Quem está usando o app agora. */
sealed interface Who {
    /** Logins desativados: tudo liberado, como um app comum. */
    data object Open : Who
    /** Sistema (administração). */
    data object Admin : Who
    data class Member(val id: String) : Who
    /** Sem login: só informações gerais, somente leitura. */
    data object Guest : Who
}

val Who.isAdmin get() = this is Who.Open || this is Who.Admin
val Who.canWrite get() = this !is Who.Guest
val Who.meId: String? get() = (this as? Who.Member)?.id
fun Who.canEditMember(id: String) = isAdmin || meId == id
/** Notas e wishlist: só a própria pessoa e o sistema. */
fun Who.canSeePrivateOf(id: String) = isAdmin || meId == id
/** Conversas são privadas: só participantes (ou tudo no modo sem logins). */
fun Who.canSeeChat(c: Chat) = this is Who.Open || (meId != null && meId in c.memberIds)

/** Visibilidade de campo personalizado: todos / só membros com login / só a pessoa e o sistema. */
fun Who.canSeeField(f: FieldDef, memberId: String) = when (f.visibility) {
    "members" -> canWrite
    "private" -> canSeePrivateOf(memberId)
    else -> true
}

object Session {
    var who by mutableStateOf<Who?>(null)
    var stoppedAt = 0L
    const val LOCK_AFTER_MS = 5 * 60_000L
    /** Desde quando mostrar "O que eu perdi?" para quem acabou de entrar. */
    var missedSince by mutableStateOf<Long?>(null)
    /** Tela a abrir depois do login (vinda de uma notificação). */
    var pendingRoute by mutableStateOf<String?>(null)
}

// ---------------- código de recuperação da senha do sistema ----------------

private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

fun newRecoveryCode(): String {
    val r = java.security.SecureRandom()
    return (1..16).map { CODE_CHARS[r.nextInt(CODE_CHARS.length)] }.chunked(4).joinToString("-") { it.joinToString("") }
}

fun normalizeCode(s: String) = s.uppercase().filter { it.isLetterOrDigit() }

/** Mostra o código uma única vez para a pessoa anotar. */
@Composable
fun RecoveryCodeDialog(code: String, onDismiss: () -> Unit) {
    var ok by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Código de recuperação") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Se esquecer a senha do sistema, este código permite criar uma nova. Anote em papel ou guarde num lugar seguro. Ele só aparece agora.")
                Text(code, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().clickable { ok = !ok }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(ok, { ok = it }); Text("Anotei o código")
                }
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = onDismiss) { Text("Pronto") } },
    )
}

/** Gera um novo código, salva o hash e devolve o código para mostrar. */
suspend fun regenerateRecovery(): String {
    val code = newRecoveryCode()
    val s = withContext(Dispatchers.Default) { Crypto.hash(normalizeCode(code)) }
    Store.setAdminRecovery(s)
    return code
}

val LocalWho = staticCompositionLocalOf<Who> { Who.Open }

fun whoLabel(d: AppData, w: Who): String = when (w) {
    Who.Open -> "Sem logins"
    Who.Admin -> "Sistema (admin)"
    Who.Guest -> "Visitante"
    is Who.Member -> d.members.find { it.id == w.id }?.name ?: "?"
}

@Composable
fun PasswordField(value: String, onChange: (String) -> Unit, label: String, onDone: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        trailingIcon = {
            IconButton(onClick = { show = !show }) {
                Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (show) "Esconder senha" else "Mostrar senha")
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/** Pede a senha e confere em segundo plano (PBKDF2 é propositalmente lento). */
@Composable
fun PasswordDialog(title: String, subtitle: String = "", check: (String) -> Boolean, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun go() {
        if (pw.isEmpty() || busy) return
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.Default) { check(pw) }
            busy = false
            if (ok) onOk(pw) else error = true
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                PasswordField(pw, { pw = it; error = false }, "Senha", onDone = { go() })
                if (error) Text("Senha incorreta.", color = MaterialTheme.colorScheme.error)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = pw.isNotEmpty() && !busy, onClick = { go() }) { Text("Entrar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Criar/alterar senha (digitar duas vezes). */
@Composable
fun NewPasswordDialog(title: String, warning: String = "", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val tooShort = a.isNotEmpty() && a.length < 4
    val mismatch = b.isNotEmpty() && a != b
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (warning.isNotBlank()) Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PasswordField(a, { a = it }, "Nova senha")
                PasswordField(b, { b = it }, "Repita a senha")
                if (tooShort) Text("Use pelo menos 4 caracteres.", color = MaterialTheme.colorScheme.error)
                if (mismatch) Text("As senhas não são iguais.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = a.length >= 4 && a == b, onClick = { onSave(a); onDismiss() }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/**
 * Tela de entrada em duas etapas, para não confundir quem acabou de chegar:
 *  1) início: Login do Sistema · Entrar sem login · Entrar com meu perfil
 *  2) lista de perfis (só ao escolher "Entrar com meu perfil"), com aviso de mensagens novas.
 */
@Composable
fun LoginScreen(d: AppData, onSafety: () -> Unit, onWelcome: () -> Unit) {
    var asking by remember { mutableStateOf<Who?>(null) }
    var forgot by remember { mutableStateOf(false) }
    var newPw by remember { mutableStateOf(false) }
    var showCode by remember { mutableStateOf<String?>(null) }
    var step by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("home") }
    var q by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val withPw = d.members.filter { !it.archived && it.password != null }.sortedBy { it.name.lowercase() }
    val noPw = d.members.count { !it.archived && it.password == null }
    val unread = withPw.associate { it.id to d.unreadFor(it.id) }
    val withNews = unread.count { it.value > 0 }
    androidx.activity.compose.BackHandler(enabled = step != "home") { step = "home"; q = "" }

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground, contentWindowInsets = WindowInsets.safeDrawing) { pad ->
        com.plurali.app.ui.theme.AppBackground(Modifier.fillMaxSize())
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (step == "home") {
                item {
                    Spacer(Modifier.height(24.dp))
                    Text(d.systemName.ifBlank { "Plurali" }, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Text("Como você quer entrar?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    if (d.welcome.isNotEmpty()) {
                        Button(onClick = onWelcome, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.WavingHand, null); Spacer(Modifier.width(8.dp)); Text("Chegou agora? Leia isto primeiro")
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    FilledTonalButton(onClick = onSafety, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.HealthAndSafety, null); Spacer(Modifier.width(8.dp)); Text("Preciso de ajuda · Plano de segurança")
                    }
                    Spacer(Modifier.height(10.dp))
                }
                item {
                    EntryOption(Icons.Filled.AdminPanelSettings, "Login do Sistema", "Administração: vê e edita tudo") { asking = Who.Admin }
                }
                item {
                    EntryOption(Icons.Filled.Visibility, "Entrar sem login", "Informações gerais, sem poder editar. Bom para quem acabou de chegar.") {
                        Session.who = Who.Guest
                    }
                }
                item {
                    EntryOption(
                        Icons.Filled.Person, "Entrar com meu perfil",
                        if (withPw.isEmpty()) "Ninguém tem senha ainda. Entre como Sistema e crie a senha no perfil de cada um."
                        else "Escolher quem é você na lista (${withPw.size} ${if (withPw.size == 1) "perfil" else "perfis"})",
                        badge = withNews.takeIf { it > 0 }?.let { if (it == 1) "1 pessoa com mensagem nova" else "$it pessoas com mensagens novas" },
                        enabled = withPw.isNotEmpty(),
                    ) { step = "profiles" }
                }
                item {
                    if (d.adminRecovery != null) TextButton(onClick = { forgot = true }) { Text("Esqueci a senha do sistema") }
                }
            } else {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { step = "home"; q = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                        Text("Quem é você?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    }
                    if (withPw.size > 6) OutlinedTextField(
                        q, { q = it }, placeholder = { Text("Buscar nome") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, null) }, modifier = Modifier.fillMaxWidth(),
                    )
                }
                val list = withPw.filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }
                items(list, key = { it.id }) { m ->
                    val n = unread[m.id] ?: 0
                    SpRow(m.color, Modifier.fillMaxWidth(), onClick = { asking = Who.Member(m.id) }) {
                        Box {
                            Avatar(m.name, m.color, m.avatar, 48.dp)
                            if (n > 0) Box(
                                Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(16.dp)
                                    .background(MaterialTheme.colorScheme.error, androidx.compose.foundation.shape.CircleShape)
                                    .border(2.dp, MaterialTheme.colorScheme.surfaceContainer, androidx.compose.foundation.shape.CircleShape),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        NameAndPronouns(m.name, m.pronouns, Modifier.weight(1f), extra = if (n > 0) (if (n == 1) "1 mensagem nova" else "$n mensagens novas") else "")
                        if (n > 0) Text(
                            if (n > 99) "99+" else "$n",
                            color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.background(MaterialTheme.colorScheme.error, androidx.compose.foundation.shape.RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.Lock, null)
                    }
                }
                if (list.isEmpty()) item { Text("Ninguém encontrado.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (noPw > 0) item {
                    Text(
                        "$noPw membro(s) ainda sem senha. Entre como Sistema e crie a senha no perfil de cada um.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
    if (forgot) PasswordDialog(
        title = "Código de recuperação",
        subtitle = "Digite o código que apareceu quando os logins foram ativados (ex.: ABCD-EFGH-…).",
        check = { Crypto.verify(normalizeCode(it), d.adminRecovery) },
        onDismiss = { forgot = false },
    ) { forgot = false; newPw = true }
    if (newPw) NewPasswordDialog("Nova senha do sistema", onDismiss = { newPw = false }) { pw ->
        scope.launch {
            val s = withContext(Dispatchers.Default) { Crypto.hash(pw) }
            Store.setAdminPassword(s)
            showCode = regenerateRecovery() // o código antigo deixa de valer
        }
    }
    showCode?.let { c -> RecoveryCodeDialog(c) { showCode = null; Session.who = Who.Admin } }
    asking?.let { w ->
        val secret = when (w) {
            Who.Admin -> d.adminPassword
            is Who.Member -> d.members.find { it.id == w.id }?.password
            else -> null
        }
        PasswordDialog(
            title = "Entrar como ${whoLabel(d, w)}",
            check = { Crypto.verify(it, secret) },
            onDismiss = { asking = null },
        ) { Session.who = w; asking = null }
    }
}

/** Opção grande da tela de entrada. */
@Composable
private fun EntryOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    sub: String,
    badge: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().themed()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).background(
                if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.shapes.medium,
            ),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (badge != null) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.error, androidx.compose.foundation.shape.CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(badge, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
        }
        if (enabled) Icon(Icons.Filled.ChevronRight, null)
    }
}
