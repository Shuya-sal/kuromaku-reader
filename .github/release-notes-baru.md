2.75.0 - versi rilis di halaman awal, Server 3 bisa dibuka, Anime Server 2 di menu

Tiga keluhan yang datang bersama setelah 2.74.0.

Yang berubah
- Halaman awal menampilkan versi rilis di kanan atas, sebaris dengan nama
  "KUROMAKU READER". Nilainya dibaca dari konfigurasi build, jadi tidak pernah
  basi ketika nomor rilis dinaikkan.
- Manga Server 3 bisa dibuka dari menu samping Manga Server 2. Sebelumnya
  barisnya ada di menu tetapi tidak punya penanganan, sehingga mengetuknya
  hanya menutup menu — persis terasa seperti "Server 3 tidak bisa dibuka".
- Anime Server 2 kini ada di menu samping semua sumber, tepat setelah Anime
  Streaming, dan ditandai aktif saat sedang dibuka. Halaman Anime Server 2
  sendiri sebelumnya tidak punya menu sama sekali: setelah masuk lewat kartu
  di beranda, satu-satunya jalan keluar adalah tombol kembali.
- Teks di halaman awal diperbaiki: "Empat perpustakaan" menjadi "Enam
  perpustakaan", sesuai jumlah kartu yang memang sudah tampil.

Yang sengaja TIDAK dibuat
- Menu samping tidak diubah urutannya: Beranda, Novel, Manga Server 1-3,
  Anime Streaming, Anime Server 2, lalu cadangan dan pulihkan.

Yang tidak diubah
- applicationId, kunci penandatanganan, ID konten, dan seluruh data pengguna.
- Unduhan, riwayat, serta halaman yang sudah tersimpan offline tetap terbaca.
- Pemutar, katalog, dan tata letak sumber mana pun.

Pengujian
- Seluruh unit test Android dijalankan.
- Tes baru mengetuk kontrol yang benar-benar dipakai pembaca: kartu di beranda
  dan baris menu di drawer, termasuk memastikan baris menu yang tampak tidak
  diam saja saat diketuk.
