package id.shuyasal.kuromaku.komiku;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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

import id.shuyasal.kuromaku.sync.SyncBar;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.ThemeManager;
import id.shuyasal.kuromaku.ui.Ui;

/**
 * Detail judul Manga Server 3: metadata, sinopsis, dan daftar chapter.
 *
 * <p>Daftar chapter default terbaru → terlama, bisa dibalik, dan bisa dicari.
 * Urutan berlaku untuk seluruh daftar, bukan hanya halaman yang sedang tampil.
 */
public class KomikuDetailActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "komiku_url";
    public static final String EXTRA_TITLE = "komiku_title";
    public static final String EXTRA_COVER = "komiku_cover";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private Palette palette;
    private KomikuSource source;
    private KomikuStore store;
    private KomikuDetail detail;
    private boolean newestFirst = true;
    private String chapterQuery = "";

    private TextView titleView;
    private TextView metaView;
    private TextView synopsisView;
    private TextView statusView;
    private ImageView coverView;
    private LinearLayout chapterList;
    private LinearLayout syncHolder;
    private SyncBar syncBar;
    private id.shuyasal.kuromaku.sync.SyncBridge bridge;
    private Button orderButton;
    private Button favoriteButton;
    private ProgressBar spinner;
    private String url;
    private String fallbackTitle;
    private String fallbackCover;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeManager.applyTheme(this);
        palette = Palette.of(new Prefs(this).resolvedTheme());
        source = new KomikuSource();
        store = new KomikuStore(this);
        bridge = new id.shuyasal.kuromaku.sync.SyncBridge(this);
        url = getIntent().getStringExtra(EXTRA_URL);
        fallbackTitle = getIntent().getStringExtra(EXTRA_TITLE);
        fallbackCover = getIntent().getStringExtra(EXTRA_COVER);
        buildUi();
        load();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 4));
        Button back = Ui.backButton(this, palette);
        back.setTag("komiku_detail_back");
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        titleView = Ui.label(this, fallbackTitle == null ? "Detail" : fallbackTitle, 17,
                palette.text);
        titleView.setTag("komiku_detail_title");
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleView.setPadding(Ui.dp(this, 8), 0, 0, 0);
        bar.addView(titleView, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        favoriteButton = Ui.chip(this, "Favorit", palette);
        favoriteButton.setTag("komiku_favorite");
        favoriteButton.setOnClickListener(v -> toggleFavorite());
        bar.addView(favoriteButton);
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 24));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        coverView = new ImageView(this);
        coverView.setTag("komiku_detail_cover");
        coverView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverView.setBackgroundColor(palette.surface);
        header.addView(coverView, new LinearLayout.LayoutParams(Ui.dp(this, 110),
                Ui.dp(this, 155)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(Ui.dp(this, 12), 0, 0, 0);
        metaView = Ui.label(this, "", 12, palette.muted);
        metaView.setTag("komiku_detail_meta");
        info.addView(metaView);
        statusView = Ui.label(this, "Memuat detail…", 12, palette.muted);
        statusView.setTag("komiku_detail_status");
        statusView.setPadding(0, Ui.dp(this, 6), 0, 0);
        info.addView(statusView);
        header.addView(info, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(header);

        synopsisView = Ui.label(this, "", 13, palette.text);
        synopsisView.setTag("komiku_detail_synopsis");
        synopsisView.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        content.addView(synopsisView);

        syncHolder = new LinearLayout(this);
        syncHolder.setOrientation(LinearLayout.VERTICAL);
        syncHolder.setTag("komiku_detail_sync_holder");
        LinearLayout.LayoutParams syncParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        syncParams.bottomMargin = Ui.dp(this, 12);
        content.addView(syncHolder, syncParams);

        LinearLayout chapterBar = new LinearLayout(this);
        chapterBar.setOrientation(LinearLayout.HORIZONTAL);
        chapterBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView chapterTitle = Ui.label(this, "Daftar Chapter", 15, palette.text);
        chapterTitle.setTypeface(chapterTitle.getTypeface(), android.graphics.Typeface.BOLD);
        chapterBar.addView(chapterTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        orderButton = Ui.chip(this, "Terbaru → Terlama", palette);
        orderButton.setTag("komiku_order");
        orderButton.setOnClickListener(v -> {
            newestFirst = !newestFirst;
            orderButton.setText(newestFirst ? "Terbaru → Terlama" : "Terlama → Terbaru");
            renderChapters();
        });
        chapterBar.addView(orderButton);
        content.addView(chapterBar);

        EditText chapterSearch = new EditText(this);
        chapterSearch.setHint("Cari chapter");
        chapterSearch.setSingleLine(true);
        chapterSearch.setTag("komiku_chapter_search");
        chapterSearch.setTextColor(palette.text);
        chapterSearch.setHintTextColor(palette.muted);
        chapterSearch.setBackground(Ui.rounded(palette.surface, palette.divider, 14, this));
        chapterSearch.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        chapterSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                chapterQuery = s.toString().trim().toLowerCase(java.util.Locale.ROOT);
                renderChapters();
            }

            @Override public void afterTextChanged(Editable s) {
            }
        });
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.topMargin = Ui.dp(this, 8);
        content.addView(chapterSearch, searchParams);

        chapterList = new LinearLayout(this);
        chapterList.setOrientation(LinearLayout.VERTICAL);
        chapterList.setTag("komiku_chapters");
        content.addView(chapterList);

        spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        content.addView(spinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL));

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private void load() {
        if (url == null || url.isEmpty()) {
            statusView.setText("Alamat judul tidak tersedia.");
            spinner.setVisibility(View.GONE);
            return;
        }
        worker.execute(() -> {
            KomikuDetail loaded = null;
            String error = null;
            try {
                loaded = source.detail(url);
            } catch (Throwable failure) {
                error = failure.getMessage();
            }
            final KomikuDetail result = loaded;
            final String message = error;
            main.post(() -> {
                if (isFinishing()) return;
                spinner.setVisibility(View.GONE);
                if (result == null) {
                    statusView.setText("Gagal memuat detail: " + message);
                    return;
                }
                detail = result;
                bind();
            });
        });
    }

    private void bind() {
        titleView.setText(detail.title);
        metaView.setText(detail.meta());
        String alternative = detail.alternative.isEmpty() ? "" : "Alternatif: " + detail.alternative;
        String genres = detail.genres.isEmpty() ? "" : "Genre: " + String.join(", ", detail.genres);
        statusView.setText(KomikuManga.join(java.util.Arrays.asList(alternative, genres)));
        synopsisView.setText(detail.synopsis);
        if (!detail.coverUrl.isEmpty()) KomikuImages.load(detail.coverUrl, coverView);
        else if (fallbackCover != null) KomikuImages.load(fallbackCover, coverView);
        favoriteButton.setText(store.isFavorite(detail.id) ? "★ Favorit" : "Favorit");
        attachSyncBar();
        renderChapters();
    }

    /** Bar tautan ke Manga Server 2 untuk judul ini. */
    private void attachSyncBar() {
        if (syncHolder == null || detail == null) return;
        syncHolder.removeAllViews();
        syncBar = new SyncBar(this, palette, new SyncBar.Self(
                SyncBar.SOURCE_KOMIKU, detail.id, detail.title, detail.alternative, url))
                .onChanged(() -> { if (syncBar != null) syncBar.refresh(syncHolder.getChildAt(0)); });
        syncHolder.addView(syncBar.build());
    }

    private void toggleFavorite() {
        if (detail == null) return;
        boolean now = store.toggleFavorite(detail.id, detail.title, detail.coverUrl, url);
        favoriteButton.setText(now ? "★ Favorit" : "Favorit");
    }

    private void renderChapters() {
        if (detail == null) return;
        chapterList.removeAllViews();
        View resume = resumeRow();
        if (resume != null) chapterList.addView(resume);
        List<KomikuChapter> ordered = newestFirst
                ? KomikuChapter.newestFirst(detail.chapters)
                : KomikuChapter.oldestFirst(detail.chapters);
        int shown = 0;
        for (KomikuChapter chapter : ordered) {
            if (!chapterQuery.isEmpty()
                    && !chapter.title.toLowerCase(java.util.Locale.ROOT).contains(chapterQuery)) {
                continue;
            }
            chapterList.addView(chapterRow(chapter));
            shown++;
        }
        if (shown == 0) {
            TextView empty = Ui.label(this, chapterQuery.isEmpty()
                    ? "Belum ada chapter." : "Tidak ada chapter yang cocok.", 13, palette.muted);
            empty.setPadding(0, Ui.dp(this, 10), 0, 0);
            chapterList.addView(empty);
        }
    }

    /**
     * Baris "Lanjutkan" dari chapter kanonis terakhir yang tersedia di sumber
     * ini. Tidak pernah mengarang alamat: bila tidak ada chapter setara,
     * baris ini tidak muncul.
     */
    private View resumeRow() {
        List<String> labels = new ArrayList<>();
        for (KomikuChapter chapter : KomikuChapter.oldestFirst(detail.chapters)) {
            labels.add(chapter.title);
        }
        String next = bridge.nextAfterLastCompleted(SyncBar.SOURCE_KOMIKU, detail.id, labels);
        if (next.isEmpty()) return null;
        KomikuChapter target = null;
        for (KomikuChapter chapter : detail.chapters) {
            if (chapter.title.equals(next)) target = chapter;
        }
        if (target == null) return null;

        final KomikuChapter chapter = target;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        row.setBackground(Ui.ripple(this, palette.surface, palette.accent, 12, palette.ripple));
        TextView text = Ui.label(this, "Lanjutkan · " + chapter.title, 14, palette.text);
        row.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = Ui.label(this, "›", 18, palette.accent);
        row.addView(arrow);
        row.setOnClickListener(v -> openChapter(chapter));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(this, 8);
        row.setLayoutParams(params);
        return row;
    }

    private View chapterRow(KomikuChapter chapter) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        row.setBackground(Ui.ripple(this, palette.surface, palette.divider, 12, palette.ripple));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.label(this, chapter.title, 14, palette.text);
        texts.addView(name);
        if (!chapter.date.isEmpty()) {
            TextView date = Ui.label(this, chapter.date, 11, palette.muted);
            texts.addView(date);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        double progress = store.progress(chapter.url);
        if (progress > 0.01) {
            TextView mark = Ui.label(this, Math.round(progress * 100) + "%", 11, palette.accent);
            row.addView(mark);
        } else if (bridge.isCompletedAnywhere(SyncBar.SOURCE_KOMIKU, detail.id, chapter.title)) {
            // Chapter yang sama sudah selesai dibaca di Manga Server 2.
            TextView mark = Ui.label(this, "✓ dibaca", 11, palette.accent);
            row.addView(mark);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(this, 6);
        row.setLayoutParams(params);
        row.setOnClickListener(v -> openChapter(chapter));
        return row;
    }

    private void openChapter(KomikuChapter chapter) {
        List<KomikuChapter> ordered = KomikuChapter.oldestFirst(detail.chapters);
        ArrayList<String> urls = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        for (KomikuChapter item : ordered) {
            urls.add(item.url);
            labels.add(item.title);
        }
        Intent intent = new Intent(this, KomikuReaderActivity.class);
        intent.putExtra(KomikuReaderActivity.EXTRA_CHAPTER_URL, chapter.url);
        intent.putExtra(KomikuReaderActivity.EXTRA_CHAPTER_LABEL, chapter.title);
        intent.putExtra(KomikuReaderActivity.EXTRA_MANGA_ID, detail.id);
        intent.putExtra(KomikuReaderActivity.EXTRA_MANGA_TITLE, detail.title);
        intent.putExtra(KomikuReaderActivity.EXTRA_COVER, detail.coverUrl);
        intent.putStringArrayListExtra(KomikuReaderActivity.EXTRA_CHAPTERS, urls);
        intent.putStringArrayListExtra(KomikuReaderActivity.EXTRA_LABELS, labels);
        startActivity(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        // Setelah kembali dari pembaca, status "selesai"/"Lanjutkan" harus
        // mengikuti data terbaru tanpa memuat ulang katalog.
        if (detail != null) renderChapters();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}