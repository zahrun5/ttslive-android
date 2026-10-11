# TTS Live (Android native) — fase 1: Hitung Cepat

Port Kotlin dari `tts-live-v2`. Semua jalan di HP: engine game, skor, dan koneksi TikTok
(lewat WebSocket EulerStream). Tidak butuh server Node/MongoDB.

## Isi proyek
- `engine/` — Kotlin murni: kontrak `Game`, `HitungCepatGame`, `Room` (skor, avatar emoji, alur komentar → jawaban → skor, jeda papan hasil).
- `tiktok/` — `EulerClient` (WebSocket + reconnect 5x) dan `EventParser` (ambil komentar dari JSON).
- `ui/` — Jetpack Compose: layar setup, layar game (8 kartu soal, leaderboard, ticker chat, papan hasil ronde).
- `LiveViewModel` — penghubung; skor tersimpan di SharedPreferences.

## Build lewat GitHub Actions (paling gampang)
1. Push folder ini ke repo GitHub (branch `main`).
2. Tab **Actions** → **Build APK** → tunggu selesai.
3. Unduh artifact `tts-live-debug-apk` → `app-debug.apk` → install di HP.

## Build di Termux
```bash
pkg install openjdk-17 aapt2 gradle
# Android SDK: butuh platform android-35 + build-tools. Arahkan lewat local.properties:
echo "sdk.dir=/path/ke/android-sdk" > local.properties
# buka gradle.properties, hapus tanda # di baris android.aapt2FromMavenOverride
gradle assembleDebug
# hasil: app/build/outputs/apk/debug/app-debug.apk
```
Catatan: versi `gradle` dari pkg Termux harus 8.9 atau lebih baru untuk AGP 8.7.3.
Kalau lebih lama, pakai wrapper: `gradle wrapper --gradle-version 8.9` lalu `./gradlew assembleDebug`.
Build pertama lambat dan butuh RAM besar (≥ 4 GB).

## Cara pakai
- **Mode Tes**: ketik nama + komentar di panel bawah (tombol "Tes"), untuk coba jawaban dan skor tanpa TikTok.
- **Mulai Live**: isi username TikTok (tanpa @) dan API key EulerStream (opsional untuk tier gratis; kuota harian tetap berlaku).
- Tampilkan layar ini ke live lewat screen share / TikTok Live Studio.

## Belum ada di fase 1
- Voting `SKIP 10x` / `GANTI 10x` (komentar "skip" saat ini diabaikan).
- Avatar berjalan; kartu pemenang pakai emoji.
- Game lain (11 modul sisanya) dan rotasi game acak.
- Foreground service: kalau Android mematikan app di background, koneksi putus. Biarkan layar menyala.
- Format pesan EulerStream: parser menelusuri JSON secara longgar dan belum dites dengan live asli.
  Kalau status menampilkan "frame N • komentar terbaca 0", kirim contoh isi frame supaya parser disesuaikan.
