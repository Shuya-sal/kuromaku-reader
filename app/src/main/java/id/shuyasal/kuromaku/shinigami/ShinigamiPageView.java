package id.shuyasal.kuromaku.shinigami;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

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
    final ImageView image;
    private final TiledSurface surface;
    private final LinearLayout loading;
    private final LinearLayout errorBox;
    private final TextView error;
    private double ratio;
    private Runnable retryAction;

    public ShinigamiPageView(Context context) {
        super(context);
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

    public void showLoading() {
        ratio = 0;
        image.setImageDrawable(null);
        image.setVisibility(INVISIBLE);
        surface.setVisibility(GONE);
        loading.setVisibility(VISIBLE);
        errorBox.setVisibility(GONE);
        requestLayout();
    }

    /** Mode bitmap tunggal (dipakai tes dan pemanggil lama). */
    public void showReady(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            showFailure("Gambar tidak dapat didekode.");
            return;
        }
        ratio = (double) bitmap.getHeight() / bitmap.getWidth();
        surface.clear();
        surface.setVisibility(GONE);
        image.setImageBitmap(bitmap);
        image.setVisibility(VISIBLE);
        loading.setVisibility(GONE);
        errorBox.setVisibility(GONE);
        requestLayout();
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
        ratio = (double) size[1] / size[0];
        image.setImageDrawable(null);
        image.setVisibility(GONE);
        loading.setVisibility(GONE);
        errorBox.setVisibility(GONE);
        surface.show(file, size[0], size[1], quality == null ? ShinigamiQuality.AUTO : quality);
        surface.setVisibility(VISIBLE);
        requestLayout();
    }

    public void showFailure(String reason) {
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
        int height = ratio > 0 ? Math.max(1, (int) Math.ceil(width * ratio))
                : Math.max(320, getResources().getDisplayMetrics().heightPixels);
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override protected void onDetachedFromWindow() {
        surface.clear();
        super.onDetachedFromWindow();
    }

    /** Permukaan gambar yang hanya menggambar potongan yang terlihat. */
    private static final class TiledSurface extends View {
        interface Listener { void onError(String reason); }

        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Map<Integer, Bitmap> tiles =
                new LinkedHashMap<Integer, Bitmap>(ShinigamiPageTiles.CACHE_TILES + 1, 0.75f, true) {
                    @Override protected boolean removeEldestEntry(Map.Entry<Integer, Bitmap> eldest) {
                        if (size() <= ShinigamiPageTiles.CACHE_TILES) return false;
                        eldest.getValue().recycle();
                        return true;
                    }
                };

        Listener listener;
        private File file;
        private BitmapRegionDecoder decoder;
        private int sourceWidth;
        private int sourceHeight;
        private int tileHeight = 1280;
        private boolean rgb565;
        private boolean failed;

        TiledSurface(Context context) {
            super(context);
            setBackgroundColor(Color.BLACK);
        }

        void show(File source, int width, int height, ShinigamiQuality quality) {
            clear();
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
            for (Bitmap bitmap : tiles.values()) bitmap.recycle();
            tiles.clear();
            if (decoder != null && !decoder.isRecycled()) decoder.recycle();
            decoder = null;
            file = null;
            sourceWidth = 0;
            sourceHeight = 0;
            failed = false;
        }

        @Override protected void onDraw(Canvas canvas) {
            if (decoder == null || sourceWidth <= 0 || sourceHeight <= 0) return;
            float scale = getWidth() / (float) sourceWidth;
            if (scale <= 0) return;
            Rect clip = canvas.getClipBounds();
            int from = Math.max(0, (int) Math.floor((clip.top / scale) / tileHeight));
            int to = Math.min(ShinigamiPageTiles.slices(sourceHeight, tileHeight).size() - 1,
                    (int) Math.floor((clip.bottom / scale) / tileHeight));
            for (int index = from; index <= to; index++) {
                Bitmap tile = tiles.get(index);
                if (tile == null) {
                    tile = decode(index);
                    if (tile == null) continue;
                    tiles.put(index, tile);
                }
                int top = index * tileHeight;
                int bottom = Math.min(sourceHeight, top + tileHeight);
                Rect dst = new Rect(0, Math.round(top * scale), getWidth(), Math.round(bottom * scale));
                canvas.drawBitmap(tile, null, dst, paint);
            }
        }

        private Bitmap decode(int index) {
            int top = index * tileHeight;
            int bottom = Math.min(sourceHeight, top + tileHeight);
            if (bottom <= top) return null;
            Bitmap tile = ShinigamiPageTiles.region(decoder, new Rect(0, top, sourceWidth, bottom), rgb565);
            if (tile == null && !failed && listener != null) {
                failed = true;
                listener.onError("Halaman gagal didekode pada bagian " + (index + 1) + ".");
            }
            return tile;
        }
    }
}
