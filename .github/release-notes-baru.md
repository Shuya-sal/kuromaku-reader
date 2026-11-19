2.76.0 - header Anime Server 2 tidak menutupi jam, namanya diseragamkan, dan pemutarnya punya layar penuh

Tiga hal di Anime Server 2, semuanya dari pemakaian sehari-hari.

Yang berubah
- Judul halaman "Anime Kompi" menjadi "Anime Server 2", sama dengan nama yang
  dipakai di halaman awal dan menu samping.
- Header tidak lagi menutupi jam dan ikon status bar. Penyebabnya: status bar
  transparan sejak dulu, tetapi layar ini satu-satunya sumber yang belum memakai
  padding sistem, jadi judul dan tombol kembali naik ke area status bar. Layar
  Novel, Manga Server 1-3, dan Anime Streaming sudah benar sejak awal.
- Pemutar Anime Server 2 sekarang punya tombol "Layar penuh", seperti pemutar
  Anime Streaming: bilah sistem disembunyikan dan tombol berganti menjadi
  "Keluar penuh". Orientasi tidak dipaksa — memutar perangkat tetap keputusan
  pembaca, dan layar penuh di posisi tegak tetap tegak.
- Tombol kembali di pemutar menutup layar penuh lebih dulu, jadi pembaca tidak
  keluar dari pemutar hanya karena ingin melihat jam.

Yang tidak diubah
- Pemutaran otomatis saat episode dibuka: tetap berjalan seperti sebelumnya.
- applicationId, kunci penandatanganan, ID konten, dan seluruh data pengguna.
- Unduhan, riwayat, serta halaman yang sudah tersimpan offline tetap terbaca.

Pengujian
- Seluruh unit test Android dijalankan.
- Tes baru mengetuk tombol "Layar penuh" yang sama dipakai pembaca, memeriksa
  label tombol berganti, memastikan tombol kembali menutup layar penuh lebih
  dulu, dan memastikan padding status bar benar-benar terpasang pada header.
