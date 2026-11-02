package id.shuyasal.kuromaku.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

/**
 * Small, cheap motion helpers.
 *
 * <p>Rules this class enforces:
 * <ul>
 *   <li><b>Respect the system setting.</b> When Android "Remove animations" is
 *       on (animator duration scale 0), every helper jumps straight to the final
 *       state instead of animating.</li>
 *   <li><b>Cheap properties only.</b> Transitions animate alpha and translation,
 *       never layout of a full list, so there is no measurable jank.</li>
 *   <li><b>Short.</b> {@link #DURATION_MS} stays inside 180–250 ms.</li>
 * </ul>
 */
public final class Motion {

    /** Page transitions: inside the requested 180–250 ms window. */
    public static final int DURATION_MS = 220;
    /** Accordion expand/collapse: a touch quicker so it feels responsive. */
    public static final int ACCORDION_MS = 200;
    /** Slide distance for a page entering from the right. */
    private static final int SLIDE_DP = 24;

    private Motion() {
    }

    /**
     * True when animations may play: the app wide animator scale is not zero
     * (Android's "Remove animations" / developer "Animation scale: off") and the
     * platform still considers animators enabled.
     */
    public static boolean enabled(Context context) {
        if (context == null) return false;
        if (!ValueAnimator.areAnimatorsEnabled()) return false;
        try {
            float scale = Settings.Global.getFloat(context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            return scale > 0f;
        } catch (Exception ignored) {
            // Cannot read the setting: prefer motion over a dead UI.
            return true;
        }
    }

    /** Fade + light slide in from the right (forward navigation). */
    public static void enterFromRight(View view) {
        enter(view, 1);
    }

    /** Fade + light slide in from the left (backward navigation). */
    public static void enterFromLeft(View view) {
        enter(view, -1);
    }

    private static void enter(View view, int direction) {
        if (view == null) return;
        if (!enabled(view.getContext())) {
            reset(view);
            return;
        }
        final int shift = Ui.dp(view.getContext(), SLIDE_DP) * (direction > 0 ? 1 : -1);
        view.animate().cancel();
        view.setAlpha(0f);
        view.setTranslationX(shift);
        view.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(DURATION_MS)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
    }

    /**
     * Fade + light slide out, then runs {@code after} once the animation ends.
     * Always runs {@code after} exactly once, animated or not, so navigation can
     * never be lost when motion is disabled.
     */
    public static void exitToRight(View view, Runnable after) {
        if (view == null) {
            if (after != null) after.run();
            return;
        }
        if (!enabled(view.getContext())) {
            reset(view);
            if (after != null) after.run();
            return;
        }
        final int shift = Ui.dp(view.getContext(), SLIDE_DP);
        view.animate().cancel();
        view.animate()
                .alpha(0f)
                .translationX(shift)
                .setDuration(DURATION_MS)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .withEndAction(() -> {
                    reset(view);
                    if (after != null) after.run();
                })
                .start();
    }

    /** Puts a view back to its resting state; safe to call at any time. */
    public static void reset(View view) {
        if (view == null) return;
        view.animate().cancel();
        view.setAlpha(1f);
        view.setTranslationX(0f);
    }

    /**
     * Expands a container from zero height to its natural height. The view must
     * already be attached and VISIBLE; it ends as WRAP_CONTENT so later content
     * changes still lay out correctly.
     */
    public static void expand(final View body) {
        if (body == null) return;
        final ViewGroup.LayoutParams params = body.getLayoutParams();
        if (params == null) return;

        if (!enabled(body.getContext())) {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            body.setAlpha(1f);
            body.requestLayout();
            return;
        }

        int width = body.getWidth() > 0 ? body.getWidth()
                : body.getMeasuredWidth() > 0 ? body.getMeasuredWidth()
                : body.getResources().getDisplayMetrics().widthPixels;
        body.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        final int target = body.getMeasuredHeight();
        if (target <= 0) {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            body.requestLayout();
            return;
        }

        params.height = 0;
        body.setAlpha(0f);
        body.requestLayout();

        ValueAnimator animator = ValueAnimator.ofInt(0, target);
        animator.setDuration(ACCORDION_MS);
        animator.setInterpolator(new DecelerateInterpolator(1.4f));
        animator.addUpdateListener(animation -> {
            ViewGroup.LayoutParams current = body.getLayoutParams();
            if (current == null) return;
            current.height = (int) animation.getAnimatedValue();
            body.requestLayout();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                ViewGroup.LayoutParams current = body.getLayoutParams();
                if (current != null) {
                    current.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    body.requestLayout();
                }
                body.setAlpha(1f);
            }
        });
        animator.start();
    }

    /**
     * Collapses a container to zero height, then runs {@code after} so the caller
     * can commit the new state (and rebuild) while nothing is mid-flight.
     */
    public static void collapse(final View body, final Runnable after) {
        if (body == null) {
            if (after != null) after.run();
            return;
        }
        final ViewGroup.LayoutParams params = body.getLayoutParams();
        final int start = body.getHeight();
        if (params == null || start <= 0 || !enabled(body.getContext())) {
            if (after != null) after.run();
            return;
        }

        ValueAnimator animator = ValueAnimator.ofInt(start, 0);
        animator.setDuration(ACCORDION_MS);
        animator.setInterpolator(new DecelerateInterpolator(1.4f));
        animator.addUpdateListener(animation -> {
            ViewGroup.LayoutParams current = body.getLayoutParams();
            if (current == null) return;
            current.height = (int) animation.getAnimatedValue();
            body.requestLayout();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (after != null) after.run();
            }
        });
        animator.start();
    }
}
