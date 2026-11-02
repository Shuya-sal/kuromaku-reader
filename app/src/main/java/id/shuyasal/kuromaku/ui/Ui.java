package id.shuyasal.kuromaku.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import id.shuyasal.kuromaku.R;

/**
 * Tiny view factory so the UI stays consistent without a design system.
 *
 * <p>Touch targets: every interactive view created here is at least
 * {@link #TOUCH_DP} dp in both directions, as required by the Android
 * accessibility guidance. Ripple feedback is created in code so it follows the
 * active reading theme instead of a hardcoded resource colour.
 */
public final class Ui {

    /** Minimum touch target for any button, icon or row that reacts to a tap. */
    public static final int TOUCH_DP = 48;

    private Ui() {
    }

    public static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    public static TextView label(Context context, String text, float sizeSp, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        return view;
    }

    public static Button chip(Context context, String text, Palette palette) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(palette.text);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(dp(context, TOUCH_DP));
        button.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));
        button.setBackground(ripple(context, palette.surface, palette.divider, 10,
                palette.ripple));
        button.setTypeface(Typeface.DEFAULT);
        button.setGravity(Gravity.CENTER);
        button.setStateListAnimator(null);
        return button;
    }

    /**
     * Back button used on every secondary page: a rounded rectangle with a left
     * arrow and the word "Kembali", at least 48 dp tall and wide enough for the
     * text so it is comfortable to hit with one hand.
     */
    public static Button backButton(Context context, Palette palette) {
        Button button = new Button(context);
        button.setText("Kembali");
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(palette.text);
        button.setTypeface(null, Typeface.BOLD);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setMinWidth(0);
        button.setMinimumWidth(dp(context, 92));
        button.setMinHeight(dp(context, TOUCH_DP));
        button.setMinimumHeight(dp(context, TOUCH_DP));
        button.setPaddingRelative(dp(context, 12), 0, dp(context, 16), 0);
        button.setBackground(ripple(context, palette.surface, palette.divider, 12,
                palette.ripple));
        button.setStateListAnimator(null);

        Drawable arrow = tinted(context, R.drawable.ic_arrow_back, palette.text);
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(arrow, null, null, null);
        button.setCompoundDrawablePadding(dp(context, 6));
        button.setContentDescription("Kembali");
        button.setTag("back_button");
        return button;
    }

    /**
     * Square icon-only action for the toolbar: 48 dp tap area, themed ripple and
     * a content description so it still works with a screen reader.
     */
    public static ImageButton iconButton(Context context, int iconRes, String description,
                                        Palette palette) {
        ImageButton button = new ImageButton(context);
        button.setImageDrawable(tinted(context, iconRes, palette.text));
        button.setContentDescription(description);
        button.setBackground(ripple(context, palette.surface, 0, 12, palette.ripple));
        button.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
        button.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        button.setStateListAnimator(null);
        return button;
    }

    /** Drawable from resources tinted to the active palette colour. */
    public static Drawable tinted(Context context, int iconRes, int color) {
        Drawable drawable = ContextCompat.getDrawable(context, iconRes);
        if (drawable == null) return null;
        drawable = DrawableCompat.wrap(drawable.mutate());
        DrawableCompat.setTint(drawable, color);
        return drawable;
    }

    /**
     * Rounded background with a ripple on touch. The mask is a plain rounded
     * rectangle so the ripple is clipped to the same shape as the surface.
     */
    public static RippleDrawable ripple(Context context, int fill, int stroke, int radiusDp,
                                        int rippleColor) {
        Drawable content = rounded(fill, stroke, radiusDp, context);
        Drawable mask = rounded(android.graphics.Color.WHITE, 0, radiusDp, context);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    public static GradientDrawable rounded(int fill, int stroke, int radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (stroke != 0) drawable.setStroke(Math.max(1, dp(context, 1)), stroke);
        return drawable;
    }

    /** Ripple colour that reads well on the current theme without being loud. */
    public static int defaultRipple(Palette palette) {
        return palette.ripple;
    }

    /** Ensures a view is reachable: at least 48 dp in both directions. */
    public static void ensureTouchTarget(Context context, View view) {
        int min = dp(context, TOUCH_DP);
        view.setMinimumWidth(Math.max(view.getMinimumWidth(), min));
        view.setMinimumHeight(Math.max(view.getMinimumHeight(), min));
    }
}
