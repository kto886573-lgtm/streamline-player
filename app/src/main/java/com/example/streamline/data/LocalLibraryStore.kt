package com.example.streamline.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LocalLibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences("streamline_library", Context.MODE_PRIVATE)
    fun loadTracks(): List<Track> = runCatching {
        val arr = JSONArray(prefs.getString("tracks", "[]"))
        (0 until arr.length()).map { val o = arr.getJSONObject(it); Track(o.getString("id"), o.getString("title"), o.getString("artist"), o.getString("uri")) }
    }.getOrDefault(emptyList())
    fun saveTracks(tracks: List<Track>) { val arr = JSONArray(); tracks.forEach { arr.put(JSONObject().apply { put("id", it.id); put("title", it.title); put("artist", it.artist); put("uri", it.uri) }) }; prefs.edit().putString("tracks", arr.toString()).apply() }
}
