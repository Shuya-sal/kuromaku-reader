## 2.72.0 - Unduhan Manga Server 2 tidak lagi membuat aplikasi tersendat

### Masalahnya

Mengunduh dua sampai lima chapter sekaligus di **Manga Server 2** membuat aplikasi menjadi sangat
berat, lalu tertutup sendiri. Penyebabnya bukan jaringan, melainkan pemeriksaan gambar yang terlalu
mahal:

* Setiap kali daftar chapter digambar ulang, aplikasi memeriksa **semua halaman semua baris**.
* Pemeriksaan itu membuka berkas **dan mendekode gambarnya menjadi bitmap** — bukan sekadar membaca
  ukuran berkas.
* Dengan beberapa unduhan berjalan, pemeriksaan yang sama diulang puluhan kali per detik di main
  thread: memori habis, layar membeku, sistem menutup aplikasi.

### Perbaikannya

* **Hasil pemeriksaan diingat.** Halaman yang sudah lolos diperiksa tidak diperiksa ulang selama
  ukuran dan waktu ubah berkasnya sama. Berkas halaman tidak pernah berubah setelah selesai ditulis,
  jadi ingatan ini aman.
* **Tidak ada lagi dekode bitmap hanya untuk memeriksa.** Kelengkapan gambar dipastikan dari header
  dan penanda akhir berkas (PNG `IEND`, JPEG `FFD9`, GIF, dan ukuran RIFF), ditambah pembacaan dimensi
  tanpa membuat bitmap.
* **Angka persentase memakai pemeriksaan murah**, jadi progres tetap jujur (dihitung dari halaman
  yang benar-benar ada) tanpa membebani perangkat.
* **Daftar chapter diperbarui paling banyak sekali per 400 ms.** Sebelumnya setiap halaman selesai
  langsung menggambar ulang seluruh daftar.
* **Layar Unduhan** menghitung ukuran dan jumlah halaman tanpa membuka gambar satu per satu.

### Yang tetap sama

* Batas satu transfer berjalan di seluruh aplikasi tidak diubah.
* Halaman yang terpotong, halaman galat HTML/JSON, dan respons 403/429 tetap **tidak** dihitung
  tersimpan — hanya pemeriksaannya yang kini jauh lebih murah.
* Unduhan yang sudah ada, riwayat baca, dan pengaturan pengguna tidak disentuh.
* ID aplikasi, konfigurasi signing, dan struktur basis data tidak berubah.

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
