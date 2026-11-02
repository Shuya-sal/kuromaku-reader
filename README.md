# Kuromaku Reader 2.2

Aplikasi pembaca Android **native** untuk novel *Monogatari no Kuromaku* di label
[Kaori Translation](https://kaoritranslation.blogspot.com/search/label/Monogatari%20no%20Kuromaku).

Versi 1.0 hanya membungkus website di dalam WebView, sehingga iklan, sidebar, dan
menu Blogspot ikut tampil. Versi 2.0 membuang WebView sepenuhnya: isi chapter
diambil lewat HTTP, diparse dengan Jsoup, lalu dirender sebagai tampilan baca native.
Versi 2.1 memperbaiki katalog yang hanya memuat Volume 5–7. Versi 2.2 memperbaiki
**akar** bug scroll yang masih tersisa, dan mendesain ulang halaman awal menjadi
halaman detail novel.

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

## Pengujian

`app/src/test/java/id/shuyasal/kuromaku/` berisi **57 pengujian**, semuanya lulus:

- **ReaderScrollRegressionTest** (8, Robolectric) — tidak ada view yang menutupi `RecyclerView`;
  panel error mulai `GONE`; area baca bisa tergulir; paragraf ke-300 tercapai; paragraf
  terakhir bersih dari panel navigasi; bookmark tidak tertimpa layout pass; persen hanya
  100% di akhir; tombol Berikutnya tetap berpindah chapter. Chapter uji berisi **300 paragraf
  + ilustrasi**.
- **LibraryPageTest** (10, Robolectric, layar kecil 320×640dp) — rasio sampul sesuai artwork
  (toleransi 0.02), placeholder berada di belakang gambar, judul & metadata tampil apa adanya,
  sinopsis mengembang lewat "Baca selengkapnya", volume tertutup menyembunyikan chapter,
  Volume 1 dan 2 keduanya terdaftar, tombol Lanjutkan Membaca memakai progres nyata,
  atribusi + tautan sumber tetap ada, tema gelap memakai struktur yang sama.
- **ReaderLayoutTest** (7, Robolectric) — geometri panel navigasi, tinggi area baca, progres.
- **FeedPaginationTest** (11) — halaman pendek tidak menghentikan crawl, Volume 1–7 bertahan
  pada ukuran halaman tidak beraturan (25/22/25/20/12/13), dedupe, peringatan crawl tidak lengkap.
- **ChapterParserTest** (10) — teks & ilustrasi tidak hilang, urutan terjaga, iklan dibuang.
- **ScrollMathTest** (6) — progres, restore posisi, padding footer.
- **LibraryBuilderTest** (5) — pengelompokan volume, urutan numerik, label ringkas.

```bash
gradle testDebugUnitTest      # 57 tes
```

Verifikasi terhadap feed asli (dijalankan manual saat pengembangan):
6 halaman, **117/117 postingan**, Volume 1–7 lengkap.

## Arsitektur

```
app/src/main/java/id/shuyasal/kuromaku/
├── MainActivity.java          # shell perpustakaan: bar, insets, muat katalog
├── ReaderActivity.java        # Reader Mode + panel pengaturan + insets
├── Session.java               # katalog in-memory untuk navigasi chapter
├── data/
│   ├── FeedEntry, Chapter, Volume, Catalog
│   ├── NovelInfo.java         # cover + metadata terverifikasi + sinopsis
│   └── LibraryBuilder.java    # parsing judul → volume, label, urutan baca
├── net/
│   ├── Http.java              # OkHttp, hanya dokumen teks
│   ├── FeedClient.java        # feed JSON label, paginasi penuh
│   └── FeedException.java     # kegagalan per halaman
├── parser/
│   ├── ChapterParser.java     # Jsoup: scrub situs → blok teks/gambar berurutan
│   ├── ChapterContent.java    # judul + daftar blok + URL sumber
│   └── ContentCache.java      # cache LRU 6 chapter
└── ui/
    ├── LibraryPage.java       # halaman detail novel + accordion volume
    ├── RatioFrameLayout.java  # kotak rasio tetap untuk sampul
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
