2.78.0 - Anime Server 2: riwayat tonton, unduh episode, menu hasil unduhan, dan penyebutan seragam

Lanjutan dari 2.77.0, semuanya soal Anime Server 2 dan Manga Server 3.

Anime Server 2 — halaman awal
- Tombol kembali pindah ke kiri. Sebelumnya menu samping yang menempati posisi
  kiri, sementara kembali ada di kanan. Sekarang: kembali di kiri, lalu judul.
- Menu titik tiga di kanan berisi "Tema tampilan", "Genre", "Muat ulang",
  "Hasil unduhan", dan "Menu sumber" — susunan yang sama dengan Anime Server 1.
- Tombol hasil unduhan berdiri sendiri di kiri titik tiga, membuka daftar
  episode yang sudah tersimpan di perangkat.
- Genre dibaca dari sumbernya sendiri (115 genre) dan menampilkan anime per
  genre; bila sumber tidak menyediakan genrenya, layar mengatakannya apa adanya.
- Ganti tema berlaku di tempat: daftar yang sudah tampil dan kata kunci
  pencarian tidak hilang.

Anime Server 2 — riwayat tontonan (baru)
- Pemutar mencatat episode yang benar-benar diputar, lalu beranda menampilkan
  rail "Lanjutkan Menonton" di paling atas.
- Dicatat per anime, bukan per episode: menonton episode 4 menggantikan episode
  3 di entri yang sama, bukan menambah judul kedua.
- Riwayat disimpan terpisah dari riwayat Anime Server 1. Penanda anime dari dua
  sumber berbeda, jadi menggabungkannya membuat satu sumber menimpa yang lain.
- Rail tetap tampil walau sumber sedang tidak bisa dihubungi, karena riwayat
  dibaca dari berkas lokal.
- Di daftar episode, episode terakhir yang ditonton ditandai "✓ Ditonton".

Anime Server 2 — unduhan (baru)
- Setiap episode di daftar punya tombol "Unduh". Server episode diperiksa di
  latar belakang, berkas media pertama yang benar-benar tersedia dipilih, lalu
  unduhan diantrekan — tanpa membuka pemutar lebih dulu.
- Pemutar juga punya "Unduh episode" di menu titik tiga, untuk server yang
  sedang diputar.
- Episode yang berkasnya sudah ada ditandai "✓ Tersimpan", dan unduhan yang
  sudah selesai tidak diantrekan ulang.
- Bila episode hanya menyediakan halaman pemutar, tombolnya menyebut alasan itu.
  Tidak ada unduhan palsu dan tidak ada berkas kosong yang diklaim selesai.

Penyebutan
- "Anime Streaming" menjadi "Anime Server 1" di halaman awal, menu samping, dan
  judul halaman. Nama sumber di dalam kode tidak diubah, jadi riwayat dan
  unduhan yang sudah ada tetap terbaca.

Manga Server 3
- Daftar katalog kini memuat chapter terbaru. Penyebabnya: kartu katalog Komiku
  memuat DUA baris chapter — "Awal: Chapter 1" lalu "Terbaru: Chapter 10" — dan
  pembaca kartunya mengambil yang pertama, sehingga yang tersimpan selalu
  chapter satu. Sekarang "Terbaru:" yang dibaca.

Anime Server 2 — pemutar (dari 2.77.0, disertakan)
- Server, layar penuh, rotasi, dan unduh ada di menu titik tiga; daftar server
  bisa ditutup dengan tombol yang sama; tombol kembali menutup layar penuh lebih
  dulu.

Yang tidak diubah
- Pemutaran otomatis saat episode dibuka: tetap berjalan seperti sebelumnya.
- applicationId, kunci penandatanganan, ID konten, dan seluruh data pengguna.
- Sumber lain: Novel, Manga Server 1-2, Anime Server 1.
- Riwayat dan unduhan Anime Server 1 tidak disentuh.

Pengujian
- Seluruh unit test Android dijalankan.
- Tes baru mengetuk kontrol yang benar-benar dipakai pembaca: titik tiga membuka
  dan menutup menu, tombol Server menutup daftarnya, tombol Rotasi membalik
  orientasi lalu kembali, posisi kembali/titik tiga/unduhan di header, rail
  riwayat muncul hanya bila ada tontonan, dan pencatatan riwayat per anime.
- Untuk Manga Server 3, tes memakai fragmen katalog asli dari situs: 10 judul,
  semua punya chapter, dan yang terambil "Chapter 10" bukan "Chapter 1".
