package com.example.streamline.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Resolves a YouTube video to an audio URL so Media3 can play it in-app. */
class InnertubePlayer {
    suspend fun resolveAudio(videoId: String): String? = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("videoId", videoId).put("context", JSONObject().put("client", JSONObject()
            .put("clientName", "WEB_REMIX").put("clientVersion", "1.20241009.01.00").put("hl", "en").put("gl", "US")))
        val c = (URL("https://music.youtube.com/youtubei/v1/player?prettyPrint=false").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 15000; readTimeout = 20000
            setRequestProperty("Content-Type", "application/json"); setRequestProperty("Origin", "https://music.youtube.com"); setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        c.outputStream.use { it.write(payload.toString().toByteArray()) }
        if (c.responseCode !in 200..299) return@withContext null
        val root = JSONObject(c.inputStream.bufferedReader().use { it.readText() }); val formats = root.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats") ?: return@withContext null
        var best: String? = null; var bitrate = 0
        for (i in 0 until formats.length()) { val f = formats.getJSONObject(i); val mime = f.optString("mimeType"); val url = f.optString("url"); val br = f.optInt("bitrate"); if (mime.startsWith("audio/") && url.isNotBlank() && br > bitrate) { best = url; bitrate = br } }
        best
    }
}
