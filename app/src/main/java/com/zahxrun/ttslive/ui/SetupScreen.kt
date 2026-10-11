package com.zahxrun.ttslive.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zahxrun.ttslive.GameMode
import com.zahxrun.ttslive.LiveViewModel
import com.zahxrun.ttslive.stream.StreamState
import com.zahxrun.ttslive.tiktok.ConnState

@Composable
fun SetupScreen(
    vm: LiveViewModel,
    onStartLive: () -> Unit,
    onStartTest: () -> Unit,
    onResumeGame: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text("TTS Live", color = TextMain, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Text("Game interaktif TikTok Live", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))

        if (vm.connState != ConnState.IDLE) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Live sedang berjalan di latar belakang", fontWeight = FontWeight.SemiBold)
                    Text(vm.connDetail, color = Muted, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onResumeGame, modifier = Modifier.weight(1f)) { Text("Kembali ke game") }
                        OutlinedButton(onClick = { vm.stopLive() }, modifier = Modifier.weight(1f)) { Text("Hentikan") }
                    }
                }
            }
        }

        // Pilih game
        Text("Pilih Game", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        GameMode.entries.chunked(2).forEach { rowModes ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowModes.forEach { mode ->
                    FilterChip(
                        selected = vm.selectedGame == mode,
                        onClick = { vm.switchGame(mode) },
                        label = { Text(mode.label) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowModes.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(4.dp))

        OutlinedTextField(
            value = vm.username,
            onValueChange = { vm.username = it },
            label = { Text("Username TikTok (tanpa @)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = vm.apiKey,
            onValueChange = { vm.apiKey = it },
            label = { Text("API key EulerStream (opsional)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onStartLive,
            enabled = vm.username.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Mulai Live") }

        OutlinedButton(onClick = onStartTest, modifier = Modifier.fillMaxWidth()) {
            Text("Mode Tes (tanpa TikTok)")
        }

        Text(
            "Mode Tes: ketik komentar sendiri di layar game.",
            color = Muted,
            fontSize = 12.sp,
        )

        Spacer(Modifier.height(12.dp))
        Text("Stream video ke YouTube", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = vm.streamKey,
            onValueChange = { vm.streamKey = it },
            label = { Text("Stream key YouTube") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        val streaming = vm.streamState == StreamState.STARTING || vm.streamState == StreamState.LIVE
        if (streaming) {
            OutlinedButton(onClick = { vm.stopStream() }, modifier = Modifier.fillMaxWidth()) {
                Text("Hentikan Stream")
            }
        } else {
            Button(
                onClick = { vm.startStream() },
                enabled = vm.streamKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Mulai Stream YouTube") }
        }
        Text(vm.streamDetail, color = Muted, fontSize = 12.sp)
        Text(
            "Saat ini hanya game TTS yang bisa distream. Stream berjalan terpisah dari koneksi komentar TikTok.",
            color = Muted,
            fontSize = 12.sp,
        )
    }
}
