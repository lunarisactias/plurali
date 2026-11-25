package com.plurali.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.plurali.app.data.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val relationTypes = listOf(
    "Protege", "Cuida de", "É próximo(a) de", "É irmão/irmã de", "É parceiro(a) de",
    "É fragmento de", "Surgiu de", "Tem conflito com", "Confia em",
)

fun relationColor(type: String): Long = palette[(type.lowercase().hashCode() and 0x7fffffff) % 18]

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RelationDialog(d: AppData, initial: Relation, selfId: String, onDismiss: () -> Unit) {
    val isNew = d.relations.none { it.id == initial.id }
    var r by remember { mutableStateOf(initial) }
    var pick by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(if (initial.type !in relationTypes) initial.type else "") }
    val otherId = if (r.a == selfId) r.b else r.a
    val selfName = d.members.find { it.id == selfId }?.name ?: "?"
    val otherName = d.members.find { it.id == otherId }?.name
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Nova relação" else "Relação") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pick = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Com quem: ${otherName ?: "escolher…"}")
                }
                val aName = if (r.a == selfId) selfName else (otherName ?: "…")
                val bName = if (r.a == selfId) (otherName ?: "…") else selfName
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$aName ${r.type.ifBlank { "…" }.lowercase()} $bName",
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { r = r.copy(a = r.b, b = r.a) }) { Icon(Icons.Filled.SwapHoriz, "Inverter direção") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    relationTypes.forEach { t ->
                        FilterChip(r.type == t, { r = r.copy(type = t); custom = "" }, label = { Text(t) })
                    }
                }
                OutlinedTextField(custom, { custom = it; r = r.copy(type = it) }, label = { Text("Outro tipo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(r.note, { r = r.copy(note = it) }, label = { Text("Observação") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = r.a.isNotBlank() && r.b.isNotBlank() && r.a != r.b && r.type.isNotBlank(), onClick = {
                Store.upsertRelation(r.copy(type = r.type.trim())); onDismiss()
            }) { Text("Salvar") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { Store.deleteRelation(r.id); onDismiss() }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
    if (pick) {
        val dd = d.copy(members = d.members.filter { it.id != selfId }, customFronts = emptyList())
        SubjectPickerDialog(dd, "Com quem?", includeCustom = false, onDismiss = { pick = false }) { s ->
            if (s != null) r = if (r.a == selfId) r.copy(b = s.id) else r.copy(a = s.id)
            pick = false
        }
    }
}

@Composable
fun SystemMapScreen(nav: NavHostController) {
    val d by Store.data.collectAsState()
    val who = LocalWho.current
    val members = remember(d.members) { d.members.filter { !it.archived }.sortedBy { it.name.lowercase() } }
    var focus by remember { mutableStateOf<String?>(null) }
    val rels = d.relations.filter { r -> members.any { it.id == r.a } && members.any { it.id == r.b } }
    ScreenScaffold("Mapa do sistema") { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                "Cada linha é uma relação. Toque em alguém para destacar as relações; toque de novo para abrir o perfil. " +
                    (if (who.canWrite) "Crie relações na aba “Relações” do perfil." else ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            if (members.isEmpty()) { EmptyHint("Nenhum membro."); return@Column }
            val n = members.size
            val node = 48.dp
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                val side = maxWidth.coerceAtLeast(node * n / 2.2f)
                val center = side / 2
                val radius = center - node
                val density = LocalDensity.current
                fun pos(i: Int): Pair<Dp, Dp> {
                    val ang = -PI / 2 + 2 * PI * i / n
                    return (center + radius * cos(ang).toFloat()) to (center + radius * sin(ang).toFloat())
                }
                val idx = members.mapIndexed { i, m -> m.id to i }.toMap()
                Box(Modifier.size(side)) {
                    Canvas(Modifier.fillMaxSize()) {
                        rels.forEach { r ->
                            val (ax, ay) = pos(idx[r.a] ?: return@forEach)
                            val (bx, by) = pos(idx[r.b] ?: return@forEach)
                            val hi = focus == null || r.a == focus || r.b == focus
                            drawLine(
                                Color(relationColor(r.type)).copy(alpha = if (hi) 0.9f else 0.15f),
                                Offset(with(density) { ax.toPx() }, with(density) { ay.toPx() }),
                                Offset(with(density) { bx.toPx() }, with(density) { by.toPx() }),
                                strokeWidth = with(density) { (if (hi) 3.dp else 2.dp).toPx() },
                            )
                        }
                    }
                    // rótulos das relações (no meio da linha) quando em destaque
                    rels.filter { focus != null && (it.a == focus || it.b == focus) }.forEach { r ->
                        val (ax, ay) = pos(idx[r.a] ?: return@forEach)
                        val (bx, by) = pos(idx[r.b] ?: return@forEach)
                        Text(
                            r.type,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .offset(x = (ax + bx) / 2 - 40.dp, y = (ay + by) / 2 - 10.dp)
                                .width(80.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    members.forEachIndexed { i, m ->
                        val (x, y) = pos(i)
                        Column(
                            Modifier.offset(x = x - 40.dp, y = y - node / 2).width(80.dp)
                                .clickable { if (focus == m.id) nav.go("member/${m.id}") else focus = m.id },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Avatar(
                                m.name, m.color, m.avatar, if (focus == m.id) node + 8.dp else node,
                            )
                            Text(m.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            SectionHeader("Relações (${rels.size})", Modifier.padding(horizontal = 16.dp))
            if (rels.isEmpty()) Box(Modifier.padding(horizontal = 16.dp)) { EmptyHint("Nenhuma relação registrada ainda.") }
            rels.sortedBy { r -> d.members.find { it.id == r.a }?.name?.lowercase() }.forEach { r ->
                val a = d.members.find { it.id == r.a }
                val b = d.members.find { it.id == r.b }
                Row(
                    Modifier.fillMaxWidth().clickable { focus = r.a }.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(relationColor(r.type))))
                    Spacer(Modifier.width(10.dp))
                    Text("${a?.name} ${r.type.lowercase()} ${b?.name}" + if (r.note.isNotBlank()) " — ${r.note}" else "")
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
