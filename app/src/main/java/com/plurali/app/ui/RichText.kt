package com.plurali.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/*
 * Formatação simples por marcas, que ficam guardadas no próprio texto:
 *   [b]negrito[/b]  [i]itálico[/i]  [u]sublinhado[/u]  [s]riscado[/s]
 *   [g]grande[/g]   [p]pequeno[/p]
 *   [c=#E53935]cor da letra[/c]     [m=#FFF176]marca-texto[/m]
 * Ninguém precisa digitar as marcas: a barra do editor coloca em volta do trecho selecionado.
 */
object Rich {
    private val TAG = Regex("""\[(/?)(b|i|u|s|g|p|c|m)(?:=#([0-9A-Fa-f]{6}))?\]""")

    /** Texto sem as marcas (para notificações, companion e buscas). */
    fun strip(s: String): String = s.replace(TAG, "")

    fun hasTags(s: String) = TAG.containsMatchIn(s)

    private fun styleOf(tag: String, hex: String?): SpanStyle? = when (tag) {
        "b" -> SpanStyle(fontWeight = FontWeight.Bold)
        "i" -> SpanStyle(fontStyle = FontStyle.Italic)
        "u" -> SpanStyle(textDecoration = TextDecoration.Underline)
        "s" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        "g" -> SpanStyle(fontSize = 1.35.em)
        "p" -> SpanStyle(fontSize = 0.82.em)
        "c" -> hex?.let { SpanStyle(color = Color(0xFF000000 or it.toLong(16))) }
        "m" -> hex?.let { SpanStyle(background = Color(0xFF000000 or it.toLong(16)).copy(alpha = 0.45f)) }
        else -> null
    }

    /** Transforma o texto com marcas em texto formatado. Marcas sem par são toleradas. */
    /** keepTags: mantém as marcas no texto (apagadinhas), para o campo de edição já mostrar a formatação. */
    fun annotate(s: String, keepTags: Boolean = false, tagStyle: SpanStyle = SpanStyle()): AnnotatedString = buildAnnotatedString {
        data class Open(val tag: String, val style: SpanStyle, var idx: Int)
        val stack = mutableListOf<Open>()
        var last = 0
        for (m in TAG.findAll(s)) {
            append(s.substring(last, m.range.first))
            last = m.range.last + 1
            val closing = m.groupValues[1] == "/"
            val tag = m.groupValues[2]
            if (!closing) {
                if (keepTags) withStyle(tagStyle) { append(m.value) }
                val st = styleOf(tag, m.groupValues[3].ifBlank { null }) ?: continue
                stack.add(Open(tag, st, pushStyle(st)))
            } else {
                val pos = stack.indexOfLast { it.tag == tag }
                if (pos < 0) { if (keepTags) withStyle(tagStyle) { append(m.value) }; continue }
                // tira esta e as de cima, e recoloca as de cima (as marcas podem fechar fora de ordem)
                pop(stack[pos].idx)
                val above = stack.subList(pos + 1, stack.size).toList()
                stack.subList(pos, stack.size).clear()
                if (keepTags) withStyle(tagStyle) { append(m.value) }
                above.forEach { o -> o.idx = pushStyle(o.style); stack.add(o) }
            }
        }
        append(s.substring(last))
    }
}

/** Texto com formatação. */
@Composable
fun RichText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, color: Color = Color.Unspecified, textAlign: TextAlign? = null) {
    val annotated = remember(text) { Rich.annotate(text) }
    Text(annotated, modifier = modifier, style = style, color = color, textAlign = textAlign)
}

private val TEXT_COLORS = listOf(
    0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF3949AB, 0xFF1E88E5, 0xFF00897B, 0xFF43A047, 0xFFF9A825,
    0xFFFB8C00, 0xFF6D4C41, 0xFF757575, 0xFF000000, 0xFFFFFFFF,
)
private val HIGHLIGHTS = listOf(0xFFFFF176, 0xFFA5D6A7, 0xFF81D4FA, 0xFFF48FB1, 0xFFCE93D8, 0xFFFFCC80)

/**
 * Campo de texto com barra de formatação (negrito, itálico, sublinhado, riscado, cor, marca-texto, tamanho)
 * e prévia ao vivo. Guarda o texto com as marcas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RichTextField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, minLines: Int = 3) {
    var tv by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (tv.text != value) tv = tv.copy(text = value, selection = TextRange(value.length.coerceAtMost(tv.selection.end)))
    var colorMenu by remember { mutableStateOf(false) }
    var markMenu by remember { mutableStateOf(false) }

    fun wrap(open: String, close: String) {
        val s = tv.selection.min; val e = tv.selection.max
        val t = tv.text
        val nt = t.substring(0, s) + open + t.substring(s, e) + close + t.substring(e)
        val sel = if (s == e) TextRange(s + open.length) else TextRange(s + open.length, e + open.length)
        tv = TextFieldValue(nt, sel); onChange(nt)
    }
    fun clearFormat() {
        val s = tv.selection.min; val e = tv.selection.max
        val t = tv.text
        val nt = if (s == e) Rich.strip(t) else t.substring(0, s) + Rich.strip(t.substring(s, e)) + t.substring(e)
        tv = TextFieldValue(nt, TextRange(nt.length.coerceAtMost(s))); onChange(nt)
    }
    fun hex(c: Long) = "%06X".format(c and 0xFFFFFF)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // barra de formatação
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FmtButton(Icons.Filled.FormatBold, "Negrito") { wrap("[b]", "[/b]") }
            FmtButton(Icons.Filled.FormatItalic, "Itálico") { wrap("[i]", "[/i]") }
            FmtButton(Icons.Filled.FormatUnderlined, "Sublinhado") { wrap("[u]", "[/u]") }
            FmtButton(Icons.Filled.FormatStrikethrough, "Riscado") { wrap("[s]", "[/s]") }
            Box {
                FmtButton(Icons.Filled.FormatColorText, "Cor da letra") { colorMenu = true }
                DropdownMenu(colorMenu, { colorMenu = false }) {
                    Text("Cor da letra", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp))
                    SwatchGrid(TEXT_COLORS) { c -> colorMenu = false; wrap("[c=#${hex(c)}]", "[/c]") }
                }
            }
            Box {
                FmtButton(Icons.Filled.BorderColor, "Marca-texto") { markMenu = true }
                DropdownMenu(markMenu, { markMenu = false }) {
                    Text("Marca-texto", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp))
                    SwatchGrid(HIGHLIGHTS) { c -> markMenu = false; wrap("[m=#${hex(c)}]", "[/m]") }
                }
            }
            FmtButton(Icons.Filled.TextIncrease, "Letra grande") { wrap("[g]", "[/g]") }
            FmtButton(Icons.Filled.TextDecrease, "Letra pequena") { wrap("[p]", "[/p]") }
            FmtButton(Icons.Filled.FormatClear, "Tirar formatação") { clearFormat() }
        }
        val tagColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        OutlinedTextField(
            tv, { tv = it; if (it.text != value) onChange(it.text) },
            label = { Text(label) }, minLines = minLines, modifier = Modifier.fillMaxWidth(),
            // o próprio campo já mostra a formatação; as marcas ficam pequenas e apagadas
            visualTransformation = { t ->
                androidx.compose.ui.text.input.TransformedText(
                    Rich.annotate(t.text, keepTags = true, tagStyle = SpanStyle(color = tagColor, fontSize = 0.7.em, fontWeight = FontWeight.Normal, fontStyle = FontStyle.Normal, background = Color.Transparent)),
                    androidx.compose.ui.text.input.OffsetMapping.Identity,
                )
            },
        )
        Text(
            "Selecione um trecho e toque num botão acima. Sem seleção, a formatação vale para o que você digitar entre as marcas.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (Rich.hasTags(tv.text)) {
            Text("Prévia", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            RichText(
                tv.text, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)).padding(12.dp),
            )
        }
    }
}

@Composable
private fun FmtButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(42.dp)) { Icon(icon, desc) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwatchGrid(colors: List<Long>, onPick: (Long) -> Unit) {
    FlowRow(Modifier.width(220.dp).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEach { c ->
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).clickable { onPick(c) },
            )
        }
    }
}
