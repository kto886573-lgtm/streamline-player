package com.example.streamline.sync

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val YOUTUBE_SCOPE = "https://www.googleapis.com/auth/youtube.readonly"
private data class PlaylistInfo(val id: String, val title: String, val count: Int)
private data class VideoInfo(val id: String, val title: String)

@Composable
fun GooglePlaylistScreen() {
    val context = LocalContext.current
    val authorizationClient = remember(context) { Identity.getAuthorizationClient(context) }
    val scope = remember { Scope(YOUTUBE_SCOPE) }
    var accessToken by remember { mutableStateOf<String?>(null) }
    var playlists by remember { mutableStateOf<List<PlaylistInfo>>(emptyList()) }
    var videos by remember { mutableStateOf<List<VideoInfo>>(emptyList()) }
    var selected by remember { mutableStateOf<PlaylistInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            message = "Вход в Google отменён"
        } else {
            try {
                val authResult = authorizationClient.getAuthorizationResultFromIntent(result.data!!)
                accessToken = authResult.accessToken
                message = null
            } catch (e: Exception) {
                message = "Ошибка Google: ${e.localizedMessage ?: e.javaClass.simpleName}"
            }
        }
    }

    LaunchedEffect(accessToken, selected?.id, refresh) {
        val token = accessToken ?: return@LaunchedEffect
        busy = true
        message = null
        try {
            val current = selected
            if (current == null) {
                playlists = withContext(Dispatchers.IO) { loadPlaylists(token) }
            } else {
                videos = withContext(Dispatchers.IO) { loadVideos(token, current.id) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = e.localizedMessage ?: "Не удалось получить данные YouTube"
        } finally {
            busy = false
        }
    }

    Column(Modifier.fillMaxWidth()) {
        if (accessToken == null) {
            Text("Подключи Google, чтобы увидеть свои YouTube-плейлисты.", color = Color.White)
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                message = null
                val request = AuthorizationRequest.builder()
                    .setRequestedScopes(listOf(scope))
                    .build()
                authorizationClient.authorize(request)
                    .addOnSuccessListener { result ->
                        if (result.hasResolution()) {
                            val pending = result.pendingIntent
                            if (pending == null) {
                                message = "Google не вернул окно подтверждения"
                            } else {
                                authLauncher.launch(
                                    IntentSenderRequest.Builder(pending.intentSender).build()
                                )
                            }
                        } else {
                            accessToken = result.accessToken
                        }
                    }
                    .addOnFailureListener { e ->
                        message = "Ошибка авторизации: ${e.localizedMessage ?: e.javaClass.simpleName}"
                    }
            }) { Text("Подключить Google") }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { selected = null }) { Text("Мои плейлисты") }
                TextButton(onClick = { refresh++ }) { Text("Обновить") }
            }
            if (selected == null) {
                Text("Плейлисты (${playlists.size})", color = Color.White)
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(playlists) { playlist ->
                        OutlinedButton(
                            onClick = { selected = playlist },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(playlist.title, color = Color.White)
                                Text("Видео: ${playlist.count}", color = Color.LightGray)
                            }
                        }
                    }
                }
                if (playlists.isEmpty() && !busy && message == null) {
                    Text("Плейлистов пока нет", color = Color.LightGray)
                }
            } else {
                Text(selected!!.title, color = Color.White)
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(videos) { video ->
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val uri = Uri.parse("https://www.youtube.com/watch?v=${video.id}")
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }
                        ) { Text(video.title, color = Color.White) }
                    }
                }
                if (videos.isEmpty() && !busy && message == null) {
                    Text("Видео в плейлисте не найдены", color = Color.LightGray)
                }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = {
                authorizationClient.revokeAccess(
                    RevokeAccessRequest.builder().setScopes(listOf(scope)).build()
                ).addOnSuccessListener {
                    accessToken = null
                    playlists = emptyList()
                    videos = emptyList()
                    selected = null
                    message = null
                }.addOnFailureListener { e ->
                    message = "Не удалось отключить: ${e.localizedMessage ?: e.javaClass.simpleName}"
                }
            }) { Text("Отключить Google") }
        }
        if (busy) {
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator()
        }
        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color(0xFFFF9999))
        }
    }
}

private fun youtubeJson(url: String, token: String): JSONObject {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 15000
    conn.readTimeout = 15000
    conn.setRequestProperty("Authorization", "Bearer $token")
    conn.setRequestProperty("Accept", "application/json")
    try {
        val status = conn.responseCode
        val body = (if (status in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) {
            val reason = runCatching {
                JSONObject(body).optJSONObject("error")?.optString("message")
            }.getOrNull() ?: body.take(250)
            error("YouTube API HTTP $status: $reason")
        }
        return JSONObject(body)
    } finally {
        conn.disconnect()
    }
}

private fun loadPlaylists(token: String): List<PlaylistInfo> {
    val found = mutableListOf<PlaylistInfo>()
    var next: String? = null
    do {
        val page = next?.let { "&pageToken=${Uri.encode(it)}" }.orEmpty()
        val json = youtubeJson(
            "https://www.googleapis.com/youtube/v3/playlists?part=snippet%2CcontentDetails&mine=true&maxResults=50$page",
            token
        )
        val array = json.optJSONArray("items")
        if (array != null) for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            found += PlaylistInfo(
                item.optString("id"),
                item.optJSONObject("snippet")?.optString("title") ?: "Без названия",
                item.optJSONObject("contentDetails")?.optInt("itemCount") ?: 0
            )
        }
        next = json.optString("nextPageToken").takeIf { it.isNotBlank() }
    } while (next != null)
    return found
}

private fun loadVideos(token: String, playlistId: String): List<VideoInfo> {
    val found = mutableListOf<VideoInfo>()
    var next: String? = null
    do {
        val page = next?.let { "&pageToken=${Uri.encode(it)}" }.orEmpty()
        val json = youtubeJson(
            "https://www.googleapis.com/youtube/v3/playlistItems?part=snippet%2CcontentDetails&playlistId=${Uri.encode(playlistId)}&maxResults=50$page",
            token
        )
        val array = json.optJSONArray("items")
        if (array != null) for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val id = item.optJSONObject("contentDetails")?.optString("videoId").orEmpty()
            if (id.isNotBlank()) found += VideoInfo(
                id,
                item.optJSONObject("snippet")?.optString("title") ?: "Без названия"
            )
        }
        next = json.optString("nextPageToken").takeIf { it.isNotBlank() }
    } while (next != null)
    return found
}
