package com.zahxrun.ttslive.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Warna disalin dari CSS overlay Hitung Cepat versi web.
val Bg1 = Color(0xFF120D22)
val Bg2 = Color(0xFF1B1233)
val SurfaceCol = Color(0xFF1D1633)
val Line = Color(0xFF322857)
val Paper = Color(0xFFF1ECDF)
val TextMain = Color(0xFFF3F1FA)
val Muted = Color(0xFF9A93B8)
val Accent = Color(0xFF8B5CF6)
val Ok = Color(0xFF34D399)
val OkDark = Color(0xFF062B1D)
val Gold = Color(0xFFF5B84A)
val Lilac = Color(0xFFB9A4FF)

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Bg1,
    surface = SurfaceCol,
    onSurface = TextMain,
    onBackground = TextMain,
)

@Composable
fun TtsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Bg1, Bg2))),
        ) {
            content()
        }
    }
}
