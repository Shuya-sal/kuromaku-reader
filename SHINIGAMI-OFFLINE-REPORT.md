# Laporan Perbaikan Shinigami — Offline Download & Progres Baca (2.23.0)

Tanggal: 2026-11-05
Versi: 2.23.0 (versionCode 50), minSdk 26, targetSdk 35
APK: `kuromaku-reader-2.23.0-debug.apk`

## 1. Pemisahan progres baca dan progres unduhan

| Data | Penyimpanan | Contoh tampilan |
|------|-------------|-----------------|
| Progres baca | `ShinigamiStore` kunci `progress:<id>` + `page:<id>` (tidak diubah) | `Dibaca 12%`, `Selesai` |
| Progres unduhan | `ShinigamiDownloadStatus` (memori + `Data` WorkManager) | `Mengunduh 48%` |
| Status unduhan | diturunkan dari validasi berkas nyata | `Belum diunduh`, `Tersimpan offline`, `Unduhan gagal` |

`ShinigamiChapterProgress.readLabel()` satu-satunya tempat angka baca menjadi teks, jadi angka 12% tidak pernah dipakai sebagai status unduhan.

## 2. Tata letak baris chapter

- Kiri: judul chapter, tanggal rilis/update (`shinigami_chapter_date`), lalu progres baca di bawahnya.
- Kanan: status unduhan (`shinigami_chapter_download_status`) + tombol aksi (`shinigami_chapter_download`): `Unduh` / `Batal` / `Ulangi` / `Hapus`.
- Kedua kolom dipisah `LinearLayout` bersarang, jadi teks tidak berdempetan.

## 3. Pengelola unduhan

- `ShinigamiDownloadQueue` (WorkManager, satu work unik per chapter, `KEEP` saat antre ganda, `REPLACE` saat ulang).
- `ShinigamiDownloadWorker`: resume per halaman, validasi tiap gambar, `setProgressAsync` berdasarkan jumlah gambar tersimpan, `Result.retry()` untuk HTTP 429 / gangguan sementara, `stop` untuk batal.
- `ShinigamiPageStore`: tulis ke `.tmp` → fsync → rename; berkas setengah jadi selalu dihapus.
- Multi-chapter: tekan lama baris chapter → bilah pilihan → `Unduh N chapter`.
- Hapus unduhan lewat dialog; hanya berkas yang dihapus, `progress:`/`page:` tidak disentuh.
- Penyimpanan: `filesDir/shinigami/<chapterId>/<index>.img` (khusus aplikasi, tanpa izin storage).

## 4. Reader offline

1. Buka chapter → kalau semua halaman tersedia & valid → baca file lokal, **tanpa** permintaan jaringan (`offlineChapter`), banner "Dibaca dari penyimpanan offline".
2. Kalau belum lengkap dan jaringan ada → reader online seperti biasa.
3. Kalau jaringan gagal dan hanya ada urutan halaman cache → banner peringatan; halaman yang ada tetap tampil.
4. Kalau tidak ada apa pun → pesan jelas: chapter belum tersimpan lengkap + progres tidak hilang.
5. Progres baca hanya ditulis saat posisi halaman sudah pasti (`positionReady`) dan urutan halaman tepercaya (`progressReliable`), sehingga restore otomatis tidak bisa mengubah 12% menjadi 100%.

## 5. Pembatasan izin sumber (tetap dipertahankan)

- `shinigami.asia` `/`, `/tos`, `/terms`, `/dmca`, `/privacy-policy` → HTTP 403 Cloudflare challenge. Tidak dilewati.
- Pencarian web tidak menemukan pernyataan izin unduhan untuk reader pihak ketiga.
- `GET https://api.shngm.io/v1/manga/list...` → 200 JSON (akses data), **bukan** izin menyimpan gambar.
- Karena itu `ShinigamiDownloadPolicy` tetap terkunci: tombol unduh menampilkan penjelasan, worker menolak jalan, dan tidak ada gambar chapter yang disimpan permanen.
- Konsekuensi jujur: skenario "unduh 1 chapter", "unduh beberapa chapter", "putus koneksi saat unduh" **belum bisa dijalankan di perangkat** karena kebijakan ini. Yang diuji adalah mesinnya (lihat bagian 6).

## 6. Pengujian yang benar-benar dijalankan

Perintah: `gradle :app:testDebugUnitTest :app:assembleDebug --offline`

Hasil: **55 berkas tes, 514 tes, 0 gagal, 0 error, 1 dilewati** (hanya `ShinigamiLiveTest`, tes jaringan opt-in).

Tes baru yang relevan:
- `ShinigamiDownloadStatusTest` (7 tes): kunci progres baca vs unduhan terpisah, label `Mengunduh 48%` / `Unduhan gagal` / `Tersimpan offline`, hapus unduhan tidak mereset progres, kebijakan terkunci menolak antrean, WorkManager `Data` membawa semua chapter terpilih, label aksi.
- `ShinigamiOfflineReaderTest` (5 tes): chapter lengkap dibaca tanpa satu pun permintaan jaringan, chapter tanpa file menampilkan pesan jelas dan progres 12% tidak berubah, hapus unduhan mempertahankan progres 67% dan posisi halaman, unduhan sebagian tidak dilaporkan `Tersimpan offline`, unduhan sebagian tetap memakai reader online saat jaringan ada.
- `ShinigamiPageStoreTest` (6 tes): halaman valid jadi file lokal, HTML/MIME salah ditolak tanpa file sisa, PNG terpotong tidak dihitung tersimpan, HTTP 403 tidak menulis apa pun, resume hanya menghitung halaman utuh (66% dari 2/3).
- `ShinigamiDetailTest` diperbarui ke kontrak baru: tanggal di view terpisah, progres baca `Selesai`, status unduhan terpisah dari tombol aksi.

Yang **tidak** diuji: perangkat/emulator fisik (tidak ada `adb` di mesin ini), jaringan seluler nyata, dan unduhan nyata dari sumber (diblokir kebijakan).

## 7. Fitur lain

Tidak ada perubahan pada Novel, Voratoon, drawer, tanggal update, riwayat, favorit, tema, atau Backup/Restore. 509 tes lama tetap lulus.
