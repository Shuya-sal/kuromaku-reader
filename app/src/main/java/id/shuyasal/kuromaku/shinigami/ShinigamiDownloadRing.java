package id.shuyasal.kuromaku.shinigami;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Indikator unduhan berbentuk lingkaran.
 *
 * <p>Busur mengikuti persentase halaman yang benar-benar tersimpan — nilainya
 * berasal dari {@link ShinigamiDownloadStatus} yang diisi worker, bukan timer.
 * Animasi hanya menghaluskan transisi angka lama ke angka baru, tidak pernah
 * bergerak tanpa data.
 *
 * <p>Tampilan: persentase di tengah saat mengunduh, centang saat selesai,
 * tanda seru saat gagal.
 */
public final class ShinigamiDownloadRing extends View {

    public static final int IDLE = 0;
    public static final int RUNNING = 1;
    public static final int DONE = 2;
    public static final int FAILED = 3;

    private static final long ANIMATION_MS = 320L;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Path check = new Path();

    private int state = IDLE;
    private float shownPercent;
    private int targetPercent;
    private ValueAnimator animator;
    private int accent = 0xFF4C8DFF;
    private int track = 0x33FFFFFF;
    private int danger = 0xFFD05757;

    public ShinigamiDownloadRing(Context context) {
        super(context);
        setTag("shinigami_download_ring");
        float density = getResources().getDisplayMetrics().density;
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(3f * density);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(3f * density);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(10f * density);
        textPaint.setFakeBoldText(true);
        setMinimumWidth((int) (44 * density));
        setMinimumHeight((int) (44 * density));
    }

    /** Warna mengikuti tema aplikasi (light/dark). */
    public void setColors(int accentColor, int dividerColor, int dangerColor) {
        accent = accentColor;
        track = dividerColor;
        danger = dangerColor;
        invalidate();
    }

    public void setState(int newState) {
        state = newState;
        invalidate();
    }

    public int ringState() {
        return state;
    }

    /**
     * Atur persentase. Animasi hanya dipakai untuk perubahan kecil supaya
     * status yang baru dibuka tidak "berlari" dari 0 ke 80%.
     */
    public void setPercent(int percent, boolean animate) {
        int clamped = Math.max(0, Math.min(100, percent));
        if (clamped == targetPercent) return;
        targetPercent = clamped;
        if (!animate || Math.abs(clamped - shownPercent) < 1f) {
            shownPercent = clamped;
            invalidate();
            return;
        }
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(shownPercent, clamped);
        animator.setDuration(ANIMATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value -> {
            shownPercent = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public int shownPercentInt() {
        return Math.round(shownPercent);
    }

    /** Nilai persentase yang saat ini ditampilkan. */
    public int progressPercent() {
        return Math.round(shownPercent);
    }

    /** Benar bila ring sedang menampilkan centang (unduhan lengkap). */
    public boolean showsCheck() {
        return state == DONE;
    }

    public int targetPercentInt() {
        return targetPercent;
    }

    @Override protected void onDraw(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float stroke = 3f * density;
        float size = Math.min(getWidth(), getHeight());
        float inset = stroke / 2f + 1f;
        oval.set((getWidth() - size) / 2f + inset, (getHeight() - size) / 2f + inset,
                (getWidth() + size) / 2f - inset, (getHeight() + size) / 2f - inset);

        trackPaint.setColor(track);
        canvas.drawCircle(oval.centerX(), oval.centerY(), oval.width() / 2f, trackPaint);

        int color = state == FAILED ? danger : accent;
        arcPaint.setColor(color);

        if (state == DONE) {
            canvas.drawArc(oval, -90f, 360f, false, arcPaint);
            drawCheck(canvas);
            return;
        }
        if (state == IDLE && targetPercent <= 0) {
            drawDots(canvas, color);
            return;
        }
        float sweep = 360f * Math.max(0f, Math.min(100f, shownPercent)) / 100f;
        if (sweep > 0f) canvas.drawArc(oval, -90f, sweep, false, arcPaint);

        textPaint.setColor(state == FAILED ? danger : color);
        String label = state == FAILED ? "!" : Math.round(shownPercent) + "%";
        float baseline = oval.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f;
        canvas.drawText(label, oval.centerX(), baseline, textPaint);
    }

    private void drawCheck(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float cx = oval.centerX();
        float cy = oval.centerY();
        float unit = Math.min(oval.width(), oval.height());
        check.reset();
        check.moveTo(cx - unit * 0.20f, cy + unit * 0.02f);
        check.lineTo(cx - unit * 0.04f, cy + unit * 0.16f);
        check.lineTo(cx + unit * 0.22f, cy - unit * 0.16f);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2.4f * density);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(accent);
        canvas.drawPath(check, stroke);
    }

    /** Lingkaran kosong dengan tiga titik: belum ada unduhan. */
    private void drawDots(Canvas canvas, int color) {
        float density = getResources().getDisplayMetrics().density;
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(color);
        float radius = 1.6f * density;
        float gap = 5f * density;
        float cy = oval.centerY();
        float cx = oval.centerX();
        canvas.drawCircle(cx - gap, cy, radius, dot);
        canvas.drawCircle(cx, cy, radius, dot);
        canvas.drawCircle(cx + gap, cy, radius, dot);
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    /** Warna default untuk pengujian dan pemakaian tanpa tema. */
    public static int defaultAccent() {
        return Color.rgb(76, 141, 255);
    }
}
