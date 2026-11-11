package id.shuyasal.kuromaku.ui;

import android.view.KeyEvent;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Kendali tombol remote untuk layar baca.
 *
 * <p>Layar baca berbeda dari daftar biasa: isinya gambar atau teks yang tidak
 * bisa difokus satu per satu. Di televisi, tombol arah atas/bawah harus
 * menggulir isi halaman, bukan memindahkan sorotan ke balok teks berikutnya —
 * kalau sorotan yang berpindah, gambar tidak ikut tergulir dan halaman terasa
 * macet.
 *
 * <p>Kelas ini menerjemahkan tombol remote menjadi guliran:
 *
 * <ul>
 *   <li>atas/bawah: gulir satu tinggi layar (dikurangi sebagian agar ada
 *       sambungan yang terlihat)</li>
 *   <li>kiri/kanan: pindah halaman atau chapter, diserahkan ke pemanggil</li>
 *   <li>tombol media (putar/jeda, maju, mundur): sama dengan kiri/kanan</li>
 * </ul>
 */
public final class TvRemote {

    /**
     * Bagian tinggi layar yang digulir sekali tekan.
     *
     * <p>Bukan satu layar penuh: sisa yang terlihat membuat pembaca tahu di mana
     * ia berhenti, seperti menggulir dengan jari.
     */
    public static final float SCROLL_FRACTION = 0.82f;

    private TvRemote() {
    }

    /** Arah yang diminta tombol, atau {@code null} bila tombol itu bukan kendali baca. */
    public enum Action {
        SCROLL_UP, SCROLL_DOWN, PREVIOUS, NEXT
    }

    /** Terjemahkan tombol remote menjadi aksi baca. */
    public static Action actionOf(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return Action.SCROLL_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return Action.SCROLL_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                return Action.PREVIOUS;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                return Action.NEXT;
            default:
                return null;
        }
    }

    /**
     * Gulir wadah baca sejauh satu bagian layar.
     *
     * @return true bila wadah benar-benar tergulir; false bila sudah di ujung,
     *         sehingga pemanggil bisa memakai tombol itu untuk hal lain
     *         (mis. pindah chapter)
     */
    public static boolean scroll(View content, boolean down) {
        if (content == null) return false;
        int distance = content.getHeight();
        if (distance <= 0) {
            distance = content.getResources().getDisplayMetrics().heightPixels;
        }
        int step = Math.max(1, Math.round(distance * SCROLL_FRACTION));

        if (content instanceof RecyclerView) {
            RecyclerView list = (RecyclerView) content;
            RecyclerView.LayoutManager manager = list.getLayoutManager();
            if (!(manager instanceof androidx.recyclerview.widget.LinearLayoutManager)) {
                return false;
            }
            androidx.recyclerview.widget.LinearLayoutManager linear =
                    (androidx.recyclerview.widget.LinearLayoutManager) manager;
            if (linear.getOrientation() != RecyclerView.VERTICAL) return false;
            // Dipakai posisi item pertama sebagai penanda: cara ini bekerja
            // untuk daftar bergulir ke bawah maupun ke atas, dan tidak
            // bergantung pada API internal layout manager.
            int before = linear.findFirstVisibleItemPosition();
            View anchor = linear.findViewByPosition(before);
            int offsetBefore = anchor == null ? 0 : anchor.getTop();
            list.scrollBy(0, down ? step : -step);
            int after = linear.findFirstVisibleItemPosition();
            View anchorAfter = linear.findViewByPosition(after);
            int offsetAfter = anchorAfter == null ? 0 : anchorAfter.getTop();
            return before != after || offsetBefore != offsetAfter;
        }
        int moved = content.canScrollVertically(down ? 1 : -1) ? step : 0;
        if (moved == 0) return false;
        content.scrollBy(0, down ? moved : -moved);
        return true;
    }

    /** Apakah isi ini masih bisa digulir ke arah tersebut. */
    public static boolean canScroll(View content, boolean down) {
        if (content == null) return false;
        return content.canScrollVertically(down ? 1 : -1);
    }
}
