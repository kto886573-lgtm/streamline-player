package com.example.streamline.sync

import com.example.streamline.data.CloudPlaylist

data class CloudTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val playlistId: String
)

interface YouTubeSyncContract {
    suspend fun signIn(): Boolean

    suspend fun playlists(): List<CloudPlaylist>

    suspend fun playlistTracks(playlistId: String): List<CloudTrack>

    suspend fun addVideoToPlaylist(
        playlistId: String,
        videoId: String
    ): Boolean

    suspend fun markFavorite(
        playlistId: String
    ): Boolean
}