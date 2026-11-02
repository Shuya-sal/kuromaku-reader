package id.shuyasal.kuromaku.ui;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

import androidx.annotation.NonNull;

/**
 * ImageView with pinch-to-zoom, drag to pan and double-tap to toggle zoom.
 *
 * <p>Kept separate from the activity so the zoom maths can be reasoned about and
 * the view reused anywhere an illustration is shown full screen.
 */
public final class ZoomableImageView extends ImageView {

    private static final float MIN_SCALE = 1f;
    private static final float DOUBLE_TAP_SCALE = 2.5f;

    private final Matrix matrix = new Matrix();
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    private float scale = 1f;
    private float baseScale = 1f;
    private float transX;
    private float transY;

    public ZoomableImageView(@NonNull Context context) {
        super(context);
        setScaleType(ScaleType.MATRIX);

        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float target = scale * detector.getScaleFactor();
                        applyScale(target, detector.getFocusX(), detector.getFocusY());
                        return true;
                    }
                });

        gestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent event) {
                        float target = scale > MIN_SCALE * 1.05f ? MIN_SCALE : DOUBLE_TAP_SCALE;
                        applyScale(target, event.getX(), event.getY());
                        return true;
                    }

                    @Override
                    public boolean onScroll(MotionEvent down, MotionEvent current,
                                            float distanceX, float distanceY) {
                        if (scale <= MIN_SCALE) return false;
                        transX -= distanceX;
                        transY -= distanceY;
                        clampTranslation();
                        updateMatrix();
                        return true;
                    }
                });
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        reset();
    }

    /** Fits the picture inside the view at 1x, centred. */
    public void reset() {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() == 0 || getHeight() == 0) {
            scale = 1f;
            transX = 0;
            transY = 0;
            updateMatrix();
            return;
        }
        float viewWidth = getWidth();
        float viewHeight = getHeight();
        float imageWidth = drawable.getIntrinsicWidth();
        float imageHeight = drawable.getIntrinsicHeight();
        if (imageWidth <= 0 || imageHeight <= 0) return;

        baseScale = Math.min(viewWidth / imageWidth, viewHeight / imageHeight);
        scale = MIN_SCALE;
        transX = (viewWidth - imageWidth * baseScale) / 2f;
        transY = (viewHeight - imageHeight * baseScale) / 2f;
        updateMatrix();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        reset();
    }

    /** Applies a new zoom level, keeping the focus point steady. */
    public void applyScale(float target, float focusX, float focusY) {
        float clamped = Math.max(MIN_SCALE, Math.min(4f, target));
        if (Math.abs(clamped - scale) < 0.001f) return;

        float rawScale = baseScale * clamped;
        float factor = rawScale / (baseScale * scale);
        transX = focusX - (focusX - transX) * factor;
        transY = focusY - (focusY - transY) * factor;
        scale = clamped;

        clampTranslation();
        updateMatrix();
    }

    /** Keeps the picture from being dragged off screen. */
    private void clampTranslation() {
        Drawable drawable = getDrawable();
        if (drawable == null) return;
        float contentWidth = drawable.getIntrinsicWidth() * baseScale * scale;
        float contentHeight = drawable.getIntrinsicHeight() * baseScale * scale;

        if (contentWidth <= getWidth()) {
            transX = (getWidth() - contentWidth) / 2f;
        } else {
            transX = Math.min(0f, Math.max(getWidth() - contentWidth, transX));
        }
        if (contentHeight <= getHeight()) {
            transY = (getHeight() - contentHeight) / 2f;
        } else {
            transY = Math.min(0f, Math.max(getHeight() - contentHeight, transY));
        }
    }

    private void updateMatrix() {
        matrix.reset();
        matrix.postScale(baseScale * scale, baseScale * scale);
        matrix.postTranslate(transX, transY);
        setImageMatrix(matrix);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            return true;
        }
        return true;
    }

    /** Current zoom level, 1 means fit to screen. Used by tests. */
    public float zoom() {
        return scale;
    }
}
