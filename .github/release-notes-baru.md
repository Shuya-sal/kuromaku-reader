2.74.0 - beranda Manga Server 2 mengikuti bentuk sumbernya

Beranda Manga Server 2 sekarang berbentuk sorotan di atas katalog, bukan lagi
daftar "Chapter Terbaru" berbaris.

Yang berubah
- Sorotan "Rekomendasi": banner lanskap yang bisa digeser ke samping. Isinya
  HANYA judul yang sumbernya sendiri tandai sebagai rekomendasi (penanda
  is_recommended) — sama seperti tab Rekomendasi di situs sumber. Pada halaman
  judul terbaru ada enam judul bertanda, jadi banner terisi penuh; tidak ada
  judul yang dikarang. Bila sumber tidak menandai satu pun, seluruh banner
  disembunyikan.
- Banner memakai sampul lanskap dari sumber, bukan sampul potret, jadi tidak
  terpotong. Rating hanya tampil bila sumber mengirimkannya.
- Kartu katalog jadi ringkas satu baris: "Ch. 117 · 3 jam". Waktu relatif untuk
  judul segar (baru, 5 mnt, 3 jam, 5 hr, 2 mgg, 3 bln), lalu tanggal ringkas
  (5 Nov 2026) untuk judul lama.
- Sorotan dimuat bersamaan dengan halaman pertama. Sebelumnya banner yang
  muncul belakangan menambah satu item dan menggeser seluruh katalog tepat saat
  pengguna sedang menggulir.
- Pemutar Anime Server 1 dan Anime Server 2: ketukan ganda di sisi kiri/kanan
  video melompat 10 detik, dengan label "−10 detik · 1:24". Ketukan tunggal
  tetap memunculkan/menyembunyikan kontrol seperti sebelumnya.

Yang sengaja TIDAK dibuat
- Tab "Rank Harian"/"Rank Mingguan" milik situs sumber tidak dibuat, karena API
  sumber mengabaikan parameter periode: daily, weekly, monthly, dan all
  mengembalikan daftar yang sama persis. Tombol seperti itu hanya menipu.
- Jumlah chapter tetap tidak dikarang saat sumber tidak mengirim totalnya.

Yang tidak diubah
- applicationId, kunci penandatanganan, ID konten, dan seluruh data pengguna.
- Novel, Manga Server 1, Manga Server 3, Anime Server 1, dan Anime Server 2.
- Unduhan, riwayat, serta halaman yang sudah tersimpan offline tetap terbaca.

Pengujian
- Seluruh unit test Android dijalankan.
- Tes sorotan memakai respons asli api.shinigami.io: halaman judul terbaru berisi
  24 judul, enam di antaranya bertanda rekomendasi, dan hanya enam itu yang
  muncul di banner.
