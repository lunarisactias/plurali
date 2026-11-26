package com.plurali.app.ui

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.plurali.app.data.Store
import com.plurali.app.data.newId
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

/** Gravação de mensagens de voz (AAC em .m4a, na pasta audio/ — entra no backup). */
object AudioRec {
    private var rec: MediaRecorder? = null
    private var path: String? = null
    private var started = 0L
    val active get() = rec != null

    fun start(ctx: Context): Boolean = runCatching {
        File(Store.filesDir, "audio").mkdirs()
        val rel = "audio/${newId()}.m4a"
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(64_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(File(Store.filesDir, rel).path)
        r.prepare(); r.start()
        rec = r; path = rel; started = System.currentTimeMillis()
        true
    }.getOrElse { cancel(); false }

    /** Para e devolve (caminho, duração), ou null se deu errado ou ficou curto demais. */
    fun stop(): Pair<String, Long>? {
        val r = rec ?: return null
        val p = path
        val ms = System.currentTimeMillis() - started
        rec = null; path = null
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        if (!ok || p == null || ms < 700) { p?.let { File(Store.filesDir, it).delete() }; return null }
        return p to ms
    }

    fun cancel() {
        runCatching { rec?.stop() }; runCatching { rec?.release() }
        path?.let { File(Store.filesDir, it).delete() }
        rec = null; path = null
    }

    fun elapsed() = if (rec == null) 0L else System.currentTimeMillis() - started
}

/** Toca um áudio por vez. */
object AudioPlay {
    private var mp: MediaPlayer? = null
    var playing by mutableStateOf<String?>(null)
        private set

    fun toggle(path: String) {
        if (playing == path) { stop(); return }
        stop()
        Narrator.stop()
        runCatching {
            val p = MediaPlayer()
            p.setDataSource(File(Store.filesDir, path).path)
            p.setOnCompletionListener { stop() }
            p.prepare(); p.start()
            mp = p; playing = path
        }.onFailure { stop() }
    }

    fun position(): Int = runCatching { mp?.currentPosition ?: 0 }.getOrDefault(0)

    fun stop() {
        runCatching { mp?.stop() }; runCatching { mp?.release() }
        mp = null; playing = null
    }
}

fun fmtSecs(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

/** Balão de áudio: tocar/pausar, barra de progresso e duração. */
@Composable
fun AudioBubble(path: String, durationMs: Long) {
    val isPlaying = AudioPlay.playing == path
    var pos by remember(path) { mutableIntStateOf(0) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) { pos = 0; return@LaunchedEffect }
        while (true) { pos = AudioPlay.position(); delay(150) }
    }
    Row(Modifier.widthIn(min = 200.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledIconButton(onClick = { AudioPlay.toggle(path) }, modifier = Modifier.size(40.dp)) {
            Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (isPlaying) "Pausar" else "Ouvir")
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { if (durationMs > 0) (pos / durationMs.toFloat()).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (isPlaying) "${fmtSecs(pos.toLong())} / ${fmtSecs(durationMs)}" else "🎤 ${fmtSecs(durationMs)}",
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Faixa mostrada enquanto grava: bolinha vermelha, tempo, cancelar e enviar. */
@Composable
fun RecordingBar(onCancel: () -> Unit, onSend: () -> Unit) {
    var ms by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { ms = AudioRec.elapsed(); delay(200) } }
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "Cancelar gravação") }
        Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.error, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text("Gravando ${fmtSecs(ms)}", modifier = Modifier.weight(1f))
        FilledIconButton(onClick = onSend) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar áudio") }
    }
}

/** Leitura em voz alta (para quem chega e tem dificuldade de ler ou está confuso). */
object Speech {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<String, String>? = null // (id, texto) pedido antes do motor de voz ficar pronto
    private var doneCb: (() -> Unit)? = null // chamado (na thread principal) quando a fala termina sozinha
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    var speakingId by mutableStateOf<String?>(null)
        private set

    private fun finished(utteranceId: String?) {
        main.post {
            if (utteranceId != speakingId) return@post
            speakingId = null
            val cb = doneCb; doneCb = null
            cb?.invoke()
        }
    }

    /** Fala o texto e avisa quando terminar (usado para ler vários itens em sequência). */
    fun speak(ctx: Context, id: String, text: String, onDone: () -> Unit) {
        stop()
        init(ctx)
        val t = tts ?: run { onDone(); return }
        speakingId = id
        doneCb = onDone
        if (!ready) { pending = id to text; return }
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun init(ctx: Context) {
        if (tts != null) return
        tts = TextToSpeech(ctx.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale("pt", "BR")
                tts?.setSpeechRate(0.92f)
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) = finished(utteranceId)
                    @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) = finished(utteranceId)
                })
                pending?.let { (id, text) -> tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) }
            } else finished(speakingId)
            pending = null
        }
    }

    /** Fala (ou para, se já estiver falando este mesmo id). */
    fun toggle(ctx: Context, id: String, text: String) {
        init(ctx)
        if (speakingId == id) { stop(); return }
        val t = tts ?: return
        speakingId = id
        if (!ready) { pending = id to text; return }
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stop() { doneCb = null; pending = null; runCatching { tts?.stop() }; speakingId = null }
}

/**
 * Lê uma sequência de partes: a voz gravada por alguém do sistema quando existe
 * (mais natural), senão a leitura automática do texto. Um item = uma parte; "ouvir tudo" = várias.
 */
object Narrator {
    sealed class Part {
        data class Rec(val path: String) : Part()
        data class Tts(val text: String) : Part()
    }

    /** Parte de um texto com voz opcional: usa a gravação se o arquivo existir. */
    fun part(voice: String?, text: String): Part? =
        if (voice != null && File(Store.filesDir, voice).exists()) Part.Rec(voice)
        else text.takeIf { it.isNotBlank() }?.let { Part.Tts(it) }

    var playingId by mutableStateOf<String?>(null)
        private set
    private var queue: List<Part> = emptyList()
    private var idx = 0
    private var mp: MediaPlayer? = null
    private var appCtx: Context? = null

    /** Começa (ou para, se já estiver tocando este id). */
    fun toggle(ctx: Context, id: String, parts: List<Part?>) {
        if (playingId == id) { stop(); return }
        stop()
        val ps = parts.filterNotNull()
        if (ps.isEmpty()) return
        appCtx = ctx.applicationContext; queue = ps; idx = 0; playingId = id
        AudioPlay.stop()
        next()
    }

    private fun next() {
        val c = appCtx ?: return
        val id = playingId ?: return
        if (idx >= queue.size) { playingId = null; return }
        when (val p = queue[idx++]) {
            is Part.Rec -> {
                val ok = runCatching {
                    val m = MediaPlayer()
                    m.setDataSource(File(Store.filesDir, p.path).path)
                    m.setOnCompletionListener {
                        runCatching { it.release() }
                        if (mp === it) { mp = null; if (playingId == id) next() }
                    }
                    m.prepare(); m.start()
                    mp = m
                }.isSuccess
                if (!ok) next()
            }
            is Part.Tts -> Speech.speak(c, "narrator", p.text) { if (playingId == id) next() }
        }
    }

    fun stop() {
        val m = mp; mp = null
        runCatching { m?.stop() }; runCatching { m?.release() }
        if (Speech.speakingId == "narrator") Speech.stop()
        playingId = null; queue = emptyList()
    }
}
