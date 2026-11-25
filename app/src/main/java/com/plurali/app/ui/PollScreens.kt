package com.plurali.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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

@Composable
fun PollsScreen(nav: NavHostController) {
    val d by Store.data.collectAsState()
    val now = rememberNow()
    val (open, closed) = remember(d.polls, now) { d.polls.sortedByDescending { it.createdAt }.partition { it.isOpen(now) } }
    ScreenScaffold(
        "Enquetes",
        fab = {
            ExtendedFloatingActionButton(
                onClick = { nav.go("pollEdit/new") },
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Nova enquete") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (d.polls.isEmpty()) item { EmptyHint("Nenhuma enquete. Use para decisões do sistema: cada membro vota.") }
            if (open.isNotEmpty()) item { SectionHeader("Abertas") }
            items(open, key = { it.id }) { p -> PollCard(d, p) { nav.go("poll/${p.id}") } }
            if (closed.isNotEmpty()) item { SectionHeader("Encerradas") }
            items(closed, key = { it.id }) { p -> PollCard(d, p) { nav.go("poll/${p.id}") } }
        }
    }
}

@Composable
private fun PollCard(d: AppData, p: Poll, onClick: () -> Unit) {
    val leader = p.options.maxByOrNull { o -> p.votes.count { it.optionId == o.id } }
    val leaderVotes = leader?.let { o -> p.votes.count { it.optionId == o.id } } ?: 0
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Text(p.title, style = MaterialTheme.typography.titleMedium)
            if (p.description.isNotBlank()) Text(p.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val active = d.members.count { !it.archived }
            Text(
                "${p.votes.size}/$active votos" + if (leader != null && leaderVotes > 0) " · liderando: ${leader.name}" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PollDetailScreen(nav: NavHostController, id: String) {
    val d by Store.data.collectAsState()
    val now = rememberNow()
    val p = d.polls.find { it.id == id }
    var voting by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (p == null) {
        ScreenScaffold("Enquete", onBack = { nav.popBackStack() }) { pad -> Box(Modifier.padding(pad).padding(16.dp)) { EmptyHint("Enquete não encontrada.") } }
        return
    }
    val isOpen = p.isOpen(now)
    val optionIds = p.options.map { it.id } + listOfNotNull(if (p.allowAbstain) ABSTAIN else null, if (p.allowVeto) VETO else null)
    val total = p.votes.size.coerceAtLeast(1)
    val voted = p.votes.map { it.memberId }.toSet()
    val missing = d.members.filter { !it.archived && it.id !in voted }

    ScreenScaffold(
        p.title, onBack = { nav.popBackStack() },
        actions = {
            IconButton(onClick = { nav.go("pollEdit/${p.id}") }) { Icon(Icons.Filled.Edit, "Editar") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Excluir") }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                if (p.description.isNotBlank()) Text(p.description)
                Text(
                    if (isOpen) "Aberta · criada em ${fmtDateTime(p.createdAt)}" + (p.endTime?.let { " · encerra em ${fmtDateTime(it)}" } ?: "")
                    else "Encerrada em ${fmtDateTime(p.endTime ?: now)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isOpen) Button(onClick = { voting = true }) { Icon(Icons.Filled.HowToVote, null); Spacer(Modifier.width(6.dp)); Text("Votar") }
                    OutlinedButton(onClick = { Store.setPollEnd(p.id, if (isOpen) System.currentTimeMillis() else null) }) {
                        Text(if (isOpen) "Encerrar" else "Reabrir")
                    }
                }
            }
            item { SectionHeader("Resultado") }
            items(optionIds, key = { "o-$it" }) { oid ->
                val votes = p.votes.filter { it.optionId == oid }
                val frac = votes.size.toFloat() / total
                Column(Modifier.fillMaxWidth()) {
                    Row {
                        Text(p.optionName(oid), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Text("${votes.size} (${(frac * 100).toInt()}%)")
                    }
                    LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    if (votes.isNotEmpty()) Text(
                        votes.mapNotNull { v -> d.members.find { it.id == v.memberId }?.name }.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (p.votes.any { it.comment.isNotBlank() }) {
                item { SectionHeader("Comentários") }
                items(p.votes.filter { it.comment.isNotBlank() }, key = { "c-" + it.memberId }) { v ->
                    val m = d.members.find { it.id == v.memberId }
                    ListItem(
                        leadingContent = { Avatar(m?.name ?: "?", m?.color ?: 0xFF9E9E9E, m?.avatar, 32.dp) },
                        headlineContent = { Text("${m?.name ?: "?"} → ${p.optionName(v.optionId)}") },
                        supportingContent = { Text(v.comment) },
                    )
                }
            }
            if (isOpen && missing.isNotEmpty()) item {
                SectionHeader("Ainda não votaram (${missing.size})")
                Text(missing.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (voting) VoteDialog(d, p) { voting = false }
    if (confirmDelete) ConfirmDialog("Excluir enquete?", "Não dá para desfazer.", onDismiss = { confirmDelete = false }) {
        Store.deletePoll(p.id); nav.popBackStack()
    }
}

@Composable
private fun VoteDialog(d: AppData, p: Poll, onDismiss: () -> Unit) {
    val me = LocalWho.current.meId
    var voter by remember {
        mutableStateOf(me ?: d.currentFronts().map { it.subjectId }.firstOrNull { id -> d.members.any { it.id == id } })
    }
    var option by remember { mutableStateOf<String?>(null) }
    var comment by remember { mutableStateOf("") }
    var pickVoter by remember { mutableStateOf(false) }
    LaunchedEffect(voter) {
        val prev = p.votes.find { it.memberId == voter }
        option = prev?.optionId
        comment = prev?.comment ?: ""
    }
    val optionIds = p.options.map { it.id } + listOfNotNull(if (p.allowAbstain) ABSTAIN else null, if (p.allowVeto) VETO else null)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Votar") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedButton(onClick = { if (me == null) pickVoter = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Quem vota: " + (d.members.find { it.id == voter }?.name ?: "escolher…"))
                }
                Spacer(Modifier.height(8.dp))
                optionIds.forEach { oid ->
                    Row(
                        Modifier.fillMaxWidth().selectable(option == oid, onClick = { option = oid }).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(option == oid, onClick = { option = oid })
                        Text(p.optionName(oid))
                    }
                }
                OutlinedTextField(comment, { comment = it }, label = { Text("Comentário (opcional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = voter != null && option != null, onClick = {
                Store.vote(p.id, voter!!, option!!, comment.trim()); onDismiss()
            }) { Text("Votar") }
        },
        dismissButton = {
            Row {
                if (voter != null && p.votes.any { it.memberId == voter }) TextButton(onClick = {
                    Store.removeVote(p.id, voter!!); onDismiss()
                }) { Text("Retirar voto") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
    if (pickVoter) SubjectPickerDialog(d, "Quem vota?", includeCustom = false, onDismiss = { pickVoter = false }) { s ->
        if (s != null) voter = s.id
        pickVoter = false
    }
}

@Composable
fun PollEditScreen(nav: NavHostController, id: String) {
    val d = remember { Store.data.value }
    val existing = d.polls.find { it.id == id }
    var p by remember {
        mutableStateOf(existing ?: Poll(newId(), "", options = listOf(PollOption(newId(), "Sim"), PollOption(newId(), "Não"))))
    }
    val valid = p.title.isNotBlank() && p.options.count { it.name.isNotBlank() } >= 2
    var pickEnd by remember { mutableStateOf(false) }
    if (pickEnd) DateTimePickerDialog(p.endTime ?: (System.currentTimeMillis() + 86_400_000L), onDismiss = { pickEnd = false }) {
        p = p.copy(endTime = it); pickEnd = false
    }
    ScreenScaffold(
        if (existing == null) "Nova enquete" else "Editar enquete",
        onBack = { nav.popBackStack() },
        actions = {
            TextButton(enabled = valid, onClick = {
                Store.upsertPoll(p.copy(title = p.title.trim(), options = p.options.filter { it.name.isNotBlank() }))
                nav.popBackStack()
            }) { Text("Salvar") }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(p.title, { p = p.copy(title = it) }, label = { Text("Pergunta *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(p.description, { p = p.copy(description = it) }, label = { Text("Descrição") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Text("Opções", style = MaterialTheme.typography.titleSmall)
            p.options.forEachIndexed { i, o ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        o.name, { v -> p = p.copy(options = p.options.toMutableList().also { it[i] = o.copy(name = v) }) },
                        label = { Text("Opção ${i + 1}") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    IconButton(enabled = p.options.size > 2, onClick = { p = p.copy(options = p.options - o) }) {
                        Icon(Icons.Filled.Close, "Remover opção")
                    }
                }
            }
            TextButton(onClick = { p = p.copy(options = p.options + PollOption(newId(), "")) }) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Adicionar opção")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Permitir abstenção", Modifier.weight(1f)); Switch(p.allowAbstain, { p = p.copy(allowAbstain = it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Permitir veto", Modifier.weight(1f)); Switch(p.allowVeto, { p = p.copy(allowVeto = it) })
            }
            Text("Prazo", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val end = p.endTime
                OutlinedButton(onClick = { pickEnd = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Schedule, null); Spacer(Modifier.width(6.dp))
                    Text(if (end == null) "Sem prazo (encerrar manualmente)" else "Encerra sozinha em ${fmtDateTime(end)}")
                }
                if (end != null) IconButton(onClick = { p = p.copy(endTime = null) }) { Icon(Icons.Filled.Close, "Tirar prazo") }
            }
            Text(
                "Quando o prazo acabar, a enquete fecha e o app avisa com o resultado.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (existing != null && existing.votes.isNotEmpty()) Text(
                "Votos em opções removidas serão descartados.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
