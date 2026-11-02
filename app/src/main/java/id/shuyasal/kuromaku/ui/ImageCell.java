package id.shuyasal.kuromaku.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

/**
 * One illustration in the reader, inside a box that is laid out before the
 * bitmap arrives.
 *
 * <p>Why this class exists: the old cell was a {@code wrap_content}
 * {@code ImageView} with no real content, so it measured to zero width and the
 * picture never appeared even when the download succeeded. Here the width is
 * always the available width and the height comes from the size the source
 * declared, so the cell has a real box from the first layout pass and Glide is
 * handed a sensible target size.
 *
 * <p>Failure is visible, never silent: the cell shows the stage that failed plus
 * a "Coba lagi" button, and it keeps the reserved height so the page does not
 * jump around.
 */
public final class ImageCell extends FrameLayout {

    /** Ratio used when the source gives no pixel size: a typical book page. */
    private static final float FALLBACK_RATIO = 0.72f;

    private final ImageView image;
    private final LinearLayout errorBox;
    private final TextView errorTitle;
    private final TextView errorDetail;
    private final Button retry;

    private String url;
    private String stage = "url";
    private float ratio = FALLBACK_RATIO;
    private boolean failed;
    private Runnable onLoaded;

    public ImageCell(@NonNull Context context) {
        super(context);
        setTag("image_cell");

        image = new ImageView(context);
        image.setTag("image_view");
        image.setAdjustViewBounds(false);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription("Ilustrasi");
        addView(image, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        errorBox = new LinearLayout(context);
        errorBox.setTag("image_error");
        errorBox.setOrientation(LinearLayout.VERTICAL);
        errorBox.setGravity(Gravity.CENTER);
        errorBox.setPadding(Ui.dp(context, 14), Ui.dp(context, 14),
                Ui.dp(context, 14), Ui.dp(context, 14));
        errorBox.setVisibility(View.GONE);

        errorTitle = Ui.label(context, "Ilustrasi gagal dimuat", 13, 0xFF444444);
        errorTitle.setTypeface(Typeface.DEFAULT_BOLD);
        errorTitle.setGravity(Gravity.CENTER);
        errorBox.addView(errorTitle);

        errorDetail = Ui.label(context, "", 11, 0xFF666666);
        errorDetail.setTag("image_error_detail");
        errorDetail.setGravity(Gravity.CENTER);
        errorDetail.setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 8));
        errorBox.addView(errorDetail);

        retry = Ui.chip(context, "Coba lagi", Palette.of(Prefs.THEME_LIGHT));
        retry.setTag("image_retry");
        retry.setOnClickListener(v -> load());
        errorBox.addView(retry);

        addView(errorBox, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Ratio the box keeps: width divided by height. */
    public void setRatio(float ratio) {
        if (ratio > 0.05f && ratio < 20f) {
            this.ratio = ratio;
        } else {
            this.ratio = FALLBACK_RATIO;
        }
        requestLayout();
    }

    public void setOnLoaded(Runnable listener) {
        this.onLoaded = listener;
    }

    /** Stage name reported to the failure message and to logcat. */
    public String stage() {
        return stage;
    }

    public boolean hasFailed() {
        return failed;
    }

    /** The box always has a real height, independent of the bitmap. */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            width = getSuggestedMinimumWidth();
        }
        if (width <= 0) width = Ui.dp(getContext(), 320);
        int height = Math.max(1, Math.round(width / ratio));
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    /** Binds the illustration and starts the download. */
    public void bind(String imageUrl, Palette palette) {
        this.url = imageUrl;
        applyPalette(palette);
        load();
    }

    private void applyPalette(Palette palette) {
        setBackground(Ui.rounded(palette.surface, palette.divider, 8, getContext()));
        errorTitle.setTextColor(palette.text);
        errorDetail.setTextColor(palette.muted);
    }

    /** Loads (or reloads) the current URL, resetting the failure state. */
    public void load() {
        if (url == null || url.isEmpty()) {
            fail("url", "URL gambar kosong pada artikel sumber.");
            return;
        }
        failed = false;
        errorBox.setVisibility(View.GONE);
        image.setVisibility(View.VISIBLE);
        stage = "antrean";
        ImageLoadLog.stage(stage, url);

        try {
            Glide.with(getContext())
                    .load(url)
                    .fitCenter()
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                    @NonNull Target<Drawable> target,
                                                    boolean isFirstResource) {
                            String reason = e == null ? "tidak diketahui"
                                    : (e.getRootCauses().isEmpty()
                                    ? String.valueOf(e.getMessage())
                                    : String.valueOf(e.getRootCauses().get(0).getMessage()));
                            fail("unduh", reason);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model,
                                                       Target<Drawable> target,
                                                       @NonNull DataSource dataSource,
                                                       boolean isFirstResource) {
                            failed = false;
                            stage = "selesai";
                            errorBox.setVisibility(View.GONE);
                            image.setVisibility(View.VISIBLE);
                            ImageLoadLog.ok(url, resource.getIntrinsicWidth(),
                                    resource.getIntrinsicHeight());
                            if (onLoaded != null) onLoaded.run();
                            return false;
                        }
                    })
                    .into(image);
        } catch (Exception error) {
            fail("loader", error.getClass().getSimpleName()
                    + (error.getMessage() == null ? "" : ": " + error.getMessage()));
        }
    }

    private void fail(String failedStage, String reason) {
        failed = true;
        stage = failedStage;
        ImageLoadLog.fail(failedStage, url, reason);

        // The picture is gone, but the box keeps its height so the page does not
        // jump and the reader can still tell something belongs here.
        image.setVisibility(View.INVISIBLE);
        errorBox.setVisibility(View.VISIBLE);
        errorDetail.setText("Tahap: " + failedStage + "\n" + shorten(reason));
        if (retry.getParent() == errorBox) errorBox.removeView(retry);
        errorBox.addView(retry);
    }

    private static String shorten(String reason) {
        if (reason == null) return "tanpa keterangan";
        String one = reason.replace('\n', ' ').trim();
        return one.length() > 120 ? one.substring(0, 120) + "…" : one;
    }
}
