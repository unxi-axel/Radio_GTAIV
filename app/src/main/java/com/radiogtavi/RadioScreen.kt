package com.radiogtavi

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

private val Pink = Color(0xFFFF2E93)
private val Cyan = Color(0xFF00E5FF)
private val Dark1 = Color(0xFF0B0014)
private val Dark2 = Color(0xFF2A0845)

@Composable
fun RadioScreen(vm: RadioViewModel, onLogin: () -> Unit) {
    val needsLogin by vm.needsLogin.collectAsStateWithLifecycle()
    val stations by vm.stations.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    val now by vm.nowPlaying.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Dark2, Dark1)))
    ) {
        when {
            needsLogin -> LoginView(onLogin, status)
            stations.isEmpty() -> Centered("Chargement des stations...")
            else -> WheelView(stations, current, now, vm)
        }
    }
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
    }
}

@Composable
private fun LoginView(onLogin: () -> Unit, status: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("RADIO", color = Pink, fontSize = 18.sp, letterSpacing = 8.sp)
        Text("GTA VI", color = Cyan, fontSize = 56.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onLogin,
            colors = ButtonDefaults.buttonColors(containerColor = Pink)
        ) { Text("Se connecter a Spotify") }
        Spacer(Modifier.height(16.dp))
        Text(status, color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun WheelView(stations: List<Playlist>, current: Int, now: NowPlaying?, vm: RadioViewModel) {
    var dragX by remember { mutableStateOf(0f) }
    val n = stations.size
    fun at(offset: Int) = stations[((current + offset) % n + n) % n]

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(current, n) {
                detectHorizontalDragGestures(
                    onDragStart = { dragX = 0f },
                    onHorizontalDrag = { _, d -> dragX += d },
                    onDragEnd = {
                        if (dragX < -120) vm.next() else if (dragX > 120) vm.prev()
                        dragX = 0f
                    }
                )
            }
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("RADIO GTA VI", color = Pink, fontSize = 14.sp, letterSpacing = 6.sp)
        Text("${current + 1} / $n", color = Color.Gray, fontSize = 12.sp)

        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (n > 1) Logo(at(-1), 90, 0.45f) { vm.prev() }
            Spacer(Modifier.width(14.dp))
            Logo(at(0), 230, 1f, glow = true) {}
            Spacer(Modifier.width(14.dp))
            if (n > 1) Logo(at(1), 90, 0.45f) { vm.next() }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            at(0).name.uppercase(), color = Color.White, fontSize = 28.sp,
            fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
            maxLines = 2, modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(now?.title ?: "...", color = Cyan, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp))
        Text(now?.artist ?: "", color = Color.LightGray, fontSize = 14.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))

        Spacer(Modifier.weight(1f))
        Text("Glisse pour changer de station", color = Color.Gray, fontSize = 11.sp)
    }
}

@Composable
private fun Logo(p: Playlist, size: Int, alpha: Float, glow: Boolean = false, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (glow) 1f else 0.9f, label = "s")
    AsyncImage(
        model = p.imageUrl, contentDescription = p.name,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size.dp)
            .scale(scale)
            .alpha(alpha)
            .clip(CircleShape)
            .border(BorderStroke(if (glow) 4.dp else 2.dp, if (glow) Pink else Cyan), CircleShape)
            .clickable { onClick() }
    )
}
