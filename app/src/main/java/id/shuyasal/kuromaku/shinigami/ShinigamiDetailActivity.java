package id.shuyasal.kuromaku.shinigami;

import android.content.Intent;
import android.graphics.Color;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.RatioFrameLayout;
import id.shuyasal.kuromaku.ui.Ui;

public class ShinigamiDetailActivity extends AppCompatActivity {

    public static final String EXTRA_MANGA_ID = "manga_id";
    public static final String EXTRA_MANGA_TITLE = "manga_title";
    public static final String EXTRA_MANGA_COVER = "manga_cover";

    private static final int PAGE_SIZE = 24;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<ShinigamiChapter> chapters = new ArrayList<>();

    private Palette palette;
    private ShinigamiSource source;
    private ShinigamiStore store;
    private ShinigamiDownloader downloader;

    private String mangaId;
    private String title;
    private String coverUrl;

    private LinearLayout chapterList;
    private TextView synopsis;
    private TextView meta;
    private TextView errorText;
    private Button retryButton;
    private Button moreButton;
    private Button favoriteButton;
    private ProgressBar spinner;
    private ImageView cover;

    private int chapterPage = 1;
    private int chapterTotalPages = 1;
    private boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        palette = Palette.of(new Prefs(this).theme());
        source = createSource();
        store = new ShinigamiStore(this);
        downloader = new ShinigamiDownloader(this);

        mangaId = getIntent().getStringExtra(EXTRA_MANGA_ID);
        title = getIntent().getStringExtra(EXTRA_MANGA_TITLE);
        coverUrl = getIntent().getStringExtra(EXTRA_MANGA_COVER);
        if (mangaId == null || mangaId.isEmpty()) {
            Toast.makeText(this, "Identitas manga tidak ditemukan.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        buildUi();
        loadDetail();
        loadChapters(1);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);

        root.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 24));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);

        RatioFrameLayout box = new RatioFrameLayout(this);
        box.setRatio(0.68f);
        box.setTag("shinigami_detail_cover_box");
        cover = new ImageView(this);
        cover.setTag("shinigami_detail_cover");
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackgroundColor(palette.surface);
        box.addView(cover, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        head.addView(box, new LinearLayout.LayoutParams(Ui.dp(this, 110), Ui.dp(this, 162)));

        LinearLayout facts = new LinearLayout(this);
        facts.setOrientation(LinearLayout.VERTICAL);
        facts.setPadding(Ui.dp(this, 12), 0, 0, 0);

        TextView name = Ui.label(this, title == null ? "" : title, 18, palette.text);
        name.setTag("shinigami_detail_title");
        name.setMaxLines(3);
        facts.addView(name);

        meta = Ui.label(this, "", 13, palette.muted);
        meta.setTag("shinigami_detail_meta");
        meta.setPadding(0, Ui.dp(this, 6), 0, 0);
        facts.addView(meta);

        favoriteButton = Ui.chip(this, "Favorit", palette);
        favoriteButton.setTag("shinigami_favorite");
        favoriteButton.setOnClickListener(v -> toggleFavorite());
        LinearLayout.LayoutParams favoriteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        favoriteParams.topMargin = Ui.dp(this, 10);
        facts.addView(favoriteButton, favoriteParams);

        head.addView(facts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        body.addView(head);

        synopsis = Ui.label(this, "", 14, palette.text);
        synopsis.setTag("shinigami_detail_synopsis");
        synopsis.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 6));
        body.addView(synopsis);

        errorText = Ui.label(this, "", 13, Color.rgb(190, 60, 60));
        errorText.setTag("shinigami_detail_error");
        errorText.setVisibility(View.GONE);
        body.addView(errorText);

        retryButton = Ui.chip(this, "Coba lagi", palette);
        retryButton.setTag("shinigami_detail_retry");
        retryButton.setVisibility(View.GONE);
        retryButton.setOnClickListener(v -> {
            loadDetail();
            loadChapters(1);
        });
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        retryParams.bottomMargin = Ui.dp(this, 10);
        body.addView(retryButton, retryParams);

        TextView chapterHeading = Ui.label(this, "Daftar chapter", 16, palette.text);
        chapterHeading.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 4));
        body.addView(chapterHeading);

        chapterList = new LinearLayout(this);
        chapterList.setOrientation(LinearLayout.VERTICAL);
        chapterList.setTag("shinigami_chapter_list");
        body.addView(chapterList);

        moreButton = Ui.chip(this, "Muat chapter berikutnya", palette);
        moreButton.setTag("shinigami_more_chapters");
        moreButton.setVisibility(View.GONE);
        moreButton.setOnClickListener(v -> loadChapters(chapterPage + 1));
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        moreParams.topMargin = Ui.dp(this, 12);
        body.addView(moreButton, moreParams);

        scroll.addView(body, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                            | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        updateFavoriteButton();
    }

    private LinearLayout buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        header.setBackgroundColor(palette.surface);

        Button back = Ui.backButton(this, palette);
        back.setTag("shinigami_back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, Ui.TOUCH_DP)));

        TextView heading = Ui.label(this, title == null ? "Detail manga" : title, 18, palette.text);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        heading.setPadding(Ui.dp(this, 10), 0, 0, 0);
        header.addView(heading, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        android.widget.ImageButton drawerButton = Ui.iconButton(this, id.shuyasal.kuromaku.R.drawable.ic_menu,
                "Buka menu Novel dan Manga", palette);
        drawerButton.setTag("section_menu");
        drawerButton.setOnClickListener(v -> showSectionDrawer());
        header.addView(drawerButton, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));
        return header;
    }

    private void loadDetail() {
        spinner = new ProgressBar(this);
        worker.execute(() -> {
            ShinigamiManga manga = null;
            String failure = null;
            try {
                manga = source.detail(mangaId);
            } catch (ShinigamiSource.SourceException error) {
                failure = error.getMessage();
            }
            final ShinigamiManga result = manga;
            final String message = failure;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (message != null) {
                    errorText.setText(message);
                    errorText.setVisibility(View.VISIBLE);
                    retryButton.setVisibility(View.VISIBLE);
                    return;
                }
                errorText.setVisibility(View.GONE);
                retryButton.setVisibility(View.GONE);
                if (result == null) return;
                title = result.title.isEmpty() ? title : result.title;
                if (!result.coverUrl.isEmpty()) coverUrl = result.coverUrl;
                store.setMangaMeta(mangaId, title, coverUrl);
                applyManga(result);
            });
        });
    }

    private void applyManga(ShinigamiManga manga) {
        StringBuilder facts = new StringBuilder();
        facts.append(manga.statusLabel());
        if (!manga.releaseYear.isEmpty()) {
            facts.append(" - ").append(manga.releaseYear);
        }
        if (manga.rating > 0) {
            facts.append(" - Rating ").append(manga.rating);
        }
        if (!manga.format.isEmpty()) {
            facts.append(" - ").append(manga.format);
        }
        if (!manga.authors.isEmpty()) {
            facts.append("\nAuthor: ").append(String.join(", ", manga.authors));
        }
        if (!manga.genres.isEmpty()) {
            facts.append("\nGenre: ").append(String.join(", ", manga.genres));
        }
        if (manga.latestChapterNumber > 0) {
            facts.append("\nChapter terbaru: ").append(ShinigamiManga.formatNumber(manga.latestChapterNumber));
        }
        facts.append("\nRilis chapter: ").append(ShinigamiDates.format(manga.latestChapterTimeMillis));
        facts.append("\nDiperbarui: ").append(ShinigamiDates.format(manga.updatedAtMillis));
        meta.setText(facts.toString());
        synopsis.setText(manga.description.isEmpty()
                ? "Sumber tidak memberikan sinopsis untuk judul ini."
                : manga.description);
        Glide.with(this).load(coverUrl).centerCrop().into(cover);
    }

    private void loadChapters(int targetPage) {
        if (loading) return;
        loading = true;
        worker.execute(() -> {
            ShinigamiSource.ChapterPage result = null;
            String failure = null;
            try {
                result = source.chapters(mangaId, targetPage);
            } catch (ShinigamiSource.SourceException error) {
                failure = error.getMessage();
            }
            final ShinigamiSource.ChapterPage page_result = result;
            final String message = failure;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                loading = false;
                if (message != null) {
                    errorText.setText(message);
                    errorText.setVisibility(View.VISIBLE);
                    retryButton.setVisibility(View.VISIBLE);
                    return;
                }
                if (page_result == null) return;
                errorText.setVisibility(View.GONE);
                retryButton.setVisibility(View.GONE);
                chapterPage = targetPage;
                chapterTotalPages = Math.max(1, page_result.totalPages);
                if (targetPage <= 1) chapters.clear();
                java.util.Set<String> known = new java.util.HashSet<>();
                for (ShinigamiChapter existing : chapters) known.add(existing.id);
                for (ShinigamiChapter item : page_result.items) {
                    if (known.add(item.id)) chapters.add(item);
                }
                renderChapters();
                moreButton.setVisibility(chapterPage < chapterTotalPages ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void renderChapters() {
        chapterList.removeAllViews();
        for (ShinigamiChapter chapter : chapters) {
            chapterList.addView(buildChapterRow(chapter));
        }
        if (chapters.isEmpty()) {
            TextView empty = Ui.label(this, "Sumber belum memuat chapter untuk judul ini.",
                    14, palette.muted);
            empty.setTag("shinigami_no_chapters");
            chapterList.addView(empty);
        }
    }

    private View buildChapterRow(ShinigamiChapter chapter) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        row.setBackground(Ui.rounded(palette.surface, palette.divider, 10, this));
        row.setTag("shinigami_chapter_" + chapter.id);

        TextView label = Ui.label(this, chapter.label() + "\n"
                + chapter.dateLabel(), 14, palette.text);
        label.setTag("shinigami_chapter_label");
        label.setMaxLines(4);
        label.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        row.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        int progress = store.progress(chapter.id);
        TextView state = Ui.label(this, progress > 0 ? progress + "%" : "", 12,
                progress > 0 ? palette.accent : palette.muted);
        state.setTag("shinigami_chapter_progress");
        state.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        row.addView(state);

        Button download = Ui.chip(this, downloader.storageLabel(chapter.id,
                store.expectedPages(chapter.id)), palette);
        download.setTag("shinigami_chapter_download");
        download.setAlpha(0.6f);
        download.setOnClickListener(v -> explainDownloadUnavailable());
        row.addView(download);

        row.setOnClickListener(v -> openChapter(chapter));
        return row;
    }

    private void openChapter(ShinigamiChapter chapter) {
        Intent intent = new Intent(this, ShinigamiReaderActivity.class);
        intent.putExtra(ShinigamiReaderActivity.EXTRA_MANGA_ID, mangaId);
        intent.putExtra(ShinigamiReaderActivity.EXTRA_MANGA_TITLE, title);
        intent.putExtra(ShinigamiReaderActivity.EXTRA_MANGA_COVER, coverUrl);
        intent.putExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_ID, chapter.id);
        intent.putExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_NUMBER, chapter.number);
        intent.putExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_TITLE, chapter.title);
        startActivity(intent);
    }

    static final String DOWNLOAD_UNAVAILABLE =
            "Unduhan offline Shinigami dinonaktifkan. Sumber belum menyatakan izin "
            + "pengambilan konten untuk reader pihak ketiga, jadi aplikasi tidak menyimpan "
            + "gambar chapter secara permanen. Chapter tetap dapat dibaca secara online.";

    private void explainDownloadUnavailable() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Unduhan tidak tersedia")
                .setMessage(DOWNLOAD_UNAVAILABLE)
                .setPositiveButton("Tutup", null)
                .show();
    }

    private void toggleFavorite() {
        boolean nowFavorite = store.toggleFavorite(mangaId);
        updateFavoriteButton();
        Toast.makeText(this, nowFavorite ? "Ditambahkan ke favorit." : "Dihapus dari favorit.",
                Toast.LENGTH_SHORT).show();
    }

    private void updateFavoriteButton() {
        if (favoriteButton == null) return;
        boolean favorite = store.isFavorite(mangaId);
        favoriteButton.setText(favorite ? "Hapus favorit" : "Favorit");
    }

    private void showSectionDrawer() {
        ShinigamiNavigation.showDrawer(this, palette, false);
    }

    File localChapterFile(String chapterId, int index) {
        return downloader.pageFile(chapterId, index);
    }

    protected ShinigamiSource createSource() {
        return new ShinigamiSource(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store != null && chapterList != null && !chapters.isEmpty()) renderChapters();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
