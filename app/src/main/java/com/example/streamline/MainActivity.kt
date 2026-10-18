package com.example.streamline

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.streamline.data.*
import com.example.streamline.playback.PlaybackService
import com.example.streamline.sync.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

private const val CLIENT_ID = "320919845410-f1mm3enr5smh5v9pnpnvl0vj5nugjr7h.apps.googleusercontent.com"
private val Ink = Color(0xFF0B0A10)
private val Card = Color(0xFF181620)
private val Violet = Color(0xFF9B7BFF)

class MainActivity : ComponentActivity() {
    companion object { var pendingOAuthCode by mutableStateOf<String?>(null) }
    override fun onCreate(state: Bundle?) { super.onCreate(state); handleIntent(intent); setContent { StreamlineApp() } }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent) }
    private fun handleIntent(intent: Intent?) { intent?.data?.getQueryParameter("code")?.let { pendingOAuthCode = it } }
}

@Composable
fun StreamlineApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { LocalLibraryStore(context) }
    val oauth = remember { GoogleOAuthManager(context, CLIENT_ID) }
    val api = remember(oauth.accessToken) { oauth.accessToken?.let { YouTubeApiClient(it) } }
    val resolver = remember { InnertubePlayer() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var connected by remember { mutableStateOf(oauth.connected) }
    var playlists by remember { mutableStateOf(store.loadPlaylists()) }
    var tracks by remember { mutableStateOf(store.loadTracks()) }
    var current by remember { mutableStateOf<Track?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    val addFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val next = (tracks + uris.map { Track(it.toString(), it.lastPathSegment?.substringAfterLast('/') ?: "Трек", "Мой MP3", it.toString(), isDownloaded = true) }).distinctBy { it.id }
        tracks = next; store.saveTracks(next)
    }
    LaunchedEffect(Unit) {
        val f = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        f.addListener({ controller = f.get() }, ContextCompat.getMainExecutor(context))
    }
    LaunchedEffect(MainActivity.pendingOAuthCode) {
        val code = MainActivity.pendingOAuthCode ?: return@LaunchedEffect
        MainActivity.pendingOAuthCode = null
        oauth.exchangeCode(code) { token -> connected = token != null; if (token != null) scope.launch(Dispatchers.IO) { runCatching { YouTubeApiClient(token).playlistModels() }.onSuccess { withContext(Dispatchers.Main) { playlists = it; store.savePlaylists(it) } } } }
    }
    fun play(track: Track) {
        scope.launch {
            status = "Подготавливаю воспроизведение…"
            val uri = if (track.isDownloaded || track.videoId == null) track.uri else resolver.resolveAudio(track.videoId)
            if (uri.isNullOrBlank()) { status = "Не удалось получить аудиопоток"; return@launch }
            current = track; status = null
            controller?.setMediaItem(MediaItem.Builder().setUri(Uri.parse(uri)).setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).build()).build())
            controller?.prepare(); controller?.play()
        }
    }
    fun loadPlaylist(p: CloudPlaylist) {
        val client = api ?: return
        scope.launch(Dispatchers.IO) { runCatching { client.playlistTrackModels(p.id) }.onSuccess { loaded -> withContext(Dispatchers.Main) { tracks = (tracks + loaded).distinctBy { it.id }; store.saveTracks(tracks) } }.onFailure { withContext(Dispatchers.Main) { status = it.message ?: "Ошибка загрузки плейлиста" } } }
    }
    fun downloadPlaylist(p: CloudPlaylist) {
        val client = api ?: return
        scope.launch(Dispatchers.IO) {
            val list = runCatching { client.playlistTrackModels(p.id) }.getOrElse { emptyList() }
            list.forEachIndexed { i, t ->
                val url = t.videoId?.let { resolver.resolveAudio(it) } ?: return@forEachIndexed
                runCatching { val file = File(context.filesDir, "downloads/${t.id.removePrefix("yt:")}.audio"); file.parentFile?.mkdirs(); URL(url).openStream().use { input -> file.outputStream().use { input.copyTo(it) } }; tracks = (tracks.filterNot { it.id == t.id } + t.copy(uri = Uri.fromFile(file).toString(), isDownloaded = true)); withContext(Dispatchers.Main) { downloadProgress = (i + 1f) / list.size } }
            }
            withContext(Dispatchers.Main) { store.saveTracks(tracks); status = "Плейлист скачан"; downloadProgress = 0f }
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(background = Ink, surface = Card, primary = Violet)) {
        Scaffold(containerColor = Ink, bottomBar = { NavigationBar(containerColor = Card) {
            NavigationBarItem(tab == 0, { tab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Главная") })
            NavigationBarItem(tab == 1, { tab = 1 }, icon = { Icon(Icons.Default.Download, null) }, label = { Text("Скачанные") })
            NavigationBarItem(tab == 2, { tab = 2 }, icon = { Icon(Icons.Default.Cloud, null) }, label = { Text("Google") })
        } }) { pad ->
            Column(Modifier.padding(pad).padding(horizontal = 18.dp).fillMaxSize()) {
                Spacer(Modifier.height(22.dp)); Text(when (tab) { 0 -> "Главная"; 1 -> "Скачанные"; else -> "Google Sync" }, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                status?.let { Text(it, color = Violet, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp)) }
                if (downloadProgress > 0) LinearProgressIndicator(downloadProgress, Modifier.fillMaxWidth(), color = Violet)
                Spacer(Modifier.height(12.dp))
                when (tab) {
                    0 -> HomeTab(playlists, tracks, current, ::play, ::loadPlaylist, ::downloadPlaylist, { addFiles.launch(arrayOf("audio/*")) })
                    1 -> DownloadedTab(tracks.filter { it.isDownloaded }, current, ::play)
                    2 -> GoogleTab(connected, playlists, { oauth.startSignIn() }, { oauth.clearSession { connected = false; playlists = emptyList(); store.clearGoogleData() } })
                }
                current?.let { Spacer(Modifier.height(10.dp)); PlayerBar(it, controller) }
            }
        }
    }
}

@Composable private fun ColumnScope.HomeTab(playlists: List<CloudPlaylist>, tracks: List<Track>, current: Track?, play: (Track) -> Unit, open: (CloudPlaylist) -> Unit, download: (CloudPlaylist) -> Unit, add: () -> Unit) {
    if (playlists.isNotEmpty()) { Text("Твои плейлисты", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(8.dp)); LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) { items(playlists) { p -> PlaylistRow(p, { open(p) }, { download(p) }) } } } else { EmptyState(add, "Подключи Google, чтобы увидеть плейлисты") }
    if (tracks.isNotEmpty()) { Spacer(Modifier.height(12.dp)); Text("Недавние треки", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold); tracks.take(5).forEach { TrackRow(it, it == current) { play(it) } } }
}
@Composable private fun PlaylistRow(p: CloudPlaylist, open: () -> Unit, download: () -> Unit) { Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(16.dp)).clickable { open() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.QueueMusic, null, tint = Violet, modifier = Modifier.size(36.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(p.title, color = Color.White, fontWeight = FontWeight.SemiBold); Text("${p.itemCount} треков", color = Color(0xFFAAA6B8), fontSize = 13.sp) }; IconButton(download) { Icon(Icons.Default.Download, null, tint = Violet) } } }
@Composable private fun DownloadedTab(tracks: List<Track>, current: Track?, play: (Track) -> Unit) { if (tracks.isEmpty()) EmptyState(null, "Скачанных треков пока нет") else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(tracks) { TrackRow(it, it == current) { play(it) } } } }
@Composable private fun GoogleTab(connected: Boolean, playlists: List<CloudPlaylist>, connect: () -> Unit, disconnect: () -> Unit) { Row(Modifier.fillMaxWidth().background(Color(0xFF29223E), RoundedCornerShape(18.dp)).padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(if (connected) "Google подключён" else "Google не подключён", color = Color.White, fontWeight = FontWeight.Bold); Text(if (connected) "${playlists.size} плейлистов загружено" else "Войди для синхронизации", color = Color(0xFFB7ACD8), fontSize = 12.sp) }; if (connected) OutlinedButton(disconnect) { Text("Отключить") } else Button(connect) { Text("Подключить") } } }
@Composable private fun EmptyState(onAdd: (() -> Unit)?, message: String) { Box(Modifier.fillMaxWidth().padding(top = 55.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.LibraryMusic, null, tint = Violet, modifier = Modifier.size(58.dp)); Spacer(Modifier.height(12.dp)); Text(message, color = Color(0xFFAAA6B8)); onAdd?.let { Button(onClick = it) { Text("Добавить MP3") } } } } }
@Composable private fun TrackRow(track: Track, selected: Boolean, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().background(if (selected) Color(0xFF28223C) else Card, RoundedCornerShape(16.dp)).clickable { onClick() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.MusicNote, null, tint = Violet, modifier = Modifier.size(32.dp)); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold); Text(track.artist, color = Color(0xFFAAA6B8), fontSize = 13.sp) }; if (track.isDownloaded) Icon(Icons.Default.OfflinePin, null, tint = Color(0xFF8BD6B0)) } }
@Composable private fun PlayerBar(track: Track, controller: MediaController?) { var playing by remember { mutableStateOf(false) }; Row(Modifier.fillMaxWidth().background(Color(0xFF29223E), RoundedCornerShape(18.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(track.title, color = Color.White, fontWeight = FontWeight.Bold); Text(track.artist, color = Color(0xFFB7ACD8), fontSize = 12.sp) }; IconButton({ if (playing) controller?.pause() else controller?.play(); playing = !playing }) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White) } } }

