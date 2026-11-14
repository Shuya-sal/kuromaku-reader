## 2.71.0 - Sumber anime kedua, dengan server berkas langsung

### Yang baru

* **Kartu "Anime Server 2" di halaman sumber.** Sumber anime kedua, terpisah dari Anime Streaming.
  Sumber lama tidak diubah sama sekali: alamat, tata letak, unduhan, dan riwayatnya tetap.
* **Beranda per bagian**: Episode Terbaru, Populer Minggu Ini, Sedang Tayang, dan Film, lengkap
  dengan pencarian.
* **Pemutar sendiri**, bukan peramban: daftar episode membuka pemutar aplikasi.
* **Server ditandai jujur.** Hanya server yang memberi berkas video yang diputar:
  * dua server berkas langsung - berkas `.mp4` 720p;
  * server lain hanya halaman pemutar, jadi ditolak dengan alasan jelas dan tombol
    "Buka player sumber". Layar hitam tidak pernah ditampilkan sebagai "berhasil".
* **Televisi**: layar penuh sejak awal, kontrol hilang sendiri saat video berjalan dan muncul
  lagi pada setiap tekanan tombol, kiri/kanan menggeser 10 detik saat kontrol tersembunyi,
  dan tombol kembali menyembunyikan kontrol dulu lalu keluar.

### Kenapa sumber ini ditambahkan

Server video sumber anime pertama sedang bermasalah (beberapa mirror membalas galat dan hanya
menyisakan halaman pemutar). Sumber kedua menyediakan berkas `.mp4` langsung, jadi anime bisa
benar-benar diputar tanpa perantara pemutar beriklan.

### Yang diuji

* 14 tes baru: pembacaan beranda, pencarian, detail (episode terbaru dulu), penandaan server
  berkas, pengubahan tautan server menjadi alamat berkas, penolakan halaman pemutar, penyampaian
  galat sumber, serta layar ponsel dan televisi.
* Tes penandaan server terbukti gagal saat perbaikannya dilepas sementara.

### Catatan rilis ini

Versi @VERSION@ (@VERSION_CODE@), dibangun @BUILD_DATE@ dari commit @COMMIT@.
