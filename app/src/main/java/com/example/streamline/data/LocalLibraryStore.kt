package com.example.streamline.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LocalLibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences("streamline_library", Context.MODE_PRIVATE)
    fun loadTracks(): List<Track> = runCatching {
        val arr = JSONArray(prefs.getString("tracks", "[]"))
        (0 until arr.length()).map { val o = arr.getJSONObject(it); Track(o.getString("id"), o.getString("title"), o.getString("artist"), o.getString("uri"), o.optString("videoId").ifBlank { null }, o.optString("playlistId").ifBlank { null }, o.optBoolean("isDownloaded", false)) }
    }.getOrDefault(emptyList())
    fun saveTracks(tracks: List<Track>) { val arr = JSONArray(); tracks.forEach { arr.put(JSONObject().apply { put("id", it.id); put("title", it.title); put("artist", it.artist); put("uri", it.uri); put("videoId", it.videoId ?: ""); put("playlistId", it.playlistId ?: ""); put("isDownloaded", it.isDownloaded) }) }; prefs.edit().putString("tracks", arr.toString()).apply() }

    fun loadPlaylists(): List<CloudPlaylist> = runCatching {
        val arr = JSONArray(prefs.getString("playlists", "[]")); (0 until arr.length()).map { val o = arr.getJSONObject(it); CloudPlaylist(o.getString("id"), o.getString("title"), o.optInt("itemCount"), o.optString("thumbnail").ifBlank { null }) }
    }.getOrDefault(emptyList())
    fun savePlaylists(items: List<CloudPlaylist>) { val arr = JSONArray(); items.forEach { arr.put(JSONObject().apply { put("id", it.id); put("title", it.title); put("itemCount", it.itemCount); put("thumbnail", it.thumbnail ?: "") }) }; prefs.edit().putString("playlists", arr.toString()).apply() }
    fun clearGoogleData() { prefs.edit().remove("playlists").apply() }
}
