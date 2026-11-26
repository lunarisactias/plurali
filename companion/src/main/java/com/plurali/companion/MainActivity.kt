package com.plurali.companion

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.plurali.shared.*
import com.plurali.app.data.Appearance
import com.plurali.app.data.AppearanceJson
import com.plurali.app.ui.theme.LocalUi
import com.plurali.app.ui.theme.Strip
import com.plurali.app.ui.theme.ThemedBackground
import com.plurali.app.ui.theme.isDark
import com.plurali.app.ui.theme.themedCard
import com.plurali.app.LocalExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var openSystem by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CompanionApp.ensure(this)
        enableEdgeToEdge(SystemBarStyle.dark(AColor.TRANSPARENT), SystemBarStyle.dark(AColor.TRANSPARENT))
        openSystem = intent?.getStringExtra("system")
        setContent { CompanionRoot(openSystem) { openSystem = null } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openSystem = intent.getStringExtra("system")
    }
}

// ---------------- tema: o mesmo do Plurali, seguindo quem está no front ----------------

private val Gold: Color @Composable get() = MaterialTheme.colorScheme.primary
private val Bg: Color @Composable get() = MaterialTheme.colorScheme.background
private val Card: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer
private val Field: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
private val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val Pron: Color @Composable get() = MaterialTheme.colorScheme.tertiary

/** Aparência que veio do sistema (a de quem está no front); sem ela, o tema padrão. */
fun Snapshot?.look(): Appearance =
    this?.appearance?.let { runCatching { AppearanceJson.decode(org.json.JSONObject(it)) }.getOrNull() } ?: Appearance()

@Composable
fun CompanionTheme(a: Appearance = Appearance(), content: @Composable () -> Unit) {
    val activity = LocalContext.current as? ComponentActivity
    val dark = a.isDark()
    LaunchedEffect(dark) {
        activity?.enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT) else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT),
            navigationBarStyle = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT) else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT),
        )
    }
    com.plurali.app.PluraliTheme(a, content)
}

private val ptBR = Locale("pt", "BR")
private val zone: ZoneId get() = ZoneId.systemDefault()
private fun fmtTime(ms: Long) = DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(ms).atZone(zone))
private fun fmtDT(ms: Long) = DateTimeFormatter.ofPattern("dd/MM HH:mm").format(Instant.ofEpochMilli(ms).atZone(zone))
private fun fmtDur(ms: Long): String {
    val m = maxOf(0, ms) / 60_000
    return when { m >= 1440 -> "${m / 1440}d ${(m % 1440) / 60}h"; m >= 60 -> "${m / 60}h ${m % 60}min"; else -> "${m}min" }
}
private fun ago(ms: Long, now: Long): String = when {
    ms == 0L -> "nunca"
    now - ms < 60_000 -> "agora"
    else -> "há " + fmtDur(now - ms)
}

@Composable
private fun rememberNow(): Long {
    var n by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); n = System.currentTimeMillis() } }
    return n
}

@Composable
private fun Avatar(name: String, color: Long, b64: String?, size: Dp) {
    val bmp: ImageBitmap? = remember(b64) {
        b64?.let { runCatching { Base64.decode(it, Base64.NO_WRAP).let { b -> BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() } }.getOrNull() }
    }
    val ui = LocalUi.current
    val ears = ui.avatarId == "cat" || ui.avatarId == "bunny"
    Box(Modifier.size(size).clip(ui.avatarShape).background(Color(color)), contentAlignment = Alignment.Center) {
        if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(name.take(1).uppercase(), color = if (Color(color).luminance() > 0.5f) Color.Black else Color.White, fontWeight = FontWeight.Bold,
            fontSize = (size.value * (if (ears) 0.34f else 0.42f)).sp, modifier = if (ears) Modifier.padding(top = size * 0.24f) else Modifier)
    }
}

/** Cartão com a "meia-lua" colorida, igual ao Plurali/SimplyPlural. */
@Composable
private fun SpCard(color: Long, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val ui = LocalUi.current
    Row(
        modifier.fillMaxWidth()
            .themedCard(ui, MaterialTheme.colorScheme.surfaceContainer, Color(color), MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.onSurface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = if (ui.strip == Strip.LEFT) 14.dp else 10.dp, end = 10.dp, top = 10.dp + ui.cardTopExtra + (if (ui.strip == Strip.SPOTS) 8.dp else 0.dp), bottom = 10.dp + ui.cardBottomExtra),
        verticalAlignment = Alignment.CenterVertically, content = content,
    )
}

@Composable
private fun Hint(text: String) = Text(text, color = Muted, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), textAlign = TextAlign.Center)

@Composable
private fun Header(text: String) {
    val mark = LocalUi.current.headerMark
    Text(if (mark.isNotEmpty()) "$mark  $text" else text, color = Gold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

// ---------------- raiz ----------------

@Composable
fun CompanionRoot(openSystem: String?, consumed: () -> Unit) {
    val list by Follows.list.collectAsState()
    val ctx = LocalContext.current
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        withContext(Dispatchers.IO) { Poller.fetchAll(ctx, notify = false) }
    }
    var adding by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(Follows.selected) }
    LaunchedEffect(openSystem) { if (openSystem != null) { selected = openSystem; Follows.selected = openSystem; consumed() } }
    val current = list.find { it.id == selected } ?: list.firstOrNull()

    // o app inteiro segue o visual de quem está no front no sistema escolhido
    CompanionTheme(if (adding) Appearance() else current?.snapshot.look()) {
        if (current == null || adding) AddScreen(canCancel = current != null, onCancel = { adding = false }) { inv ->
            selected = inv.shareId; Follows.selected = inv.shareId; adding = false
        } else SystemScreen(current, list, onSelect = { selected = it; Follows.selected = it }, onAdd = { adding = true })
    }
}

// ---------------- adicionar sistema ----------------

@Composable
private fun AddScreen(canCancel: Boolean, onCancel: () -> Unit, onAdded: (Invite) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var paste by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun tryAdd(text: String) {
        val inv = Invite.decode(text)
        if (inv == null) { msg = "Esse código não é um convite do Plurali."; return }
        busy = true; msg = "Conectando…"
        scope.launch {
            val f = withContext(Dispatchers.IO) { Poller.fetch(ctx, Followed(inv), notify = false) }
            busy = false
            when {
                f.revoked -> msg = "Esse convite não está mais ativo. Peça um novo ao sistema."
                f.snapshot == null -> msg = "Não consegui ler: ${f.error ?: "erro desconhecido"}"
                else -> { Follows.add(inv); Follows.replace(f); onAdded(inv) }
            }
        }
    }
    val scan = rememberLauncherForActivityResult(ScanContract()) { r -> r.contents?.let { tryAdd(it) } }

    Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground, contentWindowInsets = WindowInsets.safeDrawing) { pad ->
        ThemedBackground(Modifier.fillMaxSize())
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Icon(Icons.Filled.Diversity1, null, Modifier.size(56.dp), tint = Gold)
            Text("Plurali Companion", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Acompanhe um sistema plural: veja quem está no front e receba um aviso quando mudar, " +
                    "além do que o sistema escolher compartilhar com você.",
                textAlign = TextAlign.Center, color = Muted,
            )
            Spacer(Modifier.height(8.dp))
            Button(enabled = !busy, onClick = {
                scan.launch(ScanOptions().setPrompt("Aponte para o QR code do convite").setBeepEnabled(false).setOrientationLocked(false)
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE))
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text("Escanear QR code")
            }
            OutlinedButton(enabled = !busy, onClick = { paste = !paste }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.ContentPaste, null); Spacer(Modifier.width(8.dp)); Text("Colar código do convite")
            }
            if (paste) {
                OutlinedTextField(code, { code = it }, label = { Text("Código (começa com PLURALI1:)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Button(enabled = !busy && code.isNotBlank(), onClick = { tryAdd(code) }) { Text("Adicionar") }
            }
            msg?.let { Text(it, color = if (it.startsWith("Conectando")) Gold else MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
            if (canCancel) TextButton(onClick = onCancel) { Text("Cancelar") }
            Text(
                "Você só vê o que o sistema liberou para você, e ele pode tirar o acesso quando quiser.",
                style = MaterialTheme.typography.bodySmall, color = Muted, textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------- tela do sistema ----------------

private data class Section(val key: String, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SystemScreen(f: Followed, all: List<Followed>, onSelect: (String) -> Unit, onAdd: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val now = rememberNow()
    val s = f.snapshot
    var menu by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var person by remember { mutableStateOf<SnapPerson?>(null) }
    val sections = buildList {
        if (s?.front != null) add(Section("front", "Front", Icons.Filled.People))
        if (s?.notices != null) add(Section("notices", "Mural", Icons.Filled.Campaign))
        if (s?.events != null) add(Section("calendar", "Agenda", Icons.Filled.CalendarMonth))
        if (s?.people != null) add(Section("people", "Pessoas", Icons.Filled.Badge))
        if (s?.safety != null || s?.body != null || s?.history != null) add(Section("more", "Mais", Icons.Filled.MoreHoriz))
    }
    var tab by remember(f.id) { mutableStateOf(sections.firstOrNull()?.key ?: "front") }
    if (sections.none { it.key == tab } && sections.isNotEmpty()) tab = sections.first().key

    person?.let { p -> if (s != null) { PersonScreen(s, p) { person = null }; return } }

    fun refresh() {
        refreshing = true
        scope.launch { withContext(Dispatchers.IO) { Poller.fetch(ctx, f, notify = false) }; refreshing = false }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.clickable { menu = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s?.systemName ?: f.invite.systemName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Filled.ArrowDropDown, "Trocar de sistema")
                        }
                        Text("Atualizado ${ago(f.lastFetch, now)}", style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                    DropdownMenu(menu, { menu = false }) {
                        all.forEach { o ->
                            DropdownMenuItem(text = { Text(o.snapshot?.systemName ?: o.invite.systemName) }, onClick = { onSelect(o.id); menu = false },
                                leadingIcon = { if (o.id == f.id) Icon(Icons.Filled.Check, null) })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Acompanhar outro sistema") }, onClick = { menu = false; onAdd() }, leadingIcon = { Icon(Icons.Filled.Add, null) })
                        DropdownMenuItem(text = { Text("Parar de acompanhar este") }, onClick = { menu = false; confirmRemove = true }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
                    }
                },
                actions = {
                    IconButton(enabled = !refreshing, onClick = { refresh() }) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.Refresh, "Atualizar")
                    }
                },
                colors = LocalExtra.current.let { ex ->
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = ex.topBar, titleContentColor = ex.barContent, actionIconContentColor = ex.barContent, navigationIconContentColor = ex.barContent,
                    )
                },
            )
        },
        bottomBar = {
            if (sections.size > 1) NavigationBar(containerColor = LocalExtra.current.bottomBar, contentColor = LocalExtra.current.barContent) {
                sections.forEach { sec ->
                    NavigationBarItem(tab == sec.key, { tab = sec.key }, icon = { Icon(sec.icon, null) }, label = { Text(sec.label) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.secondaryContainer))
                }
            }
        },
    ) { pad ->
        ThemedBackground(Modifier.fillMaxSize())
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (f.revoked) item { Banner("O sistema removeu seu acesso ou gerou um novo convite. Peça um novo QR code se precisar.") }
            f.error?.let { item { Banner("Não consegui atualizar: $it") } }
            if (s != null) item {
                Text("Você acompanha como: ${s.profileName}", style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            if (s == null) item { Hint("Carregando…") }
            else if (sections.isEmpty()) item { Hint("O sistema ainda não liberou nada para você ver.") }
            else when (tab) {
                "front" -> frontItems(s, now) { id -> s.people?.find { it.id == id }?.let { person = it } }
                "notices" -> noticeItems(s, now)
                "calendar" -> calendarItems(s)
                "people" -> peopleItems(s) { person = it }
                else -> moreItems(s, now, ctx)
            }
            if (s?.canMessage == true && tab == "front") item { SendMessageCard(f) }
        }
    }
    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text("Parar de acompanhar?") },
        text = { Text("Você deixa de receber avisos deste sistema. Para voltar, vai precisar do convite de novo.") },
        confirmButton = { TextButton(onClick = { Follows.remove(f.id); confirmRemove = false }) { Text("Parar", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancelar") } },
    )
}

@Composable
private fun Banner(text: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x33EF6B6B)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Info, null, tint = Color(0xFFEF6B6B)); Spacer(Modifier.width(8.dp)); Text(text)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.frontItems(s: Snapshot, now: Long, open: (String) -> Unit) {
    val list = s.front.orEmpty()
    item { Header(if (list.size > 1) "No front agora (co-front)" else "No front agora") }
    if (list.isEmpty()) item { Hint("Ninguém registrado no front agora.") }
    items(list.size) { i ->
        val f = list[i]
        val intro = s.people?.find { it.id == f.personId }?.intro.orEmpty()
        val canOpen = f.personId != null && s.people?.any { it.id == f.personId } == true
        SpCard(f.color, onClick = if (canOpen) ({ open(f.personId!!) }) else null) {
            Avatar(f.name, f.color, f.avatar, 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(f.name, style = MaterialTheme.typography.titleMedium)
                if (f.pronouns.isNotBlank()) Text(f.pronouns, color = Pron)
                if (intro.isNotBlank()) Text(intro, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (f.custom) Text("Custom front", style = MaterialTheme.typography.bodySmall, color = Muted)
                Text("Há ${fmtDur(now - f.since)} · desde ${fmtTime(f.since)}", style = MaterialTheme.typography.bodySmall, color = Muted)
                if (f.status.isNotBlank()) Text(f.status, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    item {
        Text(
            "Você recebe uma notificação quando o front muda (o app confere a cada ~15 minutos).",
            style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.noticeItems(s: Snapshot, now: Long) {
    val list = s.notices.orEmpty()
    item { Header("Mural de avisos") }
    if (list.isEmpty()) item { Hint("Nenhum aviso.") }
    items(list.size) { i ->
        val n = list[i]
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(12.dp)).background(Card)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(Color(n.color)))
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (n.pinned) Icon(Icons.Filled.PushPin, null, Modifier.size(14.dp), tint = Gold)
                    if (n.title.isNotBlank()) Text(" " + n.title, style = MaterialTheme.typography.titleSmall)
                }
                if (n.text.isNotBlank()) Text(n.text)
                Text("${n.author} · ${ago(n.time, now)}", style = MaterialTheme.typography.labelSmall, color = Muted)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.calendarItems(s: Snapshot) {
    val events = s.events.orEmpty()
    val today = LocalDate.now()
    val days = (0..60).map { today.plusDays(it.toLong()) }.mapNotNull { d ->
        val evs = events.filter { it.occursOn(d) }
        if (evs.isEmpty()) null else d to evs
    }
    item { Header("Próximos 60 dias") }
    if (days.isEmpty()) item { Hint("Nada marcado nos próximos 60 dias.") }
    days.forEach { (d, evs) ->
        item {
            Text(
                (if (d == today) "Hoje · " else if (d == today.plusDays(1)) "Amanhã · " else "") +
                    d.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", ptBR)),
                color = Gold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp),
            )
        }
        items(evs.size) { i ->
            val e = evs[i]
            SpCard(e.color) {
                Icon(if (e.id.startsWith("bday-")) Icons.Filled.Cake else Icons.Filled.Event, null, tint = Color(e.color))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.titleMedium)
                    Text(if (e.allDay) "Dia todo" else fmtTime(e.start), style = MaterialTheme.typography.bodySmall, color = Muted)
                    Text(e.who, style = MaterialTheme.typography.bodySmall, color = Pron)
                    if (e.description.isNotBlank()) Text(e.description)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.peopleItems(s: Snapshot, open: (SnapPerson) -> Unit) {
    val list = s.people.orEmpty()
    item { Header("Pessoas do sistema (${list.size})") }
    if (list.isEmpty()) item { Hint("Nenhum perfil compartilhado.") }
    items(list.size) { i ->
        val p = list[i]
        SpCard(p.color, onClick = { open(p) }) {
            Avatar(p.name, p.color, p.avatar, 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                if (p.pronouns.isNotBlank()) Text(p.pronouns, color = Pron)
                if (p.role.isNotBlank()) Text(p.role, style = MaterialTheme.typography.bodySmall, color = Muted)
                if (p.intro.isNotBlank()) Text(p.intro, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (s.front?.any { it.personId == p.id } == true) Text("● No front agora", style = MaterialTheme.typography.labelSmall, color = Color(0xFF66BB6A))
            }
            Icon(Icons.Filled.ChevronRight, "Ver perfil", tint = Muted)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.moreItems(s: Snapshot, now: Long, ctx: android.content.Context) {
    s.safety?.let { p ->
        item { Header("Plano de segurança") }
        if (p.info.isNotBlank()) item { Block("Onde estamos e quem somos", p.info) }
        if (p.grounding.isNotBlank()) item { Block("Para acalmar", p.grounding) }
        if (p.steps.isNotBlank()) item { Block("O que fazer numa crise", p.steps) }
        items(p.contacts.size) { i ->
            val c = p.contacts[i]
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, fontWeight = FontWeight.Medium); Text(c.phone, color = Gold)
                    if (c.note.isNotBlank()) Text(c.note, style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                Button(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + c.phone.filter { it.isDigit() || it == '+' }))) } }) {
                    Icon(Icons.Filled.Call, null); Spacer(Modifier.width(6.dp)); Text("Ligar")
                }
            }
        }
    }
    s.body?.let { l ->
        item { Header("Dados gerais do corpo") }
        if (l.isEmpty()) item { Hint("Nada preenchido.") }
        items(l.size) { i -> Block(l[i].label, l[i].value) }
    }
    s.history?.let { l ->
        item { Header("Histórico de front (14 dias)") }
        if (l.isEmpty()) item { Hint("Sem registros.") }
        items(l.size) { i ->
            val h = l[i]
            SpCard(h.color) {
                Column(Modifier.weight(1f)) {
                    Text(h.name, style = MaterialTheme.typography.titleSmall)
                    Text("${fmtDT(h.start)} – ${h.end?.let { fmtDT(it) } ?: "agora"} · ${fmtDur((h.end ?: now) - h.start)}",
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
    }
}

@Composable
private fun Block(title: String, text: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(12.dp)) {
        Text(title, color = Pron, style = MaterialTheme.typography.labelLarge)
        Text(text)
    }
}


/** Recado do companion para o sistema (chega no Plurali, com notificação). */
@Composable
private fun SendMessageCard(f: Followed) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val ui = LocalUi.current
    Column(
        Modifier.padding(top = 12.dp).fillMaxWidth()
            .themedCard(ui, MaterialTheme.colorScheme.surfaceContainer, null, MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.onSurface)
            .padding(start = 14.dp, end = 14.dp, top = 14.dp + ui.cardTopExtra, bottom = 14.dp + ui.cardBottomExtra),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MarkEmailUnread, null, tint = Gold)
            Spacer(Modifier.width(8.dp))
            Text("Mandar recado para o sistema", style = MaterialTheme.typography.titleMedium)
        }
        OutlinedTextField(text, { text = it; msg = null }, placeholder = { Text("Ex.: chego às 19h; estou pensando em vocês") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(msg ?: "Chega no Plurali com uma notificação.", style = MaterialTheme.typography.bodySmall,
                color = if (msg?.startsWith("Erro") == true) MaterialTheme.colorScheme.error else Muted, modifier = Modifier.weight(1f))
            Button(enabled = text.isNotBlank() && !busy, onClick = {
                busy = true
                scope.launch {
                    val err = withContext(Dispatchers.IO) { Poller.sendMessage(f, text.trim()) }
                    busy = false
                    if (err == null) { text = ""; msg = "Recado enviado ✓" } else msg = "Erro: $err"
                }
            }) { Text(if (busy) "Enviando…" else "Enviar") }
        }
    }
}
