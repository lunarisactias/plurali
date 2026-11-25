package com.plurali.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Como a cor do membro aparece nos cartões. */
enum class Strip { LEFT, TOP, BORDER, GLOW, CORNER, TAPE, SPOTS, NONE }

/** Tipo de contorno dos cartões. */
enum class Border { NONE, SOLID, DASHED, DOUBLE, SKETCH, GLOW, DOTTED }

/** Cores de um tema. */
data class Palette(
    val dark: Boolean,
    val bg: Long, val card: Long, val field: Long, val pill: Long,
    val text: Long, val muted: Long, val pronoun: Long, val line: Long,
    val accent: Long,
    val frontButton: Long, val timer: Long, val bubble: Long,
    val cardTop: Long? = null,        // se tiver, o cartão ganha um degradê card → cardTop
    val pattern: List<Long>,          // cores dos desenhos de fundo
    val patternAlpha: Float,
)

/** Tudo que muda num tema além das cores: formatos, caixas, contornos, enfeites e letras. */
data class ThemeDef(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val palette: Palette,
    val cardShape: Shape,
    val small: CornerBasedShape,      // botões, chips, campos
    val medium: CornerBasedShape,     // menus, cartões do Material
    val large: CornerBasedShape,      // diálogos, botão flutuante
    val avatar: String,               // id em AVATAR_SHAPES
    val strip: Strip,
    val border: Border,
    val borderWidth: Float = 1.2f,    // dp
    val titleFont: String = "sans",   // sans | serif | cursive | mono
    val titleWeight: Int = 500,
    val headerMark: String = "",      // enfeite antes dos títulos de seção
    val headerStyle: String = "plain",// plain | underline | pill | wavy | dashed
    val decor: String = "none",       // enfeite embaixo da barra de título
    val pattern: String = "none",     // desenhos do fundo
)

private val classicDark = Palette(
    true, 0xFF1D2228, 0xFF232732, 0xFF2B2E35, 0xFF384048, 0xFFECECEE, 0xFFA0A3AB, 0xFF7C84A6, 0xFF3B3E47,
    0xFFD6AC5E, 0xFF22222C, 0xFF12131A, 0xFF384048, pattern = emptyList(), patternAlpha = 0f,
)
private val classicLight = Palette(
    false, 0xFFF1F2F5, 0xFFFFFFFF, 0xFFE9EBF0, 0xFFE2E5EB, 0xFF1D2228, 0xFF5B606B, 0xFF4E5680, 0xFFC5C9D2,
    0xFFD6AC5E, 0xFFF7F7FA, 0xFFE6E8EE, 0xFFDDE3F3, pattern = emptyList(), patternAlpha = 0f,
)

private fun classic(dark: Boolean) = ThemeDef(
    "classic", "Clássico", "🌗", "O visual do SimplyPlural, escuro ou claro.",
    if (dark) classicDark else classicLight,
    cardShape = RoundedCornerShape(14.dp), small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp),
    avatar = "rounded", strip = Strip.LEFT, border = Border.NONE,
)

val THEMES: List<ThemeDef> = listOf(
    classic(true),
    ThemeDef(
        "cat", "Gatos", "🐈‍⬛", "Noite, lua e patinhas. Cartões com orelhinhas e avatares de gato.",
        Palette(
            true, 0xFF121116, 0xFF1C1A22, 0xFF26232E, 0xFF332F3D, 0xFFEDEAF2, 0xFF9E99A8, 0xFFC9D86A, 0xFF34303D,
            0xFFE8C547, 0xFF1A181F, 0xFF0C0B0F, 0xFF332F3D,
            pattern = listOf(0xFF8C86A0, 0xFFE8C547, 0xFFEDEAF2), patternAlpha = 0.13f,
        ),
        cardShape = CatCardShape, small = RoundedCornerShape(50), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(26.dp),
        avatar = "cat", strip = Strip.GLOW, border = Border.SOLID, borderWidth = 1f,
        titleFont = "sans", titleWeight = 600, headerMark = "🐾", headerStyle = "pill", decor = "paws", pattern = "cat",
    ),
    ThemeDef(
        "garden", "Jardim", "🌷", "Folhas, flores e cartões em formato de folha.",
        Palette(
            false, 0xFFEEF4E4, 0xFFFBFDF7, 0xFFE3EDD6, 0xFFD4E4C3, 0xFF22311C, 0xFF5E6E55, 0xFF8A5A9E, 0xFFC9D8BA,
            0xFF4E8F45, 0xFFF6FAF0, 0xFFE3EDD6, 0xFFDCEBCB, cardTop = 0xFFF1F8E9,
            pattern = listOf(0xFF6BA35E, 0xFFE58FB0, 0xFFF2C94C, 0xFF9C7BC4), patternAlpha = 0.24f,
        ),
        cardShape = RoundedCornerShape(topStart = 26.dp, topEnd = 6.dp, bottomEnd = 26.dp, bottomStart = 6.dp),
        small = RoundedCornerShape(topStart = 14.dp, topEnd = 4.dp, bottomEnd = 14.dp, bottomStart = 4.dp),
        medium = RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomEnd = 20.dp, bottomStart = 6.dp),
        large = RoundedCornerShape(topStart = 32.dp, topEnd = 8.dp, bottomEnd = 32.dp, bottomStart = 8.dp),
        avatar = "leaf", strip = Strip.TOP, border = Border.SOLID, borderWidth = 1f,
        titleFont = "serif", titleWeight = 600, headerMark = "🌿", headerStyle = "wavy", decor = "vine", pattern = "garden",
    ),
    ThemeDef(
        "spider", "Homem-Aranha", "🕷️", "Vermelho e azul, teias no fundo e cantos cortados.",
        Palette(
            true, 0xFF0D1330, 0xFF151D45, 0xFF1D2758, 0xFF283471, 0xFFF1F3FF, 0xFFA3AACC, 0xFFFF8A8A, 0xFF2A3570,
            0xFFE23636, 0xFF121A3E, 0xFF080C22, 0xFF5C1C2A, cardTop = 0xFF1B2452,
            pattern = listOf(0xFFC8D0F0, 0xFFE23636, 0xFF05081A), patternAlpha = 0.16f,
        ),
        cardShape = CutCornerShape(topStart = 0.dp, topEnd = 16.dp, bottomEnd = 0.dp, bottomStart = 16.dp),
        small = CutCornerShape(6.dp), medium = CutCornerShape(10.dp), large = CutCornerShape(topStart = 0.dp, topEnd = 22.dp, bottomEnd = 0.dp, bottomStart = 22.dp),
        avatar = "hexagon", strip = Strip.LEFT, border = Border.SOLID, borderWidth = 1.4f,
        titleFont = "sans", titleWeight = 800, headerMark = "🕸️", headerStyle = "underline", decor = "web", pattern = "spider",
    ),
    ThemeDef(
        "bunny", "Coelhos", "🐰", "Tons pastel, tudo bem redondinho e avatares com orelhas.",
        Palette(
            false, 0xFFFFF0F4, 0xFFFFFFFF, 0xFFFCE1E9, 0xFFF8D0DD, 0xFF4A2B37, 0xFF8F6B78, 0xFF9B7BC8, 0xFFF2C9D6,
            0xFFEC7FA9, 0xFFFFF8FA, 0xFFFCE1E9, 0xFFFADBE6, cardTop = 0xFFFFF7FA,
            pattern = listOf(0xFFD9B8C4, 0xFFF5A05A, 0xFF7CC08A, 0xFFEC7FA9), patternAlpha = 0.30f,
        ),
        cardShape = RoundedCornerShape(30.dp), small = RoundedCornerShape(50), medium = RoundedCornerShape(24.dp), large = RoundedCornerShape(36.dp),
        avatar = "bunny", strip = Strip.CORNER, border = Border.DOTTED, borderWidth = 2f,
        titleFont = "cursive", titleWeight = 700, headerMark = "🥕", headerStyle = "pill", decor = "hearts", pattern = "bunny",
    ),
    ThemeDef(
        "inferno", "Inferno", "🔥", "Brasas subindo, chamas no rodapé dos cartões e contorno em brasa.",
        Palette(
            true, 0xFF140606, 0xFF220B0B, 0xFF2E1010, 0xFF431616, 0xFFFBE6DA, 0xFFC79A8A, 0xFFFFB067, 0xFF4A1A14,
            0xFFFF5A1F, 0xFF1C0808, 0xFF0B0303, 0xFF5A1A12, cardTop = 0xFF2E0E0A,
            pattern = listOf(0xFFFF5A1F, 0xFFFFA726, 0xFFFFD54F, 0xFFB71C1C), patternAlpha = 0.20f,
        ),
        cardShape = FlameCardShape, small = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp, topEnd = 0.dp, bottomStart = 0.dp),
        medium = CutCornerShape(10.dp), large = CutCornerShape(14.dp),
        avatar = "shield", strip = Strip.GLOW, border = Border.GLOW, borderWidth = 1.2f,
        titleFont = "serif", titleWeight = 800, headerMark = "🔥", headerStyle = "underline", decor = "flames", pattern = "inferno",
    ),
    ThemeDef(
        "fairy", "Fadas", "🧚", "Brilhos, cogumelos e cartões em degradê com bordas suaves.",
        Palette(
            false, 0xFFF6F0FF, 0xFFFFFFFF, 0xFFEDE3FB, 0xFFE1D2F7, 0xFF33264A, 0xFF7A6B92, 0xFF3F9A9C, 0xFFDCCDF0,
            0xFFA66CD9, 0xFFFBF8FF, 0xFFEDE3FB, 0xFFE6D9FA, cardTop = 0xFFFCEFFA,
            pattern = listOf(0xFFB98CE6, 0xFFF7A8D0, 0xFF7FCFCF, 0xFFF2C94C), patternAlpha = 0.32f,
        ),
        cardShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 8.dp, bottomStart = 24.dp),
        small = RoundedCornerShape(50), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(30.dp),
        avatar = "flower", strip = Strip.BORDER, border = Border.GLOW, borderWidth = 1f,
        titleFont = "cursive", titleWeight = 700, headerMark = "✨", headerStyle = "wavy", decor = "sparkles", pattern = "fairy",
    ),
    ThemeDef(
        "geo", "Exploração", "🧭", "Papel de mapa, bússola, rotas e cartões como bilhetes de viagem.",
        Palette(
            false, 0xFFF2E8D0, 0xFFFBF6EA, 0xFFE9DDBF, 0xFFDCCBA3, 0xFF3A2E1C, 0xFF7A6A4F, 0xFF1F6E8C, 0xFFCDB98F,
            0xFF1F6E8C, 0xFFF8F1E1, 0xFFE9DDBF, 0xFFDDE8E2,
            pattern = listOf(0xFF8B6B3E, 0xFF1F6E8C, 0xFFB5523B), patternAlpha = 0.22f,
        ),
        cardShape = TicketShape, small = RoundedCornerShape(3.dp), medium = RoundedCornerShape(4.dp), large = RoundedCornerShape(6.dp),
        avatar = "stamp", strip = Strip.LEFT, border = Border.DOUBLE, borderWidth = 1f,
        titleFont = "serif", titleWeight = 700, headerMark = "🗺️", headerStyle = "dashed", decor = "scale", pattern = "geo",
    ),
    ThemeDef(
        "insects", "Insetos", "🐞", "Joaninhas, abelhas e borboletas. Formas de favo de mel.",
        Palette(
            false, 0xFFF3F1E2, 0xFFFFFEF7, 0xFFE7E5CF, 0xFFD9D8B8, 0xFF2A2A1B, 0xFF6E6C55, 0xFF5F7F2E, 0xFFD6D3B6,
            0xFFD2452E, 0xFFFBFAF0, 0xFFE7E5CF, 0xFFF3DED6,
            pattern = listOf(0xFFD2452E, 0xFF2B2B2B, 0xFFF2B632, 0xFF7FA650, 0xFF8FB8DE), patternAlpha = 0.26f,
        ),
        cardShape = CutCornerShape(12.dp), small = CutCornerShape(30), medium = CutCornerShape(8.dp), large = CutCornerShape(16.dp),
        avatar = "hexagon", strip = Strip.TOP, border = Border.DASHED, borderWidth = 1.2f,
        titleFont = "sans", titleWeight = 700, headerMark = "🐝", headerStyle = "dashed", decor = "ants", pattern = "insects",
    ),
    ThemeDef(
        "artist", "Desenho", "✏️", "Folha de caderno, lápis, manchas de tinta e tudo com cara de rabisco.",
        Palette(
            false, 0xFFFAF8F2, 0xFFFFFFFF, 0xFFF0EDE4, 0xFFE5E1D5, 0xFF222222, 0xFF6B6B6B, 0xFF2F4BD8, 0xFFDDD8CB,
            0xFF2F4BD8, 0xFFFFFFFF, 0xFFF0EDE4, 0xFFE3E8FF,
            pattern = listOf(0xFF9DB5D9, 0xFFE57373, 0xFF555555, 0xFFF2B632, 0xFF4FC3F7, 0xFFBA68C8, 0xFF81C784), patternAlpha = 0.34f,
        ),
        cardShape = SketchShape, small = RoundedCornerShape(topStart = 10.dp, topEnd = 3.dp, bottomEnd = 9.dp, bottomStart = 4.dp),
        medium = RoundedCornerShape(topStart = 4.dp, topEnd = 12.dp, bottomEnd = 5.dp, bottomStart = 11.dp),
        large = RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomEnd = 8.dp, bottomStart = 16.dp),
        avatar = "sketch", strip = Strip.TAPE, border = Border.SKETCH, borderWidth = 1.3f,
        titleFont = "cursive", titleWeight = 700, headerMark = "✏️", headerStyle = "wavy", decor = "pencil", pattern = "artist",
    ),
    ThemeDef(
        "birds", "Pássaros", "🐦", "Céu claro, bandos voando, penas e passarinhos pousados no fio. Fotos em formato de ovo.",
        Palette(
            false, 0xFFEAF4FB, 0xFFFFFFFF, 0xFFDCEBF5, 0xFFC9E0EF, 0xFF1F3344, 0xFF5D7385, 0xFFC0692E, 0xFFC7DCEA,
            0xFF2E86C1, 0xFFF7FBFE, 0xFFDCEBF5, 0xFFD6EAF8, cardTop = 0xFFF4FAFE,
            pattern = listOf(0xFF34495E, 0xFFE67E22, 0xFF5DADE2, 0xFFF4D03F, 0xFFD35400), patternAlpha = 0.22f,
        ),
        cardShape = RoundedCornerShape(20.dp), small = RoundedCornerShape(50), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp),
        avatar = "egg", strip = Strip.TOP, border = Border.SOLID, borderWidth = 1f,
        titleFont = "serif", titleWeight = 700, headerMark = "🪶", headerStyle = "underline", decor = "wire", pattern = "birds",
    ),
    ThemeDef(
        "ladybug", "Ladybug", "🐞", "Vermelho com bolinhas pretas, ioiôs e a Torre Eiffel numa noite de Paris.",
        Palette(
            true, 0xFF120E1C, 0xFF1F1729, 0xFF2A1F36, 0xFF3B2540, 0xFFF6EEF4, 0xFFB3A3B8, 0xFFFF80AB, 0xFF3A2C45,
            0xFFE53935, 0xFF1A1323, 0xFF0A070F, 0xFF5A1E2A, cardTop = 0xFF27182E,
            pattern = listOf(0xFFE53935, 0xFF000000, 0xFF8E7AA8, 0xFFFFE082), patternAlpha = 0.22f,
        ),
        cardShape = RoundedCornerShape(22.dp), small = RoundedCornerShape(50), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(26.dp),
        avatar = "circle", strip = Strip.SPOTS, border = Border.SOLID, borderWidth = 1.2f,
        titleFont = "sans", titleWeight = 800, headerMark = "🐞", headerStyle = "pill", decor = "dots", pattern = "ladybug",
    ),
    ThemeDef(
        "wind", "Vento", "🌬️", "Leve e arejado: rajadas de vento, folhas voando e cartões de vidro.",
        Palette(
            false, 0xFFEAF5EC, 0xD9FFFFFF, 0xFFDCEEE0, 0xFFC6E4CD, 0xFF20362A, 0xFF5B7463, 0xFF2E7D6B, 0xFFBBD9C3,
            0xFF3E9B5A, 0xEEF6FBF7, 0xFFDCEEE0, 0xFFD1ECD8,
            pattern = listOf(0xFF6FA882, 0xFF7CB342, 0xFFF2A65A), patternAlpha = 0.30f,
        ),
        cardShape = RoundedCornerShape(26.dp), small = RoundedCornerShape(50), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp),
        avatar = "circle", strip = Strip.GLOW, border = Border.SOLID, borderWidth = 0.8f,
        titleFont = "sans", titleWeight = 400, headerMark = "🍃", headerStyle = "wavy", decor = "breeze", pattern = "wind",
    ),
)

/** Tema pelo id; o clássico muda conforme o modo claro/escuro. */
fun themeOf(id: String, dark: Boolean): ThemeDef =
    if (id == "classic") classic(dark) else THEMES.find { it.id == id } ?: classic(dark)

/** Formatos de cartão escolhíveis (sobrepõem o do tema). */
val CARD_SHAPES: List<Pair<String, String>> = listOf(
    "theme" to "Do tema", "round" to "Arredondado", "pill" to "Bem redondo", "square" to "Reto", "cut" to "Cortado",
    "leaf" to "Folha", "ticket" to "Bilhete", "sketch" to "Rabisco", "cat" to "Orelhinhas",
)

fun cardShapeOf(id: String, theme: ThemeDef): Shape = when (id) {
    "round" -> RoundedCornerShape(14.dp)
    "pill" -> RoundedCornerShape(30.dp)
    "square" -> RoundedCornerShape(3.dp)
    "cut" -> CutCornerShape(12.dp)
    "leaf" -> RoundedCornerShape(topStart = 26.dp, topEnd = 6.dp, bottomEnd = 26.dp, bottomStart = 6.dp)
    "ticket" -> TicketShape
    "sketch" -> SketchShape
    "cat" -> CatCardShape
    else -> theme.cardShape
}

