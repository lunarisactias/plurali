package com.plurali.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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

@Composable
fun LoginScreen(d: AppData, onSafety: () -> Unit, onWelcome: () -> Unit) {
    var asking by remember { mutableStateOf<Who?>(null) }
    var forgot by remember { mutableStateOf(false) }
    var newPw by remember { mutableStateOf(false) }
    var showCode by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val withPw = d.members.filter { !it.archived && it.password != null }.sortedBy { it.name.lowercase() }
    val noPw = d.members.count { !it.archived && it.password == null }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground, contentWindowInsets = WindowInsets.safeDrawing) { pad ->
        com.plurali.app.ui.theme.ThemedBackground(Modifier.fillMaxSize())
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Spacer(Modifier.height(24.dp))
                Text(d.systemName.ifBlank { "Plurali" }, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text("Quem está usando?", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Spacer(Modifier.height(8.dp))
            }
            item {
                SpRow(0xFFD6AC5E, Modifier.fillMaxWidth(), onClick = { asking = Who.Admin }) {
                    Icon(Icons.Filled.AdminPanelSettings, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    NameAndPronouns("Sistema", "", Modifier.weight(1f), extra = "Administração: vê e edita tudo")
                    Icon(Icons.Filled.Lock, null)
                }
            }
            items(withPw, key = { it.id }) { m ->
                SpRow(m.color, Modifier.fillMaxWidth(), onClick = { asking = Who.Member(m.id) }) {
                    Avatar(m.name, m.color, m.avatar, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    NameAndPronouns(m.name, m.pronouns, Modifier.weight(1f))
                    Icon(Icons.Filled.Lock, null)
                }
            }
            if (noPw > 0) item {
                Text(
                    "$noPw membro(s) ainda sem senha. Entre como Sistema e crie a senha no perfil de cada um.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp),
                )
            }
            item {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { Session.who = Who.Guest }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Visibility, null); Spacer(Modifier.width(8.dp)); Text("Entrar sem login (informações gerais)")
                }
                Text(
                    "Para quem acabou de chegar: mostra os membros, o mural, o calendário e os dados gerais do corpo, sem poder editar. " +
                        "Lá dentro tem também “Sou novo aqui”, para se apresentar ao sistema.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp),
                )
                if (d.adminRecovery != null) TextButton(onClick = { forgot = true }) { Text("Esqueci a senha do sistema") }
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
