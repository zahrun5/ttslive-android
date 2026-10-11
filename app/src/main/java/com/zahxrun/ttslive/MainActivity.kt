package com.zahxrun.ttslive

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zahxrun.ttslive.ui.CariKataScreen
import com.zahxrun.ttslive.ui.Family100Screen
import com.zahxrun.ttslive.ui.GameScreen
import com.zahxrun.ttslive.ui.SambungKataScreen
import com.zahxrun.ttslive.ui.SetupScreen
import com.zahxrun.ttslive.ui.SusunKataScreen
import com.zahxrun.ttslive.ui.TtsScreen
import com.zahxrun.ttslive.ui.TtsTheme

class MainActivity : ComponentActivity() {

    // Android 13+: tanpa izin ini notifikasi status live tidak tampil (service tetap jalan).
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            TtsTheme {
                val vm: LiveViewModel = viewModel()
                // Kalau live masih jalan di service, langsung masuk ke layar game lagi.
                var inGame by rememberSaveable { mutableStateOf(vm.isLive) }

                if (inGame) {
                    // Back hanya menutup layar; live tetap jalan di service. Pakai tombol Exit / Stop untuk berhenti.
                    BackHandler { inGame = false }
                    when (vm.selectedGame) {
                        GameMode.FAMILY_100 -> Family100Screen(vm = vm, onExit = {
                            vm.stopLive(); inGame = false
                        })
                        GameMode.HITUNG_CEPAT -> GameScreen(vm = vm, onExit = {
                            vm.stopLive(); inGame = false
                        })
                        GameMode.CARI_KATA -> CariKataScreen(vm = vm, onExit = {
                            vm.stopLive(); inGame = false
                        })
                        GameMode.SUSUN_KATA -> SusunKataScreen(vm = vm, onExit = {
                            vm.stopLive(); inGame = false
                        })
                        GameMode.SAMBUNG_KATA -> SambungKataScreen(vm = vm, onExit = {
                            vm.stopLive(); inGame = false
                        })
                        GameMode.TTS -> TtsScreen(vm = vm, onExit = {
                            vm.stopLive(); inGame = false
                        })
                    }
                } else {
                    SetupScreen(
                        vm = vm,
                        onStartLive = { vm.startLive(); inGame = true },
                        onResumeGame = { inGame = true },
                        onStartTest = { vm.saveSettings(); vm.stopLive(); inGame = true },
                    )
                }
            }
        }
    }
}
