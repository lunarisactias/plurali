package com.plurali.companion

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plurali.shared.*
import com.plurali.app.ui.theme.themedCard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// cores do tema em uso (o mesmo do Plurali, seguindo quem está no front)
private val PGold: Color @Composable get() = MaterialTheme.colorScheme.primary
private val PBg: Color @Composable get() = MaterialTheme.colorScheme.background
private val PCard: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer
private val PField: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
private val PMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val PPron: Color @Composable get() = MaterialTheme.colorScheme.tertiary
private val PGreen = Color(0xFF66BB6A)
private val pt = Locale("pt", "BR")

private fun b64Image(ref: String?): ImageBitmap? = MediaCache.bitmap(ref)?.asImageBitmap()

private fun dur(ms: Long): String {
    val m = maxOf(0, ms) / 60_000
    return when { m >= 1440 -> "${m / 1440}d ${(m % 1440) / 60}h"; m >= 60 -> "${m / 60}h ${m % 60}min"; else -> "${m}min" }
}

private fun dt(ms: Long, p: String) = DateTimeFormatter.ofPattern(p, pt).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

/** "12/03" ou "12/03/1999" → texto amigável com quantos dias faltam (e idade, se tiver ano). */
private fun birthdayText(raw: String, today: LocalDate): String {
    val parts = raw.trim().split('/', '-', '.').mapNotNull { it.trim().toIntOrNull() }
    if (parts.size < 2) return raw
    val (d, m) = parts
    val y = parts.getOrNull(2)?.let { if (it < 100) it + 1900 else it }
    val next = runCatching {
        var n = LocalDate.of(today.year, m, d)
        if (n < today) n = n.plusYears(1)
        n
    }.getOrNull() ?: return raw
    val days = ChronoUnit.DAYS.between(today, next)
    val date = next.format(DateTimeFormatter.ofPattern("d 'de' MMMM", pt))
    val whenTxt = when (days) { 0L -> "é hoje! 🎉"; 1L -> "amanhã"; else -> "daqui a $days dias" }
    val age = y?.let { " · faz ${next.year - it} anos" } ?: ""
    return "$date · $whenTxt$age"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonScreen(s: Snapshot, start: SnapPerson, onBack: () -> Unit) {
    // permite ir de uma pessoa para outra pelas relações, voltando pela pilha
    val stack = remember { mutableStateListOf(start.id) }
    val p = s.people?.find { it.id == stack.last() } ?: start
    BackHandler { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onBack() }
    val now = System.currentTimeMillis()
    val today = LocalDate.now()
    val color = Color(p.color)
    val onColor = if (color.luminance() > 0.5f) Color.Black else Color.White

    val fronting = s.front?.find { it.personId == p.id }
    val hist = s.history?.filter { it.personId == p.id || (it.personId == null && it.name == p.name) }?.sortedByDescending { it.start }
    val events = s.events.orEmpty().filter { p.id in it.personIds }
    val upcoming = (0..60L).flatMap { i -> val d = today.plusDays(i); events.filter { it.occursOn(d) }.map { d to it } }.take(6)

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(p.name) },
                navigationIcon = {
                    IconButton(onClick = { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                },
                colors = com.plurali.app.LocalExtra.current.let { ex ->
                    TopAppBarDefaults.topAppBarColors(containerColor = ex.topBar, titleContentColor = ex.barContent, navigationIconContentColor = ex.barContent)
                },
            )
        },
    ) { pad ->
        com.plurali.app.ui.theme.ThemedBackground(Modifier.fillMaxSize())
        LazyColumn(Modifier.padding(pad).fillMaxSize(), state = remember(p.id) { LazyListState() }, contentPadding = PaddingValues(bottom = 24.dp)) {
            // ---------- cabeçalho: capa + foto + nome ----------
            item {
                val banner = remember(p.banner, MediaCache.version) { b64Image(p.banner) }
                Box(Modifier.fillMaxWidth().height(200.dp)) {
                    if (banner != null) Image(banner, null, Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Crop)
                    else Box(
                        Modifier.fillMaxWidth().height(150.dp)
                            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0.25f)))),
                    )
                    // contorno com o mesmo formato da foto (que muda conforme o tema: círculo, orelhas, etc.)
                    Box(
                        Modifier.align(Alignment.BottomStart).padding(start = 16.dp)
                            .size(116.dp).clip(com.plurali.app.ui.theme.LocalUi.current.avatarShape).background(PBg),
                        contentAlignment = Alignment.Center,
                    ) { BigAvatar(p.name, p.color, p.avatar, 104.dp, p.frame) }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (p.pronouns.isNotBlank()) Text(p.pronouns, color = PPron, style = MaterialTheme.typography.titleMedium)
                    val roles = p.role.split(',', '/', ';').map { it.trim() }.filter { it.isNotEmpty() }
                    if (roles.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        roles.forEach { Pill(it, color, onColor) }
                    }
                    when {
                        fronting != null -> StatusLine(PGreen, "No front agora · há ${dur(now - fronting.since)}" +
                            (if (fronting.status.isNotBlank()) " · ${fronting.status}" else ""))
                        !hist.isNullOrEmpty() -> StatusLine(PMuted, "Último front: há ${dur(now - (hist.first().end ?: now))}")
                    }
                }
            }

            // ---------- textos principais ----------
            if (p.intro.isNotBlank()) item { Section(Icons.Filled.WavingHand, "Quem sou eu", color, highlight = true) { Text(p.intro) } }
            if (p.comfort.isNotBlank()) item { Section(Icons.Filled.Spa, "Como me acalmar", color) { Text(p.comfort) } }
            if (p.description.isNotBlank()) item { Section(Icons.Filled.Person, "Sobre", color) { Text(p.description) } }
            if (p.likes.isNotBlank() || p.dislikes.isNotBlank()) item {
                Section(Icons.Filled.Favorite, "Gostos", color) {
                    if (p.likes.isNotBlank()) LabeledText(Icons.Filled.ThumbUp, "Gosta de", p.likes, PGreen)
                    if (p.likes.isNotBlank() && p.dislikes.isNotBlank()) Spacer(Modifier.height(10.dp))
                    if (p.dislikes.isNotBlank()) LabeledText(Icons.Filled.ThumbDown, "Não gosta de", p.dislikes, Color(0xFFEF6B6B))
                }
            }
            if (p.birthday.isNotBlank()) item {
                Section(Icons.Filled.Cake, "Aniversário", color) { Text(birthdayText(p.birthday, today)) }
            }
            if (p.fields.isNotEmpty()) item {
                Section(Icons.Filled.ListAlt, "Informações", color) {
                    p.fields.forEachIndexed { i, f ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFF3B3E47))
                        Text(f.name, color = PMuted, style = MaterialTheme.typography.labelLarge)
                        Text(f.value)
                    }
                }
            }
            if (p.groups.isNotEmpty()) item {
                Section(Icons.Filled.Workspaces, "Grupos", color) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        p.groups.forEach { Pill(it, PField, Color(0xFFECECEE)) }
                    }
                }
            }
            if (p.relations.isNotEmpty()) item {
                Section(Icons.Filled.Hub, "Relações", color) {
                    p.relations.forEachIndexed { i, r ->
                        val other = s.people?.find { it.id == r.otherId }
                        if (i > 0) Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(PField)
                                .then(if (other != null) Modifier.clickable { stack.add(other.id) } else Modifier).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (other != null) { BigAvatar(other.name, other.color, other.avatar, 36.dp, other.frame); Spacer(Modifier.width(10.dp)) }
                            Column(Modifier.weight(1f)) {
                                Text(r.text)
                                if (r.note.isNotBlank()) Text(r.note, style = MaterialTheme.typography.bodySmall, color = PMuted)
                            }
                            if (other != null) Icon(Icons.AutoMirrored.Filled.ArrowForward, "Abrir perfil de ${other.name}", tint = PMuted)
                        }
                    }
                }
            }

            // ---------- wishlist ----------
            p.wishlist?.let { wl ->
                listOf("buy" to "Quer comprar", "do" to "Quer fazer").forEach { (kind, title) ->
                    val items = wl.filter { it.kind == kind }
                    item {
                        Section(if (kind == "buy") Icons.Filled.CardGiftcard else Icons.Filled.Explore,
                            "Wishlist · $title" + if (items.isNotEmpty()) " (${items.count { !it.done }})" else "", color) {
                            if (items.isEmpty()) Text("Nada por enquanto.", color = PMuted)
                            items.forEach { w ->
                                Row(Modifier.padding(vertical = 3.dp)) {
                                    Icon(
                                        if (w.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                                        Modifier.size(20.dp).padding(top = 2.dp), tint = if (w.done) PMuted else PGold,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(w.text, color = if (w.done) PMuted else Color.Unspecified,
                                            textDecoration = if (w.done) TextDecoration.LineThrough else null)
                                        if (w.note.isNotBlank()) Text(w.note, style = MaterialTheme.typography.bodySmall, color = PMuted)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---------- agenda e front ----------
            if (upcoming.isNotEmpty()) item {
                Section(Icons.Filled.Event, "Próximos compromissos", color) {
                    upcoming.forEachIndexed { i, (d, e) ->
                        if (i > 0) Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(e.color)))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(e.title, fontWeight = FontWeight.Medium)
                                Text(
                                    (if (d == today) "Hoje" else if (d == today.plusDays(1)) "Amanhã" else d.format(DateTimeFormatter.ofPattern("EEE, d MMM", pt))) +
                                        (if (e.allDay) "" else " · " + dt(e.start, "HH:mm")),
                                    style = MaterialTheme.typography.bodySmall, color = PMuted,
                                )
                            }
                        }
                    }
                }
            }
            if (hist != null) item {
                Section(Icons.Filled.History, "Front nos últimos 14 dias", color) {
                    if (hist.isEmpty()) Text("Não esteve no front nesse período.", color = PMuted)
                    else {
                        val total = hist.sumOf { (it.end ?: now) - it.start }
                        Text("${dur(total)} no total · ${hist.size} ${if (hist.size == 1) "vez" else "vezes"}", fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        hist.take(5).forEach { h ->
                            Text(
                                "${dt(h.start, "dd/MM HH:mm")} – ${h.end?.let { dt(it, "HH:mm") } ?: "agora"} · ${dur((h.end ?: now) - h.start)}",
                                style = MaterialTheme.typography.bodySmall, color = PMuted,
                            )
                        }
                    }
                }
            }

            val empty = p.intro.isBlank() && p.comfort.isBlank() && p.description.isBlank() && p.likes.isBlank() && p.dislikes.isBlank() &&
                p.birthday.isBlank() && p.fields.isEmpty() && p.groups.isEmpty() && p.relations.isEmpty() && p.wishlist == null
            if (empty) item {
                Text(
                    "O sistema não liberou outros detalhes deste perfil.",
                    color = PMuted, modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
    }
}

@Composable
private fun BigAvatar(name: String, color: Long, b64: String?, size: Dp, frame: String? = null) {
    if (frame != null) { com.plurali.app.ui.theme.FramedAvatar(frame, size, color) { BigAvatar(name, color, b64, size) }; return }
    val bmp = remember(b64, MediaCache.version) { b64Image(b64) }
    val ui = com.plurali.app.ui.theme.LocalUi.current
    val ears = ui.avatarId == "cat" || ui.avatarId == "bunny"
    Box(Modifier.size(size).clip(ui.avatarShape).background(Color(color)), contentAlignment = Alignment.Center) {
        if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(name.take(1).uppercase(), color = if (Color(color).luminance() > 0.5f) Color.Black else Color.White,
            fontWeight = FontWeight.Bold, fontSize = (size.value * (if (ears) 0.34f else 0.42f)).sp, modifier = if (ears) Modifier.padding(top = size * 0.24f) else Modifier)
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color) {
    Text(text, color = fg, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 5.dp))
}

@Composable
private fun StatusLine(dot: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (dot == PMuted) PMuted else Color.Unspecified)
    }
}

@Composable
private fun LabeledText(icon: ImageVector, label: String, text: String, tint: Color) {
    Row {
        Icon(icon, null, Modifier.size(18.dp).padding(top = 2.dp), tint = tint)
        Spacer(Modifier.width(8.dp))
        Column { Text(label, color = PMuted, style = MaterialTheme.typography.labelLarge); Text(text) }
    }
}

/** Cartão de seção com faixa da cor do membro (estilo do perfil no Plurali). */
@Composable
private fun Section(icon: ImageVector, title: String, color: Color, highlight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val ui = com.plurali.app.ui.theme.LocalUi.current
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 5.dp).fillMaxWidth()
            .themedCard(ui, if (highlight) androidx.compose.ui.graphics.lerp(PCard, color, 0.16f) else PCard, color, MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.onSurface)
            .padding(top = ui.cardTopExtra, bottom = ui.cardBottomExtra),
    ) {
        Column(Modifier.padding(start = 14.dp + (if (ui.strip == com.plurali.app.ui.theme.Strip.LEFT) 4.dp else 0.dp), end = 14.dp, top = 14.dp + (if (ui.strip == com.plurali.app.ui.theme.Strip.SPOTS) 8.dp else 0.dp), bottom = 14.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(18.dp), tint = PGold)
                Spacer(Modifier.width(8.dp))
                Text(title, color = PGold, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
