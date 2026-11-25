package com.plurali.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.plurali.app.data.*

// ===================== Grupos =====================

@Composable
fun GroupsScreen(nav: NavHostController) {
    val d by Store.data.collectAsState()
    val tree = remember(d.groups) { d.groupTree() }
    ScreenScaffold(
        "Grupos", onBack = { nav.popBackStack() },
        fab = { FloatingActionButton(onClick = { nav.go("groupEdit/new") }) { Icon(Icons.Filled.Add, "Novo grupo") } },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            if (tree.isEmpty()) item {
                Box(Modifier.padding(16.dp)) { EmptyHint("Nenhum grupo. Grupos podem ter subgrupos (subsistemas).") }
            }
            items(tree, key = { it.first.id }) { (g, depth) ->
                val count = d.groupMemberIdsDeep(g.id).size
                ListItem(
                    modifier = Modifier.clickable { nav.go("groupEdit/${g.id}") }.padding(start = (depth * 24).dp),
                    leadingContent = { Avatar(g.emoji.ifBlank { g.name }, g.color, null, 40.dp) },
                    headlineContent = { Text(g.name) },
                    supportingContent = {
                        Text(
                            "$count membro(s)" + if (g.description.isNotBlank()) " · ${g.description}" else "",
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun GroupEditScreen(nav: NavHostController, id: String, parent: String?) {
    val d = remember { Store.data.value }
    val existing = d.groups.find { it.id == id }
    var g by remember { mutableStateOf(existing ?: MemberGroup(newId(), "", color = palette.random(), parentId = parent)) }
    var nameError by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var parentPick by remember { mutableStateOf(false) }
    val blocked = remember { d.descendants(g.id) + g.id }

    ScreenScaffold(
        if (existing == null) "Nova pasta" else "Editar pasta",
        onBack = { nav.popBackStack() },
        actions = {
            if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Excluir") }
            TextButton(onClick = {
                if (g.name.isBlank()) nameError = true else {
                    Store.upsertGroup(g.copy(name = g.name.trim())); nav.popBackStack()
                }
            }) { Text("Salvar") }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    g.emoji, { g = g.copy(emoji = it.take(4)) }, label = { Text("Emoji") },
                    singleLine = true, modifier = Modifier.width(96.dp),
                )
                OutlinedTextField(
                    g.name, { g = g.copy(name = it); nameError = false }, label = { Text("Nome *") },
                    isError = nameError, singleLine = true, modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(g.description, { g = g.copy(description = it) }, label = { Text("Descrição") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { parentPick = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Dentro de: " + (d.groups.find { it.id == g.parentId }?.name ?: "Raiz"))
            }
            Text("Cor", style = MaterialTheme.typography.titleSmall)
            ColorPicker(g.color) { g = g.copy(color = it) }
            Text("Membros (${g.memberIds.size})", style = MaterialTheme.typography.titleSmall)
            if (d.members.isEmpty()) Text("Nenhum membro cadastrado ainda.")
            d.members.sortedBy { it.name.lowercase() }.forEach { m ->
                val on = m.id in g.memberIds
                Row(
                    Modifier.fillMaxWidth().clickable {
                        g = g.copy(memberIds = if (on) g.memberIds - m.id else g.memberIds + m.id)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(on, { g = g.copy(memberIds = if (it) g.memberIds + m.id else g.memberIds - m.id) })
                    Avatar(m.name, m.color, m.avatar, 32.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(m.name + if (m.archived) " (arquivado)" else "")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (parentPick) AlertDialog(
        onDismissRequest = { parentPick = false },
        title = { Text("Grupo pai") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                item {
                    ListItem(headlineContent = { Text("Nenhum (raiz)") }, modifier = Modifier.clickable {
                        g = g.copy(parentId = null); parentPick = false
                    })
                }
                items(d.groupTree().filter { it.first.id !in blocked }, key = { it.first.id }) { (pg, depth) ->
                    ListItem(
                        headlineContent = { Text(pg.name) },
                        modifier = Modifier.clickable { g = g.copy(parentId = pg.id); parentPick = false }.padding(start = (depth * 16).dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { parentPick = false }) { Text("Cancelar") } },
    )
    if (confirmDelete && existing != null) ConfirmDialog(
        "Excluir pasta?", "Os membros não são apagados. Subpastas passam para a pasta de cima.",
        onDismiss = { confirmDelete = false },
    ) { Store.deleteGroup(existing.id); nav.popBackStack() }
}

// ===================== Custom fronts =====================

@Composable
fun CustomFrontsScreen(nav: NavHostController) {
    val d by Store.data.collectAsState()
    ScreenScaffold(
        "Custom fronts", onBack = { nav.popBackStack() },
        fab = { FloatingActionButton(onClick = { nav.go("customFrontEdit/new") }) { Icon(Icons.Filled.Add, "Novo") } },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            item {
                Text(
                    "Custom fronts são estados que podem ir pro front como se fossem membros: “Blurry”, “Dissociado”, “Desconhecido”, “Dormindo”…",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(d.customFronts.sortedBy { it.name.lowercase() }, key = { it.id }) { c ->
                ListItem(
                    modifier = Modifier.clickable { nav.go("customFrontEdit/${c.id}") },
                    leadingContent = { Avatar(c.name, c.color, null, 40.dp) },
                    headlineContent = { Text(c.name) },
                    supportingContent = { if (c.description.isNotBlank()) Text(c.description, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}

@Composable
fun CustomFrontEditScreen(nav: NavHostController, id: String) {
    val d = remember { Store.data.value }
    val existing = d.customFronts.find { it.id == id }
    var c by remember { mutableStateOf(existing ?: CustomFront(newId(), "", palette.random())) }
    var confirmDelete by remember { mutableStateOf(false) }
    ScreenScaffold(
        if (existing == null) "Novo custom front" else "Editar custom front",
        onBack = { nav.popBackStack() },
        actions = {
            if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Excluir") }
            TextButton(enabled = c.name.isNotBlank(), onClick = {
                Store.upsertCustomFront(c.copy(name = c.name.trim())); nav.popBackStack()
            }) { Text("Salvar") }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(c.name, { c = c.copy(name = it) }, label = { Text("Nome *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(c.description, { c = c.copy(description = it) }, label = { Text("Descrição") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Text("Cor", style = MaterialTheme.typography.titleSmall)
            ColorPicker(c.color) { c = c.copy(color = it) }
        }
    }
    if (confirmDelete && existing != null) ConfirmDialog(
        "Excluir ${existing.name}?", "O histórico de front deste custom front também será apagado.",
        onDismiss = { confirmDelete = false },
    ) { Store.deleteCustomFront(existing.id); nav.popBackStack() }
}

// ===================== Campos personalizados =====================

@Composable
fun FieldsScreen(nav: NavHostController) {
    val d by Store.data.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<FieldDef?>(null) }
    var deleting by remember { mutableStateOf<FieldDef?>(null) }
    ScreenScaffold(
        "Campos personalizados", onBack = { nav.popBackStack() },
        fab = { FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, "Novo campo") } },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            item {
                Text(
                    "Campos aparecem no perfil de todos os membros (ex.: Idade, Gostos, Gatilhos, Função).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(d.fields, key = { it.id }) { f ->
                ListItem(
                    headlineContent = { Text(f.name) },
                    supportingContent = {
                        val next = mapOf("all" to "members", "members" to "private", "private" to "all")
                        val label = mapOf(
                            "all" to "Visível para todos (inclusive sem login)",
                            "members" to "Só membros com login",
                            "private" to "Privado: só o próprio membro e o sistema",
                        )
                        AssistChip(
                            onClick = { Store.upsertField(f.copy(visibility = next[f.visibility] ?: "all")) },
                            label = { Text(label[f.visibility] ?: label["all"]!!) },
                            leadingIcon = {
                                Icon(
                                    when (f.visibility) { "private" -> Icons.Filled.Lock; "members" -> Icons.Filled.Group; else -> Icons.Filled.Public },
                                    null, Modifier.size(16.dp),
                                )
                            },
                        )
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { Store.moveField(f.id, -1) }) { Icon(Icons.Filled.KeyboardArrowUp, "Subir") }
                            IconButton(onClick = { Store.moveField(f.id, 1) }) { Icon(Icons.Filled.KeyboardArrowDown, "Descer") }
                            IconButton(onClick = { renaming = f }) { Icon(Icons.Filled.Edit, "Renomear") }
                            IconButton(onClick = { deleting = f }) { Icon(Icons.Filled.Delete, "Excluir") }
                        }
                    },
                )
            }
        }
    }
    if (adding) TextInputDialog("Novo campo", "", "Nome do campo", { adding = false }) { Store.upsertField(FieldDef(newId(), it)) }
    renaming?.let { f -> TextInputDialog("Renomear campo", f.name, "Nome do campo", { renaming = null }) { Store.upsertField(f.copy(name = it)) } }
    deleting?.let { f ->
        ConfirmDialog("Excluir “${f.name}”?", "O valor deste campo será apagado em todos os membros.", onDismiss = { deleting = null }) {
            Store.deleteField(f.id)
        }
    }
}

