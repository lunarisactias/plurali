package com.plurali.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.Alignment
import com.plurali.app.data.AppData
import com.plurali.app.data.Json
import com.plurali.app.data.Prefs
import com.plurali.app.data.Store
import com.plurali.app.sys.AutoBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun MoreScreen(nav: NavHostController) {
    val d by Store.data.collectAsState()
    val who = LocalWho.current
    var editSystem by remember { mutableStateOf(false) }
    ScreenScaffold("Sistema e configurações") { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            ElevatedCard(Modifier.fillMaxWidth().padding(16.dp).clickable(enabled = who.isAdmin) { editSystem = true }) {
                Column(Modifier.padding(16.dp)) {
                    Text(d.systemName.ifBlank { "Nosso sistema" }, style = MaterialTheme.typography.titleLarge)
                    Text(
                        d.systemDescription.ifBlank { "Toque para dar nome e descrição ao sistema." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${d.members.count { !it.archived }} membros · ${d.groups.size} grupos · ${d.fronts.size} registros de front",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            MoreItem(
                Icons.Filled.Security, "Segurança e logins",
                if (d.loginsEnabled) "Logins ativados · senhas do sistema e dos membros" else "Desativado · ative para ter login de sistema, de cada membro e visitante",
            ) { nav.go("security") }
            MoreItem(Icons.Filled.Palette, "Aparência", "Temas (gatos, jardim, inferno, fadas…), formatos, cores, letras e fundo, por perfil") { nav.go("appearance") }
            MoreItem(Icons.Filled.Notifications, "Notificações e atalhos", "Notificação fixa de front, widget, atalho rápido, avisos") { nav.go("notifications") }
            MoreItem(Icons.Filled.Alarm, "Lembretes e timers", "Remédios, rotina e timers de front") { nav.go("reminders") }
            HorizontalDivider()
            if (who.isAdmin) MoreItem(Icons.Filled.AccountTree, "Pastas", "Grupos e subsistemas") { nav.go("groups") }
            MoreItem(Icons.Filled.BlurOn, "Custom fronts", "Estados como “blurry” ou “dissociado”") { nav.go("customFronts") }
            MoreItem(Icons.Filled.Tune, "Campos personalizados", "Campos extras no perfil dos membros") { nav.go("fields") }
            if (who.isAdmin) {
                HorizontalDivider()
                MoreItem(
                    Icons.Filled.Backup, "Backup e restauração",
                    if (Prefs.backupFreq != "off" && Prefs.backupUri != null) "Backup automático ligado" else "Exportar, importar e backup automático",
                ) { nav.go("backup") }
            }
            Text(
                "Plurali 1.2 · seus dados ficam só neste aparelho (e nos backups que você configurar).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
    if (editSystem) {
        var name by remember { mutableStateOf(d.systemName) }
        var desc by remember { mutableStateOf(d.systemDescription) }
        AlertDialog(
            onDismissRequest = { editSystem = false },
            title = { Text("Sistema") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nome do sistema") }, singleLine = true)
                    OutlinedTextField(desc, { desc = it }, label = { Text("Descrição") }, minLines = 3)
                }
            },
            confirmButton = { TextButton(onClick = { Store.setSystem(name.trim(), desc.trim()); editSystem = false }) { Text("Salvar") } },
            dismissButton = { TextButton(onClick = { editSystem = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun MoreItem(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(sub) },
    )
}

@Composable
fun BackupScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Pair<String, AppData>?>(null) }

    val exportL = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(Store.exportJson().toByteArray()) }
                }.isSuccess
            }
            msg = if (ok) "Backup salvo com sucesso." else "Erro ao salvar o backup."
        }
    }
    val importL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val text = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                    text to Json.decode(text)
                }
            }
            r.onSuccess { pending = it }.onFailure { msg = "Arquivo inválido: não é um backup do Plurali." }
        }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            Prefs.putBackupUri(uri.toString())
            if (Prefs.backupFreq == "off") Prefs.putBackupFreq("daily")
            AutoBackup.schedule(ctx)
            scope.launch {
                val err = withContext(Dispatchers.IO) { AutoBackup.runNow(ctx) }
                msg = err?.let { "Erro no primeiro backup automático: $it" } ?: "Pasta escolhida. Primeiro backup automático feito."
            }
        }
    }

    ScreenScaffold("Backup e restauração", onBack = { nav.popBackStack() }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CloudBackupCard()
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Arquivo de backup", style = MaterialTheme.typography.titleMedium)
            Text(
                "Também dá para salvar um arquivo e guardar onde quiser (Google Drive, PC, etc.). " +
                    "O backup inclui membros, fotos, histórico, mensagens, notas, enquetes e a aparência de cada perfil.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { exportL.launch("plurali-backup-${LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Upload, null); Spacer(Modifier.width(8.dp)); Text("Exportar backup")
            }
            OutlinedButton(onClick = { importL.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Download, null); Spacer(Modifier.width(8.dp)); Text("Restaurar de um backup")
            }
            msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Backup automático", style = MaterialTheme.typography.titleMedium)
            Text(
                "O app grava sozinho uma cópia na pasta escolhida e guarda só as mais recentes. " +
                    "Para ter cópia fora do celular, escolha uma pasta que você sincroniza com a nuvem (ex.: pela Google Drive ou outro app de sincronização).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { pickFolder.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Folder, null); Spacer(Modifier.width(8.dp))
                Text(Prefs.backupUri?.let { "Pasta: " + Uri.decode(it).substringAfterLast(':').ifBlank { "escolhida" } } ?: "Escolher pasta")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("off" to "Desligado", "daily" to "Todo dia", "weekly" to "Toda semana").forEach { (k, l) ->
                    FilterChip(Prefs.backupFreq == k, {
                        Prefs.putBackupFreq(k); AutoBackup.schedule(ctx)
                    }, label = { Text(l) }, enabled = Prefs.backupUri != null || k == "off")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Manter os últimos", Modifier.weight(1f))
                listOf(5, 10, 30).forEach { n ->
                    FilterChip(Prefs.backupKeep == n, { Prefs.putBackupKeep(n) }, label = { Text("$n") }, modifier = Modifier.padding(start = 6.dp))
                }
            }
            Text(
                when {
                    Prefs.lastBackupError != null -> "Último backup automático falhou: ${Prefs.lastBackupError}"
                    Prefs.lastBackup > 0 -> "Último backup automático: ${fmtDateTime(Prefs.lastBackup)}"
                    else -> "Nenhum backup automático feito ainda."
                },
                color = if (Prefs.lastBackupError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (Prefs.backupUri != null) TextButton(onClick = {
                scope.launch {
                    val err = withContext(Dispatchers.IO) { AutoBackup.runNow(ctx) }
                    msg = err?.let { "Erro: $it" } ?: "Backup automático feito agora."
                }
            }) { Text("Fazer um backup automático agora") }
        }
    }
    pending?.let { (text, d) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Restaurar backup?") },
            text = {
                Text(
                    "O backup tem ${d.members.size} membros, ${d.fronts.size} registros de front, ${d.messages.size} mensagens," +
                        "${d.notes.size} notas e ${d.polls.size} enquetes.\n\nIsso SUBSTITUI todos os dados atuais do app.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { runCatching { Store.importJson(text) }.isSuccess }
                        msg = if (ok) "Backup restaurado." else "Erro ao restaurar."
                    }
                }) { Text("Substituir", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun SecurityScreen(nav: NavHostController) {
    val d by Store.data.collectAsState()
    val who = LocalWho.current
    val scope = rememberCoroutineScope()
    var enable by remember { mutableStateOf(false) }
    var change by remember { mutableStateOf(false) }
    var confirmOld by remember { mutableStateOf<String?>(null) } // "change" | "disable"
    var myPw by remember { mutableStateOf(false) }
    var memberPw by remember { mutableStateOf<com.plurali.app.data.Member?>(null) }
    var showCode by remember { mutableStateOf<String?>(null) }

    fun save(set: (com.plurali.app.data.Secret) -> Unit, pw: String) {
        scope.launch {
            val s = withContext(Dispatchers.Default) { com.plurali.app.data.Crypto.hash(pw) }
            set(s)
        }
    }

    ScreenScaffold("Segurança e logins", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!d.loginsEnabled) {
                Text("Os logins estão desativados: qualquer pessoa que abrir o app vê e edita tudo.")
                Text(
                    "Ao ativar, o app passa a abrir numa tela de login com:\n" +
                        "• Sistema (admin): vê e edita tudo, cria as senhas dos membros.\n" +
                        "• Cada membro, com a própria senha: edita o próprio perfil e só vê as próprias conversas e notas.\n" +
                        "• Entrar sem login: só informações gerais, sem editar (para quem acabou de chegar).",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { enable = true }) { Icon(Icons.Filled.Lock, null); Spacer(Modifier.width(8.dp)); Text("Ativar logins (criar senha do sistema)") }
            } else {
                Text("Logins ativados.", style = MaterialTheme.typography.titleMedium)
                Text("Conectado como: ${whoLabel(d, who)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "O app volta para a tela de login depois de 5 minutos fora dele.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (who.isAdmin) {
                    OutlinedButton(onClick = { confirmOld = "change" }) { Text("Trocar senha do sistema") }
                    OutlinedButton(onClick = { confirmOld = "code" }) { Text("Gerar novo código de recuperação") }
                    Text(
                        if (d.adminRecovery != null) "Existe um código de recuperação. Gerar outro invalida o anterior."
                        else "Sem código de recuperação: se a senha do sistema for esquecida, não há como recuperar. Gere um!",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (d.adminRecovery != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = { confirmOld = "disable" }) { Text("Desativar logins") }
                    SectionHeader("Senhas dos membros")
                    d.members.filter { !it.archived }.sortedBy { it.name.lowercase() }.forEach { m ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Avatar(m.name, m.color, m.avatar, 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.name)
                                Text(if (m.password != null) "Com senha" else "Sem senha (não consegue entrar)",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { memberPw = m }) { Text(if (m.password == null) "Criar" else "Trocar") }
                            if (m.password != null) TextButton(onClick = { Store.setMemberPassword(m.id, null) }) {
                                Text("Remover", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                } else if (who.meId != null) {
                    OutlinedButton(onClick = { myPw = true }) { Text("Trocar minha senha") }
                }
            }
        }
    }
    if (enable) NewPasswordDialog(
        "Senha do sistema",
        warning = "É a senha de administração. Guarde bem: se esquecer, não dá para entrar como Sistema.",
        onDismiss = { enable = false },
    ) { pw ->
        Session.who = Who.Admin // continua dentro do app como Sistema
        scope.launch {
            val s = withContext(Dispatchers.Default) { com.plurali.app.data.Crypto.hash(pw) }
            Store.setAdminPassword(s)
            showCode = regenerateRecovery()
        }
    }
    confirmOld?.let { what ->
        PasswordDialog(
            title = "Confirme a senha do sistema",
            check = { com.plurali.app.data.Crypto.verify(it, d.adminPassword) },
            onDismiss = { confirmOld = null },
        ) {
            when (what) {
                "disable" -> { Store.setAdminPassword(null); Store.setAdminRecovery(null); Session.who = Who.Open }
                "code" -> scope.launch { showCode = regenerateRecovery() }
                else -> change = true
            }
            confirmOld = null
        }
    }
    showCode?.let { c -> RecoveryCodeDialog(c) { showCode = null } }
    if (change) NewPasswordDialog("Nova senha do sistema", onDismiss = { change = false }) { pw -> save({ Store.setAdminPassword(it) }, pw) }
    if (myPw) who.meId?.let { me ->
        NewPasswordDialog("Minha nova senha", onDismiss = { myPw = false }) { pw -> save({ Store.setMemberPassword(me, it) }, pw) }
    }
    memberPw?.let { m ->
        NewPasswordDialog("Senha de ${m.name}", onDismiss = { memberPw = null }) { pw -> save({ Store.setMemberPassword(m.id, it) }, pw) }
    }
}
