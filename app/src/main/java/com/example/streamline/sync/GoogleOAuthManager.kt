package com.example.streamline.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import org.json.JSONObject

class GoogleOAuthManager(private val context: Context, private val clientId: String) {
    private val redirectUri = "com.example.streamline:/oauth2redirect"
    private var verifier: String? = null

    fun startSignIn() {
        val bytes = ByteArray(32); SecureRandom().nextBytes(bytes)
        verifier = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier!!.toByteArray()), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val uri = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "openid email https://www.googleapis.com/auth/youtube.force-ssl")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("access_type", "offline")
            .build()
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    fun exchangeCode(code: String, onResult: (String?) -> Unit) {
        Thread {
            val body = "code=${enc(code)}&client_id=${enc(clientId)}&redirect_uri=${enc(redirectUri)}&grant_type=authorization_code&code_verifier=${enc(verifier ?: "")}"
            val c = URL("https://oauth2.googleapis.com/token").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write(body.toByteArray()) }
            val text = (if (c.responseCode in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
            onResult(runCatching { JSONObject(text).getString("access_token") }.getOrNull())
        }.start()
    }
    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
