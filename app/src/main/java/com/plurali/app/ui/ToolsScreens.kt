package com.plurali.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.plurali.app.data.*
import com.plurali.app.sys.Effects
import com.plurali.app.sys.Notifier

private val dayLetters = listOf(1 to "S", 2 to "T", 3 to "Q", 4 to "Q", 5 to "S", 6 to "S", 7 to "D")
private fun daysLabel(days: List<Int>) = when {
    days.size == 7 -> "Todos os dias"
    days.sorted() == listOf(1, 2, 3, 4, 5) -> "Seg a sex"
    days.sorted() == listOf(6, 7) -> "Fins de semana"
    else -> days.sorted().joinToString(", ") { listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")[it - 1] }
}

/** Aviso para permitir notificações (Android 13+). */
@Composable
fun NotificationPermissionBanner() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(Notifier.canPost(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        Effects.onChange(ctx)
    }
    if (granted || Build.VERSION.SDK_INT < 33) return
    Row(
        Modifier.fillMaxWidth().padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.NotificationsOff, null)
        Spacer(Modifier.width(10.dp))
        Text("As notificações estão desligadas para o Plurali.", Modifier.weight(1f))
        TextButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Permitir") }
    }
}

// ===================== Lembretes e timers =====================

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(nav: NavHostController) {
    val d by Store.data.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editR by remember { mutableStateOf<Reminder?>(null) }
    var editT by remember { mutableStateOf<FrontTimer?>(null) }
    ScreenScaffold(
        "Lembretes e timers",
        fab = {
            FloatingActionButton(onClick = {
                if (tab == 0) editR = Reminder(newId(), "", hour = 9, minute = 0)
                else editT = FrontTimer(newId(), null, 120, "")
            }) { Icon(Icons.Filled.Add, "Novo") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            NotificationPermissionBanner()
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Horários (${d.reminders.size})") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Timers de front (${d.timers.size})") })
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (tab == 0) {
                    item {
                        Text(
                            "Lembretes de horário: remédios, comer, beber água, terapia… O celular avisa no horário escolhido.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp),
                        )
                    }
                    if (d.reminders.isEmpty()) item { EmptyHint("Nenhum lembrete. Toque em + para criar.") }
                    items(d.reminders.sortedBy { it.hour * 60 + it.minute }, key = { it.id }) { r ->
                        SpRow(if (r.enabled) 0xFFD6AC5E else 0xFF5B606B, onClick = { editR = r }) {
                            Text("%02d:%02d".format(r.hour, r.minute), style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.title, style = MaterialTheme.typography.titleMedium)
                                val who = r.memberIds.mapNotNull { id -> d.members.find { it.id == id }?.name }
                                Text(daysLabel(r.days) + if (who.isNotEmpty()) " · ${who.joinToString(", ")}" else "",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(r.enabled, { Store.upsertReminder(r.copy(enabled = it)) })
                        }
                    }
                } else {
                    item {
                        Text(
                            "Timers de front avisam quando alguém entra no front ou já está lá há um tempo (ex.: “Bruno está no front há 3h: hora de comer”).",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp),
                        )
                    }
                    if (d.timers.isEmpty()) item { EmptyHint("Nenhum timer. Toque em + para criar.") }
                    items(d.timers, key = { it.id }) { t ->
                        val s = d.subject(t.subjectId)
                        SpRow(s?.color ?: 0xFFD6AC5E, onClick = { editT = t }) {
                            Icon(Icons.Filled.Timer, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    (s?.name ?: "Qualquer pessoa") + if (t.afterMin == 0) " entra no front" else " no front há ${fmtDuration(t.afterMin * 60_000L)}",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                if (t.message.isNotBlank()) Text(t.message, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Switch(t.enabled, { Store.upsertTimer(t.copy(enabled = it)) })
                        }
                    }
                }
            }
        }
    }

    editR?.let { r0 ->
        val isNew = d.reminders.none { it.id == r0.id }
        var r by remember(r0.id) { mutableStateOf(r0) }
        val tp = rememberTimePickerState(r0.hour, r0.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { editR = null },
            title = { Text(if (isNew) "Novo lembrete" else "Lembrete") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(r.title, { r = r.copy(title = it) }, label = { Text("O quê? (ex.: Remédio da manhã)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(r.text, { r = r.copy(text = it) }, label = { Text("Detalhes (opcional)") }, modifier = Modifier.fillMaxWidth())
                    TimeInput(tp)
                    Text("Dias", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        dayLetters.forEach { (n, l) ->
                            val on = n in r.days
                            Box(
                                Modifier.size(34.dp).clip(CircleShape)
                                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { r = r.copy(days = if (on) r.days - n else (r.days + n).sorted()) },
                                contentAlignment = Alignment.Center,
                            ) { Text(l, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
                        }
                    }
                    Text("Para quem (opcional)", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        d.members.filter { !it.archived }.sortedBy { it.name.lowercase() }.forEach { m ->
                            val on = m.id in r.memberIds
                            FilterChip(on, { r = r.copy(memberIds = if (on) r.memberIds - m.id else r.memberIds + m.id) }, label = { Text(m.name) })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = r.title.isNotBlank() && r.days.isNotEmpty(), onClick = {
                    Store.upsertReminder(r.copy(title = r.title.trim(), hour = tp.hour, minute = tp.minute)); editR = null
                }) { Text("Salvar") }
            },
            dismissButton = {
                Row {
                    if (!isNew) TextButton(onClick = { Store.deleteReminder(r.id); editR = null }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { editR = null }) { Text("Cancelar") }
                }
            },
        )
    }

    editT?.let { t0 ->
        val isNew = d.timers.none { it.id == t0.id }
        var t by remember(t0.id) { mutableStateOf(t0) }
        var customMin by remember(t0.id) { mutableStateOf(t0.afterMin.toString()) }
        val presets = listOf(0 to "Ao entrar", 30 to "30 min", 60 to "1 h", 120 to "2 h", 180 to "3 h", 240 to "4 h", 360 to "6 h")
        AlertDialog(
            onDismissRequest = { editT = null },
            title = { Text(if (isNew) "Novo timer de front" else "Timer de front") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Quem", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(t.subjectId == null, { t = t.copy(subjectId = null) }, label = { Text("Qualquer pessoa") })
                        (d.members.filter { !it.archived }.map { it.toSubject() } + d.customFronts.map { it.toSubject() }).forEach { s ->
                            FilterChip(t.subjectId == s.id, { t = t.copy(subjectId = s.id) }, label = { Text(s.name) })
                        }
                    }
                    Text("Quando", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        presets.forEach { (v, l) -> FilterChip(t.afterMin == v, { t = t.copy(afterMin = v); customMin = v.toString() }, label = { Text(l) }) }
                    }
                    OutlinedTextField(
                        customMin, { v -> customMin = v.filter { it.isDigit() }.take(5); customMin.toIntOrNull()?.let { t = t.copy(afterMin = it) } },
                        label = { Text("ou minutos no front") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(t.message, { t = t.copy(message = it) }, label = { Text("Mensagem (ex.: hora de comer, tomar água)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { Store.upsertTimer(t.copy(message = t.message.trim())); editT = null }) { Text("Salvar") } },
            dismissButton = {
                Row {
                    if (!isNew) TextButton(onClick = { Store.deleteTimer(t.id); editT = null }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { editT = null }) { Text("Cancelar") }
                }
            },
        )
    }
}

// ===================== Busca =====================

private data class Hit(val section: String, val title: String, val sub: String, val color: Long, val route: String?)

@Composable
fun SearchScreen(nav: NavHostController) {
    val d by Store.data.collectAsState()
    val who = LocalWho.current
    var q by rememberSaveable { mutableStateOf("") }
    val hits = remember(d, q, who) {
        val s = q.trim()
        if (s.length < 2) return@remember emptyList()
        fun has(vararg t: String?) = t.any { it != null && it.contains(s, ignoreCase = true) }
        fun snip(t: String): String {
            val i = t.indexOf(s, ignoreCase = true)
            if (i < 0) return t.take(80)
            val a = (i - 30).coerceAtLeast(0)
            return (if (a > 0) "…" else "") + t.substring(a, (i + s.length + 50).coerceAtMost(t.length))
        }
        val out = mutableListOf<Hit>()
        d.members.filter { !it.archived }.forEach { m ->
            val fieldVals = d.fields.filter { who.canSeeField(it, m.id) }.mapNotNull { m.fields[it.id] }
            val texts = listOf(m.name, m.pronouns, m.role, m.description, m.likes, m.dislikes, m.comfort) + fieldVals
            texts.firstOrNull { has(it) }?.let { out += Hit("Membros", m.name, if (it == m.name) m.pronouns else snip(it), m.color, "member/${m.id}") }
        }
        d.notes.filter { who.canSeePrivateOf(it.memberId) && has(it.title, it.body) }.forEach { n ->
            out += Hit("Notas", n.title.ifBlank { "(sem título)" }, (d.members.find { it.id == n.memberId }?.name ?: "") + " · " + snip(n.body), d.members.find { it.id == n.memberId }?.color ?: 0xFF9E9E9E, "noteEdit/${n.memberId}/${n.id}")
        }
        val chats = d.chats.filter { who.canSeeChat(it) }.associateBy { it.id }
        d.messages.filter { it.chatId in chats && has(it.text) }.sortedByDescending { it.time }.take(50).forEach { m ->
            val c = chats[m.chatId]!!
            out += Hit("Conversas", d.chatTitle(c, who.meId), "${d.subject(m.authorId)?.name ?: "Anônimo"}: ${snip(m.text)}", d.subject(m.authorId)?.color ?: 0xFF9E9E9E, "chat/${c.id}")
        }
        d.events.filter { has(it.title, it.description) }.forEach { e ->
            out += Hit("Calendário", e.title, fmtDate(e.start.toDate()), e.color, "calendar")
        }
        d.notices.filter { has(it.title, it.text) }.forEach { n -> out += Hit("Mural", n.title.ifBlank { "Aviso" }, snip(n.text), n.color, "home") }
        d.polls.filter { has(it.title, it.description) }.forEach { p -> out += Hit("Enquetes", p.title, if (p.isOpen()) "Aberta" else "Encerrada", 0xFFD6AC5E, "poll/${p.id}") }
        d.body.filter { has(it.label, it.value) }.forEach { b -> out += Hit("Corpo", b.label, b.value, 0xFF90A4AE, "body") }
        d.reminders.filter { has(it.title, it.text) }.forEach { r -> out += Hit("Lembretes", r.title, "%02d:%02d".format(r.hour, r.minute), 0xFFD6AC5E, "reminders") }
        out
    }
    ScreenScaffold("Buscar") { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            SearchField(q) { q = it }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (q.trim().length < 2) item { EmptyHint("Busque em membros, notas, conversas, calendário, mural, enquetes, corpo e lembretes.") }
                else if (hits.isEmpty()) item { EmptyHint("Nada encontrado para “${q.trim()}”.") }
                hits.groupBy { it.section }.forEach { (section, list) ->
                    item(key = "s-$section") { SectionHeader("$section (${list.size})", Modifier.padding(horizontal = 8.dp)) }
                    items(list.size, key = { "$section-$it" }) { i ->
                        val h = list[i]
                        SpRow(h.color, onClick = { h.route?.let { r -> nav.go(r) } }) {
                            Column(Modifier.weight(1f)) {
                                Text(h.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (h.sub.isNotBlank()) Text(h.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===================== Notificações =====================

@Composable
fun NotificationsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    ScreenScaffold("Notificações e atalhos", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NotificationPermissionBanner()
            ToggleRow("Notificação fixa de front", "Mostra quem está no front e botões para trocar sem abrir o app.", Prefs.frontNotif) {
                Prefs.putFrontNotif(it); Effects.onChange(ctx)
            }
            ToggleRow("Mensagens ao entrar no front", "Avisa “você tem mensagens não lidas” quando alguém entra no front.", Prefs.msgNotif) { Prefs.putMsgNotif(it) }
            ToggleRow("Enquetes encerradas", "Avisa o resultado quando o prazo de uma enquete acaba.", Prefs.pollNotif) { Prefs.putPollNotif(it); Effects.onChange(ctx) }
            ToggleRow("Aniversários", "Avisa às 9h no dia do aniversário de cada membro.", Prefs.birthdayNotif) { Prefs.putBirthdayNotif(it); Effects.onChange(ctx) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { nav.go("reminders") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Alarm, null); Spacer(Modifier.width(8.dp)); Text("Lembretes de horário e timers de front")
            }
            SectionHeader("Widget e atalho rápido")
            Text("• Widget: segure o dedo num espaço vazio da tela inicial → Widgets → Plurali → arraste “Plurali · Front”.")
            Text("• Atalho rápido: puxe a barra de notificações até o fim → lápis (editar) → arraste o bloco “Front” para cima.")
            Text(
                "Os dois mostram quem está no front e abrem o app ao tocar.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(value, onChange)
    }
}
