package id.shuyasal.kuromaku.shinigami;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Zoom satu halaman Shinigami.
 *
 * <p>Halaman dirender bertahap dari {@link ShinigamiZoomView}: hanya potongan
 * yang terlihat yang didekode, pada resolusi asli sumber (800 px). Tidak ada
 * lagi decode seluruh halaman 800×10.000 ke RAM, sehingga halaman ekstra
 * panjang tetap stabil.
 *
 * <p>Ketukan: dua kali → 2× (ketuk dua lagi kembali ke 1×), tiga kali → 3×,
 * cubit dua jari dan geser tetap didukung. Reset zoom tidak menyentuh progres
 * membaca karena activity ini tidak menulis progres apa pun.
 */
public class ShinigamiImageZoomActivity extends AppCompatActivity {

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ShinigamiZoomView image;
    private ProgressBar loading;
    private LinearLayout error;
    private TextView message;
    private TextView hint;
    private String url;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ShinigamiImageRequest.register(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        image = new ShinigamiZoomView(this);
        image.setTag("zoom_image");
        root.addView(image, new FrameLayout.LayoutParams(-1, -1));
        loading = new ProgressBar(this);
        root.addView(loading, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        error = new LinearLayout(this);
        error.setOrientation(LinearLayout.VERTICAL);
        error.setGravity(Gravity.CENTER);
        error.setTag("zoom_error");
        message = new TextView(this);
        message.setTextColor(Color.WHITE);
        message.setGravity(Gravity.CENTER);
        message.setPadding(24, 24, 24, 24);
        error.addView(message);
        Button retry = new Button(this);
        retry.setText("Coba Lagi");
        retry.setTag("zoom_retry");
        retry.setOnClickListener(v -> load());
        error.addView(retry);
        root.addView(error, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        hint = new TextView(this);
        hint.setTextColor(0xCCFFFFFF);
        hint.setTextSize(11f);
        hint.setText("Ketuk 2×: zoom 2×  •  ketuk 3×: zoom 3×  •  cubit untuk bebas");
        hint.setPadding(24, 24, 24, 24);
        hint.setTag("zoom_hint");
        root.addView(hint, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        Button close = new Button(this);
        close.setText("Kembali");
        close.setTag("zoom_close");
        close.setOnClickListener(v -> finish());
        root.addView(close, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
        setContentView(root);

        image.setOnTapListener(this::toggleChrome);
        url = getIntent().getStringExtra("url");
        load();
    }

    /** Ketuk satu kali menyembunyikan/menampilkan tombol supaya gambar lega. */
    private void toggleChrome() {
        int next = hint.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE;
        hint.setVisibility(next);
    }

    private void failure(String reason) {
        loading.setVisibility(View.GONE);
        image.setVisibility(View.INVISIBLE);
        message.setText(reason);
        error.setVisibility(View.VISIBLE);
    }

    private void load() {
        String chapter = getIntent().getStringExtra("chapter_id");
        int page = getIntent().getIntExtra("page", -1);
        File local = new ShinigamiDownloader(this).pageFile(chapter, page);
        if (local == null && !ShinigamiConfig.isValidResource(url)) {
            failure("Alamat gambar tidak valid atau bukan HTTPS.");
            return;
        }
        image.setVisibility(View.INVISIBLE);
        loading.setVisibility(View.VISIBLE);
        error.setVisibility(View.GONE);
        final File offline = local;
        final String referer = new ShinigamiConfig(this).siteBase() + "/";
        worker.execute(() -> {
            File file = offline;
            String reason = null;
            if (file == null) {
                try {
                    file = new ShinigamiPageCache(ShinigamiImageZoomActivity.this).fetch(url, referer);
                } catch (java.io.IOException failure) {
                    reason = failure.getMessage() == null ? "Gambar gagal dimuat." : failure.getMessage();
                }
            }
            final File ready = file;
            final String failure = reason;
            final int[] size = ready == null ? null : ShinigamiPageTiles.bounds(ready);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (ready == null) {
                    failure(failure != null ? failure : "Gambar tidak dapat didekode.");
                    return;
                }
                image.showFile(ready);
                if (!image.hasPage()) {
                    failure("Gambar tidak dapat didekode.");
                    return;
                }
                float factor = getIntent().getFloatExtra("zoom_factor", 1f);
                float focusX = getIntent().getFloatExtra("focus_x", 0.5f) * image.getWidth();
                float focusY = getIntent().getFloatExtra("focus_y", 0.5f) * image.getHeight();
                image.post(() -> {
                    if (factor > 1.01f) image.applyInitialZoom(factor, focusX, focusY);
                });
                image.setVisibility(View.VISIBLE);
                loading.setVisibility(View.GONE);
                error.setVisibility(View.GONE);
                android.util.Log.i("ShinigamiZoom", "source="
                        + (size == null ? "?" : size[0] + "x" + size[1]) + " tiled=true");
            });
        });
    }

    @Override public void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
