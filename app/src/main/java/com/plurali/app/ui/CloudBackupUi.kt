package com.plurali.app.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plurali.app.data.*
import com.plurali.app.sys.CloudBackupSync
import com.plurali.app.sys.DriveBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun mb(bytes: Long) = if (bytes < 1_000_000) "%.0f KB".format(bytes / 1000.0) else "%.1f MB".format(bytes / 1_000_000.0)

private fun copy(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Código do backup", text))
}

/**
 * Faz algo que precisa do Google Drive: pega a autorização (abrindo a tela do Google na primeira vez)
 * e então roda [action] fora da thread principal com o token.
 */
class DriveAccess(private val run: (suspend (String) -> Unit, (String) -> Unit) -> Unit) {
    /** [onError] recebe a mensagem de erro (já em português). */
    fun use(onError: (String) -> Unit, action: suspend (token: String) -> Unit) = run(action, onError)
}

@Composable
fun rememberDriveAccess(): DriveAccess {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pair<suspend (String) -> Unit, (String) -> Unit>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val p = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (r.resultCode != Activity.RESULT_OK) { p.second("Autorização do Google Drive cancelada."); return@rememberLauncherForActivityResult }
        scope.launch {
            val res = withContext(Dispatchers.IO) { runCatching { DriveBackup.tokenFromResult(ctx, r.data) } }
            res.onSuccess { t -> runCatching { p.first(t) }.onFailure { p.second(it.message ?: "Erro") } }
                .onFailure { p.second(it.message ?: "Erro") }
        }
    }
    return remember {
        DriveAccess { action, onError ->
            scope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { DriveBackup.token(ctx) } }
                r.onSuccess { t -> runCatching { action(t) }.onFailure { onError(it.message ?: "Erro") } }
                    .onFailure { e ->
                        if (e is DriveBackup.NeedsConsent) {
                            pending = action to onError
                            launcher.launch(IntentSenderRequest.Builder(e.intent.intentSender).build())
                        } else onError(e.message ?: "Erro")
                    }
            }
        }
    }
}

/** Cartão "Backup na nuvem" da tela de backup. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloudBackupCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val d by Store.data.collectAsState()
    val b = d.cloudBackup
    val drive = rememberDriveAccess()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var showCode by remember { mutableStateOf(false) }
    var newCodeDialog by remember { mutableStateOf<String?>(null) }
    var confirmRotate by remember { mutableStateOf(false) }
    var confirmOff by remember { mutableStateOf(false) }
    var restore by remember { mutableStateOf(false) }
    val onDrive = b?.target == "drive"

    fun fail(e: String) { busy = false; msg = "Erro: $e" }

    /** Envia agora (no Drive, garante a autorização antes). */
    fun upload(force: Boolean, then: suspend () -> Unit = {}) {
        busy = true; msg = "Enviando…"
        val target = Store.data.value.cloudBackup?.target
        if (target == "drive") drive.use(::fail) {
            val err = withContext(Dispatchers.IO) { CloudBackupSync.runNow(ctx, force) }
            busy = false
            msg = err?.let { "Erro: $it" } ?: "Backup enviado ao seu Google Drive."
            if (err == null) then()
        } else scope.launch {
            val err = withContext(Dispatchers.IO) { CloudBackupSync.runNow(ctx, force) }
            busy = false
            msg = err?.let { "Erro: $it" } ?: "Backup enviado."
        }
    }

    Column(
        Modifier.fillMaxWidth().themed().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CloudUpload, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text("Backup na nuvem", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (b != null) Text(if (b.enabled) "Ligado" else "Pausado", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
        Text(
            "Uma cópia de tudo (com fotos) vai sozinha para uma pasta oculta do seu Google Drive, criptografada com um código que só vocês têm. " +
                "Não aparece entre seus arquivos e só o Plurali consegue abrir. Se perder ou trocar de celular, é só instalar o Plurali e restaurar.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (b == null) {
            Button(enabled = !busy, onClick = {
                busy = true; msg = null
                drive.use(::fail) {
                    val code = CloudBackupSync.newCode()
                    Store.setCloudBackup(CloudBackup(code, target = "drive"))
                    CloudBackupSync.schedule(ctx)
                    newCodeDialog = code
                    upload(force = true)
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.AddToDrive, null); Spacer(Modifier.width(8.dp)); Text("Ativar backup no Google Drive")
            }
        } else {
            if (!onDrive) {
                // backups antigos ficavam no Firebase do Plurali (espaço dividido entre todo mundo)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Seu backup ainda está no Firebase do Plurali", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Passe para o seu Google Drive: o espaço é seu (não dividido com outros sistemas) e o código continua o mesmo. " +
                            "Depois do primeiro envio, a cópia no Firebase é apagada.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(enabled = !busy, onClick = {
                        busy = true; msg = null
                        drive.use(::fail) {
                            val old = Store.data.value.cloudBackup ?: return@use
                            Store.setCloudBackup(old.copy(target = "drive"))
                            Prefs.forgetCloudBackup()
                            CloudBackupSync.schedule(ctx)
                            upload(force = true) {
                                withContext(Dispatchers.IO) { CloudBackupSync.deleteRemote(old.code) }
                                msg = "Pronto: o backup agora fica no seu Google Drive."
                            }
                        }
                    }) { Icon(Icons.Filled.AddToDrive, null); Spacer(Modifier.width(8.dp)); Text("Mudar para o Google Drive") }
                }
            }
            // estado do último envio
            Text(
                when {
                    Prefs.cloudBackupError != null -> "Último envio falhou: ${Prefs.cloudBackupError}"
                    Prefs.lastCloudBackup > 0 -> "Último backup: ${fmtDateTime(Prefs.lastCloudBackup)}" +
                        (if (Prefs.cloudBackupSize > 0) " · ${mb(Prefs.cloudBackupSize.toLong())}" else "") +
                        (if (onDrive) " · Google Drive" else " · Firebase")
                    else -> "Ainda não enviado."
                },
                color = if (Prefs.cloudBackupError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )

            // código
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(10.dp)) {
                Text("Código do backup", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (showCode) CloudBackupSync.pretty(b.code) else "•••• •••• •••• •••• ••••",
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showCode = !showCode }) {
                        Icon(if (showCode) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (showCode) "Esconder" else "Mostrar")
                    }
                    IconButton(onClick = { copy(ctx, CloudBackupSync.pretty(b.code)); msg = "Código copiado." }) { Icon(Icons.Filled.ContentCopy, "Copiar") }
                }
                Text(
                    "Anote fora do celular (papel, gerenciador de senhas). Sem ele não dá para abrir o backup.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text("Frequência", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("1h" to "A cada hora", "6h" to "A cada 6 horas", "24h" to "Todo dia").forEach { (k, l) ->
                    FilterChip(b.freq == k, { Store.setCloudBackup(b.copy(freq = k)); CloudBackupSync.schedule(ctx) }, label = { Text(l) })
                }
            }
            Row(Modifier.fillMaxWidth().clickable {
                Store.setCloudBackup(b.copy(wifiOnly = !b.wifiOnly)); CloudBackupSync.schedule(ctx)
            }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Só no Wi‑Fi")
                    Text("Não gasta seus dados móveis. Só envia quando algo mudou.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(b.wifiOnly, { Store.setCloudBackup(b.copy(wifiOnly = it)); CloudBackupSync.schedule(ctx) })
            }
            Row(Modifier.fillMaxWidth().clickable {
                Store.setCloudBackup(b.copy(enabled = !b.enabled)); CloudBackupSync.schedule(ctx)
            }, verticalAlignment = Alignment.CenterVertically) {
                Text("Backup automático ligado", Modifier.weight(1f))
                Switch(b.enabled, { Store.setCloudBackup(b.copy(enabled = it)); CloudBackupSync.schedule(ctx) })
            }
            Button(enabled = !busy, onClick = { upload(force = true) }, modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.CloudUpload, null)
                Spacer(Modifier.width(8.dp)); Text("Enviar backup agora")
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { confirmRotate = true }) { Text("Gerar código novo") }
                TextButton(onClick = { confirmOff = true }) { Text("Desativar", color = MaterialTheme.colorScheme.error) }
            }
        }
        OutlinedButton(enabled = !busy, onClick = { restore = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.CloudDownload, null); Spacer(Modifier.width(8.dp)); Text("Restaurar da nuvem")
        }
        msg?.let { Text(it, color = if (it.startsWith("Erro")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
    }

    newCodeDialog?.let { code ->
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Filled.Key, null) },
            title = { Text("Anote este código") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("É a única forma de abrir o backup num celular novo. Guarde fora deste celular.")
                    Text(
                        CloudBackupSync.pretty(code), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, softWrap = false,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
                    )
                    TextButton(onClick = { copy(ctx, CloudBackupSync.pretty(code)) }) {
                        Icon(Icons.Filled.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Copiar")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { newCodeDialog = null }) { Text("Já anotei") } },
        )
    }

    if (confirmRotate && b != null) ConfirmDialog(
        "Gerar código novo?",
        "O código atual deixa de funcionar e as cópias antigas são apagadas da nuvem. Um backup novo é enviado na hora com o código novo.",
        confirm = "Gerar",
        onDismiss = { confirmRotate = false },
    ) {
        confirmRotate = false
        val old = b
        val code = CloudBackupSync.newCode()
        Store.setCloudBackup(b.copy(code = code, target = "drive"))
        Prefs.forgetCloudBackup()
        newCodeDialog = code
        busy = true
        drive.use(::fail) {
            withContext(Dispatchers.IO) { if (old.target == "drive") CloudBackupSync.deleteDrive(ctx) else CloudBackupSync.deleteRemote(old.code) }
            upload(force = true)
        }
    }

    if (confirmOff && b != null) ConfirmDialog(
        "Desativar o backup na nuvem?",
        if (onDrive) "As cópias guardadas no seu Google Drive serão apagadas. Os dados deste celular não mudam."
        else "As cópias guardadas no Firebase serão apagadas. Os dados deste celular não mudam.",
        confirm = "Desativar",
        onDismiss = { confirmOff = false },
    ) {
        confirmOff = false
        val old = b
        Store.setCloudBackup(null)
        Prefs.forgetCloudBackup()
        CloudBackupSync.schedule(ctx)
        scope.launch {
            withContext(Dispatchers.IO) { if (old.target == "drive") CloudBackupSync.deleteDrive(ctx) else CloudBackupSync.deleteRemote(old.code) }
            msg = "Backup na nuvem desativado."
        }
    }

    if (restore) RestoreCloudDialog(initial = b?.code ?: "", drive = drive, onDismiss = { restore = false }) { restore = false; msg = it }
}

/** Uma cópia para escolher, venha do Drive ou do Firebase antigo. */
private data class Copy(val id: String, val time: Long, val size: Long, val members: Int, val device: String)

/** Escolher de onde → ver as cópias → código → confirmar → restaurar. */
@Composable
private fun RestoreCloudDialog(initial: String, drive: DriveAccess, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<String?>(null) } // "drive" | "firebase"
    var token by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf(if (initial.isNotBlank()) CloudBackupSync.pretty(initial) else "") }
    var copies by remember { mutableStateOf<List<Copy>?>(null) }
    var pick by remember { mutableStateOf<Copy?>(null) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    fun openDrive() {
        source = "drive"; busy = true; err = null
        drive.use({ busy = false; err = it }) { t ->
            val l = withContext(Dispatchers.IO) { CloudBackupSync.driveFiles(t) }
            token = t; busy = false
            if (l.isEmpty()) err = "Nenhum backup do Plurali neste Google Drive. Confira se escolheu a mesma conta Google."
            else { copies = l.map { Copy(it.id, it.time, it.size, it.members, it.device) }; pick = copies?.firstOrNull() }
        }
    }

    fun findFirebase() {
        busy = true; err = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { CloudBackupSync.slots(code) } }
            busy = false
            r.onSuccess { l ->
                if (l.isEmpty()) err = "Nenhum backup encontrado com esse código."
                else { copies = l.map { Copy(it.id, it.time, it.size.toLong(), it.members, it.device) }; pick = copies?.firstOrNull() }
            }.onFailure { err = "Não consegui buscar: ${it.message}" }
        }
    }

    fun doRestore() {
        val s = pick ?: return
        busy = true; err = null
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val text = if (source == "drive") CloudBackupSync.downloadDrive(token!!, s.id, code) else CloudBackupSync.download(code, s.id)
                    CloudBackupSync.apply(ctx, text)
                }
            }
            busy = false
            r.onSuccess { onDone("Backup de ${fmtDateTime(s.time)} restaurado.") }
                .onFailure { err = it.message ?: "Erro ao restaurar" }
        }
    }

    val codeOk = CloudBackupSync.isValid(code)
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Filled.CloudDownload, null) },
        title = { Text("Restaurar da nuvem") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    source == null -> {
                        Text("De onde vem o backup?")
                        Button(onClick = { openDrive() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.AddToDrive, null); Spacer(Modifier.width(8.dp)); Text("Do meu Google Drive")
                        }
                        OutlinedButton(onClick = { source = "firebase" }, modifier = Modifier.fillMaxWidth()) {
                            Text("Cópia antiga no Firebase (com o código)")
                        }
                    }
                    copies == null && source == "firebase" -> CodeField(code) { code = it }
                    copies == null -> Text("Buscando suas cópias no Google Drive…")
                    else -> {
                        Text("Escolha a cópia:", style = MaterialTheme.typography.labelLarge)
                        copies!!.forEachIndexed { i, s ->
                            val sel = pick?.id == s.id
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { pick = s }.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(sel, { pick = s })
                                Column {
                                    Text(fmtDateTime(s.time) + if (i == 0) " · mais recente" else "", fontWeight = FontWeight.Medium)
                                    Text(listOf("${s.members} membros", mb(s.size), s.device).filter { it.isNotBlank() }.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (source == "drive") CodeField(code) { code = it }
                        if (pick != null) Text(
                            "Isso SUBSTITUI todos os dados deste celular pelos do backup.",
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            when {
                source == "firebase" && copies == null -> TextButton(enabled = !busy && codeOk, onClick = { findFirebase() }) { Text("Buscar") }
                copies != null -> TextButton(enabled = !busy && pick != null && codeOk, onClick = { doRestore() }) {
                    Text("Restaurar", color = MaterialTheme.colorScheme.error)
                }
                else -> {}
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun CodeField(code: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        code, { onChange(it.uppercase()) }, label = { Text("Código do backup") }, singleLine = true,
        placeholder = { Text("XXXX-XXXX-XXXX-XXXX-XXXX") },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth(),
    )
}
