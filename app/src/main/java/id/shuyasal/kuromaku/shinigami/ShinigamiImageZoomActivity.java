package id.shuyasal.kuromaku.shinigami;

import android.graphics.Bitmap;
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

import id.shuyasal.kuromaku.ui.ZoomableImageView;

/**
 * Zoom satu halaman Shinigami.
 *
 * <p>Gambar didekode pada resolusi asli sumber (lebar 800 px, tinggi sampai
 * 10.000 px) selama masih di bawah batas piksel; batas itu mencegah
 * {@link OutOfMemoryError} pada halaman ekstra panjang. Versi sebelumnya
 * memaksa hasil muat menjadi 1080×8192 dengan RGB_565 sehingga zoom tampak
 * lunak.
 */
public class ShinigamiImageZoomActivity extends AppCompatActivity {

    /** Batas piksel satu bitmap di layar zoom (~32 MB ARGB_8888). */
    private static final int MAX_PIXELS = 8_100_000;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ZoomableImageView image;
    private ProgressBar loading;
    private LinearLayout error;
    private TextView message;
    private String url;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ShinigamiImageRequest.register(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        image = new ZoomableImageView(this);
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
        Button close = new Button(this);
        close.setText("Kembali");
        close.setOnClickListener(v -> finish());
        root.addView(close, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
        setContentView(root);
        url = getIntent().getStringExtra("url");
        load();
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
            Bitmap bitmap = null;
            if (file != null) bitmap = ShinigamiPageTiles.decodeFull(file, MAX_PIXELS, true);
            if (bitmap == null && file != null) {
                // Halaman ekstra panjang bisa kehabisan heap pada ARGB_8888;
                // RGB_565 memakai separuh memori dengan lebar tetap sama.
                bitmap = ShinigamiPageTiles.decodeFull(file, MAX_PIXELS / 2, false);
            }
            final Bitmap ready = bitmap;
            final String failure = reason;
            final int[] size = file == null ? null : ShinigamiPageTiles.bounds(file);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (ready == null) {
                    failure(failure != null ? failure : "Gambar tidak dapat didekode.");
                    return;
                }
                image.setImageBitmap(ready);
                image.reset();
                image.setVisibility(View.VISIBLE);
                loading.setVisibility(View.GONE);
                error.setVisibility(View.GONE);
                android.util.Log.i("ShinigamiZoom", "source=" + (size == null ? "?" : size[0] + "x" + size[1])
                        + " decoded=" + ready.getWidth() + "x" + ready.getHeight());
            });
        });
    }

    @Override public void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
