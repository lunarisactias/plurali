package com.plurali.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun mb(bytes: Int) = if (bytes < 1_000_000) "%.0f KB".format(bytes / 1000.0) else "%.1f MB".format(bytes / 1_000_000.0)

private fun copy(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Código do backup", text))
}

/** Cartão "Backup na nuvem" da tela de backup. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloudBackupCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val d by Store.data.collectAsState()
    val b = d.cloudBackup
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var showCode by remember { mutableStateOf(false) }
    var newCodeDialog by remember { mutableStateOf<String?>(null) }
    var confirmRotate by remember { mutableStateOf(false) }
    var confirmOff by remember { mutableStateOf(false) }
    var restore by remember { mutableStateOf(false) }

    fun upload(force: Boolean) {
        busy = true; msg = "Enviando…"
        scope.launch {
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
            "Uma cópia de tudo (com fotos) vai sozinha para o Firebase do Plurali, criptografada com um código que só vocês têm. " +
                "Se perder ou trocar de celular, é só instalar o Plurali e restaurar com o código.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (b == null) {
            Button(onClick = {
                val code = CloudBackupSync.newCode()
                Store.setCloudBackup(CloudBackup(code))
                CloudBackupSync.schedule(ctx)
                newCodeDialog = code
                upload(force = true)
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CloudDone, null); Spacer(Modifier.width(8.dp)); Text("Ativar backup na nuvem")
            }
        } else {
            // estado do último envio
            Text(
                when {
                    Prefs.cloudBackupError != null -> "Último envio falhou: ${Prefs.cloudBackupError}"
                    Prefs.lastCloudBackup > 0 -> "Último backup: ${fmtDateTime(Prefs.lastCloudBackup)}" +
                        (if (Prefs.cloudBackupSize > 0) " · ${mb(Prefs.cloudBackupSize)}" else "")
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
                    "Anote fora do celular (papel, gerenciador de senhas). Sem ele não dá para restaurar.",
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
                    Text("É a única forma de recuperar o backup num celular novo. Guarde fora deste celular.")
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
        val old = b.code
        val code = CloudBackupSync.newCode()
        Store.setCloudBackup(b.copy(code = code))
        Prefs.forgetCloudBackup()
        newCodeDialog = code
        scope.launch { withContext(Dispatchers.IO) { CloudBackupSync.deleteRemote(old) } }
        upload(force = true)
    }

    if (confirmOff && b != null) ConfirmDialog(
        "Desativar o backup na nuvem?",
        "As cópias guardadas no Firebase serão apagadas. Os dados deste celular não mudam.",
        confirm = "Desativar",
        onDismiss = { confirmOff = false },
    ) {
        confirmOff = false
        val old = b.code
        Store.setCloudBackup(null)
        Prefs.forgetCloudBackup()
        CloudBackupSync.schedule(ctx)
        scope.launch { withContext(Dispatchers.IO) { CloudBackupSync.deleteRemote(old) }; msg = "Backup na nuvem desativado." }
    }

    if (restore) RestoreCloudDialog(initial = b?.code ?: "", onDismiss = { restore = false }) { restore = false; msg = it }
}

/** Digitar o código → ver as cópias → escolher → confirmar → restaurar. */
@Composable
private fun RestoreCloudDialog(initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf(if (initial.isNotBlank()) CloudBackupSync.pretty(initial) else "") }
    var slots by remember { mutableStateOf<List<CloudBackupSync.Slot>?>(null) }
    var pick by remember { mutableStateOf<CloudBackupSync.Slot?>(null) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    fun find() {
        busy = true; err = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { CloudBackupSync.slots(code) } }
            busy = false
            r.onSuccess { if (it.isEmpty()) err = "Nenhum backup encontrado com esse código." else slots = it }
                .onFailure { err = "Não consegui buscar: ${it.message}" }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Filled.CloudDownload, null) },
        title = { Text("Restaurar da nuvem") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (slots == null) {
                    OutlinedTextField(
                        code, { code = it.uppercase() }, label = { Text("Código do backup") }, singleLine = true,
                        placeholder = { Text("XXXX-XXXX-XXXX-XXXX-XXXX") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("Escolha a cópia:", style = MaterialTheme.typography.labelLarge)
                    slots!!.forEachIndexed { i, s ->
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
                                Text("${s.members} membros · ${mb(s.size)} · ${s.device}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (pick != null) Text(
                        "Isso SUBSTITUI todos os dados deste celular pelos do backup.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (slots == null) TextButton(enabled = !busy && CloudBackupSync.isValid(code), onClick = { find() }) { Text("Buscar") }
            else TextButton(enabled = !busy && pick != null, onClick = {
                val s = pick ?: return@TextButton
                busy = true; err = null
                scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        runCatching { CloudBackupSync.apply(ctx, CloudBackupSync.download(code, s.id)) }
                    }
                    busy = false
                    r.onSuccess { onDone("Backup de ${fmtDateTime(s.time)} restaurado.") }
                        .onFailure { err = it.message ?: "Erro ao restaurar" }
                }
            }) { Text("Restaurar", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } },
    )
}
