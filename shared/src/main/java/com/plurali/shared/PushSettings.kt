package com.plurali.shared

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Escolha de como receber avisos instantâneos (ntfy). Usado no Plurali e no Companion.
 * [offText]: o que acontece com os avisos desligados (ex.: "confere a cada 15 min").
 */
@Composable
fun PushSettings(what: String, offText: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    // o endereço do app ntfy chega alguns segundos depois; a permissão de bateria volta de outra tela
    LaunchedEffect(Unit) { while (true) { delay(1500); tick++ } }
    @Suppress("UNUSED_EXPRESSION") tick
    val mode = Push.mode(ctx)
    val endpoint = Push.endpoint(ctx)
    val err = Push.error(ctx)
    val hasNtfy = Push.ntfyInstalled(ctx)
    val battery = Push.batteryOk(ctx)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Avisos instantâneos", style = MaterialTheme.typography.titleSmall)
        Text(
            when {
                mode == Push.OFF -> "Desligado: $offText."
                endpoint == null -> err ?: "Ligando…"
                mode == Push.NTFY -> "Ligado pelo app ntfy: $what chega na hora."
                else -> "Ligado pela conexão própria: $what chega na hora."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (mode != Push.OFF && endpoint == null && err != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Option(mode == Push.NTFY, "Pelo app ntfy (recomendado)",
            if (hasNtfy) "Quase não gasta bateria." else "Precisa do app ntfy (gratuito) instalado.") { Push.setMode(ctx, Push.NTFY); tick++ }
        if (!hasNtfy) TextButton(onClick = {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + Push.NTFY_PACKAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ntfy.sh/app")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }, modifier = Modifier.padding(start = 40.dp)) { Text("Instalar o ntfy") }
        Option(mode == Push.OWN, "Conexão própria",
            "Sem instalar nada, mas fica uma notificação fixa e gasta um pouco mais de bateria.") { Push.setMode(ctx, Push.OWN); tick++ }
        if (mode == Push.OWN && !battery) TextButton(onClick = {
            runCatching { ctx.startActivity(Push.batteryIntent(ctx).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }, modifier = Modifier.padding(start = 40.dp)) { Text("Deixar funcionar com a tela desligada") }
        Option(mode == Push.OFF, "Desligado", offText.replaceFirstChar { it.uppercase() } + ".") { Push.setMode(ctx, Push.OFF); tick++ }
        Text(
            "O aviso só diz “mudou”, por um canal secreto no ntfy.sh. Os dados continuam indo criptografados pelo Firebase.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Option(selected: Boolean, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
