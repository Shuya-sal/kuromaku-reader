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
    /**
     * Apakah layar ini televisi.
     *
     * <p>Dipakai sebagai penjaga tunggal: di ponsel tidak ada satu pun perilaku
     * televisi yang boleh aktif, karena kartu di ponsel sudah bisa diketuk dan
     * memblokir fokus anak-anaknya akan mengubah perilaku yang sudah ada.
     */
    private static boolean tv(View view) {
        return view != null && TvMode.isTelevision(view.getContext());
    }

    public static void focusable(View view, Palette palette) {
        if (!tv(view)) return;
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
        if (!tv(root) || palette == null) return;
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

    /**
     * Pasang sorotan tahan lama pada satu view.
     *
     * <p>Daftar mengganti latar itemnya sendiri saat diikat ulang (warna
     * berganti mengikuti tema), dan itu menghapus sorotan yang sudah dipasang.
     * Karena itu latar asli diingat, dan sorotan dibangun ulang dari latar
     * terbaru setiap kali view kehilangan latar bersorot.
     *
     * <p>Dipanggil dari {@code onBindViewHolder}: setiap kali item dipakai ulang,
     * sorotan dipasang lagi tanpa mengubah tampilan latarnya.
     */
    public static void keepRing(View view, Palette palette, int radiusDp) {
        if (!tv(view) || palette == null) return;
        view.setFocusable(true);
        view.setFocusableInTouchMode(false);
        if (view instanceof ViewGroup) {
            ((ViewGroup) view).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        // Jangan bungkus ulang bila latar ini memang sudah bersorot.
        Drawable current = view.getBackground();
        if (current instanceof RingBackground) return;
        int radius = view.getTag(R_ID_RADIUS) instanceof Integer
                ? (Integer) view.getTag(R_ID_RADIUS) : radiusDp;
        view.setTag(R_ID_RADIUS, radius);
        RingBackground ring = new RingBackground(current, palette.accent, radius,
                view.getContext());
        ring.attach(view);
        view.setBackground(ring);
    }

    /** Kunci tag untuk menyimpan sudut kartu. */
    private static final int R_ID_RADIUS = "kuromaku_tv_radius".hashCode();

    /**
     * Latar yang menggambar cincin sorotan tanpa mengubah latar aslinya.
     *
     * <p>Berbeda dari {@link StateListDrawable}: state dibaca dari view pemilik,
     * jadi sorotan tetap bekerja walau latar diganti berkali-kali dan tidak
     * bergantung pada pembungkusan ulang.
     */
    private static final class RingBackground extends android.graphics.drawable.Drawable
            implements android.graphics.drawable.Drawable.Callback {
        private final Drawable content;
        private final int accent;
        private final int radiusDp;
        private final Context context;
        private android.graphics.Paint ring;
        private View owner;

        RingBackground(Drawable content, int accent, int radiusDp, Context context) {
            this.content = content;
            this.accent = accent;
            this.radiusDp = radiusDp;
            this.context = context;
            if (content != null) content.setCallback(this);
        }

        /** Pemilik dipakai untuk membaca state fokus. */
        void attach(View view) {
            this.owner = view;
        }

        @Override public void draw(android.graphics.Canvas canvas) {
            if (content != null) content.draw(canvas);
            if (owner == null || !owner.isFocused()) return;
            if (ring == null) {
                ring = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                ring.setStyle(android.graphics.Paint.Style.STROKE);
                ring.setColor(accent);
                ring.setStrokeWidth(Ui.dp(context, RING_DP));
            }
            android.graphics.Rect bounds = getBounds();
            float inset = ring.getStrokeWidth() / 2f;
            canvas.drawRoundRect(new android.graphics.RectF(
                            bounds.left + inset, bounds.top + inset,
                            bounds.right - inset, bounds.bottom - inset),
                    Ui.dp(context, radiusDp), Ui.dp(context, radiusDp), ring);
        }

        @Override public void setAlpha(int alpha) {
            if (content != null) content.setAlpha(alpha);
        }

        @Override public void setColorFilter(android.graphics.ColorFilter filter) {
            if (content != null) content.setColorFilter(filter);
        }

        @Override public int getOpacity() {
            return content == null ? android.graphics.PixelFormat.TRANSLUCENT
                    : content.getOpacity();
        }

        @Override public boolean isStateful() {
            return true;
        }

        @Override protected boolean onStateChange(int[] state) {
            invalidateSelf();
            return true;
        }

        @Override public void invalidateDrawable(Drawable who) {
            invalidateSelf();
        }

        @Override public void scheduleDrawable(Drawable who, Runnable what, long when) {
            if (owner != null) owner.postDelayed(what, Math.max(0, when
                    - android.os.SystemClock.uptimeMillis()));
        }

        @Override public void unscheduleDrawable(Drawable who, Runnable what) {
            if (owner != null) owner.removeCallbacks(what);
        }
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
