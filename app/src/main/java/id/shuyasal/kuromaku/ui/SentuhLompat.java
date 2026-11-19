package id.shuyasal.kuromaku.ui;

import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

/**
 * Ketukan ganda di sisi kiri/kanan permukaan video untuk melompat 10 detik.
 *
 * <p>Dibuat sebagai kelas sendiri karena dua alasan: dua pemutar berbeda
 * (Otakudesu dan Animekompi) harus berperilaku persis sama, dan perhitungan
 * posisi harus bisa diuji tanpa video sungguhan — Robolectric tidak punya codec,
 * jadi menguji lewat pemutaran nyata tidak mungkin.
 *
 * <p>Ketukan tunggal sengaja ditunda sampai sistem memastikan tidak ada ketukan
 * kedua ({@code onSingleTapConfirmed}). Tanpa penundaan itu, setiap ketukan
 * ganda akan lebih dulu memunculkan bilah kontrol, lalu melompat — bilah
 * berkedip setiap kali pengguna ingin melompat.
 */
public final class SentuhLompat {

    /** Jarak lompatan satu ketukan ganda. */
    public static final long LOMPAT_MS = 10_000L;

    /** Sisi layar tempat ketukan terjadi: -1 kiri (mundur), +1 kanan (maju). */
    public int sisi(float x, int lebar) {
        if (lebar <= 0) return 1;
        return x < lebar / 2f ? -1 : 1;
    }

    /**
     * Posisi tujuan setelah melompat, sudah dijepit ke rentang video.
     *
     * <p>Batas atas dikurangi sedikit supaya tidak langsung menyentuh akhir
     * berkas: beberapa penyedia menganggap posisi == durasi sebagai "selesai"
     * lalu menutup pemutaran.
     */
    public long posisiBaru(long kini, long durasi, int sisi) {
        long tujuan = kini + (sisi < 0 ? -LOMPAT_MS : LOMPAT_MS);
        if (tujuan < 0) tujuan = 0;
        if (durasi > 0 && tujuan > durasi) tujuan = Math.max(0, durasi - 300L);
        return tujuan;
    }

    /** Umpan balik yang ditampilkan setelah melompat. */
    public String label(int sisi, long tujuan) {
        String tanda = sisi < 0 ? "−" : "+";
        return tanda + (LOMPAT_MS / 1000) + " detik · " + jam(tujuan);
    }

    public static String jam(long ms) {
        long total = Math.max(0, ms) / 1000L;
        long menit = total / 60L;
        long detik = total % 60L;
        return menit + ":" + (detik < 10 ? "0" : "") + detik;
    }

    /** Penerima perintah lompat; dijalankan di thread UI. */
    public interface Pendengar {
        /** @param sisi -1 mundur, +1 maju */
        void lompat(int sisi);
    }

    /**
     * Pasang pendeteksi ketukan pada sebuah view.
     *
     * <p>Touch listener mengembalikan {@code true} supaya ketukan tidak diteruskan
     * ke pendengar klik bawaan — kalau tidak, satu ketukan ganda akan menjalankan
     * klik dua kali sekaligus melompat. Pendengar klik tetap dipasang pemanggil
     * untuk jalur remote televisi, yang tidak menghasilkan kejadian sentuh.
     */
    public void pasang(View target, Runnable satuKetuk, Pendengar pendengar) {
        if (target == null) return;
        final GestureDetector detektor = new GestureDetector(target.getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override public boolean onDown(MotionEvent e) {
                        return true;
                    }

                    @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                        if (satuKetuk != null) satuKetuk.run();
                        return true;
                    }

                    @Override public boolean onDoubleTap(MotionEvent e) {
                        if (pendengar == null) return true;
                        pendengar.lompat(sisi(e.getX(), target.getWidth()));
                        return true;
                    }
                });
        detektor.setIsLongpressEnabled(false);
        target.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent event) {
                detektor.onTouchEvent(event);
                // Dikonsumsi seluruhnya: klik bawaan tidak boleh ikut menyala.
                return true;
            }
        });
    }
}
