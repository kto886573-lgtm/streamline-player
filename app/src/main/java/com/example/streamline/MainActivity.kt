package com.example.streamline

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import com.example.streamline.data.Track
import com.example.streamline.data.LocalLibraryStore
import com.example.streamline.playback.PlaybackService

private val Ink = Color(0xFF0B0A10); private val Card = Color(0xFF181620); private val Violet = Color(0xFF9B7BFF)

class MainActivity : ComponentActivity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { StreamlineApp() } } }

@Composable fun StreamlineApp() {
    val context = LocalContext.current
    val store = remember { LocalLibraryStore(context) }
    var tracks by remember { mutableStateOf(store.loadTracks()) }
    var current by remember { mutableStateOf<Track?>(null) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { val future = MediaController.Builder(context, SessionToken(context, android.content.ComponentName(context, PlaybackService::class.java))).buildAsync(); future.addListener({ controller = future.get() }, ContextCompat.getMainExecutor(context)) }
    val addFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> val next = (tracks + uris.mapIndexed { i, uri -> Track(uri.toString(), uri.lastPathSegment?.substringAfterLast('/') ?: "Трек ${i + 1}", "Мой MP3", uri.toString()) }).distinctBy { it.id }; tracks = next; store.saveTracks(next) }
    val attachFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { val next = (tracks + Track(it.toString(), it.lastPathSegment?.substringAfterLast('/') ?: "Прикреплённый трек", "Мой MP3", it.toString())).distinctBy { t -> t.id }; tracks = next; store.saveTracks(next) } }
    fun play(track: Track) { current = track; controller?.setMediaItem(MediaItem.Builder().setUri(Uri.parse(track.uri)).setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).build()).build()); controller?.prepare(); controller?.play() }
    MaterialTheme(colorScheme = darkColorScheme(background = Ink, surface = Card, primary = Violet)) {
        Scaffold(containerColor = Ink, bottomBar = { NavigationBar(containerColor = Card) { NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("Медиатека") }); NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.Download, null) }, label = { Text("Скачанные") }); NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Cloud, null) }, label = { Text("Google") }) } }) { pad ->
            Column(Modifier.padding(pad).padding(horizontal = 18.dp).fillMaxSize()) { Spacer(Modifier.height(24.dp)); Text(if (tab == 0) "Твоя музыка" else if (tab == 1) "Скачанные" else "Google Sync", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold); Text(if (tab == 1) "Все треки, доступные офлайн" else "Слушай где угодно", color = Color(0xFFAAA6B8), fontSize = 15.sp); Spacer(Modifier.height(18.dp)); when (tab) { 0 -> LibraryTab(tracks, current, ::play, { addFiles.launch(arrayOf("audio/*")) }, { attachFile.launch(arrayOf("audio/mpeg", "audio/*")) }); 1 -> DownloadedTab(tracks, current, ::play, { attachFile.launch(arrayOf("audio/mpeg", "audio/*")) }); 2 -> GoogleTab() }; current?.let { track -> Spacer(Modifier.height(12.dp)); PlayerBar(track, controller) } }
        }
    }
}

@Composable private fun LibraryTab(tracks: List<Track>, current: Track?, play: (Track) -> Unit, add: () -> Unit, attach: () -> Unit) { GoogleBanner(); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button(onClick = add, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Добавить MP3") }; OutlinedButton(onClick = attach, modifier = Modifier.weight(1f)) { Icon(Icons.Default.AttachFile, null); Spacer(Modifier.width(5.dp)); Text("Прикрепить") } }; Spacer(Modifier.height(16.dp)); Text("Медиатека", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(8.dp)); if (tracks.isEmpty()) EmptyState(add) else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(tracks) { track -> TrackRow(track, track == current) { play(track) } } } }
@Composable private fun DownloadedTab(tracks: List<Track>, current: Track?, play: (Track) -> Unit, attach: () -> Unit) { Row(Modifier.fillMaxWidth().background(Color(0xFF29223E), RoundedCornerShape(18.dp)).padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Офлайн‑медиатека", color = Color.White, fontWeight = FontWeight.Bold); Text("${tracks.size} файлов на устройстве", color = Color(0xFFB7ACD8), fontSize = 12.sp) }; Icon(Icons.Default.OfflinePin, null, tint = Violet) }; Spacer(Modifier.height(14.dp)); if (tracks.isEmpty()) EmptyState(attach) else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(tracks) { track -> TrackRow(track, track == current) { play(track) } } } }
@Composable private fun GoogleTab() { val context = LocalContext.current; GoogleBanner(); Spacer(Modifier.height(18.dp)); Text("Что будет доступно", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(10.dp)); listOf("Вход через Google OAuth", "Загрузка и выбор любимых YouTube‑плейлистов", "Синхронизация изменений", "Добавление найденного видео в плейлист", "Сопоставление трека с собственным MP3").forEach { Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, null, tint = Violet); Spacer(Modifier.width(10.dp)); Text(it, color = Color(0xFFD8D4E2)) } }; Spacer(Modifier.height(16.dp)); Button(onClick = { com.example.streamline.sync.GoogleOAuthManager(context, "YOUR_ANDROID_CLIENT_ID.apps.googleusercontent.com").startSignIn() }) { Icon(Icons.Default.Login, null); Spacer(Modifier.width(8.dp)); Text("Подключить Google") } }
@Composable private fun GoogleBanner() { Row(Modifier.fillMaxWidth().height(78.dp).background(Brush.linearGradient(listOf(Color(0xFF3A2764), Color(0xFF1C1830))), RoundedCornerShape(22.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column { Text("Google Sync", color = Color.White, fontWeight = FontWeight.SemiBold); Text("Плейлисты и избранное будут синхронизированы", color = Color(0xFFD0C6F8), fontSize = 12.sp) }; Spacer(Modifier.weight(1f)); Icon(Icons.Default.Cloud, null, tint = Violet) } }
@Composable private fun EmptyState(onAdd: () -> Unit) { Box(Modifier.fillMaxWidth().padding(top = 70.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.LibraryMusic, null, tint = Violet, modifier = Modifier.size(58.dp)); Spacer(Modifier.height(12.dp)); Text("Здесь пока нет треков", color = Color(0xFFAAA6B8)); Button(onClick = onAdd) { Text("Выбрать MP3") } } } }
@Composable private fun TrackRow(track: Track, selected: Boolean, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().background(if (selected) Color(0xFF28223C) else Card, RoundedCornerShape(16.dp)).clickable { onClick() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).background(Violet.copy(alpha = .25f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = Violet) }; Spacer(Modifier.width(14.dp)); Column { Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold); Text(track.artist, color = Color(0xFFAAA6B8), fontSize = 13.sp) }; Spacer(Modifier.weight(1f)); Icon(Icons.Default.OfflinePin, null, tint = Color(0xFF8BD6B0)) } }
@Composable private fun PlayerBar(track: Track, controller: MediaController?) { var playing by remember { mutableStateOf(false) }; Row(Modifier.fillMaxWidth().background(Color(0xFF29223E), RoundedCornerShape(18.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(track.title, color = Color.White, fontWeight = FontWeight.Bold); Text(track.artist, color = Color(0xFFB7ACD8), fontSize = 12.sp) }; IconButton(onClick = { if (playing) controller?.pause() else controller?.play(); playing = !playing }) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White) } } }
