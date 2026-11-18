2.73.0 - Manga Server 2 kembali memuat katalog dan chapter

Sumber Manga Server 2 berpindah alamat: alamat API lama (api.shngm.io) tidak
lagi menjawab, dan situs barunya ada di s1.shinigami.mx dengan API
api.shinigami.io. Versi ini memindahkan aplikasi ke alamat baru itu.

Yang berubah
- Alamat sumber: api.shinigami.io, situs rujukan s1.shinigami.mx, gambar dari
  cdn.shinigami.gg. Alamat lama tetap diterima, jadi konfigurasi lama tidak
  membuat aplikasi berhenti.
- Penomoran halaman katalog: sumber baru memakai offset/limit dan penanda
  has_next, bukan total halaman. "Muat lagi" berhenti saat sumber menyatakan
  tidak ada halaman berikutnya; tidak ada nomor halaman yang dikarang.
- Daftar chapter: sumber baru memakai cursor berantai. Aplikasi mengikuti
  rantai itu apa adanya, jadi halaman kedua dan seterusnya tidak menggandakan
  atau melewatkan chapter.
- Tab "Manga Terbaru" memakai urutan new_manga dari sumber (judul yang paling
  baru ditambahkan lebih dulu), bukan lagi perkiraan dari urutan populer.
- Daftar genre memakai alamat baru /taxonomy/list (38 genre).
- Jumlah chapter tidak lagi dikarang saat sumber tidak mengirimkannya; yang
  tampil adalah jumlah chapter yang benar-benar sudah dimuat, dan halaman
  berikutnya tetap bisa dimuat.

Yang tidak diubah
- applicationId, kunci penandatanganan, ID konten, dan seluruh data pengguna.
- Novel, Manga Server 1, Manga Server 3, Anime Server 1, dan Anime Server 2.
- Unduhan, riwayat, serta halaman yang sudah tersimpan offline tetap terbaca.

Pengujian
- Seluruh unit test Android dijalankan.
- Pemeriksaan langsung ke sumber baru: katalog 24 judul, pencarian, detail,
  daftar 936 chapter (dua halaman cursor), 10 gambar chapter dari
  cdn.shinigami.gg, dan 38 genre semuanya terbaca.
