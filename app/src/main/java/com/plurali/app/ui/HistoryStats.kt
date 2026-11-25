package com.plurali.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.plurali.app.data.*
import java.time.LocalDate
import java.time.ZoneId

private fun LocalDate.startMs() = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
private fun LocalDate.endMs() = plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

// ===================== Histórico de front =====================

@Composable
fun HistoryScreen(nav: NavHostController) {
    val d by Store.data.collectAsState()
    val now = rememberNow()
    var from by rememberSaveable { mutableStateOf(LocalDate.now().minusDays(6).toString()) }
    var to by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var timeline by rememberSaveable { mutableStateOf(true) }
    var filter by remember { mutableStateOf<Subject?>(null) }
    var pickFilter by remember { mutableStateOf(false) }
    var pickNew by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Pair<FrontEntry, Boolean>?>(null) }
    val fromD = LocalDate.parse(from)
    val toD = LocalDate.parse(to)
    val rangeStart = fromD.startMs()
    val rangeEnd = minOf(toD.endMs(), now)
    val entries = remember(d.fronts, filter, rangeStart, rangeEnd) {
        d.fronts.filter {
            (filter == null || it.subjectId == filter?.id) && it.start < rangeEnd && (it.end ?: now) > rangeStart
        }
    }

    ScreenScaffold(
        "Histórico de front",
        actions = {
            IconButton(onClick = { pickFilter = true }) {
                Icon(Icons.Filled.FilterList, "Filtrar", tint = if (filter != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { timeline = !timeline }) {
                Icon(if (timeline) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.ShowChart, if (timeline) "Ver em lista" else "Ver gráfico")
            }
        },
        fab = { FloatingActionButton(onClick = { pickNew = true }) { Icon(Icons.Filled.Add, "Adicionar registro") } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            DateRangeBar(fromD, toD) { a, b -> from = a.toString(); to = b.toString() }
            filter?.let { f ->
                InputChip(
                    selected = true, onClick = { filter = null },
                    label = { Text("Só ${f.name}") },
                    trailingIcon = { Icon(Icons.Filled.Close, "Remover filtro") },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            if (entries.isEmpty()) {
                Box(Modifier.padding(16.dp)) { EmptyHint("Nenhum registro de front neste período.") }
            } else if (timeline) {
                FrontTimeline(d, entries, rangeStart, rangeEnd, now) { edit = it to false }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                    historyItems(d, entries, now, "h") { edit = it to false }
                }
            }
        }
    }
    if (pickFilter) SubjectPickerDialog(d, "Filtrar por", includeCustom = true, noneLabel = "Todos", onDismiss = { pickFilter = false }) {
        filter = it; pickFilter = false
    }
    if (pickNew) SubjectPickerDialog(d, "Quem esteve no front?", includeCustom = true, onDismiss = { pickNew = false }) { s ->
        pickNew = false
        if (s != null) {
            val t = System.currentTimeMillis()
            edit = FrontEntry(newId(), s.id, s.custom, t - 3_600_000, t) to true
        }
    }
    edit?.let { (e, isNew) -> EntryEditDialog(d, e, isNew) { edit = null } }
}

/** Gráfico vertical: uma coluna por membro, barras na cor dele, mais recente no topo. */
@Composable
private fun FrontTimeline(
    d: AppData,
    entries: List<FrontEntry>,
    rangeStart: Long,
    rangeEnd: Long,
    now: Long,
    onClick: (FrontEntry) -> Unit,
) {
    val hourDp = 44.dp
    val colW = 44.dp
    val gutter = 52.dp
    val hours = ((rangeEnd - rangeStart) / 3_600_000f).coerceAtLeast(1f)
    val totalH = hourDp * hours
    // colunas: quem esteve no front mais recentemente primeiro
    val subjects = remember(entries) {
        entries.sortedByDescending { it.end ?: Long.MAX_VALUE }.map { it.subjectId }.distinct()
    }
    fun yOf(t: Long) = hourDp * ((rangeEnd - t.coerceIn(rangeStart, rangeEnd)) / 3_600_000f)

    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row {
            // eixo das horas
            Box(Modifier.width(gutter).height(totalH + 40.dp)) {
                var t = (rangeEnd / 3_600_000) * 3_600_000
                while (t >= rangeStart) {
                    val h = java.time.Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault())
                    if (h.hour % 3 == 0) {
                        Text(
                            if (h.hour == 0) "%02d/%02d".format(h.dayOfMonth, h.monthValue) else "%02dh".format(h.hour),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (h.hour == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.offset(x = 6.dp, y = yOf(t) + 30.dp),
                        )
                    }
                    t -= 3_600_000
                }
            }
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                Box(Modifier.width(colW * subjects.size + 8.dp).height(totalH + 40.dp)) {
                    // linhas de meia-noite
                    var day = (rangeEnd / 3_600_000) * 3_600_000
                    while (day >= rangeStart) {
                        val h = java.time.Instant.ofEpochMilli(day).atZone(ZoneId.systemDefault())
                        if (h.hour == 0) Box(
                            Modifier.offset(y = yOf(day) + 36.dp).fillMaxWidth().height(1.dp)
                                .background(MaterialTheme.colorScheme.outline)
                        )
                        day -= 3_600_000
                    }
                    subjects.forEachIndexed { i, sid ->
                        val s = d.subject(sid)
                        val c = Color(s?.color ?: 0xFF9E9E9E)
                        // avatar no topo da coluna
                        SubjectAvatar(s, 32.dp, Modifier.offset(x = colW * i + 6.dp, y = 0.dp))
                        entries.filter { it.subjectId == sid }.forEach { e ->
                            val top = yOf(e.end ?: now)
                            val h = (yOf(e.start) - top).coerceAtLeast(10.dp)
                            Box(
                                Modifier
                                    .offset(x = colW * i + 10.dp, y = top + 36.dp)
                                    .width(24.dp)
                                    .height(h)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(c.copy(alpha = 0.85f))
                                    .border(if (e.end == null) 2.dp else 0.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(12.dp))
                                    .clickable { onClick(e) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ===================== Análises =====================

@Composable
fun StatsScreen(nav: NavHostController) {
    val d by Store.data.collectAsState()
    val now = rememberNow()
    var from by rememberSaveable { mutableStateOf(LocalDate.now().minusDays(29).toString()) }
    var to by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val modes = listOf("Tempo total", "Vezes", "Manhã", "Tarde", "Noite", "Madrugada")
    val rangeStart = LocalDate.parse(from).startMs()
    val rangeEnd = minOf(LocalDate.parse(to).endMs(), now)

    data class Row3(val sid: String, val dur: Long, val count: Int, val parts: IntArray)

    val rows = remember(d.fronts, rangeStart, rangeEnd, mode) {
        val map = mutableMapOf<String, Row3>()
        d.fronts.forEach { e ->
            val s = maxOf(e.start, rangeStart)
            val en = minOf(e.end ?: now, rangeEnd)
            if (en > s) {
                val r = map.getOrPut(e.subjectId) { Row3(e.subjectId, 0, 0, IntArray(4)) }
                val part = if (e.start >= rangeStart) {
                    when (java.time.Instant.ofEpochMilli(e.start).atZone(ZoneId.systemDefault()).hour) {
                        in 6..11 -> 0
                        in 12..17 -> 1
                        in 18..23 -> 2
                        else -> 3
                    }
                } else -1
                if (part >= 0) r.parts[part]++
                map[e.subjectId] = r.copy(dur = r.dur + (en - s), count = r.count + 1)
            }
        }
        map.values.sortedByDescending {
            when (mode) { 0 -> it.dur; 1 -> it.count.toLong(); else -> it.parts[mode - 2].toLong() }
        }.filter { mode < 2 || it.parts[mode - 2] > 0 }
    }
    val periodo = listOf("de manhã", "à tarde", "à noite", "de madrugada")

    ScreenScaffold("Análises") { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            DateRangeBar(LocalDate.parse(from), LocalDate.parse(to)) { a, b -> from = a.toString(); to = b.toString() }
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(modes.size) { i -> FilterChip(mode == i, { mode = i }, label = { Text(modes[i]) }) }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (rows.isEmpty()) item { Box(Modifier.padding(16.dp)) { EmptyHint("Sem dados de front neste período.") } }
                items(rows, key = { it.sid }) { r ->
                    val s = d.subject(r.sid)
                    val sub = when (mode) {
                        0 -> "Esteve no front por ${fmtDurationLong(r.dur)}"
                        1 -> "Entrou no front ${r.count} ${if (r.count == 1) "vez" else "vezes"}"
                        else -> {
                            val n = r.parts[mode - 2]
                            "Entrou no front $n ${if (n == 1) "vez" else "vezes"} ${periodo[mode - 2]}"
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { openSubject(nav, s) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SubjectAvatar(s, 52.dp, Modifier.padding(6.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(s?.name ?: "(excluído)", style = MaterialTheme.typography.titleMedium)
                            Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(Modifier.width(5.dp).fillMaxHeight().background(Color(s?.color ?: 0xFF9E9E9E)))
                    }
                }
            }
        }
    }
}

/** "31 dias 1 hora", "3 horas 20 minutos" */
fun fmtDurationLong(ms: Long): String {
    val m = maxOf(0L, ms) / 60_000
    val d = m / 1440
    val h = (m % 1440) / 60
    val mm = m % 60
    fun p(n: Long, s: String, pl: String) = "$n ${if (n == 1L) s else pl}"
    return when {
        d > 0 -> p(d, "dia", "dias") + " " + p(h, "hora", "horas")
        h > 0 -> p(h, "hora", "horas") + " " + p(mm, "minuto", "minutos")
        else -> p(mm, "minuto", "minutos")
    }
}
