package com.plurali.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.plurali.app.data.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ===================== Plano de segurança =====================

@Composable
fun SafetyScreen(nav: NavHostController?, onClose: (() -> Unit)?) {
    val d by Store.data.collectAsState()
    val who = LocalWho.current
    val ctx = LocalContext.current
    val now = rememberNow(30_000)
    var editing by remember { mutableStateOf(false) }
    val p = d.safety
    val today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy, HH:mm", Locale("pt", "BR")))
        .replaceFirstChar { it.uppercase() }

    ScreenScaffold(
        "Plano de segurança",
        onBack = onClose,
        actions = { if (who.isAdmin && !editing) IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, "Editar") } },
    ) { pad ->
        if (editing) {
            SafetyEditor(p, onDone = { editing = false }, modifier = Modifier.padding(pad))
            return@ScreenScaffold
        }
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().themed().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.HealthAndSafety, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text("Você está em segurança.", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                key(now) { Text("Hoje é $today.", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) }
                if (d.systemName.isNotBlank()) Text(
                    "Você faz parte do sistema ${d.systemName}.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
            if (p.info.isNotBlank()) SafetyBlock("Onde estou e quem somos", p.info, Icons.Filled.Place)
            SafetyBlock("Para se acalmar agora", p.grounding.ifBlank { DEFAULT_GROUNDING }, Icons.Filled.SelfImprovement)
            if (p.steps.isNotBlank()) SafetyBlock("O que fazer", p.steps, Icons.Filled.Checklist)
            Text("Pessoas e serviços para chamar", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            p.contacts.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().themed().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontWeight = FontWeight.Medium)
                        Text(c.phone, color = MaterialTheme.colorScheme.primary)
                        if (c.note.isNotBlank()) Text(c.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + c.phone.filter { it.isDigit() || it == '+' }))) }
                    }) { Icon(Icons.Filled.Call, null); Spacer(Modifier.width(6.dp)); Text("Ligar") }
                }
            }
            if (who.isAdmin) Text(
                "Toque no lápis para escrever onde vocês moram, quem cuida de vocês, o que fazer numa crise e trocar os contatos.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SafetyBlock(title: String, text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(Modifier.fillMaxWidth().themed().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SafetyEditor(p: SafetyPlan, onDone: () -> Unit, modifier: Modifier) {
    var info by remember { mutableStateOf(p.info) }
    var grounding by remember { mutableStateOf(p.grounding) }
    var steps by remember { mutableStateOf(p.steps) }
    var contacts by remember { mutableStateOf(p.contacts) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            info, { info = it }, label = { Text("Onde estou e quem somos") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("Ex.: Moramos em … com …. Somos um sistema; outras pessoas usam este corpo. Ninguém vai te machucar.") },
        )
        OutlinedTextField(grounding, { grounding = it }, label = { Text("Para se acalmar") }, minLines = 5, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            steps, { steps = it }, label = { Text("O que fazer numa crise") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("Passo a passo, remédios de crise, quem avisar, lugares seguros…") },
        )
        Text("Contatos", style = MaterialTheme.typography.titleSmall)
        contacts.forEachIndexed { i, c ->
            Column(Modifier.fillMaxWidth().themed().padding(8.dp)) {
                OutlinedTextField(c.name, { v -> contacts = contacts.toMutableList().also { it[i] = c.copy(name = v) } }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(c.phone, { v -> contacts = contacts.toMutableList().also { it[i] = c.copy(phone = v) } }, label = { Text("Telefone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(c.note, { v -> contacts = contacts.toMutableList().also { it[i] = c.copy(note = v) } }, label = { Text("Observação") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { contacts = contacts - c }) { Text("Remover contato", color = MaterialTheme.colorScheme.error) }
            }
        }
        OutlinedButton(onClick = { contacts = contacts + SafetyContact(newId(), "", "") }) {
            Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Adicionar contato")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                Store.setSafety(SafetyPlan(info.trim(), grounding.trim(), steps.trim(), contacts.filter { it.name.isNotBlank() || it.phone.isNotBlank() }))
                onDone()
            }) { Text("Salvar") }
            TextButton(onClick = onDone) { Text("Cancelar") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ===================== Sou novo aqui =====================

@Composable
fun NewcomerScreen(nav: NavHostController) {
    var m by remember { mutableStateOf(Member(newId(), "", color = palette.random())) }
    var sent by remember { mutableStateOf(false) }
    ScreenScaffold("Sou novo aqui") { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (sent) {
                Icon(Icons.Filled.Favorite, null, Modifier.size(48.dp).align(Alignment.CenterHorizontally), tint = MaterialTheme.colorScheme.primary)
                Text("Obrigado por se apresentar, ${m.name}!", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(
                    "Seu perfil foi enviado para o sistema. Quando alguém com acesso de administração aprovar, você aparece na lista de membros.",
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { nav.go("safety") }, modifier = Modifier.fillMaxWidth()) { Text("Ver o plano de segurança") }
                OutlinedButton(onClick = { nav.go("home") }, modifier = Modifier.fillMaxWidth()) { Text("Ver quem faz parte do sistema") }
                return@Column
            }
            val welcome = Store.data.collectAsState().value.welcome
            if (welcome.isNotEmpty()) {
                var showWelcome by remember { mutableStateOf(true) }
                TextButton(onClick = { showWelcome = !showWelcome }) {
                    Text(if (showWelcome) "Esconder as boas-vindas" else "Antes de começar: ler as boas-vindas")
                }
                if (showWelcome) WelcomeCards(welcome)
            }
            Text("Bem-vindo(a). Você não está sozinho(a): este corpo é compartilhado por um sistema, e todo mundo aqui quer te conhecer.")
            Text("Conte o que quiser. Nada é obrigatório além de um nome (pode ser um apelido ou só uma letra).", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(m.name, { m = m.copy(name = it) }, label = { Text("Como quer ser chamado(a)? *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(m.pronouns, { m = m.copy(pronouns = it) }, label = { Text("Pronomes") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                m.description, { m = m.copy(description = it) }, label = { Text("Sobre você: como se sente, do que lembra, sua idade…") },
                minLines = 4, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(m.comfort, { m = m.copy(comfort = it) }, label = { Text("O que te ajuda a se acalmar?") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(m.likes, { m = m.copy(likes = it) }, label = { Text("Do que você gosta?") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Text("Escolha uma cor", style = MaterialTheme.typography.titleSmall)
            ColorPicker(m.color) { m = m.copy(color = it) }
            Button(
                enabled = m.name.isNotBlank(),
                onClick = { m = m.copy(name = m.name.trim()); Store.addPending(m); sent = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Enviar para o sistema") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Banner na aba Membros (para o sistema) com pedidos de novos membros. */
@Composable
fun PendingMembersBanner(d: AppData) {
    var open by remember { mutableStateOf(false) }
    if (d.pendingMembers.isEmpty()) return
    SpRow(0xFFD6AC5E, Modifier.padding(bottom = 6.dp), onClick = { open = true }) {
        Icon(Icons.Filled.WavingHand, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(
            "${d.pendingMembers.size} ${if (d.pendingMembers.size == 1) "novo membro se apresentou" else "novos membros se apresentaram"} · toque para ver",
            Modifier.weight(1f),
        )
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("Novos membros") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                d.pendingMembers.forEach { m ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(m.name, m.color, null, 36.dp); Spacer(Modifier.width(8.dp))
                            NameAndPronouns(m.name, m.pronouns, Modifier.weight(1f))
                        }
                        if (m.description.isNotBlank()) Text(m.description, style = MaterialTheme.typography.bodyMedium)
                        if (m.comfort.isNotBlank()) Text("Acalma: ${m.comfort}", style = MaterialTheme.typography.bodySmall)
                        if (m.likes.isNotBlank()) Text("Gosta de: ${m.likes}", style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = { Store.approvePending(m.id) }) { Text("Aprovar") }
                            TextButton(onClick = { Store.rejectPending(m.id) }) { Text("Recusar", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                if (d.pendingMembers.isEmpty()) Text("Nenhum pedido.")
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Fechar") } },
    )
}

// ===================== Mural de boas-vindas =====================

/** Os cartões de boas-vindas, em sequência. Usado na tela própria, no início (sem login) e em "Sou novo aqui". */
@Composable
fun WelcomeCards(cards: List<WelcomeCard>, onEdit: ((WelcomeCard) -> Unit)? = null, total: Int = cards.size) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cards.forEachIndexed { i, w ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .then(if (onEdit != null) Modifier.clickable { onEdit(w) } else Modifier),
            ) {
                Box(Modifier.width(6.dp).fillMaxHeight().background(androidx.compose.ui.graphics.Color(w.color)))
                Column(Modifier.padding(16.dp)) {
                    Text("${i + 1} de $total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (w.title.isNotBlank()) Text(w.title, style = MaterialTheme.typography.titleLarge)
                    if (w.text.isNotBlank()) Text(w.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(nav: NavHostController?, onClose: (() -> Unit)?) {
    val d by Store.data.collectAsState()
    val who = LocalWho.current
    var edit by remember { mutableStateOf<WelcomeCard?>(null) }
    val canEdit = who.isAdmin
    ScreenScaffold(
        "Boas-vindas",
        onBack = onClose,
        fab = { if (canEdit) FloatingActionButton(onClick = { edit = WelcomeCard(newId(), "", "") }) { Icon(Icons.Filled.Add, "Novo cartão") } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (canEdit) Text(
                "Este mural aparece para quem entra sem login (ex.: um alter que acabou de chegar): " +
                    "na tela de login, no início e em “Sou novo aqui”. Escreva em sequência o que a pessoa precisa saber. Toque num cartão para editar.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (d.welcome.isEmpty()) {
                EmptyHint(if (canEdit) "Nenhum cartão ainda." else "O sistema ainda não escreveu as boas-vindas.")
                if (canEdit) Button(onClick = { Store.setWelcome(SUGGESTED_WELCOME.map { it.copy(id = newId()) }) }) {
                    Icon(Icons.Filled.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Começar com textos sugeridos")
                }
            } else WelcomeCards(d.welcome, if (canEdit) { w -> edit = w } else null)
            if (!canEdit) {
                OutlinedButton(onClick = { nav?.go("safety") ?: onClose?.invoke() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.HealthAndSafety, null); Spacer(Modifier.width(8.dp)); Text("Plano de segurança")
                }
            }
            Spacer(Modifier.height(72.dp))
        }
    }
    edit?.let { w0 ->
        val isNew = d.welcome.none { it.id == w0.id }
        var w by remember(w0.id) { mutableStateOf(w0) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(if (isNew) "Novo cartão" else "Editar cartão") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(w.title, { w = w.copy(title = it) }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(w.text, { w = w.copy(text = it) }, label = { Text("Texto") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                    ColorPicker(w.color) { w = w.copy(color = it) }
                    if (!isNew) Row {
                        TextButton(onClick = { Store.moveWelcome(w.id, -1) }) { Icon(Icons.Filled.KeyboardArrowUp, null); Text("Subir") }
                        TextButton(onClick = { Store.moveWelcome(w.id, 1) }) { Icon(Icons.Filled.KeyboardArrowDown, null); Text("Descer") }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = w.title.isNotBlank() || w.text.isNotBlank(), onClick = { Store.upsertWelcome(w); edit = null }) { Text("Salvar") }
            },
            dismissButton = {
                Row {
                    if (!isNew) TextButton(onClick = { Store.deleteWelcome(w.id); edit = null }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { edit = null }) { Text("Cancelar") }
                }
            },
        )
    }
}
