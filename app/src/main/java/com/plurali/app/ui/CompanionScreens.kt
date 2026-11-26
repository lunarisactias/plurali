package com.plurali.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.plurali.app.data.*
import com.plurali.app.sys.CompanionSync
import com.plurali.shared.FirebaseRest
import com.plurali.shared.Invite
import com.plurali.shared.ShareCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun qrBitmap(text: String, size: Int = 720): ImageBitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private fun copy(ctx: Context, label: String, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
}

fun inviteFor(d: AppData, c: CompanionProfile): Invite? = d.cloud?.let {
    Invite(it.dbUrl, it.apiKey, c.shareId, c.key, d.systemName.ifBlank { "Sistema" }, c.name)
}

// ===================== Lista + configuração do Firebase =====================

@Composable
fun CompanionsScreen(nav: NavHostController) {
    val d by Store.data.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val admin = LocalWho.current.isAdmin
    @Suppress("UNUSED_VARIABLE") val tick = Prefs.companionTick
    var editCloud by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val now = rememberNow()
    // busca recados novos dos companions ao abrir a tela
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { runCatching { com.plurali.app.sys.CompanionInbox.fetch(ctx) } } }

    if (!admin) {
        // membros: só leitura, para saber quem acompanha o sistema e o que cada pessoa vê
        ScreenScaffold("Companions") { pad ->
            LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(
                        "Pessoas de fora do sistema que acompanham vocês pelo app Plurali Companion. Só o Sistema pode mudar isso.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item { CompanionMessagesBlock(d) }
                if (d.companions.isEmpty()) item { EmptyHint("Ninguém de fora acompanha o sistema.") }
                items(d.companions, key = { it.id }) { c ->
                    SpRow(0xFFD6AC5E) {
                        Icon(Icons.Filled.Visibility, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium)
                            if (c.about.isNotBlank()) Text(c.about)
                            Text("Pode ver: " + permSummary(c.perms), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        return
    }

    ScreenScaffold(
        "Companions",
        fab = { if (d.cloud != null) ExtendedFloatingActionButton(onClick = { nav.go("companionEdit/new") }, icon = { Icon(Icons.Filled.PersonAdd, null) }, text = { Text("Novo companion") }) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(
                    "Companions são pessoas de fora do sistema (parceria, amizades, família) que usam o app Plurali Companion. " +
                        "Elas recebem um aviso quando o front muda e veem só o que você liberar no perfil delas. " +
                        "Os dados vão criptografados: nem o Google consegue ler.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { CompanionMessagesBlock(d) }
            if (d.cloud == null || editCloud) item { CloudSetup(d.cloud) { editCloud = false } }
            else item {
                Column(Modifier.fillMaxWidth().themed().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (Prefs.companionError == null) Icons.Filled.CloudDone else Icons.Filled.CloudOff, null,
                            tint = if (Prefs.companionError == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (Prefs.companionError == null) "Conectado ao Firebase do Plurali" else "Sem conexão com o Firebase", style = MaterialTheme.typography.titleSmall)
                            Text("Os dados vão criptografados, uma chave por companion.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Prefs.companionError?.let { Text("Último envio falhou: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    TextButton(enabled = !busy && d.companions.isNotEmpty(), onClick = {
                        busy = true
                        scope.launch { withContext(Dispatchers.IO) { CompanionSync.syncNow(ctx, force = true) }; busy = false }
                    }) { Icon(Icons.Filled.Sync, null); Spacer(Modifier.width(6.dp)); Text(if (busy) "Enviando…" else "Enviar tudo agora") }
                }
            }
            if (d.cloud != null && d.companions.isNotEmpty()) item {
                com.plurali.shared.PushSettings(
                    "o recado de um companion", "os recados dos companions são conferidos a cada 1 h",
                    Modifier.fillMaxWidth().themed().padding(12.dp),
                )
            }
            if (d.cloud != null && d.companions.isEmpty()) item { EmptyHint("Nenhum companion ainda. Toque em “Novo companion”.") }
            items(d.companions, key = { it.id }) { c ->
                val synced = Prefs.companionSynced(c.id)
                SpRow(0xFFD6AC5E, onClick = { nav.go("companionEdit/${c.id}") }) {
                    Icon(Icons.Filled.Visibility, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium)
                        if (c.about.isNotBlank()) Text(c.about, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(permSummary(c.perms), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (synced > 0) "Atualizado ${fmtWhen(synced, now)}" else "Ainda não enviado",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }
    }
}

fun permSummary(p: CompanionPerms): String = listOfNotNull(
    "front".takeIf { p.front }, "mural".takeIf { p.notices }, "calendário".takeIf { p.calendar },
    (if (p.memberIds == null) "todos os membros" else "${p.memberIds.size} membro(s)").takeIf { p.people },
    "wishlist".takeIf { p.people && p.wishlist },
    "histórico".takeIf { p.history }, "plano de segurança".takeIf { p.safety }, "corpo".takeIf { p.body },
).joinToString(" · ").ifBlank { "Nada liberado" }

@Composable
private fun CloudSetup(current: CloudConfig?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(current?.dbUrl ?: "") }
    var key by remember { mutableStateOf(current?.apiKey ?: "") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxWidth().themed().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Configurar o Firebase (uma vez só)", style = MaterialTheme.typography.titleMedium)
        Text(
            "1. Entre em console.firebase.google.com com sua conta Google e crie um projeto (pode desligar o Google Analytics).\n" +
                "2. Criação → Authentication → Começar → Método de login → Anônimo → Ativar.\n" +
                "3. Criação → Realtime Database → Criar banco de dados → escolha o local → “modo bloqueado”.\n" +
                "4. Na aba Regras do banco, apague tudo, cole as regras abaixo e publique.\n" +
                "5. Copie o endereço do banco (https://…firebaseio.com ou …firebasedatabase.app) e, em Configurações do projeto → Geral, a “Chave de API da Web”.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = { copy(ctx, "Regras", FirebaseRest.RULES); msg = "Regras copiadas." }) {
            Icon(Icons.Filled.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Copiar regras do banco")
        }
        OutlinedTextField(url, { url = it.trim() }, label = { Text("Endereço do Realtime Database") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(key, { key = it.trim() }, label = { Text("Chave de API da Web") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy && url.isNotBlank() && key.isNotBlank(), onClick = {
                busy = true; msg = "Testando…"
                scope.launch {
                    val cfg = CloudConfig(url.trimEnd('/'), key)
                    val err = withContext(Dispatchers.IO) { CompanionSync.test(cfg) }
                    busy = false
                    if (err == null) {
                        // mantém a sessão anterior se o projeto não mudou
                        Store.setCloud(if (current?.dbUrl == cfg.dbUrl && current.apiKey == cfg.apiKey) current else cfg)
                        msg = null; onDone()
                    } else msg = "Não funcionou: $err"
                }
            }) { Text("Testar e salvar") }
            if (current != null) TextButton(onClick = onDone) { Text("Cancelar") }
        }
        msg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
    }
}

// ===================== Perfil de um companion =====================

@Composable
fun CompanionEditScreen(nav: NavHostController, id: String) {
    val live by Store.data.collectAsState()
    val d = remember { Store.data.value }
    val ctx = LocalContext.current
    val existing = live.companions.find { it.id == id }
    var c by remember {
        mutableStateOf(existing ?: CompanionProfile(id = newId(), name = "", shareId = ShareCrypto.newShareId(), key = ShareCrypto.newKey()))
    }
    var p by remember { mutableStateOf(c.perms) }
    val saved = live.companions.any { it.id == c.id }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRotate by remember { mutableStateOf(false) }
    var showCode by remember { mutableStateOf(false) }
    val activeMembers = d.members.filter { !it.archived }.sortedBy { it.name.lowercase() }

    fun save(): CompanionProfile {
        val nc = c.copy(name = c.name.trim(), perms = p)
        Store.upsertCompanion(nc)
        c = nc
        return nc
    }

    ScreenScaffold(
        if (!saved) "Novo companion" else c.name.ifBlank { "Companion" },
        onBack = { nav.popBackStack() },
        actions = {
            if (saved) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Excluir") }
            TextButton(enabled = c.name.isNotBlank(), onClick = { val was = saved; save(); if (was) nav.popBackStack() }) { Text("Salvar") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                c.name, { c = c.copy(name = it) }, label = { Text("Quem é? (ex.: Namorada, Amigo João, Mãe)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                c.about, { c = c.copy(about = it) }, label = { Text("Quem é essa pessoa") }, minLines = 2, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Para os membros saberem quem está acompanhando (ex.: Júlia, namorada da Ana desde 2023).") },
            )

            SectionHeader("O que essa pessoa pode ver")
            PermRow("Quem está no front", "E recebe aviso quando o front muda.", p.front) { p = p.copy(front = it) }
            PermRow("Pode mandar recados", "A pessoa escreve um recado no app dela e ele chega aqui (e numa notificação).", p.messages) { p = p.copy(messages = it) }
            if (p.front) {
                PermRow("Status do front", "O texto de status (ex.: co-consciente).", p.frontStatus, indent = true) { p = p.copy(frontStatus = it) }
                PermRow("Custom fronts", "Mostra estados como “blurry”. Desligado: não aparecem.", p.customFronts, indent = true) { p = p.copy(customFronts = it) }
            }
            PermRow("Mural de avisos", null, p.notices) { p = p.copy(notices = it) }
            PermRow("Calendário", "Eventos que envolvem só membros escondidos não aparecem.", p.calendar) { p = p.copy(calendar = it) }
            PermRow("Histórico de front", "Últimos 14 dias.", p.history) { p = p.copy(history = it) }
            PermRow("Plano de segurança", "Para ajudar numa crise: como acalmar e quem chamar.", p.safety) { p = p.copy(safety = it) }
            PermRow("Dados gerais do corpo", "Só os gerais. Dados sensíveis nunca são enviados.", p.body) { p = p.copy(body = it) }
            PermRow("Membros (perfis)", null, p.people) { p = p.copy(people = it) }

            SectionHeader("Quais membros")
            Row(Modifier.fillMaxWidth().clickable { p = p.copy(memberIds = null) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(p.memberIds == null, { p = p.copy(memberIds = null) }); Text("Todos os membros ativos (inclusive os novos)")
            }
            Row(Modifier.fillMaxWidth().clickable { if (p.memberIds == null) p = p.copy(memberIds = activeMembers.map { it.id }) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(p.memberIds != null, { if (p.memberIds == null) p = p.copy(memberIds = activeMembers.map { it.id }) }); Text("Só os que eu escolher")
            }
            p.memberIds?.let { ids ->
                activeMembers.forEach { m ->
                    val on = m.id in ids
                    Row(
                        Modifier.fillMaxWidth().clickable { p = p.copy(memberIds = if (on) ids - m.id else ids + m.id) }.padding(start = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(on, { p = p.copy(memberIds = if (it) ids + m.id else ids - m.id) })
                        Avatar(m.name, m.color, m.avatar, 28.dp); Spacer(Modifier.width(8.dp)); Text(m.name)
                    }
                }
            }
            Text(
                "Quem não estiver liberado e entrar no front aparece só como “Alguém do sistema”.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (p.people) {
                SectionHeader("Detalhes dos perfis")
                OutlinedButton(
                    onClick = {
                        p = p.copy(
                            avatars = true, pronouns = true, intro = true, roles = true, description = true, comfort = true,
                            likes = true, birthday = true, wishlist = true, banner = true, groups = true, relations = true,
                            fieldIds = d.fields.filter { it.visibility != "private" }.map { it.id },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Filled.DoneAll, null); Spacer(Modifier.width(8.dp)); Text("Liberar todos os detalhes") }
                PermRow("Foto", null, p.avatars) { p = p.copy(avatars = it) }
                PermRow("Capa", "A imagem de fundo do perfil.", p.banner) { p = p.copy(banner = it) }
                PermRow("Pronomes", null, p.pronouns) { p = p.copy(pronouns = it) }
                PermRow("Apresentação para companions", "O texto “quem sou eu” que cada membro escreve para pessoas de fora.", p.intro) { p = p.copy(intro = it) }
                PermRow("Papéis", null, p.roles) { p = p.copy(roles = it) }
                PermRow("Descrição", null, p.description) { p = p.copy(description = it) }
                PermRow("Como me acalmar", null, p.comfort) { p = p.copy(comfort = it) }
                PermRow("Gosta / não gosta", null, p.likes) { p = p.copy(likes = it) }
                PermRow("Aniversário", "Também aparece no calendário.", p.birthday) { p = p.copy(birthday = it) }
                PermRow("Wishlist", "O que cada um quer comprar e fazer (ótimo para presentes).", p.wishlist) { p = p.copy(wishlist = it) }
                PermRow("Grupos", "Em quais grupos/subsistemas cada um está.", p.groups) { p = p.copy(groups = it) }
                PermRow("Relações", "Ex.: “Ana protege Bruno”. Só entre membros liberados.", p.relations) { p = p.copy(relations = it) }
                val fields = d.fields.filter { it.visibility != "private" }
                if (fields.isNotEmpty()) {
                    Text("Campos personalizados", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    fields.forEach { f ->
                        val on = f.id in p.fieldIds
                        Row(Modifier.fillMaxWidth().clickable { p = p.copy(fieldIds = if (on) p.fieldIds - f.id else p.fieldIds + f.id) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(on, { p = p.copy(fieldIds = if (it) p.fieldIds + f.id else p.fieldIds - f.id) }); Text(f.name)
                        }
                    }
                    Text("Campos privados nunca são enviados.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SectionHeader("Convite")
            if (!saved) {
                Text("Salve para gerar o convite (QR code).", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(enabled = c.name.isNotBlank(), onClick = { save() }) { Text("Salvar e gerar convite") }
            }
            val inv = if (live.companions.any { it.id == c.id }) inviteFor(live, c) else null
            if (inv != null) {
                val code = inv.encode()
                val qr = remember(code) { qrBitmap(code) }
                Text(
                    "Peça para ${c.name} instalar o Plurali Companion e escanear este código. " +
                        "O convite dá acesso de leitura: envie só para essa pessoa.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(qr, "QR code do convite", Modifier.size(260.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(
                            Intent.EXTRA_TEXT,
                            "Convite do Plurali Companion (${inv.systemName}).\nAbra o app Plurali Companion → “Colar código” e cole:\n\n$code",
                        )
                        ctx.startActivity(Intent.createChooser(send, "Enviar convite"))
                    }) { Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text("Enviar convite") }
                    OutlinedButton(onClick = { copy(ctx, "Convite", code) }) { Text("Copiar código") }
                }
                TextButton(onClick = { showCode = !showCode }) { Text(if (showCode) "Esconder código" else "Mostrar código em texto") }
                if (showCode) SelectionContainer {
                    Text(code, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(8.dp))
                }
                OutlinedButton(onClick = { confirmRotate = true }) {
                    Icon(Icons.Filled.Autorenew, null); Spacer(Modifier.width(6.dp)); Text("Gerar novo convite (corta o acesso atual)")
                }
                val preview = remember(live, c, p) { CompanionSync.build(ctx, live, c.copy(perms = p)) }
                Text(
                    "Prévia do que ${c.name} vê agora: " + listOfNotNull(
                        preview.front?.let { "${it.size} no front" }, preview.notices?.let { "${it.size} avisos" },
                        preview.events?.let { "${it.size} eventos" }, preview.people?.let { "${it.size} perfis" },
                        preview.history?.let { "${it.size} registros de front" }, preview.safety?.let { "plano de segurança" },
                        preview.body?.let { "${it.size} dados do corpo" },
                    ).joinToString(", ").ifBlank { "nada" } + ".",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (confirmRotate) ConfirmDialog(
        "Gerar novo convite?", "O convite atual para de funcionar e ${c.name} vai precisar escanear o novo QR.", "Gerar novo",
        onDismiss = { confirmRotate = false },
    ) {
        Prefs.addPendingDelete(c.shareId)
        Prefs.forgetCompanionHash(c.id)
        c = c.copy(shareId = ShareCrypto.newShareId(), key = ShareCrypto.newKey())
        Prefs.setCompanionPush(c.id, null) // o endereço de aviso vem de novo do companion, pelo convite novo
        Store.rotateMediaKey() // quem tinha o convite antigo também perde as fotos
        save()
    }
    if (confirmDelete) ConfirmDialog(
        "Excluir ${c.name}?", "Os dados compartilhados com essa pessoa são apagados do Firebase e o companion dela para de receber atualizações.",
        onDismiss = { confirmDelete = false },
    ) { Store.deleteCompanion(c.id); nav.popBackStack() }
}

@Composable
private fun PermRow(title: String, sub: String?, value: Boolean, indent: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(start = if (indent) 24.dp else 0.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(value, onChange)
    }
}

/** Recados que os companions mandaram para o sistema. */
@Composable
fun CompanionMessagesBlock(d: AppData) {
    val msgs = d.companionMessages.sortedByDescending { it.time }
    if (msgs.isEmpty()) return
    val now = rememberNow()
    val unread = msgs.count { !it.read }
    Column(Modifier.fillMaxWidth().themed().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MarkEmailUnread, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Recados dos companions" + if (unread > 0) " ($unread novos)" else "", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (unread > 0) TextButton(onClick = { Store.readCompanionMessages() }) { Text("Marcar lidos") }
        }
        msgs.take(20).forEach { m ->
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 6.dp).size(8.dp).clip(RoundedCornerShape(50))
                    .background(if (m.read) Color.Transparent else MaterialTheme.colorScheme.error))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("${d.companions.find { it.id == m.companionId }?.name ?: "Companion"} · ${fmtWhen(m.time, now)}",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(m.text)
                }
                IconButton(onClick = { Store.deleteCompanionMessage(m.id) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Close, "Apagar", Modifier.size(16.dp)) }
            }
        }
    }
}
