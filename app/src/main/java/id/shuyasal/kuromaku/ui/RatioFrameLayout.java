package id.shuyasal.kuromaku.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * FrameLayout that keeps a fixed width/height ratio.
 *
 * <p>Reserving the correct box before the image downloads means the cover never
 * stretches: the height is always {@code width / ratio}, matching the real
 * artwork regardless of the downloaded bitmap's resolution.
 */
public final class RatioFrameLayout extends FrameLayout {

    private float ratio = 0.7f;

    public RatioFrameLayout(Context context) {
        super(context);
    }

    public RatioFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** Sets width divided by height. Values <= 0 are ignored. */
    public void setRatio(float ratio) {
        if (ratio > 0 && ratio != this.ratio) {
            this.ratio = ratio;
            requestLayout();
        }
    }

    public float ratio() {
        return ratio;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            width = getSuggestedMinimumWidth();
        }
        int height = Math.max(1, Math.round(width / ratio));
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}
