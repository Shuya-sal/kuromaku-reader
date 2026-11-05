package id.shuyasal.kuromaku;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import id.shuyasal.kuromaku.ui.ImageLoadLog;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.SystemBarInsets;
import id.shuyasal.kuromaku.ui.Ui;
import id.shuyasal.kuromaku.ui.ZoomableImageView;

/**
 * Full-screen illustration viewer: pinch to zoom, drag to pan, double-tap to
 * toggle, tap the close button to return to the chapter.
 *
 * <p>Loading failures are reported on screen with the stage that failed and a
 * retry button, matching the inline cell inside the reader.
 */
public class ImageViewerActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "image_url";
    public static final String EXTRA_CAPTION = "image_caption";

    private Palette palette;
    private ZoomableImageView image;
    private ProgressBar spinner;
    private LinearLayout errorBox;
    private TextView errorDetail;
    private String url;
    /**
     * Single source of truth for window insets (same fix as MainActivity).
     * A "changed?" guard against loose int fields loses the status bar padding
     * whenever the tree is rebuilt for a new theme.
     */
    private final SystemBarInsets insets = new SystemBarInsets();
    private LinearLayout bar;
    private FrameLayout stage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        palette = Palette.of(new Prefs(this).resolvedTheme());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        url = getIntent().getStringExtra(EXTRA_URL);
        String caption = getIntent().getStringExtra(EXTRA_CAPTION);

        buildUi(caption);
        if (url == null || url.isEmpty()) {
            showError("url", "URL gambar kosong.");
            return;
        }
        load();
    }

    private void buildUi(String caption) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF000000);

        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xCC000000);
        bar.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));

        android.widget.Button close = Ui.backButton(this, palette);
        close.setText("Tutup");
        close.setTag("viewer_close");
        close.setOnClickListener(v -> finish());
        bar.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, Ui.TOUCH_DP)));

        TextView title = Ui.label(this,
                caption == null || caption.isEmpty() ? "Ilustrasi" : caption, 12, 0xFFEEEEEE);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(Ui.dp(this, 10), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        android.widget.Button reset = Ui.chip(this, "Pas layar", palette);
        reset.setTag("viewer_reset");
        reset.setOnClickListener(v -> image.reset());
        bar.addView(reset);

        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        stage = new FrameLayout(this);
        image = new ZoomableImageView(this);
        image.setTag("viewer_image");
        stage.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        stage.addView(spinner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        errorBox = new LinearLayout(this);
        errorBox.setOrientation(LinearLayout.VERTICAL);
        errorBox.setGravity(Gravity.CENTER);
        errorBox.setPadding(Ui.dp(this, 24), Ui.dp(this, 24),
                Ui.dp(this, 24), Ui.dp(this, 24));
        errorBox.setVisibility(View.GONE);

        TextView errorTitle = Ui.label(this, "Ilustrasi gagal dimuat", 15, 0xFFEEEEEE);
        errorTitle.setGravity(Gravity.CENTER);
        errorBox.addView(errorTitle);

        errorDetail = Ui.label(this, "", 12, 0xFFAAAAAA);
        errorDetail.setTag("viewer_error_detail");
        errorDetail.setGravity(Gravity.CENTER);
        errorDetail.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 12));
        errorBox.addView(errorDetail);

        android.widget.Button retry = Ui.chip(this, "Coba lagi", palette);
        retry.setTag("viewer_retry");
        retry.setOnClickListener(v -> load());
        errorBox.addView(retry);

        stage.addView(errorBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        root.addView(stage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            insets.accept(windowInsets);
            applyInsets();
            return windowInsets;
        });
        applyInsets();
        ViewCompat.requestApplyInsets(root);
    }

    /** Top inset on the close bar, bottom inset on the image stage — one each. */
    private void applyInsets() {
        if (bar != null) {
            bar.setPadding(Ui.dp(this, 10), insets.get().top + Ui.dp(this, 8),
                    Ui.dp(this, 10), Ui.dp(this, 8));
        }
        if (stage != null) {
            stage.setPadding(0, 0, 0, insets.get().bottom);
        }
    }

    private void load() {
        spinner.setVisibility(View.VISIBLE);
        errorBox.setVisibility(View.GONE);
        image.setVisibility(View.INVISIBLE);
        ImageLoadLog.stage("penampil", url);

        try {
            Glide.with(this)
                    .load(url)
                    .fitCenter()
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                    Target<Drawable> target,
                                                    boolean isFirstResource) {
                            String reason = e == null ? "tidak diketahui"
                                    : (e.getRootCauses().isEmpty()
                                    ? String.valueOf(e.getMessage())
                                    : String.valueOf(e.getRootCauses().get(0).getMessage()));
                            showError("unduh", reason);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model,
                                                       Target<Drawable> target,
                                                       DataSource dataSource,
                                                       boolean isFirstResource) {
                            spinner.setVisibility(View.GONE);
                            errorBox.setVisibility(View.GONE);
                            image.setVisibility(View.VISIBLE);
                            image.reset();
                            ImageLoadLog.ok(url, resource.getIntrinsicWidth(),
                                    resource.getIntrinsicHeight());
                            return false;
                        }
                    })
                    .into(image);
        } catch (Exception error) {
            showError("loader", error.getClass().getSimpleName());
        }
    }

    private void showError(String stageName, String reason) {
        spinner.setVisibility(View.GONE);
        image.setVisibility(View.INVISIBLE);
        errorBox.setVisibility(View.VISIBLE);
        errorDetail.setText("Tahap: " + stageName + "\n" + reason);
        ImageLoadLog.fail(stageName, url, reason);
        Toast.makeText(this, "Gambar tidak dapat dimuat", Toast.LENGTH_SHORT).show();
    }
}
