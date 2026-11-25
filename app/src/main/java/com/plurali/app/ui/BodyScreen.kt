package com.plurali.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.plurali.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val sections = listOf(
    "geral" to "Geral",
    "saude" to "Saúde",
    "contatos" to "Contatos",
    "outros" to "Outros",
)

private val suggested = listOf(
    "geral" to "Data de nascimento", "geral" to "Altura", "geral" to "Peso", "geral" to "Tipo sanguíneo",
    "saude" to "Alergias", "saude" to "Medicamentos de uso contínuo", "saude" to "Condições de saúde",
    "saude" to "Médico(a) / clínica",
    "contatos" to "Contato de emergência", "contatos" to "Pessoas de confiança",
)

private val suggestedSensitive = listOf(
    "Documentos (RG, CPF…)", "Diagnósticos", "Medicação controlada", "Plano de saúde",
    "Terapeuta / psiquiatra", "Gatilhos", "Observações privadas",
)

/** Dados sensíveis abertos nesta tela (só na memória, nunca salvos sem criptografia). */
private class Unlocked(val password: String, val entries: List<BodyEntry>)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BodyScreen(nav: NavHostController) {
    val d by Store.data.collectAsState()
    val who = LocalWho.current
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf<Pair<BodyEntry, Boolean>?>(null) } // (entrada, sensível?)
    var unlocked by remember { mutableStateOf<Unlocked?>(null) }
    var askUnlock by remember { mutableStateOf(false) }
    var createVault by remember { mutableStateOf(false) }
    var changePw by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun saveSensitive(list: List<BodyEntry>, pw: String = unlocked!!.password) {
        busy = true
        scope.launch {
            val v = withContext(Dispatchers.Default) { Crypto.encrypt(Json.encodeEntries(list), pw) }
            Store.setVault(v)
            unlocked = Unlocked(pw, list)
            busy = false
        }
    }

    ScreenScaffold("Corpo") { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
        ) {
            item {
                Text(
                    "Informações do corpo que todo mundo do sistema compartilha.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            sections.forEach { (key, title) ->
                val list = d.body.filter { it.section == key && (who.canWrite || it.value.isNotBlank()) }
                item(key = "h-$key") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader(title, Modifier.weight(1f))
                        if (who.canWrite) IconButton(onClick = { edit = BodyEntry(newId(), key, "", "") to false }) {
                            Icon(Icons.Filled.Add, "Adicionar em $title")
                        }
                    }
                }
                if (list.isEmpty()) item(key = "e-$key") {
                    Text("Nada preenchido.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                items(list, key = { it.id }) { b -> EntryRow(b, who.canWrite) { edit = b to false } }
            }
            if (who.canWrite && d.body.isEmpty()) item {
                OutlinedButton(
                    onClick = { suggested.forEach { (s, l) -> Store.upsertBody(BodyEntry(newId(), s, l, "")) } },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { Icon(Icons.Filled.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Adicionar campos sugeridos") }
            }

            // ---------- sensível ----------
            if (who.canWrite) {
                item(key = "sens") {
                    Spacer(Modifier.height(16.dp))
                    Column(
                        Modifier.fillMaxWidth().themed().padding(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (unlocked != null) Icons.Filled.LockOpen else Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Dados sensíveis", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                        Text(
                            "Ficam criptografados com uma senha própria, inclusive dentro do backup. " +
                                "Se a senha for esquecida, não há como recuperar.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                        when {
                            d.vault == null -> Button(onClick = { createVault = true }) {
                                Icon(Icons.Filled.Key, null); Spacer(Modifier.width(6.dp)); Text("Criar senha dos dados sensíveis")
                            }
                            unlocked == null -> Button(onClick = { askUnlock = true }) {
                                Icon(Icons.Filled.LockOpen, null); Spacer(Modifier.width(6.dp)); Text("Desbloquear")
                            }
                            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { edit = BodyEntry(newId(), "sensivel", "", "") to true }) {
                                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Adicionar")
                                }
                                OutlinedButton(onClick = { unlocked = null }) { Text("Bloquear") }
                                TextButton(onClick = { changePw = true }) { Text("Trocar senha") }
                            }
                        }
                    }
                }
                unlocked?.let { u ->
                    if (u.entries.isEmpty()) item {
                        OutlinedButton(
                            onClick = { saveSensitive(suggestedSensitive.map { BodyEntry(newId(), "sensivel", it, "") }) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text("Adicionar campos sugeridos") }
                    }
                    items(u.entries, key = { "s-" + it.id }) { b -> EntryRow(b, true) { edit = b to true } }
                }
            }
        }
    }

    edit?.let { (b, sensitive) ->
        val isNew = if (sensitive) unlocked?.entries?.none { it.id == b.id } ?: true else d.body.none { it.id == b.id }
        var label by remember(b.id) { mutableStateOf(b.label) }
        var value by remember(b.id) { mutableStateOf(b.value) }
        var section by remember(b.id) { mutableStateOf(b.section) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(if (sensitive) "Dado sensível" else "Dado do corpo") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(label, { label = it }, label = { Text("Nome do campo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value, { value = it }, label = { Text("Valor") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    if (!sensitive) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sections.forEach { (k, t) -> FilterChip(section == k, { section = k }, label = { Text(t) }) }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = label.isNotBlank(), onClick = {
                    val nb = b.copy(label = label.trim(), value = value.trim(), section = section)
                    if (sensitive) {
                        val cur = unlocked!!.entries
                        saveSensitive(if (cur.any { it.id == nb.id }) cur.map { if (it.id == nb.id) nb else it } else cur + nb)
                    } else Store.upsertBody(nb)
                    edit = null
                }) { Text("Salvar") }
            },
            dismissButton = {
                Row {
                    if (!isNew) TextButton(onClick = {
                        if (sensitive) saveSensitive(unlocked!!.entries.filterNot { it.id == b.id }) else Store.deleteBody(b.id)
                        edit = null
                    }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { edit = null }) { Text("Cancelar") }
                }
            },
        )
    }
    if (askUnlock) {
        val vault = d.vault
        val result = remember { arrayOfNulls<List<BodyEntry>>(1) }
        PasswordDialog(
            title = "Desbloquear dados sensíveis",
            check = { pw -> vault?.let { Crypto.decrypt(it, pw) }?.let { result[0] = Json.decodeEntries(it); true } ?: false },
            onDismiss = { askUnlock = false },
        ) { pw -> unlocked = Unlocked(pw, result[0] ?: emptyList()); askUnlock = false }
    }
    if (createVault) NewPasswordDialog(
        "Senha dos dados sensíveis",
        warning = "Guarde bem esta senha: sem ela os dados sensíveis não podem ser recuperados por ninguém.",
        onDismiss = { createVault = false },
    ) { pw -> saveSensitive(emptyList(), pw) }
    if (changePw) NewPasswordDialog("Nova senha dos dados sensíveis", onDismiss = { changePw = false }) { pw ->
        saveSensitive(unlocked!!.entries, pw)
    }
}

@Composable
private fun EntryRow(b: BodyEntry, canEdit: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = canEdit, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(b.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        Text(
            b.value.ifBlank { "—" },
            fontWeight = if (b.value.isBlank()) FontWeight.Normal else FontWeight.Medium,
            color = if (b.value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}
