# Kuromaku Reader

Pembaca novel ringan, manga, dan penonton anime untuk Android ponsel dan Android TV.
Seluruh antarmuka berbahasa Indonesia.

APK rilis: [latest-apk](https://github.com/Shuya-sal/kuromaku-reader/releases/tag/latest-apk)

## Sumber

Aplikasi menyusun isi dari beberapa sumber pihak ketiga. Sumber hanya dirujuk lewat
label di dalam aplikasi, bukan nama mereknya:

| Bagian | Label di aplikasi |
|---|---|
| Novel | Novel |
| Manga | Manga Server 1, Manga Server 2, Manga Server 3 |
| Anime | Anime Streaming, Anime Server 2 |

Aplikasi tidak membundel gambar bermerek milik sumber, tidak menyimpan kredensial, dan
tidak melewati autentikasi, CAPTCHA, atau pembatasan lain milik sumber. Bila sebuah
sumber tidak menyediakan berkas video, aplikasi menyebutnya sebagai "halaman pemutar"
dan tidak menampilkannya seolah berhasil diputar.

## Fitur

**Novel** - katalog, detail, daftar volume dan chapter, reader dengan tema terang/gelap,
progres baca, riwayat, favorit, serta unduhan offline.

**Manga** - tiga sumber terpisah dengan katalog, pencarian, detail, dan reader vertikal:

- Manga Server 1 - berbasis halaman web di dalam aplikasi.
- Manga Server 2 - katalog dan reader native, pemuatan gambar bertahap, zoom, mode layar penuh.
- Manga Server 3 - katalog penuh (ribuan judul), pengambilan berjeda agar sumber tidak
  terbebani, dan dapat dilanjutkan dari halaman terakhir.

**Anime** - dua sumber:

- Anime Streaming - kualitas dan server dipilih per episode.
- Anime Server 2 - beranda per bagian (episode terbaru, populer, sedang tayang, film),
  pencarian, dan pemutar sendiri.

Penandaan server jujur: hanya server yang benar-benar memberikan berkas video yang diputar.
Server yang hanya menyediakan halaman pemutar ditandai demikian, dengan tombol membuka
halaman sumber.

**Android TV** - navigasi remote (fokus, kiri/kanan/atas/bawah, OK, kembali), layar penuh
sejak awal, kontrol yang hilang sendiri saat video berjalan dan muncul lagi pada setiap
tekanan tombol, serta tombol kembali dua langkah (sembunyikan kontrol, lalu keluar).

**Unduhan offline** - chapter manga disimpan sebagai gambar, episode anime sebagai berkas
video, dan novel sebagai teks. Unduhan dapat diputar tanpa jaringan dan dibatalkan per
pekerjaan tanpa menghapus unduhan lain.

**Backup dan restore** - progres baca, riwayat, favorit, dan pengaturan. Format backup
memakai versi agar berkas lama tetap dapat dipulihkan.

## Membangun

```
gradle testDebugUnitTest      # suite unit (Robolectric)
gradle assembleDebug          # APK debug
```

Kebutuhan: JDK 17, Android SDK 35, Gradle 8.9.

## Ketentuan

Aplikasi ini alat pembaca, bukan penyedia konten. Hak atas materi yang dibaca tetap milik
sumber dan pemegang haknya. Gunakan sesuai ketentuan layanan masing-masing sumber.
