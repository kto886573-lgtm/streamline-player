package com.example.streamline.data

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val uri: String,
    val videoId: String? = null,
    val playlistId: String? = null,
    val isDownloaded: Boolean = uri.startsWith("file:") || uri.startsWith("content:")
)

data class CloudPlaylist(val id: String, val title: String, val itemCount: Int, val thumbnail: String? = null)
