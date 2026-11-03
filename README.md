# Kuromaku Reader 2.9

Aplikasi pembaca Android **native** untuk novel *Monogatari no Kuromaku* di label
[Kaori Translation](https://kaoritranslation.blogspot.com/search/label/Monogatari%20no%20Kuromaku).

Versi 1.0 hanya membungkus website di dalam WebView, sehingga iklan, sidebar, dan
menu Blogspot ikut tampil. Versi 2.0 membuang WebView sepenuhnya: isi chapter
diambil lewat HTTP, diparse dengan Jsoup, lalu dirender sebagai tampilan baca native.
Versi 2.1 memperbaiki katalog yang hanya memuat Volume 5–7. Versi 2.2 memperbaiki
**akar** bug scroll yang masih tersisa, dan mendesain ulang halaman awal menjadi
halaman detail novel. Versi 2.3 memperbaiki **sistem gambar**: ilustrasi di dalam
chapter dan seluruh isi postingan "Illustrasi" kini benar-benar tampil.

Versi 2.4 menambahkan **novel kedua**, *Magical★Explorer*, dan memperbaiki tampilan
daftar volume: halaman perpustakaan berisi kartu cover kedua novel, halaman detail
tiap novel menampilkan **cover berbeda untuk setiap volume**, progres baca disimpan
terpisah per novel, dan navigasi chapter tidak pernah menyeberang ke novel lain.

Versi 2.5 memoles **header dan animasi**: toolbar ringkas dengan status bar inset
yang benar, tombol **Kembali** berlabel yang mudah ditekan, tombol aksi berbentuk
ikon, dan animasi navigasi 180–250 ms yang menghormati pengaturan
*Remove animations* Android. Tidak ada fitur 2.4 yang dihapus.

Versi 2.6 memperbaiki **BUG status bar**: header bertabrakan dengan jam setelah
tema diganti terang → gelap. Akarnya ada di pengelolaan `WindowInsets`, bukan di
ukuran padding. Detail lengkap di bagian **BUG 4 (2.6)**.

Versi 2.7 menambahkan **Download Offline**: chapter dan seluruh volume dapat
diunduh lebih dulu, lalu dibaca tanpa internet termasuk semua ilustrasi. Teks
disimpan sebagai JSON terstruktur yang mempertahankan urutan blok dan posisi
gambar, sedangkan setiap ilustrasi disimpan sebagai file di penyimpanan internal
aplikasi. Unduhan berjalan lewat **WorkManager** (bertahan saat aplikasi ditutup,
maksimal 2 pekerja paralel, retry backoff), ditulis secara **atomik**, dan
diverifikasi sebelum chapter ditandai tersimpan. Ada halaman **Unduhan** dengan
jumlah chapter, ukuran penyimpanan, serta hapus per chapter/volume — tanpa
menyentuh progres membaca. Detail di bagian **Fitur Download Offline (2.7)**.

Versi 2.9 menambahkan **pengelolaan novel dinamis**: novel baru bisa ditambahkan
sendiri lewat URL halaman katalog, tidak lagi bergantung pada dua novel yang
ditanam di dalam APK. Perpustakaan disimpan di **Room Database** dengan kunci URL
yang sudah dinormalisasi, sehingga URL yang sama tidak pernah menjadi dua novel,
daftar novel tetap ada setelah aplikasi ditutup atau HP direstart, dan dua novel
lama dipertahankan saat migrasi. Setiap kartu punya **menu tiga titik** (Buka,
Perbarui, Hapus) dan penghapusan menawarkan dua pilihan terpisah: hapus dari
perpustakaan saja dengan unduhan serta progres tetap tersimpan, atau hapus beserta
unduhan offline dengan centang terpisah untuk progres membaca. Detail di bagian
**Fitur Pengelolaan Novel Dinamis (2.9)**.

## Fitur (baru di 2.5)

**Header ringkas di seluruh halaman**
- Toolbar satu baris setinggi **58 dp** (di luar system inset) dengan garis pemisah
  tipis. Status bar inset diterapkan sebagai padding **di atas** toolbar, jadi judul
  tidak pernah bertabrakan dengan jam atau ikon baterai, termasuk di layar dengan notch.
- Judul toolbar **selalu satu baris** dan memakai ellipsize; teks yang panjang tidak
  bisa lagi membungkus dan membuat toolbar meninggi.
- Judul tidak diulang: toolbar menulis nama halaman (**Perpustakaan** / **Detail Novel**),
  sedangkan nama aplikasi muncul satu kali di awal daftar novel.
- Halaman detail memakai judul singkat **Detail Novel**; judul lengkap novel tetap
  ditampilkan di bagian konten bersama cover, metadata, sinopsis, dan daftar volume.
- Reader Mode memakai toolbar 56 dp: tombol Kembali, judul chapter satu baris, dan
  ikon pengaturan tampilan. Metadata chapter dipindah ke baris kecil di bawahnya.

**Tombol Kembali**
- Berupa rounded rectangle berisi ikon panah kiri dan teks **Kembali**, tinggi 48 dp,
  lebar minimal 92 dp, dengan ripple sesuai tema gelap/terang.
- Dipakai konsisten di halaman detail novel, daftar chapter, dan Reader Mode, serta
  pada penampil ilustrasi (label "Tutup").
- Tombol Back Android tetap bekerja: dari detail kembali ke perpustakaan, dari
  Reader Mode menutup panel pengaturan lebih dulu.

**Tombol aksi header**
- **Muat ulang** menjadi ikon refresh, **Gelap/Terang** menjadi ikon bulan/matahari;
  keduanya kotak 48 dp dengan `contentDescription` untuk pembaca layar.
- Jarak antar elemen konsisten (padding 6 dp + margin 2–6 dp) sehingga toolbar tidak
  pernah melar karena teks terbungkus.

**Animasi navigasi**
- Perpustakaan → Detail novel: fade-in + slide ringan dari kanan (220 ms).
- Detail novel → Perpustakaan: slide ringan ke kanan + fade-out, lalu halaman tujuan
  fade-in dari kiri (220 ms).
- Detail novel → Daftar chapter dan Daftar chapter → Reader Mode: fade-in + slide
  ringan memakai `overridePendingTransition` dengan durasi 220 ms.
- Accordion volume: expand dari tinggi 0 ke tinggi alami dan collapse ke 0 (200 ms).
- Kartu novel, baris volume, baris chapter, tombol Kembali, dan tombol ikon memakai
  `RippleDrawable` yang diwarnai sesuai tema.
- Semua animasi **menghormati "Remove animations"**: bila skala animator sistem 0,
  `Motion.enabled()` mengembalikan `false` dan perubahan state langsung diterapkan
  tanpa animasi — navigasi tidak pernah tertahan.
- Hanya `alpha`, `translationX`, dan tinggi accordion yang dianimasikan; tidak ada
  animasi layout daftar penuh, sehingga tidak menimbulkan lag.

## Fitur

**Halaman awal (baru di 2.2)**
- Header Kuromaku Reader, lalu halaman detail novel di bagian atas:
  sampul (rasio 2:3 sesuai artwork asli), judul lengkap, metadata terverifikasi,
  sinopsis dengan **Baca selengkapnya**, dan tombol **Lanjutkan Membaca**.
- Daftar volume berbentuk **accordion**; chapter hanya dimuat saat volume dibuka.
  Status buka/tutup disimpan, jadi tidak berubah saat aplikasi dibuka lagi.
- Sampul yang gagal dimuat menampilkan placeholder rapi, bukan kotak kosong.

**Metadata (semua dari halaman sumber, tidak ada yang dikarang)**
- Penulis: Yuuki Ryou · Ilustrator: Nakamura
- Genre: Action, Adventure, Fantasy, Harem, Romance, School Life
- Terjemahan: Kaori Translation (Bahasa Indonesia)
- Status: halaman sumber **tidak mencantumkan** status, jadi aplikasi menulis
  "Tidak dicantumkan di halaman sumber" daripada menebak *Ongoing*/*Completed*.

**Perpustakaan**
- Daftar volume dan chapter dibangun dari feed label resmi Blogspot (JSON), bukan dari halaman HTML penuh.
- **Semua halaman feed dibaca** sampai Blogger mengembalikan halaman kosong, bukan berhenti di halaman pendek.
- Pengelompokan otomatis per volume, urutan baca alami: Prolog → Chapter 1..N → Interlude → Short/Side Story → Epilog → Afterword → Illustrasi.
- Volume diurutkan menaik (Volume 1 di atas), chapter diurutkan numerik (Chapter 2 sebelum Chapter 10).
- Penanda progres per chapter (`42%`, `selesai`) dan tombol "Lanjutkan Membaca".
- Tombol **Muat ulang** di bar atas untuk mengambil katalog terbaru.

**Reader Mode**
- Ekstraksi hanya isi artikel: judul, heading, paragraf, kutipan, daftar, dan ilustrasi.
- Urutan paragraf dan gambar dipertahankan; URL gambar asli dipakai (varian `=s1600` agar tidak buram).
- Script, `<ins class="adsbygoogle">`, iframe, share widget, komentar, sidebar, dan footer situs dibuang sebelum render.
- Tidak ada JavaScript yang dijalankan di aplikasi — konten diolah sebagai teks, bukan halaman web.

**Ilustrasi (baru di 2.3)**
- Gambar tampil dengan **kotak yang dipesan sejak layout pertama**, ukurannya diambil dari
  `data-original-width` / `data-original-height` milik sumber, jadi rasio asli terjaga
  dan tidak ada ruang kosong besar ketika gambar gagal dimuat.
- Lebar responsif mengikuti kolom baca; tinggi mengikuti rasio gambar.
- Ketuk gambar → penampil layar penuh dengan cubit/zoom **1x–4x**, geser, dan ketuk dua kali.
- Gagal dimuat → pesan jelas, tahap kegagalan, dan tombol **Coba lagi**.
- Logging `KuromakuImage` mencatat tahap (`antrean`, `unduh`, `selesai`, `url`, `loader`)
  dengan URL disamarkan (tanpa token panjang).

**Dua novel (baru di 2.4)**
- Halaman perpustakaan: satu kartu per novel berisi cover, judul, judul Jepang, dan
  ringkasan `N volume · M chapter` dari katalog novel itu sendiri.
- Halaman detail per novel: cover utama, judul lengkap, penulis, ilustrator, genre,
  status, terjemahan, sinopsis, **Lanjutkan Membaca**, lalu daftar volume.
- **Setiap volume punya cover sendiri** (720–1160 px, rasio asli terjaga, tidak
  terpotong). Cover yang diketuk membuka penampil zoom penuh.
- Volume tanpa gambar di halaman sumber memakai placeholder rapi — tidak pernah
  meminjam cover Volume 1.
- Volume yang belum punya tautan chapter (mis. Volume 14 Magical★Explorer) tetap
  ditampilkan dengan keterangan "belum ada chapter", bukan chapter palsu.
- Progres baca, posisi gulir, dan status accordion disimpan **per novel** dengan
  prefiks `<novel>:`. Tema, ukuran huruf, dan spasi baris tetap berlaku aplikasi.
- Tombol Sebelumnya/Berikutnya memakai katalog novel yang sedang dibuka, jadi tidak
  bisa berpindah ke chapter novel lain.

**Dua bentuk katalog**
- **Monogatari no Kuromaku** → feed JSON label Blogger, dikelompokkan per volume dari
  judul postingan.
- **Magical★Explorer** → halaman katalognya memuat daftar volume dan tautan chapter
  langsung, tanpa label Blogger tunggal. `LandingCatalogParser` membaca judul
  `Volume N` lalu tautan di bawahnya, dan setiap chapter mewarisi volume di atasnya.
  Ejaan sumber yang salah seperti **"Cahpter 2"** tetap diurutkan di posisi kedua.

**Kenyamanan baca**
- Tema Terang / Gelap / Sepia.
- Ukuran huruf, spasi baris, dan rata kanan-kiri yang bisa diatur.
- Progres membaca disimpan per chapter **beserta posisi blok dan offset piksel**.
- Tombol Sebelumnya / Berikutnya / Kembali ke perpustakaan, plus nomor posisi `12/117` dan persen berjalan.
- Panel navigasi berada **di bawah** area baca dalam layout vertikal, bukan overlay di atas teks.

**Penanganan kegagalan**
- Jika kontainer artikel tidak ditemukan atau isinya kosong, layar menampilkan alasan kegagalan,
  tombol "Coba lagi", tautan sumber yang bisa disalin, dan tombol buka browser.
- Kegagalan feed menyebut halaman dan `start-index` yang gagal, bukan pesan generik.
- Crawl yang tidak lengkap tetap menampilkan daftar, dengan peringatan "Hanya X dari Y postingan".
- Aplikasi **tidak** pernah diam-diam beralih menampilkan website penuh.

Versi 2.8 memperbaiki **BUG halaman blank**: menekan ikon Unduhan bisa
meninggalkan toolbar hidup dengan seluruh isi di bawahnya hilang. Akarnya bukan
data kosong, melainkan urutan render dan thread. Detail di bagian **BUG 5 (2.8)**.

## Fitur Download Offline (2.7)

Pengguna dapat mengunduh chapter satu per satu atau seluruh volume, lalu membaca
tanpa koneksi. Ilustrasi ikut tersimpan, jadi chapter tetap utuh di mode pesawat.

### Format penyimpanan

Semua data berada di penyimpanan internal aplikasi (`getFilesDir()/offline`) dan
di-namespace per novel, sehingga Monogatari dan Magical★Explorer tidak pernah
tercampur:

```
offline/
  <novelId>/
    chapters/
      <hash-url>.json      teks terstruktur + daftar gambar
    images/
      <hash-gambar>.<ext>  berkas gambar asli
```

- **Identifier stabil**: `sha256(novelId + "\n" + urlChapter)` dipotong 32 hex.
  URL chapter adalah identitas chapter di sumber, jadi unduhan lama tetap
  dikenali walau katalog dimuat ulang atau nomor volume berubah.
- **Teks tidak disimpan sebagai HTML**. JSON menyimpan daftar blok berurutan:
  `type` (`title`/`paragraph`/`image`), `text`, serta untuk gambar `url` asli,
  `path` berkas lokal, dan `width`/`height`. Urutan blok adalah urutan baca, jadi
  posisi gambar di antara paragraf tetap sama seperti di sumber.
- **Gambar bukan URL**. Setiap ilustrasi diunduh ke berkas sendiri. Saat membaca
  offline, `Block.imageSource()` mengembalikan path file, bukan URL, sehingga
  tidak ada permintaan jaringan sama sekali. Berkas gambar yang sama dipakai
  beberapa chapter hanya disimpan sekali (`images/` bersifat content-addressed
  per hash URL gambar).
- **Penulisan atomik**. JSON ditulis ke `<nama>.json.tmp`, di-`fsync`, lalu
  di-`rename` menjadi `<nama>.json`. Chapter hanya dianggap tersimpan bila berkas
  final ada dan bisa diparse, jadi unduhan yang terputus tidak pernah tampil
  sebagai selesai.
- **Validasi sebelum menandai tersimpan**: HTTP 200, `Content-Type` gambar,
  signature JPEG/PNG/GIF/WebP yang cocok, dimensi terbaca lewat `BitmapFactory`,
  dan minimal satu blok teks. Gambar yang gagal validasi tidak disimpan.
- **Pembersihan**: berkas `.tmp` sisa dan gambar tanpa rujukan dibuang saat
  penyimpanan diakses, sehingga tidak ada duplikasi atau file tak terpakai.
- **Hapus unduhan tidak menghapus progres**. Progres baca dan bookmark tetap di
  `SharedPreferences` (`Prefs`) dengan kunci per novel, terpisah dari berkas
  offline.

### Antrean dan batas beban sumber

- **WorkManager** menjalankan pekerjaan, jadi unduhan lanjut walau aplikasi
  ditutup, dan berhenti bila proses dimatikan sistem tanpa merusak data
  (penulisan atomik).
- Maksimal **2 pekerja paralel** (`DownloadQueue.MAX_PARALLEL`) agar situs sumber
  tidak dibanjiri permintaan.
- Retry memakai **backoff eksponensial** dari WorkManager. Tidak ada polling dan
  tidak ada pengulangan permintaan tanpa sebab.
- Volume berjalan **berurutan** chapter demi chapter, menampilkan `x dari y
  chapter`, dan menyediakan **Batalkan** yang menghentikan pekerjaan secara
  kooperatif (chapter yang sedang ditulis diselesaikan atau dibatalkan bersih).
- Chapter yang sudah tersimpan **dilewati**, jadi menekan Download Semua lagi
  hanya mengunduh yang belum ada.

### Reader Mode offline

- Reader memeriksa penyimpanan lokal lebih dulu. Bila ada, konten dibaca dari
  berkas dan **tidak ada satu pun permintaan jaringan**.
- Bila tidak ada internet dan chapter belum diunduh, Reader menampilkan pesan
  jelas beserta tombol coba lagi dan tautan ke sumber asli — bukan layar kosong.
- Ukuran font, spasi baris, tema, scrolling, posisi gulir, dan progres membaca
  tetap memakai mekanisme 2.4–2.6 tanpa perubahan.

### BUG 5 (2.8) — Halaman blank setelah menekan ikon Download

**Gejala.** Di halaman Detail Novel, menekan ikon download di header
menghilangkan seluruh isi di bawah header. Header masih terlihat, tetapi tidak ada
daftar unduhan, indikator loading, pesan error, maupun konten novel.

**Akar masalah.** Dua cacat terpisah, dan keduanya harus diperbaiki:

1. **Layar dikosongkan sebelum halaman dibangun.** `render()` memanggil
   `content.removeAllViews()` lebih dulu, baru kemudian membangun halaman baru.
   Kalau pembangunan halaman melempar exception — index unduhan rusak, satu status
   chapter gagal dibaca, katalog belum siap — layar sudah kosong lebih dulu dan
   exception itu tidak punya jalan untuk menampilkan apa pun. Hasilnya persis
   keluhan: toolbar hidup, isi hilang, tanpa pesan.
2. **Perubahan status dikirim dari thread worker.** `DownloadStatusStore`
   memanggil semua listener tepat di thread pemanggil, dan `DownloadWorker`
   memanggilnya dari thread latar. Listener di activity kemudian membangun ulang
   view dari thread non-main — pekerjaan view gagal, halaman berhenti setengah
   jadi, dan sisanya kosong. Di Robolectric ini tidak terlihat karena semua
   dijadwalkan di main looper.

**Perbaikan.**

- `render()` sekarang **membangun halaman lebih dulu**, lalu menukarnya. Kalau
  pembangunan gagal, yang ditampilkan adalah `page_failure`: judul
  "Halaman Unduhan gagal dimuat", alasannya, dan tombol **Coba lagi** berukuran
  48 dp — bukan layar kosong. Halaman lama tetap terlihat sampai halaman baru siap.
- `DownloadStatusStore.notifyListeners()` mengirim ke **main thread**
  (`Handler(Looper.getMainLooper())`). Kalau perubahan memang terjadi di main
  thread, pengiriman tetap sinkron sehingga urutan tidak berubah. Setiap listener
  dibungkus `try/catch` dan dicatat ke log, jadi satu layar yang gagal repaint
  tidak menghentikan yang lain.
- **Navigasi diperbaiki**: membuka Unduhan tidak lagi mengosongkan
  `selectedNovel`, sebab novel yang terbuka itulah tujuan tombol Kembali dan
  "Lihat Chapter". Posisi gulir halaman detail disimpan di `detailScrollY` dan
  dipulihkan saat kembali.

**Bukti tes menangkap bug.** Tiga bug dikembalikan sementara:

1. `content.removeAllViews()` dipindah ke depan dan guard dihapus;
2. listener dipanggil inline lagi (bukan lewat main thread);
3. keadaan kosong ditampilkan walau ada unduhan berjalan.

Hasil: **3 tes gagal**, yaitu `aPageBuildFailureNeverLeavesABlankScreen`,
`statusChangesAreDeliveredOnTheMainThread`, dan `activityShowsRunningProgress`.
Mutasi pertama awalnya lolos karena tidak ada jalur yang benar-benar melempar
exception saat membangun halaman; tes ditambah dengan seam `MainActivity.pageFault`
yang sengaja melempar, lalu mutasi yang sama tertangkap. Setelah dipulihkan,
**207/207** lulus.

## Fitur Pengelolaan Novel Dinamis (2.9)

### Tambah novel lewat URL

Halaman **Perpustakaan** punya tombol **+ Tambah Novel** (tinggi minimal 48 dp)
yang membuka halaman input URL. Alur kerjanya:

1. URL dinormalisasi lebih dulu, sebelum ada permintaan jaringan. `?m=1`, garis
   miring di akhir, parameter pelacak, `www`, dan perbedaan `http`/`https`
   dibuang. URL yang bukan halaman artikel Kaori Translation — beranda, halaman
   label, feed, atau situs lain — ditolak dengan pesan yang bisa dibaca.
2. Halaman katalog diunduh dan dibaca oleh parser generik.
3. **Preview** ditampilkan: judul, judul Jepang, cover, penulis, ilustrator,
   genre, status, sinopsis, jumlah volume, jumlah chapter, dan daftar
   peringatan. Metadata yang tidak tertulis di halaman ditampilkan sebagai
   *Tidak tercantum di halaman sumber* — tidak ada yang dikarang.
4. Tombol **Simpan ke Perpustakaan** menulis satu baris ke database. Novel yang
   sudah ada ditolak sebagai duplikat, tombol simpan dimatikan, dan preview
   menampilkan bahwa novel itu sudah ada.

Kegagalan pengambilan menampilkan alasannya beserta tombol **Coba Lagi**;
indikator loading tampil selama pemrosesan. Novel kosong tidak pernah disimpan.

### Parser generik dan batasnya

`NovelDiscovery` bekerja dari apa yang dimiliki bersama oleh halaman-halaman
Kaori Translation, bukan dari bentuk satu novel tertentu:

- **Metadata** dibaca dari baris berlabel (`Author :`, `Illustrator :`, `Genre :`,
  `Japanese Tittle :`, `Status :`), termasuk bentuk yang labelnya ditebalkan
  sehingga titik dua jatuh di baris berikutnya.
- **Chapter** dikenali dari tautan di dalam badan artikel yang teksnya berbentuk
  chapter (`Chapter 12`, `Prolog`, `Epilog`, `Afterword`, `Illustrasi`, termasuk
  salah ejaan sumber seperti `Cahpter 2`) atau yang slug-nya mengandung
  `chapter-N` / `volume-N`. Menu, sidebar, tombol bagikan, halaman label, beranda,
  dan postingan novel lain dikecualikan.
- **Volume** diambil dari heading `Volume N` di atas tautan, lalu dari teks
  tautan, lalu dari slug. Halaman tanpa heading volume (landing Monogatari: 116
  tautan, 0 heading) mengelompokkan chapter menurut slug-nya.
- **Cover** adalah gambar potret pertama di seluruh artikel, termasuk yang
  dibungkus `<a>` — cara sumber menaruh sampul. Banner lebar di awal deskripsi
  tidak dipakai, dan rasionya diambil dari dimensi gambar itu.
- **Peringatan** dikumpulkan, bukan disembunyikan: jumlah tautan chapter yang
  tidak punya tautan, jumlah chapter tanpa nomor volume, dan field metadata yang
  tidak tercantum.

Yang **tidak** didukung, dan karenanya dilaporkan sebagai kegagalan alih-alih
ditebak: daftar chapter yang dimuat dengan JavaScript, katalog yang menautkan
halaman statis `/p/`, halaman dari host selain `kaoritranslation.blogspot.com`,
dan tautan chapter yang tidak berada di path post bertanggal `/20xx/xx/`.

### Simpan permanen, tanpa duplikat

- Database: Room, entitas `NovelRecord`, versi 1, satu tabel `novels`.
- Kunci baris adalah URL katalog ternormalisasi; `id` adalah slug URL dan dipakai
  untuk preferensi, folder offline, dan navigasi reader. `id` ditulis sekali saat
  novel ditambahkan dan **tidak pernah** dihitung ulang dari judul, sehingga
  mengganti judul di sumber tidak memutus progres membaca.
- Saat tabel masih kosong, dua novel bawaan ditulis otomatis dengan metadata
  terverifikasinya — itu sekaligus jalur migrasi dari versi yang menanam keduanya
  di kode.
- **Muat Ulang** membaca ulang halaman dan memperbarui metadata, tetapi hanya
  kolom yang benar-benar dicantumkan halaman. Kolom kosong di halaman tidak
  menghapus nilai tersimpan, dan id, posisi, serta URL tidak berubah.

### Hapus novel

Menu tiga titik pada setiap kartu berisi **Buka Novel**, **Perbarui Novel**, dan
**Hapus dari Perpustakaan**. Dialog hapus menyebut judul novel, jumlah chapter
yang tersimpan beserta ukurannya, dan dua centang:

1. **Hapus juga unduhan offline** — diaktifkan hanya bila memang ada unduhan.
2. **Hapus juga progres membaca dan bookmark** — berlaku terpisah.

Tanpa centang apa pun, hanya baris perpustakaan yang dihapus: berkas offline dan
progres tetap di perangkat. Urutannya sengaja begitu — baris dihapus terakhir,
supaya kegagalan saat menghapus berkas tidak meninggalkan novel yang hilang dari
daftar tetapi datanya setengah terhapus. Semua data offline dan preferensi
memakai namespace per novel, jadi menghapus satu novel tidak menyentuh novel lain.

## BUG 6 (2.9) — Parser generik salah membaca metadata, cover, dan identitas

Empat cacat ditemukan lewat **probe parser** yang dijalankan pada dua halaman asli,
bukan dari membaca kode. Semuanya lolos kompilasi dan tidak terlihat di APK sampai
hasil parse-nya dicetak.

**(1) Teks terbaca tiga kali.** `textOf()` memanggil `Element.text()` untuk setiap
elemen dalam daftar yang sudah diratakan, sehingga satu kalimat muncul sekali per
tingkat leluhurnya. Akibat nyata: `Genre : Action, Adventure, ...` terbaca sebagai
`genre = "Genre"` (labelnya sendiri jadi nilainya) dan sinopsis 10 paragraf runtuh
menjadi satu blok. Perbaikan: hanya text node serta elemen daun (`a`, `img`) yang
menyumbang teks.

**(2) Cover memilih banner, dan cover yang dibungkus tautan tidak terbaca.**
Aturan lama adalah "gambar pertama", sehingga banner promosi 1526×1080 dipakai
sebagai sampul. Lebih buruk lagi: `flatten()` tidak menelusuri isi `<a>`, padahal
sumber membungkus sampul dengan tautan — jadi cover portrait yang benar-benar ada
tidak pernah terlihat. Perbaikan: ambil **gambar potret pertama di seluruh artikel**
termasuk yang berada di dalam tautan, dan hitung rasio dari dimensi gambar itu.
Sekarang menghasilkan `m3.jpg` (rasio 0,704) untuk Monogatari dan cover WordPress
(rasio 0,705) untuk Magical — sesuai artwork aslinya.

**(3) Label tebal tidak terbaca.** Halaman menulis `<b>Penulis</b> : Nilai`, sehingga
baris `Penulis` berakhir sebelum titik dua dan nilainya jatuh ke baris berikutnya.
Pembaca label hanya tahu bentuk `Author:` dalam satu baris. Perbaikan: baris yang
dimulai dengan `:` digabung kembali ke baris sebelumnya sebelum label dibaca.

**(4) Novel yang dihapus bisa muncul kembali sebagai novel lain.** `Library.byId()`
punya fallback "kalau id tidak ketemu, kembalikan novel pertama". Untuk novel yang
baru dihapus, method itu mengembalikan buku lain, sehingga unduhan dan progres novel
terhapus bisa terbaca sebagai milik buku yang salah. Ditemukan oleh tes skenario 10,
bukan oleh review kode. Perbaikan: `byId()` mengembalikan `null`; pemanggil yang
memang ingin "novel mana saja" memakai `first()`.

**Bukti tes menangkap bug — uji mutasi.** Empat bug dikembalikan satu per satu:

1. `textOf()` kembali memanggil `Element.text()` untuk semua elemen →
   `theGenericParserAlsoReadsTheTwoBuiltInPages` gagal.
2. `coverUrl()` kembali memakai gambar pertama → 
   `aCatalogueInADifferentLayoutIsReadCompletely` gagal (banner terpilih sebagai cover).
3. `byId()` kembali berfallback ke novel pertama →
   `deletingFromTheShelfKeepsDownloadsAndProgress` gagal.
4. Urutan pemeriksaan ekstensi dan garis miring ditukar (URL `...judul.html/`
   ditolak lagi) → `onePageHasOneIdentityWhateverTheSpelling` gagal.

Setelah dipulihkan, suite penuh **239 tes, 0 gagal**, dan penanda `MUTATION` di
`app/src/main` berjumlah nol.

## Perbaikan bug

### BUG 1 (2.1) — Volume 1–4 tidak muncul

Penyebab: Blogger tidak memenuhi `max-results`. Untuk label ini, permintaan 25 item
dijawab dengan halaman berukuran **25, 22, 25, 20, 12, 13**. Klien 2.0 berhenti
begitu sebuah halaman lebih pendek dari yang diminta
(`if (items.length() < PAGE_SIZE) break;`), sehingga crawl selesai di 47 postingan
dan perpustakaan seolah dimulai dari Volume 5.

Perbaikan di `FeedClient`:
- Kursor maju sebesar **jumlah item yang benar-benar diterima**, bukan ukuran yang diminta.
- Crawl berlanjut sampai Blogger mengembalikan halaman kosong, atau `openSearch$totalResults` tercapai.
- Duplikat URL dibuang; `FeedException` menyebut halaman dan `start-index` bila satu halaman gagal.
- `Result.warning` memberi tahu bila hasil belum lengkap.

### BUG 2 (2.2) — Scroll tetap berhenti walau padding sudah ditambah

**Ini akar masalah yang sebenarnya, ditemukan dengan mengukur hierarki view, bukan menebak.**

Perbaikan 2.1 hanya menambah padding bawah dan menangani window insets. Itu benar
tetapi **tidak menyentuh penyebabnya**. Di `ReaderActivity.buildUi()`, panel error
dibungkus `ScrollView` berukuran `MATCH_PARENT` dan **selalu ditambahkan sebagai
anak terakhir** `FrameLayout` area baca. Kode hanya pernah memanggil
`errorBox.setVisibility(GONE)` — yaitu `LinearLayout` **di dalam** `ScrollView` itu —
sehingga `ScrollView` pembungkusnya tetap `VISIBLE`, setinggi penuh, dan berada **di
atas** `RecyclerView`.

Bukti dari pengukuran (Robolectric, 411×891dp):

```
HOST children (topmost last)
  [0] ProgressBar   vis=GONE      size=0x0
  [1] RecyclerView  vis=VISIBLE   size=822x1477
  [2] ScrollView    vis=VISIBLE   size=822x1477   <-- menutupi seluruh area baca
HOST canScroll=false
```

Karena `ScrollView` kosong itu yang menerima gesture, daftar tidak pernah menerima
sentuhan gulir. Itulah sebabnya bug terasa "sontek tidak jalan" pada chapter panjang,
sementara pengukuran `findLastVisibleItemPosition` di tes 2.1 tetap lulus: tes lama
memanggil `scrollToPositionWithOffset` secara programatik, sehingga melewati jalur
sentuhan yang sebenarnya rusak.

Perbaikan di `ReaderActivity`:
- `errorScroll` disimpan sebagai field dan visibilitasnya **ikut dikelola**:
  `GONE` saat memuat/berhasil, `VISIBLE` hanya saat error.
- Panel error tidak lagi pernah berada di atas daftar saat chapter tampil;
  daftar menjadi anak teratas yang menerima sentuhan.
- Bookmark dibaca **sebelum** `adapter.setContent()`, dan `saveProgress()` tidak menyimpan
  selama pemulihan (`restoring`), agar layout pass tidak menimpa posisi baca dengan 0.
- `ScrollMath` tetap dipakai: 100% hanya dilaporkan saat blok terakhir benar-benar terlihat.

### Bukti tes benar-benar menangkap bug

`ReaderScrollRegressionTest` menguji **tumpukan view**, bukan hanya padding. Untuk
memastikan tes ini tidak kosong, bug lama dikembalikan sementara ke `ReaderActivity`:
`errorScroll` dibiarkan tanpa pengelolaan visibilitas sama sekali. Hasilnya
**3 tes langsung gagal**:

```
ReaderScrollRegressionTest > nothingOverlaysTheReadingList FAILED
ReaderScrollRegressionTest > errorScrollStartsHidden FAILED
ReaderScrollRegressionTest > readingAreaScrollsAndErrorPaneDoesNotBlockIt FAILED
8 tests completed, 3 failed
```

Setelah perbaikan dipulihkan, **8/8 lulus**. Jadi tes ini menangkap persis bug yang dilaporkan.

### BUG 3 (2.3) — Seluruh gambar tidak muncul

**Akar masalah ditemukan dengan mengukur view, bukan menebak.**

Diagnostik Robolectric mencatat sel gambar di dalam reader:

```
IMAGE VIEW measured=0x240   wrapper=80x240
```

`ImageView` di `BlockAdapter` dibuat dengan `wrap_content` dan **tanpa isi** saat
layout pertama. Tanpa drawable dan tanpa tinggi minimum yang benar, ia terukur
**lebar 0**; wrapper-nya hanya menyisakan padding samping. Bitmap yang berhasil
diunduh pun tidak punya tempat untuk digambar, sehingga teks tampil tetapi semua
ilustrasi hilang — termasuk isi postingan "Illustrasi".

Yang **bukan** penyebabnya, sudah diuji dan disingkirkan:
- **URL hasil parser**: 46 URL dari 7 volume diunduh langsung → **46/46 HTTP 200**,
  `Content-Type: image/*`, hasil decode JPEG/PNG valid.
- **Akses gambar**: tanpa User-Agent khusus tetap `200`; tidak ada redirect aneh,
  tidak ada blokir.
- **Dependency Glide**: `glide:4.16.0` ada di `debugRuntimeClasspath` dan kelasnya
  masuk ke APK.

Perbaikan:
- `ImageCell` baru: kotaknya **selalu** `MATCH_PARENT` lebar dan tinggi dihitung dari
  rasio gambar (`width / ratio`), diukur `EXACTLY`, jadi tidak pernah 0 selama kolom
  baca punya lebar.
- Ukuran asli dibaca parser dari `data-original-width`/`data-original-height`
  (fallback ke `width`/`height`), dibawa di `Block.imageWidth/imageHeight`.
  Tanpa ukuran, dipakai rasio cadangan 0,72 (halaman buku).
- Parser tidak lagi berhenti pada placeholder: `data-src` → `data-original` →
  `data-lazy-src` → `srcset` (varian terlebar) → `src`, dan nilai `data:`/`file:`
  **dilewati**, bukan dijadikan hasil akhir.
- Kegagalan tidak lagi senyap: pesan + tahap + tombol **Coba lagi**, tinggi kotak
  tetap dipesan agar tidak muncul ruang kosong besar.
- Ketuk gambar membuka `ImageViewerActivity` (zoom 1x–4x, geser, ketuk dua kali).

### Bukti tes menangkap BUG 3

Bug lama dikembalikan sementara: `ImageCell.onMeasure` tidak memesan kotak, dan parser
berhenti pada placeholder `data:`. Hasilnya **6 tes gagal**:

```
ChapterImageParserTest > lazyLoadedAttributesAreResolved FAILED
ReaderImageTest > imageCellsOccupyARealBox FAILED
ReaderImageTest > imageBoxKeepsSourceRatio FAILED
ReaderImageTest > imageWidthIsResponsive FAILED
ReaderImageTest > imageWithoutSizeStillGetsABox FAILED
ReaderImageTest > scrollingAndNavigationStillWork FAILED
22 tests completed, 6 failed
```

Setelah perbaikan dipulihkan, seluruh **79/79** tes lulus.

### BUG 4 (2.6) — Header bertabrakan dengan status bar setelah ganti tema

Gejala: pada halaman **Perpustakaan** judul "Perpustakaan" bertabrakan dengan jam, dan
pada halaman **Detail Novel** tombol Kembali, judul, ikon refresh, dan ikon tema ikut
naik ke area status bar. Yang penting: ini **hanya** terjadi setelah tema diganti
terang → gelap, bukan saat aplikasi pertama dibuka. Karena itu dugaan pertama
("padding kurang") salah — menambah 24 dp hanya akan menghilangkan bug di satu tema
lalu merusak tema lain.

**Akar masalah.** Ada dua cacat di pengelolaan `WindowInsets`, keduanya soal *kapan*
nilai inset dipakai, bukan berapa nilainya:

1. **Listener hanya menerapkan padding kalau nilainya berubah.**
   `MainActivity` menyimpan `insetTop`/`insetBottom` sebagai field, lalu:

   ```java
   if (bars.top != insetTop || bars.bottom != insetBottom) { ... applyInsets(); }
   ```

   Ganti tema di aplikasi ini **membangun ulang seluruh view tree**
   (`rebuildShell()` → `buildShell()` → `setContentView()`). Toolbar yang baru lahir
   dengan padding 0, tetapi field `insetTop` masih menyimpan nilai lama. Saat platform
   mengirim ulang inset yang sama, perbandingan itu menyimpulkan "tidak ada yang
   berubah" dan padding **tidak pernah** dipasang ke toolbar baru. Itulah sebabnya
   bug selalu muncul setelah pergantian tema, dan tinggi header terlihat "bergeser
   terlalu tinggi" — isinya naik, bukan bar-nya yang bertambah tinggi.

2. **Dispatch inset kosong bisa menimpa nilai yang sudah benar.** Sebagian dispatch
   datang sebagai hasil layout pass dan melaporkan `top = 0, bottom = 0`. Menyimpan
   angka nol itu menghapus tinggi status bar yang sudah diketahui, sehingga
   penerapan berikutnya justru mencabut padding yang tadinya benar.

Cacat yang sama ada di `ReaderActivity` dan `ImageViewerActivity` (pola
`if (bars.top != insetTop)` disalin ke keduanya), jadi Header di Reader Mode juga bisa
jatuh ke bawah jam setelah tema diganti dari panel pengaturan.

**Perbaikan.** Nilai inset dipusatkan di satu kelas, `ui/SystemBarInsets`, dan
penerapannya dibuat idempoten:

- `SystemBarInsets.accept(windowInsets)` menyimpan nilai sungguhan dan **menolak**
  dispatch kosong selama ada nilai non-nol yang diketahui. Aplikasi ini tidak pernah
  menyembunyikan system bar, jadi bacaan nol di tengah jalan adalah pengukuran
  sementara, bukan keadaan.
- `buildShell()` memanggil `applyInsets()` **segera setelah `setContentView()`**, jadi
  shell hasil rebuild langsung benar tanpa menunggu dispatch berikutnya.
- Listener memanggil `applyInsets()` **tanpa syarat** setiap dispatch. `setPadding`
  idempoten, sehingga menerapkan inset yang sama berkali-kali tidak pernah menumpuk —
  ini yang membuat pendekatan lama ("hanya kalau berubah") tidak diperlukan lagi.
- **Satu inset, satu level layout.** Top inset menjadi padding **container toolbar**
  saja (agar warna permukaan tetap mengecat di belakang status bar, sementara baris
  58 dp mulai di bawah jam). Bottom inset menjadi padding **di dalam konten yang
  bisa digulir** saja. Tidak ada komponen lain yang membaca inset yang sama.
- **Tidak ada padding-top hardcoded.** Tinggi status bar selalu berasal dari
  `WindowInsetsCompat.Type.systemBars() | displayCutout()`.
- Ganti tema di Reader Mode memanggil `adaptInsets()` lagi dari `repaint()`, jadi
  repaint tidak bisa meninggalkan toolbar di atas status bar.
- Ikon status bar diatur ulang di `applySystemBars()` setiap kali tema berubah:
  gelap pada tema terang/sepia, terang pada tema gelap.
- Animasi navigasi hanya menggeser `page_host`; toolbar dan root tidak pernah
  ditranslasi, sehingga animasi tidak bisa membawa header ke area status bar.

**File yang diubah:** `MainActivity.java`, `ReaderActivity.java`,
`ImageViewerActivity.java`, dan file baru `ui/SystemBarInsets.java`.

**Bukti tes menangkap BUG 4.** Bug lama dikembalikan sementara dalam tiga bentuk
sekaligus (listener "hanya kalau berubah" tanpa penerapan awal, `SystemBarInsets`
menerima dispatch kosong, dan padding-top hardcoded 24 dp) → **11 dari 13 tes gagal**:

```
InsetStabilityTest > aRebuiltShellReappliesKnownInsetsWithoutWaitingForADispatch FAILED
InsetStabilityTest > themeChangeKeepsTheToolbarBelowTheStatusBar FAILED
InsetStabilityTest > tenThemeChangesNeverLoseOrDoubleTheInset FAILED
InsetStabilityTest > storedDarkThemeOnRelaunchStillAppliesTheInset FAILED
InsetStabilityTest > detailPageThenBackThenThemeChangeKeepsTheInset FAILED
InsetStabilityTest > repeatedInsetDeliveriesDoNotDoubleThePadding FAILED
InsetStabilityTest > headerHeightStaysStableAcrossThemeChanges FAILED
InsetStabilityTest > navigationAnimationNeverMovesTheToolbar FAILED
InsetStabilityTest > themeChangeWithMotionDisabledKeepsTheSameGeometry FAILED
InsetStabilityTest > firstLaunchAppliesTheStatusBarInset FAILED
InsetStabilityTest > readerToolbarKeepsItsInsetAfterThemeChange FAILED
13 tests completed, 11 failed
```

Setelah perbaikan dipulihkan, seluruh **207/207** tes lulus pada versi 2.8 dan suite dijalankan
tiga kali berturut-turut tanpa kegagalan.

## Pengujian

`app/src/test/java/id/shuyasal/kuromaku/` berisi **239 pengujian** di 23 kelas,
semuanya lulus:

- **DynamicCatalogueTest** (8, parser generik + normalisasi URL) — sepuluh bentuk URL
  yang tidak layak ditolak dengan pesan yang bisa dibaca; satu halaman menghasilkan
  satu identitas untuk sembilan penulisan berbeda (`?m=1`, garis miring di akhir,
  parameter pelacak, `www`, `http`, huruf besar, spasi di tepi, tanpa skema);
  artikel biasa ditolak alih-alih disimpan sebagai novel kosong; halaman tanpa badan
  artikel ditolak; katalog berformat berbeda (`entry-content`, heading `h4`, ordered
  list, label tebal dengan titik dua terpisah) terbaca lengkap 6 chapter dalam 2
  volume, urutan `Prolog → Chapter 1 → Chapter 2 → Illustrasi` benar walau sumber
  menulis "Cahpter 2", cover memilih gambar potret di dalam tautan dan bukan banner
  lebar, tautan menu/footer bukan chapter; parser generik yang sama juga membaca
  kedua halaman novel bawaan (7 volume Monogatari, 13 volume Magical, Volume 14
  tanpa chapter palsu).
- **DynamicLibraryTest** (14, Room sungguhan) — tabel kosong di-seed dua novel
  bawaan dengan metadata terverifikasi dan mode katalog masing-masing; membuka
  perpustakaan berulang tidak menggandakan baris; novel valid tersimpan dengan id
  dari slug URL dan ditambahkan di urutan akhir; URL yang sama dua kali ditolak
  sebagai duplikat tanpa menimpa judul; pemeriksaan duplikat memakai URL
  ternormalisasi; perpustakaan tetap utuh setelah database ditutup dan dibuka ulang
  (tiga novel, judul, penulis, cover, dan dua paragraf sinopsis); Muat Ulang
  memperbarui judul/genre/status tetapi tidak menghapus ilustrator, cover, atau
  sinopsis yang tidak lagi dicantumkan halaman, serta tidak mengubah id, posisi,
  progres, dan posisi gulir; mutasi satu novel tidak menyentuh progres novel lain;
  hapus dari perpustakaan menyisakan unduhan dan progres; hapus beserta unduhan
  hanya menghapus berkas novel itu sementara novel lain utuh; hapus unduhan saja
  menyisakan novel dan progresnya; setelah satu novel dihapus, katalog dan progres
  dua novel bawaan tetap bekerja dan katalog novel terhapus dilupakan; setiap kolom
  yang disimpan kembali utuh lewat mapper; novel kosong ditolak.
- **DynamicDownloadTest** (7, Robolectric) — novel hasil tambah lewat URL, bukan dua
  novel bawaan: unduh satu chapter menyimpan `content.json` di folder ber-id slug
  dan dua berkas gambar nyata; unduh satu volume menyelesaikan semua chapternya
  dengan gambar identik tidak disimpan dua kali; mengunduh chapter yang sama dua
  kali tidak menambah satu pun permintaan; berkas novel baru tidak tercampur dengan
  novel bawaan (dan sebaliknya) walau isinya mirip; chapter tersimpan tetap terbuka
  saat sumber dibuat tidak dapat dihubungi — teks tampil, tanpa satu pun permintaan,
  dan tanpa pesan gagal; ilustrasi dibaca dari berkas lokal (`file:`), bukan URL, dan
  sel gambar punya kotak nyata; urutan gambar di antara paragraf dipertahankan;
  progres novel baru terpisah dari novel bawaan; berpindah ke chapter yang belum
  diunduh saat offline menampilkan pesan jelas alih-alih layar kosong.
- **ChapterImageParserTest** (11) — fixture HTML asli 7 volume disimpan di
  `app/src/test/resources/fixtures/`. Postingan Illustrasi menghasilkan
  5/5/5/8/7/7/7 gambar sesuai sumber, urutan gambar sama dengan HTML asli,
  teks & gambar tetap berselang-seling, ukuran asli (`1080x1534`) terbaca,
  thumbnail `s320`/`w400-h600` dinaikkan ke `s1600`, `data-src`/`srcset` dipakai,
  placeholder `data:`/`file:` tidak dianggap gambar, chapter tanpa ilustrasi → 0 gambar.
- **ReaderImageTest** (11, Robolectric) — sel gambar punya lebar & tinggi nyata,
  rasio potret 1080×1534 dan lanskap 1523×1080 dipertahankan, lebar responsif,
  gambar tanpa ukuran tetap dapat kotak, ketuk gambar membuka penampil,
  zoom dibatasi 1x–4x, kegagalan menampilkan pesan + tombol Coba lagi,
  retry mereset status, logging menyebut tahap tanpa membocorkan token,
  posisi gambar di antara paragraf, scroll + navigasi tetap jalan.
- **ReaderScrollRegressionTest** (8, Robolectric) — tidak ada view yang menutupi `RecyclerView`;
  panel error mulai `GONE`; area baca bisa tergulir; paragraf ke-300 tercapai; paragraf
  terakhir bersih dari panel navigasi; bookmark tidak tertimpa layout pass; persen hanya
  100% di akhir; tombol Berikutnya tetap berpindah chapter. Chapter uji berisi **300 paragraf
  + ilustrasi**.
- **LibraryPageTest** (14, Robolectric, layar kecil 320×640dp) — rasio sampul sesuai artwork
  (toleransi 0.02), placeholder berada di belakang gambar, judul & metadata tampil apa adanya,
  sinopsis mengembang lewat "Baca selengkapnya", volume tertutup menyembunyikan chapter,
  Volume 1 dan 2 keduanya terdaftar, tombol Lanjutkan Membaca memakai progres nyata,
  atribusi + tautan sumber tetap ada, tema gelap memakai struktur yang sama, **setiap volume
  menampilkan cover-nya sendiri**, ketuk cover membuka artwork volume itu, volume tanpa cover
  memakai placeholder, volume kosong menyatakan belum ada chapter.
- **LandingCatalogParserTest** (13) — halaman katalog Magical★Explorer asli: 14 volume
  terdeteksi (V1..V14 berurutan), jumlah tautan per volume `12,9,11,13,11,9,11,11,11,11,10,10,12,0`,
  semua chapter milik novel ini, "Cahpter 2" dinormalkan dan diurutkan di posisi kedua,
  Chapter 0 sebelum Chapter 1, Prolog sebelum Chapter 1, Illustrasi setelah chapter terakhir,
  teks tanpa tautan tidak menjadi chapter, tautan novel lain ditolak, `Volume&nbsp;2` dikenali.
- **VolumeCoverParserTest** (7) — cover per volume dari kedua halaman sumber asli, tidak ada
  cover yang dipakai dua kali, ukuran dinaikkan ke `s1600`, hanya gambar pertama setelah judul
  volume yang dianggap cover, volume tanpa gambar tidak diberi cover.
- **VolumeCoversTableTest** (4) — tabel cover statis di aplikasi dibandingkan ulang dengan hasil
  parse halaman sumber, jadi tabel tidak bisa melenceng dari sumber; tidak ada cover ganda;
  semua rasio berada di rentang buku (0,55–0,85).
- **TwoNovelTest** (10) — dua novel di perpustakaan, satu kartu per novel, ketuk kartu membuka
  novel yang benar, progres & accordion tidak bocor antar novel, tema/huruf tetap bersama,
  `Session` memisahkan katalog, dan reader tidak bisa menyeberang ke novel lain.
- **HeaderAndMotionTest** (18, Robolectric, layar kecil 320×640dp + sedang 411×891dp) —
  tinggi toolbar berada di 56–64 dp di luar system inset; status bar inset benar-benar
  menjadi padding di atas toolbar sehingga judul tidak menabrak jam; judul toolbar selalu
  satu baris dan memakai ellipsize; halaman detail berjudul **Detail Novel** sementara
  judul lengkap novel tetap di konten bersama cover/metadata/sinopsis/daftar volume;
  nama aplikasi tidak tercetak dua kali; tombol Kembali berikon + berlabel "Kembali",
  48 dp, lebar ≥ 92 dp; `back_to_library`, `refresh`, `theme` semuanya ≥ 48 dp; Muat ulang
  dan Gelap/Terang berupa `ImageButton` dengan `contentDescription`; kartu novel dan
  tombol Kembali memakai `RippleDrawable`; Back Android kembali dari detail ke
  perpustakaan tanpa menutup aplikasi; `Motion.enabled()` mengikuti
  `ANIMATOR_DURATION_SCALE` (0 = mati); navigasi tetap bekerja dan accordion tetap
  terbuka saat animasi dimatikan; durasi animasi berada di rentang 180–250 ms;
  halaman detail tetap bisa digulir sampai atribusi/tombol sumber; tema gelap tetap
  memakai warna teks yang benar; toolbar Reader Mode punya tombol Kembali berlabel dan
  ikon pengaturan 48 dp dengan judul satu baris.
- **InsetStabilityTest** (13, Robolectric, 411×891dp) — bug 2.6 dikunci di sini:
  - peluncuran pertama menerapkan padding status bar;
  - **ganti tema terang → gelap tidak menghilangkan padding** (bug yang dilaporkan);
  - **10 pergantian tema berturut-turut** tidak pernah kehilangan atau menggandakan
    padding, dan tinggi baris toolbar tetap sama persis di setiap putaran;
  - dispatch inset berulang 5 kali tidak menumpuk padding (idempoten);
  - shell yang dibangun ulang langsung memakai inset yang sudah diketahui, tanpa
    menunggu dispatch berikutnya (ini reproduksi setia bug di perangkat);
  - tema gelap yang tersimpan lalu aplikasi dibuka ulang tetap benar;
  - detail novel → Back → ganti tema lagi tetap benar;
  - ikon status bar gelap di tema terang dan terang di tema gelap;
  - animasi navigasi hanya menggeser `page_host`, tidak pernah toolbar/root;
  - konten tetap menyisakan ruang untuk navigation bar dan halaman tetap bisa digulir;
  - toolbar Reader Mode tetap di bawah status bar setelah ganti tema di panel, dan
    tombol Kembali tetap 48 dp;
  - dengan animasi dimatikan, geometri tetap identik.
- **ReaderLayoutTest** (7, Robolectric) — geometri panel navigasi, tinggi area baca, progres.
- **FeedPaginationTest** (11) — halaman pendek tidak menghentikan crawl, Volume 1–7 bertahan
  pada ukuran halaman tidak beraturan (25/22/25/20/12/13), dedupe, peringatan crawl tidak lengkap.
- **ChapterParserTest** (10) — teks & ilustrasi tidak hilang, urutan terjaga, iklan dibuang.
- **ScrollMathTest** (6) — progres, restore posisi, padding footer.
- **LibraryBuilderTest** (5) — pengelompokan volume, urutan numerik, label ringkas.

```bash
gradle testDebugUnitTest      # 148 tes
```

Mutation test untuk 2.5 (dibuktikan, bukan diklaim): mengembalikan bug lama membuat
tes benar-benar gagal, lalu kode dipulihkan dan seluruh suite hijau kembali.
Mutation test untuk 2.6 ada di bagian **BUG 4 (2.6)** — 11 dari 13 tes gagal saat bug
WindowInsets dikembalikan, lalu 148/148 hijau setelah dipulihkan.

| Mutasi yang dikembalikan | Tes yang gagal |
| --- | --- |
| Toolbar ditinggikan menjadi 92 dp | `toolbarStaysCompactOnASmallPhone` |
| Judul toolbar dibiarkan membungkus dua baris | `titleIsNeverWrappedOntoTwoLines` |
| Tombol Kembali dikembalikan menjadi glyph `‹` kecil | `backButtonHasAnArrowAndAVisibleLabel`, `toolbarControlsAreAtLeast48Dp` |
| Animasi mengabaikan "Remove animations" | `motionHonoursTheSystemAnimationScale` |
| Status bar inset dihapus dari toolbar | `statusBarInsetPushesTheToolbarBelowTheClock` |

Verifikasi terhadap feed asli (dijalankan manual saat pengembangan):
6 halaman, **117/117 postingan**, Volume 1–7 lengkap.

Verifikasi terhadap gambar asli (dijalankan manual saat pengembangan):
**46/46 URL hasil parser** dari 7 volume diunduh → HTTP 200, `image/*`, decode valid.

- **OfflineDownloadTest** (17, Robolectric) — inti penyimpanan offline:
  chapter tersimpan lalu dibaca ulang tanpa jaringan; ilustrasi benar-benar
  menjadi berkas lokal (bukan URL) dan ikut hilang saat dihapus; menekan unduh
  lagi untuk chapter yang sudah ada **tidak menambah satu request pun** (dihitung
  dari total request, bukan jumlah URL unik); gambar gagal validasi tidak
  disimpan dan chapter ditandai gagal; HTML yang terpotong/berisi penanda iklan
  tidak pernah disimpan; berkas `.tmp` sisa dibersihkan; menulis dua chapter
  dengan URL sama tidak menggandakan berkas; chapter dengan URL berbeda tetapi
  isi gambar sama hanya menyimpan satu berkas gambar; **novel A dan novel B tidak
  pernah saling menimpa** (folder dan hash terpisah); volume berjalan berurutan
  dan melaporkan `x dari y`; **resume setelah batal hanya mengunduh sisa chapter**;
  membatalkan di tengah tidak meninggalkan chapter setengah jadi; unduhan ulang
  penuh menghasilkan ukuran penyimpanan yang sama; hapus volume hanya menghapus
  volume itu; dan menghapus seluruh unduhan mengembalikan penyimpanan ke nol.
- **ReaderOfflineTest** (8, Robolectric) — Reader Mode offline: chapter terunduh
  **dibuka tanpa satu pun request jaringan**; jumlah blok dan teks identik dengan
  versi online; setiap gambar memakai `file:` lokal sehingga tampil di mode
  pesawat dan posisinya di antara paragraf tetap sama (indeks blok dibandingkan
  satu per satu); adaptor membaca `imageSource()` sehingga sel gambar memakai
  berkas lokal; Sebelumnya/Berikutnya tetap bekerja antar chapter terunduh;
  chapter yang belum diunduh saat offline menampilkan pesan jelas dan bukan layar
  kosong; membuka ulang chapter tersimpan tidak menembak jaringan; dan progres
  baca offline berperilaku sama dengan online (ekuivalensi, bukan angka tetap).
- **DownloadUiTest** (12, Robolectric, 320×640dp) — kontrol unduh di halaman
  detail: setiap baris chapter punya tombol unduh 48 dp dengan
  `contentDescription`; label status dimulai "Belum diunduh", berubah
  "Tersimpan" setelah tersimpan, dan tombol unduh diganti tombol hapus; chapter
  yang gagal menampilkan **Coba lagi** yang benar-benar memicu unduh ulang;
  tombol hapus melaporkan chapter yang benar; baris volume punya **Download
  Semua** 48 dp yang memicu unduhan volume; progres sebagian tercetak
  "1 dari 2 tersimpan"; halaman detail tanpa lapisan unduh tetap tampil seperti
  sebelumnya; halaman **Unduhan** menampilkan novel, volume, jumlah chapter,
  ukuran penyimpanan, tombol hapus chapter & volume 48 dp, keadaan kosong yang
  menjelaskan cara mengunduh, dan kembali ke keadaan kosong setelah semua dihapus.

- **DownloadsNavigationTest** (11, Robolectric) — alur nyata dari `MainActivity`:
  menekan ikon download merender halaman Unduhan (bukan layar kosong), halaman
  benar-benar terlihat (alpha 1, tanpa translasi, punya ukuran), keadaan kosong
  menjelaskan pilihan dan menyediakan **Lihat Chapter** yang kembali ke daftar
  chapter, Back kembali ke **Detail Novel** (bukan Perpustakaan), posisi gulir
  detail dipulihkan setelah bolak-balik, Back dari Perpustakaan tetap menutup
  aplikasi, chapter tersimpan tercantum dengan label aslinya, membuka halaman
  Unduhan tidak menembak jaringan sama sekali, cover/metadata/sinopsis detail
  tetap utuh, dan **pembangunan halaman yang gagal menampilkan pesan + Coba lagi
  alih-alih layar kosong**.
- **DownloadsProgressTest** (11, Robolectric) — status yang ditampilkan adalah
  status sebenarnya: kartu unduhan berjalan menyebut novel + volume, "2 dari 5
  chapter selesai", progress bar dengan `max`/`progress` yang benar, status
  chapter yang sedang diunduh, dan tombol **Batalkan** 48 dp yang memicu
  pembatalan; bagian "Sedang diunduh" muncul walau belum ada yang tersimpan;
  pekerjaan gagal menampilkan alasannya dan **Coba Lagi** yang benar-benar
  mengulang; bagian berjalan dan gagal bisa tampil bersamaan; keadaan kosong
  menyediakan ikon + **Lihat Chapter** 48 dp; perubahan status dari thread worker
  **selalu dikirim di main thread**, dan listener yang melempar exception tidak
  membungkam listener lain; index unduhan yang rusak tidak membuat layar kosong.

### Bukti tes offline menangkap bug

Tiga bug dikembalikan sementara untuk memastikan tes baru tidak hijau palsu:

1. Reader berhenti memilih salinan lokal (`localChapter()` selalu `null`);
2. gambar jatuh kembali ke URL walau berkas lokal ada (`imageSource()` → `imageUrl`);
3. penjaga "sudah tersimpan" di `ChapterDownloader` dimatikan.

Hasil: **7 tes gagal**, termasuk `downloadedChapterOpensWithoutTouchingTheNetwork`,
`illustrationsAreLocalFilesWhenReadOffline`, `adapterBindsLocalImageSourceForStoredBlocks`,
`everyIllustrationResolvesToALocalFile`, dan `readingProgressBehavesTheSameOfflineAsOnline`.
Mutasi ketiga awalnya **lolos** karena tes menghitung jumlah URL unik, bukan jumlah
request — tes diperbaiki menjadi menghitung total request, lalu mutasi yang sama
menghasilkan `expected:<3> but was:<6>`. Setelah dipulihkan, **185/185** lulus.

## Arsitektur

```
app/src/main/java/id/shuyasal/kuromaku/
├── MainActivity.java          # shell perpustakaan: bar, insets, muat katalog
├── ReaderActivity.java        # Reader Mode + panel pengaturan + insets
├── ImageViewerActivity.java   # penampil ilustrasi layar penuh (zoom 1x–4x)
├── Session.java               # katalog in-memory untuk navigasi chapter
├── data/
│   ├── FeedEntry, Chapter, Volume, Catalog
│   ├── Novel.java             # satu novel: metadata, cover, cara katalog dibaca
│   ├── Novels.java            # daftar dua novel (teks dari halaman sumber)
│   ├── VolumeCovers.java      # tabel cover per volume (diverifikasi tes)
│   ├── NovelInfo.java         # metadata Monogatari (dipakai tes lama)
│   └── LibraryBuilder.java    # parsing judul → volume, label, urutan baca
├── net/
│   ├── Http.java              # OkHttp, hanya dokumen teks
│   ├── CatalogLoader.java     # pilih feed vs halaman katalog, pasang cover
│   ├── FeedClient.java        # feed JSON label, paginasi penuh
│   └── FeedException.java     # kegagalan per halaman
├── parser/
│   ├── ChapterParser.java     # Jsoup: scrub situs → blok teks/gambar berurutan
│   ├── LandingCatalogParser.java # katalog dari tautan di halaman landing
│   ├── VolumeCoverParser.java # cover per volume dari halaman landing
│   ├── ChapterContent.java    # judul + daftar blok + URL sumber
│   └── ContentCache.java      # cache LRU 6 chapter
└── ui/
    ├── NovelListPage.java     # halaman perpustakaan: kartu per novel
    ├── LibraryPage.java       # halaman detail novel + accordion volume + cover volume
    ├── RatioFrameLayout.java  # kotak rasio tetap untuk sampul
    ├── ImageCell.java         # sel ilustrasi: kotak berasio, error + coba lagi
    ├── ZoomableImageView.java # cubit/geser/ketuk dua kali
    ├── ImageLoadLog.java      # logging tahap gambar, URL disamarkan
    ├── BlockAdapter.java      # render blok jadi view native
    ├── ScrollMath.java        # progres, restore posisi, padding footer
    └── Palette.java, Prefs.java, Ui.java
```

Kontainer artikel yang dikenali: `div.post-body.entry-content`, lalu `div.post-body`,
`div.entry-content`, dan `article` sebagai cadangan.

## Build

GitHub Actions: **Actions → Build Kuromaku Reader APK** (`.github/workflows/android.yml`).
Alur: unit test → `assembleDebug` → verifikasi APK dengan `aapt2` → artifact → rilis `latest-apk`.

Lokal (JDK 17, Android SDK 35, Gradle 8.9):

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
gradle testDebugUnitTest assembleDebug
```

## Atribusi dan hak cipta

- Seluruh teks dan ilustrasi milik penulis, penerbit, dan penerjemah aslinya.
- Aplikasi ini hanya menampilkan konten publik dari situs sumber saat dibuka; **tidak**
  menyertakan salinan novel di dalam APK dan tidak menyimpan arsip offline.
- Setiap chapter menampilkan tautan ke halaman aslinya.

## Batasan

- Butuh koneksi internet.
- Jika Kaori Translation mengubah struktur HTML-nya, selector di `ChapterParser` perlu disesuaikan.
- Ekstraksi mengandalkan kontainer artikel; halaman yang benar-benar berbeda strukturnya akan
  menampilkan pesan error (bukan website penuh), sesuai desain.
- Status terbit tidak tersedia di halaman sumber, jadi aplikasi menyatakan demikian.
- Verifikasi scroll dilakukan lewat pengukuran layout Robolectric; perilaku pada perangkat
  dengan notch/gesture bar berbeda tetap perlu konfirmasi saat dipakai.
- Verifikasi gambar dilakukan lewat pengukuran layout Robolectric dan unduhan URL asli;
  Glide tidak benar-benar mengunduh di unit test, jadi tampilan akhir, kecepatan cache,
  dan zoom multi-touch tetap perlu dicoba di perangkat Android.
- Parser katalog Magical★Explorer bergantung pada bentuk halaman katalognya (judul
  `Volume N` diikuti tautan chapter). Bila Kaori Translation mengubah tata letaknya,
  `LandingCatalogParser` perlu disesuaikan.
- Cover volume berasal dari URL yang tercantum di halaman sumber. Bila sumber mengganti
  gambarnya, `VolumeCovers` dan fixture di `app/src/test/resources/catalog/` harus
  diperbarui bersama; `VolumeCoversTableTest` akan gagal bila hanya salah satunya diubah.
- Katalog dimuat saat aplikasi dibuka. Bila salah satu novel gagal dimuat, novel lain
  tetap tampil dan pesan kegagalan disebutkan di halaman perpustakaan.

### Batasan parser generik (2.9) — diukur, bukan diperkirakan

Parser generik **tidak** menjamin semua URL novel bisa ditambahkan. Yang benar-benar
diuji hanya tiga bentuk halaman: dua halaman katalog Kaori Translation yang asli, dan
satu fixture dengan tata letak berbeda (kontainer `entry-content`, heading `h4`,
ordered list, label tebal). Selebihnya belum diuji sama sekali.

Batasan yang pasti:

- **Host.** Hanya `kaoritranslation.blogspot.com` yang diterima. Novel dari situs lain
  ditolak dengan pesan, bukan dicoba-coba.
- **Pola tautan.** Chapter harus berada di path post bertanggal `/20xx/xx/`. Katalog
  yang menautkan halaman statis `/p/` tidak akan terbaca sebagai chapter.
- **Chapter tanpa tautan.** Teks "Chapter 3" tanpa `<a>` tidak dibuatkan chapter; hanya
  dihitung sebagai peringatan pada preview.
- **Daftar yang dimuat JavaScript.** Halaman seperti ini menghasilkan pesan
  "tidak ada tautan chapter yang dikenali" dan novel **tidak** disimpan.
- **Volume.** Bila halaman tidak punya heading `Volume N` dan slug-nya tidak memuat
  `volume-N`, semua chapter masuk satu kelompok "Volume lain". Volume yang hanya punya
  cover tanpa tautan (seperti Volume 14 Magical★Explorer) tidak dibuatkan chapter palsu.
- **Cover.** Bila tidak ada gambar potret sama sekali, cover jatuh ke gambar pertama dan
  preview menandainya lewat peringatan "Cover tidak ditemukan" atau lewat tampilan
  placeholder di kartu.
- **Metadata.** Hanya baris berlabel yang dibaca. Halaman yang menulis metadata sebagai
  kalimat bebas akan menghasilkan kolom kosong — dan itu ditampilkan apa adanya, bukan
  ditebak.
- **Belum diuji di perangkat.** Halaman Tambah Novel, dialog hapus dua opsi, dan menu
  tiga titik hanya diverifikasi lewat Robolectric (pengukuran layout), bukan di layar
  Android sungguhan. Tampilan akhir, kecepatan Glide memuat cover dari URL baru, dan
  perilaku pada perangkat dengan notch/gesture bar masih perlu dicoba.
