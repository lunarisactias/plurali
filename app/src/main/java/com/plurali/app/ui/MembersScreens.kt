package com.plurali.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.plurali.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemberDetailScreen(nav: NavHostController, id: String) {
    val d by Store.data.collectAsState()
    val who = LocalWho.current
    val me = who.meId
    val m = d.members.find { it.id == id }
    val now = rememberNow()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var entryEdit by remember { mutableStateOf<FrontEntry?>(null) }
    var wishEdit by remember { mutableStateOf<WishItem?>(null) }

    if (m == null) {
        ScreenScaffold("Membro", onBack = { nav.popBackStack() }) { pad ->
            Box(Modifier.padding(pad).padding(16.dp)) { EmptyHint("Membro não encontrado.") }
        }
        return
    }
    val private = who.canSeePrivateOf(m.id)
    val current = d.fronts.find { it.end == null && it.subjectId == m.id }
    val entries = remember(d.fronts, m.id) { d.fronts.filter { it.subjectId == m.id } }
    val notes = remember(d.notes, m.id) { d.notes.filter { it.memberId == m.id }.sortedByDescending { it.time } }
    val groups = d.groups.filter { m.id in it.memberIds }
    val rels = remember(d.relations, m.id) { d.relations.filter { it.a == m.id || it.b == m.id } }
    var relEdit by remember { mutableStateOf<Relation?>(null) }
    val tabs = buildList {
        add("Perfil")
        add("Relações (${rels.size})")
        if (who.canWrite) add("Wishlist (${m.wishlist.count { !it.done }})")
        if (private) add("Notas (${notes.size})")
        if (who.canWrite) add("Front")
    }
    val tabName = tabs.getOrElse(tab) { tabs[0] }.substringBefore(" (")

    ScreenScaffold(
        m.name,
        onBack = { nav.popBackStack() },
        actions = {
            if (who.canEditMember(m.id)) IconButton(onClick = { nav.go("memberEdit/${m.id}") }) { Icon(Icons.Filled.Edit, "Editar") }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                // capa com o avatar sobreposto na borda de baixo
                if (m.banner != null) Box(Modifier.fillMaxWidth().height(150.dp + 56.dp)) {
                    StoredImage(m.banner, Modifier.fillMaxWidth().height(150.dp))
                    Avatar(m.name, m.color, m.avatar, 112.dp, Modifier.align(Alignment.BottomCenter))
                }
                Column(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = if (m.banner != null) 8.dp else 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (m.banner == null) {
                        Avatar(m.name, m.color, m.avatar, 112.dp)
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(m.name, style = MaterialTheme.typography.headlineSmall)
                    val sub = listOf(m.pronouns, m.role).filter { it.isNotBlank() }.joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (m.archived) AssistChip({}, label = { Text("Arquivado") })
                    Spacer(Modifier.height(12.dp))
                    if (current != null) Text("No front há ${fmtDuration(now - current.start)}", color = MaterialTheme.colorScheme.primary)
                    if (who.canWrite) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        if (current != null) OutlinedButton(onClick = { Store.endFront(current.id) }) {
                            Icon(Icons.Filled.Close, null); Spacer(Modifier.width(6.dp)); Text("Sair do front")
                        } else {
                            FilledTonalButton(onClick = { Store.addFront(m.id, false) }) {
                                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Co-front")
                            }
                            Button(onClick = { Store.switchFront(m.id, false) }) {
                                Icon(Icons.Filled.SwapHoriz, null); Spacer(Modifier.width(6.dp)); Text("Trocar")
                            }
                        }
                        if (me != null && me != m.id) FilledTonalButton(onClick = {
                            nav.go("chat/${Store.directChat(me, m.id)}")
                        }) { Icon(Icons.AutoMirrored.Filled.Chat, "Conversar") }
                    }
                }
            }
            item {
                ScrollableTabRow(selectedTabIndex = tab.coerceIn(0, tabs.size - 1), edgePadding = 8.dp) {
                    tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1) }) }
                }
            }
            when (tabName) {
                "Relações" -> {
                    if (who.canWrite) item {
                        FilledTonalButton(onClick = { relEdit = Relation(newId(), m.id, "", "") }, modifier = Modifier.padding(16.dp)) {
                            Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Nova relação")
                        }
                    }
                    if (rels.isEmpty()) item { Box(Modifier.padding(horizontal = 16.dp)) { EmptyHint("Nenhuma relação registrada.") } }
                    items(rels, key = { "rel-" + it.id }) { r ->
                        val other = d.members.find { it.id == (if (r.a == m.id) r.b else r.a) }
                        val aName = d.members.find { it.id == r.a }?.name ?: "?"
                        val bName = d.members.find { it.id == r.b }?.name ?: "?"
                        ListItem(
                            modifier = Modifier.clickable { if (who.canWrite) relEdit = r else if (other != null) nav.go("member/${other.id}") },
                            leadingContent = { Avatar(other?.name ?: "?", other?.color ?: 0xFF9E9E9E, other?.avatar, 40.dp) },
                            headlineContent = { Text("$aName ${r.type.lowercase()} $bName") },
                            supportingContent = { if (r.note.isNotBlank()) Text(r.note) },
                            trailingContent = {
                                if (other != null) IconButton(onClick = { nav.go("member/${other.id}") }) { Icon(Icons.Filled.Person, "Ver perfil") }
                            },
                        )
                    }
                }
                "Perfil" -> item {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (m.comfort.isNotBlank()) {
                            Column(
                                Modifier.fillMaxWidth().themed().padding(12.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Spa, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(6.dp))
                                    Text("Como me acalmar", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                Text(m.comfort)
                            }
                        }
                        if (m.description.isNotBlank()) { SectionHeader("Descrição"); Text(m.description) }
                        if (m.intro.isNotBlank() && who.canWrite) { SectionHeader("Apresentação para companions"); Text(m.intro) }
                        if (m.likes.isNotBlank()) { SectionHeader("Gosta de"); Text(m.likes) }
                        if (m.dislikes.isNotBlank()) { SectionHeader("Não gosta de"); Text(m.dislikes) }
                        if (m.birthday.isNotBlank()) { SectionHeader("Aniversário"); Text(m.birthday) }
                        d.fields.filter { who.canSeeField(it, m.id) }.forEach { f ->
                            val v = m.fields[f.id]
                            if (!v.isNullOrBlank()) { SectionHeader(f.name); Text(v) }
                        }
                        if (groups.isNotEmpty()) {
                            SectionHeader("Grupos")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                groups.forEach { g ->
                                    AssistChip(
                                        onClick = { if (who.canWrite) nav.go("groupEdit/${g.id}") },
                                        label = { Text((if (g.emoji.isNotBlank()) g.emoji + " " else "") + g.name) },
                                        leadingIcon = { Avatar("", g.color, null, 12.dp) },
                                    )
                                }
                            }
                        }
                        SectionHeader("Cor")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar("", m.color, null, 20.dp); Spacer(Modifier.width(8.dp)); Text("#" + hexOf(m.color))
                        }
                        Text(
                            "Criado em ${fmtDateTime(m.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
                "Wishlist" -> {
                    val canEdit = who.canEditMember(m.id)
                    listOf("buy" to "Quero comprar", "do" to "Quero fazer").forEach { (kind, title) ->
                        item(key = "wh-$kind") {
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                SectionHeader(title, Modifier.weight(1f))
                                if (canEdit) TextButton(onClick = { wishEdit = WishItem(newId(), kind, "") }) {
                                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Adicionar")
                                }
                            }
                        }
                        val list = m.wishlist.filter { it.kind == kind }.sortedWith(compareBy({ it.done }, { -it.createdAt }))
                        if (list.isEmpty()) item(key = "we-$kind") {
                            Text("Nada ainda.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                        items(list, key = { "w-" + it.id }) { w ->
                            ListItem(
                                modifier = Modifier.clickable(enabled = canEdit) { wishEdit = w },
                                leadingContent = {
                                    Checkbox(w.done, { if (canEdit) Store.upsertWish(m.id, w.copy(done = it)) }, enabled = canEdit)
                                },
                                headlineContent = {
                                    Text(w.text, textDecoration = if (w.done) TextDecoration.LineThrough else null,
                                        color = if (w.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                                },
                                supportingContent = { if (w.note.isNotBlank()) Text(w.note, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
                "Notas" -> {
                    item {
                        FilledTonalButton(onClick = { nav.go("noteEdit/${m.id}/new") }, modifier = Modifier.padding(16.dp)) {
                            Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Nova nota")
                        }
                    }
                    item {
                        Text(
                            "Notas são privadas: só ${m.name} e o Sistema podem ver.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    if (notes.isEmpty()) item { Box(Modifier.padding(horizontal = 16.dp)) { EmptyHint("Nenhuma nota.") } }
                    items(notes, key = { it.id }) { n ->
                        ListItem(
                            modifier = Modifier.clickable { nav.go("noteEdit/${m.id}/${n.id}") },
                            headlineContent = { Text(n.title.ifBlank { "(sem título)" }) },
                            supportingContent = {
                                Column {
                                    Text(n.body, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    Text(fmtDateTime(n.time), style = MaterialTheme.typography.bodySmall)
                                }
                            },
                        )
                    }
                }
                else -> {
                    item {
                        val month = entries.sumOf { e ->
                            val s = maxOf(e.start, now - 30L * 86_400_000)
                            val en = e.end ?: now
                            if (en > s) en - s else 0L
                        }
                        val total = entries.sumOf { (it.end ?: now) - it.start }
                        Column(Modifier.padding(16.dp)) {
                            Text("Últimos 30 dias: ${fmtDuration(month)}")
                            Text("Total registrado: ${fmtDuration(total)} em ${entries.size} entradas")
                        }
                    }
                    if (entries.isEmpty()) item { Box(Modifier.padding(horizontal = 16.dp)) { EmptyHint("Ainda não esteve no front.") } }
                    historyItems(d, entries, now, "m") { entryEdit = it }
                }
            }
        }
    }
    entryEdit?.let { e -> EntryEditDialog(d, e, isNew = false) { entryEdit = null } }
    relEdit?.let { r -> RelationDialog(d, r, m.id) { relEdit = null } }
    wishEdit?.let { w ->
        val isNew = m.wishlist.none { it.id == w.id }
        var text by remember(w.id) { mutableStateOf(w.text) }
        var note by remember(w.id) { mutableStateOf(w.note) }
        AlertDialog(
            onDismissRequest = { wishEdit = null },
            title = { Text(if (w.kind == "buy") "Quero comprar" else "Quero fazer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(text, { text = it }, label = { Text("O quê?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(note, { note = it }, label = { Text(if (w.kind == "buy") "Link, preço, loja…" else "Detalhes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    Store.upsertWish(m.id, w.copy(text = text.trim(), note = note.trim())); wishEdit = null
                }) { Text("Salvar") }
            },
            dismissButton = {
                Row {
                    if (!isNew) TextButton(onClick = { Store.deleteWish(m.id, w.id); wishEdit = null }) {
                        Text("Excluir", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { wishEdit = null }) { Text("Cancelar") }
                }
            },
        )
    }
}

@Composable
fun MemberEditScreen(nav: NavHostController, id: String) {
    val d = remember { Store.data.value }
    val who = LocalWho.current
    val existing = d.members.find { it.id == id }
    var m by remember { mutableStateOf(existing ?: Member(id = newId(), name = "", color = palette.random())) }
    var groups by remember { mutableStateOf(d.groups.filter { m.id in it.memberIds }.map { it.id }.toSet()) }
    var nameError by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var setPw by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val p = withContext(Dispatchers.IO) { saveAvatar(ctx, uri, m.id) }
            if (p != null) m = m.copy(avatar = p)
        }
    }
    val pickBanner = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val p = withContext(Dispatchers.IO) { saveImage(ctx, uri, "banners", m.id, aspect = 2.6f, maxSide = 1400) }
            if (p != null) m = m.copy(banner = p)
        }
    }
    val live by Store.data.collectAsState()
    val livePw = live.members.find { it.id == m.id }?.password

    if (!(if (existing == null) who.isAdmin || who is Who.Member else who.canEditMember(id))) {
        ScreenScaffold("Editar", onBack = { nav.popBackStack() }) { pad ->
            Box(Modifier.padding(pad).padding(16.dp)) { EmptyHint("Você não tem permissão para editar este membro.") }
        }
        return
    }

    ScreenScaffold(
        if (existing == null) "Novo membro" else "Editar ${existing.name}",
        onBack = { nav.popBackStack() },
        actions = {
            if (existing != null && who.isAdmin) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Excluir") }
            TextButton(onClick = {
                if (m.name.isBlank()) nameError = true
                else {
                    // a senha é gerenciada à parte; não sobrescrever com a cópia antiga
                    Store.upsertMember(m.copy(name = m.name.trim(), password = livePw, wishlist = live.members.find { it.id == m.id }?.wishlist ?: m.wishlist))
                    if (who.isAdmin || existing == null) Store.setMemberGroups(m.id, groups)
                    nav.popBackStack()
                }
            }) { Text("Salvar") }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(m.name, m.color, m.avatar, 88.dp, Modifier.clickable {
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
                Spacer(Modifier.width(16.dp))
                Column {
                    TextButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Filled.Image, null); Spacer(Modifier.width(6.dp)); Text("Escolher foto")
                    }
                    if (m.avatar != null) TextButton(onClick = { m = m.copy(avatar = null) }) { Text("Remover foto") }
                }
            }
            Box(
                Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { pickBanner.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center,
            ) {
                if (m.banner != null) StoredImage(m.banner, Modifier.fillMaxSize())
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Wallpaper, null); Spacer(Modifier.width(6.dp)); Text("Escolher capa do perfil")
                }
            }
            if (m.banner != null) TextButton(onClick = { m = m.copy(banner = null) }) { Text("Remover capa") }
            OutlinedTextField(
                m.name, { m = m.copy(name = it); nameError = false }, label = { Text("Nome *") },
                isError = nameError, supportingText = { if (nameError) Text("Informe um nome") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(m.pronouns, { m = m.copy(pronouns = it) }, label = { Text("Pronomes") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                m.role, { m = m.copy(role = it) }, label = { Text("Papéis / funções") }, singleLine = true,
                supportingText = { Text("Separe por vírgula: Protetor, Host… (usado no filtro do calendário)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                m.birthday, { m = m.copy(birthday = it) }, label = { Text("Aniversário") }, singleLine = true,
                supportingText = { Text("dd/mm ou dd/mm/aaaa — aparece no calendário") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(m.description, { m = m.copy(description = it) }, label = { Text("Descrição") }, minLines = 4, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                m.comfort, { m = m.copy(comfort = it) }, label = { Text("Como me acalmar") }, minLines = 2, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Aparece em destaque no perfil, inclusive para quem entra sem login.") },
            )
            OutlinedTextField(
                m.intro, { m = m.copy(intro = it) }, label = { Text("Apresentação para companions") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Para pessoas de fora do sistema: quem é você, como te reconhecer, como falar com você.") },
            )
            OutlinedTextField(m.likes, { m = m.copy(likes = it) }, label = { Text("Gosta de") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(m.dislikes, { m = m.copy(dislikes = it) }, label = { Text("Não gosta de") }, minLines = 2, modifier = Modifier.fillMaxWidth())

            Text("Cor", style = MaterialTheme.typography.titleSmall)
            ColorPicker(m.color) { m = m.copy(color = it) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Campos personalizados", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { nav.go("fields") }) { Text("Gerenciar") }
            }
            if (live.fields.isEmpty()) Text(
                "Nenhum campo criado. Ex.: Idade, Gostos, Gatilhos, Função no sistema…",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            live.fields.forEach { f ->
                OutlinedTextField(
                    m.fields[f.id] ?: "", { v -> m = m.copy(fields = m.fields + (f.id to v)) },
                    label = { Text(f.name) }, modifier = Modifier.fillMaxWidth(),
                )
            }

            if (d.groups.isNotEmpty() && (who.isAdmin || existing == null)) {
                Text("Pastas", style = MaterialTheme.typography.titleSmall)
                d.groupTree().forEach { (g, depth) ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            groups = if (g.id in groups) groups - g.id else groups + g.id
                        }.padding(start = (depth * 20).dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(g.id in groups, { groups = if (it) groups + g.id else groups - g.id })
                        Text((if (g.emoji.isNotBlank()) g.emoji + " " else "") + g.name)
                    }
                }
            }

            if (live.loginsEnabled && existing != null && (who.isAdmin || who.meId == m.id)) {
                Text("Login", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (livePw != null) "Este membro já tem senha de login." else "Sem senha: este membro ainda não consegue entrar com o próprio login.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { setPw = true }) {
                        Icon(Icons.Filled.Key, null); Spacer(Modifier.width(6.dp))
                        Text(if (livePw == null) "Criar senha" else "Trocar senha")
                    }
                    if (livePw != null && who.isAdmin) TextButton(onClick = { Store.setMemberPassword(m.id, null) }) { Text("Remover senha") }
                }
            }

            if (who.isAdmin) Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Arquivado")
                    Text(
                        "Esconde da lista e do seletor de front, sem apagar nada.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(m.archived, { m = m.copy(archived = it) })
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (setPw) NewPasswordDialog("Senha de ${m.name.ifBlank { "membro" }}", onDismiss = { setPw = false }) { pw ->
        scope.launch {
            val s = withContext(Dispatchers.Default) { Crypto.hash(pw) }
            Store.setMemberPassword(m.id, s)
        }
    }
    if (confirmDelete && existing != null) ConfirmDialog(
        "Excluir ${existing.name}?",
        "Isso apaga o membro, o histórico de front e as notas dele. Não dá para desfazer. Se quiser só esconder, use “Arquivado”.",
        onDismiss = { confirmDelete = false },
    ) {
        Store.deleteMember(existing.id)
        if (!nav.popBackStack("home", false)) nav.popBackStack()
    }
}

@Composable
fun NoteEditScreen(nav: NavHostController, memberId: String, id: String) {
    val d = remember { Store.data.value }
    val who = LocalWho.current
    val existing = d.notes.find { it.id == id }
    var n by remember { mutableStateOf(existing ?: Note(newId(), memberId, "", "", System.currentTimeMillis())) }
    var confirmDelete by remember { mutableStateOf(false) }
    val who2 = d.members.find { it.id == memberId }?.name ?: ""
    if (!who.canSeePrivateOf(memberId)) {
        ScreenScaffold("Nota", onBack = { nav.popBackStack() }) { pad ->
            Box(Modifier.padding(pad).padding(16.dp)) { EmptyHint("Notas são privadas.") }
        }
        return
    }
    ScreenScaffold(
        if (existing == null) "Nova nota · $who2" else "Nota · $who2",
        onBack = { nav.popBackStack() },
        actions = {
            if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Excluir") }
            TextButton(
                enabled = n.title.isNotBlank() || n.body.isNotBlank(),
                onClick = { Store.upsertNote(n); nav.popBackStack() },
            ) { Text("Salvar") }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(n.title, { n = n.copy(title = it) }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(n.body, { n = n.copy(body = it) }, label = { Text("Texto") }, minLines = 12, modifier = Modifier.fillMaxWidth())
            Text(fmtDateTime(n.time), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirmDelete) ConfirmDialog("Excluir nota?", "Não dá para desfazer.", onDismiss = { confirmDelete = false }) {
        Store.deleteNote(n.id); nav.popBackStack()
    }
}
