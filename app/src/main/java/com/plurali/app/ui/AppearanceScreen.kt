package com.plurali.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.plurali.app.PluraliTheme
import com.plurali.app.data.*
import com.plurali.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val FONTS = listOf("sans" to "Normal", "serif" to "Serifada", "cursive" to "Manuscrita", "mono" to "Máquina de escrever")
private fun fontOf(id: String) = when (id) { "serif" -> FontFamily.Serif; "cursive" -> FontFamily.Cursive; "mono" -> FontFamily.Monospace; else -> FontFamily.SansSerif }

/**
 * Aparência por perfil. O Sistema pode editar a de qualquer perfil; cada membro edita a sua.
 * A própria tela já aparece no visual que está sendo editado (prévia ao vivo).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(nav: NavHostController) {
    val d by Store.data.collectAsState()
    val who = LocalWho.current
    val profiles: List<Pair<String, String>> = buildList {
        if (who.isAdmin) {
            add("system" to "Sistema (padrão)")
            d.members.filter { !it.archived }.sortedBy { it.name.lowercase() }.forEach { add(it.id to it.name) }
        } else who.meId?.let { id -> add(id to (d.members.find { it.id == id }?.name ?: "Eu")) }
    }
    var key by remember { mutableStateOf(appearanceKey(Session.who).takeIf { k -> profiles.any { it.first == k } } ?: profiles.firstOrNull()?.first ?: "system") }
    val own = d.appearance[key]
    val a = own ?: defaultAppearance()
    fun set(n: Appearance) = Store.setAppearance(key, n)

    PluraliTheme(a) {
        ScreenScaffold("Aparência", onBack = { nav.popBackStack() }) { pad ->
            AppearanceBody(Modifier.padding(pad), d, key, profiles, own != null, a, onKey = { key = it }, set = ::set)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceBody(
    modifier: Modifier,
    d: AppData,
    key: String,
    profiles: List<Pair<String, String>>,
    hasOwn: Boolean,
    a: Appearance,
    onKey: (String) -> Unit,
    set: (Appearance) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ui = LocalUi.current
    val pickWall = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val p = withContext(Dispatchers.IO) { saveImage(ctx, uri, "wallpapers", key, aspect = null, maxSide = 1600) }
            if (p != null) set(a.copy(wallpaper = p, pattern = if (a.pattern > 0.6f) 0.6f else a.pattern))
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ---------- de quem é ----------
        if (profiles.size > 1) {
            Text("Perfil", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                profiles.forEach { (k, l) ->
                    FilterChip(k == key, { onKey(k) }, label = { Text(l) },
                        leadingIcon = if (d.appearance.containsKey(k)) ({ Icon(Icons.Filled.Palette, null, Modifier.size(16.dp)) }) else null)
                }
            }
        }
        Text(
            when {
                key == "system" -> "Visual da conta Sistema (e do app todo quando os logins estão desligados). A tela “Quem está usando?” sempre usa o tema padrão."
                hasOwn -> "Este perfil tem um visual próprio. Ele aparece quando a pessoa entra na conta dela."
                else -> "Este perfil usa o tema padrão (como um alter novo). Qualquer mudança aqui cria um visual só dele."
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (key != "system" && hasOwn) TextButton(onClick = { Store.resetAppearance(key) }) {
            Icon(Icons.Filled.Restore, null); Spacer(Modifier.width(6.dp)); Text("Voltar ao tema padrão")
        }

        // ---------- prévia ----------
        SectionHeader("Prévia")
        PreviewBlock(d)

        // ---------- temas ----------
        SectionHeader("Tema")
        val cols = 2
        THEMES.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { th ->
                    ThemeTile(th, a.theme == th.id, Modifier.weight(1f)) {
                        // trocar de tema volta as escolhas "do tema" para o padrão dele
                        set(a.copy(theme = th.id, accent = null))
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (a.theme == "classic") {
            Text("Modo do Clássico", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("dark" to "Escuro", "light" to "Claro", "system" to "Seguir o celular").forEach { (k, l) ->
                    FilterChip(a.mode == k, { set(a.copy(mode = k)) }, label = { Text(l) })
                }
            }
        }

        // ---------- cor ----------
        SectionHeader("Cor de destaque")
        FilterChip(a.accent == null, { set(a.copy(accent = null)) }, label = { Text("Cor do tema") },
            leadingIcon = { Box(Modifier.size(16.dp).clip(RoundedCornerShape(50)).background(Color(ui.theme.palette.accent))) })
        ColorPicker(a.accent ?: ui.theme.palette.accent) { set(a.copy(accent = it)) }

        // ---------- formatos ----------
        SectionHeader("Formato dos cartões")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CARD_SHAPES.forEach { (k, l) ->
                ShapeOption(l, cardShapeOf(k, ui.theme), a.cardShape == k, wide = true) { set(a.copy(cardShape = k)) }
            }
        }
        SectionHeader("Formato das fotos")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShapeOption("Do tema", avatarShapeOf(ui.theme.avatar)!!, a.avatarShape == "theme") { set(a.copy(avatarShape = "theme")) }
            AVATAR_SHAPES.forEach { (k, l, s) -> ShapeOption(l, s, a.avatarShape == k) { set(a.copy(avatarShape = k)) } }
        }
        SectionHeader("Cor de cada membro nos cartões")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("theme" to "Do tema", "left" to "Faixa lateral", "top" to "Faixa no topo", "border" to "Borda", "glow" to "Brilho",
                "corner" to "Coração no canto", "tape" to "Fita adesiva", "spots" to "Faixa de bolinhas", "none" to "Sem cor").forEach { (k, l) ->
                FilterChip(a.strip == k, { set(a.copy(strip = k)) }, label = { Text(l) })
            }
        }
        SectionHeader("Contorno")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("theme" to "Do tema", "none" to "Sem", "solid" to "Linha", "dashed" to "Tracejado", "dotted" to "Pontilhado",
                "double" to "Duplo", "sketch" to "Lápis", "glow" to "Brilho").forEach { (k, l) ->
                FilterChip(a.border == k, { set(a.copy(border = k)) }, label = { Text(l) })
            }
        }

        // ---------- letras ----------
        SectionHeader("Letras")
        Text("Títulos", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(a.titleFont == "theme", { set(a.copy(titleFont = "theme")) }, label = { Text("Do tema", fontFamily = fontOf(ui.theme.titleFont)) })
            FONTS.forEach { (k, l) -> FilterChip(a.titleFont == k, { set(a.copy(titleFont = k)) }, label = { Text(l, fontFamily = fontOf(k)) }) }
        }
        Text("Texto", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FONTS.forEach { (k, l) -> FilterChip(a.bodyFont == k, { set(a.copy(bodyFont = k)) }, label = { Text(l, fontFamily = fontOf(k)) }) }
        }
        Text("Tamanho", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0.9f to "Pequeno", 1f to "Normal", 1.15f to "Grande", 1.3f to "Maior", 1.5f to "Enorme").forEach { (v, l) ->
                FilterChip(a.textScale == v, { set(a.copy(textScale = v)) }, label = { Text(l) })
            }
        }

        // ---------- fundo ----------
        SectionHeader("Fundo")
        Text(
            if (ui.theme.pattern == "none") "O tema Clássico não tem desenhos de fundo." else "Desenhos do tema: " + when {
                a.pattern <= 0f -> "desligados"; a.pattern < 0.7f -> "suaves"; a.pattern < 1.3f -> "normais"; else -> "fortes"
            },
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(a.pattern, { set(a.copy(pattern = (it * 10).toInt() / 10f)) }, valueRange = 0f..2f, steps = 9, enabled = ui.theme.pattern != "none")
        ToggleLine("Animar o fundo", "Brasas sobem, brilhos piscam, aranhas balançam… Gasta um pouco mais de bateria.", a.animate) { set(a.copy(animate = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { pickWall.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Icon(Icons.Filled.Wallpaper, null); Spacer(Modifier.width(6.dp)); Text(if (a.wallpaper == null) "Imagem de fundo" else "Trocar imagem")
            }
            if (a.wallpaper != null) TextButton(onClick = { set(a.copy(wallpaper = null)) }) { Text("Remover") }
        }
        if (a.wallpaper != null) {
            Text("Escurecer a imagem (para ler melhor)", style = MaterialTheme.typography.labelLarge)
            Slider(a.wallpaperDim, { set(a.copy(wallpaperDim = it)) }, valueRange = 0f..0.95f)
        }

        // ---------- enfeites ----------
        SectionHeader("Enfeites")
        ToggleLine("Enfeite embaixo do título", "Patinhas, trepadeira, teia, chamas, brilhos, escala de mapa, formigas ou lápis.", a.decor) { set(a.copy(decor = it)) }
        ToggleLine("Símbolo nos títulos de seção", "Ex.: 🐾, 🌿, 🕸️, 🥕, 🔥, ✨, 🗺️, 🐝, ✏️", a.headerMarks) { set(a.copy(headerMarks = it)) }
        if (key == "system") ToggleLine(
            "Usar o visual de quem está no front",
            "Quando o Sistema está aberto (ou os logins estão desligados), o app troca para o visual de quem está no front, se a pessoa tiver um.",
            a.followFront,
        ) { set(a.copy(followFront = it)) }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun ToggleLine(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(value, onChange)
    }
}

/** Opção de formato: desenha a forma de verdade. */
@Composable
private fun ShapeOption(label: String, shape: Shape, selected: Boolean, wide: Boolean = false, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier.width(if (wide) 92.dp else 74.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(if (wide) 76.dp else 44.dp, 44.dp)) {
            drawOutline(shape.createOutline(size, layoutDirection, this), accent.copy(alpha = 0.35f))
            drawOutline(shape.createOutline(size, layoutDirection, this), accent, style = Stroke(2 * density))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Miniatura de um tema: fundo com os desenhos, um cartão no formato dele e o avatar. */
@Composable
private fun ThemeTile(th: ThemeDef, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = th.palette
    val tf = fontOf(th.titleFont)
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .border(if (selected) 3.dp else 1.dp, if (selected) Color(p.accent) else MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(112.dp).background(Color(p.bg))) {
            Canvas(Modifier.fillMaxSize()) { drawThemePreviewArt(th) }
            Canvas(Modifier.fillMaxWidth().height(12.dp).align(Alignment.TopStart)) { drawThemeDecor(th, 0f) }
            // cartão de exemplo no formato do tema
            Row(
                Modifier.align(Alignment.Center).padding(horizontal = 12.dp).fillMaxWidth()
                    .themedCard(buildUi(Appearance(theme = th.id), p.dark), Color(p.card), Color(0xFFE57373), Color(p.line), Color(p.text))
                    .padding(start = 12.dp, end = 8.dp, top = 8.dp + (if (th.cardShape === CatCardShape) 9.dp else 0.dp), bottom = 8.dp + (if (th.cardShape === FlameCardShape) 6.dp else 0.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(28.dp).clip(avatarShapeOf(th.avatar)!!).background(Color(p.accent)))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Nome", color = Color(p.text), fontFamily = tf, fontWeight = FontWeight(th.titleWeight), fontSize = 13.sp)
                    Text("ela/dela", color = Color(p.pronoun), fontSize = 10.sp)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Color(p.card)).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(th.emoji, fontSize = 18.sp)
            Spacer(Modifier.width(6.dp))
            Text(th.name, color = Color(p.text), fontFamily = tf, fontWeight = FontWeight(th.titleWeight), fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Filled.CheckCircle, "Escolhido", tint = Color(p.accent), modifier = Modifier.size(18.dp))
        }
    }
}

/** Pedaço do app de verdade, com o visual que está sendo editado. */
@Composable
private fun PreviewBlock(d: AppData) {
    val m = d.members.firstOrNull { !it.archived }
    val name = m?.name ?: "Ana"
    val color = m?.color ?: 0xFFE57373
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            SpRow(color, Modifier.weight(1f)) {
                Avatar(name, color, m?.avatar, 48.dp)
                Spacer(Modifier.width(12.dp))
                NameAndPronouns(name, m?.pronouns ?: "ela/dela")
            }
            Spacer(Modifier.width(8.dp))
            FrontButton(color, fronting = true) {}
        }
        ThemedBox {
            Text("Mural de avisos", style = MaterialTheme.typography.titleMedium)
            Text("Assim ficam as caixas, os textos e os detalhes.", style = MaterialTheme.typography.bodyMedium)
            Text("Textos menores, como datas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {}) { Text("Botão") }
            OutlinedButton(onClick = {}) { Text("Outro") }
            FilterChip(true, {}, label = { Text("Filtro") })
        }
    }
}
