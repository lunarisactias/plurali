package com.plurali.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plurali.app.data.*
import com.plurali.app.ui.theme.themedCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

// ---------------- formatação ----------------

private val ptBR = Locale("pt", "BR")
private val dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", ptBR)
private val tf = DateTimeFormatter.ofPattern("HH:mm", ptBR)
private val dayf = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", ptBR)
private val zone: ZoneId get() = ZoneId.systemDefault()

fun fmtDateTime(ms: Long): String = dtf.format(Instant.ofEpochMilli(ms).atZone(zone))
fun fmtTime(ms: Long): String = tf.format(Instant.ofEpochMilli(ms).atZone(zone))
fun fmtDay(d: LocalDate): String = dayf.format(d).replaceFirstChar { it.uppercase() }
fun dayOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

fun fmtDuration(ms: Long): String {
    val m = max(0L, ms) / 60_000
    val d = m / 1440
    val h = (m % 1440) / 60
    val mm = m % 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${mm}min"
        else -> "${mm}min"
    }
}

fun fmtWhen(ms: Long, now: Long): String {
    val diff = now - ms
    return when {
        diff < 60_000 -> "agora"
        diff < 86_400_000 && dayOf(ms) == dayOf(now) -> "hoje, ${fmtTime(ms)}"
        else -> fmtDateTime(ms)
    }
}

@Composable
fun rememberNow(intervalMs: Long = 30_000): Long {
    val v by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(intervalMs)
            value = System.currentTimeMillis()
        }
    }
    return v
}

// ---------------- cores ----------------

val palette: List<Long> = listOf(
    0xFFE57373, 0xFFF06292, 0xFFBA68C8, 0xFF9575CD, 0xFF7986CB, 0xFF64B5F6,
    0xFF4FC3F7, 0xFF4DD0E1, 0xFF4DB6AC, 0xFF81C784, 0xFFAED581, 0xFFDCE775,
    0xFFFFF176, 0xFFFFD54F, 0xFFFFB74D, 0xFFFF8A65, 0xFFA1887F, 0xFF90A4AE,
    0xFFC62828, 0xFF6A1B9A, 0xFF283593, 0xFF00695C, 0xFF2E7D32, 0xFF37474F,
)

fun onColor(c: Long): Color = if (Color(c).luminance() > 0.5f) Color.Black else Color.White

/** Cor do membro usada como TEXTO: clareia cores escuras no tema escuro e escurece cores claras no tema claro. */
@Composable
fun readable(c: Long): Color {
    val col = Color(c)
    return if (com.plurali.app.LocalExtra.current.dark) {
        if (col.luminance() >= 0.18f) col else androidx.compose.ui.graphics.lerp(col, Color.White, 0.45f)
    } else {
        if (col.luminance() <= 0.45f) col else androidx.compose.ui.graphics.lerp(col, Color.Black, 0.45f)
    }
}

fun hexOf(c: Long): String = "%06X".format(c and 0xFFFFFF)
fun parseHex(s: String): Long? {
    val t = s.trim().removePrefix("#")
    if (t.length != 6) return null
    return t.toLongOrNull(16)?.let { 0xFF000000 or it }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(color: Long, onChange: (Long) -> Unit) {
    var hex by remember(color) { mutableStateOf(hexOf(color)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            palette.forEach { c ->
                val sel = (c and 0xFFFFFF) == (color and 0xFFFFFF)
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(if (sel) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { onChange(c) },
                    contentAlignment = Alignment.Center,
                ) { if (sel) Icon(Icons.Filled.Check, null, tint = onColor(c), modifier = Modifier.size(18.dp)) }
            }
        }
        OutlinedTextField(
            value = hex,
            onValueChange = { v ->
                hex = v.take(7)
                parseHex(hex)?.let(onChange)
            },
            label = { Text("Cor (hex)") },
            prefix = { Text("#") },
            singleLine = true,
            leadingIcon = { Box(Modifier.size(20.dp).clip(CircleShape).background(Color(color))) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------- avatares ----------------

private val avatarCache = LruCache<String, ImageBitmap>(80)

@Composable
fun rememberAvatar(path: String?): ImageBitmap? {
    if (path == null) return null
    val cached = avatarCache.get(path)
    if (cached != null) return cached
    val bmp by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeFile(File(Store.filesDir, path).path)?.asImageBitmap()
            }.getOrNull()?.also { avatarCache.put(path, it) }
        }
    }
    return bmp
}

@Composable
fun Avatar(name: String, color: Long, avatar: String?, size: Dp = 44.dp, modifier: Modifier = Modifier, frame: String? = null) {
    if (frame != null) {
        // moldura escolhida pela pessoa (neon, vinhas…) em volta da foto
        com.plurali.app.ui.theme.FramedAvatar(frame, size, color, modifier) { Avatar(name, color, avatar, size) }
        return
    }
    val bmp = rememberAvatar(avatar)
    val ui = com.plurali.app.ui.theme.LocalUi.current
    val ears = ui.avatarId == "cat" || ui.avatarId == "bunny"
    Box(
        modifier.size(size).clip(ui.avatarShape).background(Color(color)),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            if (name.isNotBlank()) Text(
                name.trim().take(1).uppercase(),
                color = onColor(color),
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * (if (ears) 0.34f else 0.42f)).sp,
                modifier = if (ears) Modifier.padding(top = size * 0.24f) else Modifier,
            )
        }
    }
}

@Composable
fun SubjectAvatar(s: Subject?, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    if (s == null) Avatar("?", 0xFF9E9E9E, null, size, modifier)
    else Avatar(s.name, s.color, s.avatar, size, modifier, frame = s.frame)
}

/** Copia a imagem escolhida para o armazenamento do app (quadrada, máx. 512px). */
fun saveAvatar(ctx: Context, uri: Uri, memberId: String): String? =
    saveImage(ctx, uri, "avatars", memberId, aspect = 1f, maxSide = 512)

/**
 * Copia uma imagem para a pasta [dir] do app. [aspect] = largura/altura para recortar no centro
 * (1 = quadrada, 3 = capa larga, null = mantém), [maxSide] = maior lado em pixels.
 */
fun saveImage(ctx: Context, uri: Uri, dir: String, prefix: String, aspect: Float?, maxSide: Int): String? = runCatching {
    val bmp: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
            val s = info.size
            val scale = min(1f, (maxSide * 1.5f) / max(s.width, s.height))
            dec.setTargetSize(max(1, (s.width * scale).toInt()), max(1, (s.height * scale).toInt()))
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, o) }
        var ss = 1
        while (max(o.outWidth, o.outHeight) / ss > 1024) ss *= 2
        ctx.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = ss })
        }!!
    }
    var out = bmp
    if (aspect != null) {
        val cur = bmp.width.toFloat() / bmp.height
        val (w, h) = if (cur > aspect) ((bmp.height * aspect).toInt() to bmp.height) else (bmp.width to (bmp.width / aspect).toInt())
        out = Bitmap.createBitmap(bmp, (bmp.width - w) / 2, (bmp.height - h) / 2, max(1, w), max(1, h))
    }
    val longest = max(out.width, out.height)
    if (longest > maxSide) {
        val f = maxSide.toFloat() / longest
        out = Bitmap.createScaledBitmap(out, max(1, (out.width * f).toInt()), max(1, (out.height * f).toInt()), true)
    }
    File(ctx.filesDir, dir).mkdirs()
    val name = "$dir/${prefix}_${System.currentTimeMillis()}.jpg"
    File(ctx.filesDir, name).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    name
}.getOrNull()

/** Imagem salva no app (capa, foto de conversa). */
@Composable
fun StoredImage(path: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val bmp = rememberAvatar(path) ?: return
    Image(bmp, null, modifier, contentScale = contentScale)
}

// ---------------- layout ----------------

/** Abre o menu lateral (☰). Fornecido pelo App. */
val LocalOpenDrawer = staticCompositionLocalOf<() -> Unit> { {} }

/** Tela padrão: barra superior com ☰ (telas principais) ou ← (telas internas). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val openDrawer = LocalOpenDrawer.current
    Box(Modifier.fillMaxSize()) {
    com.plurali.app.ui.theme.AppBackground(Modifier.matchParentSize())
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            Column {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    } else IconButton(onClick = openDrawer) {
                        Icon(Icons.Filled.Menu, "Menu")
                    }
                },
                actions = actions,
                colors = com.plurali.app.LocalExtra.current.let { ex ->
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = ex.topBar, scrolledContainerColor = ex.topBar,
                        titleContentColor = ex.barContent, navigationIconContentColor = ex.barContent, actionIconContentColor = ex.barContent,
                    )
                },
            )
            com.plurali.app.ui.theme.ThemeDecorBand()
            }
        },
        bottomBar = bottomBar,
        floatingActionButton = fab,
        contentWindowInsets = WindowInsets.safeDrawing,
        content = content,
    )
    }
}

// ---------------- componentes no estilo SimplyPlural ----------------

/** Cartão com a "meia-lua" colorida na borda esquerda (cor do membro/grupo). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpRow(
    color: Long,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val ui = com.plurali.app.ui.theme.LocalUi.current
    Row(
        modifier
            .fillMaxWidth()
            .themedCard(ui, MaterialTheme.colorScheme.surfaceContainer, Color(color), MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.onSurface)
            .then(
                if (onClick != null || onLongClick != null)
                    Modifier.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
                else Modifier
            )
            .padding(
                start = if (ui.strip == com.plurali.app.ui.theme.Strip.LEFT) 14.dp else 10.dp, end = 10.dp,
                top = 8.dp + ui.cardTopExtra + (when (ui.strip) { com.plurali.app.ui.theme.Strip.TOP -> 3.dp; com.plurali.app.ui.theme.Strip.SPOTS -> 8.dp; else -> 0.dp }),
                bottom = 8.dp + ui.cardBottomExtra,
            ),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Caixa no estilo do tema para blocos sem dono (configurações, avisos, textos). */
@Composable
fun ThemedBox(modifier: Modifier = Modifier, color: Long? = null, padding: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    val ui = com.plurali.app.ui.theme.LocalUi.current
    Column(
        modifier
            .fillMaxWidth()
            .themedCard(ui, MaterialTheme.colorScheme.surfaceContainer, color?.let { Color(it) }, MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.onSurface)
            .padding(start = padding, end = padding, top = padding + ui.cardTopExtra, bottom = padding + ui.cardBottomExtra),
        content = content,
    )
}

/** Botão quadrado com borda na cor do membro: ↑ entra no front, ↓ sai. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FrontButton(
    color: Long,
    fronting: Boolean,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val c = Color(color)
    Box(
        modifier
            .width(60.dp)
            .fillMaxHeight()
            .defaultMinSize(minHeight = 52.dp)
            .clip(shape)
            .background(com.plurali.app.LocalExtra.current.frontButton)
            .border(2.dp, if (fronting) c else c.copy(alpha = 0.45f), shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (fronting) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
            if (fronting) "Sair do front" else "Entrar no front",
            tint = if (fronting) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun NameAndPronouns(name: String, pronouns: String, modifier: Modifier = Modifier, extra: String = "") {
    Column(modifier) {
        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        pronouns.split("\n").filter { it.isNotBlank() }.forEach {
            Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
        if (extra.isNotBlank()) Text(extra, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Cronômetro estilo SP: 45:50:50 (horas podem passar de 24). */
fun fmtTimer(ms: Long): String {
    val s = max(0L, ms) / 1000
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

private val shortDtf = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale("pt", "BR"))
fun fmtShort(ms: Long): String = shortDtf.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
private val dateOnly = DateTimeFormatter.ofPattern("dd/MM/yyyy")
fun fmtDate(d: LocalDate): String = dateOnly.format(d)

/** Barra "📅 22/01/2022  >  📅 29/01/2022" usada no histórico e nas análises. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeBar(from: LocalDate, to: LocalDate, onChange: (LocalDate, LocalDate) -> Unit) {
    var picking by remember { mutableStateOf<Int?>(null) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DatePill(fmtDate(from), Modifier.weight(1f)) { picking = 0 }
        Icon(Icons.Filled.ChevronRight, null, Modifier.padding(horizontal = 8.dp))
        DatePill(fmtDate(to), Modifier.weight(1f)) { picking = 1 }
    }
    picking?.let { which ->
        val init = if (which == 0) from else to
        val st = rememberDatePickerState(initialSelectedDateMillis = init.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let {
                        val d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        if (which == 0) onChange(d, if (to < d) d else to) else onChange(if (from > d) d else from, d)
                    }
                    picking = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancelar") } },
        ) { DatePicker(st) }
    }
}

@Composable
private fun DatePill(text: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.themed()
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CalendarMonth, null, Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    val ui = com.plurali.app.ui.theme.LocalUi.current
    val accent = MaterialTheme.colorScheme.primary
    val label = if (ui.headerMark.isNotEmpty()) ui.headerMark + "  " + text else text
    val style = ui.theme.headerStyle
    when (style) {
        "pill" -> Text(
            label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = modifier.padding(top = 16.dp, bottom = 6.dp).clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 12.dp, vertical = 4.dp),
        )
        else -> Column(modifier.padding(top = 16.dp, bottom = 6.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = accent)
            if (style != "plain") androidx.compose.foundation.Canvas(Modifier.padding(top = 3.dp).width(120.dp).height(5.dp)) {
                when (style) {
                    "underline" -> drawLine(accent, androidx.compose.ui.geometry.Offset(0f, size.height / 2), androidx.compose.ui.geometry.Offset(size.width, size.height / 2), strokeWidth = 2.5f * density)
                    "dashed" -> drawLine(
                        accent, androidx.compose.ui.geometry.Offset(0f, size.height / 2), androidx.compose.ui.geometry.Offset(size.width, size.height / 2),
                        strokeWidth = 2f * density, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6 * density, 4 * density)),
                    )
                    else -> { // wavy
                        val p = androidx.compose.ui.graphics.Path().apply {
                            moveTo(0f, size.height / 2); var x = 0f; var up = true
                            while (x < size.width) { quadraticTo(x + 5 * density, if (up) 0f else size.height, x + 10 * density, size.height / 2); x += 10 * density; up = !up }
                        }
                        drawPath(p, accent, style = androidx.compose.ui.graphics.drawscope.Stroke(1.8f * density))
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "Excluir", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun TextInputDialog(title: String, initial: String, label: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(v, { v = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = {
            TextButton(enabled = v.isNotBlank(), onClick = { onConfirm(v.trim()); onDismiss() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Escolher um membro (e opcionalmente custom front). noneLabel adiciona uma opção "nenhum". */
@Composable
fun SubjectPickerDialog(
    d: AppData,
    title: String,
    includeCustom: Boolean,
    noneLabel: String? = null,
    onDismiss: () -> Unit,
    onPick: (Subject?) -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val list = remember(d, q) {
        (d.members.filter { !it.archived }.sortedBy { it.name.lowercase() }.map { it.toSubject() } +
            (if (includeCustom) d.customFronts.sortedBy { it.name.lowercase() }.map { it.toSubject() } else emptyList()))
            .filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    q, { q = it }, placeholder = { Text("Buscar") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) }, modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    if (noneLabel != null) item {
                        ListItem(
                            headlineContent = { Text(noneLabel) },
                            leadingContent = { Avatar("?", 0xFF9E9E9E, null, 36.dp) },
                            modifier = Modifier.clickable { onPick(null) },
                        )
                    }
                    items(list, key = { it.id }) { s ->
                        ListItem(
                            headlineContent = { Text(s.name) },
                            supportingContent = { if (s.custom) Text("Custom front") else if (s.pronouns.isNotBlank()) Text(s.pronouns) },
                            leadingContent = { SubjectAvatar(s, 36.dp) },
                            modifier = Modifier.clickable { onPick(s) },
                        )
                    }
                    if (list.isEmpty()) item { EmptyHint("Ninguém encontrado. Cadastre membros na aba Membros.") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(initial: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val initDt = remember { Instant.ofEpochMilli(initial).atZone(zone) }
    var step by remember { mutableIntStateOf(0) }
    var date by remember { mutableStateOf(initDt.toLocalDate()) }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = initDt.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    val timeState = rememberTimePickerState(initDt.hour, initDt.minute, is24Hour = true)
    if (step == 0) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    step = 1
                }) { Text("Próximo") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        ) { DatePicker(dateState) }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    onConfirm(date.atTime(timeState.hour, timeState.minute).atZone(zone).toInstant().toEpochMilli())
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text("Voltar") } },
            text = { TimePicker(timeState) },
        )
    }
}

// ---------------- histórico (compartilhado) ----------------

fun LazyListScope.historyItems(
    d: AppData,
    entries: List<FrontEntry>,
    now: Long,
    keyPrefix: String,
    onClick: (FrontEntry) -> Unit,
) {
    val byDay = entries.sortedByDescending { it.start }.groupBy { dayOf(it.start) }
    byDay.forEach { (day, list) ->
        item(key = "$keyPrefix-day-$day") { SectionHeader(fmtDay(day), Modifier.padding(horizontal = 16.dp)) }
        items(list, key = { "$keyPrefix-${it.id}" }) { e -> HistoryRow(d, e, now, onClick) }
    }
}

@Composable
fun HistoryRow(d: AppData, e: FrontEntry, now: Long, onClick: (FrontEntry) -> Unit) {
    val s = d.subject(e.subjectId)
    val end = e.end
    val endText = when {
        end == null -> "agora"
        dayOf(end) == dayOf(e.start) -> fmtTime(end)
        else -> fmtDateTime(end)
    }
    ListItem(
        modifier = Modifier.clickable { onClick(e) },
        leadingContent = { SubjectAvatar(s, 36.dp) },
        headlineContent = { Text(s?.name ?: "(excluído)") },
        supportingContent = {
            Column {
                Text("${fmtTime(e.start)} – $endText · ${fmtDuration((end ?: now) - e.start)}")
                if (e.status.isNotBlank()) Text(e.status, style = MaterialTheme.typography.bodySmall)
                if (e.comment.isNotBlank()) Text(e.comment, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = {
            if (end == null) Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("no front") }
        },
    )
}

@Composable
fun EntryEditDialog(d: AppData, initial: FrontEntry, isNew: Boolean, onDismiss: () -> Unit) {
    var e by remember { mutableStateOf(initial) }
    var pick by remember { mutableStateOf<String?>(null) }
    val end = e.end
    val valid = end == null || end >= e.start
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(d.subject(e.subjectId)?.name ?: "(excluído)") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pick = "start" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Início: ${fmtDateTime(e.start)}")
                }
                OutlinedButton(onClick = { pick = "end" }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (end == null) "Fim: em andamento" else "Fim: ${fmtDateTime(end)}")
                }
                if (end != null) TextButton(onClick = { e = e.copy(end = null) }) { Text("Marcar como em andamento") }
                if (!valid) Text("O fim está antes do início.", color = MaterialTheme.colorScheme.error)
                OutlinedTextField(e.status, { e = e.copy(status = it) }, label = { Text("Status") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(e.comment, { e = e.copy(comment = it) }, label = { Text("Comentário") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { Store.upsertEntry(e); onDismiss() }) { Text("Salvar") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { Store.deleteEntry(e.id); onDismiss() }) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
    pick?.let { which ->
        DateTimePickerDialog(
            initial = if (which == "start") e.start else (e.end ?: System.currentTimeMillis()),
            onDismiss = { pick = null },
        ) { t ->
            e = if (which == "start") e.copy(start = t) else e.copy(end = t)
            pick = null
        }
    }
}

/** Deixa uma caixa comum com o formato, fundo e contorno do tema atual. */
fun Modifier.themed(): Modifier = composed {
    val ui = com.plurali.app.ui.theme.LocalUi.current
    this.themedCard(ui, MaterialTheme.colorScheme.surfaceContainer, null, MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.onSurface)
        .padding(top = ui.cardTopExtra, bottom = ui.cardBottomExtra)
}
