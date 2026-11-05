# Kuromaku Reader 2.22.1 — Perbaikan Shinigami

## Status dan akar masalah
Perbaikan diterapkan pada source terbaru. Tombol daftar chapter sebelumnya berlabel statis "Offline", bukan hasil pemeriksaan file. Reader tidak menampilkan error ketika Glide gagal; pengukuran gambar panjang dan ukuran decoding juga tidak dikendalikan secara memadai. HTTP/decoding sumber berhasil diuji, tetapi ini bukan bukti seluruh gambar tampil pada perangkat Android.

## Perubahan dan file
Semua Java berikut berada di `app/src/main/java/id/shuyasal/kuromaku/shinigami/`:
- `ShinigamiReaderActivity.java`: pemuatan bitmap Glide bertahap, ukuran decoding terbatas, tinggi sel proporsional, loading/error/Coba Lagi per halaman; kegagalan satu gambar tidak mengosongkan reader. Daftar URL tersimpan digunakan hanya ketika terjadi kegagalan koneksi, bukan untuk mengatasi CAPTCHA/403. Progres halaman/fullscreen dipertahankan.
- `ShinigamiPageView.java` (baru): sel gambar dengan placeholder terlihat, ukuran sesuai rasio gambar, pesan error dan retry.
- `ShinigamiImageRequest.java` (baru): transport OkHttp untuk Glide, Referer situs yang valid, validasi HTTPS/host/redirect, status HTTP, MIME image dan signature JPEG/PNG/GIF/WebP; timeout dan batas ukuran. Tidak mengarang cookie atau mencatat token, cookie, dan URL bertanda tangan.
- `ShinigamiImageZoomActivity.java` (baru): zoom khusus Shinigami memakai transport yang sama, bitmap dibatasi, loading/error/retry. Viewer Novel lama tidak diubah.
- `ShinigamiDownloader.java`: "Tersimpan offline" hanya untuk jumlah halaman yang diketahui dan semua file yang lolos pemeriksaan format, kelengkapan container dan decoding sampel; file kosong/HTML/terpotong ditolak. Status "Belum diunduh" dan "Mengunduh..." terpisah dari progres membaca. Daftar offline tidak menerima folder berisi metadata saja.
- `ShinigamiChapter.java`, `ShinigamiManga.java`, `ShinigamiDates.java` (baru): UTC epoch milliseconds dari timestamp sumber, tampilan zona waktu pengguna, label Rilis/Diperbarui sesuai metadata, fallback "Tanggal belum tersedia". Tidak memakai nomor chapter atau waktu fetch untuk mengarang tanggal.
- `ShinigamiSource.java`: cache metadata dan URL tervalidasi, jumlah halaman dan HTTP dicatat tanpa rahasia. Pencarian dapat membawa sort, genre dan status bersama-sama.
- `ShinigamiStore.java`: penambahan key metadata/UTC/URL/jumlah halaman secara aditif pada preferensi Shinigami yang sudah ada. Tidak mengganti database Room Novel, menghapus data, atau melakukan migrasi destruktif. Metadata kosong/null tidak menghapus tanggal yang sudah diketahui, progres, riwayat, favorit atau posisi baca.
- `ShinigamiDetailActivity.java`: tanggal chapter dan status lokal yang benar; status diperbarui saat kembali ke detail.
- `ShinigamiActivity.java`: kartu "Chapter terbaru" dan "Update terakhir", pilihan "Update terbaru", filter/pencarian dipertahankan, deduplikasi ID saat pagination dan perlindungan dari respons lama yang menimpa riwayat.

Konfigurasi: `app/src/main/AndroidManifest.xml` mendaftarkan zoom Shinigami; `app/build.gradle.kts` menjadi 2.22.1/versionCode 49 dengan application ID dan debug signing yang tetap.

Tes yang ditambah/diperbarui di `app/src/test/java/id/shuyasal/kuromaku/shinigami/`: `ShinigamiCatalogTest`, `ShinigamiDatesTest`, `ShinigamiDownloadStateTest`, `ShinigamiImageRequestTest`, `ShinigamiImageTransportTest`, `ShinigamiImageZoomTest`, `ShinigamiPageViewTest`, `ShinigamiDetailTest`, `ShinigamiReaderTest`, `ShinigamiSourceTest`, `ShinigamiStoreTest`.

Novel, Manga Voratoon WebView, Navigation Drawer, Backup/Restore, tema, dan pengaturan bersama tidak diedit untuk perbaikan ini. Unduhan permanen Shinigami tetap dinonaktifkan karena izin sumber belum terverifikasi; cache pembacaan bukan jaminan chapter tersimpan offline.

## Pengujian yang benar-benar dilakukan
### Suite otomatis dan build
Perintah:

```sh
/home/ubuntu/tools/gradle-8.9/bin/gradle testDebugUnitTest assembleDebug --offline --console=plain
```

Hasil final: **BUILD SUCCESSFUL**. XML: **52 kelas, 495 tes terdaftar, 494 lulus, 0 gagal, 0 error, 1 dilewati**. Tes yang dilewati adalah `ShinigamiLiveTest`, bersifat jaringan opt-in. Pengujian HTTP nyata di bawah dilakukan terpisah, bukan dianggap sebagai hasil tes yang dilewati.

Cakupan JVM/Robolectric: regresi Novel/Voratoon/drawer/backup, urutan URL, lazy binding, ukuran gambar panjang, loading/error/retry sel, fullscreen dan restore progres halaman, tanggal/zona waktu/fallback, refresh metadata tanpa reset progres, kelengkapan file offline, serta filter/sort/pagination katalog.

Tes transport menggunakan interceptor OkHttp: respons tertunda, koneksi terputus, 403 tidak diulang, redirect ke host/HTTP tak valid ditolak, cancellation tidak memulai request, Referer valid tanpa cookie palsu. Ini simulasi terkontrol, bukan uji koneksi lambat/terputus pada HP nyata.

### Konten nyata: semua halaman pada sembilan chapter
Probe API publik, daftar chapter berhalaman, detail chapter, HTTP gambar dan decoding Pillow dilakukan tanpa kredensial. Hasil per halaman ada di `SHINIGAMI-LIVE-EVIDENCE.json`.

| Judul | Chapter awal / tengah / terbaru | Halaman diuji dan berhasil HTTP 200 + MIME image + decoding |
|---|---|---|
| Miss Ji-Hye, The Uncool Adult | 1 / 14 / 26 | 15 / 11 / 9 |
| Nano Machine | 1 / 169 / 337 | 26 / 16 / 25 |
| Necromancer Academy’s Genius Summoner | 1 / 112 / 223 | 20 / 18 / 16 |

Total **156/156 gambar** lolos HTTP dan decoding, termasuk halaman pertama sampai terakhir setiap chapter terpilih. Timestamp `release_date`, `updated_at` dan `latest_chapter_time` tersedia pada respons yang diperiksa. Bukti ini menguji konten sumber, **bukan membuktikan rendering Glide di Android atau seluruh katalog**.

### Verifikasi APK
- `app/build/outputs/apk/debug/app-debug.apk`: 5.709.609 byte.
- `apksigner verify`: berhasil.
- `aapt2 dump badging`: `id.shuyasal.kuromaku`, versionName 2.22.1, versionCode 49, minimum API 26.
- `git diff --check`: lulus.

## Batasan dan pengujian perangkat yang belum dilakukan
`adb devices` tidak menemukan perangkat. Instalasi/update dari APK lama, rendering seluruh gambar di HP, pinch-zoom, scrolling panjang, orientasi, perilaku fullscreen sistem, serta koneksi lambat/terputus pada perangkat nyata belum diuji. Restore data lama diuji lewat tes otomatis, bukan upgrade instalasi nyata. Progres masih per halaman/persentase, bukan offset piksel di dalam gambar panjang.

Akses API bukan izin reader pihak ketiga. Integrasi tetap eksperimental, unduhan permanen tetap disabled. CAPTCHA/autentikasi/paywall/pembatasan akses tidak dilewati. Perubahan API/CDN di masa depan tetap dapat menyebabkan error yang perlu ditangani. Login Google Voratoon tidak diuji manual pada pengerjaan ini dan tidak diklaim berhasil hanya karena build sukses.
