# Kuromaku Reader 2.26.0 — Source Hub & Offline Library

`versionCode 53`, `versionName "2.26.0"`, `applicationId id.shuyasal.kuromaku`
tidak berubah, signing tidak disentuh, database/preferensi lama tetap dipakai.

## 1. Source Hub

`SourceHubActivity` (baru) kini satu-satunya launcher.

- Tiga kartu: **Novel**, **Manga Voratoon**, **Manga Shinigami**. Tiap kartu:
  monogram 52 dp di kiri, judul + satu baris keterangan, chevron di kanan,
  tinggi minimum 88 dp (di atas target sentuh 48 dp), sudut 18 dp, ripple tema.
- Latar memakai gambar lampiran yang dioptimalkan: `drawable-nodpi/source_hub_background.webp`
  (1080×1871, 208 KB) dengan `CENTER_CROP` — rasio tidak diubah, tidak dipaksa
  memenuhi layar — plus lapisan gradien gelap (#F2111318 → #E6111318) agar teks
  tetap terbaca.
- Insets status/navigation bar diterapkan; konten bisa di-scroll pada layar
  pendek; warna mengikuti `Palette` (light/dark/sepia).
- Teks diambil dari `strings.xml`, ukuran dari helper `Ui.dp` — tidak ada teks
  atau ukuran hardcoded yang tersebar.

## 2. Launcher dan navigasi

- `MAIN`/`LAUNCHER` dipindah dari `MainActivity` ke `SourceHubActivity`;
  `MainActivity` sekarang `exported="false"` tanpa intent-filter.
  Diverifikasi pada APK: `aapt2 badging` → `launchable-activity:
  id.shuyasal.kuromaku.SourceHubActivity`, hanya satu launcher.
- Kartu memakai `FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP` sehingga
  berpindah sumber tidak menumpuk activity.
- Back dari halaman sumber kembali ke Source Hub; Back dari Source Hub menutup
  aplikasi (perilaku default, tidak ada `onBackPressed` override).
- Drawer bersama (`SectionDrawer`) mendapat entri **Beranda**; ketiganya
  (Novel, Voratoon, Shinigami) membuka Source Hub dengan `CLEAR_TOP` lalu
  `finish()`, jadi tumpukan tetap bersih.

## 3. Tombol Unduhan Shinigami

- Ikon `ic_download` ditambahkan di toolbar katalog, tepat di kiri ikon
  Settings. Judul memakai `layout_weight=1` + `ellipsize`, kedua ikon 48 dp,
  jadi judul tidak bertabrakan dengan ikon pada layar sempit.
- Tombol membuka `ShinigamiDownloadsActivity`.

## 4. Halaman Unduhan

`ShinigamiDownloadsActivity` (baru) — tanpa database kedua:

- Data dari `ShinigamiDownloads.scan()` yang memindai `<filesDir>/shinigami/*`
  dan memvalidasi tiap halaman dengan aturan baca offline (header, container
  utuh, bounds, decode). Status WorkManager `SUCCEEDED` tidak dipakai sebagai
  bukti.
- Per manga: cover, judul, jumlah chapter offline + total ukuran, tombol
  **Lanjut membaca** (memakai chapter yang terakhir dibaca, jika tidak ada maka
  chapter lengkap pertama), daftar chapter (label, ukuran, persen bila parsial),
  dan tombol **Hapus unduhan** dengan dialog konfirmasi.
- Hapus memanggil `ShinigamiDownloadQueue.deleteChapter` + membersihkan status
  memori; progres baca, riwayat, favorit tidak disentuh.
- Empty state rapi bila belum ada unduhan.
- Halaman memindai ulang saat `onResume`, jadi setelah kembali dari reader
  daftar tetap benar.

## 5. Berkas baru/diubah

Baru:
- `SourceHubActivity.java`
- `shinigami/ShinigamiDownloadsActivity.java`
- `res/drawable-nodpi/source_hub_background.webp`
- tes: `SourceHubActivityTest`, `ShinigamiDownloadsActivityTest`

Diubah:
- `AndroidManifest.xml` (launcher pindah, daftar activity baru)
- `res/values/strings.xml` (label Source Hub, Unduhan, Beranda)
- `shinigami/ShinigamiActivity.java` (ikon Unduhan di toolbar)
- `ui/SectionDrawer.java` (entri Beranda + `onHome()` default)
- `MainActivity.java`, `MangaActivity.java`, `shinigami/ShinigamiNavigation.java`
  (implementasi `onHome()`)
- `app/build.gradle.kts` (versi 2.26.0 / 53)

## 6. Hasil build dan pengujian

`gradle :app:testDebugUnitTest :app:assembleDebug --offline` → **BUILD SUCCESSFUL**,
**559 tes, 0 gagal, 1 dilewati**, APK 2.26.0 terbentuk.

Diverifikasi otomatis (Robolectric):
1. Launcher: `queryIntentActivities(MAIN+LAUNCHER)` → tepat 1 activity, yaitu
   `SourceHubActivity`.
2. Tiga kartu ada dan masing-masing mengirim Intent ke `MainActivity`,
   `MangaActivity`, `ShinigamiActivity`.
3. Intent kartu memakai `CLEAR_TOP | SINGLE_TOP` (tidak menumpuk).
4. Latar: drawable lokal terpasang, `CENTER_CROP`, lapisan scrim ada.
5. Drawer punya entri `drawer_home` dan memicu `onHome()`.
6. Toolbar Shinigami: tombol unduhan ada, berada di kiri ikon Settings, lebar
   ≥ 48 dp, dan membuka `ShinigamiDownloadsActivity`.
7. Halaman Unduhan: kartu manga, judul, jumlah chapter, tombol lanjut membaca,
   baris chapter, dan tombol hapus muncul dari berkas nyata; empty state saat
   tidak ada berkas.
8. Hapus unduhan: dialog konfirmasi muncul, berkas hilang, entri hilang, dan
   progres baca (42) tetap 42.
9. Lanjut membaca membuka `ShinigamiReaderActivity` dengan `chapter_id` yang
   benar dan tidak mengubah progres.
10. Regresi: seluruh tes Novel/Voratoon/Shinigami lama tetap lulus (559 total).

## 7. Keterbatasan

- **Tidak ada perangkat/emulator** di mesin build (`adb` tidak tersedia). Jadi
  cold start nyata, Back berantai di perangkat, tampilan latar pada berbagai
  rasio layar, dan mode pesawat hanya terbukti lewat tes Robolectric + jalur
  kode, belum di HP.
- **Library Material Components tidak tersedia di cache Gradle offline**
  (`com.google.android.material` tidak ada). Alih-alih menambah dependensi yang
  akan menggagalkan build offline, kartu dibuat dengan AppCompat + drawable
  rounded/ripple/elevation bergaya Material. Bila Master ingin `MaterialCardView`
  sungguhan, dependensi perlu diunduh sekali saat build online.
- **Logo resmi Voratoon/Shinigami tidak dibundel**: lisensi aset merek tidak
  dapat dipastikan. Kartu memakai monogram berwarna (V/S) dan identitas
  Kuromaku untuk Novel. Bila Master menyediakan berkas logo resmi, tinggal
  ganti badge teks dengan `ImageView` di `SourceHubActivity.card()`.
- Uji unduhan lintas proses (WorkManager nyata) tetap tidak bisa dijalankan di
  Robolectric; yang diuji adalah berkas, validasi, dan penghapusan.
