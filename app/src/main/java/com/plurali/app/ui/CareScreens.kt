package com.plurali.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.plurali.app.data.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

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

private val WELCOME_SIZES = listOf(2 to "Pequeno", 3 to "Médio", 4 to "Grande", 6 to "Largura toda")

/** Agrupa os itens em linhas de até 6 colunas, na ordem. */
private fun packRows(cards: List<WelcomeCard>): List<List<WelcomeCard>> {
    val rows = mutableListOf<MutableList<WelcomeCard>>()
    var used = 0
    for (c in cards) {
        val s = c.size.coerceIn(2, 6)
        if (rows.isEmpty() || used + s > 6) { rows.add(mutableListOf()); used = 0 }
        rows.last().add(c); used += s
    }
    return rows
}

/**
 * O mural de boas-vindas em grade: textos e fotos lado a lado, cada um com seu tamanho.
 * Cada linha sempre ocupa a largura toda (os itens esticam na proporção), então nada fica torto.
 * Usado na tela própria, no início (sem login) e em "Sou novo aqui".
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WelcomeCards(
    cards: List<WelcomeCard>,
    onEdit: ((WelcomeCard) -> Unit)? = null,
    total: Int = cards.size,
    onOpenPhoto: ((WelcomeCard) -> Unit)? = null,
) {
    var viewing by remember { mutableStateOf<WelcomeCard?>(null) }
    val open = onOpenPhoto ?: { w: WelcomeCard -> viewing = w }
    val index = cards.withIndex().associate { it.value.id to it.index }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        packRows(cards).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { w ->
                    WelcomeTile(
                        w, (index[w.id] ?: 0) + 1, total,
                        Modifier.weight(w.size.coerceIn(2, 6).toFloat()).fillMaxHeight(),
                        onClick = { if (w.image != null) open(w) else onEdit?.invoke(w) },
                        onLongClick = onEdit?.let { { it(w) } },
                    )
                }
            }
        }
    }
    viewing?.let { v ->
        val photos = cards.filter { it.image != null }
        PhotoViewer(photos, photos.indexOfFirst { it.id == v.id }.coerceAtLeast(0), onEdit = onEdit?.let { e -> { w: WelcomeCard -> viewing = null; e(w) } }) { viewing = null }
    }
}

/** O que tocar ao ouvir este item: a voz gravada, se houver; senão o título + texto em leitura automática. */
private fun WelcomeCard.speechPart(): Narrator.Part? =
    Narrator.part(voice, listOf(title, Rich.strip(text)).filter { it.isNotBlank() }.joinToString(". "))

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun WelcomeTile(w: WelcomeCard, n: Int, total: Int, modifier: Modifier, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val small = w.size <= 3
    val imgH = when (w.size) { 2 -> 110.dp; 3 -> 140.dp; 4 -> 170.dp; else -> 210.dp }
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(Modifier.fillMaxWidth().height(5.dp).background(androidx.compose.ui.graphics.Color(w.color)))
        if (w.image != null) Box {
            StoredImage(w.image, Modifier.fillMaxWidth().height(imgH))
            Icon(
                Icons.Filled.ZoomIn, "Ver maior", tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50)).padding(4.dp).size(16.dp),
            )
        }
        val ta = when (w.align) { "center" -> TextAlign.Center; "end" -> TextAlign.End; else -> TextAlign.Start }
        if (w.title.isNotBlank() || w.text.isNotBlank() || w.image == null) Column(Modifier.fillMaxWidth().padding(if (small) 10.dp else 16.dp)) {
            // número do cartão + botão para ouvir o texto em voz alta
            val ctx = LocalContext.current
            val speaking = Narrator.playingId == w.id
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (total > 1) "$n de $total" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = ta, modifier = Modifier.weight(1f))
                // com voz gravada toca a gravação; sem ela, lê o texto automaticamente
                if (w.voice != null || w.title.isNotBlank() || w.text.isNotBlank()) FilledTonalIconButton(
                    onClick = { Narrator.toggle(ctx, w.id, listOf(w.speechPart())) },
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        if (speaking) Icons.Filled.Stop else if (w.voice != null) Icons.Filled.RecordVoiceOver else Icons.Filled.VolumeUp,
                        if (speaking) "Parar" else if (w.voice != null) "Ouvir a voz gravada" else "Ouvir", Modifier.size(18.dp),
                    )
                }
            }
            if (w.title.isNotBlank()) Text(w.title, style = if (small) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge, textAlign = ta, modifier = Modifier.fillMaxWidth())
            if (w.text.isNotBlank()) RichText(
                w.text, style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
                textAlign = ta, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
fun WelcomeScreen(nav: NavHostController?, onClose: (() -> Unit)?) {
    val d by Store.data.collectAsState()
    val who = LocalWho.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf<WelcomeCard?>(null) } // item sendo editado (estado vivo do diálogo)
    var busy by remember { mutableStateOf(false) }
    val canEdit = who.isAdmin
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        val cur = edit
        if (uri != null && cur != null) scope.launch {
            busy = true
            val path = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { saveImage(ctx, uri, "welcome", cur.id, aspect = null, maxSide = 2000) }
            busy = false
            if (path != null) edit = edit?.copy(image = path)
        }
    }
    fun pickPhoto() = pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
    // gravar a própria voz lendo o item (fica mais natural que a leitura automática)
    var recording by remember { mutableStateOf(false) }
    fun startRec() { Narrator.stop(); AudioPlay.stop(); if (AudioRec.start(ctx)) recording = true }
    val micPerm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startRec()
    }
    fun recordVoice() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) startRec()
        else micPerm.launch(android.Manifest.permission.RECORD_AUDIO)
    }
    DisposableEffect(Unit) { onDispose { if (AudioRec.active) AudioRec.cancel() } }
    LaunchedEffect(edit == null) { if (edit == null && recording) { AudioRec.cancel(); recording = false } }

    ScreenScaffold(
        "Boas-vindas",
        onBack = onClose,
        fab = {
            if (canEdit) ExtendedFloatingActionButton(
                onClick = { edit = WelcomeCard(newId(), "", "", size = 6) },
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Novo item") },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (canEdit) Text(
                "Este mural aparece para quem entra sem login (ex.: um alter que acabou de chegar): na tela de entrada, no início e em “Sou novo aqui”. " +
                    "Cada item pode ter texto e/ou foto (planta da casa, família, pets…) e um tamanho. Toque para editar; nas fotos, toque para ver maior e segure para editar.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (d.welcome.any { it.voice != null || it.title.isNotBlank() || it.text.isNotBlank() }) {
                val speakingAll = Narrator.playingId == "welcome-all"
                FilledTonalButton(onClick = { Narrator.toggle(ctx, "welcome-all", d.welcome.map { it.speechPart() }) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(if (speakingAll) Icons.Filled.Stop else Icons.Filled.VolumeUp, null); Spacer(Modifier.width(8.dp))
                    Text(if (speakingAll) "Parar a leitura" else "Ouvir tudo em voz alta")
                }
            }
            DisposableEffect(Unit) { onDispose { Narrator.stop() } }
            if (d.welcome.isEmpty()) {
                EmptyHint(if (canEdit) "Nenhum item ainda." else "O sistema ainda não escreveu as boas-vindas.")
                if (canEdit) Button(onClick = { Store.setWelcome(SUGGESTED_WELCOME.map { it.copy(id = newId()) }) }) {
                    Icon(Icons.Filled.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Começar com textos sugeridos")
                }
            } else WelcomeCards(d.welcome, if (canEdit) { w -> edit = w } else null)
            if (!canEdit) {
                OutlinedButton(onClick = { nav?.go("safety") ?: onClose?.invoke() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.HealthAndSafety, null); Spacer(Modifier.width(8.dp)); Text("Plano de segurança")
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    edit?.let { w ->
        val isNew = d.welcome.none { it.id == w.id }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(if (isNew) "Novo item" else "Editar item") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // foto
                    if (w.image != null) {
                        StoredImage(w.image, Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(10.dp)))
                        Row {
                            TextButton(onClick = { pickPhoto() }) { Icon(Icons.Filled.Image, null); Spacer(Modifier.width(4.dp)); Text("Trocar foto") }
                            TextButton(onClick = { edit = w.copy(image = null) }) { Text("Tirar foto", color = MaterialTheme.colorScheme.error) }
                        }
                    } else OutlinedButton(onClick = { pickPhoto() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "Carregando…" else "Adicionar foto (opcional)")
                    }
                    OutlinedTextField(w.title, { edit = w.copy(title = it) }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
                    RichTextField(w.text, { edit = w.copy(text = it) }, if (w.image != null) "Legenda / texto" else "Texto", Modifier.fillMaxWidth())
                    // voz gravada: toca no lugar da leitura automática
                    Text("Voz", style = MaterialTheme.typography.labelLarge)
                    when {
                        recording -> Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp)) {
                            RecordingBar(
                                onCancel = { AudioRec.cancel(); recording = false },
                                onSend = { recording = false; AudioRec.stop()?.let { (p, ms) -> edit = edit?.copy(voice = p, voiceMs = ms) } },
                            )
                        }
                        w.voice != null -> {
                            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp)) {
                                Box(Modifier.padding(horizontal = 10.dp)) { AudioBubble(w.voice, w.voiceMs) }
                            }
                            Row {
                                TextButton(onClick = { recordVoice() }) { Icon(Icons.Filled.Mic, null); Spacer(Modifier.width(4.dp)); Text("Regravar") }
                                TextButton(onClick = { AudioPlay.stop(); edit = w.copy(voice = null, voiceMs = 0) }) { Text("Tirar voz", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                        else -> OutlinedButton(onClick = { recordVoice() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Mic, null); Spacer(Modifier.width(8.dp)); Text("Gravar minha voz lendo (opcional)")
                        }
                    }
                    Text(
                        if (w.voice != null) "O botão de ouvir toca esta gravação." else "Sem gravação, o botão de ouvir lê o texto com a voz automática do celular.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Alinhamento", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(Triple("start", "Esquerda", Icons.Filled.FormatAlignLeft), Triple("center", "Centro", Icons.Filled.FormatAlignCenter), Triple("end", "Direita", Icons.Filled.FormatAlignRight)).forEach { (k, l, ic) ->
                            val sel = w.align == k
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { edit = w.copy(align = k) }.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(ic, null, tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(l, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    // tamanho, com desenho de quanto ocupa da largura
                    Text("Tamanho no mural", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WELCOME_SIZES.forEach { (s, l) ->
                            val sel = w.size == s
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { edit = w.copy(size = s) }.padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Row(Modifier.fillMaxWidth().height(14.dp)) {
                                    Box(Modifier.weight(s.toFloat()).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primary))
                                    if (s < 6) Box(Modifier.weight((6 - s).toFloat()).fillMaxHeight())
                                }
                                Text(l, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                    Text("Itens menores ficam lado a lado.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ColorPicker(w.color) { edit = w.copy(color = it) }
                    if (!isNew) Row {
                        TextButton(onClick = { Store.moveWelcome(w.id, -1) }) { Icon(Icons.Filled.KeyboardArrowUp, null); Text("Antes") }
                        TextButton(onClick = { Store.moveWelcome(w.id, 1) }) { Icon(Icons.Filled.KeyboardArrowDown, null); Text("Depois") }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !recording && (w.title.isNotBlank() || w.text.isNotBlank() || w.image != null || w.voice != null), onClick = { Store.upsertWelcome(w); edit = null }) { Text("Salvar") }
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

/** Foto em tela cheia: zoom com dois dedos, arrastar e passar para a próxima. */
@Composable
private fun PhotoViewer(list: List<WelcomeCard>, start: Int, onEdit: ((WelcomeCard) -> Unit)?, onClose: () -> Unit) {
    if (list.isEmpty()) { onClose(); return }
    var i by remember { mutableStateOf(start.coerceIn(0, list.lastIndex)) }
    val p = list[i.coerceIn(0, list.lastIndex)]
    var scale by remember(p.id) { mutableStateOf(1f) }
    var offset by remember(p.id) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val white = androidx.compose.ui.graphics.Color.White
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
            StoredImage(
                p.image,
                Modifier.fillMaxSize()
                    .pointerInput(p.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Fechar", tint = white) }
                Text(if (list.size > 1) "${i + 1} de ${list.size}" else "", color = white, modifier = Modifier.weight(1f))
                if (onEdit != null) IconButton(onClick = { onEdit(p) }) { Icon(Icons.Filled.Edit, "Editar", tint = white) }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
                    .navigationBarsPadding().padding(12.dp),
            ) {
                if (p.title.isNotBlank()) Text(p.title, color = white, style = MaterialTheme.typography.titleMedium)
                if (p.text.isNotBlank()) Text(p.text, color = white)
                if (list.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = i > 0, onClick = { i-- }) { Text("‹ Anterior", color = white) }
                    TextButton(enabled = i < list.lastIndex, onClick = { i++ }) { Text("Próxima ›", color = white) }
                }
            }
        }
    }
}
