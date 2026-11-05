# Kuromaku Reader 2.25.0 — progres unduhan, tab Unduhan, zoom gestur

## Ringkasan

Rilis ini menambahkan tiga hal yang diminta: indikator unduhan berbentuk
lingkaran yang mengikuti halaman yang benar-benar tersimpan, tab **Unduhan**
yang membaca berkas di disk (bukan status di memori), dan zoom dua/tiga ketukan
di reader Shinigami tanpa meninggalkan render bertahap (tile).

Versi: `versionCode 52`, `versionName "2.25.0"`, `applicationId
id.shuyasal.kuromaku` tidak diubah, konfigurasi signing tidak disentuh.

## 1. Indikator unduhan

- `ShinigamiDownloadRing` (baru): busur melingkar, persentase di tengah,
  centang saat 100%, tanda seru saat gagal. Animasi `ValueAnimator` 320 ms hanya
  menghaluskan transisi angka lama → angka baru; tidak ada timer yang mengarang
  progres.
- Sumber angka: `ShinigamiPageStore.percent()` menghitung `pageFile()` yang
  lolos validasi (header gambar, container utuh, decode). Bukan WorkManager
  "selesai", bukan estimasi waktu.
- Baris chapter di `ShinigamiDetailActivity` dirombak jadi dua kolom: kiri
  judul/tanggal/progres baca, kanan kolom tetap 88 dp berisi ring 42 dp, label
  status pendek, dan tombol aksi (`Unduh` / `Batal` / `Coba Lagi` / `Hapus`).
  Label tidak lagi terpotong ("Mengun…") karena teks statusnya pendek dan
  kolomnya berlebar tetap.
- Status tetap benar saat halaman ditinggalkan lalu dibuka lagi:
  `ShinigamiDownloadQueue.refreshStatus()` menyelaraskan status memori dengan
  `WorkInfo` WorkManager dan jumlah halaman nyata di disk.

## 2. Tab Unduhan

- `ShinigamiDownloads` (baru) memindai `<filesDir>/shinigami/*`, memvalidasi
  setiap halaman dengan aturan yang sama seperti saat membaca offline, lalu
  mengelompokkan per manga memakai catatan pemilik chapter
  (`ShinigamiStore.setChapterOwner`, dicatat saat mengantre).
- Unduhan lama tanpa catatan pemilik tetap tampil lewat riwayat; chapter yang
  tidak bisa dipetakan masuk grup "Chapter tanpa judul manga" dan tidak dibuang.
- Chip urutan: **Manga terbaru, Chapter terbaru, Riwayat, Favorit, Unduhan**.
- Kartu: cover, judul, jumlah chapter offline, ukuran penyimpanan. Status line
  menampilkan total manga/chapter/byte. Empty state: "Belum ada unduhan" +
  petunjuk.
- Detail bisa dibuka tanpa internet: bila katalog gagal, `renderOfflineChapters`
  menampilkan chapter yang tersimpan dan memberi banner "Mode offline".

## 3. Zoom dua/tiga ketukan

- `ShinigamiZoomView` (baru) merender halaman sebagai tile dari
  `BitmapRegionDecoder`; zoom hanya menskalakan kanvas, tidak men-decode ulang
  seluruh halaman 800×10.000. Batas zoom 4×, tile cache 6.
- Ketukan: satu → buka zoom penuh (reader), dua → 2× di titik jari, dua lagi →
  kembali 1×, tiga → 3×. `ShinigamiTaps` menunda keputusan selama 280 ms supaya
  ketukan ketiga tidak terbaca sebagai dua ketukan.
- Pinch (`ScaleGestureDetector`) dan geser (`ACTION_MOVE` + clamp) didukung.
- Reader: `ShinigamiPageView` mendeteksi ketukan lewat `onInterceptTouchEvent`
  supaya scroll daftar tetap jalan, lalu membuka `ShinigamiImageZoomActivity`
  dengan `zoom_factor` 2 atau 3 dan titik fokus. Activity zoom kini memakai
  `ShinigamiZoomView`, bukan decode bitmap penuh (`decodeFull` 8,1 MP dihapus).
  Zoom tidak menulis progres baca.

## 4. Berkas yang diubah/ditambah

Baru:
- `shinigami/ShinigamiDownloadRing.java`
- `shinigami/ShinigamiDownloads.java`
- `shinigami/ShinigamiZoomView.java`
- `shinigami/ShinigamiTaps.java`
- tes: `ShinigamiTapsTest`, `ShinigamiDownloadsTest`, `ShinigamiDownloadRingTest`,
  `ShinigamiDownloadFlowTest`

Diubah:
- `ShinigamiActivity` (mode DOWNLOADS, chip, adapter, empty state)
- `ShinigamiDetailActivity` (ring, kolom kanan, refresh status, mode offline)
- `ShinigamiPageView` (deteksi ketukan)
- `ShinigamiReaderActivity` (zoom 2×/3× + fokus)
- `ShinigamiImageZoomActivity` (tile + gestur)
- `ShinigamiStore` (pemilik chapter)
- `ShinigamiDownloadQueue` (`refreshStatus`)
- `app/build.gradle.kts` (versi)

## 5. Hasil pengujian otomatis

`gradle :app:testDebugUnitTest :app:assembleDebug --offline` →
**BUILD SUCCESSFUL**, **549 tes, 0 gagal, 0 error, 1 dilewati** (tes live opt-in).

Yang benar-benar diuji otomatis:
- Unduh 3 halaman dari transport stub: 3/3 berkas valid, `Result.complete=true`,
  progres 33/66/100 berasal dari halaman tersimpan.
- Unduh ulang tidak mengunduh lagi (0 permintaan tambahan).
- Kegagalan di tengah: halaman valid tetap ada, halaman gagal tidak dibuat,
  hasil `complete=false`.
- Pemindaian unduhan: hanya chapter dengan halaman valid yang muncul; halaman
  rusak diabaikan walau status memori bilang "SAVED".
- Unduhan lama tanpa catatan pemilik dipetakan lewat riwayat.
- Hapus unduhan tidak mengubah progres baca (42 tetap 42).
- Ring: clamp 0–100, centang hanya di state selesai, persen terakhir dipertahankan
  saat gagal.
- Ketukan: 3 ketukan cepat → 3, 2 ketukan → 2, jeda > 280 ms memulai rangkaian baru.
- Tab Unduhan kosong: empty state tampil, adapter 0 item, status "Belum ada
  chapter tersimpan".
- Kontrak UI lama tetap lulus (katalog, detail, reader offline, zoom).

## 6. Yang belum bisa diuji di sini

Tidak ada perangkat Android maupun emulator pada mesin build (`adb` tidak
tersedia), jadi poin uji berikut **belum diverifikasi di HP**:

1. Animasi ring terlihat halus di layar nyata.
2. Pindah halaman lalu kembali saat unduhan berjalan (butuh WorkManager nyata).
3. Batal unduhan lewat WorkManager nyata.
4. Chapter 100% dibuka penuh di mode pesawat.
5. Tab Unduhan pada perangkat dengan unduhan nyata.
6. Membaca offline halaman pertama sampai terakhir di mode pesawat.
7. Double-tap/triple-tap pada layar sentuh sungguhan.
8. Pinch dan geser pada layar sentuh.
9. Halaman vertikal sangat panjang di perangkat kelas bawah (memori nyata).
10. Riwayat/favorit/backup pada data pengguna nyata.

Yang bisa diklaim: jalur kode, validasi berkas, dan perilaku data sudah diuji
otomatis; sensasi gestur dan unduhan lintas proses hanya bisa dipastikan setelah
APK dipasang di perangkat.

## 7. Catatan izin

Kebijakan unduhan tetap berupa persetujuan pengguna sekali dengan teks jujur
bahwa izin tertulis sumber belum dapat diverifikasi. Tidak ada bypass login,
paywall, CAPTCHA, atau proteksi situs.
