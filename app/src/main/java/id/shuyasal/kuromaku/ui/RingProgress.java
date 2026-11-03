package id.shuyasal.kuromaku.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Small circular download indicator: a ring that fills with real progress, or
 * spins when there is no measurable fraction.
 *
 * <p>The percentage drawn here is only ever the one reported by the downloader
 * from actual bytes; when the server sends no {@code Content-Length} the ring
 * turns into a spinner and no number is printed, because a percentage nobody
 * measured would be a lie.
 *
 * <p>The ring animates between values instead of jumping, but the animation never
 * invents a value: it only interpolates towards the one it was given. When the
 * system asks for reduced motion the new value is shown at once.
 */
public final class RingProgress extends View {

    private static final long ANIM_MS = 180;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    /** Fraction actually shown, 0..1, animated towards {@link #target}. */
    private float shown;
    private float target;
    /** Spinner state: no measurable total, so only rotation is drawn. */
    private boolean indeterminate;
    private ValueAnimator animator;
    private ValueAnimator spinner;

    public RingProgress(Context context) {
        super(context);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
    }

    /** Ring colours follow the theme, so dark mode stays readable. */
    public void colors(int trackColor, int arcColor, int strokeDp) {
        track.setColor(trackColor);
        arc.setColor(arcColor);
        float stroke = Ui.dp(getContext(), strokeDp);
        track.setStrokeWidth(stroke);
        arc.setStrokeWidth(stroke);
        invalidate();
    }

    /**
     * Sets a measured fraction. Values are clamped; a negative percentage means
     * the total size is unknown and the ring becomes a spinner.
     */
    public void setProgress(int percent) {
        if (percent < 0) {
            indeterminate(true);
            return;
        }
        indeterminate(false);
        target = Math.max(0f, Math.min(1f, percent / 100f));
        if (!Motion.enabled(getContext())) {
            shown = target;
            invalidate();
            return;
        }
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(shown, target);
        animator.setDuration(ANIM_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value -> {
            shown = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    /** True while the ring spins because no total size was declared. */
    public boolean isIndeterminate() {
        return indeterminate;
    }

    private void indeterminate(boolean value) {
        if (indeterminate == value) return;
        indeterminate = value;
        if (!value) {
            if (spinner != null) spinner.cancel();
            invalidate();
            return;
        }
        if (!Motion.enabled(getContext())) {
            invalidate();
            return;
        }
        if (spinner != null) spinner.cancel();
        spinner = ValueAnimator.ofFloat(0f, 360f);
        spinner.setDuration(900);
        spinner.setRepeatCount(ValueAnimator.INFINITE);
        spinner.setInterpolator(null);
        spinner.addUpdateListener(animation -> {
            shown = (float) animation.getAnimatedValue() / 360f;
            invalidate();
        });
        spinner.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        if (spinner != null) spinner.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int size = Math.min(getWidth(), getHeight());
        if (size <= 0) return;
        float inset = Math.max(track.getStrokeWidth(), arc.getStrokeWidth()) / 2f;
        box.set(inset, inset, size - inset, size - inset);

        canvas.drawArc(box, 0f, 360f, false, track);
        if (indeterminate) {
            // A rotating arc is honest about not knowing how far along it is.
            canvas.drawArc(box, (shown * 360f) - 90f, 90f, false, arc);
            return;
        }
        if (shown <= 0f) return;
        canvas.drawArc(box, -90f, shown * 360f, false, arc);
    }
}