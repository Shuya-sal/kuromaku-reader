# Kuromaku Reader 2.20

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

Versi 2.18 memperbaiki **cover volume dan indikator unduhan**. Novel yang
ditambahkan lewat URL kini menampilkan cover asli setiap volume — dibaca dari
struktur halaman katalognya sendiri, bukan dari tabel bawaan — dan cover itu
disimpan ke database sehingga tidak perlu parsing ulang setiap membuka detail.
Indikator unduhan memakai **progres byte nyata** dari `Content-Length`: cincin
progres di setiap chapter dan volume, persentase yang hanya muncul bila ukurannya
benar-benar diketahui, jumlah "x dari y chapter", tombol Batal, serta status
**parsial** ketika teks tersimpan tetapi ilustrasi gagal. Teks "67%Mengunduh…"
yang berdempetan hilang karena persentase, status, dan tombol kini punya jarak
masing-masing. Detail di bagian **Fitur Cover Volume & Indikator Unduhan (2.18)**.

Versi 2.17 memindahkan sumber katalog **Monogatari no Kuromaku** dari feed label ke
artikel daftar isi milik sumber sendiri, dan menjalankan pengujian ke tiga novel.
Feed label yang rusak ("Respons feed halaman ke-1 tidak dapat dibaca") tidak lagi
bisa mengosongkan daftar chapter, karena artikel tetap menjadi sumber utama dan feed
hanya dipakai untuk menambal. Detail di bagian **BUG 12 (2.17)**.

Versi 2.16 **tidak mengubah kode produksi**: yang ditambahkan hanya pengujian untuk
pemilih tautan manual, satu-satunya bagian halaman Tambah Novel yang belum pernah
diuji lewat UI. Tidak ada bug baru yang diklaim — tiga aturan yang sudah dijanjikan
sejak 2.10 sekarang dijaga tes: pemilih hanya menawarkan tautan yang mungkin chapter,
centang/batal centang menentukan isi pilihan, dan tidak ada yang dikirim sebelum
"Pakai pilihan ini" ditekan. Detail di bagian **Pengujian 2.16**.

Versi 2.15 menyebut **dua bentuk alamat** yang diterima halaman Tambah Novel.
Petunjuk di bawah judul hanya menyebut artikel katalog, padahal URL label
`/search/label/...` sudah didukung sejak 2.10 — dan justru bentuk itu yang memberi
daftar chapter lengkap dalam satu langkah. Detail di bagian **BUG 11 (2.15)**.

Versi 2.14 memperbaiki **kata di panel diagnostik**: baris pertama menulis
"Tautan ditemukan di artikel" untuk alamat label juga, padahal tautannya datang
dari feed JSON — halaman artikelnya tidak pernah dibaca. Detail di bagian
**BUG 10 (2.14)**.

Versi 2.13 memperbaiki **angka di panel diagnostik**: panel sempat menulis
"43 chapter diambil dari feed" di atas daftar berisi 42, karena yang dihitung
adalah jumlah postingan feed sebelum judul kembar digabung. Semua angka sekarang
sama dengan panjang daftar yang terlihat di layar. Detail di bagian
**BUG 9 (2.13)**.

Versi 2.12 memperbaiki jalur **Muat Ulang** untuk novel yang disimpan lewat URL
label: jalur itu masih memakai aturan lama, sehingga postingan deskripsi novel
ikut menjadi chapter di "Volume 0" setelah novel dimuat ulang — daftar yang sama
dengan hasil tambah, tapi dua jawaban berbeda. Detail di bagian **BUG 8 (2.12)**.

Versi 2.11 memperbaiki **parser Tambah Novel** pada dua kegagalan yang dilaporkan:
URL Blogger `/search/label/...` ditolak sebelum diunduh, dan artikel katalog
`/2026/03/yariko...` ditolak dengan "Tidak ada tautan chapter yang dikenali"
padahal artikel itu berisi 43 chapter. Keduanya bukan soal parser "terlalu ketat":
yang pertama fitur yang belum ada, yang kedua artikelnya dihapus oleh pembersih
boilerplate sebelum dibaca. Detail akar masalah di bagian **BUG 7 (2.11)**.

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
- Penanda progres per chapter (`42% dibaca`, `Selesai dibaca`) dan tombol "Lanjutkan Membaca".
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

## BUG 7 (2.11) — Dua URL dilaporkan gagal, dan keduanya bukan soal "terlalu ketat"

Dua alamat dari laporan dicoba apa adanya:

- `https://kaoritranslation.blogspot.com/search/label/Yarikonde%20ita%20Game%20Sekai%20no%20Akuyaku%20Mob%20ni%20Tensei%20Shimashita?m=1`
- `https://kaoritranslation.blogspot.com/2026/03/yarikon-de-ita-game-sekai-no-akuyaku.html?m=1`

### (1) URL label ditolak sebelum satu byte pun diunduh

`NovelUrl` hanya mengenal dua bentuk alamat: artikel bertanggal `/20xx/xx/nama.html`
dan beranda. Tidak ada jalur ketiga, jadi `/search/label/...` gagal di
`normalize()` dengan pesan "Itu bukan halaman artikel" — sebelum jaringan disentuh.
Bukan parser yang menolak: **fiturnya belum ada**.

Perbaikan: label dikenali sebagai jenis ketiga (`NovelUrl.Kind.LABEL`), namanya
di-decode dari path (`%20` → spasi), dan chapter diambil dari **feed JSON per-label**
`/feeds/posts/summary/-/<label>?alt=json&max-results=25&start-index=N`, bukan dari
HTML listing. HTML label hanya memuat 20 postingan per halaman; feed-nya bisa
dipaginasi sampai habis. Untuk label Yariko, feed berisi **44 postingan dalam 2
halaman** (25 + 19), lalu halaman ketiga kosong dan pembacaan berhenti.

Halaman label tetap dipakai sekali, hanya untuk deskripsi: cover, penulis,
ilustrator, genre, sinopsis. Judul novel diambil dari **nama label**, bukan dari
heading halaman — heading itu judul chapter terbaru (`… Volume 3 Afterword`), dan
memakainya akan menamai seluruh novel dengan satu chapter.

### (2) Artikel Yariko kehilangan artikelnya sendiri sebelum dibaca

Ini akar yang sebenarnya, dan bukan soal pola chapter. HTML aslinya berisi **47
tautan dengan 43 chapter** dalam 3 volume. Yang terjadi:

```
div.widget.Blog#Blog1
  └── div.blog-posts
        └── article
              └── div.post-body.entry-content   ← artikelnya di sini
```

Parser lama membuang boilerplate **lebih dulu** dengan `select("div.widget").remove()`,
baru kemudian mencari artikel. Karena `div.post-body` adalah **keturunan** dari
`div.widget.Blog`, pembersihan itu menghapus artikel beserta seluruh daftar
chapternya, menyisakan sekitar 40 karakter chrome halaman. Parser lalu melaporkan
"Tidak ada tautan chapter yang dikenali" — pesan yang benar untuk kondisi yang salah.

Perbaikan: artikel dicari **lebih dulu** (`articleBody()`), baru dibersihkan, dan
pembersihan hanya menyentuh elemen di dalam artikel itu (`nav`, `footer`, `.share`,
`script`, `iframe`, iklan) — tidak lagi se-dokumen.

### (3) Bug ketiga, ditemukan saat menguji: tautan yang sudah ditolak bisa dicuci

Aturan "tautan di dalam grup volume dianggap chapter" ternyata menimpa alasan
penolakan apa pun. Tautan "Baca juga review sebelumnya" ditolak dengan alasan benar,
lalu alasannya ditulis ulang menjadi `"dalam grup volume"` — sehingga lolos ke daftar
pilihan manual. Kalau dicentang, iklan atau review novel lain bisa tersimpan sebagai
chapter.

Perbaikan: hanya penolakan **lunak** (`teks tidak berpola chapter`) yang boleh naik
menjadi kandidat. Navigasi, halaman statis, halaman-itu-sendiri, dan postingan novel
lain tetap tertolak dan **tidak muncul** di pemilih. `Candidate.selectable` membawa
keputusan itu lewat proses pengelompokan, tidak dihitung ulang dari teks alasan.

### (4) Bug keempat: `Interlude I`, `II`, `III` runtuh menjadi satu chapter

Label chapter dipakai sebagai kunci anti-duplikat. `LibraryBuilder` memotong
"Interlude III" menjadi `"Interlude"`, jadi ketiganya dianggap chapter yang sama dan
dua di antaranya dibuang. Pada feed Yariko ini menghapus satu chapter per volume
tanpa pesan apa pun. Perbaikan: angka Romawi/arab di belakang "Interlude" ikut
menjadi bagian label.

### (5) Bug kelima: postingan deskripsi novel dihitung sebagai chapter

Feed label mengembalikan **semua** postingan berlabel itu — termasuk postingan
landing yang judulnya adalah nama novel dan isinya cover, penulis, serta sinopsis.
Postingan itu tidak punya volume dan tidak punya kata "chapter", jadi masuk
kelompok "Volume 0" sebagai bahan bacaan. Perbaikan: `LibraryBuilder.isChapterPost()`
memisahkan postingan chapter dari postingan deskripsi; yang deskripsi tidak dihitung
dan jumlahnya dilaporkan di preview.

### Diagnostik dan pemilihan manual

Panel **Diagnostik tautan** pada preview sekarang menyebut: jumlah tautan yang
ditemukan, jumlah yang dikenali sebagai chapter, dan alasan setiap tautan yang
ditolak (maksimal enam baris, sisanya diringkas). Bila belum ada satu pun yang bisa
dipastikan, preview tidak langsung gagal: daftar tautan yang **bisa dipakai**
ditampilkan dengan kotak centang, dan novel baru disimpan setelah Tuan menekan
**Pakai pilihan ini**. Tautan di luar halaman tidak bisa diselundupkan ke pilihan.

### Hasil parse sesudah perbaikan (data nyata, bukan perkiraan)

- **Artikel Yariko** — 47 tautan, **43 chapter**, 3 volume (16 + 13 + 14), penulis
  Natsunomi, ilustrator Shimanun, genre Comedy/Fantasy/Romance, cover potret terbaca.
- **Label Yariko** — feed 44 postingan → **42 chapter** dalam 3 volume (15 + 13 + 14),
  1 postingan deskripsi tidak dihitung, 1 judul kembar digabung (dilaporkan di
  preview). Cover dan metadata sengaja kosong: halaman label tidak memuatnya.
- **Magical★Explorer** — tetap **141 chapter, 14 volume**, tidak berubah.

### Bukti tes menangkap bug — uji mutasi 2.11

Sepuluh mutasi, dikembalikan satu per satu. Setiap kali ada tes bernama yang gagal.

Kegagalan yang dilaporkan:

1. Pembersihan `div.widget` dikembalikan ke urutan lama (sebelum artikel dicari) →
   `everyRejectedLinkCarriesItsReason` gagal.
2. `/search/label/` ditolak lagi sebagai "bukan halaman artikel" →
   `aLabelCatalogueComesFromThePagedFeed` gagal.
3. Setiap tautan yang ditolak dibuat bisa dipilih (tiga variasi: alasan lunak
   dikembalikan `true`, hanya "dalam grup volume", dan field `selectable` dipaksa
   `true`) → `aLinkThatWasNotOnThePageCannotBeSelected` gagal pada ketiganya.
4. Semua postingan feed dihitung sebagai chapter →
   `aFeedPostThatDescribesTheNovelIsNotAChapter` gagal.

Cacat yang ditemukan saat menguji dengan HTML asli:

5. Angka "Interlude" dibuang lagi → `aFeedPostThatDescribesTheNovelIsNotAChapter`
   gagal (jumlah chapter berubah).
6. Alasan `"ditolak: tautan gambar"` dihapus → `everyRejectionSaysWhatTheLinkActuallyIs`
   gagal.
7. Alasan `"ditolak: potongan judul"` dihapus → `aStrayNumeralIsNamedAsAFragment`
   gagal. **Mutasi ini sempat lolos** pada percobaan pertama, karena di halaman
   Yariko labelnya sudah digabung lebih dulu sehingga cabang itu tidak pernah
   tercapai; tes dengan angka liar yang berdiri sendiri ditambahkan supaya cabangnya
   benar-benar dieksekusi.
8. Penggabungan potongan judul dimatikan → `aNumeralTornIntoItsOwnAnchorIsPutBack`
   gagal.
9. Alasan spesifik ditimpa lagi oleh alasan generik grup volume →
   `everyRejectionSaysWhatTheLinkActuallyIs` gagal.
10. Label interlude kembali memakai angka apa adanya (tanpa normalisasi Romawi) →
    `anInterludeIsLabelledTheSameFromDigitsOrRoman` gagal.

Setelah semua dipulihkan: suite penuh **265 tes, 0 gagal**, penanda `MUTATION` di
`app/src/main` berjumlah nol.

### Batasan yang masih berlaku untuk 2.11

- Semua pengujian memakai HTML dan feed yang **disimpan sebagai fixture**, ditambah
  satu kali pembacaan jaringan nyata untuk memastikan angkanya. Novel lain dengan
  tata letak berbeda belum tentu terbaca.
- Label hanya bisa dipakai kalau nama labelnya unik. Blogger mengizinkan dua novel
  berbagi label, dan feed tidak memberi cara memisahkannya.
- Cover dan metadata **tidak** diambil dari halaman label (isinya ilustrasi chapter).
  Untuk cover, tambahkan novel lewat URL artikelnya.
- Belum diuji di perangkat: panel diagnostik, pemilih tautan, dan kotak centang hanya
  diverifikasi lewat Robolectric.

## BUG 8 (2.12) — Daftar chapter berubah sendiri setelah "Muat Ulang"

Ini bukan keluhan yang dilaporkan Master Lupi; Shuvi menemukannya saat memeriksa
jalur kode label sesudah 2.11 terbit. Tetap dicatat karena akibatnya membingungkan:
novel yang **baru ditambahkan** terlihat benar, lalu setelah **Muat Ulang** daftarnya
bertambah satu chapter palsu.

### Akar masalah: dua jalur, dua aturan

Ada dua fungsi berbeda yang membaca feed label yang sama:

| Jalur | Fungsi | Menyaring postingan deskripsi? |
| --- | --- | --- |
| Tambah Novel (preview) | `CatalogLoader.inspectLabel` | Ya (ditambahkan di 2.11) |
| Muat Ulang (novel tersimpan) | `CatalogLoader.fromLabel` | **Tidak** |

`fromLabel` dibaca lebih dulu, saat menambahkan dukungan label. Penyaringan
postingan deskripsi ditambahkan belakangan, dan hanya di jalur preview. Jadi:

- Tambah Novel → 42 chapter, 3 volume.
- Muat Ulang → 44 chapter, dan postingan deskripsi muncul sebagai chapter terakhir
  di **"Volume 0"** — volume yang tidak pernah ada di sumber.

Selain itu `fromLabel` memanggil `FeedClient.fetchLabel(label)` yang memakai
`Http.get` langsung, bukan seam `Fetcher`. Akibatnya jalur itu **tidak bisa diuji
tanpa jaringan** — itulah sebabnya cacat ini tidak tertangkap sebelumnya: tidak ada
tes yang bisa menjalankannya secara deterministik.

### Perbaikan

1. Penyaringan `LibraryBuilder.isChapterPost` dipindah ke **kedua** jalur. Kalau feed
   hanya berisi postingan deskripsi, Muat Ulang sekarang **melempar pesan kesalahan**
   alih-alih menyimpan novel tanpa chapter.
2. `fromLabel` memakai seam `Fetcher.get()` seperti jalur preview, sehingga bisa
   diuji tanpa jaringan.
3. Ringkasan status di layar perpustakaan sekarang **menyebut alasannya**: "1
   postingan deskripsi novel tidak dihitung sebagai chapter." Sebelumnya `null`,
   sehingga selisih jumlah terlihat seperti chapter yang hilang tanpa sebab.

### Bukti tes menangkap bug

Dua tes baru, keduanya memakai feed buatan sendiri:

- `reloadingALabelNovelKeepsTheDescriptionOutOfTheChapters` — memuat novel label
  tersimpan, memastikan 3 chapter (bukan 4), Volume 0 tidak ada, dan ringkasan
  menyebut postingan deskripsi.
- `aLabelWithOnlyADescriptionPostIsRefusedOnReload` — label tanpa chapter ditolak
  dengan pesan, bukan disimpan kosong.

Uji mutasi:

1. Semua entri feed dimasukkan kembali ke daftar chapter → **dua** tes gagal.
2. Penjelasan "postingan deskripsi tidak dihitung" dihapus dari ringkasan →
   `reloadingALabelNovelKeepsTheDescriptionOutOfTheChapters` gagal.

Setelah dipulihkan: **267 tes, 0 gagal**, penanda `MUTATION` nol.

## BUG 9 (2.13) — Panel diagnostik menyebut angka yang tidak ada di daftar

Ditemukan Shuvi sendiri saat memeriksa keluaran panel dengan kedua URL asli, bukan
dari laporan Master Lupi.

### Gejala

Untuk URL label Yariko, panel menulis:

```
43 chapter diambil dari feed label "..."
```

di atas daftar **42** chapter. Selisih satu, tanpa penjelasan. Pengguna yang
menghitung chapternya akan menyimpulkan satu chapter hilang.

### Akar masalah

Kalimat itu disusun dari `chapterEntries.size()` — jumlah **postingan** feed yang
lolos penyaringan deskripsi — bukan dari jumlah chapter yang benar-benar masuk
daftar. Feed Yariko memuat satu judul yang terbit dua kali
(`Volume 3 Chapter 12` ada sebagai dua postingan berbeda), dan `LibraryBuilder`
menggabungkannya menjadi satu baris. Jadi:

| Angka | Nilai |
| --- | --- |
| Postingan di feed | 44 |
| Setelah penyaringan deskripsi | 43 |
| Setelah judul kembar digabung | **42** ← yang tampil di daftar |

Peringatan penggabungan sudah ada di daftar `warnings`, tapi angka utamanya tetap
yang salah — dan angka utama itulah yang dibaca orang lebih dulu.

### Perbaikan

1. Angka di kalimat utama dihitung dari `catalog.volumes` (jumlah chapter yang
   benar-benar ada di daftar), bukan dari jumlah entri feed. Berlaku untuk **kedua**
   jalur — preview dan Muat Ulang — karena keduanya punya kalimat sendiri.
2. Kalimat penggabungan menyebut kedua angka sekaligus: berapa postingan chapter di
   feed, berapa yang digabung, dan berapa chapter unik yang dihasilkan. Jadi selisih
   44 → 42 bisa dilacak tanpa menebak.

### Bukti tes menangkap bug

`theReportedChapterCountEqualsTheListOnScreen` — feed berisi 5 postingan: satu
deskripsi, satu judul yang terbit dua kali, dua chapter lain. Hasilnya harus
**3 chapter**, dan peringatan harus menyebut "3 chapter diambil dari feed label"
serta "4 postingan chapter di feed … 1 di antaranya … 3 chapter unik".

Uji mutasi:

1. Angka dikembalikan ke jumlah entri feed (sebelum penggabungan) →
   `theReportedChapterCountEqualsTheListOnScreen` gagal.
2. Kalimat yang menjelaskan penggabungan dihapus →
   `theReportedChapterCountEqualsTheListOnScreen` gagal.

Setelah dipulihkan: **268 tes, 0 gagal**, penanda `MUTATION` nol.

### Yang diverifikasi dengan jaringan nyata

Dijalankan ke `kaoritranslation.blogspot.com` setelah perbaikan:

- Artikel Yariko → **43 chapter / 3 volume** (16 + 13 + 14).
- Label Yariko → **42 chapter / 3 volume** (15 + 13 + 14), panel melaporkan 44
  ditemukan dan 42 dipakai, dengan alasan untuk dua yang tidak dipakai.
- Muat Ulang novel label yang sama → **42 chapter / 3 volume**, sama persis dengan
  preview.

## BUG 10 (2.14) — Panel menyebut "di artikel" untuk alamat yang dibaca dari feed

Ditemukan Shuvi saat memeriksa sendiri keluaran panel untuk kedua URL, sesudah 2.13.

### Gejala

Untuk URL label, panel menulis:

```
Tautan ditemukan di artikel: 44
```

Tidak ada artikel yang dibaca pada alamat label. Chapternya datang dari **feed JSON**
per-label (dua halaman), dan halaman labelnya hanya dibaca untuk mencoba mencari
deskripsi. Kalimat itu membuat pengguna membayangkan aplikasi memindai halaman
artikel — dan kalau nanti angkanya berbeda dari yang dia lihat di halaman itu, dia
akan mengira aplikasi salah hitung.

### Akar masalah

`AddNovelPage.diagnostics()` menulis satu kalimat tetap untuk semua jenis alamat.
Kalimat itu dibuat sebelum dukungan URL label ada, saat semua preview memang berasal
dari satu artikel.

### Perbaikan

Kata "di artikel" / "di feed label" dipilih dari jenis alamat (`NovelUrl.isLabel`
pada `preview.sourceUrl`). Angkanya tidak berubah — hanya keterangannya yang jujur.

### Bukti tes menangkap bug

`AddNovelDiagnosticsTest` (3 tes, Robolectric) membangun panel dengan dua alamat:

- alamat label → baris harus memuat "di feed label"
- alamat artikel → baris harus memuat "di artikel"
- kedua bentuk alamat yang diterima disebut namanya di petunjuk

Uji mutasi: kalimat dikembalikan menjadi satu versi tetap untuk semua alamat →
`aLabelAddressSaysTheLinksCameFromTheFeed` gagal. Setelah dipulihkan:
**270 tes, 0 gagal**, penanda `MUTATION` nol. (Angka ini benar untuk 2.14; suite
terus bertambah di versi berikutnya.)

## BUG 11 (2.15) — Bentuk alamat yang didukung tidak diberitahukan

### Gejala

Halaman Tambah Novel menulis di bawah judul:

> Buka halaman katalog novel di kaoritranslation.blogspot.com, salin URL-nya, lalu
> tempel di bawah. URL dengan ?m=1 juga diterima.

Tidak sepatah kata pun tentang `/search/label/...`. Padahal Master Lupi sendiri
mengirim URL label sebagai salah satu dari dua URL uji, dan URL itulah yang paling
mudah didapat dari halaman kategori blog.

### Akar masalah

Kalimat petunjuk ditulis saat halaman ini hanya menerima artikel katalog. Ketika URL
label ditambahkan di 2.10, yang berubah hanya jalur penguraian — teks di layar tidak
ikut diperiksa. Fitur yang tidak bisa ditemukan sama saja dengan fitur yang tidak ada.

### Perbaikan

Petunjuk menyebut kedua bentuk, lengkap dengan contoh jalurnya:

> Tempel URL halaman katalog novel dari kaoritranslation.blogspot.com: artikel
> katalog (/2025/09/...) atau halaman label (/search/label/...). URL dengan ?m=1
> juga diterima.

### Bukti tes menangkap bug

`theHintNamesBothAddressShapesItAccepts` — membangun halaman, mengambil elemen
`add_novel_hint`, dan memastikan teksnya memuat `/search/label/`, `artikel katalog`,
dan `?m=1`.

Uji mutasi: petunjuk dikembalikan ke kalimat lama → tes itu gagal. Setelah
dipulihkan: **271 tes, 0 gagal**, penanda `MUTATION` nol.

## Drawer bagian dan backup progres membaca (2.20)

Tombol menu di toolbar Perpustakaan dulu membuka popup kecil berisi satu pilihan.
Sekarang tombol yang sama membuka **drawer samping** gaya modal: panel selebar 82%
layar, latar diredupkan 55%, dan animasi geser 220 ms yang tidak dijalankan bila
*Remove animations* aktif. Isinya empat baris — **Novel**, **Manga**, **Backup
progres membaca**, dan **Restore progres membaca** — masing-masing setinggi 64 dp
sehingga tidak ada yang perlu dibidik.

Halaman dan drawer tidak berbagi state: drawer hanya memanggil aksi yang sama
dengan tombol lama (`showLibrary()`, `MangaActivity`). Fitur Manga 2.19 tetap utuh,
hanya jalan masuknya berpindah dari popup ke drawer.

### Kenapa backup mencatat tipe, bukan hanya nilai

Progres baca disimpan di satu berkas preferensi sebagai **peta datar bertipe
campuran**:

| Kunci | Tipe | Arti |
| --- | --- | --- |
| `<novel>:last_url` | `String` | chapter terakhir |
| `<novel>:scroll_pos:<url>` | `int` | posisi gulir |
| `<novel>:progress:<url>` | `int` | persen baca |
| `<novel>:scroll_off:<url>` | `int` | offset di dalam blok |
| `expanded_volumes` | `Set<String>` | akordeon volume yang terbuka |
| `line_spacing` | `float` | spasi baris |

Kalau berkas backup hanya menulis nilai, `SharedPreferences` akan mengembalikan
setiap angka sebagai teks. `getInt` pada kunci yang berisi teks mengembalikan nilai
default, jadi progres terbaca 0 dan pembaca dibuka di awal chapter **tanpa satu pun
pesan galat**. Karena itu setiap nilai ditulis sebagai
`{"type": "...", "value": ...}` dan dipulihkan sesuai tipenya. Himpunan akordeon
ditulis sebagai larik dan dikembalikan sebagai `Set<String>`; kalau tidak, semua
volume akan menutup sendiri setelah restore.

### Restore menolak sebelum menulis

`ReadingBackup.restore()` mem-parse **seluruh** dokumen lebih dulu, menyimpannya di
daftar sementara, dan baru menyentuh penyimpanan kalau semuanya sah:

- format selain `kuromaku-reading-v1` → ditolak;
- JSON terpotong → ditolak oleh parser;
- dokumen `null` atau kosong → ditolak;
- lebih dari 5 MB → ditolak, dan pembacaan berkas berhenti begitu batas terlampaui
  sehingga berkas raksasa tidak pernah ditahan di memori;
- tipe yang tidak dikenal (berkas dari versi lebih baru) → **hanya kunci itu** yang
  dilewati, sisanya tetap dipulihkan.

Restore tidak pernah mengosongkan lebih dulu. Karena itu mencadangkan satu novel
lalu memulihkannya **tidak** menghapus bookmark novel lain — perilaku ini diuji,
bukan diasumsikan.

Jumlah nilai yang diterima dikembalikan ke pemanggil dan ditampilkan sebagai
`"N progres dipulihkan"`, jadi restore yang hanya separuh berhasil tidak tampak
seperti sukses penuh. Setelah restore, halaman rak digambar ulang; cache katalog
**tidak** dibuang, karena membuangnya memaksa request jaringan dan akan membuat
restore yang berhasil terlihat gagal saat offline.

### Berkas milik pembaca

Ekspor memakai `ACTION_CREATE_DOCUMENT` dengan nama saran
`kuromaku-reading-backup.json`; impor memakai `ACTION_OPEN_DOCUMENT` dengan tipe
`application/json` dan kategori `CATEGORY_OPENABLE`. Tidak ada server, tidak ada
akun, dan berkasnya bisa disalin ke Drive atau kartu SD seperti berkas biasa.
Picker yang dibatalkan (`RESULT_CANCELED`) tidak dianggap berkas kosong: progres
tidak disentuh dan tidak ada pesan.

### Bukti tes

`ReadingBackupTest` (9 tes) menguji setiap tipe bolak-balik **sebagai tipe**,
penolakan berkas asing/terpotong/kosong/kebesaran tanpa mengubah progres, isolasi
antar novel, pelewatan tipe asing, dan kestabilan hasil ekspor.
`SectionDrawerTest` (9 tes) menekan tombol menu dan setiap item drawer yang
sungguhan: drawer memuat keempat baris, Backup membuka
`ACTION_CREATE_DOCUMENT` dengan kode permintaan `REQUEST_BACKUP`, Restore membuka
`ACTION_OPEN_DOCUMENT` dengan kode yang berbeda, ekspor menulis dokumen nyata ke
stream terdaftar, dokumen itu dipulihkan lewat `onActivityResult` ke penyimpanan
kosong, picker yang dibatalkan tidak menyentuh progres, dan berkas yang tidak bisa
dibuka dilaporkan sebagai `Gagal: …`.

Uji mutasi 2.20: **18/18 tertangkap** pada berkas hidup dengan `md5` dipulihkan
sesudah setiap mutasi — 14 mutasi fitur (backup, drawer, label progres) lulus
14/14 dalam satu run, sedangkan 4 mutasi animasi drawer butuh **empat run**:

| Run | Hasil | Sebab |
|---|---|---|
| 1 | **1/3** | dua mutasi lolos: tes membaca posisi *sesudah* looper dikuras, sehingga animasi yang seharusnya berjalan sudah selesai lebih dulu |
| 2 | **0/3 — tidak sah** | `#1` dan `#2` `SKIP` karena anchor basi, `#3` `SURVIVED` — celah tes nyata, bukan sekadar target hilang |
| 3 | **3/4** | mutasi durasi lolos: `HeaderAndMotionTest` hanya menguji konstanta `Motion.DURATION_MS`, bukan angka yang dipakai call site |
| 4 | **4/4** | tes membaca posisi **sebelum** looper berjalan dan `decor.animate().getDuration()` |

Bentuk kode animasi ditulis eksplisit (`slide ? -lebar : 0f`) supaya "animasi
mati" tidak bisa tertukar dengan "animasi tidak pernah jalan". Run 2 tidak sah
karena dua dari tiga targetnya sudah tidak ada (`SKIP`), tetapi mutasi ketiga
benar-benar `SURVIVED` — jadi run itu bukan bukti apa pun, sekaligus bukan alasan
untuk mengabaikan celah yang ditunjukkannya.

**Batasan:** pertukaran berkas lewat pemilih dokumen **belum** dicoba di perangkat
sungguhan — verifikasi masih Robolectric dengan `ContentResolver` dan `Dialog`
bayangan. Versi berkas tidak punya tanda tangan atau pemeriksaan integritas: berkas
JSON yang sah tetapi diubah tangan akan diterima selama format dan tipenya benar.

## Bagian Manga tahap 1 (2.19, layar penuh di 2.19.2)

Tombol menu di toolbar Perpustakaan membuka pilihan **Novel / Manga**. Memilih Manga
membuka layar terpisah; memilih Novel membawa kembali ke rak. Menu itu satu-satunya
jalan masuk, jadi tanpa tombol ini layar manga tidak bisa dicapai dari aplikasi.

### Layar penuh 2.19.2

Tombol **⛶** di header Manga menyembunyikan header dan bilah alamat, jadi halaman
manga mendapat seluruh tinggi layar. Status bar dan bilah navigasi ikut hilang lewat
`WindowInsetsControllerCompat`, dan bisa dimunculkan sementara dengan menggeser dari
tepi layar.

**Kembali keluar dari layar penuh lebih dulu**, sebelum memundurkan riwayat WebView
atau menutup layar. Tanpa urutan itu, satu-satunya jalan keluar dari halaman penuh
adalah meninggalkannya — dan posisi baca hilang.

Status layar penuh disimpan di `onSaveInstanceState`, jadi memutar HP tidak
mengembalikan toolbar. Ukuran layar penuh juga bertahan saat layar dibuat ulang.

#### Insets, bukan padding tetap

`setDecorFitsSystemWindows(false)` dipakai supaya layar penuh benar-benar penuh.
Konsekuensinya konten berada di bawah status bar, jadi **padding inset menjadi
satu-satunya** yang mencegah toolbar tertutup. Di mode normal padding atas dan bawah
memakai tinggi sebenarnya dari `WindowInsets` (`systemBars | displayCutout`); di
layar penuh keduanya nol.

Tes mengirim inset 72/48 px dari sistem lalu memeriksa padding yang dihasilkan:
toolbar tergeser tepat 72 px, konten menyisakan 48 px di bawah, dan keduanya nol
setelah tombol ⛶ ditekan. Angka 72 dan 48 itu **dipilih tes**, bukan hasil ukur
perangkat — yang diuji adalah bahwa kode memakai angka yang dilaporkan sistem, bukan
angka yang dipatok sendiri.

### Perbaikan navigasi 2.19.1

Versi 2.19 memakai tombol teks `☰` sebagai tombol menu bagian. Tiga hal salah di sana:

1. **Tombol teks tidak bisa berganti menjadi panah kembali.** Panah kembali dan menu
   bagian memakai `Button` yang sama, jadi panah muncul dengan tulisan `☰`, dan tidak
   ada cara menyembunyikan menu saat masuk ke detail novel atau halaman Unduhan.
   Sekarang menu bagian adalah `ImageButton` berikon (`ic_menu.xml`) dengan area ketuk
   48 dp, dan visibilitasnya diatur eksplisit: menu tampil **hanya** di rak, panah
   kembali mengambil tempatnya di halaman lain.
2. **Ikon tanpa `drawable` lolos dari tes.** Tes lama hanya memeriksa tipe
   `ImageButton`; tombol kosong — bisa diketuk, dibacakan pembaca layar, tapi tak
   terlihat — tetap hijau. Tes sekarang juga menuntut `getDrawable()` tidak null.
3. **Penjaga domain longgar tidak tertangkap.** Tes hanya memuat host yang
   *berakhiran* nama sumber, jadi pemeriksaan `contains(".voratoon.id")` lolos tanpa
   gagal. Ditambah kasus host yang *memuat* nama di tengah
   (`cdn.voratoon.id.evil.com`), yang membedakan "berakhiran" dari "mengandung".

### Mengapa alamatnya `voratoon.id`, bukan subdomain bernomor

Versi pertama sempat memakai subdomain bernomor (`12.voratoon.com`). Nomor itu tidak
stabil: ia berubah dan yang lama bisa mati, sehingga aplikasi akan menampilkan
halaman kosong tanpa penjelasan. Halaman mendarat `https://voratoon.id/` dipakai
supaya **situs sendiri** yang menentukan domain mana yang aktif saat itu; kode tidak
pernah mematok nomor.

### Penjaga domain

Setiap navigasi bingkai utama diperiksa sebelum dimuat:

| Alamat | Hasil |
|---|---|
| `https://voratoon.id/` | diterima |
| `https://cdn.voratoon.id/page/1.jpg` | diterima |
| `https://12.voratoon.com/manga` | diterima |
| `https://evilvoratoon.id/` | **ditolak** |
| `https://voratoon.id.evil.com/` | **ditolak** |
| `http://voratoon.id/` | **ditolak** |
| `file:///etc/hosts`, `javascript:…`, tanpa host | **ditolak** |
| `https://voratoon.id/palsu.evil.com` | diterima — itu path di situs asli |

Baris `evilvoratoon.id` adalah alasan penjaga menulis titik di depan akhiran
(`endsWith(".voratoon.id")`). Tanpanya, host mana pun yang namanya berakhiran
`voratoon.id` akan lolos. Baris terakhir menjaga arah sebaliknya: nama domain yang
muncul di **path** bukan host, dan menolaknya akan mematahkan tautan biasa.

Penolakan tidak diam-diam: pesan "Tautan luar diblokir. Buka melalui browser jika
diperlukan." muncul, dan WebView tidak pernah dibelokkan ke alamat itu.

### Batasan tahap 1

Layar ini menampilkan situs apa adanya, jadi ia masih **WebView** — beda dengan
bagian novel yang sudah native. Belum ada ekstraksi chapter, belum ada penyimpanan
offline untuk manga, dan belum ada tampilan baca khusus. Konsekuensi lain: isi
layar bergantung sepenuhnya pada tata letak situs sumber.

### Bukti tes menangkap bug

`MangaScreenTest` (8 tes saat 2.19.1; kini 12 setelah 4 tes layar penuh di
2.19.2 ditambahkan). Uji mutasi `2.19.1` menjalankan **4 mutasi pada berkas
hidup** dengan `md5` sebelum/sesudah, dan **4/4 tertangkap** setelah dua celah
ditutup:

| Mutasi | Tertangkap oleh |
|---|---|
| Visibilitas menu bagian dihapus (menu tetap tampil di Unduhan) | `theSectionMenuStepsAsideOnTheOtherPages` |
| Panah kembali selalu tampil di rak | `theSectionMenuStepsAsideOnTheOtherPages` |
| Tombol menu kembali jadi tombol teks tanpa ikon | `theNovelToolbarOffersTheMangaSection` |
| Penjaga domain longgar (`contains` ganti `endsWith`) | `aLookalikeHostIsRejected` |

Run pertama hanya **2/4**: mutasi tombol kosong dan penjaga longgar sama-sama
bertahan hijau. Itu bukan keberhasilan, itu celah tes — kedua tes di atas ditulis
ulang sampai mutasinya gagal, lalu run diulang 4/4.

Penjaga domain sendiri tetap diuji dari 2.19: titik di depan akhiran, `https`,
`voratoon.com`, deklarasi manifest, tag tombol, dan penyimpanan posisi halaman.

## Fitur Cover Volume & Indikator Unduhan (2.18)

Dua keluhan dari laporan diterjemahkan menjadi satu perubahan yang bisa diukur:
volume novel yang ditambahkan lewat URL menampilkan **"Vol 1", "Vol 2", "Vol 3"**
alih-alih cover aslinya, dan baris unduhan mencetak teks berdempetan
**"67%Mengunduh…"** dengan tombol berlabel "Menunggu" pada chapter yang justru
sedang diunduh.

### Akar masalah cover: bukan parser, tapi tabel

`VolumeCoverParser` ternyata **sudah benar** sejak awal — diukur pada tiga sumber
asli: Yarikon **3 cover**, Monogatari **7**, Magical★Explorer **14**, semuanya cocok
dengan tabel. Yang salah adalah `Catalog.withCovers()`: ia **hanya** membaca tabel
hardcoded `VolumeCovers`, yang tidak punya entri untuk novel yang ditambahkan lewat
URL. Karena hasilnya `null`, UI jatuh ke placeholder.

Perbaikannya bukan menambah entri tabel — itu hanya akan memperbaiki Yarikon hari
ini dan gagal untuk novel berikutnya:

1. **`LandingCatalogParser.withParsedCovers`** dan
   **`NovelDiscovery.withCovers`** menempelkan cover hasil parse ke volume yang
   bersangkutan, dibaca dari **body artikel yang sama** dengan chapter-nya. Tidak ada
   request tambahan, dan cover tidak bisa berasal dari versi halaman yang berbeda.
2. **Cover hasil parse menang** atas tabel bawaan di `Catalog.withCovers()`. Tabel
   kini hanya cadangan untuk volume yang tidak digambarkan halaman sumbernya.
3. **`VolumeCovers.forVolume` tidak lagi meminjam tabel Magical★Explorer** untuk id
   yang tidak dikenal. Sebelumnya setiap novel asing mendapat cover Magical★Explorer
   — yaitu menampilkan artwork seri lain pada novel pengguna.
4. **Cover disimpan ke tabel Room baru `volume_covers`** (database versi 2, migrasi
   `1 → 2` menambah tabel saja; baris novel tidak disentuh). Detail novel memakai
   `Catalog.withStoredCovers` sehingga cover tetap tampil dari cache tanpa parsing
   ulang, dan cover yang lebih baru tidak pernah ditimpa yang lama.
5. **Volume tanpa cover tetap tanpa cover.** Tidak ada peminjaman cover tetangga dan
   tidak ada ilustrasi chapter yang diambil sebagai cover — `VolumeCoverParser`
   berhenti menunggu cover begitu tautan chapter pertama muncul di bawah heading.
6. **Pemilihan tautan manual tidak menghilangkan cover**: `NovelDiscovery.reselect`
   memindahkan cover hasil parse sebelumnya ke daftar volume yang disusun ulang
   (`keepCovers`).

### Akar masalah "67%Mengunduh…": dua TextView tanpa jarak

Persentase dan status adalah **dua TextView terpisah yang berdempetan tanpa
padding**, dan label tombolnya salah: baris yang sedang mengunduh menampilkan
"Menunggu". Perbaikannya:

- **`RingProgress`** — view cincin baru. Ia menggambar busur sesuai fraksi yang
  diberikan, menganimasikan 180 ms menuju nilai itu (tidak pernah mengarang nilai),
  dan berubah menjadi spinner berputar ketika ukuran total tidak diketahui. Animasi
  dihormati: bila "Remove animations" aktif, nilai baru langsung tampil.
- **Persentase punya view dan jarak sendiri** (`download_percent:<url>`), status
  punya padding kanan 8 dp, tombol 48 dp. Teks tidak bisa lagi menempel.
- **`bytePercent`** mengalirkan progres byte nyata dari `Http.getBytes(url,
  progress)` → `Fetcher.Source.image(url, progress)` → `ChapterDownloader.Progress`
  → `DownloadStatusStore.setBytes`. Tanpa `Content-Length`, nilainya **-1** dan UI
  hanya menampilkan spinner — tidak ada angka yang tidak diukur.
- **Label tombol mengikuti status**: "Menunggu" hanya untuk antrean, "Mengunduh"
  untuk yang berjalan.

### Status parsial (teks ada, ilustrasi gagal)

Permintaan nomor 8 mengubah perilaku lama secara sadar: dulu satu ilustrasi rusak
**menggagalkan seluruh chapter** dan teks yang sudah terunduh dibuang. Sekarang:

- `ChapterDownloader.fetchImagesTolerant` mengunduh semua ilustrasi yang bisa,
  menghitung yang gagal, dan **tetap menyimpan teksnya**;
- entri offline menyimpan `imagesExpected`, jadi chapter parsial tetap parsial
  setelah aplikasi ditutup (`isComplete` berbeda dari `isSaved`);
- `VolumeJob` menandai chapter itu `PARTIAL`, tidak menghitungnya selesai, dan
  `DownloadWorker` mengakhiri job tanpa retry buta — UI yang menawarkan
  **"Lanjutkan"**;
- gambar rusak **tidak pernah** ditulis dengan nama `.jpg`; ia dihitung hilang.

Tes lama `imageThatIsNotAPictureFailsTheChapterInsteadOfSavingIt` diganti
`brokenIllustrationStoresTextAsPartialAndCountsTheMissingPicture`; perbedaan
perilaku ini dilaporkan, bukan disembunyikan.

### Tampilan volume

| Keadaan | Yang tampil di kartu volume |
|---|---|
| Belum diunduh | "Download Semua" |
| Sedang berjalan | cincin + "50% · 1 dari 2 chapter" + "Batalkan" |
| Sebagian | "1 dari 2 tersimpan" + "Lanjutkan Download" |
| Selesai | centang + "Tersimpan semua" (tombol nonaktif) |

"Tersimpan semua" tidak pernah muncul selama masih ada chapter yang belum lengkap.

### Bukti tes menangkap bug

`VolumeCoversAndDownloadStateTest` (17 tes) memakai `yariko_article_covers.html` —
artikel asli yang memicu laporan — dan mengukur UI pada layar 320 dp.
`RingProgressMotionTest` (5 tes) menguji cincin progres secara langsung: nilainya
langsung tampil saat "Remove animations" aktif, animasi berakhir tepat pada angka
yang diberikan, spinner tidak pernah melaporkan persen, dan fraksi di luar 0–100
dijepit.

Uji mutasi `2.18` menjalankan **15 mutasi pada berkas hidup** dengan `md5`
sebelum/sesudah, dan **15/15 tertangkap**:

| Mutasi | Tertangkap oleh |
|---|---|
| Cover hasil parse tidak ditempel lagi | `everyVolumeOfTheUrlAddedNovelCarriesItsOwnCover` |
| Semua volume memakai cover pertama | `everyVolumeOfTheUrlAddedNovelCarriesItsOwnCover` |
| Tabel bawaan menimpa cover hasil parse | `builtInTableDoesNotReplaceAParsedCover` |
| Novel asing meminjam tabel Magical | `volumeWithoutArtworkStaysWithoutOne` |
| Persentase volume dipalsukan jadi 100 | `volumeWhileDownloadingShowsRingPercentAndCount` |
| "Tersimpan semua" walau belum lengkap | `tersimpanSemuaIsNeverShownForAnIncompleteVolume` |
| Persentase menempel ke status | `downloadingChapterShowsSeparatePercentAndStatus` |
| Angka persen dikarang saat ukuran tidak diketahui | `unknownSizeShowsSpinnerWithoutAPercentage` |
| Chapter parsial dianggap lengkap | `brokenIllustrationStoresTextAsPartialAndCountsTheMissingPicture` |
| `isComplete` disamakan dengan `isSaved` | `brokenIllustrationStoresTextAsPartialAndCountsTheMissingPicture` |
| Gambar rusak tetap ditulis | `brokenIllustrationStoresTextAsPartialAndCountsTheMissingPicture` |
| Progres byte tidak diteruskan ke status | `byteProgressOfTheRunningChapterReachesTheStatusStore` |
| Animasi dijalankan walau "Remove animations" aktif | `withMotionOffTheRingJumpsStraightToTheMeasuredValue` |
| Spinner melaporkan persen padahal total tidak diketahui | `aRingWithoutAMeasuredTotalNeverReportsAPercentage` |
| Fraksi di luar 0–100 tidak dijepit | `aFractionOutsideTheRangeIsClampedInsteadOfDrawnOutsideTheRing` |

### Hasil rilis 2.18 / 2.18.1 (terukur)

- `2.18` — commit `fe519a8`; Actions run **`41623707350`** `completed success`.
  APK `versionCode='38'` / `2.18`, 5.547.084 byte.
- Dokumen `2.18` — commit `516bb4b` (run `41624149137`) dan `e0d5d40`
  (run `41624759027`), keduanya success.
- `2.18.1` — tes `RingProgressMotionTest` (5 tes) dan mutasi 13–15.
  Commit `fc5b9c5` membuat Actions run `41627748298` **gagal** padahal clone bersih
  lulus 3× lokal: tes animasi mengharuskan seluruh 180 ms habis dalam satu
  `ShadowLooper.idle`, yang tidak dijamin runner lain. Commit `8499773` mengganti
  itu dengan 40 langkah 20 ms sampai nilai mengendap, sekaligus menambahkan langkah
  CI yang **menulis nama setiap tes gagal ke job summary** — sebelumnya log CI butuh
  token, jadi kegagalan hanya terbaca "exit code 1".
- `2.18.1` final: run **`41631200544`** (`8499773`) `completed success`, semua 13
  langkah hijau termasuk Publish APK release. Lokal **306 tes / 29 kelas / 0 gagal**.
  APK rilis **5.547.144 byte**, `aapt2 dump badging` →
  `versionCode='39' versionName='2.18.1'` (exit 0).
- **`md5` APK tidak dicatat sebagai patokan.** Setiap build ulang menghasilkan byte
  berbeda pada ukuran yang sama, jadi `md5` hanya berguna untuk membandingkan satu
  unduhan dengan unduhan lain **dari build yang sama**. Yang menentukan versi adalah
  keluaran `aapt2`, bukan hash.
- Probe live pada sumber asli: Monogatari **116 chapter / 7 volume / 7 cover**;
  Magical★Explorer **141 chapter / 14 volume / 14 cover**; Yarikon lewat URL
  **43 chapter / 3 volume / 3 cover** (16+13+14).

### Yang belum diuji di perangkat

Tidak ada emulator atau HP di mesin build, jadi verifikasi UI hanya lewat
Robolectric. Yang **belum** dibuktikan pada perangkat nyata: cincin 60 fps, animasi
yang dihormati saat "Remove animations" aktif, notch/gesture bar, koneksi lambat
atau terputus sungguhan, aplikasi ditutup paksa saat unduhan berjalan, dan mode
pesawat. Semua itu masih perlu dicoba manual.

## BUG 12 (2.17) — Sumber katalog Monogatari berhenti bekerja, daftar chapter jadi 0

### Gejala

Membuka **Monogatari no Kuromaku** gagal dengan pesan:

> Respons feed halaman ke-1 tidak dapat dibaca

Daftar chapter kosong: 0 chapter, 0 volume. Novel lain (Magical★Explorer,
Yarikon) tetap normal, jadi ini bukan kerusakan jaringan atau parser umum.

### Akar masalah

Monogatari adalah satu-satunya novel yang katalognya **hanya** bergantung pada feed
label Blogger: `feeds/posts/summary/-/Monogatari no Kuromaku`. Sumbernya sendiri
punya artikel daftar isi yang lengkap — `2025/09/ln-monogatari-no-kuromaku-ni-tensei.html` —
tetapi aplikasi tidak pernah membacanya. Ketika satu halaman feed gagal diuraikan,
seluruh katalog ikut hilang karena tidak ada sumber kedua. Satu titik kegagalan
untuk seluruh daftar chapter.

Artikel itu juga **tidak** identik dengan feed, dan bedanya penting:

- Artikel memuat **7 heading volume** dan 116 tautan chapter; landing lama yang
  dibaca parser generik hanya menghasilkan 111 chapter karena heading volume tidak
  dibaca sebagai penanda.
- Satu tautan di artikel salah: entri berlabel `Prolog` pada Volume 2 menunjuk ke
  URL `...volume-2_49.html`, URL yang sama dengan `Chapter 1`. Prolog sebenarnya
  ada di `...volume-2_12.html` dan hanya dikenal feed.
- Parser lama membuang tautan kedua saat URL-nya kembar, sehingga `Chapter 1`
  hilang seluruhnya dari daftar — bukan cuma Prolog yang salah alamat.

### Perbaikan

Sumber katalog Monogatari dipindahkan ke artikelnya sendiri, dengan feed label
tetap dipakai sebagai penambal dan cadangan:

1. **Mode baru `CATALOG_ARTICLE_FEED`** (`Novel.CATALOG_ARTICLE_FEED = 3`).
   Monogatari memakainya; Magical★Explorer tetap `CATALOG_LANDING`, Yarikon tetap
   `CATALOG_FEED` atau jalur label sesuai bentuk URL yang ditempel.
2. **`CatalogueMerge`** menggabungkan dua sumber dengan aturan yang jelas:
   - artikel menentukan **urutan, volume, dan judul**;
   - bila URL sebuah chapter di artikel ternyata dipakai chapter lain, dan feed
     mengenal URL yang benar untuk chapter itu, tautannya **diperbaiki** (Prolog
     Volume 2 → `_12`);
   - chapter yang hanya diketahui feed **ditambahkan** ke volume yang sesuai;
   - tidak ada chapter yang dihitung dua kali; kuncinya judul **dan** URL.
3. **`LandingCatalogParser`** tidak lagi membuang tautan hanya karena URL-nya
   kembar. Kuncinya kini `URL | volume | label`, jadi dua chapter berbeda yang
   kebetulan menunjuk URL sama tetap tercatat, dan pembersihan duplikat tetap jalan
   untuk anchor yang benar-benar sama.
4. **Kegagalan tidak mengosongkan apa pun.** Kalau artikel gagal dan feed berhasil,
   katalog diambil dari feed disertai peringatan jujur di panel diagnostik. Kalau
   **keduanya** gagal, loader **melempar galat** dan katalog lama di rak tidak
   diganti daftar kosong.
5. **Baris lama di perpustakaan ikut diperbaiki** lewat `upgradeBuiltIns()`: novel
   bawaan yang masih tercatat memakai feed lama dipindahkan ke sumber baru.
   Yang **tidak** disentuh: `id`, `position`, judul, metadata yang sudah tersimpan,
   progres membaca, bookmark, dan unduhan offline. Tidak ada baris baru yang dibuat,
   jadi tidak ada duplikat.
6. **Muat Ulang** memakai jalur yang sama, jadi chapter baru yang muncul di artikel
   atau di feed ikut terambil tanpa memperbarui APK.

### Hasil ukur pada sumber asli

| | Sebelum (2.16) | Sesudah (2.17) |
|---|---|---|
| Monogatari chapter | **0** (feed gagal) | **116** |
| Monogatari volume | 0 | **7** (14+18+17+17+16+18+16) |
| Monogatari cover volume | 0 | **7 dari 7** |
| Monogatari metadata | kosong | penulis **Yuuki Ryou** terbaca dari artikel |
| Magical★Explorer | 141 chapter / 14 volume | **141 chapter / 13 volume berisi** (tidak berubah) |
| Yarikon | 43 chapter / 3 volume | **43 chapter / 3 volume** (tidak berubah) |

Angka 116 berasal dari 115 tautan unik di artikel ditambah 1 chapter yang hanya
dikenal feed. Peringatan yang ditampilkan aplikasi saat itu:

> Daftar chapter diambil dari artikel katalog sumber, 1 tautan yang salah di
> artikel diperbaiki dari feed label.

### Bukti tes menangkap bug

`MonogatariCatalogueTest` (9 tes) memakai dua fixture yang diambil dari sumber asli:
`monogatari_article.html` (artikel daftar isi) dan `monogatari_feed.json`
(117 entri feed label). Yang diuji antara lain:

- `loadingTheBuiltInMonogatariUsesArticleAndFeed` — 116 chapter, 7 volume, Prolog
  Volume 2 memakai URL `_12`, `Chapter 1` tetap `_49`;
- `aChapterThatOnlyTheFeedKnowsYetIsAddedOnReload` — chapter baru dari feed ikut
  muncul tanpa update APK;
- `whenBothSourcesFailTheLoaderRefusesInsteadOfReturningNothing` — kedua sumber
  gagal → galat, bukan daftar kosong;
- `anOlderStoredRowIsPointedAtTheNewSourceWithoutLosingAnything` — baris lama
  dipindahkan sumbernya, `id`/posisi/judul/progres tetap;
- `aChapterAlreadyOnThePageIsNotAddedTwice` — chapter yang sudah ada di artikel
  tidak ditambahkan lagi oleh feed, jadi tidak ada duplikat.

Uji mutasi `2.17` (5 mutasi, dijalankan pada berkas hidup dengan `md5` sebelum dan
sesudah):

| Mutasi | Tertangkap oleh |
|---|---|
| Perbaikan tautan kembar dimatikan | `loadingTheBuiltInMonogatariUsesArticleAndFeed` |
| Penambahan chapter dari feed dimatikan | `aChapterThatOnlyTheFeedKnowsYetIsAddedOnReload` |
| `LandingCatalogParser` kembali dedupe URL saja | `loadingTheBuiltInMonogatariUsesArticleAndFeed` |
| Loader mengembalikan katalog kosong saat kedua sumber gagal | `whenBothSourcesFailTheLoaderRefusesInsteadOfReturningNothing` |
| Upgrade baris lama di `Library` dimatikan | `anOlderStoredRowIsPointedAtTheNewSourceWithoutLosingAnything` |

Kelima-limanya **tertangkap**; pemulihan `md5` cocok untuk keempat berkas. Setelah
dipulihkan: **283 tes, 27 kelas, 0 gagal**, penanda `MUTATION` nol, working tree bersih.

## Pengujian 2.16 — pemilih tautan manual akhirnya punya tes

Versi ini tidak menyentuh kode produksi. Yang kurang sejak 2.10 bukan fitur, tapi
bukti: `AddNovelPage.picker()` — daftar centang yang muncul saat parser tidak yakin —
belum pernah dijalankan oleh satu tes pun. Tiga janji yang menyertainya karena itu
hanya dijamin oleh pembacaan kode.

### Yang sekarang dijaga `AddNovelPickerTest`

1. **Hanya tautan yang mungkin chapter yang ditawarkan.** Fixture
   `unlabelled_catalogue.html` memuat lima tautan seri, salah satunya menunjuk
   halaman yang sedang dibaca. Kotak centang yang muncul harus sebanyak
   `selectableCandidates()` — bukan `candidates` — karena tautan navigasi atau iklan
   yang bisa dicentang berarti satu ketukan cukup untuk mengimpor iklan sebagai
   chapter. Judul pemilih juga harus menyebut kedua angka ("3 dari 5"), supaya daftar
   yang terpotong tidak disangka daftar lengkap.
2. **Centang dan batal centang menentukan isi pilihan.** Tes mencentang tiga kotak
   lalu membatalkan yang ketiga, menekan "Pakai pilihan ini", dan memastikan yang
   terkirim tepat dua — bukan "semua yang pernah disentuh".
3. **Tidak ada yang dikirim tanpa tombol.** Setelah kotak dicentang, daftar
   `onApplySelection` harus masih kosong. Katalog yang sudah terbaca dengan yakin
   (`yariko_toc.html`, 43 chapter) juga tidak boleh menampilkan pemilih sama sekali.

### Uji mutasi

Tiga mutasi dijalankan satu per satu, semuanya tertangkap:

1. Pemilih menawarkan `preview.candidates` (semua tautan) bukan yang `selectable` →
   `applySendsExactlyTheLinksLeftTicked` gagal.
2. Batal centang tidak menghapus URL dari `state.selected` → tes yang sama gagal.
3. Pemilih ditampilkan untuk katalog yang sudah yakin →
   `aConfidentCatalogueHidesThePicker` gagal.

Setelah dipulihkan: **274 tes, 0 gagal**, penanda `MUTATION` nol, working tree bersih.

### Yang tetap belum diuji di perangkat

Tes ini memakai Robolectric pada 320 dp — tata letak dan perilaku klik nyata, tapi
bukan layar HP sungguhan.

Angka yang benar-benar diukur, bukan diperkirakan:

- fixture `unlabelled_catalogue.html` → **5 kandidat, 3 boleh dipilih**, 0 chapter,
  `needsConfirmation = true`; itulah yang diuji;
- `yariko_toc.html` → **47 kandidat, 43 boleh dipilih**, 43 chapter, pemilih tidak
  muncul;
- artikel Yariko live → **47 tautan, 43 chapter, 3 volume (16+13+14)**;
- URL label Yariko live → **44 tautan, 42 chapter, 3 volume (15+13+14)**.

Jadi pemilih yang paling padat baru diuji dengan **3 kotak centang**. Kandidat
terbanyak yang terukur di katalog nyata adalah **43** — angka itu belum pernah
dirender sebagai pemilih di tes mana pun. Layar HP sungguhan dengan puluhan kotak
centang (scroll di dalam scroll, tinggi layar, jempol) masih perlu dicoba Master Lupi.

### Verifikasi ulang mutasi 2.11 di pohon 2.16

Sebuah proses mutasi lama (skrip 2.11) mengirim notifikasi terlambat bahwa
**N2 — alasan "potongan judul" dihapus — LOLOS (buruk)**. Notifikasi itu tidak
menggambarkan kode sekarang: skrip tersebut menulis mutannya ke *snapshot lama*, lalu
memulihkan dari snapshot yang sama, sehingga verdiktnya berlaku untuk pohon lama.

Kelima mutasi 2.11 dijalankan ulang di pohon 2.16, dengan backup diambil dari berkas
hidup saat itu juga (`md5sum` sebelum/sesudah dibandingkan, bukan dipercaya):

1. N1 alasan `"tautan gambar"` dihapus → `everyRejectionSaysWhatTheLinkActuallyIs` gagal.
2. N2 alasan `"potongan judul"` dihapus → `aStrayNumeralIsNamedAsAFragment` gagal.
3. N3 `mergeSplitLabels` dimatikan → `aNumeralTornIntoItsOwnAnchorIsPutBack` gagal.
4. N4 alasan spesifik ditimpa alasan generik → `everyRejectionSaysWhatTheLinkActuallyIs` gagal.
5. N5 label interlude memakai angka apa adanya → `anInterludeIsLabelledTheSameFromDigitsOrRoman` gagal.

**Kelima mutasi tertangkap; tidak ada yang lolos.** Pemulihan terverifikasi
(`RESTORE ND: OK`, `RESTORE CN: OK`), `git diff HEAD` kosong, penanda `MUTATION` nol,
dan suite penuh tetap **274 tes, 0 gagal**.

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
- **URL hasil parser**: 44 URL dari 7 postingan Illustrasi (5+5+5+8+7+7+7) diunduh
  langsung → **44/44 HTTP 200**, `Content-Type: image/*`, hasil decode JPEG/PNG valid.
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

`app/src/test/java/id/shuyasal/kuromaku/` berisi **340 pengujian** di 32 kelas,
semuanya lulus:

- **AddNovelDiagnosticsTest** (3) — panel diagnostik menyebut sumber tautannya
  sesuai jenis alamat: "di feed label" untuk URL label, "di artikel" untuk URL
  artikel. Dibangun lewat Robolectric pada 320 dp.
- **AddNovelPickerTest** (3) — pemilih tautan manual lewat Robolectric pada 320 dp:
  hanya tautan yang mungkin chapter yang ditawarkan, centang/batal centang menentukan
  isi pilihan, dan tidak ada yang dikirim sebelum tombol ditekan. Tiga mutasi
  terbukti tertangkap.
- **YarikoAndLabelTest** (28) — dua kegagalan dari laporan, dibuktikan lewat fixture
  HTML dan feed asli. URL label dinormalisasi menjadi satu identitas walau diketik
  dengan `?m=1`, garis miring, `+`, atau huruf besar; alamat feed label dibangun untuk
  label apa pun (tidak di-hardcode); katalog label dibaca dari **dua halaman feed**
  (44 postingan) dan menghasilkan 42 chapter dalam 3 volume; label kosong ditolak
  alih-alih menyimpan novel kosong; halaman label tidak boleh menjadi judul/cover
  novel; postingan deskripsi tidak dihitung sebagai chapter dan alasannya dilaporkan;
  `Interlude I/II/III` tetap tiga chapter berbeda; artikel Yariko asli terbaca
  43 chapter / 3 volume dengan metadata lengkap; setiap tautan yang ditolak membawa
  alasannya; postingan satu-chapter ditolak sebagai katalog tapi tetap bisa dibaca;
  katalog tanpa kata "chapter" ditawarkan untuk dipilih manual, dan tautan di luar
  halaman tidak bisa dipilih; Magical★Explorer (141 chapter / 14 volume) dan
  Monogatari (111 chapter / 7 volume) tidak berubah.
- **DynamicCatalogueTest** (9, parser generik + normalisasi URL) — sepuluh bentuk URL
  yang tidak layak ditolak dengan pesan yang bisa dibaca; satu halaman menghasilkan
  satu identitas untuk sembilan penulisan berbeda (`?m=1`, garis miring di akhir,
  parameter pelacak, `www`, `http`, huruf besar, spasi di tepi, tanpa skema);
  artikel biasa ditolak alih-alih disimpan sebagai novel kosong; halaman tanpa badan
  artikel ditolak; katalog berformat berbeda (`entry-content`, heading `h4`, ordered
  list, label tebal dengan titik dua terpisah) terbaca lengkap 6 chapter dalam 2
  volume, urutan `Prolog → Chapter 1 → Chapter 2 → Illustrasi` benar walau sumber
  menulis "Cahpter 2", cover memilih gambar potret di dalam tautan dan bukan banner
  lebar, tautan menu/footer bukan chapter; membaca ulang halaman yang kini punya satu
  volume baru memunculkan chapter dan volume itu tanpa mengubah URL chapter lama;
  parser generik yang sama juga membaca kedua halaman novel bawaan (7 volume
  Monogatari, 13 volume Magical, Volume 14 tanpa chapter palsu).
- **DynamicLibraryTest** (15, Room sungguhan) — tabel kosong di-seed dua novel
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
- **DynamicDownloadTest** (9, Robolectric) — novel hasil tambah lewat URL, bukan dua
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
**44/44 URL hasil parser** dari 7 postingan Illustrasi diunduh → HTTP 200, `image/*`, decode valid.
Angka 44 sama dengan yang dijaga `ChapterImageParserTest` (5+5+5+8+7+7+7).

- **OfflineDownloadTest** (18, Robolectric) — inti penyimpanan offline:
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
- **DownloadUiTest** (16, Robolectric, 320×640dp) — kontrol unduh di halaman
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
  Empat tes 2.20 menguji label progres baca: chapter yang sedang dibaca menulis
  "67% dibaca" di view sendiri, chapter selesai menulis "Selesai dibaca", chapter
  yang belum dibuka tidak punya label sama sekali, dan progres baca tidak pernah
  berbagi satu view dengan status unduhan — bug "67%Mengunduh…" yang dilaporkan.

- **ReadingBackupTest** (9) — cadangan progres membaca sebagai berkas milik
  pembaca. Penyimpanan progres adalah peta datar bertipe campuran (bookmark
  string, offset gulir int, persen int, akordeon string set), jadi setiap tipe
  diuji bolak-balik **sebagai tipe**, bukan hanya nilainya: int yang kembali
  sebagai teks akan mereset progres tanpa pesan. Berkas asing, berkas terpotong,
  dokumen kosong, dan berkas di atas batas 5 MB ditolak **sebelum** apa pun
  ditulis, dan progres yang sudah ada tidak berubah. Cadangan satu novel tidak
  menghapus bookmark novel lain. Tipe dari versi lebih baru dilewati satu kunci
  saja, sisanya tetap dipulihkan.

- **SectionDrawerTest** (9, Robolectric) — drawer bagian dan backup yang
  dibawanya, ditekan lewat **kontrol nyata**: tombol menu di toolbar Perpustakaan
  membuka drawer yang memuat Novel, Manga, Backup, dan Restore. Menekan Backup
  membuka `ACTION_CREATE_DOCUMENT` dengan tipe `application/json`, nama berkas
  berakhiran `.json`, dan kode permintaan `REQUEST_BACKUP`; menekan Restore
  membuka `ACTION_OPEN_DOCUMENT` dengan kode permintaan yang berbeda. Item drawer
  tanpa listener akan tampak sama seperti yang berfungsi, jadi setiap tes menekan
  itemnya. Ekspor menulis dokumen sungguhan ke stream terdaftar yang memuat titik
  baca, dan dokumen itu dipulihkan kembali lewat `onActivityResult` ke penyimpanan
  kosong. Picker yang dibatalkan tidak menyentuh progres, dan berkas yang tidak
  bisa dibuka dilaporkan sebagai "Gagal: …" alih-alih diam.

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

- **MangaScreenTest** (12, Robolectric) — layar Manga: sumber dan
  subdomainnya diterima, domain lama diterima, kemiripan nama (`evilvoratoon.id`,
  `voratoon.id.evil.com`, `cdn.voratoon.id.evil.com`) ditolak,
  `http`/`file`/`javascript`/tanpa host ditolak, path yang memuat nama domain di
  situs asli tetap diterima, layar terdaftar di manifest dan tidak diekspor, tombol
  menu bagian berikon 48 dp, menu itu hanya tampil di rak, halaman terakhir bertahan
  saat layar dibuat ulang, dan empat tes layar penuh: header serta bilah alamat hilang
  lalu kembali lewat tombol ⛶, `Kembali` keluar dari layar penuh tanpa menutup layar,
  status layar penuh bertahan setelah dibuat ulang, serta inset 72/48 px dari sistem
  menggeser toolbar dan menyisakan ruang bawah — nol saat layar penuh.

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
├── MangaActivity.java         # layar Manga: WebView, hanya voratoon.id / voratoon.com
├── Session.java               # katalog in-memory untuk navigasi chapter
├── data/
│   ├── Block.java             # satu unit render (teks/heading/gambar), urutan dokumen
│   ├── Catalog.java           # katalog satu novel: volume + helper navigasi
│   ├── CatalogueMerge.java    # isi celah artikel katalog dari entri feed label
│   ├── Chapter.java           # satu entri chapter dari feed
│   ├── FeedEntry.java         # catatan feed minimal
│   ├── Library.java           # rak milik user, disimpan di database
│   ├── LibraryBuilder.java    # parsing judul → volume, label, urutan baca
│   ├── Novel.java             # satu novel: metadata, cover, cara katalog dibaca
│   ├── NovelInfo.java         # metadata Monogatari (dipakai tes lama)
│   ├── NovelMapper.java       # baris database ↔ Novel
│   ├── Novels.java            # daftar dua novel bawaan (teks dari halaman sumber)
│   ├── ReadingBackup.java     # backup/restore progres baca sebagai berkas milik user
│   ├── Volume.java            # satu volume: nomor, chapter, cover sendiri
│   └── VolumeCovers.java      # tabel cover per volume (diverifikasi tes)
├── db/
│   ├── NovelDatabase.java     # skema perpustakaan: satu baris per URL katalog
│   ├── NovelDao.java          # akses tabel perpustakaan
│   ├── NovelRecord.java       # satu baris novel, dikunci URL ternormalisasi
│   ├── VolumeCoverDao.java    # cover per volume tersimpan
│   └── VolumeCoverRecord.java # satu cover volume (tabel sendiri, bukan kolom)
├── net/
│   ├── Http.java              # OkHttp, hanya dokumen teks
│   ├── CatalogLoader.java     # pilih feed vs halaman katalog, pasang cover
│   ├── FeedClient.java        # feed JSON label, paginasi penuh
│   ├── FeedException.java     # kegagalan per halaman
│   ├── Fetcher.java           # sumber byte; tes mengganti dengan fixture
│   └── NovelUrl.java          # normalisasi URL novel yang diketik user
├── offline/
│   ├── OfflineStore.java      # chapter di disk, tulisan atomik (.tmp → rename)
│   ├── ChapterDownloader.java # unduh satu chapter: teks + ilustrasi
│   ├── DownloadQueue.java     # pintu tunggal UI: unduh, ulang, batal, periksa
│   ├── DownloadWorker.java    # WorkManager: satu chapter atau satu volume
│   ├── VolumeJob.java         # loop berurutan satu volume
│   ├── DownloadStatusStore.java # antrean/unduh/tersimpan/gagal untuk UI
│   ├── DownloadNotifier.java  # notifikasi progres unduhan volume
│   └── ImageValidator.java    # pastikan byte benar-benar gambar
├── parser/
│   ├── ChapterParser.java     # Jsoup: scrub situs → blok teks/gambar berurutan
│   ├── ChapterNaming.java     # baca tautan chapter: label + posisi volume
│   ├── LandingCatalogParser.java # katalog dari tautan di halaman landing
│   ├── VolumeCoverParser.java # cover per volume dari halaman landing
│   ├── NovelDiscovery.java    # baca novel apa pun langsung dari halaman sumber
│   ├── ChapterContent.java    # judul + daftar blok + URL sumber
│   ├── ContentCache.java      # cache LRU 6 chapter
│   └── ParseException.java    # isi artikel tidak ditemukan
└── ui/
    ├── NovelListPage.java     # halaman perpustakaan: kartu per novel
    ├── LibraryPage.java       # halaman detail novel + accordion volume + cover volume
    ├── AddNovelPage.java      # "Tambah Novel": tempel URL, pratinjau, simpan
    ├── DownloadsPage.java     # "Unduhan": yang tersimpan + job yang berjalan
    ├── RatioFrameLayout.java  # kotak rasio tetap untuk sampul
    ├── ImageCell.java         # sel ilustrasi: kotak berasio, error + coba lagi
    ├── ZoomableImageView.java # cubit/geser/ketuk dua kali
    ├── ImageLoadLog.java      # logging tahap gambar, URL disamarkan
    ├── BlockAdapter.java      # render blok jadi view native
    ├── ScrollMath.java        # progres, restore posisi, padding footer
    ├── SystemBarInsets.java   # insets per layar, diterapkan walau dispatch kosong
    ├── Motion.java            # animasi pendek yang menghormati "Remove animations"
    ├── RingProgress.java      # cincin progres unduhan, hanya angka yang diukur
    └── Palette.java, Prefs.java, Ui.java
```

Kontainer artikel yang dikenali: `div.post-body.entry-content`, lalu `div.post-body`,
`div.entry-content`, dan `article` sebagai cadangan.

## Build

**Persyaratan pemasangan: Android 8.0 (API 26) atau lebih baru.** Diukur dari APK
yang terbit, bukan dari ingatan: `aapt2 dump badging` memberi
`minSdkVersion:'26'` / `targetSdkVersion:'35'`. Di Android 7.1 ke bawah pemasangan
ditolak oleh sistem, dan aplikasi tidak akan muncul sebagai pilihan. APK rilis
ditandatangani dengan kunci **Android Debug** (`CN=Android Debug`) memakai skema
tanda tangan **v2** saja — cukup untuk dipasang dan diperbarui di perangkat, tetapi
bukan kunci rilis Play Store.

GitHub Actions: **Actions → Build Kuromaku Reader APK** (`.github/workflows/android.yml`).
Alur: unit test → `assembleDebug` → verifikasi APK dengan `aapt2` → artifact → rilis `latest-apk`.

Lokal (JDK 17, Android SDK 35, Gradle 8.9):

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
gradle testDebugUnitTest assembleDebug
```

## Atribusi dan hak cipta

- Seluruh teks dan ilustrasi milik penulis, penerbit, dan penerjemah aslinya.
- Aplikasi ini menampilkan konten publik dari situs sumber saat dibuka: **tidak**
  menyertakan salinan chapter di dalam APK.
- Satu-satunya teks sumber yang ditanam di APK adalah sinopsis resmi dari halaman novel
  (2.467 karakter untuk dua novel), ditampilkan di layar detail beserta atribusi. Cover
  hanya disimpan sebagai URL ke sumbernya, bukan berkas gambar di dalam APK.
- Chapter yang diunduh pengguna disimpan **di perangkat** (`getFilesDir()/offline/`), bukan
  di dalam APK, dan bisa dihapus kapan saja lewat halaman Unduhan tanpa menghapus progres
  membaca.
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

### Batasan parser generik — diukur, bukan diperkirakan

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
