package com.plurali.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.plurali.app.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val ptBR = Locale("pt", "BR")
private val zone: ZoneId get() = ZoneId.systemDefault()
private fun Long.date(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

private sealed interface CalFilter {
    data class Person(val id: String) : CalFilter
    data class Role(val name: String) : CalFilter
}

private fun CalEvent.matches(f: CalFilter?, d: AppData): Boolean = when (f) {
    null -> true
    is CalFilter.Person -> {
        val roles = d.members.find { it.id == f.id }?.roles().orEmpty().map { it.lowercase() }
        f.id in memberIds || this.roles.any { it.lowercase() in roles } || (memberIds.isEmpty() && this.roles.isEmpty())
    }
    is CalFilter.Role -> {
        val r = f.name.lowercase()
        this.roles.any { it.lowercase() == r } || memberIds.any { id -> d.members.find { it.id == id }?.roles().orEmpty().any { it.lowercase() == r } }
    }
}

@Composable
fun CalendarScreen(nav: NavHostController) {
    val d by Store.data.collectAsState()
    val who = LocalWho.current
    var monthStr by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selStr by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var filter by remember { mutableStateOf<CalFilter?>(null) }
    val month = YearMonth.parse(monthStr)
    val selected = LocalDate.parse(selStr)
    val today = LocalDate.now()
    val all = remember(d.events, d.members) { d.events + d.birthdayEvents() }
    val visible = remember(all, filter) { all.filter { it.matches(filter, d) } }
    val byDay = remember(visible, month) {
        (1..month.lengthOfMonth()).associate { day ->
            val date = month.atDay(day)
            date to visible.filter { it.occursOn(date) }
        }
    }
    val dayEvents = remember(visible, selected) {
        visible.filter { it.occursOn(selected) }.sortedWith(compareBy({ !it.allDay }, { Instant.ofEpochMilli(it.start).atZone(zone).toLocalTime() }))
    }
    val roles = remember(d.members, d.events) { d.allRoles() }

    ScreenScaffold(
        "Calendário",
        fab = { if (who.canWrite) FloatingActionButton(onClick = { nav.go("eventEdit/new/$selStr") }) { Icon(Icons.Filled.Add, "Novo evento") } },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            // filtros
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(filter == null, { filter = null }, label = { Text("Todos") }) }
                    items(roles, key = { "r-$it" }) { r ->
                        FilterChip(
                            filter == CalFilter.Role(r), { filter = if (filter == CalFilter.Role(r)) null else CalFilter.Role(r) },
                            label = { Text(r) }, leadingIcon = { Icon(Icons.Filled.Badge, null, Modifier.size(16.dp)) },
                        )
                    }
                    items(d.members.filter { !it.archived }.sortedBy { it.name.lowercase() }, key = { "p-" + it.id }) { m ->
                        FilterChip(
                            filter == CalFilter.Person(m.id), { filter = if (filter == CalFilter.Person(m.id)) null else CalFilter.Person(m.id) },
                            label = { Text(m.name) }, leadingIcon = { Avatar(m.name, m.color, m.avatar, 18.dp) },
                        )
                    }
                }
            }
            // mês
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { monthStr = month.minusMonths(1).toString() }) { Icon(Icons.Filled.ChevronLeft, "Mês anterior") }
                    Text(
                        month.month.getDisplayName(TextStyle.FULL, ptBR).replaceFirstChar { it.uppercase() } + " de ${month.year}",
                        style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                            .clickable { monthStr = YearMonth.now().toString(); selStr = today.toString() },
                    )
                    IconButton(onClick = { monthStr = month.plusMonths(1).toString() }) { Icon(Icons.Filled.ChevronRight, "Próximo mês") }
                }
            }
            item {
                val first = month.atDay(1).dayOfWeek.value % 7 // domingo = 0
                Column(Modifier.padding(horizontal = 8.dp)) {
                    Row {
                        listOf("D", "S", "T", "Q", "Q", "S", "S").forEach {
                            Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val cells = first + month.lengthOfMonth()
                    for (w in 0 until (cells + 6) / 7) {
                        Row {
                            for (c in 0 until 7) {
                                val dayNum = w * 7 + c - first + 1
                                Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                                    if (dayNum in 1..month.lengthOfMonth()) {
                                        val date = month.atDay(dayNum)
                                        val evs = byDay[date].orEmpty()
                                        val isSel = date == selected
                                        Column(
                                            Modifier.fillMaxSize().clip(CircleShape)
                                                .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                                .then(if (date == today && !isSel) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                                .clickable { selStr = date.toString() },
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            Text(
                                                "$dayNum",
                                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                evs.take(3).forEach { e -> Box(Modifier.size(5.dp).clip(CircleShape).background(Color(e.color))) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                SectionHeader(
                    selected.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", ptBR)).replaceFirstChar { it.uppercase() },
                    Modifier.padding(horizontal = 16.dp),
                )
            }
            if (dayEvents.isEmpty()) item { Box(Modifier.padding(horizontal = 16.dp)) { EmptyHint("Nada neste dia.") } }
            items(dayEvents, key = { it.id }) { e ->
                val bday = e.id.startsWith("bday-")
                SpRow(
                    e.color, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    onClick = { if (!bday && who.canWrite) nav.go("eventEdit/${e.id}/$selStr") },
                ) {
                    Icon(if (bday) Icons.Filled.Cake else Icons.Filled.Event, null, tint = Color(e.color))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = MaterialTheme.typography.titleMedium)
                        val t = Instant.ofEpochMilli(e.start).atZone(zone).toLocalTime()
                        val endT = e.end?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
                        Text(
                            (if (e.remindMin != null) "🔔 " else "") +
                            (if (e.allDay) "Dia todo" else "%02d:%02d".format(t.hour, t.minute) + (endT?.let { " – %02d:%02d".format(it.hour, it.minute) } ?: "")) +
                                when (e.repeat) { "weekly" -> " · toda semana"; "monthly" -> " · todo mês"; "yearly" -> " · todo ano"; else -> "" },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val who2 = e.memberIds.mapNotNull { id -> d.members.find { it.id == id }?.name } + e.roles
                        Text(
                            if (who2.isEmpty()) "Para todos" else who2.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                        )
                        if (e.description.isNotBlank()) Text(e.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventEditScreen(nav: NavHostController, id: String, day: String) {
    val d = remember { Store.data.value }
    val existing = d.events.find { it.id == id }
    val initDay = runCatching { LocalDate.parse(day) }.getOrElse { LocalDate.now() }
    var e by remember {
        mutableStateOf(
            existing ?: CalEvent(
                newId(), "", start = initDay.atTime(LocalTime.of(12, 0)).atZone(zone).toInstant().toEpochMilli(),
                allDay = true,
            )
        )
    }
    var pick by remember { mutableStateOf<String?>(null) }
    var newRole by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val roles = remember { d.allRoles() }
    val end = e.end
    val valid = e.title.isNotBlank() && (end == null || end >= e.start)

    ScreenScaffold(
        if (existing == null) "Novo evento" else "Editar evento",
        onBack = { nav.popBackStack() },
        actions = {
            if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Excluir") }
            TextButton(enabled = valid, onClick = {
                val ev = if (e.allDay) e.copy(start = e.start.date().atStartOfDay(zone).toInstant().toEpochMilli()) else e
                Store.upsertEvent(ev.copy(title = ev.title.trim())); nav.popBackStack()
            }) { Text("Salvar") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(e.title, { e = e.copy(title = it) }, label = { Text("Título *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(e.description, { e = e.copy(description = it) }, label = { Text("Descrição") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dia todo", Modifier.weight(1f)); Switch(e.allDay, { e = e.copy(allDay = it) })
            }
            OutlinedButton(onClick = { pick = "start" }, modifier = Modifier.fillMaxWidth()) {
                Text(if (e.allDay) "Data: ${fmtDate(e.start.date())}" else "Início: ${fmtDateTime(e.start)}")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { pick = "end" }, modifier = Modifier.weight(1f)) {
                    Text(if (end == null) "Fim: (sem fim definido)" else "Fim: ${if (e.allDay) fmtDate(end.date()) else fmtDateTime(end)}")
                }
                if (end != null) IconButton(onClick = { e = e.copy(end = null) }) { Icon(Icons.Filled.Close, "Tirar fim") }
            }
            if (!valid && e.title.isNotBlank()) Text("O fim está antes do início.", color = MaterialTheme.colorScheme.error)

            Text("Repetir", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("none" to "Não repete", "weekly" to "Toda semana", "monthly" to "Todo mês", "yearly" to "Todo ano").forEach { (k, t) ->
                    FilterChip(e.repeat == k, { e = e.copy(repeat = k) }, label = { Text(t) })
                }
            }

            Text("Lembrete (notificação)", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(null to "Nenhum", 0 to (if (e.allDay) "No dia, 9h" else "Na hora"), 15 to "15 min antes", 60 to "1 h antes", 1440 to "1 dia antes")
                    .forEach { (v, t) -> FilterChip(e.remindMin == v, { e = e.copy(remindMin = v) }, label = { Text(t) }) }
            }

            Text("Para quem (pessoas)", style = MaterialTheme.typography.titleSmall)
            Text("Sem ninguém e sem papéis = para todos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                d.members.filter { !it.archived }.sortedBy { it.name.lowercase() }.forEach { m ->
                    val on = m.id in e.memberIds
                    FilterChip(on, { e = e.copy(memberIds = if (on) e.memberIds - m.id else e.memberIds + m.id) },
                        label = { Text(m.name) }, leadingIcon = { Avatar(m.name, m.color, m.avatar, 18.dp) })
                }
            }
            Text("Para quem (papéis)", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (roles + e.roles).distinctBy { it.lowercase() }.forEach { r ->
                    val on = e.roles.any { it.equals(r, true) }
                    FilterChip(on, { e = e.copy(roles = if (on) e.roles.filterNot { it.equals(r, true) } else e.roles + r) }, label = { Text(r) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newRole, { newRole = it }, label = { Text("Outro papel") }, singleLine = true, modifier = Modifier.weight(1f))
                TextButton(enabled = newRole.isNotBlank(), onClick = { e = e.copy(roles = e.roles + newRole.trim()); newRole = "" }) { Text("Adicionar") }
            }
            Text("Cor", style = MaterialTheme.typography.titleSmall)
            ColorPicker(e.color) { e = e.copy(color = it) }
            Spacer(Modifier.height(24.dp))
        }
    }
    pick?.let { which ->
        DateTimePickerDialog(if (which == "start") e.start else (e.end ?: e.start), onDismiss = { pick = null }) { t ->
            e = if (which == "start") e.copy(start = t) else e.copy(end = t)
            pick = null
        }
    }
    if (confirmDelete && existing != null) ConfirmDialog("Excluir evento?", "Não dá para desfazer.", onDismiss = { confirmDelete = false }) {
        Store.deleteEvent(existing.id); nav.popBackStack()
    }
}
