package id.shuyasal.kuromaku.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Dukungan kendali jarak jauh (remote televisi).
 *
 * <p>Di televisi tidak ada sentuhan: satu-satunya cara memilih adalah tombol
 * arah, dan satu-satunya cara menjalankan adalah tombol tengah (OK). Android
 * hanya mengirim tombol arah ke view yang bisa menerima fokus, jadi view yang
 * hanya punya {@code setOnClickListener} tidak akan pernah bisa dipilih — layar
 * terlihat normal tetapi tidak ada yang bisa dijalankan.
 *
 * <p>Kelas ini menyediakan dua hal:
 *
 * <ul>
 *   <li>{@link #focusable(View)} — membuat satu view bisa dipilih dan
 *       menampilkan sorotan saat dipilih.</li>
 *   <li>{@link #prepare(View)} — menyiapkan seluruh isi satu layar sekaligus,
 *       dipanggil sekali setelah tata letak selesai dibangun.</li>
 * </ul>
 *
 * <p>Sorotan memakai cincin berwarna aksen, bukan perubahan warna latar, supaya
 * tetap terlihat di atas gambar cover dan di semua tema.
 */
public final class TvFocus {

    /**
     * Garis tepi sorotan, dalam dp.
     *
     * <p>Cukup tebal agar terlihat dari jarak tonton, tetapi tidak sampai
     * menutupi isi kartu.
     */
    public static final int RING_DP = 3;

    private TvFocus() {
    }

    /**
     * Jadikan view bisa dipilih dengan tombol arah, lengkap dengan sorotan.
     *
     * <p>Aman dipanggil berkali-kali pada view yang sama.
     */
    public static void focusable(View view, Palette palette) {
        if (view == null) return;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            // Anak-anak view tidak boleh mencuri fokus: satu kartu adalah satu
            // tujuan, bukan kumpulan teks yang masing-masing bisa dipilih.
            group.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            group.setFocusable(true);
            group.setFocusableInTouchMode(false);
        } else {
            view.setFocusable(true);
            view.setFocusableInTouchMode(false);
        }
        if (!(view instanceof Button) && !(view instanceof ImageButton)) {
            // Button bawaan sudah punya sorotan sendiri.
            view.setBackground(focusAware(view.getContext(), view.getBackground(),
                    palette, cornerOf(view)));
        }
        view.setSoundEffectsEnabled(true);
    }

    /**
     * Siapkan seluruh isi layar.
     *
     * <p>Semua view yang bisa diketuk dibuat bisa dipilih, dan sorotan dipasang
     * dengan warna tema yang sedang aktif. Dipanggil setelah pohon view selesai
     * dibangun; aman dipanggil lagi setelah daftar diperbarui.
     */
    public static void prepare(View root, Palette palette) {
        if (root == null || palette == null) return;
        walk(root, palette, 0);
    }

    private static void walk(View view, Palette palette, int depth) {
        // Batas kedalaman mencegah rekursi tak berujung pada pohon yang rusak.
        if (depth > 60) return;
        if (reactsToClick(view)) focusable(view, palette);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                walk(group.getChildAt(index), palette, depth + 1);
            }
        }
    }

    /** View ini menjalankan sesuatu bila diketuk? */
    private static boolean reactsToClick(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE) return false;
        if (view instanceof Button || view instanceof ImageButton) return true;
        if (view.isClickable()) return true;
        // RecyclerView dan ScrollView tidak dipilih; isinya yang dipilih.
        return view instanceof TextView && view.isClickable();
    }

    /** Perkiraan sudut kartu, supaya sorotan mengikuti bentuknya. */
    private static int cornerOf(View view) {
        int height = view.getHeight();
        if (height > 0 && height < Ui.dp(view.getContext(), 56)) return 10;
        return 14;
    }

    /**
     * Bungkus latar yang ada dengan cincin sorotan.
     *
     * <p>Latar asli dipertahankan — termasuk ripple saat disentuh di ponsel —
     * dan cincin hanya muncul saat view dipilih.
     */
    public static Drawable focusAware(Context context, Drawable content, Palette palette,
                                      int radiusDp) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},
                withRing(context, content, palette.accent, radiusDp));
        // Sebagian perangkat mengirim state "selected" untuk item daftar.
        states.addState(new int[]{android.R.attr.state_selected},
                withRing(context, content, palette.accent, radiusDp));
        states.addState(new int[]{}, content);
        return states;
    }

    private static Drawable withRing(Context context, Drawable content, int accent,
                                     int radiusDp) {
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.RECTANGLE);
        ring.setCornerRadius(Ui.dp(context, radiusDp));
        ring.setColor(0x00000000);
        ring.setStroke(Ui.dp(context, RING_DP), accent);

        if (content == null) return ring;
        LayerDrawable layered = new LayerDrawable(new Drawable[]{content, ring});
        // Cincin digambar sedikit di luar tepi isi agar tidak menutupi teks.
        int inset = Ui.dp(context, 1);
        layered.setLayerInset(1, -inset, -inset, -inset, -inset);
        return layered;
    }

    /**
     * Warnai sorotan pada view yang latarnya tidak boleh dibungkus.
     *
     * <p>Dipakai untuk view yang latarnya diganti sendiri oleh kode lain (mis.
     * cover yang memuat gambar): sorotan dipasang sebagai garis tepi.
     */
    public static void outline(View view, Palette palette) {
        if (view == null) return;
        if (view instanceof ImageView) {
            view.setBackground(focusAware(view.getContext(), view.getBackground(),
                    palette, 10));
        }
    }

    /** Apakah perangkat ini memakai kendali remote (bukan sentuh). */
    public static boolean usesRemote(Context context) {
        return TvMode.isTelevision(context);
    }
}
