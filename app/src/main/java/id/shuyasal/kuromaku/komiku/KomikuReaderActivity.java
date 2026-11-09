package id.shuyasal.kuromaku.komiku;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.ThemeManager;
import id.shuyasal.kuromaku.ui.Ui;

/**
 * Reader native Manga Server 3: gambar chapter vertikal, tanpa elemen situs.
 *
 * <p>Ketuk sekali untuk menyembunyikan/menampilkan kontrol. Progres disimpan
 * per alamat chapter, dan riwayat judul ikut diperbarui saat halaman dibuka.
 */
public class KomikuReaderActivity extends AppCompatActivity {

    public static final String EXTRA_CHAPTER_URL = "komiku_chapter_url";
    public static final String EXTRA_CHAPTER_LABEL = "komiku_chapter_label";
    public static final String EXTRA_MANGA_ID = "komiku_manga_id";
    public static final String EXTRA_MANGA_TITLE = "komiku_manga_title";
    public static final String EXTRA_COVER = "komiku_reader_cover";
    public static final String EXTRA_CHAPTERS = "komiku_chapter_urls";
    public static final String EXTRA_LABELS = "komiku_chapter_labels";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private Palette palette;
    private KomikuSource source;
    private KomikuStore store;
    private ScrollView scroll;
    private LinearLayout pages;
    private LinearLayout overlay;
    private LinearLayout panel;
    private ProgressBar spinner;
    private TextView labelView;
    private TextView statusView;
    private TextView progressView;

    private String chapterUrl;
    private String chapterLabel;
    private String mangaId;
    private String mangaTitle;
    private String coverUrl;
    private ArrayList<String> chapterUrls = new ArrayList<>();
    private ArrayList<String> chapterLabels = new ArrayList<>();

    private int loadedCount;
    private int totalCount;
    private boolean chromeVisible = true;
    private boolean zoomed;
    private final List<ImageView> pageViews = new ArrayList<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeManager.applyTheme(this);
        palette = Palette.of(new Prefs(this).resolvedTheme());
        source = new KomikuSource();
        store = new KomikuStore(this);

        chapterUrl = getIntent().getStringExtra(EXTRA_CHAPTER_URL);
        chapterLabel = getIntent().getStringExtra(EXTRA_CHAPTER_LABEL);
        mangaId = getIntent().getStringExtra(EXTRA_MANGA_ID);
        mangaTitle = getIntent().getStringExtra(EXTRA_MANGA_TITLE);
        coverUrl = getIntent().getStringExtra(EXTRA_COVER);
        ArrayList<String> urls = getIntent().getStringArrayListExtra(EXTRA_CHAPTERS);
        ArrayList<String> labels = getIntent().getStringArrayListExtra(EXTRA_LABELS);
        if (urls != null) chapterUrls = urls;
        if (labels != null) chapterLabels = labels;

        buildUi();
        loadPages();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        pages = new LinearLayout(this);
        pages.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(pages, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final GestureDetector taps = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override public boolean onSingleTapConfirmed(MotionEvent event) {
                        toggleChrome();
                        return true;
                    }
                });
        scroll.setOnTouchListener((view, event) -> {
            taps.onTouchEvent(event);
            return false;
        });
        scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> updateProgress());
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(0xE6000000);
        top.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        Button back = Ui.backButton(this, palette);
        back.setTag("komiku_reader_back");
        back.setOnClickListener(v -> finish());
        top.addView(back);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(Ui.dp(this, 8), 0, 0, 0);
        TextView manga = Ui.label(this, mangaTitle == null ? "" : mangaTitle, 14, 0xFFFFFFFF);
        manga.setSingleLine(true);
        manga.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(manga);
        labelView = Ui.label(this, chapterLabel == null ? "" : chapterLabel, 12, 0xFFB0B0B0);
        titles.addView(labelView);
        top.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        overlay.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View spacer = new View(this);
        overlay.addView(spacer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xE6000000);
        panel.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 12));

        progressView = Ui.label(this, "0%", 11, 0xFFB0B0B0);
        panel.addView(progressView);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgress(0);
        bar.setTag("komiku_read_progress");
        panel.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 6)));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(0, Ui.dp(this, 8), 0, 0);
        Button prev = Ui.chip(this, "‹ Sebelumnya", palette);
        prev.setTag("komiku_prev");
        prev.setOnClickListener(v -> step(-1));
        nav.addView(prev, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button next = Ui.chip(this, "Berikutnya ›", palette);
        next.setTag("komiku_next");
        next.setOnClickListener(v -> step(1));
        nav.addView(next, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        panel.addView(nav);

        statusView = Ui.label(this, "Memuat halaman…", 11, 0xFFB0B0B0);
        statusView.setPadding(0, Ui.dp(this, 6), 0, 0);
        panel.addView(statusView);
        overlay.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        root.addView(spinner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        setContentView(root);
    }

    private void toggleChrome() {
        chromeVisible = !chromeVisible;
        overlay.animate().alpha(chromeVisible ? 1f : 0f).setDuration(160)
                .withEndAction(() -> overlay.setVisibility(chromeVisible ? View.VISIBLE
                        : View.INVISIBLE)).start();
    }

    private void loadPages() {
        if (chapterUrl == null || chapterUrl.isEmpty()) {
            spinner.setVisibility(View.GONE);
            statusView.setText("Alamat chapter tidak tersedia.");
            return;
        }
        worker.execute(() -> {
            List<String> images = new ArrayList<>();
            String error = null;
            try {
                images = source.images(chapterUrl);
            } catch (Throwable failure) {
                error = failure.getMessage();
            }
            final List<String> result = images;
            final String message = error;
            main.post(() -> {
                if (isFinishing()) return;
                spinner.setVisibility(View.GONE);
                if (message != null) {
                    statusView.setText("Gagal memuat chapter: " + message);
                    return;
                }
                if (result.isEmpty()) {
                    statusView.setText("Tidak ada gambar yang bisa dimuat dari chapter ini.");
                    return;
                }
                bindPages(result);
            });
        });
    }

    private void bindPages(List<String> images) {
        pages.removeAllViews();
        pageViews.clear();
        totalCount = images.size();
        loadedCount = 0;
        statusView.setText("0/" + totalCount + " halaman");

        int width = getResources().getDisplayMetrics().widthPixels;
        for (int i = 0; i < images.size(); i++) {
            final String url = images.get(i);
            ImageView view = new ImageView(this);
            view.setAdjustViewBounds(true);
            view.setScaleType(ImageView.ScaleType.FIT_CENTER);
            view.setBackgroundColor(0xFF101010);
            pages.addView(view, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            pageViews.add(view);
            KomikuImages.fetch(url, width, new KomikuImages.Callback() {
                @Override public void onBitmap(Bitmap bitmap) {
                    view.setImageBitmap(bitmap);
                    loadedCount++;
                    statusView.setText(loadedCount + "/" + totalCount + " halaman");
                    if (firstRestored) applyRestoredScroll();
                    if (loadedCount == 1 && firstRestored) applyRestoredScroll();
                }

                @Override public void onFailed() {
                    loadedCount++;
                    statusView.setText("Sebagian gambar gagal dimuat ("
                            + loadedCount + "/" + totalCount + ")");
                }
            });
        }
        recordHistory();
        restoreScroll();
        updateProgress();
    }

    private boolean firstRestored;
    private float restoredFraction;

    private void restoreScroll() {
        restoredFraction = (float) store.progress(chapterUrl);
        firstRestored = restoredFraction > 0.01;
        if (!firstRestored) return;
        scroll.post(() -> {
            int height = scroll.getChildAt(0) == null ? 0 : scroll.getChildAt(0).getHeight();
            int viewport = scroll.getHeight();
            if (height > viewport) {
                scroll.scrollTo(0, (int) (restoredFraction * (height - viewport)));
            }
        });
    }

    private void applyRestoredScroll() {
        if (!firstRestored) return;
        firstRestored = false;
        scroll.post(() -> {
            int height = scroll.getChildAt(0) == null ? 0 : scroll.getChildAt(0).getHeight();
            int viewport = scroll.getHeight();
            if (height > viewport) {
                scroll.scrollTo(0, (int) (restoredFraction * (height - viewport)));
            }
        });
    }

    private void updateProgress() {
        if (pages == null || scroll == null) return;
        int height = pages.getHeight();
        int viewport = scroll.getHeight();
        float fraction = 0;
        if (height > viewport) {
            fraction = Math.min(1f, Math.max(0f, scroll.getScrollY() / (float) (height - viewport)));
        }
        progressView.setText(Math.round(fraction * 100) + "%");
        store.setProgress(chapterUrl, fraction);
        View bar = panel.findViewWithTag("komiku_read_progress");
        if (bar instanceof ProgressBar) ((ProgressBar) bar).setProgress((int) (fraction * 1000));
    }

    private void recordHistory() {
        if (mangaId == null) return;
        store.recordChapter(mangaId, mangaTitle, coverUrl, chapterUrl, chapterLabel,
                KomikuManga.chapterNumber(chapterLabel), store.progress(chapterUrl));
    }

    private int indexOf() {
        return chapterUrls.indexOf(chapterUrl);
    }

    private void step(int delta) {
        int index = indexOf();
        if (index < 0) return;
        int next = index + delta;
        if (next < 0 || next >= chapterUrls.size()) {
            statusView.setText(delta < 0 ? "Ini chapter pertama." : "Ini chapter terakhir.");
            return;
        }
        chapterUrl = chapterUrls.get(next);
        chapterLabel = next < chapterLabels.size() ? chapterLabels.get(next) : ("Chapter " + (next + 1));
        labelView.setText(chapterLabel);
        pages.removeAllViews();
        pageViews.clear();
        spinner.setVisibility(View.VISIBLE);
        scroll.scrollTo(0, 0);
        loadPages();
    }

    @Override protected void onPause() {
        updateProgress();
        recordHistory();
        super.onPause();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}