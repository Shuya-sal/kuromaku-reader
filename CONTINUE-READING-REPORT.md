# Kuromaku Reader 2.27.0 — Lanjutkan Membaca & Tema Sistem

`versionCode 54`, `versionName "2.27.0"`, `applicationId id.shuyasal.kuromaku`
tidak berubah. Database, progres, riwayat, favorit, dan signing lama dipakai
apa adanya.

## 1. Rail "Lanjutkan Membaca"

Urutan beranda sekarang: **Toolbar → Pencarian → Lanjutkan Membaca → Kategori
(chip) → Katalog**, sesuai permintaan.

- Daftar horizontal, maksimal **8 kartu**, diambil dari `ShinigamiStore.history()`
  yang sudah diurutkan terbaru-ke-terlama oleh `recordRead` (entri baru selalu di
  indeks 0). Tidak ada penyimpanan riwayat kedua.
- Tiap kartu: cover asli (Glide), judul 2 baris, `Ch. <nomor> · <persen>%`, dan
  tombol **Lanjutkan**. Kartu utuh juga bisa diketuk.
- Persentase memakai `store.progress(chapterId)` — progres baca per chapter yang
  memang dipakai reader, bukan angka turunan.
- **Lanjutkan** membuka `ShinigamiReaderActivity` dengan `chapter_id` yang sama;
  reader sudah memulihkan posisi halaman dari `store.page(chapterId)`
  (`scrollToPosition`), jadi pengguna kembali ke halaman terakhir — bukan awal
  chapter.
- **Lihat semua** membuka daftar Riwayat yang sudah ada (`Mode.HISTORY`), plus
  chip "Riwayat" ikut aktif.
- Tanpa riwayat, seluruh bagian disembunyikan (`View.GONE`) sehingga katalog
  tetap dekat ke pencarian.
- Tinggi rail terukur **210 dp** pada layar 360 dp — tiga kartu terlihat sekaligus.
- Isi rail disegarkan di `onResume`, jadi kembali dari reader/Novel/Voratoon
  langsung menampilkan progres terbaru; data tetap ada setelah aplikasi ditutup
  atau APK diperbarui karena tersimpan di SharedPreferences.

## 2. Perbaikan yang ikut diselesaikan

- **Tanggal update tidak lagi terpotong.** Katalog diubah dari 3 kolom ke
  **2 kolom**, baris tanggal dipisah ke barisnya sendiri, dan formatnya
  diringkas lewat `ShinigamiDates.formatShort()` menjadi `Update 5 Nov 2026`
  (sebelumnya `Update terakhir: 2026-11-05 10:24` yang terpotong di kartu sempit).
- **Tema "Mengikuti Sistem"** ditambahkan (`Prefs.THEME_SYSTEM = 3` +
  `resolvedTheme()`). Semua activity menggambar memakai `resolvedTheme()`, jadi
  mode Terang, Gelap, Sepia, dan Sistem bekerja konsisten; pilihan pengguna tetap
  tersimpan sebagai nilai mentah sehingga tema sistem yang berubah tidak
  menghapus pilihan. Pemilih tema di reader kini punya empat tombol
  (Terang, Gelap, Sepia, Sistem), dan tombol tema di beranda novel berputar
  melalui empat mode.
- Label "Lanjutkan" dipaksa satu baris (`setSingleLine`) setelah render
  menemukan teks pecah menjadi "Lanjutka/n" pada kartu 92 dp; lebar kartu
  dinaikkan ke 112 dp.

## 3. Berkas yang diubah

Baru:
- `shinigami/ShinigamiActivity.java` — rail Lanjutkan Membaca (build/refresh/kartu),
  `onResume`, grid 2 kolom, baris tanggal baru
- `ShinigamiDates.formatShort()`
- `ui/Prefs.java` — `THEME_SYSTEM`, `resolvedTheme()`
- `ui/Palette.java` — tidak diubah isinya (tema sistem diselesaikan di Prefs)
- tes: `ShinigamiContinueRailTest` (9 tes), `ThemeSystemTest` (5 tes),
  `ShinigamiScreenshotTest` (render PNG)
- `res/values/strings.xml` — 7 string baru (judul rail, Lihat semua, Lanjutkan,
  format `Ch. …`, baris update)

Diubah:
- `MainActivity.java`, `MangaActivity.java`, `ReaderActivity.java`,
  `ImageViewerActivity.java`, `SourceHubActivity.java`,
  `shinigami/{ShinigamiActivity,ShinigamiReaderActivity,ShinigamiDetailActivity,ShinigamiDownloadsActivity}.java`
  — semua memakai `resolvedTheme()`
- `ReaderActivity` — empat tombol tema
- `ShinigamiCatalogTest` — asersi tanggal disesuaikan ke format ringkas
- `ShinigamiDownloadsActivityTest` — menunggu daftar benar-benar diperbarui
  (sebelumnya bergantung pada satu `idle()` sehingga kadang gagal)
- `app/build.gradle.kts` — versi 2.27.0 / 54

## 4. Hasil build & pengujian

`gradle :app:testDebugUnitTest :app:assembleDebug --offline --rerun-tasks`
→ **BUILD SUCCESSFUL**, **574 tes, 0 gagal, 1 dilewati**.
APK: `kuromaku-reader-2.27.0-debug.apk`, `versionCode 54`,
`launchable-activity id.shuyasal.kuromaku.SourceHubActivity` (tetap satu launcher).

Tes yang benar-benar dijalankan untuk fitur ini:
1. Rail tersembunyi saat belum ada riwayat.
2. Urutan terbaru dulu dan berhenti di 8 kartu (`manga-10` … `manga-3`).
3. Kartu menampilkan judul, `Ch. 7 · 58%`, tombol "Lanjutkan", dan cover.
4. Tombol Lanjutkan membuka reader dengan `chapter_id` yang benar **lalu reader
   memulihkan halaman 4** dengan 0 permintaan jaringan (chapter 12 halaman
   tersimpan di disk) — bukti syarat offline + posisi baca.
5. "Lihat semua" membuka daftar Riwayat (2 entri).
6. Riwayat tetap ada setelah activity dibuka ulang (menutup aplikasi).
7. Grid 2 kolom dan baris `Update 5 Nov 2026` utuh tanpa elipsis.
8. Label kartu tidak terpotong/pecah setelah layout nyata (`EllipsisCount == 0`).
9. Rail mengikuti warna tema terang dan gelap.
10. Tema sistem: malam → gelap, siang → terang; tema eksplisit menang atas sistem.
11. Render PNG nyata dari Robolectric (graphics mode NATIVE) — dibaca OCR:
    urutan `Manga Shinigami` → `Cari judul manga` → `Lanjutkan Membaca / Lihat
    semua` → kartu `Ch. 210 · 68%` + `Lanjutkan` → chip → katalog dengan
    `Update 5 Nov 2026` utuh.

## 5. Keterbatasan

- **Tidak ada perangkat/emulator** (`adb` tidak tersedia). Uji sentuh nyata,
  cold start di HP, dan perilaku mode pesawat asli belum dijalankan; yang
  dijalankan adalah tes Robolectric + render bitmap + build APK.
- **Logo resmi Voratoon/Shinigami masih belum tersedia**: `shngm.id` tidak
  resolve dari mesin ini, `voratoon.com/favicon.ico` → 403, `favicon.png` → 404.
  Kartu sumber tetap memakai monogram V/S. Kalau Master mengirim berkas logo,
  Shuvi ganti badge monogram dengan `ImageView` tanpa mengubah layout lain.
- Background ilustrasi Source Hub & struktur Source Hub dari versi 2.26.0 tidak
  diubah pada rilis ini.
- Format tanggal ringkas mengikuti bulan berbahasa Indonesia (`5 Nov 2026`);
  pada perangkat berbahasa lain sengaja dipaksa `id-ID` agar konsisten.
