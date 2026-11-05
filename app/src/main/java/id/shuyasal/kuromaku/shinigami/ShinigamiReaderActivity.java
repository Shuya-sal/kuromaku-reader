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
    private boolean fullscreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        palette = Palette.of(new Prefs(this).theme());
        source = createSource();
        store = new ShinigamiStore(this);
        downloader = new ShinigamiDownloader(this);

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
        worker.execute(() -> {
            List<String> urls = new ArrayList<>();
            String failure = null;
            try {
                urls.addAll(source.chapterImages(chapterId).urls);
            } catch (ShinigamiSource.SourceException error) {
                failure = error.getMessage();
            }
            final String message = failure;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (message != null) {
                    statusText.setVisibility(View.GONE);
                    errorText.setText(message);
                    errorText.setVisibility(View.VISIBLE);
                    retryButton.setVisibility(View.VISIBLE);
                    return;
                }
                pages.clear();
                pages.addAll(urls);
                adapter.notifyDataSetChanged();
                statusText.setVisibility(View.GONE);
                if (pages.isEmpty()) {
                    errorText.setText("Chapter ini tidak memiliki gambar.");
                    errorText.setVisibility(View.VISIBLE);
                    return;
                }
                recordOpen();
                restorePosition();
            });
        });
    }

    private void recordOpen() {
        store.recordRead(mangaId, mangaTitle, mangaCover, chapterId, chapterNumber);
        store.setLastChapter(mangaId, chapterId, chapterNumber);
    }

    private void restorePosition() {
        int page = store.page(chapterId);
        if (page <= 0 || page >= pages.size()) return;
        list.scrollToPosition(page);
    }

    private void saveProgress() {
        if (pages.isEmpty()) return;
        LinearLayoutManager manager = (LinearLayoutManager) list.getLayoutManager();
        if (manager == null) return;
        int first = manager.findFirstVisibleItemPosition();
        if (first < 0) return;
        store.setPage(chapterId, first);
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
            final ImageView image;

            Holder(View item) {
                super(item);
                image = item.findViewWithTag("shinigami_page_image");
            }
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            ImageView image = new ImageView(ShinigamiReaderActivity.this);
            image.setTag("shinigami_page_image");
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackgroundColor(Color.BLACK);
            LinearLayout wrapper = new LinearLayout(ShinigamiReaderActivity.this);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.addView(image, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new Holder(wrapper);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            String url = pages.get(position);
            File local = downloader.pageFile(chapterId, position);
            holder.image.setImageDrawable(null);
            holder.image.setMinimumHeight(placeholderHeight());
            RequestListener<Drawable> listener = new RequestListener<Drawable>() {
                @Override
                public boolean onLoadFailed(GlideException error, Object model,
                                            Target<Drawable> target, boolean first) {
                    return false;
                }

                @Override
                public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target,
                                               DataSource dataSource, boolean first) {
                    holder.image.setMinimumHeight(0);
                    return false;
                }
            };
            if (local != null) {
                Glide.with(ShinigamiReaderActivity.this).load(local).listener(listener).into(holder.image);
            } else {
                Glide.with(ShinigamiReaderActivity.this).load(url).listener(listener).into(holder.image);
            }
            holder.image.setOnClickListener(v -> {
                int index = holder.getBindingAdapterPosition();
                if (index < 0 || index >= pages.size()) return;
                openZoomed(pages.get(index));
            });
        }

        @Override
        public int getItemCount() {
            return pages.size();
        }
    }

    private int placeholderHeight() {
        int height = list == null ? 0 : list.getHeight();
        return height > 0 ? height : getResources().getDisplayMetrics().heightPixels;
    }

    private void openZoomed(String url) {
        if (url == null || url.isEmpty()) return;
        Intent intent = new Intent(this, id.shuyasal.kuromaku.ImageViewerActivity.class);
        intent.putExtra(id.shuyasal.kuromaku.ImageViewerActivity.EXTRA_URL, url);
        intent.putExtra(id.shuyasal.kuromaku.ImageViewerActivity.EXTRA_CAPTION,
                ShinigamiManga.chapterLabel(chapterNumber, chapterTitle));
        startActivity(intent);
    }
}
