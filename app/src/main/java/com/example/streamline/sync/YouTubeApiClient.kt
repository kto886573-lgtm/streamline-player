package com.example.streamline.sync

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import com.example.streamline.data.CloudPlaylist
import com.example.streamline.data.Track

/** Минимальный HTTP-клиент YouTube Data API v3. Нужен OAuth access token пользователя. */
class YouTubeApiClient(private val accessToken: String, private val apiKey: String? = null) {
    private fun get(path: String, query: Map<String, String>): JSONObject {
        val params = query.toMutableMap(); apiKey?.let { params["key"] = it }
        val url = URL("https://www.googleapis.com/youtube/v3/$path?" + params.entries.joinToString("&") { "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" })
        val c = url.openConnection() as HttpURLConnection; c.requestMethod = "GET"; c.setRequestProperty("Authorization", "Bearer $accessToken")
        return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
    }
    fun playlists(): JSONObject = get("playlists", mapOf("part" to "snippet,contentDetails", "mine" to "true", "maxResults" to "50"))
    fun playlistItems(playlistId: String): JSONObject = get("playlistItems", mapOf("part" to "snippet,contentDetails", "playlistId" to playlistId, "maxResults" to "50"))
    fun searchVideos(query: String): JSONObject = get("search", mapOf("part" to "snippet", "q" to query, "type" to "video", "maxResults" to "10"))
    fun addToPlaylist(playlistId: String, videoId: String): Boolean {
        val url = URL("https://www.googleapis.com/youtube/v3/playlistItems?part=snippet")
        val c = url.openConnection() as HttpURLConnection; c.requestMethod = "POST"; c.doOutput = true; c.setRequestProperty("Authorization", "Bearer $accessToken"); c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(JSONObject().put("snippet", JSONObject().put("playlistId", playlistId).put("resourceId", JSONObject().put("kind", "youtube#video").put("videoId", videoId))).toString().toByteArray()) }
        return c.responseCode in 200..299
    }

    fun playlistModels(): List<CloudPlaylist> = buildList {
        val root = playlists(); val items = root.optJSONArray("items") ?: return@buildList
        for (i in 0 until items.length()) { val x = items.getJSONObject(i); add(CloudPlaylist(x.getString("id"), x.getJSONObject("snippet").optString("title", "Плейлист"), x.optJSONObject("contentDetails")?.optInt("itemCount", 0) ?: 0, x.getJSONObject("snippet").optJSONObject("thumbnails")?.optJSONObject("medium")?.optString("url"))) }
    }
    fun playlistTrackModels(playlistId: String): List<Track> = buildList {
        val items = playlistItems(playlistId).optJSONArray("items") ?: return@buildList
        for (i in 0 until items.length()) { val x = items.getJSONObject(i); val s = x.optJSONObject("snippet") ?: continue; val r = s.optJSONObject("resourceId") ?: continue; val id = r.optString("videoId"); if (id.isNotBlank()) add(Track("yt:$id", s.optString("title", "Без названия"), s.optString("videoOwnerChannelTitle", "YouTube"), "youtube:$id", id, playlistId, false)) }
    }
}
