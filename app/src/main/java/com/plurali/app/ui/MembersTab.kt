package com.plurali.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.plurali.app.data.*

/** Aba Membros: pastas (grupos) + membros, como no SimplyPlural. */
@Composable
fun MembersTab(nav: NavHostController) {
    val d by Store.data.collectAsState()
    val who = LocalWho.current
    val ctx = LocalContext.current
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var showFolders by rememberSaveable { mutableStateOf(true) }
    var compact by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf<String?>(null) }
    var showArchived by rememberSaveable { mutableStateOf(false) }

    val cur = d.groups.find { it.id == folder }
    LaunchedEffect(folder, cur) { if (folder != null && cur == null) folder = null }
    val fronting = remember(d.fronts) { d.currentFronts().associateBy { it.subjectId } }
    val q = search.orEmpty()
    val subfolders = if (showFolders && q.isBlank()) d.groups.filter { it.parentId == folder }.sortedBy { it.name.lowercase() } else emptyList()
    val members = d.members.filter {
        it.archived == showArchived &&
            (cur == null || it.id in cur.memberIds) &&
            (q.isBlank() || it.name.contains(q, true) || it.pronouns.contains(q, true))
    }.sortedBy { it.name.lowercase() }
    val path = remember(d.groups, folder) {
        val out = mutableListOf<MemberGroup>()
        var g = d.groups.find { it.id == folder }
        val seen = mutableSetOf<String>()
        while (g != null && seen.add(g.id)) { out.add(0, g); g = d.groups.find { it.id == g!!.parentId } }
        out
    }

    Column(Modifier.fillMaxSize()) {
        TabToolbar(
            left = {
                ToolIcon(Icons.Filled.Folder, "Mostrar pastas", showFolders) { showFolders = !showFolders }
                if (who.canWrite) ToolIcon(Icons.Filled.PersonAdd, "Novo membro") { nav.go("memberEdit/new") }
                if (who.isAdmin) ToolIcon(Icons.Filled.CreateNewFolder, "Nova pasta") { nav.go("groupNew/${folder ?: "root"}") }
            },
            right = {
                if (who.canWrite) ToolIcon(Icons.Filled.Archive, "Arquivados", showArchived) { showArchived = !showArchived }
                ToolIcon(Icons.Filled.ViewHeadline, "Lista compacta", compact) { compact = true }
                ToolIcon(Icons.AutoMirrored.Filled.ViewList, "Lista detalhada", !compact) { compact = false }
                ToolIcon(Icons.Filled.Search, "Buscar", search != null) { search = if (search == null) "" else null }
            },
        )
        search?.let { SearchField(it) { v -> search = v } }

        // caminho: Raiz › Pasta › Subpasta
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        ) {
            item {
                Text(
                    if (showArchived) "Arquivados" else "Raiz",
                    color = if (folder == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { folder = null }.padding(vertical = 4.dp),
                )
            }
            items(path, key = { it.id }) { g ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ChevronRight, null, Modifier.size(18.dp))
                    Text(
                        g.name,
                        color = if (g.id == folder) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { folder = g.id }.padding(vertical = 4.dp),
                    )
                }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 8.dp))

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (who.isAdmin && d.pendingMembers.isNotEmpty() && cur == null) item(key = "pending") { PendingMembersBanner(d) }
            if (cur != null) item(key = "up") {
                SpRow(cur.color, onClick = { folder = cur.parentId }) {
                    Icon(Icons.Filled.ArrowUpward, null, Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Voltar", Modifier.weight(1f))
                    if (who.isAdmin) TextButton(onClick = { nav.go("groupEdit/${cur.id}") }) { Text("Editar pasta") }
                }
            }
            items(subfolders, key = { "g-" + it.id }) { g ->
                SpRow(
                    g.color,
                    onClick = { folder = g.id },
                    onLongClick = { if (who.isAdmin) nav.go("groupEdit/${g.id}") },
                ) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        if (g.emoji.isNotBlank()) Text(g.emoji, fontSize = 24.sp)
                        else Icon(Icons.Filled.Folder, null, tint = Color(g.color), modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(g.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        "${d.groupMemberIdsDeep(g.id).size}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (members.isEmpty() && subfolders.isEmpty()) item {
                EmptyHint(
                    when {
                        d.members.isEmpty() -> "Nenhum membro ainda. Toque no ícone de pessoa com + lá em cima para adicionar."
                        showArchived -> "Nenhum membro arquivado."
                        cur != null -> "Pasta vazia. Edite a pasta para colocar membros nela."
                        else -> "Ninguém encontrado."
                    }
                )
            }
            items(members, key = { "m-" + it.id }) { m ->
                val f = fronting[m.id]
                Row(Modifier.height(IntrinsicSize.Min)) {
                    SpRow(m.color, Modifier.weight(1f), onClick = { nav.go("member/${m.id}") }) {
                        Avatar(m.name, m.color, m.avatar, if (compact) 36.dp else 56.dp, frame = if (compact) null else m.frame)
                        Spacer(Modifier.width(12.dp))
                        if (compact) Text(m.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        else NameAndPronouns(m.name, m.pronouns, Modifier.weight(1f))
                    }
                    if (who.canWrite) Spacer(Modifier.width(8.dp))
                    if (who.canWrite) FrontButton(
                        m.color, fronting = f != null,
                        onLongClick = {
                            Store.switchFront(m.id, false)
                            Toast.makeText(ctx, "${m.name} agora está sozinho no front", Toast.LENGTH_SHORT).show()
                        },
                    ) { if (f != null) Store.endFront(f.id) else Store.addFront(m.id, false) }
                }
            }
        }
    }
}
