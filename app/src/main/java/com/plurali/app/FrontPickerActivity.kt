package com.plurali.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plurali.app.data.*
import com.plurali.app.sys.PluraliApplication
import com.plurali.app.ui.SubjectAvatar
import com.plurali.app.ui.fmtTime

/** Menu pequeno aberto pelo widget de foto: trocar, somar ou tirar alguém do front sem abrir o app. */
class FrontPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PluraliApplication.ensure(this)
        setContent {
            PluraliTheme(com.plurali.app.ui.theme.currentAppearance()) {
                Picker(
                    onClose = { finish() },
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun Picker(onClose: () -> Unit, onOpenApp: () -> Unit) {
    val d by Store.data.collectAsState()
    val current = d.currentFronts().mapNotNull { e -> d.subject(e.subjectId)?.let { it to e } }.distinctBy { it.first.id }
    val inFront = current.map { it.first.id }.toSet()
    var q by remember { mutableStateOf("") }
    val all = remember(d) {
        d.members.filter { !it.archived }.sortedBy { it.name.lowercase() }.map { it.toSubject() } +
            d.customFronts.sortedBy { it.name.lowercase() }.map { it.toSubject() }
    }
    val list = all.filter { it.id !in inFront && (q.isBlank() || it.name.contains(q, ignoreCase = true)) }
    val none = remember { MutableInteractionSource() }

    Box(
        Modifier.fillMaxSize().clickable(none, null, onClick = onClose).systemBarsPadding().imePadding().padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth().clickable(remember { MutableInteractionSource() }, null) {},
        ) {
            Column(Modifier.padding(vertical = 16.dp)) {
                Text(
                    "No front", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(8.dp))
                if (current.isEmpty()) {
                    Text(
                        "Ninguém no front agora", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
                current.forEach { (s, e) ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SubjectAvatar(s, 40.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(s.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("desde ${fmtTime(e.start)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { Store.removeFromFront(s.id) }) {
                            Icon(Icons.Filled.Close, "Tirar ${s.name} do front")
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline)
                Text(
                    "Toque para trocar · + para co-front", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp),
                )
                if (all.size > 8) {
                    OutlinedTextField(
                        q, { q = it }, placeholder = { Text("Buscar") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(list, key = { it.id }) { s ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { Store.switchFront(s.id, s.custom); onClose() }
                                .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SubjectAvatar(s, 40.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sub = if (s.custom) "Custom front" else s.pronouns
                                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                            IconButton(onClick = { Store.addFront(s.id, s.custom) }) {
                                Icon(Icons.Filled.Add, "Somar ${s.name} ao front")
                            }
                        }
                    }
                    if (list.isEmpty()) item {
                        Text(
                            if (all.isEmpty()) "Cadastre membros no app primeiro." else "Ninguém encontrado.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                }

                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    if (current.isNotEmpty()) TextButton(onClick = { Store.clearFront(); onClose() }) { Text("Limpar front") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onOpenApp) { Text("Abrir app") }
                }
            }
        }
    }
}
