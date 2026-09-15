package tv.harboriptv

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HarborApp(applicationContext) }
    }
}

data class HarborTheme(val name: String, val background: Color, val surface: Color, val accent: Color, val glow: Color, val wallpaper: Int)

private val themes = listOf(
    HarborTheme("Harbor", Color(0xFF080A10), Color(0xCC171B26), Color(0xFF7DD3FC), Color(0xFF38BDF8), 0),
    HarborTheme("Aurora", Color(0xFF090713), Color(0xCC1C1429), Color(0xFFA78BFA), Color(0xFF22D3EE), 1),
    HarborTheme("OLED", Color.Black, Color(0xDD111111), Color.White, Color(0xFF8B5CF6), 2),
    HarborTheme("Sunset", Color(0xFF12090A), Color(0xCC241518), Color(0xFFFF9F68), Color(0xFFFF5C8A), 3)
)

@Composable
fun HarborApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("harbor", Context.MODE_PRIVATE) }
    var themeIndex by rememberSaveable { mutableIntStateOf(prefs.getInt("theme", 0)) }
    var channels by remember { mutableStateOf(sampleChannels) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet()) ?: emptySet()) }
    var activeGroup by rememberSaveable { mutableStateOf("Alle") }
    var showSettings by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val theme = themes[themeIndex.coerceIn(themes.indices)]
    val groups = remember(channels) { listOf("Alle") + channels.map { it.group.ifBlank { "Live TV" } }.distinct().sorted() }
    val visibleChannels = if (activeGroup == "Alle") channels else channels.filter { it.group.ifBlank { "Live TV" } == activeGroup }
    val player = remember { ExoPlayer.Builder(context).setHandleAudioBecomingNoisy(true).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    LaunchedEffect(Unit) {
        val m3u = prefs.getString("m3u", "").orEmpty()
        val xtream = prefs.getString("xtream", "").orEmpty()
        val user = prefs.getString("user", "").orEmpty()
        val pass = prefs.getString("pass", "").orEmpty()
        if (m3u.isNotBlank() || (xtream.isNotBlank() && user.isNotBlank())) {
            loading = true
            status = "Gespeicherte Playlist wird geladen …"
            try {
                val loaded = withContext(Dispatchers.IO) {
                    if (m3u.isNotBlank()) IptvRepository.parseM3u(IptvRepository.fetchText(m3u))
                    else IptvRepository.loadXtream(xtream, user, pass)
                }
                if (loaded.isNotEmpty()) { channels = loaded; status = "${loaded.size} Sender bereit" }
                else status = "Gespeicherte Playlist ist leer"
            } catch (e: Exception) { status = "Playlist konnte nicht geladen werden" }
            finally { loading = false }
        }
    }

    LaunchedEffect(selected, channels) {
        channels.getOrNull(selected)?.url?.takeIf { it.isNotBlank() }?.let { url ->
            player.setMediaItem(MediaItem.fromUri(url)); player.prepare(); player.playWhenReady = true
        }
    }

    Box(Modifier.fillMaxSize().background(wallpaper(theme))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 54.dp, vertical = 30.dp)) {
            Header(theme, { showSettings = true }, { showSearch = true })
            Spacer(Modifier.height(16.dp))
            HeroPlayer(player, channels.getOrNull(selected), theme, Modifier.fillMaxWidth().height(330.dp))
            Spacer(Modifier.height(18.dp))
            GroupRail(groups, activeGroup, theme) { activeGroup = it }
            Spacer(Modifier.height(16.dp))
            SectionTitle("Favoriten", Icons.Default.Favorite, theme)
            ChannelRail(channels.filter { it.id in favorites }.ifEmpty { visibleChannels.take(8) }, channels.getOrNull(selected)?.id, theme,
                onSelect = { c -> selected = channels.indexOfFirst { it.id == c.id }.coerceAtLeast(0) },
                onFavorite = { c -> favorites = toggleFavorite(c, prefs) })
            Spacer(Modifier.height(14.dp))
            SectionTitle(if (activeGroup == "Alle") "Alle Sender" else activeGroup, Icons.Default.LiveTv, theme)
            ChannelRail(visibleChannels, channels.getOrNull(selected)?.id, theme,
                onSelect = { c -> selected = channels.indexOfFirst { it.id == c.id }.coerceAtLeast(0) },
                onFavorite = { c -> favorites = toggleFavorite(c, prefs) })
            if (status.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(status, color = theme.accent, fontSize = 13.sp) }
        }

        if (showSettings) SettingsDialog(prefs, themeIndex, theme, loading,
            onTheme = { themeIndex = it; prefs.edit().putInt("theme", it).apply() },
            onDismiss = { showSettings = false },
            onLoad = { m3u, xtream, user, pass ->
                loading = true; status = "Playlist wird geladen …"
                scope.launch {
                    try {
                        val loaded = withContext(Dispatchers.IO) {
                            if (m3u.isNotBlank()) IptvRepository.parseM3u(IptvRepository.fetchText(m3u))
                            else if (xtream.isNotBlank() && user.isNotBlank()) IptvRepository.loadXtream(xtream, user, pass)
                            else emptyList()
                        }
                        if (loaded.isNotEmpty()) {
                            channels = loaded; selected = 0; activeGroup = "Alle"
                            prefs.edit().putString("m3u", m3u).putString("xtream", xtream).putString("user", user).putString("pass", pass).apply()
                            status = "${loaded.size} Sender geladen"; showSettings = false
                        } else status = "Keine Sender gefunden"
                    } catch (e: Exception) { status = "Fehler: ${e.message?.take(90) ?: "Playlist konnte nicht geladen werden"}" }
                    finally { loading = false }
                }
            })
        if (showSearch) SearchDialog(channels, theme, { showSearch = false }) { c ->
            selected = channels.indexOfFirst { it.id == c.id }.coerceAtLeast(0); showSearch = false
        }
    }
}

private fun toggleFavorite(channel: Channel, prefs: android.content.SharedPreferences): Set<String> {
    val old = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
    val next = if (channel.id in old) old - channel.id else old + channel.id
    prefs.edit().putStringSet("favorites", next).apply(); return next
}

@Composable
private fun Header(theme: HarborTheme, onSettings: () -> Unit, onSearch: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("HARBOR", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 5.sp)
            Text("LIVE TELEVISION", fontSize = 11.sp, color = theme.accent, letterSpacing = 3.sp)
        }
        IconButton(onClick = onSearch) { Icon(Icons.Default.Search, null, tint = Color.White, modifier = Modifier.size(27.dp)) }
        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(27.dp)) }
    }
}

@Composable
private fun GroupRail(groups: List<String>, active: String, theme: HarborTheme, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(groups, key = { it }) { group ->
            val selected = group == active
            OutlinedButton(onClick = { onSelect(group) }, border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) theme.accent else Color.White.copy(alpha = .15f))) {
                Text(group, color = if (selected) theme.accent else Color.White, maxLines = 1)
            }
        }
    }
}

@Composable
private fun HeroPlayer(player: ExoPlayer, channel: Channel?, theme: HarborTheme, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(Color.Black)) {
        AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = false; keepScreenOn = true } }, modifier = Modifier.fillMaxSize())
        if (channel?.url.isNullOrBlank()) Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(theme.surface.copy(alpha = .9f), Color.Black)))) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.LiveTv, null, tint = theme.accent, modifier = Modifier.size(52.dp))
                Spacer(Modifier.height(10.dp)); Text(channel?.name ?: "Noch kein Sender", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                Text("Playlist in Einstellungen hinzufügen", color = Color.White.copy(alpha = .55f), fontSize = 15.sp)
            }
        }
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = .62f)).padding(20.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) { Text(channel?.name ?: "Kein Sender", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("Jetzt live  •  ${channel?.group ?: "Live TV"}", color = theme.accent, fontSize = 13.sp) }
            Text("LIVE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SectionTitle(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, theme: HarborTheme) {
    Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = theme.accent, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(9.dp)); Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun ChannelRail(channels: List<Channel>, selected: String?, theme: HarborTheme, onSelect: (Channel) -> Unit, onFavorite: (Channel) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        items(channels, key = { it.id }) { c -> ChannelCard(c, c.id == selected, theme, { onSelect(c) }, { onFavorite(c) }) }
    }
}

@Composable
private fun ChannelCard(channel: Channel, focused: Boolean, theme: HarborTheme, onClick: () -> Unit, onFavorite: () -> Unit) {
    var hasFocus by remember { mutableStateOf(false) }; val active = focused || hasFocus
    val scale by animateFloatAsState(if (active) 1.055f else 1f, tween(180, easing = FastOutSlowInEasing), label = "focusScale")
    val accent by animateColorAsState(if (active) theme.accent else Color.White.copy(alpha = .10f), tween(180), label = "focusColor")
    Box(Modifier.width(178.dp).height(106.dp).scale(scale).graphicsLayer { shadowElevation = if (active) 22f else 0f }.clip(RoundedCornerShape(18.dp)).background(theme.surface).border(BorderStroke(if (active) 2.dp else 1.dp, accent), RoundedCornerShape(18.dp)).onFocusChanged { hasFocus = it.isFocused }.focusable().clickable { onClick() }.padding(16.dp)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(Brush.linearGradient(listOf(theme.accent.copy(alpha = .85f), theme.glow.copy(alpha = .3f))))) { Text(channel.number.toString(), color = Color.Black, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.Center)) }
                Spacer(Modifier.weight(1f)); Icon(Icons.Default.Star, null, tint = if (active) theme.accent else Color.White.copy(alpha = .28f), modifier = Modifier.size(18.dp).clickable { onFavorite() })
            }
            Spacer(Modifier.weight(1f)); Text(channel.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1); Text(channel.group, color = Color.White.copy(alpha = .5f), fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SettingsDialog(prefs: android.content.SharedPreferences, themeIndex: Int, theme: HarborTheme, loading: Boolean, onTheme: (Int) -> Unit, onDismiss: () -> Unit, onLoad: (String, String, String, String) -> Unit) {
    var m3u by remember { mutableStateOf(prefs.getString("m3u", "") ?: "") }
    var xtream by remember { mutableStateOf(prefs.getString("xtream", "") ?: "") }
    var user by remember { mutableStateOf(prefs.getString("user", "") ?: "") }
    var pass by remember { mutableStateOf(prefs.getString("pass", "") ?: "") }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color(0xFF141820), title = { Text("Harbor Einstellungen", color = Color.White) }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            item { Text("Playlist", color = theme.accent, fontWeight = FontWeight.Bold) }
            item { OutlinedTextField(m3u, { m3u = it }, label = { Text("M3U / M3U8 URL") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { Text("oder Xtream Codes", color = Color.White.copy(alpha = .65f)) }
            item { OutlinedTextField(xtream, { xtream = it }, label = { Text("Server URL") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(user, { user = it }, label = { Text("Benutzer") }, singleLine = true, modifier = Modifier.weight(1f)); OutlinedTextField(pass, { pass = it }, label = { Text("Passwort") }, singleLine = true, modifier = Modifier.weight(1f)) } }
            item { Text("Theme", color = theme.accent, fontWeight = FontWeight.Bold) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())) { themes.forEachIndexed { i, t -> OutlinedButton(onClick = { onTheme(i) }, border = BorderStroke(if (i == themeIndex) 2.dp else 1.dp, if (i == themeIndex) t.accent else Color.White.copy(alpha = .18f))) { Text(t.name) } } } }
        }
    }, confirmButton = { Button(enabled = !loading, onClick = { onLoad(m3u, xtream, user, pass) }) { Text(if (loading) "Lädt …" else "Playlist laden") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Schließen") } })
}

@Composable
private fun SearchDialog(channels: List<Channel>, theme: HarborTheme, onDismiss: () -> Unit, onSelect: (Channel) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = channels.filter { it.name.contains(query, true) || it.group.contains(query, true) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color(0xFF141820), title = { Text("Sender suchen", color = Color.White) }, text = {
        Column(Modifier.fillMaxWidth()) {
            OutlinedTextField(query, { query = it }, label = { Text("Sender oder Gruppe") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp)); LazyColumn(Modifier.height(300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(filtered.take(100), key = { it.id }) { c -> Text(c.name, color = Color.White, modifier = Modifier.fillMaxWidth().clickable { onSelect(c) }.padding(12.dp)) }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } })
}

private fun wallpaper(theme: HarborTheme): Brush = when (theme.wallpaper) {
    1 -> Brush.linearGradient(listOf(Color(0xFF0B0615), Color(0xFF10233D), Color(0xFF220E2F)))
    2 -> Brush.verticalGradient(listOf(Color.Black, Color(0xFF050505)))
    3 -> Brush.linearGradient(listOf(Color(0xFF160A0B), Color(0xFF321018), Color(0xFF0D0A16)))
    else -> Brush.linearGradient(listOf(Color(0xFF080A10), Color(0xFF0E1725), Color(0xFF080A10)))
}

private val sampleChannels = listOf(
    Channel("sample-1", "ARD HD", "Öffentlich-Rechtlich", "", number = 1), Channel("sample-2", "ZDF HD", "Öffentlich-Rechtlich", "", number = 2), Channel("sample-3", "RTL HD", "Privat", "", number = 3), Channel("sample-4", "ProSieben HD", "Privat", "", number = 4), Channel("sample-5", "Sat.1 HD", "Privat", "", number = 5), Channel("sample-6", "VOX HD", "Privat", "", number = 6), Channel("sample-7", "Sky Sport", "Sport", "", number = 7), Channel("sample-8", "DAZN 1", "Sport", "", number = 8)
)
