package id.shuyasal.kuromaku.shinigami;

/**
 * Menghitung ketukan berurutan untuk membedakan satu, dua, dan tiga ketukan.
 *
 * <p>Logika dipisah dari View supaya bisa diuji tanpa perangkat: satu ketukan
 * harus menunggu jendela waktu habis dulu, sedangkan tiga ketukan cepat tidak
 * boleh diperlakukan sebagai dua ketukan.
 */
public final class ShinigamiTaps {

    /** Jeda maksimum antar-ketukan agar masih dianggap satu rangkaian. */
    public static final long WINDOW_MS = 280;

    private int taps;
    private long last;

    /** Catat satu ketukan pada waktu {@code now} (milidetik). */
    public int register(long now) {
        if (taps > 0 && now - last > WINDOW_MS) taps = 0;
        taps++;
        last = now;
        return taps;
    }

    /** Ketukan yang sudah tercatat tetapi belum diselesaikan. */
    public int pending() {
        return taps;
    }

    /** Ambil hasil akhir rangkaian dan mulai dari nol. */
    public int resolve() {
        int result = taps;
        taps = 0;
        return result;
    }

    public void reset() {
        taps = 0;
        last = 0;
    }
}
