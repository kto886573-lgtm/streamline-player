package com.example.streamline.sync

/** Каркас синхронизации. Реальный OAuth client ID добавляется в локальную конфигурацию проекта. */
data class CloudPlaylist(val id: String, val title: String, val itemCount: Int, val isFavorite: Boolean = false)
data class CloudTrack(val videoId: String, val title: String, val artist: String, val playlistId: String)

interface YouTubeSyncContract {
    suspend fun signIn(): Boolean
    suspend fun playlists(): List<CloudPlaylist>
    suspend fun playlistTracks(playlistId: String): List<CloudTrack>
    suspend fun addVideoToPlaylist(playlistId: String, videoId: String): Boolean
    suspend fun markFavorite(playlistId: String): Boolean
}
