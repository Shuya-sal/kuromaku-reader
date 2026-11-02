# Kuromaku Reader 2.5

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

## Pengujian

`app/src/test/java/id/shuyasal/kuromaku/` berisi **135 pengujian**, semuanya lulus:

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
- **ReaderLayoutTest** (7, Robolectric) — geometri panel navigasi, tinggi area baca, progres.
- **FeedPaginationTest** (11) — halaman pendek tidak menghentikan crawl, Volume 1–7 bertahan
  pada ukuran halaman tidak beraturan (25/22/25/20/12/13), dedupe, peringatan crawl tidak lengkap.
- **ChapterParserTest** (10) — teks & ilustrasi tidak hilang, urutan terjaga, iklan dibuang.
- **ScrollMathTest** (6) — progres, restore posisi, padding footer.
- **LibraryBuilderTest** (5) — pengelompokan volume, urutan numerik, label ringkas.

```bash
gradle testDebugUnitTest      # 135 tes
```

Mutation test untuk 2.5 (dibuktikan, bukan diklaim): mengembalikan bug lama membuat
tes benar-benar gagal, lalu kode dipulihkan dan seluruh suite hijau kembali.

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
