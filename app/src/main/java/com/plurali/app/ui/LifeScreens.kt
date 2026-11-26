package com.plurali.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import com.plurali.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// ===================== ajudantes =====================

/** Quem está fazendo a ação: a pessoa logada; sem login, o primeiro membro no front. */
fun actorId(d: AppData, who: Who): String? =
    who.meId ?: d.currentFronts().map { it.subjectId }.firstOrNull { id -> d.members.any { it.id == id } }

fun memberName(d: AppData, id: String?): String = d.members.find { it.id == id }?.name ?: "Sistema"

private val zone: ZoneId get() = ZoneId.systemDefault()
private fun LocalDate.at(min: Int) = atTime(min / 60, min % 60).atZone(zone).toInstant().toEpochMilli()
private fun hhmm(min: Int) = "%02d:%02d".format(min / 60, min % 60)

/** Escolher uma pessoa (quem está no front aparece primeiro). */
@Composable
fun PersonPickDialog(d: AppData, title: String, initial: String?, allowNone: String? = null, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    val fronting = d.currentFronts().map { it.subjectId }.toSet()
    val list = d.members.filter { !it.archived }.sortedWith(compareBy({ it.id !in fronting }, { it.name.lowercase() }))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                if (allowNone != null) item {
                    ListItem(headlineContent = { Text(allowNone) }, leadingContent = { Avatar("?", 0xFF9E9E9E, null, 36.dp) },
                        modifier = Modifier.clickable { onPick(null) })
                }
                items(list, key = { it.id }) { m ->
                    ListItem(
                        headlineContent = { Text(m.name, fontWeight = if (m.id == initial) FontWeight.Bold else null) },
                        supportingContent = { if (m.id in fronting) Text("No front agora", color = MaterialTheme.colorScheme.primary) },
                        leadingContent = { Avatar(m.name, m.color, m.avatar, 36.dp) },
                        trailingContent = { if (m.id == initial) Icon(Icons.Filled.Check, null) },
                        modifier = Modifier.clickable { onPick(m.id) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Fotos em tela cheia, com zoom e “anterior/próxima”. */
@Composable
fun ImageViewer(images: List<Pair<String, String>>, start: Int, onClose: () -> Unit) {
    if (images.isEmpty()) { onClose(); return }
    var i by remember { mutableIntStateOf(start.coerceIn(0, images.lastIndex)) }
    val (path, caption) = images[i.coerceIn(0, images.lastIndex)]
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offset by remember(path) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val white = Color.White
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            StoredImage(
                path,
                Modifier.fillMaxSize()
                    .pointerInput(path) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                contentScale = ContentScale.Fit,
            )
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Fechar", tint = white) }
                if (images.size > 1) Text("${i + 1} de ${images.size}", color = white)
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.6f)).navigationBarsPadding().padding(12.dp)) {
                if (caption.isNotBlank()) Text(caption, color = white)
                if (images.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = i > 0, onClick = { i-- }) { Text("‹ Anterior", color = white) }
                    TextButton(enabled = i < images.lastIndex, onClick = { i++ }) { Text("Próxima ›", color = white) }
                }
            }
        }
    }
}

// ===================== Recados para quem entrar no front =====================

/** Recados ainda não lidos que valem para este membro. */
fun AppData.pendingNotesFor(memberId: String): List<FrontNote> =
    frontNotes.filter { it.doneAt == null && (it.targetId == memberId || (it.targetId == null && it.authorId != memberId)) }

/** Bloco do início: recados para quem está no front agora + botão para deixar um recado. */
@Composable
fun FrontNotesBlock(d: AppData, who: Who) {
    if (!who.canWrite) return
    var writing by remember { mutableStateOf(false) }
    val fronters = d.currentFronts().map { it.subjectId }.distinct().filter { id -> d.members.any { it.id == id } }
    val shown = fronters.flatMap { id -> d.pendingNotesFor(id).map { id to it } }.distinctBy { it.second.id }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        shown.forEach { (forId, n) ->
            ThemedBox(color = 0xFFFFB74D) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.EditNote, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Recado de ${memberName(d, n.authorId)} " + (if (n.targetId != null) "para ${memberName(d, n.targetId)}" else "para quem entrasse no front"),
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                    )
                }
                Text(n.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtDateTime(n.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = { Store.doneFrontNote(n.id, forId) }) { Icon(Icons.Filled.Check, null); Spacer(Modifier.width(4.dp)); Text("Entendi (${memberName(d, forId)})") }
                }
            }
        }
        OutlinedButton(onClick = { writing = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.EditNote, null); Spacer(Modifier.width(8.dp)); Text("Deixar recado para quem entrar no front")
        }
    }
    if (writing) FrontNoteDialog(d, who) { writing = false }
}

@Composable
fun FrontNoteDialog(d: AppData, who: Who, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var target by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recado para quem entrar no front") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text("Recado") }, placeholder = { Text("Ex.: a louça já está lavada; a consulta mudou para quinta") },
                    minLines = 3, modifier = Modifier.fillMaxWidth())
                Text("Para quem", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Person, null); Spacer(Modifier.width(8.dp))
                    Text(if (target == null) "Quem entrar primeiro (qualquer pessoa)" else memberName(d, target))
                }
                Text("O recado aparece no início e numa notificação quando a pessoa entrar no front.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = {
                Store.upsertFrontNote(FrontNote(newId(), actorId(d, who), text.trim(), System.currentTimeMillis(), target)); onDismiss()
            }) { Text("Deixar recado") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
    if (picking) PersonPickDialog(d, "Para quem é o recado?", target, allowNone = "Quem entrar primeiro", onDismiss = { picking = false }) { target = it; picking = false }
}

// ===================== O que eu perdi? =====================

/** Tem novidade desde [since] para este membro? (decide se abre a tela sozinha ao entrar) */
fun AppData.hasNewsFor(me: String, since: Long): Boolean =
    unreadFor(me) > 0 || pendingNotesFor(me).isNotEmpty() ||
        fronts.any { it.start > since && it.subjectId != me } || notices.any { it.time > since } ||
        diary.any { it.time > since && it.authorId != me } || tasks.any { (it.doneAt ?: 0) > since && it.doneById != me } ||
        medTakes.any { it.takenAt > since && it.byId != me } || companionMessages.any { it.time > since }

@Composable
fun MissedScreen(nav: NavHostController) {
    val d by Store.data.collectAsState()
    val who = LocalWho.current
    val me = who.meId
    val now = rememberNow()
    val since = Session.missedSince ?: (now - 86_400_000L)
    ScreenScaffold("O que eu perdi?", onBack = { nav.popBackStack() }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Desde ${fmtDateTime(since)}" + (me?.let { " (a última vez que ${memberName(d, it)} usou o app)" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            var any = false
            // recados
            val notes = me?.let { d.pendingNotesFor(it) }.orEmpty()
            if (notes.isNotEmpty()) {
                any = true
                item { SectionHeader("Recados para você") }
                items(notes, key = { "n" + it.id }) { n ->
                    ThemedBox(color = 0xFFFFB74D) {
                        Text("De ${memberName(d, n.authorId)} · ${fmtWhen(n.createdAt, now)}", style = MaterialTheme.typography.labelMedium)
                        Text(n.text, style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = { Store.doneFrontNote(n.id, me) }) { Text("Entendi") }
                    }
                }
            }
            // mensagens
            val chats = d.chats.filter { who.canSeeChat(it) }.map { it to d.unreadIn(it, me) }.filter { it.second > 0 }
            if (chats.isNotEmpty()) {
                any = true
                item { SectionHeader("Mensagens novas") }
                items(chats, key = { "c" + it.first.id }) { (c, n) ->
                    MissedRow(Icons.Filled.Forum, c.name.ifBlank { c.memberIds.filter { it != me }.joinToString { memberName(d, it) } }, "$n ${if (n == 1) "mensagem nova" else "mensagens novas"}") { nav.go("chat/${c.id}") }
                }
            }
            // companions
            val cmsgs = d.companionMessages.filter { it.time > since }
            if (cmsgs.isNotEmpty()) {
                any = true
                item { SectionHeader("Recados dos companions") }
                items(cmsgs, key = { "cm" + it.id }) { m ->
                    MissedRow(Icons.Filled.Share, d.companions.find { it.id == m.companionId }?.name ?: "Companion", m.text) { nav.go("companions") }
                }
            }
            // front
            val fr = d.fronts.filter { it.start > since }.sortedBy { it.start }
            if (fr.isNotEmpty()) {
                any = true
                item { SectionHeader("Quem esteve no front") }
                items(fr, key = { "f" + it.id }) { e ->
                    val s = d.subject(e.subjectId)
                    MissedRow(Icons.Filled.People, s?.name ?: "(excluído)", "${fmtDateTime(e.start)} · ${fmtDuration((e.end ?: now) - e.start)}" + (if (e.end == null) " · agora" else "")) { nav.go("history") }
                }
            }
            val nts = d.notices.filter { it.time > since }
            if (nts.isNotEmpty()) {
                any = true
                item { SectionHeader("Avisos novos") }
                items(nts, key = { "a" + it.id }) { n -> MissedRow(Icons.Filled.Campaign, n.title.ifBlank { "Aviso" }, Rich.strip(n.text)) { nav.go("home") } }
            }
            val di = d.diary.filter { it.time > since && it.authorId != me }
            if (di.isNotEmpty()) {
                any = true
                item { SectionHeader("No diário") }
                items(di, key = { "d" + it.id }) { e -> MissedRow(Icons.Filled.MenuBook, memberName(d, e.authorId), Rich.strip(e.text).ifBlank { "(fotos)" }) { nav.go("diary") } }
            }
            val td = d.tasks.filter { (it.doneAt ?: 0) > since && it.doneById != me }
            val mine = d.tasks.filter { it.doneAt == null && me != null && it.assigneeId == me }
            if (td.isNotEmpty() || mine.isNotEmpty()) {
                any = true
                item { SectionHeader("Tarefas") }
                items(td, key = { "t" + it.id }) { t -> MissedRow(Icons.Filled.TaskAlt, t.title, "Feita por ${memberName(d, t.doneById)} · ${fmtWhen(t.doneAt ?: 0, now)}") { nav.go("tasks") } }
                items(mine, key = { "tm" + it.id }) { t -> MissedRow(Icons.Filled.RadioButtonUnchecked, t.title, "Para você" + (t.due?.let { " · até ${fmtDate(dayOf(it))}" } ?: "")) { nav.go("tasks") } }
            }
            val mt = d.medTakes.filter { it.takenAt > since && (me == null || it.byId != me) }
            if (mt.isNotEmpty()) {
                any = true
                item { SectionHeader("Remédios") }
                items(mt, key = { "m" + it.id }) { t ->
                    val m = d.meds.find { it.id == t.medId }
                    MissedRow(Icons.Filled.Medication, m?.name ?: "Remédio", if (t.skipped) "Pulado · ${fmtDateTime(t.takenAt)}" else "Tomado às ${fmtTime(t.takenAt)} por ${t.byName} (${fmtDate(dayOf(t.takenAt))})") { nav.go("meds") }
                }
            }
            val today = LocalDate.now()
            val upcoming = (0..2L).flatMap { i -> val day = today.plusDays(i); d.events.filter { it.occursOn(day) }.map { day to it } }
            if (upcoming.isNotEmpty()) {
                item { SectionHeader("Próximos 3 dias") }
                items(upcoming.size) { k ->
                    val (day, e) = upcoming[k]
                    MissedRow(Icons.Filled.Event, e.title, (if (day == today) "Hoje" else fmtDate(day)) + if (e.allDay) "" else " · ${fmtTime(e.start)}") { nav.go("calendar") }
                }
            }
            if (!any) item { EmptyHint("Nada de novo desde a última vez. Tudo tranquilo.") }
        }
    }
}

@Composable
private fun MissedRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().themed().clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ===================== Remédios =====================

private data class Slot(val med: Medication, val time: Long, val min: Int, val take: MedTake?)

private fun todaySlots(d: AppData, day: LocalDate = LocalDate.now()): List<Slot> =
    d.meds.filter { it.active && day.dayOfWeek.value in it.days }.flatMap { m ->
        m.times.sorted().map { t -> val at = day.at(t); Slot(m, at, t, d.medTakes.lastOrNull { it.medId == m.id && it.slot == at }) }
    }.sortedBy { it.time }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedsScreen(nav: NavHostController) {
    val d by Store.data.collectAsState()
    val who = LocalWho.current
    val now = rememberNow()
    var edit by remember { mutableStateOf<Medication?>(null) }
    var taking by remember { mutableStateOf<Slot?>(null) }
    var confirmAgain by remember { mutableStateOf<Slot?>(null) }
    fun record(s: Slot, byId: String?, skipped: Boolean = false) {
        Store.addMedTake(MedTake(newId(), s.med.id, s.time, System.currentTimeMillis(), byId, if (byId == null) "Sistema" else memberName(d, byId), skipped))
    }
    ScreenScaffold(
        "Remédios", onBack = { nav.popBackStack() },
        fab = { if (who.canWrite) ExtendedFloatingActionButton(onClick = { edit = Medication(newId(), "") }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Remédio") }) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Cada dose fica registrada com quem tomou e a hora — assim ninguém toma duas vezes nem esquece. O lembrete chega na hora certa, com o botão “Tomei”.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { SectionHeader("Hoje") }
            val slots = todaySlots(d)
            if (slots.isEmpty()) item { EmptyHint(if (d.meds.isEmpty()) "Nenhum remédio cadastrado." else "Nenhuma dose hoje.") }
            items(slots, key = { it.med.id + it.time }) { s ->
                val t = s.take
                val late = t == null && now > s.time + 30 * 60_000L
                SpRow(s.med.color, Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("${hhmm(s.min)} · ${s.med.name}", style = MaterialTheme.typography.titleMedium)
                        if (s.med.dose.isNotBlank()) Text(s.med.dose, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        Text(
                            when {
                                t?.skipped == true -> "Pulado (${t.byName})"
                                t != null -> "✓ Tomado às ${fmtTime(t.takenAt)} por ${t.byName}"
                                late -> "Atrasado — ainda não registrado"
                                now < s.time -> "Mais tarde"
                                else -> "Na hora de tomar"
                            },
                            color = when { t != null && !t.skipped -> Color(0xFF66BB6A); late -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    if (who.canWrite) {
                        if (t == null) {
                            TextButton(onClick = { record(s, actorId(d, who), skipped = true) }) { Text("Pular") }
                            Button(onClick = { taking = s }) { Text("Tomei") }
                        } else IconButton(onClick = { Store.deleteMedTake(t.id) }) { Icon(Icons.Filled.Undo, "Desfazer") }
                    }
                }
            }
            item { SectionHeader("Remédios") }
            if (d.meds.isEmpty()) item { EmptyHint("Toque em “Remédio” para cadastrar.") }
            items(d.meds, key = { "med" + it.id }) { m ->
                SpRow(m.color, Modifier.fillMaxWidth(), onClick = if (who.canWrite) ({ edit = m }) else null) {
                    Icon(Icons.Filled.Medication, null, tint = Color(m.color))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name + if (!m.active) " (pausado)" else "", style = MaterialTheme.typography.titleSmall)
                        Text(listOf(m.dose, m.times.sorted().joinToString(" · ") { hhmm(it) }).filter { it.isNotBlank() }.joinToString(" — "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val recent = d.medTakes.sortedByDescending { it.takenAt }.take(15)
            if (recent.isNotEmpty()) {
                item { SectionHeader("Últimos registros") }
                items(recent, key = { "tk" + it.id }) { t ->
                    val m = d.meds.find { it.id == t.medId }
                    Text(
                        "${fmtDate(dayOf(t.takenAt))} ${fmtTime(t.takenAt)} · ${m?.name ?: "?"} · " + (if (t.skipped) "pulado" else "tomado por ${t.byName}"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
    taking?.let { s ->
        PersonPickDialog(d, "Quem tomou ${s.med.name}?", actorId(d, who), allowNone = "Não sei / Sistema", onDismiss = { taking = null }) { id ->
            taking = null
            val already = d.medTakes.lastOrNull { it.medId == s.med.id && it.slot == s.time && !it.skipped }
            if (already != null) confirmAgain = s.copy(take = already) else record(s, id)
        }
    }
    confirmAgain?.let { s ->
        ConfirmDialog(
            "Já foi tomado", "Esta dose já foi registrada às ${fmtTime(s.take!!.takenAt)} por ${s.take.byName}. Registrar outra dose mesmo assim?",
            confirm = "Registrar", onDismiss = { confirmAgain = null },
        ) { record(s, actorId(d, who)); confirmAgain = null }
    }
    edit?.let { m0 -> MedEditDialog(m0, isNew = d.meds.none { it.id == m0.id }) { edit = null } }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MedEditDialog(m0: Medication, isNew: Boolean, onClose: () -> Unit) {
    var m by remember { mutableStateOf(m0) }
    var addTime by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    val dayNames = listOf("S", "T", "Q", "Q", "S", "S", "D")
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (isNew) "Novo remédio" else "Editar remédio") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(m.name, { m = m.copy(name = it) }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(m.dose, { m = m.copy(dose = it) }, label = { Text("Dose (ex.: 1 comprimido, 50 mg)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Horários", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    m.times.sorted().forEach { t ->
                        InputChip(true, { m = m.copy(times = m.times - t) }, label = { Text(hhmm(t)) }, trailingIcon = { Icon(Icons.Filled.Close, "Tirar", Modifier.size(16.dp)) })
                    }
                    AssistChip({ addTime = true }, label = { Text("Horário") }, leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)) })
                }
                Text("Dias", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..7).forEach { dd ->
                        val sel = dd in m.days
                        Box(
                            Modifier.size(34.dp).clip(CircleShape)
                                .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { m = m.copy(days = if (sel) m.days - dd else m.days + dd) },
                            contentAlignment = Alignment.Center,
                        ) { Text(dayNames[dd - 1], color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
                    }
                }
                OutlinedTextField(m.notes, { m = m.copy(notes = it) }, label = { Text("Observações") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().clickable { m = m.copy(active = !m.active) }, verticalAlignment = Alignment.CenterVertically) {
                    Text("Lembretes ligados", Modifier.weight(1f)); Switch(m.active, { m = m.copy(active = it) })
                }
                ColorPicker(m.color) { m = m.copy(color = it) }
            }
        },
        confirmButton = { TextButton(enabled = m.name.isNotBlank() && m.times.isNotEmpty() && m.days.isNotEmpty(), onClick = { Store.upsertMed(m.copy(name = m.name.trim())); onClose() }) { Text("Salvar") } },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { confirmDel = true }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onClose) { Text("Cancelar") }
            }
        },
    )
    if (addTime) {
        val tp = rememberTimePickerState(8, 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { addTime = false },
            title = { Text("Horário") },
            text = { TimePicker(tp) },
            confirmButton = { TextButton(onClick = { m = m.copy(times = (m.times + (tp.hour * 60 + tp.minute)).distinct()); addTime = false }) { Text("Adicionar") } },
            dismissButton = { TextButton(onClick = { addTime = false }) { Text("Cancelar") } },
        )
    }
    if (confirmDel) ConfirmDialog("Excluir ${m.name}?", "Os registros antigos continuam no histórico.", onDismiss = { confirmDel = false }) {
        Store.deleteMed(m.id); confirmDel = false; onClose()
    }
}

// ===================== Tarefas =====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasksScreen(nav: NavHostController) {
    val d by Store.data.collectAsState()
    val who = LocalWho.current
    val now = rememberNow()
    var showDone by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<TaskItem?>(null) }
    var completing by remember { mutableStateOf<TaskItem?>(null) }
    ScreenScaffold(
        "Tarefas", onBack = { nav.popBackStack() },
        fab = { if (who.canWrite) ExtendedFloatingActionButton(onClick = { edit = TaskItem(newId(), "", createdById = actorId(d, who)) }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Tarefa") }) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!showDone, { showDone = false }, label = { Text("A fazer (${d.tasks.count { it.doneAt == null }})") })
                    FilterChip(showDone, { showDone = true }, label = { Text("Feitas") })
                }
            }
            val list = if (showDone) d.tasks.filter { it.doneAt != null }.sortedByDescending { it.doneAt }
            else d.tasks.filter { it.doneAt == null }.sortedWith(compareBy({ it.due ?: Long.MAX_VALUE }, { it.createdAt }))
            if (list.isEmpty()) item { EmptyHint(if (showDone) "Nada feito ainda." else "Nenhuma tarefa. Toque em “Tarefa” para criar (contas, limpeza, estudos…).") }
            items(list, key = { it.id }) { t ->
                val overdue = t.doneAt == null && t.due != null && dayOf(t.due) < LocalDate.now()
                SpRow(d.members.find { it.id == t.assigneeId }?.color ?: 0xFF90A4AE, Modifier.fillMaxWidth(), onClick = if (who.canWrite) ({ edit = t }) else null) {
                    Checkbox(t.doneAt != null, { checked ->
                        if (!who.canWrite) return@Checkbox
                        if (checked) completing = t else Store.reopenTask(t.id)
                    })
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.titleSmall)
                        val info = buildList {
                            t.assigneeId?.let { add("Para ${memberName(d, it)}") }
                            t.due?.let { add((if (overdue) "Atrasada · " else "Até ") + fmtDate(dayOf(it))) }
                            if (t.repeat != "none") add(when (t.repeat) { "daily" -> "todo dia"; "weekly" -> "toda semana"; else -> "todo mês" })
                        }.joinToString(" · ")
                        if (t.doneAt != null) Text("✓ Feita por ${memberName(d, t.doneById)} · ${fmtWhen(t.doneAt, now)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF66BB6A))
                        else if (info.isNotBlank()) Text(info, style = MaterialTheme.typography.bodySmall, color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (t.notes.isNotBlank()) Text(t.notes, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (t.repeat != "none") Icon(Icons.Filled.Repeat, "Repete", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
    completing?.let { t ->
        PersonPickDialog(d, "Quem fez “${t.title}”?", actorId(d, who), allowNone = "Sistema", onDismiss = { completing = null }) { id ->
            Store.completeTask(t.id, id); completing = null
        }
    }
    edit?.let { t0 -> TaskEditDialog(d, t0, isNew = d.tasks.none { it.id == t0.id }) { edit = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskEditDialog(d: AppData, t0: TaskItem, isNew: Boolean, onClose: () -> Unit) {
    var t by remember { mutableStateOf(t0) }
    var pickWho by remember { mutableStateOf(false) }
    var pickDue by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (isNew) "Nova tarefa" else "Editar tarefa") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(t.title, { t = t.copy(title = it) }, label = { Text("O que fazer") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(t.notes, { t = t.copy(notes = it) }, label = { Text("Detalhes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { pickWho = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Person, null); Spacer(Modifier.width(8.dp)); Text(t.assigneeId?.let { "Para ${memberName(d, it)}" } ?: "Para qualquer pessoa")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickDue = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Event, null); Spacer(Modifier.width(8.dp)); Text(t.due?.let { "Até ${fmtDate(dayOf(it))}" } ?: "Sem prazo")
                    }
                    if (t.due != null) IconButton(onClick = { t = t.copy(due = null) }) { Icon(Icons.Filled.Close, "Tirar prazo") }
                }
                Text("Repetir", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("none" to "Não", "daily" to "Todo dia", "weekly" to "Toda semana", "monthly" to "Todo mês").forEach { (k, l) ->
                        FilterChip(t.repeat == k, { t = t.copy(repeat = k) }, label = { Text(l) })
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = t.title.isNotBlank(), onClick = { Store.upsertTask(t.copy(title = t.title.trim())); onClose() }) { Text("Salvar") } },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { Store.deleteTask(t.id); onClose() }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onClose) { Text("Cancelar") }
            }
        },
    )
    if (pickWho) PersonPickDialog(d, "Para quem?", t.assigneeId, allowNone = "Qualquer pessoa", onDismiss = { pickWho = false }) { t = t.copy(assigneeId = it); pickWho = false }
    if (pickDue) DateTimePickerDialog(t.due ?: System.currentTimeMillis(), onDismiss = { pickDue = false }) { t = t.copy(due = it); pickDue = false }
}

// ===================== Diário =====================

@Composable
fun DiaryScreen(nav: NavHostController) {
    val d by Store.data.collectAsState()
    val who = LocalWho.current
    var edit by remember { mutableStateOf<DiaryEntry?>(null) }
    var viewer by remember { mutableStateOf<Pair<List<Pair<String, String>>, Int>?>(null) }
    ScreenScaffold(
        "Diário do sistema", onBack = { nav.popBackStack() },
        fab = { if (who.canWrite) ExtendedFloatingActionButton(onClick = { edit = DiaryEntry(newId(), actorId(d, who), System.currentTimeMillis(), "") }, icon = { Icon(Icons.Filled.Edit, null) }, text = { Text("Escrever") }) },
    ) { pad ->
        val byDay = d.diary.sortedByDescending { it.time }.groupBy { dayOf(it.time) }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("O que aconteceu, com fotos — a memória do sistema, para quem não estava no front.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (byDay.isEmpty()) item { EmptyHint("Nada escrito ainda.") }
            byDay.forEach { (day, entries) ->
                item(key = "day$day") { SectionHeader(if (day == LocalDate.now()) "Hoje" else fmtDay(day)) }
                items(entries, key = { it.id }) { e ->
                    val m = d.members.find { it.id == e.authorId }
                    Column(Modifier.fillMaxWidth().themed().clickable(enabled = who.isAdmin || who.meId == e.authorId || who is Who.Open) { edit = e }.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(m?.name ?: "S", m?.color ?: 0xFF90A4AE, m?.avatar, 32.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(m?.name ?: "Sistema", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(fmtTime(e.time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (e.text.isNotBlank()) RichText(e.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                        if (e.images.isNotEmpty()) LazyRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(e.images.size) { i ->
                                StoredImage(e.images[i], Modifier.size(110.dp).clip(RoundedCornerShape(10.dp)).clickable {
                                    viewer = e.images.map { it to Rich.strip(e.text).take(120) } to i
                                })
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
    viewer?.let { (imgs, i) -> ImageViewer(imgs, i) { viewer = null } }
    edit?.let { e0 -> DiaryEditDialog(d, e0, isNew = d.diary.none { it.id == e0.id }) { edit = null } }
}

@Composable
private fun DiaryEditDialog(d: AppData, e0: DiaryEntry, isNew: Boolean, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var e by remember { mutableStateOf(e0) }
    var busy by remember { mutableStateOf(false) }
    var pickWho by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(8)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            busy = true
            val paths = withContext(Dispatchers.IO) { uris.mapIndexedNotNull { i, u -> saveImage(ctx, u, "diary", e.id + "-" + System.currentTimeMillis() + "-" + i, aspect = null, maxSide = 1600) } }
            busy = false
            e = e.copy(images = e.images + paths)
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (isNew) "Escrever no diário" else "Editar") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickWho = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Person, null); Spacer(Modifier.width(8.dp)); Text("Quem escreve: ${memberName(d, e.authorId)}")
                }
                RichTextField(e.text, { e = e.copy(text = it) }, "O que aconteceu?", Modifier.fillMaxWidth(), minLines = 4)
                if (e.images.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    e.images.forEach { p ->
                        Box {
                            StoredImage(p, Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)))
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), CircleShape).clip(CircleShape)
                                    .clickable { e = e.copy(images = e.images - p) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Close, "Tirar", tint = Color.White, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
                OutlinedButton(enabled = !busy, onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "Carregando…" else "Adicionar fotos")
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy && (e.text.isNotBlank() || e.images.isNotEmpty()), onClick = { Store.upsertDiary(e); onClose() }) { Text("Salvar") } },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { Store.deleteDiary(e.id); onClose() }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onClose) { Text("Cancelar") }
            }
        },
    )
    if (pickWho) PersonPickDialog(d, "Quem escreve?", e.authorId, allowNone = "Sistema", onDismiss = { pickWho = false }) { e = e.copy(authorId = it); pickWho = false }
}
