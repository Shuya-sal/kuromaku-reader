Pembaca native untuk novel dari Kaori Translation. Dua novel bawaan
(*Monogatari no Kuromaku* dan *Magical★Explorer*) tetap ada, dan novel
lain bisa **ditambahkan sendiri lewat URL** tanpa memperbarui APK.

* Versi: **2.20.2** (versionCode 45) — butuh **Android 8.0 (API 26)** atau lebih baru.
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
* **367 unit test lulus**, 0 gagal (33 kelas). `VolumeCoversAndDownloadStateTest`
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
