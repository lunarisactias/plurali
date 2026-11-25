package com.plurali.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
 * Três páginas: início (perfil + prévia + dois botões), Temas e Personalizar.
 */
@Composable
fun AppearanceScreen(nav: NavHostController) {
    val d by Store.data.collectAsState()
    val who = LocalWho.current
    val profiles: List<Pair<String, String>> = buildList {
        if (who.isAdmin) {
            add("system" to "Sistema")
            d.members.filter { !it.archived }.sortedBy { it.name.lowercase() }.forEach { add(it.id to it.name) }
        } else who.meId?.let { id -> add(id to (d.members.find { it.id == id }?.name ?: "Eu")) }
    }
    var key by remember { mutableStateOf(appearanceKey(Session.who).takeIf { k -> profiles.any { it.first == k } } ?: profiles.firstOrNull()?.first ?: "system") }
    var page by rememberSaveable { mutableStateOf("home") }
    val own = d.appearance[key]
    val a = own ?: defaultAppearance()
    fun set(n: Appearance) = Store.setAppearance(key, n)
    val profileName = profiles.find { it.first == key }?.second ?: ""
    BackHandler(enabled = page != "home") { page = "home" }

    PluraliTheme(a) {
        val title = when (page) { "themes" -> "Temas"; "custom" -> "Personalizar"; else -> "Aparência" }
        ScreenScaffold(title, onBack = { if (page != "home") page = "home" else nav.popBackStack() }) { pad ->
            Column(
                Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (page) {
                    "themes" -> ThemesPage(a, profileName, profiles.size > 1, ::set)
                    "custom" -> CustomPage(a, key, profileName, profiles.size > 1, ::set)
                    else -> HomePage(d, key, profiles, own != null, onKey = { key = it }, open = { page = it })
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

// ---------------- início ----------------

@Composable
private fun HomePage(
    d: AppData, key: String, profiles: List<Pair<String, String>>, hasOwn: Boolean,
    onKey: (String) -> Unit, open: (String) -> Unit,
) {
    val ui = LocalUi.current
    if (profiles.size > 1) {
        Text("De quem é este visual?", style = MaterialTheme.typography.titleMedium)
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
            else -> "Este perfil usa o tema padrão (como um alter novo). Qualquer mudança cria um visual só dele."
        },
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (key != "system" && hasOwn) TextButton(onClick = { Store.resetAppearance(key) }) {
        Icon(Icons.Filled.Restore, null); Spacer(Modifier.width(6.dp)); Text("Voltar ao tema padrão")
    }

    SectionHeader("Prévia")
    PreviewBlock(d)

    Spacer(Modifier.height(6.dp))
    BigChoice(Icons.Filled.Palette, "Temas", "Agora: ${ui.theme.emoji} ${ui.theme.name}. Escolha entre ${THEMES.size} temas prontos.") { open("themes") }
    BigChoice(Icons.Filled.Tune, "Personalizar", "Cor de destaque, formato dos cartões e das fotos, contorno, letras, fundo e enfeites.") { open("custom") }
}

/** Botão grande que abre outra página. */
@Composable
private fun BigChoice(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().themed().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, null)
    }
}

@Composable
private fun EditingNote(profileName: String, show: Boolean) {
    if (show) Text("Editando: $profileName", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

// ---------------- temas ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemesPage(a: Appearance, profileName: String, showName: Boolean, set: (Appearance) -> Unit) {
    EditingNote(profileName, showName)
    Text(
        "Cada tema muda cores, formatos, contornos, enfeites e o desenho do fundo. Depois dá para ajustar tudo em Personalizar.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val cols = 2
    THEMES.chunked(cols).forEach { row ->
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { th ->
                ThemeTile(th, a.theme == th.id, Modifier.weight(1f)) { set(a.copy(theme = th.id, accent = null)) }
            }
            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    if (a.theme == "classic") {
        SectionHeader("Modo do Clássico")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("dark" to "Escuro", "light" to "Claro", "system" to "Seguir o celular").forEach { (k, l) ->
                FilterChip(a.mode == k, { set(a.copy(mode = k)) }, label = { Text(l) },
                    leadingIcon = { Icon(when (k) { "dark" -> Icons.Filled.DarkMode; "light" -> Icons.Filled.LightMode; else -> Icons.Filled.PhoneAndroid }, null, Modifier.size(16.dp)) })
            }
        }
    }
}

// ---------------- personalizar ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomPage(a: Appearance, key: String, profileName: String, showName: Boolean, set: (Appearance) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ui = LocalUi.current
    val dark = a.isDark()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val pickWall = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val p = withContext(Dispatchers.IO) { saveImage(ctx, uri, "wallpapers", key, aspect = null, maxSide = 1600) }
            if (p != null) set(a.copy(wallpaper = p, pattern = if (a.pattern > 0.6f) 0.6f else a.pattern))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EditingNote(profileName, showName)
        Text("Tema atual: ${ui.theme.emoji} ${ui.theme.name}. “Do tema” usa o que o tema escolheu.", style = MaterialTheme.typography.bodySmall, color = muted)

        // ---------- cor ----------
        SectionHeader("Cor de destaque")
        Text("Botões, ícones e detalhes.", style = MaterialTheme.typography.bodySmall, color = muted)
        FilterChip(a.accent == null, { set(a.copy(accent = null)) }, label = { Text("Cor do tema") },
            leadingIcon = { Box(Modifier.size(16.dp).clip(RoundedCornerShape(50)).background(Color(ui.theme.palette.accent))) })
        ColorPicker(a.accent ?: ui.theme.palette.accent) { set(a.copy(accent = it)) }

        // ---------- formatos ----------
        SectionHeader("Formato dos cartões")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CARD_SHAPES.forEach { (k, l) ->
                MiniCardOption(l, buildUi(a.copy(cardShape = k), dark), a.cardShape == k) { set(a.copy(cardShape = k)) }
            }
        }
        SectionHeader("Formato das fotos")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShapeOption("Do tema", avatarShapeOf(ui.theme.avatar)!!, a.avatarShape == "theme") { set(a.copy(avatarShape = "theme")) }
            AVATAR_SHAPES.forEach { (k, l, s) -> ShapeOption(l, s, a.avatarShape == k) { set(a.copy(avatarShape = k)) } }
        }
        SectionHeader("Cor de cada membro nos cartões")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("theme" to "Do tema", "left" to "Faixa lateral", "top" to "Faixa no topo", "border" to "Borda", "glow" to "Brilho",
                "corner" to "Coração", "tape" to "Fita adesiva", "spots" to "Bolinhas", "none" to "Sem cor").forEach { (k, l) ->
                MiniCardOption(l, buildUi(a.copy(strip = k), dark), a.strip == k) { set(a.copy(strip = k)) }
            }
        }
        SectionHeader("Contorno")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("theme" to "Do tema", "none" to "Sem", "solid" to "Linha", "dashed" to "Tracejado", "dotted" to "Pontilhado",
                "double" to "Duplo", "sketch" to "Lápis", "glow" to "Brilho").forEach { (k, l) ->
                MiniCardOption(l, buildUi(a.copy(border = k, strip = "none"), dark), a.border == k) { set(a.copy(border = k)) }
            }
        }

        // ---------- letras ----------
        SectionHeader("Letras")
        Text("Títulos", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FontOption("Do tema", fontOf(ui.theme.titleFont), a.titleFont == "theme") { set(a.copy(titleFont = "theme")) }
            FONTS.forEach { (k, l) -> FontOption(l, fontOf(k), a.titleFont == k) { set(a.copy(titleFont = k)) } }
        }
        Text("Texto", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FONTS.forEach { (k, l) -> FontOption(l, fontOf(k), a.bodyFont == k) { set(a.copy(bodyFont = k)) } }
        }
        Text("Tamanho", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.9f to "Pequeno", 1f to "Normal", 1.15f to "Grande", 1.3f to "Maior", 1.5f to "Enorme").forEach { (v, l) ->
                OptionBox(l, a.textScale == v, onClick = { set(a.copy(textScale = v)) }) {
                    Text("Aa", fontSize = (16 * v / a.textScale).sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ---------- fundo ----------
        SectionHeader("Fundo")
        if (ui.theme.pattern == "none") Text("O tema Clássico não tem desenhos de fundo.", style = MaterialTheme.typography.bodySmall, color = muted)
        else {
            Text("Desenhos do tema", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0f to "Sem", 0.5f to "Suaves", 1f to "Normais", 1.6f to "Fortes").forEach { (v, l) ->
                    OptionBox(l, kotlin.math.abs(a.pattern - v) < 0.05f, onClick = { set(a.copy(pattern = v)) }) {
                        val th = ui.theme
                        Canvas(Modifier.size(70.dp, 46.dp).clip(RoundedCornerShape(6.dp)).background(Color(th.palette.bg))) {
                            scale(0.25f, Offset.Zero) { inset(0f, 0f, -size.width * 3f, -size.height * 3f) { drawThemePattern(th, v * 2.2f, 0f) } }
                        }
                    }
                }
            }
            ToggleLine("Animar o fundo", "Brasas sobem, brilhos piscam, aranhas balançam… Gasta um pouco mais de bateria.", a.animate) { set(a.copy(animate = it)) }
        }
        Text("Imagem de fundo", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { pickWall.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Icon(Icons.Filled.Wallpaper, null); Spacer(Modifier.width(6.dp)); Text(if (a.wallpaper == null) "Escolher imagem" else "Trocar imagem")
            }
            if (a.wallpaper != null) TextButton(onClick = { set(a.copy(wallpaper = null)) }) { Text("Remover") }
        }
        if (a.wallpaper != null) {
            Text("Escurecer a imagem (para ler melhor)", style = MaterialTheme.typography.labelLarge)
            Slider(a.wallpaperDim, { set(a.copy(wallpaperDim = it)) }, valueRange = 0f..0.95f)
        }

        // ---------- enfeites ----------
        SectionHeader("Enfeites")
        ToggleLine("Enfeite embaixo do título", "A faixa logo abaixo da barra de cima. Exemplo:", a.decor) { set(a.copy(decor = it)) }
        if (ui.theme.decor != "none") Canvas(
            Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (a.decor) 1f else 0.4f)),
        ) { if (a.decor) drawThemeDecor(ui.theme, 0f) }
        else Text("O tema Clássico não tem enfeite.", style = MaterialTheme.typography.bodySmall, color = muted)
        ToggleLine("Símbolo nos títulos de seção", if (ui.theme.headerMark.isBlank()) "O tema Clássico não tem símbolo." else "Exemplo:", a.headerMarks) { set(a.copy(headerMarks = it)) }
        if (ui.theme.headerMark.isNotBlank()) SectionHeader("Título de exemplo")
        if (key == "system") ToggleLine(
            "Usar o visual de quem está no front",
            "Na conta Sistema (ou com os logins desligados), o app troca para o visual de quem está no front, se a pessoa tiver um.",
            a.followFront,
        ) { set(a.copy(followFront = it)) }
    }
}

/** Caixinha de opção: exemplo desenhado em cima, nome embaixo. */
@Composable
private fun OptionBox(label: String, selected: Boolean, width: Dp = 84.dp, onClick: () -> Unit, sample: @Composable () -> Unit) {
    Column(
        Modifier.width(width).clip(RoundedCornerShape(12.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f))
            .clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.height(50.dp), contentAlignment = Alignment.Center) { sample() }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** Mini cartão de verdade com o estilo da opção (contorno, cor do membro, formato). */
@Composable
private fun MiniCardOption(label: String, style: UiStyle, selected: Boolean, onClick: () -> Unit) {
    OptionBox(label, selected, width = 104.dp, onClick = onClick) {
        Row(
            Modifier.width(86.dp)
                .themedCard(style, MaterialTheme.colorScheme.surfaceContainer, Color(0xFFE57373), MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.onSurface)
                .padding(
                    start = 10.dp, end = 6.dp,
                    top = 6.dp + style.cardTopExtra + (if (style.strip == Strip.SPOTS) 6.dp else if (style.strip == Strip.TOP) 2.dp else 0.dp),
                    bottom = 6.dp + style.cardBottomExtra,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(16.dp).clip(style.avatarShape).background(Color(0xFFE57373)))
            Spacer(Modifier.width(5.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.size(34.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)))
                Box(Modifier.size(22.dp, 3.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.tertiary))
            }
        }
    }
}

@Composable
private fun FontOption(label: String, family: FontFamily, selected: Boolean, onClick: () -> Unit) {
    OptionBox(label, selected, width = 96.dp, onClick = onClick) { Text("Abc", fontFamily = family, fontSize = 22.sp) }
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
    OptionBox(label, selected, width = if (wide) 104.dp else 96.dp, onClick = onClick) {
        Canvas(Modifier.size(if (wide) 76.dp else 42.dp, 42.dp)) {
            drawOutline(shape.createOutline(size, layoutDirection, this), accent.copy(alpha = 0.35f))
            drawOutline(shape.createOutline(size, layoutDirection, this), accent, style = Stroke(2 * density))
        }
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
