# Kuromaku Reader 2.22.0 — Laporan Shinigami

## Status
Modul native Shinigami ditambahkan langsung pada proyek yang ada. Integrasi masih eksperimental, bukan klaim bahwa seluruh situs atau izin penggunaan pihak ketiga telah terverifikasi. Laporan ini mencatat verifikasi lokal sebelum publikasi; status build GitHub Actions diperiksa terpisah.

## Perubahan
- Drawer bersama lima entri: Novel, Manga Voratoon, Manga Shinigami, Backup progres membaca, Restore progres membaca; panel kiri 82%, scrim 0.55, swipe kiri, Back, tap di luar, tema dan insets.
- Voratoon tetap MangaActivity berbasis WebView. Perubahan pada halaman Voratoon hanya integrasi menu Shinigami, bukan migrasi reader atau penggantian fitur sumber/fullscreen/OAuth.
- ShinigamiActivity: katalog grid cover/judul, pencarian, urutan terbaru/terpopuler, informasi chapter terbaru, pagination, riwayat, favorit, dan pengaturan domain.
- ShinigamiDetailActivity: cover, sinopsis, genre/status/metadata tersedia, daftar chapter berhalaman, favorit, tombol membuka reader.
- ShinigamiReaderActivity: RecyclerView vertikal, pemuatan gambar bertahap, urutan URL dari respons sumber, tautan zoom ke ImageViewerActivity, fullscreen, penyimpanan/restorasi posisi halaman dan persentase chapter.
- ShinigamiSource terpisah dari parser novel; endpoint JSON yang menghasilkan respons nyata digunakan, tanpa endpoint fiktif. Timeout, retry koneksi maksimal dua percobaan, penolakan redirect, HTTP 401/403/429, CAPTCHA/Cloudflare, respons kosong, dan struktur berubah ditangani sebagai error.
- ShinigamiConfig: domain API/situs dapat diganti; HTTPS, host tepat di bawah domain sumber, dan penolakan userinfo/port/path traversal/domain palsu.
- Identitas memakai manga_id/chapter_id, bukan indeks atau nomor chapter. Nomor chapter desimal dan nomor ganda tidak menggabungkan chapter berbeda.
- ShinigamiStore menggunakan preferensi terpisah. ReadingBackup menambah bagian opsional Shinigami; backup lama tetap diterima. Restore memvalidasi data sebelum menulis.
- Unduhan offline dinonaktifkan sebelum pembuatan berkas karena izin sumber belum terverifikasi.
- Application ID tetap id.shuyasal.kuromaku; versi 2.22.0, versionCode 48. Tidak ada migrasi destruktif atau penghapusan database/progres lama.
- Berkas yang diedit dicadangkan lokal ke backupcode_coding-high/. Backup lokal dan direktori build tidak dimasukkan ke ZIP distribusi.

## Masalah yang ditemukan dan diperbaiki
- Nomor chapter awalnya diperlakukan sebagai integer; data nyata memuat nomor desimal dan nomor sama pada ID berbeda. Model, intent, label, dan store diperbaiki memakai double serta ID sumber.
- Gambar kosong awalnya memiliki tinggi nol: seluruh 18 halaman reader terikat sekaligus, dan posisi halaman tersimpan tidak dapat dipulihkan. Placeholder setinggi viewport digunakan sampai gambar siap. Tes regresi lazy binding dan posisi tersimpan kini lulus.
- Swipe drawer tidak selalu ditangani; dispatch gesture horizontal diperbaiki pada komponen bersama.
- Kegagalan daftar chapter tidak menampilkan tombol retry; retry juga dapat menambah chapter duplikat. Tombol retry dan deduplikasi ID lintas halaman diperbaiki.
- Callback hasil jaringan pada Activity yang ditutup diabaikan agar tidak memperbarui UI yang telah dihancurkan.

## Pengujian yang benar-benar dilakukan
### Suite otomatis final
Perintah:

    /home/ubuntu/tools/gradle-8.9/bin/gradle testDebugUnitTest assembleDebug --offline --console=plain

Hasil: BUILD SUCCESSFUL. Laporan XML final berisi 45 kelas, 457 tes terdaftar, 456 lulus, 0 gagal, 0 error, 1 dilewati. Tes yang dilewati adalah ShinigamiLiveTest pada suite reguler karena tes jaringan bersifat opt-in.

Cakupan meliputi regresi novel, MangaScreenTest (WebView Voratoon), SectionDrawerTest, parser sumber/konfigurasi, data nyata sebagai fixture, detail, reader/fullscreen, lazy loading, posisi halaman/progres, store, backup Shinigami dan format lama. Ini pengujian JVM/Robolectric, bukan pengujian perangkat fisik.

### Uji jaringan langsung terpisah
Perintah:

    KUROMAKU_LIVE=1 /home/ubuntu/tools/gradle-8.9/bin/gradle testDebugUnitTest --offline --console=plain --tests '*ShinigamiLiveTest'

Hasil yang disimpan: 1 tes, 1 lulus, 0 gagal/error/dilewati. Transport produksi ShinigamiSource membaca katalog halaman 1 dan 2, pencarian "solo", detail manga, daftar chapter, dan URL gambar dari API yang dapat diakses tanpa kredensial.

Contoh nyata: Regressor Instruction Manual, manga_id 14423a1b-8d7e-41e2-af00-ba0ba655d5f9; Chapter 210, chapter_id d6cd3f14-a528-4752-a546-0d14873e9a4a; 18 URL gambar. Pencarian menghasilkan Solo Leveling : Ragnarok. Respons nyata tersimpan dalam fixture live_*.json; tes juga memeriksa urutan gambar.

Satu URL gambar pertama diambil terpisah: HTTP 200, image/webp, 248832 byte, berhasil didekode sebagai WEBP 800 x 10000. Ini bukan bukti seluruh gambar atau seluruh katalog berhasil dimuat pada Android.

### Build dan verifikasi
- APK: app/build/outputs/apk/debug/app-debug.apk.
- Ukuran APK final: 5697085 byte.
- apksigner verify: berhasil.
- aapt2 dump badging: application ID id.shuyasal.kuromaku, versionName 2.22.0, versionCode 48.
- git diff --check: lulus.
- Pemeriksaan komentar baru pada modul Java/test dan emoji UI baru: tidak ditemukan.
- APK dan ZIP diperiksa integritas ZIP; source memuat source Shinigami serta konfigurasi proyek, tanpa app/build/, backupcode, atau .planning/.

## Batasan dan hal yang belum diuji
- https://shinigami.asia/ dan https://11.shinigami.asia/ memberikan pembatasan/pemeriksaan Cloudflare saat diperiksa. Tidak ada bypass CAPTCHA, autentikasi, paywall, atau keamanan situs yang dilakukan. Struktur HTML penuh situs belum terverifikasi; adapter menggunakan respons JSON API yang dapat diakses.
- Akses API yang berhasil bukan bukti izin penggunaan konten oleh reader pihak ketiga. Izin belum dapat diverifikasi. Integrasi tetap eksperimental; download permanen tidak diaktifkan.
- Tidak ada perangkat Android yang terhubung saat diperiksa dengan adb devices. Instalasi/update tanpa kehilangan data, rendering seluruh gambar, pinch-zoom, pergantian orientasi, performa gulir panjang, dan perilaku sistem fullscreen di perangkat nyata belum diuji.
- Login Google Voratoon dan perpindahan sesi browser/WebView tidak diuji manual pada pengerjaan ini. Tidak diklaim berhasil hanya karena browser terbuka.
- Progres reader disimpan per indeks halaman dan persentase chapter; presisi offset piksel dalam gambar panjang belum tersedia.
- Ketahanan terhadap semua perubahan API/CDN belum dapat dijamin. Jika host/struktur berubah atau akses ditolak, aplikasi menampilkan kesalahan alih-alih mengarang konten atau membungkus situs penuh.
