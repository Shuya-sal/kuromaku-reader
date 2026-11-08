package id.shuyasal.kuromaku.anime;

/**
 * Satu sumber keadaan untuk header beranda Anime Streaming.
 *
 * <p>Masalah yang ditutup: sebelumnya setiap event {@code onScrolled} langsung
 * memanggil tampil/sembunyi, tanpa ambang dan tanpa menunggu animasi selesai.
 * Fling menghasilkan {@code dy} yang berganti tanda berkali-kali, sehingga
 * header berkedip, berosilasi saat jari berhenti, dan setiap perubahan tinggi
 * memicu tata ulang daftar yang membuat posisi gulir melompat.
 *
 * <p>Kelas ini hanya memutuskan <b>kapan</b> keadaan berubah; tidak menyentuh
 * view. Ambang dibuat asimetris (sembunyi lebih sulit daripada tampil) supaya
 * gerakan kecil di sekitar batas tidak membalik keadaan bolak-balik.
 */
final class HomeHeaderController {

    /** Jarak gulir ke bawah yang diperlukan sebelum header disembunyikan. */
    static final int HIDE_THRESHOLD = 24;

    /** Jarak gulir ke atas yang diperlukan sebelum header dimunculkan lagi. */
    static final int SHOW_THRESHOLD = 12;

    /** Penerima perubahan keadaan. */
    interface Sink {
        void onHiddenChanged(boolean hidden);
    }

    private boolean hidden;
    private int accumulatedDown;
    private int accumulatedUp;

    boolean isHidden() {
        return hidden;
    }

    /** Lupakan akumulasi; dipakai saat tab/katalog diganti. */
    void reset() {
        accumulatedDown = 0;
        accumulatedUp = 0;
    }

    /**
     * Proses satu langkah gulir.
     *
     * @param dy    jarak vertikal (positif = ke bawah)
     * @param atTop benar bila daftar sudah tidak bisa digulir ke atas lagi
     */
    void onScrolled(int dy, boolean atTop, Sink sink) {
        if (dy == 0) return;

        if (dy > 0) {
            accumulatedDown += dy;
            accumulatedUp = 0;
        } else {
            accumulatedUp -= dy;
            accumulatedDown = 0;
        }

        if (!hidden && accumulatedDown >= HIDE_THRESHOLD) {
            hidden = true;
            accumulatedDown = 0;
            accumulatedUp = 0;
            sink.onHiddenChanged(true);
            return;
        }
        if (hidden && accumulatedUp >= SHOW_THRESHOLD) {
            hidden = false;
            accumulatedDown = 0;
            accumulatedUp = 0;
            sink.onHiddenChanged(false);
            return;
        }
        // Di paling atas tidak ada alasan menyembunyikan header: pengguna sedang
        // melihat awal daftar, dan header adalah bagian dari konteks itu.
        if (hidden && atTop) {
            hidden = false;
            accumulatedDown = 0;
            accumulatedUp = 0;
            sink.onHiddenChanged(false);
        }
    }
}