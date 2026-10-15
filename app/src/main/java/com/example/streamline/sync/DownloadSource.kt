package com.example.streamline.sync

data class DownloadCandidate(val title: String, val artist: String, val downloadUrl: String, val sourceName: String)
interface DownloadSource { suspend fun search(title: String, artist: String): List<DownloadCandidate> }
/** Источник подключается только если отдаёт прямую разрешённую ссылку на аудиофайл. */
interface DirectAudioDownloader { suspend fun download(candidate: DownloadCandidate, destinationName: String): String }
