package com.plurali.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.plurali.app.data.*

// ===================== Lista de conversas =====================

@Composable
fun ChatsScreen(nav: NavHostController) {
    val d by Store.data.collectAsState()
    val who = LocalWho.current
    val me = who.meId
    val now = rememberNow()
    val chats = remember(d.chats, d.messages, who) {
        d.chats.filter { who.canSeeChat(it) }
            .sortedByDescending { c -> d.messages.filter { it.chatId == c.id }.maxOfOrNull { it.time } ?: c.createdAt }
    }
    ScreenScaffold(
        "Conversas",
        fab = {
            if (who.canWrite && who !is Who.Admin) ExtendedFloatingActionButton(
                onClick = { nav.go("chatNew") },
                icon = { Icon(Icons.Filled.AddComment, null) },
                text = { Text("Nova conversa") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (who is Who.Admin) item {
                EmptyHint("As conversas são privadas entre os membros. Entre como um membro para conversar.")
            } else if (chats.isEmpty()) item {
                EmptyHint("Nenhuma conversa ainda. Crie uma conversa direta (de uma pessoa pra outra) ou um grupo.")
            }
            items(chats, key = { it.id }) { c ->
                val last = d.messages.filter { it.chatId == c.id }.maxByOrNull { it.time }
                val unread = d.unreadIn(c, me)
                val other = if (!c.group) d.members.find { it.id in c.memberIds && it.id != me } else null
                val color = other?.color ?: 0xFF5C6BC0
                SpRow(color, onClick = { nav.go("chat/${c.id}") }) {
                    if (c.group) Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(com.plurali.app.LocalExtra.current.myBubble),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Groups, null, tint = MaterialTheme.colorScheme.primary) }
                    else Avatar(other?.name ?: "?", color, other?.avatar, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            d.chatTitle(c, me), style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (who is Who.Open && !c.group) Text(
                            c.memberIds.mapNotNull { id -> d.members.find { it.id == id }?.name }.joinToString(" ↔ "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                        )
                        if (last != null) Text(
                            "${d.subject(last.authorId)?.name ?: "Anônimo"}: ${last.text.ifBlank { "📷 Foto" }}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (last != null) Text(fmtWhen(last.time, now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (unread > 0) Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("$unread") }
                    }
                }
            }
        }
    }
}

// ===================== Nova conversa =====================

@Composable
fun NewChatScreen(nav: NavHostController) {
    val d = remember { Store.data.value }
    val who = LocalWho.current
    val me = who.meId
    var selected by remember { mutableStateOf(setOfNotNull(me)) }
    var name by remember { mutableStateOf("") }
    val members = d.members.filter { !it.archived }.sortedBy { it.name.lowercase() }
    val isGroup = selected.size > 2 || name.isNotBlank()
    ScreenScaffold(
        "Nova conversa", onBack = { nav.popBackStack() },
        actions = {
            TextButton(enabled = selected.size >= 2, onClick = {
                val ids = selected.toList()
                val id = if (!isGroup) Store.directChat(ids[0], ids[1])
                else Chat(newId(), name.trim().ifBlank { "Grupo" }, ids, group = true).also { Store.upsertChat(it) }.id
                nav.navigate("chat/$id") { popUpTo("chats") }
            }) { Text("Criar") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (me != null) "Escolha uma pessoa para uma conversa direta, ou várias (com um nome) para um grupo."
                else "Escolha 2 pessoas para uma conversa direta, ou mais (com um nome) para um grupo.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(name, { name = it }, label = { Text("Nome do grupo (opcional para conversa direta)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(if (isGroup) "Grupo com ${selected.size} pessoas" else "Conversa direta", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            members.forEach { m ->
                val fixed = m.id == me
                val on = m.id in selected
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !fixed) { selected = if (on) selected - m.id else selected + m.id },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(on, { selected = if (it) selected + m.id else selected - m.id }, enabled = !fixed)
                    Avatar(m.name, m.color, m.avatar, 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(m.name + if (fixed) " (você)" else "")
                }
            }
        }
    }
}

// ===================== Conversa =====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(nav: NavHostController, id: String) {
    val d by Store.data.collectAsState()
    val who = LocalWho.current
    val me = who.meId
    val ctx = LocalContext.current
    val now = rememberNow(5_000)
    val c = d.chats.find { it.id == id }
    if (c == null || !who.canSeeChat(c)) {
        ScreenScaffold("Conversa", onBack = { nav.popBackStack() }) { pad ->
            Box(Modifier.padding(pad).padding(16.dp)) { EmptyHint("Conversa não encontrada ou sem acesso.") }
        }
        return
    }
    val msgs = remember(d.messages, id) { d.messages.filter { it.chatId == id }.sortedBy { it.time } }
    // no modo sem logins, escolhe quem está escrevendo (padrão: alguém da conversa que está no front)
    var writer by remember(id) {
        mutableStateOf(me ?: d.currentFronts().map { it.subjectId }.firstOrNull { it in c.memberIds } ?: c.memberIds.firstOrNull())
    }
    var text by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ChatMessage?>(null) }
    var pickWriter by remember { mutableStateOf(false) }
    var editGroup by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<ChatMessage?>(null) }
    var showPins by remember { mutableStateOf(false) }
    var viewImage by remember { mutableStateOf<String?>(null) }
    var attached by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            attached = withContext(Dispatchers.IO) { saveImage(ctx, uri, "chat", id.take(8), aspect = null, maxSide = 1280) }
        }
    }
    var recording by remember { mutableStateOf(false) }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok && AudioRec.start(ctx)) recording = true
    }
    DisposableEffect(Unit) { onDispose { if (AudioRec.active) AudioRec.cancel(); AudioPlay.stop() } }
    val actor = me ?: writer
    val pins = msgs.filter { it.pinned }
    val list = rememberLazyListState()
    LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) list.scrollToItem(msgs.size - 1)
        if (me != null && me in c.memberIds) Store.markChatRead(id, me)
    }

    ScreenScaffold(
        d.chatTitle(c, me),
        onBack = { nav.popBackStack() },
        actions = {
            if (pins.isNotEmpty()) IconButton(onClick = { showPins = true }) {
                BadgedBox(badge = { Badge { Text("${pins.size}") } }) { Icon(Icons.Filled.PushPin, "Mensagens fixadas") }
            }
            if (c.group && who.canWrite) IconButton(onClick = { editGroup = true }) { Icon(Icons.Filled.GroupAdd, "Editar grupo") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (c.group) Text(
                c.memberIds.mapNotNull { mid -> d.members.find { it.id == mid }?.name }.joinToString(", "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(), state = list,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (msgs.isEmpty()) item { EmptyHint("Nenhuma mensagem ainda. Mensagens não podem ser apagadas e só podem ser editadas no primeiro minuto.") }
                val lastMine = msgs.lastOrNull { it.authorId != null && it.authorId == actor }
                items(msgs, key = { it.id }) { m ->
                    val author = d.subject(m.authorId)
                    val mine = m.authorId != null && m.authorId == actor
                    val editable = mine && m.canEdit(now)
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                        Row(verticalAlignment = Alignment.Top) {
                            if (!mine) { SubjectAvatar(author, 32.dp); Spacer(Modifier.width(6.dp)) }
                            Column(
                                Modifier.widthIn(max = 290.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (mine) com.plurali.app.LocalExtra.current.myBubble else MaterialTheme.colorScheme.surfaceContainer)
                                    .combinedClickable(
                                        onClick = { m.image?.let { viewImage = it } },
                                        onLongClick = { if (who.canWrite && who !is Who.Admin) menuFor = m },
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                if (!mine) Text(author?.name ?: "Anônimo", color = readable(author?.color ?: 0xFFA0A3AB), style = MaterialTheme.typography.labelLarge)
                                if (m.pinned) Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.PushPin, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(" fixada", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                if (m.image != null) StoredImage(
                                    m.image, Modifier.padding(vertical = 4.dp).sizeIn(maxWidth = 260.dp, maxHeight = 260.dp).clip(RoundedCornerShape(10.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                )
                                if (m.audio != null) AudioBubble(m.audio, m.audioMs)
                                if (m.text.isNotBlank()) Text(m.text)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        fmtWhen(m.time, now) + if (m.editedAt != null) " · editada" else "",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (editable) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(Icons.Filled.Edit, "Editável", Modifier.size(12.dp).clickable { editing = m }, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                        // reações
                        if (m.reactions.isNotEmpty()) Row(
                            Modifier.padding(start = if (mine) 0.dp else 38.dp, top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            m.reactions.values.groupingBy { it }.eachCount().forEach { (emoji, n) ->
                                val minePick = actor != null && m.reactions[actor] == emoji
                                Text(
                                    if (n > 1) "$emoji $n" else emoji,
                                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                        .background(if (minePick) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .clickable(enabled = actor != null && who.canWrite) { Store.react(m.id, actor!!, emoji) }
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        // visto por (só na minha última mensagem)
                        if (m == lastMine) {
                            val seen = c.memberIds.filter { it != actor && (c.readBy[it] ?: 0L) >= m.time }
                                .mapNotNull { sid -> d.members.find { it.id == sid }?.name }
                            if (seen.isNotEmpty()) Text(
                                if (c.group) "Visto por ${seen.joinToString(", ")}" else "Visto",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp, end = 4.dp),
                            )
                        }
                    }
                }
            }
            if (who.canWrite && who !is Who.Admin) {
                if (who is Who.Open) AssistChip(
                    onClick = { pickWriter = true },
                    label = { Text("Escrevendo como: ${d.subject(writer)?.name ?: "escolher"}") },
                    leadingIcon = { SubjectAvatar(d.subject(writer), 18.dp) },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                attached?.let { a ->
                    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StoredImage(a, Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
                        TextButton(onClick = { attached = null }) { Text("Tirar foto") }
                    }
                }
                if (recording) RecordingBar(
                    onCancel = { AudioRec.cancel(); recording = false },
                    onSend = { AudioRec.stop()?.let { (p, ms) -> Store.sendChatMessage(id, writer, "", audio = p, audioMs = ms) }; recording = false },
                ) else Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Filled.AddPhotoAlternate, "Enviar foto")
                    }
                    OutlinedTextField(
                        text, { text = it }, placeholder = { Text("Escreva algo") },
                        modifier = Modifier.weight(1f), maxLines = 5, shape = RoundedCornerShape(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    if (text.isBlank() && attached == null) FilledIconButton(
                        enabled = writer != null,
                        onClick = {
                            val granted = ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (granted) { if (AudioRec.start(ctx)) recording = true } else micPerm.launch(android.Manifest.permission.RECORD_AUDIO)
                        },
                    ) { Icon(Icons.Filled.Mic, "Gravar áudio") }
                    else FilledIconButton(
                        enabled = (text.isNotBlank() || attached != null) && writer != null,
                        onClick = { Store.sendChatMessage(id, writer, text.trim(), attached); text = ""; attached = null },
                    ) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar") }
                }
            }
        }
    }

    menuFor?.let { m ->
        val mine = m.authorId != null && m.authorId == actor
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text("Mensagem") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("👍", "❤️", "😂", "😮", "😢", "🙏").forEach { e ->
                            Text(
                                e, style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                    .background(if (actor != null && m.reactions[actor] == e) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable(enabled = actor != null) { Store.react(m.id, actor!!, e); menuFor = null }
                                    .padding(6.dp),
                            )
                        }
                    }
                    TextButton(onClick = { Store.pinMessage(m.id, !m.pinned); menuFor = null }) {
                        Icon(Icons.Filled.PushPin, null); Spacer(Modifier.width(6.dp)); Text(if (m.pinned) "Desafixar" else "Fixar na conversa")
                    }
                    if (mine && m.canEdit()) TextButton(onClick = { editing = m; menuFor = null }) {
                        Icon(Icons.Filled.Edit, null); Spacer(Modifier.width(6.dp)); Text("Editar (primeiro minuto)")
                    }
                    Text("Mensagens não podem ser apagadas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { menuFor = null }) { Text("Fechar") } },
        )
    }
    if (showPins) AlertDialog(
        onDismissRequest = { showPins = false },
        title = { Text("Fixadas") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pins.forEach { m ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable {
                                showPins = false
                                scope.launch { list.animateScrollToItem(msgs.indexOf(m).coerceAtLeast(0)) }
                            }.padding(10.dp),
                    ) {
                        Text(d.subject(m.authorId)?.name ?: "Anônimo", style = MaterialTheme.typography.labelLarge)
                        Text(m.text.ifBlank { "📷 Foto" }, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(fmtDateTime(m.time), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showPins = false }) { Text("Fechar") } },
    )
    viewImage?.let { img ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { viewImage = null }) {
            Box(Modifier.fillMaxWidth().clickable { viewImage = null }) {
                StoredImage(img, Modifier.fillMaxWidth(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            }
        }
    }
    editing?.let { m ->
        var t by remember(m.id) { mutableStateOf(m.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Editar mensagem") },
            text = {
                Column {
                    Text("Só dá para editar no primeiro minuto. Mensagens não podem ser apagadas.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(t, { t = it }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = t.isNotBlank(), onClick = {
                    if (!Store.editChatMessage(m.id, m.authorId, t.trim()))
                        Toast.makeText(ctx, "O tempo para editar acabou.", Toast.LENGTH_SHORT).show()
                    editing = null
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancelar") } },
        )
    }
    if (pickWriter) {
        val dd = d.copy(members = d.members.filter { it.id in c.memberIds }, customFronts = emptyList())
        SubjectPickerDialog(dd, "Quem está escrevendo?", includeCustom = false, onDismiss = { pickWriter = false }) { s ->
            if (s != null) writer = s.id
            pickWriter = false
        }
    }
    if (editGroup) {
        var name by remember { mutableStateOf(c.name) }
        var ids by remember { mutableStateOf(c.memberIds.toSet()) }
        AlertDialog(
            onDismissRequest = { editGroup = false },
            title = { Text("Editar grupo") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    d.members.filter { !it.archived }.sortedBy { it.name.lowercase() }.forEach { mm ->
                        val fixed = mm.id == me
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(mm.id in ids, { ids = if (it) ids + mm.id else ids - mm.id }, enabled = !fixed)
                            Text(mm.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && ids.size >= 2, onClick = {
                    Store.upsertChat(c.copy(name = name.trim(), memberIds = ids.toList())); editGroup = false
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { editGroup = false }) { Text("Cancelar") } },
        )
    }
}
