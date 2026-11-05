# Kuromaku Reader 2.24.0 — Kualitas Gambar Shinigami & Unduhan Offline

Tanggal: 2026-11-05
Version code: 51 · versionName 2.24.0 · minSdk 26 · targetSdk 35
APK: `kuromaku-reader-2.24.0-debug.apk` (5,7 MB)
Commit: `83bac8d` (push ke `origin/main`)

---

## 1. Akar penyebab gambar pecah

Bukan RGB_565, dan bukan URL thumbnail. Yang salah adalah **geometri decode**.

Sumber (`api.shngm.io` → `assets.shngm.id`) menyajikan halaman dengan ukuran:

| Halaman chapter 210 | Ukuran piksel | Byte |
|---|---|---|
| 0 | 800 × 10000 | 248.832 |
| 1 | 800 × 9450 | 305.918 |
| 5 | 800 × 8875 | 296.292 |
| 17 | 800 × 4005 | 102.770 |

Tidak ada varian resolusi lain: `base_url_low` menunjuk host yang sama, dan
parameter `?w=1600`, `?width=1600`, `?quality=100` diabaikan server (byte
respons identik). Jadi 800 px adalah batas asli sumber, bukan sesuatu yang bisa
dinaikkan aplikasi.

Kode lama di `ShinigamiReaderActivity`:

```java
new CustomTarget<Bitmap>(width /* ≤1080 */, 4096)
... .format(PREFER_RGB_565).downsample(DownsampleStrategy.AT_MOST)
```

`AT_MOST` memakai faktor terkecil dari dua sumbu:

```
min(1080/800, 4096/10000) = 0.4096  →  bitmap 328 × 4096
```

Bitmap 328 px itu lalu direntangkan `ShinigamiPageView` ke lebar layar (1080),
yaitu pembesaran 3,3×. Di situlah gambar terlihat pecah — bukan karena warna
16-bit. `ShinigamiImageZoomActivity` memakai pola sama (1080×8192) sehingga
zoom ikut lunak.

## 2. Perbaikan kualitas

- **`ShinigamiPageTiles`** — halaman dibaca sebagai potongan vertikal dengan
  `BitmapRegionDecoder`. Lebar selalu utuh (800), tinggi potongan 640–1800 px
  menurut mode. `slices()` dan `indexOf()` murni sehingga bisa diuji.
- **`ShinigamiPageView`** — permukaan gambar kustom yang hanya menggambar
  potongan yang terlihat (`canvas.getClipBounds()`), cache 3 potongan, recycle
  saat view dilepas. Halaman 10.000 px tidak lagi menjadi satu bitmap 32 MB.
- **`ShinigamiQuality`** — pengaturan pengguna:

  | Mode | Lebar target | Piksel | Potongan |
  |---|---|---|---|
  | Otomatis | min(layar, sumber) | RGB_565 bila > 6 MP | 1280 px |
  | Hemat Data | min(layar, 720, sumber) | RGB_565 | 640 px |
  | HD / Kualitas Asli | lebar sumber (800) | ARGB_8888 | 1600 px |

  Tidak ada upscaling di atas lebar sumber; `targetWidth()` mengembalikan
  `sourceWidth` untuk HD meski layar 1440 px.
- **`ShinigamiPageCache`** — byte halaman diunduh sekali ke `cacheDir`
  (SHA-1 nama berkas, tulis `.tmp` → rename, validasi header + dimensi, prune
  96 MB). Menggulir balik atau membuka ulang tidak mengunduh ulang.
- **Zoom** — decode resolusi asli dengan batas 8,1 MP; bila heap tidak cukup,
  fallback RGB_565 pada batas separuh, bukan langsung gagal.

## 3. Akar penyebab unduhan gagal

Dua lapis, dan keduanya nyata:

1. **`ShinigamiDownloader.download()` adalah stub** yang selalu melempar:
   `"Unduhan offline dinonaktifkan: izin reader pihak ketiga dari sumber belum
   terverifikasi."` Jadi meski policy dibuka, tidak ada kode yang menulis satu
   berkas pun.
2. **`ShinigamiDownloadPolicy.allowed()` mengembalikan field statis `false`**
   yang hanya bisa diubah `unlockForTesting()` — tidak ada jalur produksi untuk
   membukanya, sehingga seluruh tombol unduh berhenti di dialog.

Worker WorkManager, `ShinigamiPageStore`, dan `ShinigamiDownloadStatus` sudah
lengkap; yang hilang adalah implementasi `download()` dan jalur izin.

## 4. Perbaikan unduhan

- **`download()` diimplementasikan**: per halaman → lewati yang sudah valid
  (resume) → unduh → validasi header/MIME/container → tulis `.tmp` → fsync →
  rename. Retry sekali untuk gangguan jaringan; 401/403/429 berhenti segera
  karena itu pembatasan sumber, bukan gangguan. Hasil mengembalikan `Result`
  (bukan exception) agar status chapter tetap konsisten; progres = jumlah
  halaman yang benar-benar tersimpan.
- **Policy = persetujuan pengguna, bukan flag tersembunyi.** Sebelum unduhan
  pertama, aplikasi menjelaskan: gambar diambil dari server publik tanpa
  melewati login/DRM/CAPTCHA; halaman syarat layanan sumber tidak dapat
  diakses sehingga izin tertulis tidak bisa dipastikan; berkas disimpan untuk
  pemakaian pribadi dan tidak untuk disebarkan ulang. Persetujuan disimpan di
  `Prefs` (`shinigami_offline_consent`). Tanpa persetujuan, tidak ada berkas
  yang ditulis dan chapter tetap terbaca online.
  Shuvi **tidak** mengklaim sumber memberi izin — itu belum terbukti.
- Hapus unduhan tetap hanya menghapus berkas; `ShinigamiStore.progress/page`
  tidak disentuh (diuji).

## 5. Berkas yang diubah / ditambah

Baru:
- `shinigami/ShinigamiQuality.java`
- `shinigami/ShinigamiPageTiles.java`
- `shinigami/ShinigamiPageCache.java`
- `test/.../ShinigamiQualityTest.java`, `ShinigamiPageTilesTest.java`,
  `ShinigamiDownloaderTest.java`

Diubah:
- `ShinigamiPageView.java` (permukaan bertahap, API lama dipertahankan)
- `ShinigamiReaderActivity.java` (berkas + cache, dialog kualitas, tanpa Glide
  untuk halaman)
- `ShinigamiImageZoomActivity.java` (decode native + batas memori)
- `ShinigamiDownloader.java` (`download()` nyata, transport dapat diuji)
- `ShinigamiDownloadPolicy.java` (persetujuan, `blockedMessage` tetap memuat "izin")
- `ShinigamiDownloadWorker.java`, `ShinigamiDownloadQueue.java` (pesan/komentar)
- `ShinigamiDetailActivity.java` (dialog persetujuan sebelum enqueue)
- `app/build.gradle.kts` (2.24.0 / 51)

Tidak berubah: `applicationId`, signing, Novel + parser multi-sumber, Manga
Voratoon (WebView), drawer, fullscreen, riwayat, favorit, Backup/Restore,
progres baca.

## 6. Pengujian yang benar-benar dijalankan

`gradle :app:testDebugUnitTest :app:assembleDebug --offline`
→ **533 tes, 0 gagal, 1 dilewati** (`ShinigamiLiveTest`, tes jaringan opt-in).

19 tes baru mencakup:
- HD tidak pernah melebihi lebar sumber; Auto/Saver sesuai layar; format piksel per mode; persistensi pilihan.
- `slices()` menutupi 10.000 px tanpa baris hilang; `indexOf()` benar di batas potongan.
- Potongan hasil `decodeRegion` tetap 300 px lebar (bukan menyusut seperti AT_MOST).
- `bounds()`/`open()` menolak berkas non-gambar.
- `decodeFull` mempertahankan native di bawah anggaran dan menyusut hanya bila lewat.
- Unduh 3 halaman → semua berkas ada, status `Tersimpan offline`, progres 1,2,3.
- Resume: percobaan kedua tidak mengunduh ulang (0 panggilan tambahan).
- Berkas terpotong tidak pernah dihitung tersimpan; tidak ada `.tmp` tersisa.
- 403 berhenti tanpa retry (1 panggilan); gangguan jaringan diulang sekali (2 panggilan).
- Tanpa persetujuan: 0 panggilan jaringan, tidak ada berkas.
- Hapus unduhan: berkas hilang, progres baca 42% dan posisi halaman 5 tetap.

Bukti byte sumber nyata (di luar unit test, `curl` + `ffprobe`):
- Chapter 210: 18/18 halaman HTTP 200, container WebP utuh (RIFF size cocok), dimensi terbaca.
- Potongan halaman 0 dipotong 1600 px: tiga potongan pertama **800 × 1600** — lebar native.
- Perhitungan jalur lama pada halaman yang sama: 328 × 4096 (pembesaran 3,3×).

APK diverifikasi: `aapt2 dump badging` → `versionCode='51' versionName='2.24.0'`,
minSdk 26, targetSdk 35.

## 7. Yang belum berhasil / belum bisa dibuktikan

1. **Belum diuji di HP.** Mesin ini tidak punya `adb`, perangkat, atau emulator
   (`adb devices` kosong). Semua verifikasi bersifat Robolectric + build APK +
   bukti byte jaringan. Mode pesawat, memori nyata, dan gestur zoom belum
   dijalankan di perangkat.
2. **Izin sumber belum terbukti.** ToS/DMCA tidak dapat diakses (Cloudflare
   403). Unduhan kini berjalan setelah persetujuan pengguna, dengan penjelasan
   terbuka; bukan karena izin tertulis sudah ada.
3. **HD = 800 px.** Itu resolusi maksimum yang dikirim server. Aplikasi tidak
   mengarang detail: gambar 800 px pada layar 1080 px tetap diperbesar 1,35×.
   Yang diperbaiki adalah hilangnya 3,3× pembesaran tambahan.
4. Repo APK asli (`app-debug (19).apk`) tidak dapat dibongkar ulang ke sumber;
   perbaikan dikerjakan pada sumber terbaru di repo, bukan menambal APK.
