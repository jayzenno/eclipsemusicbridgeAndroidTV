package tv.harboriptv

import android.graphics.BitmapFactory
import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

@Composable
fun PremiumChannelZapOverlay(channel: Channel?, theme: HarborTheme, modifier: Modifier = Modifier) {
    var visible by remember(channel?.id) { mutableStateOf(channel != null) }
    LaunchedEffect(channel?.id) {
        visible = channel != null
        if (channel != null) {
            kotlinx.coroutines.delay(3200)
            visible = false
        }
    }
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = .78f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (channel?.logo?.isNotBlank() == true) {
                    RemoteChannelLogo(channel.logo)
                } else {
                    Icon(Icons.Default.LiveTv, null, tint = theme.accent, modifier = Modifier.size(34.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = buildString {
                        if (channel?.number ?: 0 > 0) append("${channel?.number}  ")
                        append("LIVE")
                    },
                    color = theme.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(channel?.name ?: "", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(channel?.group ?: "Live TV", color = Color.White.copy(alpha = .58f), fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RemoteChannelLogo(url: String) {
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { URL(url).openStream().use { BitmapFactory.decodeStream(it) } }.getOrNull()
        }
    }
    AndroidView(
        factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        update = { image -> image.setImageBitmap(bitmap) },
        modifier = Modifier.size(56.dp)
    )
}