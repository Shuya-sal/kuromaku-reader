package id.shuyasal.kuromaku.shinigami;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Halaman tunggal yang bisa di-zoom dan digeser, tetap dirender sebagai
 * potongan (tile) pada resolusi sumber.
 *
 * <p>Zoom tidak pernah men-dekode ulang seluruh halaman: kanvas hanya
 * menskalakan potongan yang sudah ada, jadi memori tetap dibatasi beberapa
 * tile walau pengguna memperbesar sampai 4×.
 *
 * <p>Ketukan: satu ketukan memanggil {@link OnTapListener}, dua ketukan
 * memperbesar ke 2× (atau kembali ke ukuran pas bila sudah diperbesar), tiga
 * ketukan memperbesar ke 3×. Keputusan baru diambil setelah jendela
 * {@link ShinigamiTaps#WINDOW_MS} lewat supaya ketukan ketiga tidak terbaca
 * sebagai dua ketukan.
 */
public final class ShinigamiZoomView extends View {

    public interface OnTapListener {
        void onSingleTap();
    }

    private static final float MAX_FACTOR = 4f;
    private static final int TILE_HEIGHT = 1024;
    private static final int CACHE_TILES = 6;

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ShinigamiTaps taps = new ShinigamiTaps();
    private final GestureDetector detector;
    private final ScaleGestureDetector scaler;
    private final Map<Integer, Bitmap> tiles =
            new LinkedHashMap<Integer, Bitmap>(CACHE_TILES + 1, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Integer, Bitmap> eldest) {
                    if (size() <= CACHE_TILES) return false;
                    eldest.getValue().recycle();
                    return true;
                }
            };

    private BitmapRegionDecoder decoder;
    private int sourceWidth;
    private int sourceHeight;
    private float factor = 1f;
    private float offsetX;
    private float offsetY;
    private float lastX;
    private float lastY;
    private boolean dragging;
    private float touchSlop;
    private ValueAnimator animator;

    private OnTapListener tapListener;
    private final Runnable resolveTaps = () -> {
        int count = taps.resolve();
        if (count >= 3) {
            zoomTo(3f, lastX, lastY);
        } else if (count == 2) {
            if (factor > 1.05f) {
                zoomTo(1f, lastX, lastY);
            } else {
                zoomTo(2f, lastX, lastY);
            }
        } else if (count == 1 && tapListener != null) {
            tapListener.onSingleTap();
        }
    };

    public ShinigamiZoomView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        touchSlop = 12f * getResources().getDisplayMetrics().density;
        detector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) {
                return true;
            }
        });
        scaler = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector source) {
                float next = clampFactor(factor * source.getScaleFactor());
                zoomAround(next, source.getFocusX(), source.getFocusY());
                return true;
            }
        });
    }

    public void setOnTapListener(OnTapListener listener) {
        tapListener = listener;
    }

    /** Muat berkas gambar; hanya header yang dibaca di sini. */
    public void showFile(File file) {
        clear();
        decoder = ShinigamiPageTiles.open(file);
        int[] size = ShinigamiPageTiles.bounds(file);
        if (decoder == null || size == null || size[0] <= 0 || size[1] <= 0) {
            if (decoder != null) decoder.recycle();
            decoder = null;
            invalidate();
            return;
        }
        sourceWidth = size[0];
        sourceHeight = size[1];
        factor = 1f;
        offsetX = 0;
        offsetY = 0;
        invalidate();
    }

    public boolean hasPage() {
        return decoder != null;
    }

    /** Perbesar ke faktor tertentu (1 = pas lebar) dengan fokus di titik layar. */
    public void zoomTo(float target, float focusX, float focusY) {
        float from = factor;
        float to = clampFactor(target);
        if (Math.abs(from - to) < 0.01f) {
            if (to <= 1f) centerContent();
            invalidate();
            return;
        }
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(from, to);
        animator.setDuration(220);
        animator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            zoomAround(value, focusX, focusY);
            invalidate();
        });
        animator.start();
    }

    /** Terapkan zoom awal dari intent (mis. 2× dari ketuk dua kali di reader). */
    public void applyInitialZoom(float target, float focusX, float focusY) {
        zoomAround(clampFactor(target), focusX, focusY);
        invalidate();
    }

    public float zoomFactor() {
        return factor;
    }

    private void zoomAround(float next, float focusX, float focusY) {
        if (sourceWidth <= 0 || getWidth() <= 0) return;
        float previous = factor;
        if (previous <= 0f || Math.abs(next - previous) < 0.0001f) {
            factor = next;
            clampOffsets();
            return;
        }
        // Jaga titik di bawah jari tetap pada posisi yang sama.
        float scaleBefore = fitScale() * previous;
        float scaleAfter = fitScale() * next;
        float sourceX = (focusX - offsetX) / scaleBefore;
        float sourceY = (focusY - offsetY) / scaleBefore;
        factor = next;
        offsetX = focusX - sourceX * scaleAfter;
        offsetY = focusY - sourceY * scaleAfter;
        clampOffsets();
    }

    private float fitScale() {
        return sourceWidth <= 0 ? 1f : getWidth() / (float) sourceWidth;
    }

    private float clampFactor(float value) {
        return Math.max(1f, Math.min(MAX_FACTOR, value));
    }

    private void centerContent() {
        float total = fitScale() * factor;
        float contentWidth = sourceWidth * total;
        float contentHeight = sourceHeight * total;
        offsetX = (getWidth() - contentWidth) / 2f;
        offsetY = contentHeight <= getHeight() ? 0f : Math.min(0f, getHeight() - contentHeight);
    }

    private void clampOffsets() {
        float total = fitScale() * factor;
        float contentWidth = sourceWidth * total;
        float contentHeight = sourceHeight * total;
        if (contentWidth <= getWidth()) {
            offsetX = (getWidth() - contentWidth) / 2f;
        } else {
            offsetX = Math.min(0f, Math.max(getWidth() - contentWidth, offsetX));
        }
        if (contentHeight <= getHeight()) {
            offsetY = 0f;
        } else {
            offsetY = Math.min(0f, Math.max(getHeight() - contentHeight, offsetY));
        }
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (factor <= 1.01f) centerContent();
        clampOffsets();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (decoder == null || sourceWidth <= 0 || sourceHeight <= 0) return;
        float total = fitScale() * factor;
        if (total <= 0f) return;
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(total, total);

        float topSource = Math.max(0f, -offsetY / total);
        float bottomSource = Math.min(sourceHeight, (getHeight() - offsetY) / total);
        int from = Math.max(0, (int) Math.floor(topSource / TILE_HEIGHT));
        int to = Math.max(from, (int) Math.ceil(bottomSource / TILE_HEIGHT));
        for (int index = from; index <= to; index++) {
            int top = index * TILE_HEIGHT;
            if (top >= sourceHeight) break;
            int bottom = Math.min(sourceHeight, top + TILE_HEIGHT);
            Bitmap tile = tiles.get(index);
            if (tile == null) {
                tile = ShinigamiPageTiles.region(decoder,
                        new Rect(0, top, sourceWidth, bottom), false);
                if (tile == null) continue;
                tiles.put(index, tile);
            }
            canvas.drawBitmap(tile, 0f, top, paint);
        }
        canvas.restore();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        scaler.onTouchEvent(event);
        detector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                dragging = false;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                dragging = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() > 1) {
                    dragging = true;
                    break;
                }
                float dx = event.getX() - lastX;
                float dy = event.getY() - lastY;
                if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) dragging = true;
                if (dragging && factor > 1.01f && !scaler.isInProgress()) {
                    offsetX += dx;
                    offsetY += dy;
                    clampOffsets();
                    invalidate();
                }
                lastX = event.getX();
                lastY = event.getY();
                break;
            case MotionEvent.ACTION_UP:
                if (!dragging && !scaler.isInProgress()) {
                    lastX = event.getX();
                    lastY = event.getY();
                    registerTap(lastX, lastY);
                } else if (factor <= 1.01f) {
                    centerContent();
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                break;
            default:
                break;
        }
        return true;
    }

    private void registerTap(float x, float y) {
        lastX = x;
        lastY = y;
        taps.register(SystemClock.uptimeMillis());
        handler.removeCallbacks(resolveTaps);
        // Tunggu jendela ketukan: ketukan ke-3 harus menang atas ketukan ke-2.
        handler.postDelayed(resolveTaps, ShinigamiTaps.WINDOW_MS);
    }

    private void clear() {
        handler.removeCallbacks(resolveTaps);
        taps.reset();
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        for (Bitmap bitmap : tiles.values()) bitmap.recycle();
        tiles.clear();
        if (decoder != null && !decoder.isRecycled()) decoder.recycle();
        decoder = null;
        sourceWidth = 0;
        sourceHeight = 0;
        factor = 1f;
        offsetX = 0;
        offsetY = 0;
    }

    @Override protected void onDetachedFromWindow() {
        clear();
        super.onDetachedFromWindow();
    }
}
