Pembaca native untuk novel dari Kaori Translation. Dua novel bawaan
(*Monogatari no Kuromaku* dan *Magical★Explorer*) tetap ada, dan novel
lain bisa **ditambahkan sendiri lewat URL** tanpa memperbarui APK.

* **@VERSION@ — Crash pilih server, landscape yang menetap, dan kontrol auto-hide.**
  - **Crash saat memilih server diperbaiki dari akarnya.** Pilihan pengguna dulu dicari dengan `mirrors.indexOf(mirror)`. Panel yang masih terbuka bisa memegang mirror dari daftar episode sebelumnya, sehingga hasilnya `-1`; indeks itu dipakai membaca dan menulis daftar, dan penjaganya (`index < mirrors.size()`) justru **lolos** untuk `-1`. Hasilnya `IndexOutOfBoundsException: Index -1 out of bounds for length 2` — aplikasi tertutup. Sekarang setiap mirror punya **identitas kanonis** (`quality|provider|serverId|posisi`) dan tidak ada indeks mentah yang dipakai untuk menulis.
  - **Dua server pada resolusi sama tidak lagi saling menimpa.** Dulu hasil pemeriksaan disimpan per posisi di daftar, jadi 480p ODCDN dan 480p Vidhide berbagi satu kunci. Sekarang per identitas, sehingga setiap server dapat dipilih sendiri dan statusnya benar.
  - **Perpindahan server diserialisasi.** Ketukan kedua saat perpindahan berjalan diabaikan dengan pemberitahuan, bukan dijalankan bersamaan. Hasil pemeriksaan dari generasi lama dibuang (`serverGeneration`), jadi callback lama tidak menulis ke pemutar yang sudah memuat server lain.
  - **Landscape tidak lagi memantul kembali.** `setFullscreen()` dulu **selalu** menulis `SCREEN_ORIENTATION_UNSPECIFIED` di akhir, dan "Putar Layar" memanggilnya tepat setelah meminta landscape — jadi permintaan landscape dibatalkan sendiri sepersekian detik kemudian. Sekarang hanya satu metode (`applyOrientation()`) yang menulis orientasi, dan layar penuh tidak menyentuhnya sama sekali.
  - **State dipisah menjadi tiga:** `fullscreen` (immersive), `orientationMode` (`FOLLOW_DEVICE` / `FORCED_LANDSCAPE`), dan `controlsVisible`. Callback lifecycle tidak lagi menimpa pilihan pengguna; saat Activity dibuat ulang, orientasi dan layar penuh dipulihkan sebelum tata letak disusun, dan pemutar tidak di-restart.
  - **Kontrol hilang otomatis setelah 5 detik.** Satu `Handler` dan satu `Runnable` (`hideControlsTask`) — bukan beberapa timer yang berlomba. Ketukan pada permukaan video memunculkan kontrol, mengetuk lagi menyembunyikannya, dan setiap interaksi mengulang hitungan 5 detik.
  - **Timer ditahan saat penting:** selama menyeret timeline, saat panel server/dialog kualitas/subtitle/kecepatan terbuka, dan saat video dijeda atau buffering — kontrol tetap terlihat agar pengguna tidak kehilangan jalan memulihkan pemutaran. Callback dibatalkan di `onDestroy`.
  - **Galat pemutaran tidak menutup aplikasi.** Kegagalan membuat pemutar, sumber kosong, dan `onPlayerError` berakhir sebagai pesan spesifik dengan tombol **Coba Lagi**, **Pilih Server Lain**, dan **Tonton di Sumber** — bukan crash, dan Activity tidak pernah `finish()` sendiri.
  - **Timer pemutar tidak lagi menumpuk.** `startTicker()` dulu hanya membatalkan `saveTick`; setiap perpindahan server menambah satu rantai `clockTick` yang memposting dirinya sendiri. Sekarang keduanya dibatalkan lebih dulu.

* Versi: **@VERSION@** (versionCode @VERSION_CODE@) — butuh **Android 8.0 (API 26)** atau lebih baru.
* Dibangun otomatis dari commit `@COMMIT@` pada @BUILD_DATE@.
* **@VERSION@ — Pemutar video native Media3 menggantikan WebView.**
  - **WebView dihapus dari jalur menonton.** `OtakudesuPlayerActivity` (pemutar berbasis halaman web) dihapus beserta entri manifest-nya; penggantinya `AnimePlayerActivity` dengan **AndroidX Media3 ExoPlayer** dan `PlayerView`. Tidak ada banner, popup, footer, chat widget, atau tombol mirror milik website — seluruh kontrol digambar aplikasi.
  - **Sumber video diperiksa dulu, bukan ditebak.** `OtakudesuMediaResolver` memisahkan tiga hal: alamat halaman website, iframe pemutar pihak ketiga, dan berkas media yang benar-benar kompatibel (`m3u8`, `mpd`, `mp4`, `webm`). Hanya jenis terakhir yang diberikan ke Media3.
  - **Kenyataan sumber Otakudesu.** Halaman episode hanya memuat iframe `desustream` dan daftar mirror berupa ID server (`{"id":207282,"q":"480p"}`) untuk filedon, vidhide, mega, odcdn, ondesuhd, dan odstream — tidak ada satu pun alamat media. Aplikasi **tidak** menebak atau mengekstrak alamat dari mirror itu, karena berarti melewati pemutar dan monetisasi penyedia. Untuk episode seperti ini layar menampilkan penjelasan dan tombol **Tonton di Sumber** (Custom Tab), bukan layar hitam.
  - **Kontrol native:** play/pause, seek ±10 detik, timeline yang bisa digeser, layar penuh landscape (immersive, toolbar disembunyikan), pilihan kualitas dari tinggi track yang benar-benar ada, pilihan subtitle dari track teks yang benar-benar ada, kecepatan 0,5x–2x, episode sebelumnya/berikutnya, dan tombol Coba Lagi saat gagal.
  - **Kualitas tidak dikarang:** menu hanya berisi "Otomatis" plus tinggi video yang dilaporkan Media3 (`Format.height`). MP4 satu kualitas tidak menampilkan tombol 1080p palsu.
  - **Posisi menonton per episode** (`position`, `duration`, `updated`, `finished`) disimpan di `kuromaku_otakudesu`, dilanjutkan saat episode dibuka lagi, tidak mundur saat menyimpan posisi lebih kecil, dan dianggap selesai bila tersisa ≤5 detik. Rail Lanjutkan Menonton memakai persentase nyata ini lebih dulu.
  - **Satu player, tanpa tumpukan Activity:** pindah episode memakai `setMediaItem` pada player yang sama; posisi episode sebelumnya disimpan lebih dulu.
  - **Lifecycle:** player dilepas di `onDestroy`, jeda di `onPause`, buffer 15–60 detik (bukan seluruh video), dan `configChanges` mencegah pemutaran ulang saat rotasi.
  - **Bukti pengujian:** 715 tes lulus, 0 gagal, 1 tes jaringan opt-in dilewati. Tes baru: `OtakudesuMediaResolverTest` (halaman episode asli hanya mirror → tidak ada sumber; HLS/MP4 dikenali; alamat provider ditolak walau berakhiran .mp4; hanya HTTPS tanpa kredensial), `OtakudesuProgressTest` (posisi bertahan, tidak mundur, selesai di akhir), `AnimePlayerTest` (format waktu, layar tanpa sumber menampilkan pesan + Tonton di Sumber).
  - **Keterbatasan:** pemutaran video sungguhan **belum** diuji pada perangkat — Robolectric tidak punya codec, dan sumber Otakudesu tidak menyediakan berkas media yang boleh diputar aplikasi pihak ketiga. Jalur native siap dipakai begitu ada alamat media yang sah.

* **@VERSION@ — Ketukan tunggal reader yang benar-benar terbaca + Putar Layar = landscape penuh.**
  - **Akar bug "ketuk sekali tidak masuk layar penuh" ketemu.** Satu ketukan tercatat **dua kali**: `onInterceptTouchEvent` mengembalikan `true` sehingga Android mengirim sisa rangkaian sentuhan (termasuk `ACTION_UP`) ke `onTouchEvent`, dan kedua jalur itu sama-sama mencatat ketukan. Akibatnya satu ketukan dihitung sebagai dua — handler memutuskan "zoom 2×", bukan "ketuk sekali". Sekarang ada penjaga yang tidak bergantung pada urutan pemanggilan: satu `ACTION_UP` hanya dicatat sekali per rangkaian. Bukti: rangkaian ketukan sungguhan lewat `dispatchTouchEvent` kini menghasilkan tepat satu aksi ketukan tunggal (sebelumnya dua).
  - **Kotak galat tidak lagi menutupi gambar.** Tombol "Coba Lagi" dan pesan galat berada di atas daftar halaman dalam satu `FrameLayout`. Selama kotak itu tampil — termasuk pada chapter yang halaman berikutnya memang tidak ada — seluruh ketukan mendarat di tombol itu dan tidak pernah sampai ke gambar. Pesan galat kini hanya menutupi gambar bila memang tidak ada halaman yang bisa dibaca; kalau halaman sudah ada, pesannya muncul sebagai pemberitahuan kecil.
  - **Satu sumber kebenaran untuk layar penuh.** Tombol toolbar dan ketukan tunggal pada gambar memakai jalur `toggleReaderFullscreen()` yang sama, dengan keadaan `readerFullscreen` yang juga dipulihkan saat Activity dibuat ulang.
  - **"Putar Layar" kini benar-benar landscape + layar penuh.** Sebelumnya tombol ini hanya mengubah orientasi sambil menyisakan toolbar, dan pada keadaan tertentu malah mengembalikan ke portrait. Sekarang satu ketukan meminta landscape eksplisit sekaligus menyembunyikan bilah; menekannya lagi mengembalikan orientasi yang berlaku sebelumnya. Keadaan `rotatedFullscreen` terpisah dari `fullscreen`, sehingga kedua tombol bisa dipakai bergantian tanpa saling merusak.
  - **Pemutar tidak pernah di-restart.** Tidak ada pelepasan player saat mode berubah, `onConfigurationChanged` hanya menyusun ulang tata letak, dan `MediaItem`, posisi, `playWhenReady`, pilihan audio/subtitle, serta buffer tetap. Tombol Back keluar dari mode rotasi lebih dulu, lalu layar penuh, baru meninggalkan pemutar.

* **@VERSION@ — Navigasi episode yang benar arahnya, toolbar yang muat, dan layar penuh tanpa rotasi paksa.**
  - **Berikutnya/Sebelumnya tidak lagi terbalik.** Halaman episode Otakudesu menulis episode terbaru lebih dulu, jadi daftarnya menurun sementara cerita menaik. Memakai indeks daftar apa adanya membuat "Berikutnya" dari Episode 1 mati (dianggap di luar batas) dan "Berikutnya" dari Episode 3 justru mundur ke Episode 2. Daftar kini diurutkan ke urutan cerita dulu; episode tanpa nomor tidak dibuang dan tidak dibuat-buat nomornya.
  - **Toolbar pemutar tidak lagi terpotong.** Dulu lima chip (Server, Kualitas, Subtitle, 1x, Layar penuh) berbagi satu baris dengan tombol kembali dan judul, sehingga pada layar sempit chip terakhir keluar tepi kanan dan tidak bisa ditekan. Sekarang baris atas memuat kembali, judul singkat, pintasan server, dan menu tambahan; Kualitas, Subtitle, Kecepatan, dan Putar layar pindah ke menu itu dengan label jelas.
  - **Layar penuh tidak memutar perangkat.** Tombol "Layar penuh" dulu memanggil orientasi landscape paksa, jadi menonton pada posisi portrait berputar sendiri. Sekarang layar penuh hanya menyembunyikan bilah: portrait tetap portrait, dan bila perangkat diputar manual tata letak mengikuti. Pemutaran paksa tersedia terpisah sebagai **Putar layar**.
  - **Daftar server: nama dan resolusi terbaca pada baris masing-masing.** Nama server dipaksa putih sebelumnya sehingga tidak terbaca pada tema terang; kini memakai warna tema. Resolusi diulang per baris (header grup bisa tergulir keluar layar), tombol pilih berukuran minimal 48dp, dan dua server pada resolusi sama tetap dua pilihan terpisah yang bisa digulir sampai bawah.
  - **Indikator progres membaca Manga Shinigami** di bagian paling bawah reader, di atas navigation bar: garis tipis plus angka persen kecil. Persen dihitung dari jarak gulir dan panjang konten nyata — bukan dari berapa halaman yang sudah dimuat — sehingga angka tidak meloncat saat gambar berikutnya masuk; rasio dimensi halaman yang tersimpan dipakai untuk halaman yang belum dimuat. Pembaruan hanya menyentuh satu ProgressBar, tanpa `notifyDataSetChanged()` dan tanpa relayout, jadi gambar HD tidak berkedip. Bisa dimatikan di Pengaturan Manga Shinigami, dan tetap terpisah dari progres unduhan.

* **@VERSION@ — Posisi menonton tidak lagi hilang saat berganti server + pengujian lintas episode.**
  - **Ganti server tidak mengembalikan ke awal episode.** Sebelumnya memilih server lain mengosongkan posisi menonton, jadi episode yang sudah ditonton separuh mulai lagi dari nol — riwayatnya tersimpan, tetapi posisinya terbuang. Sekarang posisi diambil dari pemutar yang sedang berjalan (bila masih episode yang sama) atau dari progres tersimpan, lalu dibawa ke server baru. Riwayat, favorit, dan progres episode tetap utuh.
  - **Diverifikasi pada empat episode nyata** FX Senshi Kurumi-chan (Episode 1, 2, 3, 6). Berkas yang terbuka: **odcdn** pada keempat episode (`cdn.odcloud.net/anime/Otakudesu.io_FSKC--0N_720p.mp4`, HTTP 200 `video/mp4`, range 206), dan **odstream** pada Episode 2 (`archive.org/download/perempuan-dewasa-yang-semakindwa/Otakudesu.io_FSKC--02_720p.mp4`, HTTP 206 `video/mp4`, berkas terunduh `ISO Media, MP4`). Kelompok server yang tersedia berbeda tiap episode (8–12 mirror) dan daftarnya selalu dibaca dari halaman episode — **filedon** muncul di Episode 6, bukti bahwa tidak ada nama server yang ditulis di kode.
  - **Server yang belum kompatibel dilaporkan apa adanya:** vidhide dan mega menutup berkasnya di balik pemutar beriklan (vidhide memakai enkripsi), ondesuhd baru membentuk alamat berkas setelah pemutarnya dijalankan, dan sebagian odstream mengirim alamat yang host-nya tersusun ganda sehingga tidak bisa diminta.

* **@VERSION@ — Ketukan tunggal layar penuh yang menunggu konfirmasi + pengembalian bilah yang benar.**
  - **Ketukan tunggal tidak lagi langsung dijalankan.** Sebelumnya aksi layar penuh berjalan pada sentuhan pertama, sehingga rangkaian dua atau tiga ketukan sempat mengubah mode sebelum zoom dibuka. Kini ketukan tunggal menunggu jendela konfirmasi 280 ms — padanan `onSingleTapConfirmed()`: rangkaian yang ternyata dua ketukan (zoom 2×) atau tiga ketukan (zoom 3×) **tidak pernah** mengubah layar penuh. Tiga ketukan sudah pasti, jadi jalannya tanpa menunggu.
  - **Menggulir tidak pernah mengubah mode.** Jari yang bergeser lebih dari ambang menandai rangkaian sebagai gulir/geser dan membatalkan ketukan, jadi menggulir atau menggeser gambar yang diperbesar, sekalipun berakhir dengan ketukan, tidak menyentuh layar penuh. Ketukan pada tombol toolbar juga tetap milik toolbar.
  - **Masuk layar penuh:** bilah atas, bar navigasi chapter, status bar, dan navigation bar Android disembunyikan sekaligus (bilah sistem memakai mode sementara-lewat-geser). **Keluar:** seluruh bilah aplikasi dan bilah sistem dikembalikan, lalu padding dari window insets dipasang ulang supaya toolbar tidak tertutup status bar. Tombol kembali juga mengembalikan mode normal lebih dulu.
  - **Isi bacaan tidak tersentuh.** Bilah memakai ruang tata letaknya sendiri di luar daftar halaman, jadi beralih mode hanya menambah/mengurangi tinggi area baca: adapter tidak dibuat ulang, LayoutManager tetap, posisi halaman terlihat tidak berpindah, gambar tidak dimuat ulang, dan progres membaca tetap tersimpan. Tidak ada kedipan maupun layar hitam.
  - **Semua gestur lama tetap:** double-tap zoom, triple-tap zoom, pinch zoom, gulir vertikal, dan pan saat gambar diperbesar.

* **@VERSION@ — Jalan keluar saat server gagal + penjelasan, bukan layar hitam.**
  - **Setiap kegagalan menyebut sebabnya.** Ketika server yang dipilih tidak bisa diputar, layar tidak lagi hanya berkata "tidak ada berkas video": pesannya menyebut **nama server**, **penyebabnya** (berkas ditutup enkripsi pemutar penyedia, alamat berkas baru terbentuk setelah pemutar dijalankan, pemutar tidak memuat alamat berkas, atau berkas gagal dimuat beserta kode kesalahan ExoPlayer), lalu menunjuk dua jalan keluar.
  - **Tiga jalan keluar di kotak galat:** **Coba Server Lain** (kembali ke panel pemilih), **Buka Player Sumber** (membuka pemutar penyedia di peramban), dan **Tonton di Sumber** (halaman episode). Pemutar penyedia dibuka apa adanya — aplikasi tidak mengubah atau melewati apa pun di sana.
  - **Alamat pemutar disimpan per server.** Setelah sebuah server diperiksa, alamat pemutarnya ikut tercatat, jadi tombol "Buka Player Sumber" tetap tahu tujuannya kapan pun dipakai. Bila server mengirim berkas langsung tanpa halaman pemutar, halaman episode yang jadi tujuan.
  - **Pemeriksaan otomatis dibatasi dua server.** Tidak lagi memeriksa seluruh mirror secara agresif: hanya dua server yang paling mungkin terbuka, dan tidak menumpuk bila ada yang sedang berjalan. Sisanya menunggu sentuhan pengguna atau tombol **Periksa semua server**.
  - **Peringatan iklan sebelum membuka pemutar penyedia.** Saat membuka pemutar sumber, aplikasi memberi tahu bahwa tampilannya mengikuti penyedia dan tidak selalu bebas iklan. Pemutar native aplikasi sendiri tetap bersih: tidak ada banner, pop-up, maupun elemen HTML website.

* **@VERSION@ — Pemilih server streaming + single-tap layar penuh pada reader Manga.**
  - **Pemilihan server sebelum menonton.** Saat halaman episode tidak menyediakan berkas media langsung, pemutar membuka panel **Pilih Server Streaming**: semua mirror yang benar-benar disebut halaman episode, dikelompokkan per resolusi (720p → 480p → 360p), dengan nama server dan status pemeriksaan terpisah. Tidak ada nama server yang ditulis di kode — daftarnya selalu dibaca dari halaman episode.
  - **Status tidak disimpulkan dari nama.** "Siap diputar" hanya muncul setelah halaman pemutar server itu dibuka dan di dalamnya benar-benar tertulis alamat berkas. Selama diperiksa, barisnya menampilkan "Memeriksa…"; yang gagal atau menutup berkasnya dilaporkan apa adanya. Tombol **Periksa semua server** memeriksa sisanya sekaligus.
  - **Tombol Server di pemutar.** Bilah atas kini punya tombol **Server** untuk berpindah server kapan saja; setelah satu server dipilih, namanya tampil di tombol itu (mis. "odcdn 480p").
  - **Dua aksi halaman tidak lagi tertukar.** Inilah akar masalah "Tidak ada berkas video yang bisa diputar": aksi pemilih mirror dan aksi pengambil nonce sama-sama 32 heksadesimal dan sama-sama muncul sebagai `data:{action:"…"}`. Yang membedakan keduanya adalah bentuk pemakaiannya — aksi mirror selalu berdampingan dengan `nonce:` dalam objek yang sama, aksi nonce dikirim sendirian. Mengirimnya tertukar membuat sumber menjawab **HTTP 403**. Keduanya kini ditentukan dari bentuk itu, bukan dari urutan atau panjangnya. Nonce `null` yang tertanam di halaman juga tidak lagi dipakai.
  - **Diverifikasi langsung ke sumber** untuk FX Senshi Kurumi-chan Episode 1: sembilan mirror terdaftar, **odcdn 480p** terkonfirmasi memuat berkas `cdn.odcloud.net/anime/Otakudesu.io_FSKC--01_720p.mp4` (HTTP 200 `video/mp4`, mendukung Range/206). Tujuh server lain melaporkan keadaannya apa adanya: vidhide menutup berkasnya dengan enkripsi, mega/odstream tidak memuat alamat berkas yang bisa dibaca, ondesuhd baru membentuk alamat setelah pemutarnya dijalankan (alamat `googlevideo` yang diuji di sini menjawab 403/302 untuk permintaan langsung).
  - **Alamat rusak tidak pernah dilaporkan siap.** Sumber ini menaruh alamat tersusun ganda (`archive.org/download/https://…`) di salah satu pemutarnya; sekarang alamat seperti itu dan HLS yang segmennya belum dibakukan dilewati. Playlist HLS tidak dibuka lebih jauh — mengurai nama segmen dari playlist master berarti menebak alamat berkas.
  - **Single-tap layar penuh di reader Manga Shinigami.** Satu ketukan pada gambar masuk/keluar layar penuh; dua ketukan zoom 2×, tiga ketukan zoom 3×. Sebelumnya satu ketukan membuka zoom dan layar penuh hanya bisa dinyalakan dari tombol, lalu keluar lewat area 56dp di dasar layar — area itu sekarang dihapus, jadi tidak ada lagi ketukan yang direbut dan tidak ada halaman yang tergeser.

* **@VERSION@ — Pemutar video native (Media3 ExoPlayer) untuk Anime Otakudesu.**
  - **WebView diganti pemutar native.** `AnimePlayerActivity` memakai **Media3 ExoPlayer 1.4.1** dengan `PlayerView`, `DefaultTrackSelector`, dan `DefaultLoadControl` (buffer 15 s / 60 s / 2,5 s). Tidak ada banner, popup, footer, chat widget, atau tombol mirror milik website: seluruh kontrol digambar aplikasi.
  - **Kontrol lengkap.** Play/Pause, seek −10 s / +10 s, progress bar yang bisa digeser, layar penuh landscape (immersive, kontrol disembunyikan), pemilih kualitas dari track yang benar-benar ada di manifest, kecepatan 0,5×–2×, subtitle bila sumber menyediakannya, episode ‹ Sebelumnya / Berikutnya › dalam satu Activity, simpan posisi, dan lanjutkan dari posisi terakhir.
  - **Sumber yang diputar hanya yang benar-benar ada.** `OtakudesuMediaResolver` memeriksa halaman episode lalu halaman **/batch/** milik sumber: dari sana alamat MP4 (mis. `cdn.odcloud.net/anime/Otakudesu.io_FSKC--01_720p.mp4`) didapat dari apa yang dipublikasikan halaman, bukan dari pola URL yang ditebak. Untuk FX Senshi Kurumi-chan, **keenam episode** punya berkas 720p yang merespons HTTP 200 `video/mp4` dan mendukung Range (206), jadi bisa di-seek.
  - **Dua jenis sumber yang didukung:** MP4 progresif (`video/mp4`, mendukung Range) dan HLS (`.m3u8` + segmen `video/MP2T` 206). DASH diterima bila halaman menyebutnya.
  - **Cadangan pemilih mirror.** Halaman episode menyebut kualitas per mirror. Cadangan hanya diambil dari mirror yang berkasnya terbuka (odcdn/odstream/ondesuhd, kualitas tertinggi, maksimal dua) — **vidhide dan mega tidak pernah dipakai**, karena keduanya menutup berkasnya di balik pemutar beriklan; vidhide bahkan memalsukan keberadaan berkas (`/e/<kode>` → 404 padahal m3u8-nya masih hidup). Konsekuensi memilih mirror penyedia dihitung: aplikasi tetap memuat halaman penyedia saat itu. Bila tidak ada satu pun yang bisa diputar, layar menampilkan alasan dan tombol **Tonton di Sumber** — bukan layar hitam.
  - **Riwayat menonton.** ID anime, ID episode, posisi terakhir, durasi, waktu terakhir ditonton, dan status selesai disimpan per episode di `kuromaku_otakudesu`. Progres disimpan setiap 5 detik, saat jeda, dan saat Activity berhenti; rail "Lanjutkan Menonton" di beranda tetap memakai data yang sama.
  - **Optimasi player.** Satu player per Activity dan dilepas penuh di `onDestroy`, rotasi layar tidak mengulang video (posisi disimpan di `onSaveInstanceState`), buffering ditampilkan saat `STATE_BUFFERING`, error menampilkan pesan + **Coba Lagi**, dan playback tidak dipaksa jalan setelah pengguna menjeda sendiri.
  - **Pengujian:** 79 tes modul anime lulus (termasuk 18 tes baru resolver dua tingkat dan 4 fixture HTML baru dari halaman sumber). Suite penuh dijalankan sebelum rilis.

* **@VERSION@ — Sumber keempat: Anime Otakudesu.**
  - **Empat menu.** Source Hub dan navigation drawer sekarang berisi Novel, Manga Voratoon, Manga Shinigami, dan Anime Otakudesu (ikon film). Kartu keempat memakai latar dan glassmorphism yang sama, dan daftar kartu tetap bisa digulir di layar kecil.
  - **Katalog native, bukan WebView.** Beranda Anime Otakudesu punya pencarian, tab Ongoing (default), Complete, Jadwal Rilis, Riwayat, dan Favorit. Grid adaptif 2–4 kolom dengan kartu berisi cover, judul, episode terbaru, hari tayang, dan tanggal update.
  - **Parser HTML sungguhan.** `OtakudesuSource` membaca `div.venz div.detpost`, `h2.jdlflm`, `div.epz`, `div.epztipe`, `div.newnime`, `div.jdlrx h1`, `div.infozingle`, `div.episodelist span.zeebr`, `ul.chivsrc`, dan `div.kglist321` — struktur yang benar-benar ada di `otakudesu.blog`, disimpan sebagai fixture tes. Tidak ada endpoint API yang dikarang: kalau markup berubah, aplikasi menampilkan pesan galat, bukan data palsu.
  - **Pencarian yang benar.** Sumber menjawab 403 untuk `/page/N/?s=`; pencarian memakai `/?s=<kata>&post_type=anime` (sumber membatasi 12 hasil, satu halaman) dan aplikasi menampilkan batas itu apa adanya.
  - **Detail anime.** Poster, judul, sinopsis, genre, status, studio, total episode, tanggal rilis, dan daftar episode dengan tanggal. Urutan episode Terbaru → Terlama (default) bisa dibalik, ada pencarian nomor episode, dan episode yang sudah dibuka diberi tanda ✓.
  - **Mekanisme menonton yang jujur.** Halaman episode Otakudesu memuat pemutar milik penyedia (iframe `desustream`); tidak ada berkas media resmi yang boleh diputar aplikasi lain, jadi video **tidak** diekstrak dan **tidak** ada player ExoPlayer palsu. Tombol Tonton membuka halaman episode resmi di `OtakudesuPlayerActivity` (WebView): halaman aslinya, pemutarnya, iklannya. Tersedia layar penuh (termasuk fullscreen HTML5), tombol Sebelumnya/Berikutnya sesuai urutan cerita, dan tombol Situs untuk membukanya di browser.
  - **Riwayat dan Lanjutkan Menonton.** Posisi tidak disimpan dalam detik karena pemutaran bukan milik aplikasi; yang disimpan adalah episode terakhir, nomor episode, total episode, waktu, dan tanda episode yang sudah dibuka. Rail "Lanjutkan Menonton" menampilkan poster, episode, persentase episode, waktu relatif, dan tombol Lanjutkan.
  - **Backup/restore.** Format `kuromaku-reading-v1` tetap sama; bagian `anime_preferences` hanya ditambahkan bila ada isinya, sehingga berkas lama tetap terbaca dan berkas baru tetap terbaca aplikasi lama.
  - **Bukti pengujian:** parser diuji terhadap HTML asli (`ongoing.html`, `detail.html`, `search.html`, `schedule.html`), termasuk penolakan CAPTCHA, HTTP non-TLS, dan struktur yang berubah. Store diuji untuk favorit, migrasi format lama, progres yang tidak mundur, dan tanda episode. Backup diuji untuk tiga sumber sekaligus dan kompatibilitas berkas lama.
  - **Keterbatasan:** pemutaran video belum diuji pada perangkat fisik; seluruh pengujian berjalan di Robolectric dengan HTML sumber yang direkam.

* **@VERSION@ — Perbaikan menyeluruh gambar reader yang berkedip (area hitam + loading).**
  - **Akar masalah 1 — identitas halaman dibuang saat recycle.** `onViewRecycled()` mengosongkan `shownKey`, jadi setiap kali RecyclerView me-recycle lalu mengikat ulang posisi yang sama (sangat sering di tepi viewport saat scroll), `load()` menganggap halaman itu belum punya gambar. Sekarang `shownKey` + `shownPosition` dipertahankan melewati recycle, dan `load()` langsung kembali bila halaman yang sama masih tampil.
  - **Akar masalah 2 — `showLoading()` tidak punya penjaga.** Metode ini selalu mengosongkan ImageView, menutup permukaan gambar, lalu menampilkan spinner — persis "area hitam + loading" di video. Sekarang ia tidak melakukan apa pun bila `hasContent()` benar. `showFailure()` juga tidak lagi menghapus gambar yang valid; kegagalan hanya dicatat.
  - **Akar masalah 3 — tile di-recycle saat cache penuh.** `removeEldestEntry` memanggil `bitmap.recycle()` untuk potongan yang bisa saja masih berada di area gambar, sehingga potongan itu hilang sesaat. Sekarang tidak ada `recycle()` di jalur gambar; pemangkasan lewat `trim(from, to)` dan potongan yang sedang terlihat tidak pernah dibuang.
  - **Diagnostik** ditambahkan (tag `ShinigamiReader`, level DEBUG): posisi halaman, cache hit/miss, hasil unduhan yang dibuang, bind-ulang yang dilewati, dan recycle. Tidak ada token/cookie yang dicatat.
  - Prefetch **tidak** dimatikan: setelah kunci per-URL, prefetch terbukti tidak membatalkan permintaan halaman yang sedang tampil (ada tesnya).
  - **Bukti pengujian:** 646 tes lulus, 0 gagal, 1 tes jaringan opt-in dilewati. Tes baru: `showLoading()`/`showFailure()` pada halaman yang sudah tampil tidak menyembunyikan gambar dan tidak memunculkan spinner.
  - **Belum diuji pada perangkat fisik.** Kriteria "diamkan 15 detik, scroll lambat/cepat/balik, ulangi" pada Demonic Emperor 927, Nano Machine, dan judul lain harus Master Lupi jalankan sendiri; kalau masih berkedip, logcat tag `ShinigamiReader` akan menunjukkan penyebabnya.

* **@VERSION@ — Default Chapter Terbaru, top bar anti-kedip, dan gambar reader tanpa flicker.**
  - **Beranda dibuka di Chapter Terbaru.** Urutan tab kini Chapter Terbaru | Terpopuler | Manga Terbaru | Riwayat | Favorit | Unduhan. Label tidak pernah bertukar, dan tiap tab tetap memakai kursor pagination + cache sendiri.
  - **Migrasi preferensi lama:** state yang masih menyimpan `sort=rank` dari tombol lama dibuka pada tab Terpopuler; preferensi lain (pencarian, genre, status, tema, progres) tidak disentuh. Tanpa state lama, default selalu Chapter Terbaru.
  - **Penyebab top bar berkedip:** tinggi container header dianimasikan, sehingga tinggi RecyclerView ikut berubah setiap frame. Perubahan itu memancarkan `onScrolled` lagi, listener mengira pengguna bergerak, lalu animasi dibalik arah — berulang. Diperparah `GONE` di akhir animasi yang menggeser layout sekali lagi.
  - **Perbaikannya:** satu state machine (`chromeHidden` + `chromeBusy`), akumulasi arah scroll dengan hysteresis 48 dp untuk menyembunyikan dan 24 dp untuk memunculkan, animasi lama dibatalkan sebelum transisi baru, seluruh event scroll diabaikan selama transisi, tinggi dianimasikan ke 0 tanpa pernah memakai GONE, dan pagination tidak lagi menyentuh state header.
  - **Penyebab gambar reader berkedip:** setiap rebind memanggil `showLoading()` yang mengosongkan ImageView dan menutup permukaan tile, lalu `showFile()` membuka decoder dan mendekode ulang walaupun halamannya sama. Ditambah `requestLayout()` setiap kali dan dua permintaan (prefetch + halaman terlihat) menulis berkas sementara yang sama.
  - **Perbaikannya:** rebind pada berkas yang sama tidak melakukan apa pun (`shownKey`), `TiledSurface` memakai ulang decoder dan tile bila berkas + kualitas sama, placeholder hanya dipasang bila belum ada gambar valid, `requestLayout()` hanya saat rasio benar-benar berubah, dan `ShinigamiPageCache` memakai kunci per URL sehingga prefetch tidak membatalkan halaman yang sedang tampil. Decode tetap di thread latar dan kualitas HD tidak diturunkan.
  - **Bukti pengujian:** 645 tes lulus, 0 gagal, 1 tes jaringan opt-in dilewati. Tes baru: default + urutan tab, rebind halaman yang sama tidak menutup permukaan gambar, dan dua `fetch` bersamaan untuk URL yang sama hanya mengunduh sekali.
  - **Belum diukur pada perangkat fisik:** FPS/jank dan RAM tidak diambil dengan Profiler/Perfetto (tidak ada perangkat adb). Klaim di atas berasal dari perubahan jalur kode + tes otomatis.

* **@VERSION@ — Performa, tab kategori permanen, auto-hide header, dan scroll reader yang mulus.**
  - **Tab beranda tidak lagi berganti label.** Kini enam tab tetap: Terpopuler | Manga Terbaru | Chapter Terbaru | Riwayat | Favorit | Unduhan. Menekan tab tidak pernah mengubah teksnya.
  - **Tiap kategori benar-benar meminta data berbeda** ke sumber, bukan sekadar mengganti label: Terpopuler = `sort=rank` menurun, Chapter Terbaru = `sort=latest` menurun, Manga Terbaru = `sort=rank` menaik lalu diurutkan ulang menurut `created_at` (sumber Shinigami tidak menyediakan urutan "judul baru", jadi kursor terdekat dipakai dan hasilnya diurutkan lokal).
  - **Data, kursor pagination, dan posisi scroll disimpan terpisah per tab.** Kembali ke tab yang sudah punya data tidak memicu permintaan baru dan tidak mengembalikan pengguna ke atas.
  - **Auto-hide header:** pencarian, baris tab, dan jumlah manga menyusut ke nol tinggi saat menggulir turun (ambang 24 dp agar tidak berkedip) lalu kembali saat menggulir naik. Toolbar utama (hamburger, judul, Unduhan, Pengaturan) tidak pernah disembunyikan. Tinggi container dianimasikan, bukan hanya digeser, sehingga ruang benar-benar kembali ke katalog tanpa mengganti adapter atau LayoutManager.
  - **Scroll reader jauh lebih ringan:** potongan gambar tidak lagi didekode di `onDraw`. Decode `BitmapRegionDecoder` dipindah ke pool 2 thread, hasil dipasang hanya bila generasi masih sama, dan potongan berikutnya disiapkan lebih dulu. Ini menghilangkan sumber frame-drop terbesar pada halaman HD panjang.
  - **Placeholder halaman memakai rasio yang pernah terukur** (disimpan per chapter), jadi daftar tidak melompat saat gambar selesai dimuat. Reader juga mematikan item animator, memakai stable ID, dan menyiapkan satu halaman berikutnya di thread terpisah.
  - **Cover katalog dimuat pada ukuran kartu** (`override` selebar sel), bukan resolusi penuh; permintaan dibatalkan saat ViewHolder didaur ulang. Grid kini adaptif (2–4 kolom menurut lebar layar).
  - **Pencarian di-debounce 350 ms**, sehingga mengetik tidak membanjiri sumber dengan permintaan.
  - **Bukti pengujian:** 640 tes lulus, 0 gagal, 1 tes jaringan opt-in dilewati (80 kelas). Tes baru: label tab permanen, feed berbeda per kategori, cache per tab tanpa refetch, auto-hide pada ambang scroll, dan placeholder reader yang tidak menggeser layout.
  - **Belum diukur pada perangkat fisik:** angka FPS/jank dan pemakaian RAM nyata belum diambil (tidak ada perangkat adb tersambung); klaim performa di atas berasal dari perubahan jalur kode, bukan dari Profiler/Perfetto.
  - Batas paralelisme WorkManager untuk unduhan belum diubah; tiap chapter tetap satu work yang memproses halamannya berurutan.

* **@VERSION@ — Katalog yang tidak melompat ke atas dan panah chapter di reader.**
  - **Penyebab katalog kembali ke atas:** setiap halaman memanggil `list.setAdapter(new ConcatAdapter(...))` lalu `notifyDataSetChanged()`. RecyclerView membuang posisi gulir setiap kali adapter diganti, jadi begitu pengguna mencapai dasar daftar dan halaman berikutnya tiba, katalog melompat ke item pertama. Sekarang satu adapter hidup seumur Activity: pagination hanya menambah item di bawah lewat `notifyItemRangeInserted`, adapter punya stable ID, dan `LayoutManager` tidak diganti saat data bertambah.
  - Rail "Lanjutkan Membaca" tetap menjadi item pertama di dalam daftar (ikut tergulir, bukan header lengket) dan hanya memberi tahu daftar bila jumlah itemnya benar-benar berubah, sehingga kembali dari reader tidak merender ulang katalog.
  - **Panah chapter sebelumnya/berikutnya** di reader memakai nomor chapter (urutan cerita), bukan urutan tampilan daftar: Prolog, Chapter 0, 1, 1.5, 10.5, … Epilog. Tombol kiri/kanan berada di bar bawah 48 dp, tidak menutupi gambar, dan ikut tersembunyi saat layar penuh (ketuk strip bawah untuk memunculkan kontrol lagi).
  - Chapter tujuan dicari pada **seluruh** chapter manga, bukan halaman yang sedang dimuat: `ShinigamiSource.chaptersAll()` mengambil semua halaman (sumber membatasi 500 per halaman) lalu mengurutkan secara lokal, dengan cache 5 menit agar panah tidak meminta ulang.
  - Perpindahan menyimpan progres chapter lama lebih dulu, memulihkan posisi chapter tujuan bila pernah dibaca, tidak menumpuk Activity, dan menolak ketukan berulang saat perpindahan berjalan.
  - Offline: chapter yang tersimpan dibuka lokal, chapter yang belum ada dan perangkat tanpa internet ditolak dengan pemberitahuan — halaman yang sedang dibaca tidak diubah.
  - **Bukti pengujian:** 21 tes baru (urutan chapter, indeks 579 chapter dari fixture asli, navigasi reader termasuk layar penuh, dan pagination katalog yang menahan posisi gulir). Suite penuh: 634 tes lulus, 0 gagal, 1 tes jaringan opt-in dilewati.

* **2.29.0 — Rilis sebelumnya: tema Light/Dark/System dan pengurutan chapter Shinigami (percobaan pertama).**
* **2.22.1 — Perbaikan reader Shinigami.**
  - Loading/error/Coba Lagi per gambar; bitmap panjang dibatasi, halaman lain tetap terlihat bila satu gagal.
  - Status lokal hanya Tersimpan offline bila jumlah dan semua file gambar lengkap/valid; metadata saja bukan offline.
  - Tanggal Rilis/Diperbarui berasal dari sumber, mengikuti zona waktu pengguna. Kartu Chapter terbaru/Update terakhir dan filter/sort dipertahankan.
  - Cache metadata aditif tidak menghapus progres, riwayat, favorit, atau data lama. Novel, Voratoon WebView, drawer dan backup tidak diubah.
  - Build lokal: 494 tes lulus, 0 gagal, 1 tes jaringan opt-in dilewati. Uji sumber terpisah: 156/156 gambar dari sembilan chapter pada tiga judul lolos HTTP/MIME/decoding.
  - **Tampilan seluruh gambar di HP belum diuji**; tidak ada perangkat adb tersambung. Unduhan permanen tetap disabled karena izin sumber belum terverifikasi.
  - Detail file dan metode: SHINIGAMI-TEST-REPORT.md; bukti per halaman: SHINIGAMI-LIVE-EVIDENCE.json.
* **2.22.0 — Manga Shinigami native (eksperimental).**
  - Drawer bersama lima entri: Novel, Manga Voratoon, Manga Shinigami, Backup, Restore. Voratoon tetap WebView.
  - Katalog grid, pencarian, terbaru, detail, reader vertikal dengan pemuatan bertahap, zoom, layar penuh,
    progres per chapter, riwayat, dan favorit, tanpa iklan atau navigasi website.
  - Domain API/situs dapat dikonfigurasi dengan validasi HTTPS dan host. Pembatasan sumber (Cloudflare, 401/403/429)
    ditampilkan sebagai kesalahan dan tidak dilewati.
  - Unduhan offline Shinigami dinonaktifkan sampai izin sumber terverifikasi.
  - Progres Shinigami ikut backup/restore tanpa merusak format backup lama.
* **2.21.1 — Penanganan Google Login & Pemblokiran Iklan Terarah Manga.**
  - **Autentikasi Google via Browser/Custom Tabs**: Navigasi OAuth Google (`accounts.google.com`, `oauth2.googleapis.com`) dideteksi otomatis dan diluncurkan melalui Chrome Custom Tabs atau browser eksternal. Mencegah error penolakan embedded webview dari Google (`403 disallowed_useragent`) tanpa pernah meminta atau menyimpan password pengguna di aplikasi.
  - **Pemblokiran Iklan Terarah & Perlindungan Resource Autentikasi (`MangaAdFilter`)**: Jaringan iklan agresif (`doubleclick.net`, `googleads.g.doubleclick.net`, `adservice.google.com`, `adsterra`, `monetag`, `highperformancegate`, `popads`, `propellerads`, `exoclick`, `histats`, `mgid`, popunder, dan banner promosi) dicegat dengan respon 204 No Content. Di saat yang sama, layanan krusial TIDAK diblokir: endpoint autentikasi Google, token discovery, `api.voratoon.com`, cookie sesi, serta Cloudflare Turnstile (`challenges.cloudflare.com`, `cloudflareinsights.com`).
  - **Perapian Toolbar Alamat Manga**: Bilah alamat toolbar kini hanya menampilkan nama sumber / hostname ringkas (contoh: `Sumber: 13.voratoon.com` atau `Login Google (accounts.google.com)`) dengan satu baris dan elipsis rapi. Mengetuk bilah alamat membuka dialog modal "Alamat Halaman" yang menampilkan URL lengkap, tombol **Salin URL** ke clipboard, tombol **Buka di Browser**, dan tombol **Tutup**.
  - **CookieManager Third-Party Cookies**: Mengaktifkan penerimaan third-party cookies dan sinkronisasi flush cookie pada WebView untuk menjaga persistensi sesi pengguna dan token keamanan Cloudflare.
  - **394 unit test lulus**, 0 gagal (36 kelas).
* **2.21.0 — Penyelarasan Navigation Drawer Manga & Dukungan Multi-Source Novel.**
  - **Navigation Drawer Manga Identik**: Tombol menu tiga garis (☰) di halaman Manga (`MangaActivity`)
    kini menggunakan komponen bersama `SectionDrawer` yang sama dengan halaman Novel (`MainActivity`),
    menggantikan `PopupMenu` kecil sebelumnya. Drawer mencakup menu **Novel**, **Manga** (dengan penanda aktif `✓`),
    **Backup progres membaca**, dan **Restore progres membaca**. Memiliki lebar 82% layar, latar diredupkan
    (overlay gelap transparan), gestur swipe ke kiri untuk menutup, tombol Back untuk menutup, serta mendukung
    tema gelap & terang secara konsisten tanpa mengganggu status fullscreen WebView.
  - **Arsitektur Multi-Source Adapter**: Menambahkan dukungan penambahan novel melalui URL dari **Megumi Novel**
    (`meguminovel.com`) dan **Sakura Novel** (`sakuranovel.id`) di samping **Kaori Translation**.
    Aplikasi mendeteksi sumber secara otomatis berdasarkan hostname, mengekstrak cover, metadata, sinopsis,
    dan daftar bab secara ascending kronologis dengan pembersihan navigasi, iklan, serta widget website sumber.
  - **386 unit test lulus**, 0 gagal (35 kelas).
* **2.20.2 — Sertifikat penandatanganan konsisten (reproducible debug keystore).**
  Sebelumnya setiap eksekusi CI GitHub Actions men-generate debug keystore acak sekali pakai.
  Akibatnya, setiap rilis APK baru memiliki identitas penerbit (certificate digest SHA-256)
  yang berbeda, sehingga Android menolak pembaruan langsung (update/install over existing)
  dan memaksa uninstall yang menghapus progres baca dan chapter offline.
  Mulai 2.20.2, keystore debug repositori (`keystore/debug.keystore`) didaftarkan ke `signingConfigs`
  dan divalidasi ketat pada alur CI (`SHA-256: e413b4146721994d...`), memastikan pembaruan APK
  dapat dipasang langsung di perangkat tanpa menghapus data lokal pembaca.
* **2.20.1 — satu kalimat status, dan satu salinan yang tidak dijaga tes.**
  Kalimat **`Tidak dicantumkan di halaman sumber`** tadinya ada sebagai empat
  salinan terpisah di kode. Tiga dijaga tes; salinan keempat — cadangan saat
  menyimpan novel yang ditambahkan lewat URL — **tidak**: menggantinya dengan
  teks lain membuat seluruh 356 tes tetap hijau. Sekarang keempatnya membaca satu
  konstanta, dan keputusan "kosong berarti kalimat itu" pindah ke
  `Novel.statusOrDefault(String)` supaya bisa diuji: `null`, string kosong, dan
  spasi menjadi kalimat itu; status yang benar-benar ditulis sumber tetap apa
  adanya. Tidak ada perubahan tampilan, warna, atau navigasi.
* **Drawer bagian dan backup progres membaca (2.20).** Tombol menu di
  toolbar Perpustakaan sekarang membuka **drawer samping** bergaya modal
  (bukan popup kecil) yang memuat **Novel**, **Manga**, **Backup progres
  membaca**, dan **Restore progres membaca**. Drawer terbuka selebar 82%
  layar dengan latar diredupkan dan animasi geser 220 ms. Fitur **Manga**
  dari 2.19 tetap sama, hanya jalan masuknya kini drawer.
* **Backup progres membaca adalah berkas milik pembaca.** Progres
  disimpan di satu berkas preferensi sebagai peta datar bertipe campuran
  (bookmark `String`, posisi gulir `int`, persen `int`, akordeon
  `Set<String>`). Berkas backup menulis **setiap nilai beserta tipenya**,
  lalu memulihkannya sesuai tipe itu: nilai `int` yang kembali sebagai
  teks akan mengosongkan progres tanpa pesan apa pun, dan itu justru
  kegagalan yang harus dicegah fitur ini. Ekspor memakai
  `ACTION_CREATE_DOCUMENT` dengan nama `kuromaku-reading-backup.json`,
  impor memakai `ACTION_OPEN_DOCUMENT`; keduanya lewat pemilih berkas
  Android, jadi berkasnya bisa disimpan ke Drive, kartu SD, atau dikirim
  sendiri — tidak ada server yang terlibat.
* **Restore menolak sebelum menulis.** Berkas asing (format berbeda),
  berkas terpotong, dokumen kosong, dan berkas di atas 5 MB ditolak
  **sebelum satu kunci pun** masuk ke penyimpanan, jadi percobaan yang
  gagal tidak bisa memindahkan pembaca ke chapter yang tidak pernah
  dibuka. Cadangan satu novel tidak menghapus bookmark novel lain. Kunci
  bertipe tak dikenal (dari versi lebih baru) dilewati satu per satu,
  sisanya tetap dipulihkan dan jumlahnya dilaporkan lewat toast
  ("N progres dipulihkan").
* **Pengelolaan novel dinamis**: tombol **+ Tambah Novel** di halaman
  Perpustakaan membuka halaman input URL. Aplikasi mengunduh halaman
  katalog, membaca judul, cover, penulis, ilustrator, genre, status,
  sinopsis, daftar volume dan chapter, lalu menampilkan **preview**
  sebelum Tuan menekan **Simpan ke Perpustakaan**. Metadata yang tidak
  tertulis di halaman sumber ditampilkan sebagai "Tidak tercantum di
  halaman sumber" — tidak ada data yang dikarang.
* **Parser generik**: tidak bergantung pada dua novel yang ada. Tautan
  chapter, prolog, epilog, afterword, dan ilustrasi dikenali dari teks
  dan slug; volume diturunkan dari heading "Volume N", lalu dari teks
  tautan, lalu dari slug. Chapter yang tidak dapat dipetakan dihitung
  dan dilaporkan sebagai peringatan di preview. Parser khusus dua novel
  lama tetap dipakai sebagai fallback.
* **Perpustakaan permanen (Room)**: novel disimpan di database, di-key
  oleh URL katalog yang sudah dinormalisasi (`?m=1`, garis miring di
  akhir, `www`, dan `http/https` disamakan), jadi URL yang sama tidak
  pernah menjadi dua novel. Dua novel bawaan di-seed otomatis saat
  database dibuat sehingga tidak hilang saat migrasi.
* **Menu tiga titik per kartu novel**: Buka Novel, Perbarui Novel, Hapus
  dari Perpustakaan. Hapus menampilkan dialog dengan dua pilihan
  terpisah — hapus dari perpustakaan saja (unduhan dan progres tetap),
  atau hapus beserta unduhan offline dengan centang terpisah untuk
  menghapus progres membaca. Menghapus satu novel tidak menyentuh novel
  lain.
* **Muat Ulang tidak menimpa data**: judul, metadata, cover, dan
  sinopsis yang baru hanya menimpa bila halaman benar-benar
  mencantumkannya. Kolom yang kosong di halaman tidak menghapus nilai
  tersimpan, dan id novel tidak pernah dihitung ulang dari judul
  sehingga progres membaca tetap menempel.
* **Integrasi penuh**: novel hasil tambah mendukung Reader Mode, semua
  ilustrasi, download chapter, download volume, pembacaan offline,
  progres membaca, tema gelap/terang/sepia, ukuran teks, navigasi
  chapter, dan pembaruan katalog — bukan sekadar daftar bookmark URL
  atau WebView.
* **Refusal yang bisa dibaca**: URL bukan halaman artikel, halaman
  beranda, halaman label/feed, dan artikel biasa yang bukan katalog
  ditolak dengan pesan jelas; novel kosong tidak pernah disimpan.
* **Bug yang diperbaiki di versi ini** (ditemukan lewat probe parser,
  bukan dugaan): (1) pembacaan teks menggandakan isi satu kali per
  tingkat elemen, sehingga genre terbaca "Genre" dan sinopsis 10
  paragraf runtuh jadi satu blok; (2) cover memilih banner lebar
  pertama alih-alih sampul, dan gambar yang dibungkus tautan — cara
  sumber menaruh cover — tidak terbaca sama sekali; (3) label tebal
  seperti "Penulis :" tidak terbaca karena baris dipecah sebelum titik
  dua; (4) URL dengan garis miring di akhir ditolak; (5) pencarian
  novel berdasarkan id punya fallback "novel pertama", sehingga novel
  yang baru dihapus bisa muncul kembali sebagai novel lain sambil
  membawa progres dan unduhannya.
* **274 unit test lulus**, 0 gagal (26 kelas). Termasuk: URL tidak valid
  ditolak, satu halaman satu identitas untuk sembilan bentuk penulisan
  URL, artikel biasa ditolak tanpa membuat novel kosong, katalog
  berformat berbeda (entry-content, heading h4, ordered list, label
  tebal, "Cahpter 2") terbaca lengkap, duplikat tidak menambah baris,
  perpustakaan tetap ada setelah database ditutup dan dibuka ulang,
  Muat Ulang tidak menghapus metadata/progres, hapus dua opsi hanya
  menyentuh satu novel, download dan baca offline novel hasil tambah
  beserta ilustrasinya, serta dua novel lama tetap berfungsi.
* Uji mutasi: lima bug di atas dikembalikan satu per satu dan setiap
  kali ada tes yang gagal — tes ini terbukti menangkap bug, bukan
  sekadar hijau.
* **Pengujian 2.16 — pemilih tautan manual** (tidak ada perubahan kode
  produksi). `AddNovelPage.picker()` belum pernah dijalankan satu tes pun
  sejak 2.10, sehingga tiga janjinya hanya dijamin oleh pembacaan kode.
  Tiga tes Robolectric di 320 dp sekarang menjaga: (1) hanya tautan yang
  `selectable` yang ditawarkan — bukan semua kandidat, karena tautan
  navigasi yang bisa dicentang berarti satu ketukan cukup untuk mengimpor
  iklan sebagai chapter; (2) centang dan batal centang menentukan isi
  pilihan, dan yang terkirim adalah kotak yang masih tercentang, bukan
  semua yang pernah disentuh; (3) tidak ada yang dikirim sebelum "Pakai
  pilihan ini" ditekan, dan katalog yang sudah terbaca dengan yakin tidak
  menampilkan pemilih sama sekali. Tiga mutasi dijalankan, semuanya
  tertangkap. Yang diuji baru pemilih dengan **3 kotak centang** (fixture:
  5 kandidat, 3 boleh dipilih). Kandidat terbanyak yang terukur di katalog
  nyata adalah **43** (artikel Yariko: 47 tautan, 43 chapter, volume 16+13+14;
  URL label: 44 tautan, 42 chapter, 15+13+14) — pemilih sepadat itu belum
  pernah dirender di tes. Layar HP sungguhan dengan puluhan kotak centang
  masih perlu diuji di perangkat.
* **Verifikasi ulang mutasi 2.11 di pohon 2.16** — notifikasi terlambat
  dari skrip mutasi lama menyebut N2 "LOLOS", padahal skrip itu menulis
  mutannya ke snapshot lama lalu memulihkan dari snapshot yang sama, jadi
  verdiktnya berlaku untuk pohon lama. Kelima mutasi 2.11 dijalankan ulang
  dengan backup dari berkas hidup saat itu juga: N1 (alasan "tautan gambar"
  dihapus), N2 (alasan "potongan judul" dihapus), N3 (`mergeSplitLabels`
  dimatikan), N4 (alasan spesifik ditimpa alasan generik), N5 (label
  interlude memakai angka apa adanya) — **kelima-limanya tertangkap**, tidak
  ada yang lolos. Pemulihan diverifikasi lewat `md5sum` sebelum/sesudah,
  `git diff HEAD` kosong, penanda `MUTATION` nol, suite tetap 274 tes hijau.
* **Bagian Manga tahap 1 (2.19), navigasi diperbaiki (2.19.1).**
  Toolbar Perpustakaan memakai tombol ikon menu (48 dp) yang membuka
  menu **Novel / Manga**; memilih Manga membuka layar terpisah yang
  menampilkan sumber manga (`https://voratoon.id/`) dengan header, alamat
  halaman, bilah progres, dan tombol **Sumber** untuk kembali ke awal.
  Halaman mendarat dipakai supaya situs menentukan sendiri domain
  angkanya — subdomain bernomor seperti `12.voratoon.com` tidak pernah
  dipatok di kode, karena nomor itu berubah dan bisa sudah mati.
  **Penjaga domain** menolak setiap navigasi bingkai utama yang bukan
  `https` pada `voratoon.id`/`voratoon.com` atau subdomainnya; penolakan
  diberi pesan ("Tautan luar diblokir") dan tidak pernah diam-diam
  diikuti. Pemeriksaan memakai titik di depan akhiran, sehingga
  `evilvoratoon.id` dan `voratoon.id.evil.com` ditolak, sementara path
  yang kebetulan memuat nama domain di situs asli tetap terbaca. Posisi
  halaman disimpan saat layar dibuat ulang, tombol kembali memakai
  riwayat WebView lebih dulu, dan WebView dihancurkan di `onDestroy`.
  Tahap 1 sengaja hanya menampilkan situs: belum ada ekstraksi chapter
  atau penyimpanan offline untuk manga.
* **Layar penuh untuk manga (2.19.2).** Tombol **⛶** di header Manga
  menyembunyikan header dan bilah alamat sekaligus, sehingga halaman
  mendapat seluruh tinggi layar. Status bar dan bilah navigasi ikut
  disembunyikan lewat `WindowInsetsControllerCompat`, dan bisa dimunculkan
  sementara dengan menggeser dari tepi. Tombol **Kembali** keluar dari
  layar penuh lebih dulu sebelum menutup layar atau memundurkan riwayat
  WebView — kalau tidak, satu-satunya jalan keluar adalah meninggalkan
  halaman dan kehilangan posisi baca. Status layar penuh disimpan di
  `onSaveInstanceState`, jadi memutar HP tidak mengembalikan toolbar.
  Di mode normal, toolbar digeser turun oleh **tinggi status bar sebenarnya**
  dari `WindowInsets` (bukan padding tetap), dan konten diberi ruang di
  bawah sebesar bilah navigasi. Di layar penuh kedua padding itu nol.
  Konsekuensinya `setDecorFitsSystemWindows(false)` dipakai, jadi padding
  inset adalah satu-satunya yang mencegah toolbar tertutup status bar.
* **4 tes baru layar penuh** di `MangaScreenTest` menekan tombol ⛶ yang
  sungguhan: header dan bilah alamat hilang lalu kembali, `Kembali` keluar
  dari layar penuh tanpa menutup layar, status bertahan setelah layar dibuat
  ulang, dan inset 72/48 px dari sistem menggeser toolbar 72 px serta
  menyisakan 48 px di bawah — lalu keduanya nol saat layar penuh.
  Uji mutasi: **7/7 tertangkap** (header tidak disembunyikan, bilah alamat
  tetap tampil, tombol tidak bereaksi, `Kembali` tidak keluar dari layar
  penuh, status tidak disimpan, inset diabaikan, padding bawah tetap ada di
  layar penuh) dengan pemulihan diverifikasi `md5`.
* **8 tes `MangaScreenTest`** mengunci penjaga dan navigasi itu: sumber
  dan subdomainnya diterima, domain lama diterima, kemiripan nama ditolak
  (termasuk nama sumber yang muncul di tengah host), `http`/`file`/
  `javascript`/tanpa host ditolak, path yang memuat nama domain di situs
  asli tetap diterima, layar terdaftar di manifest dan tidak diekspor,
  tombol menu bagian berikon dan seluas 48 dp, menu itu hanya tampil di
  rak sementara panah kembali mengambil tempatnya di halaman Unduhan,
  serta halaman terakhir bertahan saat layar dibuat ulang.
  Uji mutasi navigasi 2.19.1: **4/4 tertangkap** (visibilitas menu dihapus, panah kembali
  selalu tampil, tombol menu kembali jadi tombol teks tanpa ikon, penjaga
  domain dilonggarkan jadi `contains`) dengan pemulihan diverifikasi
  `md5`. Run pertama hanya 2/4 — dua mutasi bertahan hijau, jadi kedua tes
  ditulis ulang sampai mutasinya gagal, lalu run diulang 4/4.
* **Uji mutasi 2.20: 18/18 tertangkap**, semuanya pada berkas hidup
  dengan `md5` dipulihkan sesudah setiap mutasi. Yang diuji: tipe `int`
  ditulis sebagai `string`, himpunan akordeon ditulis sebagai string
  biasa, pemeriksaan format dimatikan saat restore, berkas kebesaran
  diterima, kunci bertipe asing dipaksa jadi string, restore tidak
  menulis apa pun, `float` dipulihkan sebagai `int`, item drawer tidak
  bisa ditekan, entri backup membuka pemilih berkas yang salah, entri
  restore memakai kode permintaan backup, hasil restore tidak dilaporkan,
  label persen baca dihapus, persen ditulis tanpa kata (kembali ke
  "67%Mengunduh"), dan chapter yang sedang dibaca tidak diberi label.
  Empat mutasi animasi drawer menyusul setelahnya: `Motion.enabled`
  diganti `true` (animasi berjalan walau *Remove animations* aktif),
  posisi awal dibuat 0 (drawer tidak pernah bergeser), posisi diam dibuat
  di luar layar (drawer tak terlihat dan tak bisa ditekan), dan durasi
  diganti 40 ms (di bawah jendela 180–250 ms). Empat mutasi animasi ini butuh
  **empat run**, dan yang pertama tidak lulus: run 1 hanya **1/3** karena tes
  membaca posisi *sesudah* looper dikuras, run 2 **0/3** karena semua anchor
  basi (`SKIP`, bukan tertangkap — angka itu tidak membuktikan apa pun), run 3
  **3/4** karena mutasi durasi lolos: `HeaderAndMotionTest` hanya menguji
  **konstanta** `Motion.DURATION_MS`, bukan angka yang dipakai call site.
  Tes diperbaiki membaca posisi **sebelum** looper berjalan dan
  `decor.animate().getDuration()` — animator yang benar-benar dijalankan —
  lalu run diulang **4/4**.
  Setiap mutasi membuat tes bernama gagal — bukan sekadar build rusak.
* **Fitur Cover Volume & Indikator Unduhan (2.18).** Dua keluhan dari
  laporan: volume novel yang ditambahkan lewat URL (Yarikon) hanya
  menampilkan placeholder "Vol 1", "Vol 2", "Vol 3", dan baris unduhan
  mencetak teks berdempetan "67%Mengunduh…" dengan tombol berlabel
  "Menunggu" pada chapter yang justru sedang diunduh.
  **Akar masalah cover bukan parser** — `VolumeCoverParser` sudah benar
  sejak awal (diukur: Yarikon 3 cover, Monogatari 7, Magical★Explorer 14).
  Yang salah adalah `Catalog.withCovers()` yang **hanya** membaca tabel
  hardcoded tanpa entri untuk novel URL, sehingga hasilnya `null` dan UI
  jatuh ke placeholder. Perbaikan: cover hasil parse ditempelkan langsung
  ke volumenya dari body artikel yang sama (tanpa request tambahan) di
  `LandingCatalogParser.withParsedCovers` dan `NovelDiscovery.withCovers`;
  cover hasil parse **menang** atas tabel bawaan; `VolumeCovers.forVolume`
  tidak lagi meminjam tabel Magical★Explorer untuk id asing (dulu novel
  pengguna bisa menampilkan artwork seri lain); cover disimpan ke tabel
  Room baru `volume_covers` (database v2, migrasi hanya menambah tabel,
  baris novel tidak disentuh) sehingga tetap tampil dari cache tanpa
  parsing ulang; pemilihan tautan manual tidak menghilangkan cover
  (`reselect` + `keepCovers`); volume tanpa cover tetap tanpa cover dan
  tidak pernah meminjam ilustrasi chapter.
  **"67%Mengunduh…"** berasal dari dua TextView berdempetan tanpa padding
  plus label tombol yang salah. Sekarang: view `RingProgress` (cincin
  beranimasi 180 ms menuju nilai yang diberikan, berubah spinner bila
  ukuran tidak diketahui, menghormati "Remove animations"), persentase
  punya view dan jarak sendiri, status punya padding 8 dp, tombol 48 dp,
  dan label tombol mengikuti status ("Menunggu" hanya untuk antrean,
  "Mengunduh" untuk yang berjalan). Persentase berasal dari
  `Http.getBytes(url, progress)` → `Fetcher.Source.image(url, progress)` →
  `ChapterDownloader.Progress` → `DownloadStatusStore.setBytes`, jadi
  hanya angka yang benar-benar diukur yang tampil; tanpa `Content-Length`
  nilainya -1 dan UI hanya menampilkan spinner.
  **Status parsial** (permintaan nomor 8) mengubah perilaku lama secara
  sadar: dulu satu ilustrasi rusak menggagalkan seluruh chapter dan teks
  yang sudah terunduh dibuang. Sekarang teks tetap disimpan,
  `imagesExpected` dicatat di indeks offline sehingga chapter parsial
  tetap parsial setelah aplikasi ditutup, `isComplete` dibedakan dari
  `isSaved`, `VolumeJob` menandai `PARTIAL` tanpa menghitungnya selesai,
  UI menawarkan **"Lanjutkan"**, dan gambar rusak tidak pernah ditulis
  dengan nama `.jpg`.
  Kartu volume: belum unduh → "Download Semua"; berjalan → cincin +
  "50% · 1 dari 2 chapter" + "Batalkan"; sebagian → "Lanjutkan Download";
  selesai → centang + "Tersimpan semua" (nonaktif). "Tersimpan semua"
  tidak pernah muncul selama masih ada chapter belum lengkap.
* **369 unit test lulus**, 0 gagal (33 kelas). `VolumeCoversAndDownloadStateTest`
  (17 tes) memakai `yariko_article_covers.html` — artikel asli yang
  memicu laporan — dan mengukur UI pada layar 320 dp.
  `RingProgressMotionTest` (5 tes) menguji cincin progres langsung:
  nilainya tampil seketika saat "Remove animations" aktif, animasi
  berakhir tepat pada angka yang diberikan, spinner tanpa total tidak
  pernah melaporkan persen, dan fraksi di luar 0–100 dijepit.
* Uji mutasi 2.18: **15 mutasi pada berkas hidup** (cover tidak ditempel,
  semua volume memakai cover pertama, tabel menimpa hasil parse, novel
  asing meminjam tabel Magical, persentase volume dipalsukan, "Tersimpan
  semua" padahal belum, persentase menempel ke status, angka persen
  dikarang, chapter parsial dianggap lengkap, `isComplete` disamakan
  `isSaved`, gambar rusak tetap ditulis, progres byte tidak diteruskan,
  animasi dijalankan walau "Remove animations" aktif, spinner melaporkan
  persen, fraksi di luar 0–100 tidak dijepit) — **15/15 tertangkap**,
  pemulihan diverifikasi `md5` sebelum/sesudah.
* **BUG 12 (2.17) — sumber katalog Monogatari diganti ke artikel
  daftar isi sumbernya.** Gejala: Monogatari gagal dengan "Respons feed
  halaman ke-1 tidak dapat dibaca" dan daftar chapter jadi **0**, karena
  Monogatari satu-satunya novel yang katalognya hanya bergantung pada feed
  label Blogger — satu halaman gagal berarti seluruh katalog hilang.
  Sumbernya sendiri punya artikel daftar isi lengkap
  (`2025/09/ln-monogatari-no-kuromaku-ni-tensei.html`) yang belum pernah
  dibaca aplikasi. Artikel itu tidak identik dengan feed: ada **7 heading
  volume** (parser generik hanya menghasilkan 111 chapter karena heading
  volume tidak dibaca sebagai penanda), dan entri `Prolog` Volume 2 menunjuk
  URL yang sama dengan `Chapter 1` (`...volume-2_49.html`) sedangkan Prolog
  sebenarnya ada di `...volume-2_12.html`. Parser lama juga membuang tautan
  kedua saat URL kembar, sehingga `Chapter 1` hilang seluruhnya. Perbaikan:
  mode baru `CATALOG_ARTICLE_FEED` dengan `CatalogueMerge` yang memakai
  artikel sebagai urutan/volume/judul, memperbaiki tautan yang salah dari
  feed, dan menambahkan chapter yang hanya diketahui feed; kunci duplikat
  `LandingCatalogParser` diubah dari URL saja menjadi `URL | volume | label`.
  Bila artikel gagal, katalog diambil dari feed disertai peringatan jujur;
  bila **keduanya** gagal, loader melempar galat dan katalog lama di rak
  tidak diganti daftar kosong. Baris lama di perpustakaan dipindahkan ke
  sumber baru lewat `upgradeBuiltIns()` tanpa menyentuh `id`, posisi, judul,
  metadata tersimpan, progres membaca, bookmark, atau unduhan offline —
  tidak ada baris baru, jadi tidak ada duplikat. "Muat Ulang" memakai jalur
  yang sama, sehingga chapter baru ikut terambil tanpa update APK.
* **Hasil ukur pada sumber asli (2.17):** Monogatari **116 chapter di 7
  volume** (14+18+17+17+16+18+16) dengan **7 dari 7 cover volume** dan
  penulis **Yuuki Ryou** terbaca dari artikel — sebelumnya 0 chapter.
  Magical★Explorer tetap **141 chapter / 13 volume berisi** (14 volume,
  volume 14 hanya cover), Yarikon tetap **43 chapter / 3 volume**
  (16+13+14). Angka 116 = 115 tautan unik di artikel + 1 chapter yang hanya
  dikenal feed; aplikasi menampilkan peringatan "1 tautan yang salah di
  artikel diperbaiki dari feed label".
* **283 unit test lulus**, 0 gagal (27 kelas). `MonogatariCatalogueTest`
  (9 tes) memakai dua fixture dari sumber asli (`monogatari_article.html`,
  `monogatari_feed.json` — 117 entri feed) dan menjaga: katalog memakai
  artikel+feed, Prolog Volume 2 memakai `_12` sementara `Chapter 1` tetap
  `_49`, chapter yang hanya diketahui feed bertambah saat Muat Ulang, kedua
  sumber gagal → galat bukan daftar kosong, baris lama dipindahkan sumbernya
  tanpa kehilangan identitas/progres, dan tidak ada chapter dihitung dua kali.
* Uji mutasi 2.17: lima mutasi (perbaikan tautan kembar dimatikan,
  penambahan chapter dari feed dimatikan, `LandingCatalogParser` kembali
  dedupe URL saja, loader mengembalikan katalog kosong, upgrade baris lama
  dimatikan) — **kelima-limanya tertangkap**, pemulihan diverifikasi lewat
  `md5` sebelum/sesudah.
* **Bug 2.15 yang diperbaiki** — bentuk alamat yang didukung tidak
  diberitahukan. Petunjuk di bawah judul halaman Tambah Novel hanya
  menyebut artikel katalog, padahal URL label `/search/label/...` sudah
  didukung sejak 2.10 — dan justru bentuk itu yang memberi daftar chapter
  lengkap dalam satu langkah. Petunjuk sekarang menyebut kedua bentuk
  beserta contoh jalurnya. Satu tes Robolectric menjaga teks itu, dan
  satu mutasi terbukti tertangkap.
* **Bug 2.14 yang diperbaiki** — panel diagnostik menyebut "Tautan
  ditemukan di artikel" untuk alamat label juga, padahal tautan label
  datang dari feed JSON per-label dan halaman artikelnya tidak pernah
  dibaca. Kalimatnya sekarang dipilih dari jenis alamat: "di feed label"
  untuk URL label, "di artikel" untuk URL artikel. Angkanya tidak
  berubah, hanya keterangannya yang jujur. Dua tes Robolectric menjaga
  kedua jenis alamat, dan satu mutasi terbukti tertangkap.
* **Bug 2.13 yang diperbaiki** — panel diagnostik menyebut angka yang
  tidak ada di daftar. Untuk URL label Yariko panel menulis "43 chapter
  diambil dari feed" di atas daftar berisi 42. Angka itu dihitung dari
  jumlah postingan feed yang lolos penyaringan deskripsi, padahal
  `LibraryBuilder` menggabungkan satu judul yang terbit dua kali: 44
  postingan -> 43 setelah penyaringan -> **42** chapter unik. Semua angka
  di panel sekarang dihitung dari daftar yang benar-benar tampil, di kedua
  jalur (preview dan Muat Ulang), dan kalimat penggabungan menyebut ketiga
  angka sekaligus supaya selisihnya bisa dilacak. Dua mutasi dijalankan,
  keduanya tertangkap.
* **Bug 2.12 yang diperbaiki** — daftar chapter berubah setelah Muat
  Ulang. Novel yang disimpan lewat URL label dibaca oleh dua fungsi
  berbeda: jalur Tambah Novel menyaring postingan deskripsi novel,
  jalur Muat Ulang tidak. Hasilnya, novel yang baru ditambahkan terlihat
  benar (42 chapter) lalu bertambah satu chapter palsu di "Volume 0"
  setelah dimuat ulang. Jalur Muat Ulang juga memakai `Http` langsung
  alih-alih seam `Fetcher`, sehingga tidak bisa diuji tanpa jaringan —
  itulah sebabnya cacat ini lolos. Sekarang kedua jalur memakai
  penyaringan yang sama, ringkasan status menyebut alasan selisih
  jumlahnya, dan label yang hanya berisi postingan deskripsi ditolak
  dengan pesan, bukan disimpan tanpa chapter. Dua mutasi dijalankan dan
  keduanya tertangkap.
* **Bug 2.11 yang diperbaiki** — dua URL dari laporan:
  (1) URL label `/search/label/...` ditolak sebelum diunduh karena jenis
  alamat ketiga memang belum ada; sekarang label dibaca lewat feed JSON
  per-label dengan pagination (feed Yariko: 44 postingan / 2 halaman),
  judul novel diambil dari nama label, dan cover tidak dipinjam dari
  ilustrasi chapter. (2) Artikel `/2026/03/yariko...` berisi 47 tautan
  dan 43 chapter, tapi dilaporkan "tidak ada tautan chapter" karena
  pembersih boilerplate membuang `div.widget` sebelum artikel dicari —
  padahal `div.post-body` adalah keturunan `div.widget.Blog`, jadi
  artikelnya ikut terhapus. Sekarang artikel dicari dulu, baru dibersihkan,
  dan hanya di dalam artikel. (3) Tautan yang sudah ditolak tidak bisa
  lagi dicuci menjadi kandidat manual. (4) `Interlude I/II/III` tidak lagi
  runtuh jadi satu chapter. (5) Postingan deskripsi novel tidak dihitung
  sebagai chapter.
* **Diagnostik tautan di preview**: jumlah tautan ditemukan, jumlah yang
  dikenali sebagai chapter, dan alasan setiap tautan ditolak. Setiap alasan
  menyebut apa tautan itu sebenarnya — "tautan gambar, bukan chapter",
  "potongan judul, sudah digabung ke tautan sebelumnya", "milik novel lain",
  "halaman label/feed". Bila belum ada yang bisa dipastikan, daftar tautan
  yang bisa dipakai ditampilkan dengan kotak centang; novel disimpan
  setelah **Pakai pilihan ini**, dan tautan yang sudah ditolak tidak pernah
  muncul di daftar itu.
* **Hasil parse terukur**: artikel Yariko 43 chapter / 3 volume; label
  Yariko 42 chapter / 3 volume; Magical★Explorer tetap 141 chapter /
  14 volume; Monogatari tetap 111 chapter / 7 volume.
* **Dua cacat lagi yang hanya muncul saat diuji dengan HTML asli**: tiga
  pembungkus gambar di halaman laporan dilaporkan dengan alasan generik
  "sendiri di grup volume" padahal itu tautan gambar, dan sumber memecah
  "Interlude 2" menjadi `<a>Interlude </a><a>2</a>` sehingga chapter itu
  berlabel "Interlude" tanpa angka. Keduanya sekarang dinamai benar, dan
  angka Romawi/arab dinormalkan supaya `Interlude II` dan `Interlude 2`
  adalah chapter yang sama.
* **Uji mutasi 2.11**: sepuluh mutasi dijalankan satu per satu dan setiap
  kali ada tes bernama yang gagal. Satu mutasi sempat lolos — cabang
  "potongan judul" tidak pernah dieksekusi karena label di halaman laporan
  sudah digabung lebih dulu — sehingga tes dengan angka liar yang berdiri
  sendiri ditambahkan untuk menutup celah itu.
* Seluruh fitur 2.8 dipertahankan: download offline per chapter/volume,
  WorkManager, penulisan atomik, halaman Unduhan dengan status
  Belum/Mengunduh/Tersimpan/Gagal, header dengan WindowInsets yang
  benar, animasi 180–250 ms, dan dua novel bawaan.

**Batasan yang perlu diketahui**: parser generik hanya menguji halaman
Kaori Translation; hanya host `kaoritranslation.blogspot.com` yang
diterima; tautan chapter harus berpola post bertanggal `/20xx/xx/`;
katalog yang menautkan halaman statis `/p/` atau yang daftar chapternya
dimuat JavaScript akan ditolak dengan pesan, bukan ditebak. Novel
dengan tata letak jauh berbeda belum tentu terbaca — preview akan
mengatakannya. Verifikasi UI masih lewat Robolectric: halaman Tambah
Novel dan dialog hapus belum dilihat di layar perangkat sungguhan.

APK debug, hasil build otomatis dari commit `@COMMIT@`.
Aplikasi tidak memuat salinan chapter; yang ditanam hanya sinopsis resmi dari halaman
novel dan URL cover. Seluruh konten milik Kaori Translation.
