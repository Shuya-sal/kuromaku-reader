package id.shuyasal.kuromaku.shinigami;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.Ui;

public class ShinigamiReaderActivity extends AppCompatActivity {

    public static final String EXTRA_MANGA_ID = "manga_id";
    public static final String EXTRA_MANGA_TITLE = "manga_title";
    public static final String EXTRA_MANGA_COVER = "manga_cover";
    public static final String EXTRA_CHAPTER_ID = "chapter_id";
    public static final String EXTRA_CHAPTER_NUMBER = "chapter_number";
    public static final String EXTRA_CHAPTER_TITLE = "chapter_title";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<String> pages = new ArrayList<>();

    private Palette palette;
    private ShinigamiSource source;
    private ShinigamiStore store;
    private ShinigamiDownloader downloader;

    private String mangaId;
    private String mangaTitle;
    private String mangaCover;
    private String chapterId;
    private double chapterNumber;
    private String chapterTitle;

    private RecyclerView list;
    private PageAdapter adapter;
    private LinearLayout bar;
    private TextView statusText;
    private TextView errorText;
    private Button retryButton;
    private TextView offlineNotice;
    private boolean fullscreen;
    private boolean offlineChapter;
    private boolean progressReliable = true;
    private boolean positionReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        palette = Palette.of(new Prefs(this).theme());
        source = createSource();
        store = new ShinigamiStore(this);
        downloader = new ShinigamiDownloader(this);
        ShinigamiImageRequest.register(this);

        mangaId = getIntent().getStringExtra(EXTRA_MANGA_ID);
        mangaTitle = getIntent().getStringExtra(EXTRA_MANGA_TITLE);
        mangaCover = getIntent().getStringExtra(EXTRA_MANGA_COVER);
        chapterId = getIntent().getStringExtra(EXTRA_CHAPTER_ID);
        chapterNumber = getIntent().getDoubleExtra(EXTRA_CHAPTER_NUMBER, 0d);
        chapterTitle = getIntent().getStringExtra(EXTRA_CHAPTER_TITLE);

        if (chapterId == null || chapterId.isEmpty()) {
            Toast.makeText(this, "Chapter tidak ditemukan.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        buildUi();
        loadPages();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        bar = buildBar();
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout body = new FrameLayout(this);
        list = new RecyclerView(this);
        list.setTag("shinigami_reader_list");
        list.setBackgroundColor(Color.BLACK);
        adapter = new PageAdapter();
        list.setAdapter(adapter);
        LinearLayoutManager manager = new LinearLayoutManager(this);
        list.setLayoutManager(manager);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView view, int dx, int dy) {
                saveProgress();
            }
        });
        body.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        statusText = Ui.label(this, "Memuat halaman...", 13, 0xFFCCCCCC);
        statusText.setGravity(Gravity.CENTER);
        statusText.setTag("shinigami_reader_status");
        statusText.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        body.addView(statusText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        errorText = Ui.label(this, "", 13, 0xFFFF8080);
        errorText.setGravity(Gravity.CENTER);
        errorText.setTag("shinigami_reader_error");
        errorText.setVisibility(View.GONE);
        errorText.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        body.addView(errorText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        retryButton = Ui.chip(this, "Coba lagi", palette);
        retryButton.setTag("shinigami_reader_retry");
        retryButton.setVisibility(View.GONE);
        retryButton.setOnClickListener(v -> loadPages());
        body.addView(retryButton, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        offlineNotice = Ui.label(this, "", 12, 0xFFFFE082);
        offlineNotice.setTag("shinigami_reader_offline_notice");
        offlineNotice.setVisibility(View.GONE);
        offlineNotice.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        offlineNotice.setBackgroundColor(0xCC1A1A1A);
        body.addView(offlineNotice, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP));

        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                            | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    private LinearLayout buildBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        bar.setBackgroundColor(0xFF1A1A1A);

        Button back = Ui.backButton(this, palette);
        back.setTag("shinigami_reader_back");
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, Ui.TOUCH_DP)));

        TextView heading = Ui.label(this,
                ShinigamiManga.chapterLabel(chapterNumber, chapterTitle), 16, 0xFFEEEEEE);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        heading.setPadding(Ui.dp(this, 10), 0, 0, 0);
        bar.addView(heading, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        android.widget.ImageButton menu = Ui.iconButton(this, id.shuyasal.kuromaku.R.drawable.ic_menu,
                "Buka menu Novel dan Manga", palette);
        menu.setTag("section_menu");
        menu.setOnClickListener(v -> ShinigamiNavigation.showDrawer(this, palette, false));
        bar.addView(menu, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));

        Button zoomButton = Ui.chip(this, "Layar penuh", palette);
        zoomButton.setTag("shinigami_reader_fullscreen");
        zoomButton.setOnClickListener(v -> toggleFullscreen());
        bar.addView(zoomButton);
        return bar;
    }

    private void toggleFullscreen() {
        fullscreen = !fullscreen;
        bar.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        if (fullscreen) {
            androidx.core.view.WindowInsetsControllerCompat controller =
                    androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            androidx.core.view.WindowInsetsControllerCompat controller =
                    androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars());
        }
    }

    private void loadPages() {
        statusText.setVisibility(View.VISIBLE);
        errorText.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);

        int expected = expectedPages();
        if (expected > 0 && downloader.isDownloaded(chapterId, expected)) {
            showOfflineChapter(expected);
            return;
        }
        offlineChapter = false;
        worker.execute(() -> {
            List<String> urls = new ArrayList<>();
            String failure = null;
            boolean usedCache = false;
            try {
                urls.addAll(source.chapterImages(chapterId).urls);
            } catch (ShinigamiSource.SourceException error) {
                failure = error.getCause() instanceof java.io.IOException
                        ? null
                        : error.getMessage();
                if (failure == null) {
                    // Only connectivity failures may reuse the prior, validated page order.
                    // Access-denied/CAPTCHA responses are never worked around.
                    urls.addAll(store.chapterUrls(chapterId));
                    usedCache = !urls.isEmpty();
                }
            }
            final List<String> result = urls;
            final String message = failure;
            final boolean cached = usedCache;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (message != null) {
                    statusText.setVisibility(View.GONE);
                    errorText.setText(message);
                    errorText.setVisibility(View.VISIBLE);
                    retryButton.setVisibility(View.VISIBLE);
                    return;
                }
                if (result.isEmpty()) {
                    statusText.setVisibility(View.GONE);
                    errorText.setText(offlineMessage());
                    errorText.setVisibility(View.VISIBLE);
                    retryButton.setVisibility(View.VISIBLE);
                    return;
                }
                pages.clear();
                pages.addAll(result);
                adapter.notifyDataSetChanged();
                statusText.setVisibility(View.GONE);
                progressReliable = !cached;
                if (cached) {
                    offlineNotice.setText("Koneksi bermasalah. Chapter ini belum tersimpan "
                            + "lengkap, jadi sebagian gambar mungkin belum bisa dibuka offline.");
                    offlineNotice.setVisibility(View.VISIBLE);
                } else {
                    offlineNotice.setVisibility(View.GONE);
                }
                recordOpen();
                restorePosition();
            });
        });
    }

    private int expectedPages() {
        int expected = store.expectedPages(chapterId);
        if (expected <= 0) expected = store.chapterUrls(chapterId).size();
        return expected;
    }

    private void showOfflineChapter(int expected) {
        pages.clear();
        for (int index = 0; index < expected; index++) pages.add("");
        offlineChapter = true;
        statusText.setVisibility(View.GONE);
        errorText.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        offlineNotice.setText("Dibaca dari penyimpanan offline. Tidak perlu koneksi internet.");
        offlineNotice.setVisibility(View.VISIBLE);
        progressReliable = true;
        adapter.notifyDataSetChanged();
        recordOpen();
        restorePosition();
        Toast.makeText(this, "Dibaca dari penyimpanan offline.", Toast.LENGTH_SHORT).show();
    }

    private static String offlineMessage() {
        return "Chapter ini belum tersimpan lengkap dan koneksi internet tidak tersedia.\n\n"
                + "Unduh chapter saat online untuk membacanya offline. Progres membaca tetap "
                + "tersimpan, jadi posisi terakhir tidak hilang.";
    }

    private void recordOpen() {
        store.recordRead(mangaId, mangaTitle, mangaCover, chapterId, chapterNumber);
        store.setLastChapter(mangaId, chapterId, chapterNumber);
    }

    private void restorePosition() {
        int page = store.page(chapterId);
        if (page <= 0) {
            positionReady = true;
            return;
        }
        if (page >= pages.size()) return;
        list.scrollToPosition(page);
        positionReady = true;
    }

    private void saveProgress() {
        if (pages.isEmpty() || !positionReady) return;
        LinearLayoutManager manager = (LinearLayoutManager) list.getLayoutManager();
        if (manager == null) return;
        int first = manager.findFirstVisibleItemPosition();
        if (first < 0) return;
        store.setPage(chapterId, first);
        if (!progressReliable) return;
        int percent = (int) Math.round(((first + 1) * 100.0) / pages.size());
        store.setProgress(chapterId, percent);
    }

    protected ShinigamiSource createSource() {
        return new ShinigamiSource(this);
    }

    @Override
    public void onBackPressed() {
        if (fullscreen) {
            toggleFullscreen();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveProgress();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private final class PageAdapter extends RecyclerView.Adapter<PageAdapter.Holder> {
        class Holder extends RecyclerView.ViewHolder {
            final ShinigamiPageView cell;
            com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap> target;
            int bound = -1;
            Holder(ShinigamiPageView item) { super(item); cell = item; }
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            ShinigamiPageView cell = new ShinigamiPageView(ShinigamiReaderActivity.this);
            cell.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new Holder(cell);
        }

        @Override public void onBindViewHolder(Holder holder, int position) {
            holder.bound = position;
            holder.cell.setRetryAction(() -> {
                if (holder.bound >= 0 && holder.bound < pages.size()) load(holder, true);
            });
            holder.cell.image.setOnClickListener(v -> {
                if (holder.bound >= 0 && holder.bound < pages.size()) openZoomed(pages.get(holder.bound));
            });
            load(holder, false);
        }

        private void load(Holder holder, boolean retry) {
            if (holder.target != null) Glide.with(ShinigamiReaderActivity.this).clear(holder.target);
            holder.cell.showLoading();
            int position = holder.bound;
            String url = offlineChapter ? "" : pages.get(position);
            File local = downloader.pageFile(chapterId, position);
            if (local == null && offlineChapter) {
                holder.cell.showFailure("Halaman " + (position + 1)
                        + " tidak tersedia lagi di penyimpanan. Unduh ulang chapter ini.");
                return;
            }
            Object model = local != null ? local : new ShinigamiImageRequest(url, source.config().siteBase());
            int width = Math.max(1, Math.min(1080, getResources().getDisplayMetrics().widthPixels));
            holder.target = new com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>(width, 4096) {
                @Override public void onResourceReady(android.graphics.Bitmap bitmap,
                        com.bumptech.glide.request.transition.Transition<? super android.graphics.Bitmap> transition) {
                    if (holder.bound != position || isDestroyed()) return;
                    holder.cell.showReady(bitmap);
                    android.util.Log.i("ShinigamiReader", "decoded page=" + (position + 1)
                            + "/" + pages.size() + " size=" + bitmap.getWidth() + "x" + bitmap.getHeight());
                }
                @Override public void onLoadFailed(Drawable drawable) {
                    if (holder.bound == position) holder.cell.showFailure("Gambar halaman " + (position + 1)
                            + " gagal dimuat. Periksa koneksi atau coba lagi.");
                }
                @Override public void onLoadCleared(Drawable drawable) { holder.cell.showLoading(); }
            };
            try {
                Glide.with(ShinigamiReaderActivity.this).asBitmap().load(model)
                        .disallowHardwareConfig().format(com.bumptech.glide.load.DecodeFormat.PREFER_RGB_565)
                        .downsample(com.bumptech.glide.load.resource.bitmap.DownsampleStrategy.AT_MOST)
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.DATA)
                        .skipMemoryCache(retry)
                        .listener(new RequestListener<android.graphics.Bitmap>() {
                            @Override public boolean onLoadFailed(GlideException failure, Object ignored,
                                    Target<android.graphics.Bitmap> target, boolean first) {
                                String reason = safeFailure(failure);
                                android.util.Log.w("ShinigamiReader", "image_failed page=" + (position + 1) + " " + reason);
                                if (holder.bound == position) holder.cell.showFailure("Halaman " + (position + 1) + ": " + reason);
                                return true;
                            }
                            @Override public boolean onResourceReady(android.graphics.Bitmap bitmap, Object ignored,
                                    Target<android.graphics.Bitmap> target, DataSource data, boolean first) { return false; }
                        }).into(holder.target);
            } catch (Exception failure) {
                holder.cell.showFailure("Pemuat gambar gagal. Coba muat ulang.");
                android.util.Log.w("ShinigamiReader", "loader_failed page=" + (position + 1) + " type=" + failure.getClass().getSimpleName());
            }
        }

        @Override public void onViewRecycled(Holder holder) {
            if (holder.target != null) Glide.with(ShinigamiReaderActivity.this).clear(holder.target);
            holder.target = null;
            holder.bound = -1;
            super.onViewRecycled(holder);
        }
        @Override public int getItemCount() { return pages.size(); }
    }

    static String safeFailure(GlideException failure) {
        if (failure != null) for (Throwable cause : failure.getRootCauses()) {
            String message = cause.getMessage();
            if (message != null && !message.contains("://") && !message.contains("Cookie")
                    && (message.startsWith("HTTP ") || message.startsWith("Gambar ")
                    || message.startsWith("Respons ") || message.startsWith("Redirect "))) return message;
        }
        return "Koneksi, waktu tunggu, atau decoding gambar gagal. Coba lagi.";
    }

    private void openZoomed(String url) {
        if (url == null || url.isEmpty()) return;
        Intent intent = new Intent(this, ShinigamiImageZoomActivity.class);
        intent.putExtra("url", url);
        intent.putExtra("chapter_id", chapterId);
        intent.putExtra("page", pages.indexOf(url));
        startActivity(intent);
    }
}
