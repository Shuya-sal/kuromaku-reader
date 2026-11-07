package id.shuyasal.kuromaku.shinigami;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Satu halaman chapter Shinigami.
 *
 * <p>Halaman dirender sebagai potongan vertikal pada lebar native sumber. Versi
 * sebelumnya memakai satu bitmap dengan batas tinggi 4096 px, sehingga halaman
 * 800×10.000 didekode menjadi 327×4096 lalu ditarik ke lebar layar — itulah
 * sebab gambar terlihat pecah. Dengan pemotongan, lebar tidak pernah turun di
 * bawah resolusi sumber dan memori tetap terbatas pada beberapa potongan.
 */
public final class ShinigamiPageView extends FrameLayout {

    /**
     * Gestur ketukan di halaman.
     *
     * <p>Pembagiannya disengaja: <b>satu ketukan</b> untuk masuk/keluar layar
     * penuh, <b>dua ketukan</b> untuk zoom 2×, <b>tiga ketukan</b> untuk zoom 3×.
     *
     * <p>Ketukan tunggal baru dijalankan setelah {@link ShinigamiTaps#WINDOW_MS}
     * milik {@code onSingleTapConfirmed} habis tanpa ketukan lanjutan. Jadi
     * rangkaian yang ternyata dua atau tiga ketukan tidak pernah sempat
     * mengubah layar penuh — perilaku yang sama dengan yang diminta pada
     * {@code GestureDetector.onSingleTapConfirmed()} dan
     * {@code onDoubleTap()}: aksi ketukan tunggal harus dipastikan dulu.
     */
    public interface ZoomRequest {
        /**
         * @param factor 2 atau 3
         * @param focusY posisi jari secara vertikal, 0..1 dari tinggi halaman
         */
        void onZoom(int factor, float focusY);

        /**
         * Satu ketukan pada gambar.
         *
         * @param focusY posisi jari secara vertikal, 0..1 dari tinggi halaman
         */
        default void onSingleTap(float focusY) {
        }
    }

    private final Handler tapsHandler = new Handler(Looper.getMainLooper());
    private final ShinigamiTaps taps = new ShinigamiTaps();
    private ZoomRequest zoomRequest;
    private float tapX;
    private float tapY;
    private float downX;
    private float downY;
    private boolean tapMoved;
    /** Penjaga agar satu rangkaian sentuhan tidak dicatat dua kali. */
    private boolean downSeen;
    private final float tapSlop;
    /**
     * Menyelesaikan rangkaian ketukan setelah jendela konfirmasi habis.
     *
     * <p>Inilah padanan {@code onSingleTapConfirmed()}: satu ketukan dijalankan
     * di sini, bukan pada {@code ACTION_UP}, sehingga rangkaian yang ternyata
     * dua atau tiga ketukan tidak pernah memicu layar penuh.
     */
    private final Runnable resolveTaps = () -> {
        int count = taps.resolve();
        if (zoomRequest == null) return;
        float focus = Math.max(0f, Math.min(1f, tapY / Math.max(1, getHeight())));
        if (count >= 3) {
            zoomRequest.onZoom(3, focus);
        } else if (count == 2) {
            zoomRequest.onZoom(2, focus);
        } else if (count == 1) {
            zoomRequest.onSingleTap(focus);
        }
    };

    final ImageView image;
    private final TiledSurface surface;
    private final LinearLayout loading;
    private final LinearLayout errorBox;
    private final TextView error;
    private double ratio;
    private Runnable retryAction;

    public ShinigamiPageView(Context context) {
        super(context);
        tapSlop = 12f * getResources().getDisplayMetrics().density;
        setBackgroundColor(Color.BLACK);
        image = new ImageView(context);
        image.setTag("shinigami_page_image");
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(image, new LayoutParams(-1, -1));

        surface = new TiledSurface(context);
        surface.setTag("shinigami_page_surface");
        surface.setVisibility(GONE);
        surface.listener = reason -> showFailure(reason);
        addView(surface, new LayoutParams(-1, -1));

        loading = new LinearLayout(context);
        loading.setOrientation(LinearLayout.VERTICAL);
        loading.setGravity(Gravity.CENTER);
        loading.setTag("shinigami_page_loading");
        loading.addView(new ProgressBar(context));
        TextView message = new TextView(context);
        message.setText("Memuat gambar...");
        message.setTextColor(Color.WHITE);
        loading.addView(message);
        addView(loading, new LayoutParams(-1, -2, Gravity.CENTER));
        errorBox = new LinearLayout(context);
        errorBox.setOrientation(LinearLayout.VERTICAL);
        errorBox.setGravity(Gravity.CENTER);
        errorBox.setTag("shinigami_page_error");
        errorBox.setPadding(16, 16, 16, 16);
        error = new TextView(context);
        error.setTextColor(0xFFFF8080);
        error.setGravity(Gravity.CENTER);
        error.setTag("shinigami_page_error_detail");
        errorBox.addView(error);
        Button retry = new Button(context);
        retry.setText("Coba Lagi");
        retry.setTag("shinigami_page_retry");
        retry.setOnClickListener(v -> { if (retryAction != null) retryAction.run(); });
        errorBox.addView(retry);
        addView(errorBox, new LayoutParams(-1, -2, Gravity.CENTER));
        showLoading();
    }

    public void setRetryAction(Runnable action) { retryAction = action; }

    /** Daftarkan gestur ketukan: satu untuk layar penuh, dua/tiga untuk zoom. */
    public void setZoomRequest(ZoomRequest request) {
        zoomRequest = request;
    }

    /**
     * Aksi satu ketukan yang <b>sudah dikonfirmasi</b>: masuk/keluar layar penuh.
     *
     * <p>Ini jalur yang sama dengan {@code onSingleTapConfirmed()}. Gestur
     * sungguhan sampai ke sini lewat {@link #resolveTaps} setelah jendela
     * konfirmasi habis, sehingga rangkaian dua atau tiga ketukan tidak pernah
     * memicunya.
     */
    public void tapOnce(float focusY) {
        taps.reset();
        if (zoomRequest == null) return;
        zoomRequest.onSingleTap(Math.max(0f, Math.min(1f, focusY)));
    }

    /**
     * Menunggu jendela konfirmasi untuk gestur yang sedang berjalan.
     *
     * <p>Dipakai tes: setelah ketukan sungguhan masuk lewat
     * {@link #onInterceptTouchEvent}, tunggu selama ini untuk melihat apakah
     * rangkaiannya berakhir sebagai satu ketukan atau berlanjut.
     */
    public void settleTaps() {
        tapsHandler.removeCallbacks(resolveTaps);
        resolveTaps.run();
    }

    /**
     * Melacak ketukan pada halaman ini.
     *
     * <p>Dipanggil dari {@link #onInterceptTouchEvent} <b>dan</b> dari
     * {@link #onTouchEvent}. Android memanggil {@code onInterceptTouchEvent}
     * lebih dulu; begitu mengembalikan {@code true}, sisa rangkaian sentuhan
     * (termasuk {@code ACTION_UP}) dikirim ke {@link #onTouchEvent}. Keduanya
     * memanggil pelacak ini, jadi harus ada penjaga agar <b>satu</b> ketukan
     * tidak pernah tercatat dua kali: sebelumnya hal itu membuat satu ketukan
     * dihitung sebagai dua ketukan — layar penuh tidak pernah aktif, dan yang
     * muncul justru zoom 2×.
     *
     * <p>Penjaganya sederhana dan tidak bergantung pada urutan pemanggilan:
     * hanya {@code ACTION_UP} pada rangkaian yang <em>belum</em> ditutup yang
     * dicatat, dan setiap {@code ACTION_UP} menutup rangkaiannya.
     */
    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        trackTaps(event);
        // Halaman ini yang mengurus ketukan; tidak ada anak yang perlu
        // menerimanya, jadi seluruh rangkaian diurus di sini dan diteruskan ke
        // onTouchEvent oleh Android.
        return true;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        trackTaps(event);
        return true;
    }

    private void trackTaps(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downSeen = true;
                downX = event.getX();
                downY = event.getY();
                tapMoved = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(event.getX() - downX) > tapSlop
                        || Math.abs(event.getY() - downY) > tapSlop) tapMoved = true;
                break;
            case MotionEvent.ACTION_UP:
                // Penjaga: satu ACTION_UP hanya boleh tercatat sekali per
                // rangkaian, walau onInterceptTouchEvent dan onTouchEvent sama
                // sama memanggil pelacak ini.
                if (!downSeen) {
                    break;
                }
                downSeen = false;
                if (tapMoved) {
                    // Jari bergeser lebih dari ambang: ini gulir atau geser,
                    // bukan ketukan. Rangkaian ketukan dibatalkan supaya
                    // menggulir tidak pernah mengubah layar penuh.
                    taps.reset();
                    tapsHandler.removeCallbacks(resolveTaps);
                    break;
                }
                tapX = event.getX();
                tapY = event.getY();
                int registered = taps.register(android.os.SystemClock.uptimeMillis());
                if (registered >= 3) {
                    // Tiga ketukan sudah pasti: tidak ada yang perlu ditunggu.
                    tapsHandler.removeCallbacks(resolveTaps);
                    resolveTaps.run();
                    break;
                }
                // Satu dan dua ketukan menunggu jendela konfirmasi. Ketukan yang
                // ternyata berlanjut membatalkan aksi sebelumnya lewat
                // resolveTaps, sehingga aksi ketukan tunggal tidak pernah jalan
                // untuk rangkaian dua atau tiga ketukan.
                tapsHandler.removeCallbacks(resolveTaps);
                tapsHandler.postDelayed(resolveTaps, ShinigamiTaps.WINDOW_MS);
                break;
            case MotionEvent.ACTION_CANCEL:
                downSeen = false;
                taps.reset();
                tapsHandler.removeCallbacks(resolveTaps);
                break;
            default:
                break;
        }
    }

    /**
     * Rasio tinggi/lebar yang sudah diketahui untuk halaman ini.
     *
     * <p>Dipakai agar placeholder sudah setinggi gambar akhirnya, sehingga
     * posisi baca tidak bergeser ketika gambar selesai dimuat.
     */
    public void setPlaceholderRatio(double value) {
        if (value <= 0.2 || value > 20) return;
        if (Math.abs(ratio - value) < 0.005) return;
        ratio = value;
        requestLayout();
    }

    public double ratio() {
        return ratio;
    }

    /**
     * Pasang rasio halaman; kembalikan {@code true} bila tinggi ikut berubah.
     *
     * <p>Rebind pada halaman yang sama tidak boleh memicu {@code requestLayout()}
     * lagi — setiap layout ulang memaksa RecyclerView mengukur anak dan bisa
     * membuat gambar terlihat berkedip.
     */
    private boolean applyRatio(double value) {
        if (value <= 0.2 || value > 20) return false;
        if (Math.abs(ratio - value) < 0.005) return false;
        ratio = value;
        return true;
    }

    public void showLoading() {
        // Gambar yang sudah tampil TIDAK boleh dikosongkan. Sebelumnya setiap
        // rebind memanggil ini, menutup permukaan gambar lalu menampilkan
        // spinner — itulah area hitam + loading yang berkedip di video.
        if (hasContent()) {
            loading.setVisibility(GONE);
            errorBox.setVisibility(GONE);
            return;
        }
        // ratio TIDAK direset: placeholder mempertahankan tinggi yang sudah
        // diketahui supaya daftar tidak melompat saat halaman dimuat ulang.
        image.setImageDrawable(null);
        image.setVisibility(INVISIBLE);
        surface.setVisibility(GONE);
        loading.setVisibility(VISIBLE);
        errorBox.setVisibility(GONE);
        requestLayout();
    }

    /** True bila halaman ini sedang menampilkan gambar yang valid. */
    public boolean hasContent() {
        return surface.getVisibility() == VISIBLE && surface.hasContent();
    }

    /** Mode bitmap tunggal (dipakai tes dan pemanggil lama). */
    public void showReady(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            showFailure("Gambar tidak dapat didekode.");
            return;
        }
        ratio = (double) bitmap.getHeight() / bitmap.getWidth();
        image.setImageBitmap(bitmap);
        image.setVisibility(VISIBLE);
        surface.setVisibility(GONE);
        loading.setVisibility(GONE);
        errorBox.setVisibility(GONE);
        requestLayout();
    }

    /** Tampilkan berkas lokal dengan pemuatan ulang yang aman. */
    public void showLocalFile(File file, ShinigamiQuality quality) {
        int[] size = ShinigamiPageTiles.bounds(file);
        if (size == null) {
            showFailure("Gambar halaman tidak dapat dibaca.");
            return;
        }
        boolean resized = applyRatio((double) size[1] / size[0]);
        image.setImageDrawable(null);
        image.setVisibility(GONE);
        loading.setVisibility(GONE);
        errorBox.setVisibility(GONE);
        surface.show(file, size[0], size[1], quality == null ? ShinigamiQuality.AUTO : quality);
        surface.setVisibility(VISIBLE);
        if (resized) requestLayout();
    }

    /**
     * Tampilkan halaman dari berkas pada resolusi asli.
     *
     * @param file    berkas gambar (unduhan offline atau cache halaman)
     * @param quality lebar target; tidak pernah melebihi lebar sumber
     */
    public void showFile(File file, ShinigamiQuality quality) {
        int[] size = ShinigamiPageTiles.bounds(file);
        if (size == null) {
            showFailure("Gambar halaman tidak dapat dibaca.");
            return;
        }
        boolean resized = applyRatio((double) size[1] / size[0]);
        image.setImageDrawable(null);
        image.setVisibility(GONE);
        loading.setVisibility(GONE);
        errorBox.setVisibility(GONE);
        surface.show(file, size[0], size[1], quality == null ? ShinigamiQuality.AUTO : quality);
        surface.setVisibility(VISIBLE);
        requestLayout();
    }

    public void showFailure(String reason) {
        // Kesalahan pada halaman yang gambarnya sudah tampil tidak boleh
        // mengubah tampilan menjadi area kosong; cukup dicatat.
        if (hasContent()) {
            android.util.Log.w("ShinigamiReader", "kegagalan diabaikan (gambar valid): " + reason);
            return;
        }
        image.setVisibility(INVISIBLE);
        surface.setVisibility(GONE);
        loading.setVisibility(GONE);
        error.setText(reason);
        errorBox.setVisibility(VISIBLE);
        requestLayout();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        if (width <= 0) width = getResources().getDisplayMetrics().widthPixels;
        // Tanpa rasio yang diketahui, tinggi placeholder memakai rasio halaman
        // manga pada umumnya (bukan setinggi layar). Pergeseran saat gambar
        // akhirnya tampil menjadi jauh lebih kecil.
        double known = ratio > 0 ? ratio : 1.45;
        int height = Math.max(1, (int) Math.ceil(width * known));
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override protected void onDetachedFromWindow() {
        // Perbaikan gambar hitam: JANGAN bersihkan surface saat view hanya
        // sementara keluar dari layar (scroll). Decoder dan tiles dipertahankan;
        // pembersihan sungguhan dilakukan oleh adapter saat holder didaur ulang.
        super.onDetachedFromWindow();
    }

    /** Permukaan gambar yang hanya menggambar potongan yang terlihat. */
    private static final class TiledSurface extends View {
        interface Listener { void onError(String reason); }

        /**
         * Decoder dijalankan di thread latar. Dua thread saja: cukup untuk
         * menyiapkan potongan berikutnya tanpa merebut CPU dari rendering, dan
         * mencegah lonjakan memori saat pengguna melakukan fling cepat.
         */
        private final java.util.Set<Integer> pending = new HashSet<>();
        private final Object decoderLock = new Object();
        private int generation;

        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        /**
         * Potongan yang sudah didekode.
         *
         * <p>Tidak memakai {@code removeEldestEntry} yang me-recycle bitmap:
         * potongan yang baru saja digambar bisa ikut terbuang dan memicu
         * "trying to use a recycled bitmap" atau area kosong sesaat.
         * Pemangkasan dilakukan di {@link #trim(int, int)} dan tidak pernah
         * menyentuh potongan yang sedang terlihat.
         */
        private final Map<Integer, Bitmap> tiles =
                new LinkedHashMap<Integer, Bitmap>(ShinigamiPageTiles.CACHE_TILES * 2, 0.75f, true);

        Listener listener;
        private File file;
        private BitmapRegionDecoder decoder;
        private int sourceWidth;
        private int sourceHeight;
        private int tileHeight = 1280;
        private boolean rgb565;
        private ShinigamiQuality shownQuality;
        private boolean failed;

        TiledSurface(Context context) {
            super(context);
            setBackgroundColor(0xFF141414);
        }

        /** Satu pool bersama untuk semua halaman; jumlah thread dibatasi. */
        private static final ExecutorService DECODE_POOL = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "shinigami-tile");
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        });

        void show(File source, int width, int height, ShinigamiQuality quality) {
            // Halaman yang sama tidak dibongkar ulang. Sebelumnya setiap rebind
            // memanggil clear() + decode ulang, dan itu yang membuat gambar
            // berkedip saat menggulir bolak-balik.
            if (source != null && source.equals(file) && quality == shownQuality
                    && decoder != null && !decoder.isRecycled()) {
                return;
            }
            clear();
            generation++;
            shownQuality = quality;
            file = source;
            sourceWidth = width;
            sourceHeight = height;
            tileHeight = quality.tileHeight();
            rgb565 = quality.useRgb565(width, height);
            decoder = ShinigamiPageTiles.open(source);
            failed = decoder == null;
            if (failed && listener != null) {
                listener.onError("Gambar halaman rusak atau formatnya tidak didukung.");
            }
            invalidate();
        }

        void clear() {
            generation++;
            pending.clear();
            synchronized (decoderLock) {
                // Referensi dilepas saja; bitmap dibiarkan dibersihkan GC supaya
                // tidak ada bitmap yang di-recycle saat masih dipakai draw pass.
                tiles.clear();
                if (decoder != null && !decoder.isRecycled()) decoder.recycle();
                decoder = null;
            }
            file = null;
            sourceWidth = 0;
            sourceHeight = 0;
            failed = false;
            shownQuality = null;
        }

        @Override protected void onDraw(Canvas canvas) {
            if (decoder == null || sourceWidth <= 0 || sourceHeight <= 0) return;
            float scale = getWidth() / (float) sourceWidth;
            if (scale <= 0) return;
            Rect clip = canvas.getClipBounds();
            int from = Math.max(0, (int) Math.floor((clip.top / scale) / tileHeight));
            int to = Math.min(ShinigamiPageTiles.slices(sourceHeight, tileHeight).size() - 1,
                    (int) Math.floor((clip.bottom / scale) / tileHeight));
            trim(from, to);
            for (int index = from; index <= to; index++) {
                Bitmap tile = tiles.get(index);
                if (tile == null) {
                    // Decode TIDAK dilakukan di sini: BitmapRegionDecoder bisa
                    // memakan puluhan milidetik dan itu yang membuat scroll
                    // tersendat. Potongan disiapkan di thread latar, lalu
                    // invalidate() menggambar ulang.
                    requestDecode(index);
                    continue;
                }
                int top = index * tileHeight;
                int bottom = Math.min(sourceHeight, top + tileHeight);
                Rect dst = new Rect(0, Math.round(top * scale), getWidth(), Math.round(bottom * scale));
                canvas.drawBitmap(tile, null, dst, paint);
            }
        }

        /** True bila ada decoder hidup dan tidak pernah gagal. */
        boolean hasContent() {
            return decoder != null && !decoder.isRecycled() && !failed
                    && sourceWidth > 0 && sourceHeight > 0;
        }

        /** Buang potongan jauh dari area terlihat; yang terlihat tidak disentuh. */
        private void trim(int from, int to) {
            if (tiles.size() <= ShinigamiPageTiles.CACHE_TILES) return;
            java.util.Iterator<Map.Entry<Integer, Bitmap>> iterator = tiles.entrySet().iterator();
            while (iterator.hasNext() && tiles.size() > ShinigamiPageTiles.CACHE_TILES) {
                Map.Entry<Integer, Bitmap> entry = iterator.next();
                if (entry.getKey() >= from && entry.getKey() <= to) continue;
                iterator.remove();
            }
        }

        /**
         * Minta satu potongan didekode di thread latar.
         *
         * <p>Hasil hanya dipasang bila generasi masih sama, jadi potongan milik
         * halaman lama tidak pernah muncul di halaman baru. Setelah potongan
         * tampil, potongan berikutnya ikut disiapkan (prefetch satu langkah)
         * supaya menggulir tidak menunggu.
         */
        private void requestDecode(final int index) {
            if (index < 0 || pending.contains(index)) return;
            final BitmapRegionDecoder decoder;
            final int gen;
            final int width;
            final int height;
            final int tile;
            final boolean use565;
            synchronized (decoderLock) {
                decoder = this.decoder;
                if (decoder == null || decoder.isRecycled()) return;
                gen = generation;
                width = sourceWidth;
                height = sourceHeight;
                tile = tileHeight;
                use565 = rgb565;
            }
            final int top = index * tile;
            final int bottom = Math.min(height, top + tile);
            if (bottom <= top) return;
            pending.add(index);
            DECODE_POOL.execute(() -> {
                Bitmap bitmap;
                synchronized (decoderLock) {
                    if (gen != generation || decoder.isRecycled()) {
                        bitmap = null;
                    } else {
                        bitmap = ShinigamiPageTiles.region(decoder,
                                new Rect(0, top, width, bottom), use565);
                    }
                }
                final Bitmap decoded = bitmap;
                post(() -> {
                    pending.remove(index);
                    if (gen != generation) {
                        if (decoded != null) decoded.recycle();
                        return;
                    }
                    if (decoded == null) {
                        if (!failed && listener != null) {
                            failed = true;
                            listener.onError("Halaman gagal didekode pada bagian " + (index + 1) + ".");
                        }
                        return;
                    }
                    Bitmap previous = tiles.put(index, decoded);
                    if (previous != null) previous.recycle();
                    invalidate();
                    // Prefetch satu potongan ke arah bawah.
                    requestDecode(index + 1);
                });
            });
        }
    }
}
