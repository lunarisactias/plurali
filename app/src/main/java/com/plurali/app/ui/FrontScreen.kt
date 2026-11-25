package com.plurali.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.navigation.NavHostController
import com.plurali.app.data.*
import kotlinx.coroutines.delay

// ============ Tela inicial: abas Membros / Fronters / Custom fronts ============

@Composable
fun HomeScreen(nav: NavHostController) {
    var tab by rememberSaveable { mutableIntStateOf(1) }
    val openDrawer = LocalOpenDrawer.current
    ScreenScaffold(
        title = "",
        bottomBar = { SpBottomBar(tab, onSelect = { tab = it }, onMenu = openDrawer) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> MembersTab(nav)
                1 -> FrontersTab(nav)
                else -> CustomFrontsTab(nav)
            }
        }
    }
}

@Composable
private fun SpBottomBar(selected: Int, onSelect: (Int) -> Unit, onMenu: () -> Unit) {
    val items = listOf(
        Triple("Membros", Icons.Filled.AccessibilityNew, 0),
        Triple("Fronters", Icons.Filled.People, 1),
        Triple("Custom fronts", Icons.Filled.Mood, 2),
    )
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (label, icon, i) -> BottomItem(label, icon, selected == i) { onSelect(i) } }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onMenu) { Icon(Icons.AutoMirrored.Filled.MenuOpen, "Menu") }
        }
    }
}

@Composable
private fun BottomItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 2.dp)
            .clip(MaterialTheme.shapes.large)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Linha de ícones no topo de cada aba (como a barra do SP). */
@Composable
fun TabToolbar(left: @Composable RowScope.() -> Unit, right: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        left()
        Spacer(Modifier.weight(1f))
        right()
    }
}

@Composable
fun ToolIcon(icon: ImageVector, desc: String, active: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, desc, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

// ============ Aba Fronters ============

@Composable
fun FrontersTab(nav: NavHostController) {
    val d by Store.data.collectAsState()
    val who = LocalWho.current
    val now = rememberNow(1000)
    val current = remember(d.fronts) { d.currentFronts() }
    var compact by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var editEntry by remember { mutableStateOf<FrontEntry?>(null) }
    var editStart by remember { mutableStateOf<FrontEntry?>(null) }
    var comment by remember { mutableStateOf<FrontEntry?>(null) }

    Column(Modifier.fillMaxSize()) {
        TabToolbar(
            left = { if (who.canWrite) ToolIcon(Icons.Filled.PersonRemove, "Tirar todos do front") { if (current.isNotEmpty()) confirmClear = true } },
            right = {
                ToolIcon(Icons.Filled.ViewHeadline, "Lista compacta", compact) { compact = true }
                ToolIcon(Icons.AutoMirrored.Filled.ViewList, "Lista detalhada", !compact) { compact = false }
            },
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // para quem entra sem login: as boas-vindas vêm primeiro
            if (who is Who.Guest && d.welcome.isNotEmpty()) item(key = "welcome") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WelcomeCards(d.welcome.take(2), total = d.welcome.size)
                    if (d.welcome.size > 2) Button(onClick = { nav.go("welcome") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Continuar lendo (${d.welcome.size - 2} ${if (d.welcome.size - 2 == 1) "cartão" else "cartões"})")
                    }
                }
            }
            item(key = "notices") { NoticeBoard(d, now) }
            if (current.isEmpty()) item {
                EmptyHint(if (who.canWrite) "Ninguém no front. Vá na aba Membros (ou Custom fronts) e toque em ↑." else "Ninguém registrado no front agora.")
            }
            items(current, key = { it.id }) { e ->
                val s = d.subject(e.subjectId)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        SpRow(
                            s?.color ?: 0xFF9E9E9E, Modifier.weight(1f),
                            onClick = { openSubject(nav, s) },
                        ) {
                            SubjectAvatar(s, 56.dp)
                            Spacer(Modifier.width(12.dp))
                            NameAndPronouns(s?.name ?: "(excluído)", s?.pronouns ?: "", Modifier.weight(1f))
                            if (compact || !who.canWrite) Text(fmtTimer(now - e.start), style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"))
                        }
                        if (who.canWrite) {
                            Spacer(Modifier.width(8.dp))
                            FrontButton(s?.color ?: 0xFF9E9E9E, fronting = true) { Store.endFront(e.id) }
                        }
                    }
                    if (!compact && who.canWrite) FronterDetails(
                        e, s, now,
                        onCalendar = { editStart = e },
                        onEdit = { editEntry = e },
                        onProfile = { openSubject(nav, s) },
                        onComment = { comment = e },
                    )
                }
            }
        }
    }

    if (confirmClear) ConfirmDialog(
        "Tirar todos do front?", "Todos que estão no front serão marcados como saindo agora.", "Tirar",
        onDismiss = { confirmClear = false },
    ) { Store.clearFront() }
    editEntry?.let { e -> EntryEditDialog(d, d.fronts.find { it.id == e.id } ?: e, isNew = false) { editEntry = null } }
    editStart?.let { e ->
        DateTimePickerDialog(e.start, onDismiss = { editStart = null }) { t ->
            d.fronts.find { it.id == e.id }?.let { Store.upsertEntry(it.copy(start = minOf(t, System.currentTimeMillis()))) }
            editStart = null
        }
    }
    comment?.let { e ->
        var text by remember(e.id) { mutableStateOf(d.fronts.find { it.id == e.id }?.comment ?: "") }
        AlertDialog(
            onDismissRequest = { comment = null },
            title = { Text("Comentário") },
            text = { OutlinedTextField(text, { text = it }, minLines = 3, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    d.fronts.find { it.id == e.id }?.let { Store.upsertEntry(it.copy(comment = text.trim())) }
                    comment = null
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { comment = null }) { Text("Cancelar") } },
        )
    }
}

// ============ Mural de avisos ============

@Composable
private fun NoticeBoard(d: AppData, now: Long) {
    val who = LocalWho.current
    var open by rememberSaveable { mutableStateOf(true) }
    var edit by remember { mutableStateOf<Notice?>(null) }
    val list = remember(d.notices) { d.notices.sortedWith(compareBy<Notice>({ !it.pinned }, { -it.time })) }
    Column(
        Modifier.fillMaxWidth().themed()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Campaign, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Mural de avisos", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).clickable { open = !open })
            if (list.isNotEmpty()) Text("${list.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            if (who.canWrite) IconButton(onClick = {
                edit = Notice(newId(), who.meId, "", "", System.currentTimeMillis())
            }) { Icon(Icons.Filled.Add, "Novo aviso") }
            IconButton(onClick = { open = !open }) {
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (open) "Recolher" else "Expandir")
            }
        }
        if (open) {
            if (list.isEmpty()) Text(
                "Nenhum aviso. Use o mural para recados que todo mundo deve ver ao abrir o app.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            list.forEach { n ->
                val canEdit = who.isAdmin || (n.authorId != null && n.authorId == who.meId)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).height(IntrinsicSize.Min)
                        .clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(enabled = canEdit) { edit = n },
                ) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(Color(n.color)))
                    Column(Modifier.padding(10.dp).weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (n.pinned) { Icon(Icons.Filled.PushPin, "Fixado", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(4.dp)) }
                            if (n.title.isNotBlank()) Text(n.title, style = MaterialTheme.typography.titleSmall)
                        }
                        if (n.text.isNotBlank()) Text(n.text, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${d.subject(n.authorId)?.name ?: "Sistema"} · ${fmtWhen(n.time, now)}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    edit?.let { n ->
        val isNew = d.notices.none { it.id == n.id }
        var title by remember(n.id) { mutableStateOf(n.title) }
        var text by remember(n.id) { mutableStateOf(n.text) }
        var pinned by remember(n.id) { mutableStateOf(n.pinned) }
        var color by remember(n.id) { mutableLongStateOf(n.color) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(if (isNew) "Novo aviso" else "Editar aviso") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(text, { text = it }, label = { Text("Aviso") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().clickable { pinned = !pinned }, verticalAlignment = Alignment.CenterVertically) {
                        Text("Fixar no topo", Modifier.weight(1f)); Switch(pinned, { pinned = it })
                    }
                    ColorPicker(color) { color = it }
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank() || text.isNotBlank(), onClick = {
                    Store.upsertNotice(n.copy(title = title.trim(), text = text.trim(), pinned = pinned, color = color)); edit = null
                }) { Text("Salvar") }
            },
            dismissButton = {
                Row {
                    if (!isNew) TextButton(onClick = { Store.deleteNotice(n.id); edit = null }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { edit = null }) { Text("Cancelar") }
                }
            },
        )
    }
}

fun openSubject(nav: NavHostController, s: Subject?) {
    if (s == null) return
    if (s.custom) nav.go("customFrontEdit/${s.id}") else nav.go("member/${s.id}")
}

@Composable
private fun FronterDetails(
    e: FrontEntry,
    s: Subject?,
    now: Long,
    onCalendar: () -> Unit,
    onEdit: () -> Unit,
    onProfile: () -> Unit,
    onComment: () -> Unit,
) {
    val color = Color(s?.color ?: 0xFF9E9E9E)
    var status by remember(e.id) { mutableStateOf(e.status) }
    LaunchedEffect(status) {
        if (status != e.status) {
            delay(700)
            Store.data.value.fronts.find { it.id == e.id }?.let { Store.upsertEntry(it.copy(status = status.trim())) }
        }
    }
    val focus = LocalFocusManager.current
    Column(
        Modifier.fillMaxWidth().themed().padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                fmtTimer(now - e.start),
                fontSize = 18.sp,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.clip(MaterialTheme.shapes.small).background(com.plurali.app.LocalExtra.current.timer).padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onCalendar) { Icon(Icons.Filled.CalendarMonth, "Mudar início") }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Editar registro") }
            IconButton(onClick = onProfile) { Icon(Icons.Filled.Person, "Perfil") }
            IconButton(onClick = onComment) {
                if (e.comment.isNotBlank()) BadgedBox(badge = { Badge() }) { Icon(Icons.AutoMirrored.Filled.Chat, "Comentário") }
                else Icon(Icons.AutoMirrored.Filled.Chat, "Comentário")
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            val lineColor = if (status.isNotBlank()) color else MaterialTheme.colorScheme.outline
            BasicTextField(
                value = status,
                onValueChange = { status = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                modifier = Modifier.weight(1f).padding(end = 12.dp).drawBehind {
                    drawLine(lineColor, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx())
                }.padding(vertical = 6.dp),
                decorationBox = { inner ->
                    if (status.isEmpty()) Text(
                        "Status (ex.: co-front, co-consciente)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    inner()
                },
            )
            Text(fmtShort(e.start), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ============ Aba Custom fronts ============

@Composable
fun CustomFrontsTab(nav: NavHostController) {
    val d by Store.data.collectAsState()
    val who = LocalWho.current
    val ctx = LocalContext.current
    var search by rememberSaveable { mutableStateOf<String?>(null) }
    val fronting = remember(d.fronts) { d.currentFronts().associateBy { it.subjectId } }
    val list = remember(d.customFronts, search) {
        d.customFronts.filter { search.isNullOrBlank() || it.name.contains(search!!, true) }.sortedBy { it.name.lowercase() }
    }
    Column(Modifier.fillMaxSize()) {
        TabToolbar(
            left = { if (who.canWrite) ToolIcon(Icons.Filled.AddCircleOutline, "Novo custom front") { nav.go("customFrontEdit/new") } },
            right = { ToolIcon(Icons.Filled.Search, "Buscar", search != null) { search = if (search == null) "" else null } },
        )
        search?.let { q -> SearchField(q) { search = it } }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (list.isEmpty()) item {
                EmptyHint("Custom fronts são estados que vão pro front como membros: “Blurry”, “Dissociado”, “Desconhecido”… Toque em ⊕ para criar.")
            }
            items(list, key = { it.id }) { c ->
                val f = fronting[c.id]
                Row(Modifier.height(IntrinsicSize.Min)) {
                    SpRow(c.color, Modifier.weight(1f), onClick = { if (who.canWrite) nav.go("customFrontEdit/${c.id}") }) {
                        Avatar(c.name, c.color, null, 56.dp)
                        Spacer(Modifier.width(12.dp))
                        NameAndPronouns(c.name, "", Modifier.weight(1f), extra = c.description)
                    }
                    if (who.canWrite) Spacer(Modifier.width(8.dp))
                    if (who.canWrite) FrontButton(
                        c.color, fronting = f != null,
                        onLongClick = {
                            Store.switchFront(c.id, true)
                            Toast.makeText(ctx, "${c.name} agora está sozinho no front", Toast.LENGTH_SHORT).show()
                        },
                    ) { if (f != null) Store.endFront(f.id) else Store.addFront(c.id, true) }
                }
            }
        }
    }
}

@Composable
fun SearchField(q: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        q, onChange, placeholder = { Text("Buscar") }, singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, "Limpar") } },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
