package com.plurali.shared

import android.util.Base64
import org.json.JSONObject

/**
 * Convite de companion (vai no QR ou como texto). Contém a chave de leitura,
 * então deve ser enviado só para a pessoa certa.
 */
data class Invite(
    val dbUrl: String,
    val apiKey: String,
    val shareId: String,
    val key: String,
    val systemName: String,
    val profileName: String,
) {
    fun encode(): String {
        val o = JSONObject().put("u", dbUrl).put("k", apiKey).put("s", shareId).put("x", key)
            .put("n", systemName).put("p", profileName)
        return PREFIX + Base64.encodeToString(o.toString().toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }

    companion object {
        const val PREFIX = "PLURALI1:"

        fun decode(text: String): Invite? = runCatching {
            val i = text.indexOf(PREFIX)
            val body = (if (i >= 0) text.substring(i + PREFIX.length) else text).trim().takeWhile { !it.isWhitespace() }
            val o = JSONObject(String(Base64.decode(body, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)))
            Invite(o.getString("u"), o.getString("k"), o.getString("s"), o.getString("x"), o.optString("n"), o.optString("p"))
        }.getOrNull()
    }
}
