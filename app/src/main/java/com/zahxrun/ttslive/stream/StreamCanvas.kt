package com.zahxrun.ttslive.stream

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zahxrun.ttslive.GameMode
import com.zahxrun.ttslive.LiveController
import com.zahxrun.ttslive.ui.TextMain
import com.zahxrun.ttslive.ui.TtsCanvas
import com.zahxrun.ttslive.ui.TtsTheme

/** Isi video yang dilihat penonton. Dibaca langsung dari [LiveController], tanpa Activity. */
@Composable
fun StreamCanvasRoot() {
    val controller = LiveController.get(LocalContext.current)
    TtsTheme {
        Box(Modifier.fillMaxSize()) {
            if (controller.selectedGame == GameMode.TTS) {
                TtsCanvas(controller.snapshot, Modifier.fillMaxSize())
            } else {
                Text(
                    "Game ${controller.selectedGame.label} belum didukung untuk stream",
                    color = TextMain,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }
            PulseDot(Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
    }
}

/**
 * Titik kecil yang berkedip terus. Tujuannya memaksa layar virtual menghasilkan frame baru
 * tiap saat, supaya video tidak "membeku" ketika game sedang diam.
 */
@Composable
private fun PulseDot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    Box(
        modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(Color(0xFFEF4444))
            .graphicsLayer { this.alpha = alpha },
    )
}
