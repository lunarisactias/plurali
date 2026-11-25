package com.plurali.shared

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Onde guardar a sessão anônima do Firebase (cada app guarda do seu jeito). */
interface TokenStore {
    var uid: String?
    var refreshToken: String?
}

class FirebaseError(msg: String, val code: Int = 0) : IOException(msg)

/**
 * Cliente mínimo do Firebase via REST (sem SDK): login anônimo + Realtime Database.
 * Se o endereço do banco começar com http:// (simulador local), usa o mesmo host para o login.
 */
class FirebaseRest(dbUrl: String, private val apiKey: String, private val store: TokenStore) {
    private val db = dbUrl.trim().trimEnd('/')
    private val local = db.startsWith("http://")
    private val authBase = if (local) "$db/identitytoolkit.googleapis.com" else "https://identitytoolkit.googleapis.com"
    private val tokenBase = if (local) "$db/securetoken.googleapis.com" else "https://securetoken.googleapis.com"

    private var idToken: String? = null
    private var idTokenExp = 0L

    val uid: String? get() = store.uid

    private fun http(method: String, url: String, body: String? = null, form: Boolean = false): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15_000
        c.readTimeout = 90_000 // backups grandes demoram para o servidor processar
        if (body != null) {
            val bytes = body.toByteArray()
            c.doOutput = true
            c.setFixedLengthStreamingMode(bytes.size) // envia direto, sem copiar tudo de novo na memória
            c.setRequestProperty("Content-Type", if (form) "application/x-www-form-urlencoded" else "application/json")
            c.outputStream.use { it.write(bytes) }
        }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().decodeToString() } ?: ""
        c.disconnect()
        return code to text
    }

    private fun errorMessage(text: String): String = runCatching {
        val o = JSONObject(text)
        o.optJSONObject("error")?.optString("message") ?: o.optString("error", text)
    }.getOrDefault(text).take(200)

    /** Garante um token válido (cria conta anônima na primeira vez). */
    @Synchronized
    fun token(force: Boolean = false): String {
        val now = System.currentTimeMillis()
        if (!force && idToken != null && now < idTokenExp) return idToken!!
        val refresh = store.refreshToken
        if (refresh != null) {
            val (code, text) = http(
                "POST", "$tokenBase/v1/token?key=$apiKey",
                "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refresh, "UTF-8"), form = true,
            )
            if (code in 200..299) {
                val o = JSONObject(text)
                idToken = o.getString("id_token")
                idTokenExp = now + (o.optString("expires_in", "3600").toLongOrNull() ?: 3600) * 1000 - 60_000
                store.refreshToken = o.optString("refresh_token", refresh)
                store.uid = o.optString("user_id", store.uid ?: "")
                return idToken!!
            }
            if (code != 400) throw FirebaseError("Login: ${errorMessage(text)}", code)
            // token inválido: cria outra conta anônima abaixo
        }
        val (code, text) = http("POST", "$authBase/v1/accounts:signUp?key=$apiKey", """{"returnSecureToken":true}""")
        if (code !in 200..299) throw FirebaseError("Login anônimo: ${errorMessage(text)}", code)
        val o = JSONObject(text)
        idToken = o.getString("idToken")
        idTokenExp = now + (o.optString("expiresIn", "3600").toLongOrNull() ?: 3600) * 1000 - 60_000
        store.refreshToken = o.getString("refreshToken")
        store.uid = o.getString("localId")
        return idToken!!
    }

    private fun call(method: String, path: String, body: String? = null): Pair<Int, String> {
        var r = http(method, "$db/$path.json?auth=${token()}", body)
        if (r.first == 401) r = http(method, "$db/$path.json?auth=${token(force = true)}", body)
        return r
    }

    fun put(path: String, json: String) {
        val (code, text) = call("PUT", path, json)
        if (code !in 200..299) throw FirebaseError("Envio: ${errorMessage(text)}", code)
    }

    /** null = não existe. */
    fun get(path: String): String? {
        val (code, text) = call("GET", path)
        if (code !in 200..299) throw FirebaseError("Leitura: ${errorMessage(text)}", code)
        return text.takeIf { it.isNotBlank() && it != "null" }
    }

    fun delete(path: String) {
        val (code, text) = call("DELETE", path)
        if (code !in 200..299) throw FirebaseError("Remoção: ${errorMessage(text)}", code)
    }

    companion object {
        /** Regras do Realtime Database a colar no console do Firebase. */
        val RULES = """
{
  "rules": {
    "shares": {
      "${'$'}id": {
        ".read": "auth != null",
        ".write": "auth != null && (!data.exists() || data.child('owner').val() === auth.uid)",
        ".validate": "!newData.exists() || newData.child('owner').val() === auth.uid"
      }
    }
  }
}
""".trim()
    }
}
