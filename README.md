# Kuromaku Reader 2.1

Aplikasi pembaca Android **native** untuk novel *Monogatari no Kuromaku* di label
[Kaori Translation](https://kaoritranslation.blogspot.com/search/label/Monogatari%20no%20Kuromaku).

Versi 1.0 hanya membungkus website di dalam WebView, sehingga iklan, sidebar, dan
menu Blogspot ikut tampil. Versi 2.0 membuang WebView sepenuhnya: isi chapter
diambil lewat HTTP, diparse dengan Jsoup, lalu dirender sebagai tampilan baca native.
Versi 2.1 memperbaiki dua bug: katalog yang hanya memuat Volume 5–7, dan teks
chapter panjang yang tidak bisa digulir sampai akhir.

## Fitur

**Perpustakaan**
- Daftar volume dan chapter dibangun dari feed label resmi Blogspot (JSON), bukan dari halaman HTML penuh.
- **Semua halaman feed dibaca** sampai Blogger mengembalikan halaman kosong, bukan berhenti di halaman pendek (lihat bagian *Perbaikan bug*).
- Pengelompokan otomatis per volume, urutan baca alami: Prolog → Chapter 1..N → Interlude → Short/Side Story → Epilog → Afterword → Illustrasi.
- Volume diurutkan menaik (Volume 1 di atas), chapter diurutkan numerik (Chapter 2 sebelum Chapter 10).
- Penanda progres per chapter (`42%`, `selesai`) dan tombol "Lanjutkan membaca".
- Tombol **Muat ulang** di bar atas untuk mengambil katalog terbaru.

**Reader Mode**
- Ekstraksi hanya isi artikel: judul, heading, paragraf, kutipan, daftar, dan ilustrasi.
- Urutan paragraf dan gambar dipertahankan; URL gambar asli dipakai (varian `=s1600` agar tidak buram).
- Script, `<ins class="adsbygoogle">`, iframe, share widget, komentar, sidebar, dan footer situs dibuang sebelum render.
- Tidak ada JavaScript yang dijalankan di aplikasi — konten diolah sebagai teks, bukan halaman web.

**Kenyamanan baca**
- Tema Terang / Gelap / Sepia.
- Ukuran huruf, spasi baris, dan rata kanan-kiri yang bisa diatur.
- Progres membaca disimpan per chapter **beserta posisi blok dan offset piksel**, sehingga kembali ke chapter mengembalikan tempat terakhir, bukan perkiraan persen.
- Tombol Sebelumnya / Berikutnya / Kembali ke perpustakaan, plus nomor posisi `12/117` dan persen berjalan.
- Panel navigasi berada **di bawah** area baca dalam layout vertikal, bukan overlay di atas teks.

**Penanganan kegagalan**
- Jika kontainer artikel tidak ditemukan atau isinya kosong, layar menampilkan alasan kegagalan,
  tombol "Coba lagi", tautan sumber yang bisa disalin, dan tombol buka browser.
- Kegagalan feed menyebut halaman dan `start-index` yang gagal, bukan pesan generik.
- Crawl yang tidak lengkap tetap menampilkan daftar, dengan peringatan "Hanya X dari Y postingan".
- Aplikasi **tidak** pernah diam-diam beralih menampilkan website penuh.

## Perbaikan bug di 2.1

### BUG 1 — Volume 1–4 tidak muncul

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

`LibraryBuilder` juga diperbaiki: label kini diambil dari kata kunci (Prolog, Chapter N,
Short Story N, Side Story N, Interlude, Bonus, Epilog, Afterword, Illustrasi), sehingga
judul seperti `... Illustrasi Volume 3` tidak lagi berlabel "Volume 3", dan chapter
kembar di-dedupe berdasarkan volume + label.

### BUG 2 — Scroll tidak sampai akhir

Penyebab: `targetSdk 35` memaksa edge-to-edge, tetapi window belum menangani insets.
Panel navigasi dan gesture bar menutupi bagian bawah teks, dan tidak ada padding
bawah cadangan pada blok terakhir.

Perbaikan di `ReaderActivity`:
- `WindowCompat.setDecorFitsSystemWindows(false)` + `WindowInsetsCompat` diterapkan ke bar atas/bawah,
  sehingga tinggi area baca dihitung setelah status bar dan navigation bar.
- Area baca memakai `layout_weight=1` dan daftar memakai `clipToPadding=false`, jadi
  paragraf terakhir bisa melewati panel.
- Footer adapter menambah padding bawah `40dp + inset bawah` (`ScrollMath.footerPadding`).
- Posisi presisi per blok disimpan (`Prefs.setScroll`) dan dipulihkan dengan
  `scrollToPositionWithOffset`, jadi tema/font berubah tidak melempar pembaca ke atas.
- `ScrollMath.percent` hanya melaporkan 100% ketika blok terakhir benar-benar terlihat.

## Arsitektur

```
app/src/main/java/id/shuyasal/kuromaku/
├── MainActivity.java          # layar perpustakaan (native, tanpa WebView)
├── ReaderActivity.java        # Reader Mode + panel pengaturan + insets
├── Session.java               # katalog in-memory untuk navigasi chapter
├── data/
│   ├── FeedEntry, Chapter, Volume, Catalog
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
    ├── BlockAdapter.java      # render blok jadi view native
    ├── ScrollMath.java        # progres, restore posisi, padding footer
    ├── Palette.java, Prefs.java, Ui.java
```

Kontainer artikel yang dikenali: `div.post-body.entry-content`, lalu `div.post-body`,
`div.entry-content`, dan `article` sebagai cadangan.

## Pengujian

`app/src/test/java/id/shuyasal/kuromaku/` berisi 39 pengujian:

- **FeedPaginationTest** (11) — halaman pendek tidak menghentikan crawl, Volume 1–7 bertahan
  pada ukuran halaman tidak beraturan (25/22/25/20/12/13), duplikat dibuang, crawl tidak
  lengkap memberi peringatan, kegagalan halaman menyebut `start-index`, JSON rusak dan feed
  kosong dilaporkan jelas, urutan numerik dan label tiap volume benar.
- **ReaderLayoutTest** (7, Robolectric) — mengukur layout nyata 411×891dp: area baca punya
  tinggi nyata, panel navigasi **tidak menimpa** teks (`barPos.y >= listBottom`), blok
  terakhir bisa dicapai, footer punya padding bawah, progres 100% hanya di akhir,
  tombol Berikutnya benar-benar berpindah chapter.
- **ScrollMathTest** (6) — progres tidak pernah 100% sebelum blok terakhir, restore posisi
  dalam batas, padding footer memperhitungkan inset.
- **ChapterParserTest** (10) — teks & ilustrasi tidak hilang, urutan terjaga, iklan dibuang,
  `data-src` menang atas placeholder, `=s320` → `=s1600`, `file://`/`data:` diabaikan.
- **LibraryBuilderTest** (5) — pengelompokan volume, urutan numerik, label ringkas, lookup navigasi.

```bash
gradle testDebugUnitTest      # 39 tes
```

Verifikasi terhadap feed asli (dijalankan manual saat pengembangan):
6 halaman, **117/117 postingan**, Volume 1–7 lengkap, `isComplete() == true`.

## Build

GitHub Actions: **Actions → Build Kuromaku Reader APK** (`.github/workflows/android.yml`).
Alur: unit test → `assembleDebug` → verifikasi APK dengan `aapt2` → artifact → rilis `latest-apk`.

Lokal (JDK 17, Android SDK 35, Gradle 8.9):

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
gradle testDebugUnitTest assembleDebug
```

Ikon launcher dibuat dari artwork pribadi lewat `python3 tools/make_icons.py`
(membutuhkan Pillow; menghasilkan `mipmap-*` dan adaptive icon API 26+).

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
