package com.plurali.app.data

import org.json.JSONObject

/** Aparência de um perfil: chave "system", "guest" ou o id do membro. */
data class Appearance(
    val theme: String = "classic",
    val mode: String = "dark",           // só no tema clássico: dark | light | system
    val accent: Long? = null,            // null = cor do tema
    val textScale: Float = 1f,
    val titleFont: String = "theme",     // theme | sans | serif | cursive | mono
    val bodyFont: String = "sans",
    val cardShape: String = "theme",     // theme ou um id de CARD_SHAPES
    val avatarShape: String = "theme",   // theme ou um id de AVATAR_SHAPES
    val strip: String = "theme",         // cor do membro nos cartões: theme | left | top | border | glow | corner | none
    val border: String = "theme",        // contorno dos cartões: theme | none | solid | dashed | dotted | double | sketch | glow
    val decor: Boolean = true,           // enfeite embaixo da barra de título
    val headerMarks: Boolean = true,     // enfeite nos títulos de seção
    val pattern: Float = 1f,             // intensidade dos desenhos de fundo (0 = sem)
    val animate: Boolean = false,
    val wallpaper: String? = null,       // imagem de fundo (wallpapers/...)
    val wallpaperDim: Float = 0.55f,
    val followFront: Boolean = false,
    // cores próprias (null = cor do tema)
    val bg: Long? = null,                // fundo
    val bg2: Long? = null,               // segunda cor do fundo (degradê)
    val card: Long? = null,              // cartões e caixas
    val bars: Long? = null,              // barra de cima e de baixo
    val text: Long? = null,              // texto (null = automático, para dar leitura)
    val pronoun: Long? = null,           // pronomes e detalhes
    val chip: Long? = null,              // seleção, etiquetas e filtros
    val patternColor: Long? = null,      // desenhos do fundo e enfeites
    val memberTint: Float = 0f,          // quanto o cartão de cada membro pega a cor dele (0 = nada)    // (só no perfil Sistema) usar a aparência de quem está no front
)

/** Conversão da aparência para JSON (usada no backup e enviada ao companion). */
object AppearanceJson {
    private fun JSONObject.s(k: String) = if (has(k) && !isNull(k)) getString(k) else ""
    private fun JSONObject.sN(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null
    private fun JSONObject.lN(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null
    private fun JSONObject.putN(k: String, v: Any?): JSONObject = put(k, v ?: JSONObject.NULL)

    fun encode(a: Appearance): JSONObject = JSONObject().put("theme", a.theme).put("mode", a.mode).putN("accent", a.accent)
        .put("textScale", a.textScale.toDouble()).put("titleFont", a.titleFont).put("bodyFont", a.bodyFont).put("cardShape", a.cardShape)
        .put("avatarShape", a.avatarShape).put("strip", a.strip).put("border", a.border).put("decor", a.decor).put("headerMarks", a.headerMarks)
        .put("pattern", a.pattern.toDouble()).put("animate", a.animate).putN("wallpaper", a.wallpaper)
        .put("wallpaperDim", a.wallpaperDim.toDouble()).put("followFront", a.followFront)
        .putN("bg", a.bg).putN("bg2", a.bg2).putN("card", a.card).putN("bars", a.bars).putN("text", a.text)
        .putN("pronoun", a.pronoun).putN("chip", a.chip).putN("patternColor", a.patternColor).put("memberTint", a.memberTint.toDouble())

    fun decode(o: JSONObject): Appearance {
        val d = Appearance()
        return Appearance(
            theme = o.s("theme").ifBlank { d.theme }, mode = o.s("mode").ifBlank { d.mode }, accent = o.lN("accent"),
            textScale = o.optDouble("textScale", 1.0).toFloat(), titleFont = o.s("titleFont").ifBlank { d.titleFont },
            bodyFont = o.s("bodyFont").ifBlank { d.bodyFont }, cardShape = o.s("cardShape").ifBlank { d.cardShape },
            avatarShape = o.s("avatarShape").ifBlank { d.avatarShape }, strip = o.s("strip").ifBlank { d.strip },
            border = o.s("border").ifBlank { d.border }, decor = o.optBoolean("decor", true), headerMarks = o.optBoolean("headerMarks", true),
            pattern = o.optDouble("pattern", 1.0).toFloat(),
            animate = o.optBoolean("animate"), wallpaper = o.sN("wallpaper"), wallpaperDim = o.optDouble("wallpaperDim", 0.55).toFloat(),
            followFront = o.optBoolean("followFront"),
            bg = o.lN("bg"), bg2 = o.lN("bg2"), card = o.lN("card"), bars = o.lN("bars"), text = o.lN("text"),
            pronoun = o.lN("pronoun"), chip = o.lN("chip"), patternColor = o.lN("patternColor"),
            memberTint = o.optDouble("memberTint", 0.0).toFloat(),
        )
    }
}
