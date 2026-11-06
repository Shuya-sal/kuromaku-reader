package id.shuyasal.kuromaku.shinigami;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.RatioFrameLayout;
import id.shuyasal.kuromaku.ui.ThemeManager;
import id.shuyasal.kuromaku.ui.Ui;

public class ShinigamiDetailActivity extends AppCompatActivity {

    public static final String EXTRA_MANGA_ID = "manga_id";
    public static final String EXTRA_MANGA_TITLE = "manga_title";
    public static final String EXTRA_MANGA_COVER = "manga_cover";

    private static final int PAGE_SIZE = 24;
    /**
     * Batas halaman chapter yang diambil berurutan untuk membentuk indeks penuh.
     * Sumber membatasi page_size ke 500, jadi 20 halaman menampung 10.000 chapter
     * — jauh di atas judul terpanjang yang tersedia saat ini.
     */
    private static final int MAX_INDEX_PAGES = 20;
    /** Batas halaman yang diambil bertahap saat pencarian butuh indeks penuh. */
    private static final int MAX_DEEP_SEARCH_PAGES = 12;
    /** Batas baris hasil pencarian yang dirender agar daftar tetap ringan. */
    private static final int MAX_SEARCH_ROWS = 200;
    /** Jeda sebelum indeks chapter tambahan diambil saat pengguna mengetik. */
    private static final long SEARCH_DEBOUNCE_MS = 400L;

    // Prioritas pengurutan chapter: Terbaru -> Terlama (default) / Terlama -> Terbaru.
    public static final int SORT_LATEST = 0;
    public static final int SORT_OLDEST = 1;
    private int chapterSortOrder = SORT_LATEST;
    private int activeResolvedTheme = -1;

    public static final Comparator<ShinigamiChapter> COMPARATOR_ASC = (a, b) -> {
        if (a == b) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        int cmp = Double.compare(a.number, b.number);
        if (cmp != 0) return cmp;
        String at = a.title == null ? "" : a.title.toLowerCase(Locale.ROOT);
        String bt = b.title == null ? "" : b.title.toLowerCase(Locale.ROOT);
        if (at.contains("prolog") && !bt.contains("prolog")) return -1;
        if (!at.contains("prolog") && bt.contains("prolog")) return 1;
        if (at.contains("epilog") && !bt.contains("epilog")) return 1;
        if (!at.contains("epilog") && bt.contains("epilog")) return -1;
        return a.id.compareTo(b.id);
    };

    public static final Comparator<ShinigamiChapter> COMPARATOR_DESC = (a, b) -> COMPARATOR_ASC.compare(b, a);

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable deepSearchTask = this::ensureFullIndexForSearch;
    private final List<ShinigamiChapter> chapters = new ArrayList<>();
    private final Set<String> knownChapterIds = new HashSet<>();
    private int chapterTotalRecord = 0;
    private int chapterRequestGeneration = 0;
    private boolean loadingMorePages = false;
    private String currentSearchQuery = "";
    private android.widget.ScrollView detailScroll;
    private TextView chapterHeadingView;

    private Palette palette;
    private ShinigamiSource source;
    private ShinigamiManga lastDetail;
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
    private TextView sortLabel;
    private EditText searchView;
    private android.widget.ImageButton sortButton;

    private int chapterPage = 1;
    private int chapterTotalPages = 1;
    private boolean loading;

    private final Set<String> selected = new LinkedHashSet<>();
    private LinearLayout selectionBar;
    private TextView selectionCount;
    private Button selectionDownload;

    /**
     * State yang dibawa melewati pembuatan ulang Activity (misalnya saat tema
     * diganti). Tanpa ini, pergantian tema akan memicu fetch ulang detail dan
     * chapter sekaligus mengembalikan posisi scroll ke atas.
     */
    static final class RetainedState {
        final ShinigamiManga detail;
        final List<ShinigamiChapter> chapters;
        final int page;
        final int totalPages;
        final int totalRecord;
        final int sortOrder;
        final String query;
        final Set<String> selected;
        final int scrollY;

        RetainedState(ShinigamiManga detail, List<ShinigamiChapter> chapters,
                      int page, int totalPages, int totalRecord, int sortOrder,
                      String query, Set<String> selected, int scrollY) {
            this.detail = detail;
            this.chapters = chapters;
            this.page = page;
            this.totalPages = totalPages;
            this.totalRecord = totalRecord;
            this.sortOrder = sortOrder;
            this.query = query;
            this.selected = selected;
            this.scrollY = scrollY;
        }
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return captureRetainedState();
    }

    private RetainedState captureRetainedState() {
        int scrollY = detailScroll == null ? 0 : detailScroll.getScrollY();
        return new RetainedState(lastDetail, new ArrayList<>(chapters),
                chapterPage, chapterTotalPages, chapterTotalRecord, chapterSortOrder,
                currentSearchQuery, new LinkedHashSet<>(selected), scrollY);
    }

    private void applyThemeSelection() {
        recreate();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.applyTheme(this);
        activeResolvedTheme = new Prefs(this).resolvedTheme();
        palette = Palette.of(activeResolvedTheme);
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

        RetainedState retained = (RetainedState) getLastCustomNonConfigurationInstance();
        if (retained != null) {
            chapterSortOrder = retained.sortOrder;
        } else {
            chapterSortOrder = new Prefs(this).chapterSortOrder();
            if (chapterSortOrder != SORT_LATEST && chapterSortOrder != SORT_OLDEST) {
                chapterSortOrder = SORT_LATEST;
            }
        }

        buildUi();
        ShinigamiDownloadStatus.addListener(downloadStateListener);

        if (retained != null) {
            restoreRetainedState(retained);
            return;
        }

        loadDetail();
        loadChapters(1);
    }

    /** Kembalikan tampilan tanpa menyentuh jaringan setelah Activity dibuat ulang. */
    private void restoreRetainedState(RetainedState retained) {
        lastDetail = retained.detail;
        if (lastDetail != null) {
            title = lastDetail.title.isEmpty() ? title : lastDetail.title;
            if (!lastDetail.coverUrl.isEmpty()) coverUrl = lastDetail.coverUrl;
            applyManga(lastDetail);
        }
        chapters.clear();
        chapters.addAll(retained.chapters);
        knownChapterIds.clear();
        for (ShinigamiChapter item : chapters) knownChapterIds.add(item.id);
        chapterPage = retained.page;
        chapterTotalPages = retained.totalPages;
        chapterTotalRecord = retained.totalRecord;
        selected.clear();
        selected.addAll(retained.selected);
        currentSearchQuery = retained.query == null ? "" : retained.query;
        if (searchView != null && !currentSearchQuery.isEmpty()) {
            searchView.setText(currentSearchQuery);
            searchView.setSelection(searchView.getText().length());
        }
        updateChapterHeading();
        renderChapters();
        moreButton.setVisibility(chapterPage < chapterTotalPages ? View.VISIBLE : View.GONE);
        final int restoreY = retained.scrollY;
        detailScroll.post(() -> detailScroll.scrollTo(0, restoreY));
    }

    /**
     * Perubahan status unduhan hanya memperbarui baris yang bersangkutan.
     * Membangun ulang seluruh daftar akan membuat ring selalu mulai dari nol,
     * sehingga animasinya tidak pernah terlihat.
     */
    private final ShinigamiDownloadStatus.Listener downloadStateListener =
            () -> main.post(this::refreshDownloadStates);
    private final Map<String, ChapterRow> rows = new HashMap<>();

    private static final class ChapterRow {
        final ShinigamiChapter chapter;
        final ShinigamiDownloadRing ring;
        final TextView status;
        final Button action;

        ChapterRow(ShinigamiChapter chapter, ShinigamiDownloadRing ring,
                   TextView status, Button action) {
            this.chapter = chapter;
            this.ring = ring;
            this.status = status;
            this.action = action;
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);
        chapterSortOrder = new Prefs(this).chapterSortOrder();

        root.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setTag("shinigami_detail_scroll");
        scroll.setFillViewport(true);
        detailScroll = scroll;
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
        chapterHeadingView = chapterHeading;
        body.addView(chapterHeading);

        // Baris pengurutan: label status urutan + tombol ganti urutan di kanan.
        LinearLayout sortRow = new LinearLayout(this);
        sortRow.setOrientation(LinearLayout.HORIZONTAL);
        sortRow.setGravity(Gravity.CENTER_VERTICAL);
        sortRow.setPadding(0, 0, 0, Ui.dp(this, 4));
        sortRow.setTag("shinigami_sort_row");

        // Kolom pencarian
        searchView = new EditText(this);
        searchView.setHint("Cari Chapter, Contoh: 69 atau 76");
        searchView.setHintTextColor(palette.muted);
        searchView.setTextColor(palette.text);
        searchView.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        searchView.setBackground(Ui.rounded(palette.surface, palette.divider, 16, this));
        searchView.setTag("shinigami_search_view");
        searchView.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        // Tambahkan ikon pencarian di kiri
        Drawable searchIcon = ContextCompat.getDrawable(ShinigamiDetailActivity.this, id.shuyasal.kuromaku.R.drawable.ic_search);
        searchView.setCompoundDrawablesWithIntrinsicBounds(searchIcon, null, null, null);
        searchView.setCompoundDrawablePadding(Ui.dp(this, 8));
        searchView.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                currentSearchQuery = s == null ? "" : s.toString().trim();
                renderChapters();
                // Pencarian tidak boleh berhenti pada halaman yang sudah dimuat:
                // ambil sisa halaman dari sumber agar chapter lama tetap
                // ditemukan. Ditunda sebentar supaya tiap ketikan tidak
                // melepaskan rangkaian permintaan baru ke sumber.
                main.removeCallbacks(deepSearchTask);
                if (!currentSearchQuery.isEmpty()) {
                    main.postDelayed(deepSearchTask, SEARCH_DEBOUNCE_MS);
                }
            }
        });
        sortRow.addView(searchView, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        // Tombol urut
        sortButton = Ui.iconButton(this, id.shuyasal.kuromaku.R.drawable.ic_sort,
                chapterSortOrder == SORT_LATEST ? "Urutkan dari terbaru" : "Urutkan dari terlama", palette);
        sortButton.setTag("shinigami_sort_button");
        sortButton.setOnClickListener(v -> toggleSortOrder());
        sortRow.addView(sortButton, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));

        body.addView(sortRow);

        chapterList = new LinearLayout(this);
        chapterList.setOrientation(LinearLayout.VERTICAL);
        chapterList.setTag("shinigami_chapter_list");
        body.addView(chapterList);

        body.addView(buildSelectionBar());

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
        lastDetail = manga;
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
        loadChapters(targetPage, false);
    }

    /**
     * @param force true untuk memaksa permintaan baru walau permintaan lain
     *              masih berjalan. Dipakai saat arah pengurutan berubah: daftar
     *              sudah dikosongkan, jadi menolak memuat ulang akan
     *              meninggalkan layar kosong dan urutan lama tetap terpakai.
     *              Permintaan lama otomatis diabaikan lewat nomor generasi.
     */
    private void loadChapters(int targetPage, boolean force) {
        if (loading && !force) return;
        loading = true;
        final int gen = ++chapterRequestGeneration;
        final String order = chapterSortOrder == SORT_LATEST ? "desc" : "asc";
        final int firstPage = Math.max(1, targetPage);
        worker.execute(() -> {
            // Indeks chapter harus lengkap, bukan satu halaman. Sumber membatasi
            // page_size (maksimum 500), jadi judul dengan 579 chapter mengembalikan
            // halaman 1 berisi chapter 575..80 pada urutan desc; tanpa perulangan
            // halaman chapter 1..79 tidak pernah masuk dan "Terlama" berhenti di
            // tengah daftar. Perulangan di bawah mengambil seluruh halaman sampai
            // total_record terpenuhi, lalu pengurutan dilakukan pada data penuh.
            List<ShinigamiChapter> collected = new ArrayList<>();
            int loadedThrough = firstPage - 1;
            int totalPages = 1;
            int totalRecord = 0;
            String failure = null;
            try {
                for (int page = firstPage; page <= firstPage + MAX_INDEX_PAGES - 1; page++) {
                    ShinigamiSource.ChapterPage result = source.chapters(mangaId, page, order);
                    totalPages = Math.max(1, result.totalPages);
                    totalRecord = Math.max(totalRecord, result.totalRecord);
                    collected.addAll(result.items);
                    loadedThrough = page;
                    if (page >= totalPages) break;
                    if (totalRecord > 0 && collected.size() >= totalRecord) break;
                }
            } catch (ShinigamiSource.SourceException error) {
                failure = error.getMessage();
            }
            final List<ShinigamiChapter> fetched = collected;
            final int reachedPage = loadedThrough;
            final int pages = totalPages;
            final int record = totalRecord;
            final String message = failure;
            final ShinigamiDownloads.Manga offline = fetched.isEmpty()
                    ? ShinigamiDownloads.forManga(this, mangaId) : null;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (gen != chapterRequestGeneration) return;
                loading = false;
                if (message != null && fetched.isEmpty()) {
                    if (offline != null && !offline.chapters.isEmpty()
                            && renderOfflineChapters(offline)) return;
                    errorText.setText(message);
                    errorText.setVisibility(View.VISIBLE);
                    retryButton.setVisibility(View.VISIBLE);
                    return;
                }
                errorText.setVisibility(View.GONE);
                retryButton.setVisibility(View.GONE);
                chapters.clear();
                knownChapterIds.clear();
                for (ShinigamiChapter item : fetched) {
                    if (knownChapterIds.add(item.id)) chapters.add(item);
                }
                chapterPage = Math.max(1, reachedPage);
                chapterTotalPages = Math.max(pages, chapterPage);
                chapterTotalRecord = Math.max(record, chapters.size());
                updateChapterHeading();
                renderChapters();
                moreButton.setVisibility(View.GONE);
                if (message != null) {
                    Toast.makeText(ShinigamiDetailActivity.this,
                            "Sebagian chapter gagal dimuat: " + message,
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    /**
     * Ambil sisa halaman chapter dari sumber ketika pengguna mencari. Dengan
     * begitu pencarian "1" pada manga 1000+ chapter tetap menemukan chapter
     * paling awal walau halaman tersebut belum pernah dimuat.
     */
    private void ensureFullIndexForSearch() {
        if (loadingMorePages || chapterPage >= chapterTotalPages) return;
        if (chapters.size() >= chapterTotalRecord && chapterTotalRecord > 0) return;
        loadingMorePages = true;
        final int gen = chapterRequestGeneration;
        final int start = chapterPage + 1;
        final int last = Math.min(chapterTotalPages, start + MAX_DEEP_SEARCH_PAGES - 1);
        final String order = chapterSortOrder == SORT_LATEST ? "desc" : "asc";
        worker.execute(() -> {
            List<ShinigamiChapter> collected = new ArrayList<>();
            String failure = null;
            int reached = chapterPage;
            for (int page = start; page <= last; page++) {
                try {
                    ShinigamiSource.ChapterPage result = source.chapters(mangaId, page, order);
                    collected.addAll(result.items);
                    reached = page;
                    if (page >= result.totalPages) break;
                } catch (ShinigamiSource.SourceException error) {
                    failure = error.getMessage();
                    break;
                }
            }
            final List<ShinigamiChapter> extra = collected;
            final int loadedThrough = reached;
            final String message = failure;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                loadingMorePages = false;
                if (gen != chapterRequestGeneration) return;
                for (ShinigamiChapter item : extra) {
                    if (knownChapterIds.add(item.id)) chapters.add(item);
                }
                chapterPage = Math.max(chapterPage, loadedThrough);
                updateChapterHeading();
                renderChapters();
                moreButton.setVisibility(chapterPage < chapterTotalPages ? View.VISIBLE : View.GONE);
                if (message != null && !currentSearchQuery.isEmpty()) {
                    Toast.makeText(this, "Sebagian chapter gagal dimuat: " + message,
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void updateChapterHeading() {
        if (chapterHeadingView == null) return;
        if (chapterTotalRecord > 0) {
            chapterHeadingView.setText("Daftar chapter (" + chapterTotalRecord + ")");
        } else if (!chapters.isEmpty()) {
            chapterHeadingView.setText("Daftar chapter (" + chapters.size() + ")");
        } else {
            chapterHeadingView.setText("Daftar chapter");
        }
    }

    /**
     * Tampilkan chapter yang tersimpan saat katalog tidak dapat dihubungi.
     * Mengembalikan {@code true} bila ada yang bisa ditampilkan.
     */
    private boolean renderOfflineChapters(ShinigamiDownloads.Manga offline) {
        chapters.clear();
        knownChapterIds.clear();
        for (ShinigamiDownloads.Chapter saved : offline.chapters) {
            ShinigamiChapter ch = new ShinigamiChapter(saved.chapterId, mangaId, saved.number,
                    saved.label.replaceFirst("(?i)^chapter\\s*[\\d.,]+\\s*[-–:]?\\s*", ""),
                    "", "");
            if (knownChapterIds.add(ch.id)) chapters.add(ch);
        }
        if (chapters.isEmpty()) return false;
        errorText.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        moreButton.setVisibility(View.GONE);
        updateChapterHeading();
        renderChapters();

        TextView notice = Ui.label(this,
                "Mode offline: hanya chapter yang tersimpan ditampilkan ("
                        + chapters.size() + "). Katalog sumber tidak dapat dihubungi.",
                12, palette.accent);
        notice.setTag("shinigami_offline_chapters_notice");
        notice.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 2));
        if (chapterList != null) chapterList.addView(notice, 0);
        return true;
    }

    private void renderChapters() {
        if (chapterList == null) return;
        chapterList.removeAllViews();
        rows.clear();
        List<ShinigamiChapter> ordered = sortedChapters();
        boolean truncated = false;
        if (!currentSearchQuery.isEmpty()) {
            List<ShinigamiChapter> filtered = new ArrayList<>();
            String queryLower = currentSearchQuery.toLowerCase(Locale.ROOT);
            for (ShinigamiChapter chapter : ordered) {
                String numStr = ShinigamiManga.formatNumber(chapter.number);
                if (numStr.equals(currentSearchQuery)
                        || String.valueOf(chapter.number).contains(currentSearchQuery)
                        || chapter.label().toLowerCase(Locale.ROOT).contains(queryLower)
                        || (chapter.title != null && chapter.title.toLowerCase(Locale.ROOT).contains(queryLower))) {
                    filtered.add(chapter);
                }
            }
            ordered = filtered;
            if (ordered.size() > MAX_SEARCH_ROWS) {
                ordered = new ArrayList<>(ordered.subList(0, MAX_SEARCH_ROWS));
                truncated = true;
            }
        }
        for (ShinigamiChapter chapter : ordered) {
            chapterList.addView(buildChapterRow(chapter));
        }
        if (truncated) {
            TextView note = Ui.label(this,
                    "Menampilkan " + MAX_SEARCH_ROWS + " hasil pertama. Persempit kata kunci untuk hasil lain.",
                    12, palette.muted);
            note.setTag("shinigami_search_truncated");
            chapterList.addView(note);
        }
        if (ordered.isEmpty()) {
            String emptyMessage;
            if (!currentSearchQuery.isEmpty()) {
                emptyMessage = "Tidak ditemukan chapter yang sesuai.";
            } else if (loading) {
                emptyMessage = "Memuat chapter dari sumber…";
            } else {
                emptyMessage = "Sumber belum memuat chapter untuk judul ini.";
            }
            TextView empty = Ui.label(this, emptyMessage, 14, palette.muted);
            empty.setTag("shinigami_no_chapters");
            chapterList.addView(empty);
        }
        if (selectionBar != null) {
            boolean active = !selected.isEmpty();
            selectionBar.setVisibility(active ? View.VISIBLE : View.GONE);
            if (active) {
                selectionCount.setText(selected.size() + " chapter dipilih");
                selectionDownload.setText("Unduh " + selected.size() + " chapter");
            }
        }
    }

    /**
     * Urutan tampil daftar chapter. Pengurutan memakai nomor chapter numerik
     * (aman untuk 10.5, prolog, dan spesial), bukan perbandingan teks judul.
     * "Terbaru" mengurutkan menurun (descending), "Terlama" menaik (ascending).
     */
    private List<ShinigamiChapter> sortedChapters() {
        List<ShinigamiChapter> ordered = new ArrayList<>(chapters);
        if (chapterSortOrder == SORT_OLDEST) {
            Collections.sort(ordered, COMPARATOR_ASC);
        } else {
            Collections.sort(ordered, COMPARATOR_DESC);
        }
        return ordered;
    }

    private String sortLabel() {
        return chapterSortOrder == SORT_LATEST
                ? "Terbaru → Terlama" : "Terlama → Terbaru";
    }

    /** Ganti urutan pengurutan dan muat ulang daftar. */
    private void toggleSortOrder() {
        chapterSortOrder = chapterSortOrder == SORT_LATEST ? SORT_OLDEST : SORT_LATEST;
        new Prefs(this).setChapterSortOrder(chapterSortOrder);
        if (sortButton != null) {
            sortButton.setContentDescription(chapterSortOrder == SORT_LATEST
                    ? "Urutkan dari terbaru" : "Urutkan dari terlama");
            sortButton.setImageResource(id.shuyasal.kuromaku.R.drawable.ic_sort);
        }
        // Reset pagination lalu minta ulang dari awal dengan arah baru. Tanpa
        // `force`, permintaan sebelumnya yang masih berjalan akan menolak
        // pemuatan ini dan daftar tetap kosong.
        chapterPage = 1;
        chapters.clear();
        knownChapterIds.clear();
        chapterTotalRecord = 0;
        renderChapters();
        if (detailScroll != null) {
            detailScroll.post(() -> detailScroll.smoothScrollTo(0, chapterHeadingView != null ? chapterHeadingView.getTop() : 0));
        }
        loadChapters(1, true);
    }

    private View buildChapterRow(ShinigamiChapter chapter) {
        boolean picked = selected.contains(chapter.id);
        int expected = store.expectedPages(chapter.id);
        String status = downloader.storageLabel(chapter.id, expected);
        boolean saved = ShinigamiDownloader.LABEL_SAVED.equals(status);
        boolean running = isRunningState(chapter.id) || status.startsWith("Mengunduh");
        boolean failed = ShinigamiDownloader.LABEL_FAILED.equals(status);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        row.setBackground(Ui.rounded(palette.surface, picked ? palette.accent : palette.divider, 10, this));
        row.setTag("shinigami_chapter_" + chapter.id);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setMinimumWidth(0);

        TextView label = Ui.label(this, chapter.label(), 14, palette.text);
        label.setTag("shinigami_chapter_label");
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        left.addView(label);

        TextView date = Ui.label(this, chapter.dateLabel(), 12, palette.muted);
        date.setTag("shinigami_chapter_date");
        date.setPadding(0, Ui.dp(this, 2), 0, 0);
        left.addView(date);

        int progress = store.progress(chapter.id);
        String readLabel = ShinigamiChapterProgress.readLabel(progress);
        TextView readState = Ui.label(this, readLabel, 11,
                progress >= 100 ? palette.muted : palette.accent);
        readState.setTag("shinigami_chapter_progress");
        readState.setPadding(0, Ui.dp(this, 3), 0, 0);
        readState.setVisibility(readLabel.isEmpty() ? View.GONE : View.VISIBLE);
        left.addView(readState);

        row.addView(left, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        // Kolom kanan berlebar tetap supaya judul di kiri tidak pernah terdorong
        // melewati tepi layar, dan label status tidak terpotong.
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER_HORIZONTAL);
        right.setPadding(Ui.dp(this, 6), 0, 0, 0);

        ShinigamiDownloadRing ring = new ShinigamiDownloadRing(this);
        ring.setColors(palette.accent, palette.divider, Color.rgb(190, 60, 60));
        ring.setTag("shinigami_download_ring");

        int percentNow = downloadPercent(chapter.id, expected);
        if (saved) {
            ring.setState(ShinigamiDownloadRing.DONE);
            ring.setPercent(100, false);
        } else if (failed) {
            ring.setState(ShinigamiDownloadRing.FAILED);
            ring.setPercent(percentNow, false);
        } else if (running) {
            ring.setState(ShinigamiDownloadRing.RUNNING);
            ring.setPercent(percentNow, false);
        } else {
            ring.setState(ShinigamiDownloadRing.IDLE);
            ring.setPercent(0, false);
        }
        ring.setVisibility(running || saved || failed ? View.VISIBLE : View.INVISIBLE);
        LinearLayout.LayoutParams ringParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 42), Ui.dp(this, 42));
        right.addView(ring, ringParams);

        TextView statusText = Ui.label(this, shortStatus(status, saved, running, failed), 11,
                statusColor(saved, running, failed));
        statusText.setTag("shinigami_chapter_download_status");
        statusText.setGravity(Gravity.CENTER);
        statusText.setMaxLines(1);
        statusText.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = Ui.dp(this, 2);
        right.addView(statusText, statusParams);

        String actionLabel = saved ? "Hapus" : failed ? "Coba Lagi" : running ? "Batal" : "Unduh";
        Button action = Ui.chip(this, actionLabel, palette);
        action.setTag("shinigami_chapter_download");
        action.setContentDescription(actionLabel + ": " + chapter.label());
        action.setTextSize(11f);
        action.setMinWidth(0);
        action.setMinimumWidth(0);
        action.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        action.setOnClickListener(v -> onDownloadAction(chapter, status));
        action.setOnLongClickListener(v -> {
            toggleSelection(chapter.id);
            return true;
        });
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 30));
        actionParams.topMargin = Ui.dp(this, 4);
        right.addView(action, actionParams);

        row.addView(right, new LinearLayout.LayoutParams(
                Ui.dp(this, 88), ViewGroup.LayoutParams.WRAP_CONTENT));
        rows.put(chapter.id, new ChapterRow(chapter, ring, statusText, action));

        row.setOnClickListener(v -> {
            if (selected.isEmpty()) openChapter(chapter);
            else toggleSelection(chapter.id);
        });
        row.setOnLongClickListener(v -> {
            toggleSelection(chapter.id);
            return true;
        });
        return row;
    }

    /** Perbarui ring dan label tanpa membangun ulang daftar. */
    private void refreshDownloadStates() {
        if (isFinishing() || isDestroyed()) return;
        for (ChapterRow row : rows.values()) {
            ShinigamiChapter chapter = row.chapter;
            int expected = store.expectedPages(chapter.id);
            String status = downloader.storageLabel(chapter.id, expected);
            boolean saved = ShinigamiDownloader.LABEL_SAVED.equals(status);
            boolean running = isRunningState(chapter.id) || status.startsWith("Mengunduh");
            boolean failed = ShinigamiDownloader.LABEL_FAILED.equals(status);
            int percent = downloadPercent(chapter.id, expected);

            row.ring.setVisibility(running || saved || failed ? View.VISIBLE : View.INVISIBLE);
            if (saved) {
                row.ring.setState(ShinigamiDownloadRing.DONE);
                row.ring.setPercent(100, true);
            } else if (failed) {
                row.ring.setState(ShinigamiDownloadRing.FAILED);
                row.ring.setPercent(percent, true);
            } else if (running) {
                row.ring.setState(ShinigamiDownloadRing.RUNNING);
                row.ring.setPercent(percent, true);
            } else {
                row.ring.setState(ShinigamiDownloadRing.IDLE);
                row.ring.setPercent(0, false);
            }

            row.status.setText(shortStatus(status, saved, running, failed));
            row.status.setTextColor(statusColor(saved, running, failed));

            String label = saved ? "Hapus" : failed ? "Coba Lagi" : running ? "Batal" : "Unduh";
            row.action.setText(label);
            row.action.setContentDescription(label + ": " + chapter.label());
            row.action.setOnClickListener(v -> onDownloadAction(chapter, status));
        }
    }

    private static boolean isRunningState(String chapterId) {
        int state = ShinigamiDownloadStatus.state(chapterId);
        return state == ShinigamiDownloadStatus.QUEUED
                || state == ShinigamiDownloadStatus.DOWNLOADING
                || state == ShinigamiDownloadStatus.VERIFYING;
    }

    /** Persentase dari halaman yang benar-benar tersimpan, bukan timer. */
    private int downloadPercent(String chapterId, int expectedPages) {
        if (expectedPages <= 0) return 0;
        int saved = 0;
        for (int index = 0; index < expectedPages; index++) {
            if (downloader.pageFile(chapterId, index) != null) saved++;
        }
        return Math.max(0, Math.min(100, saved * 100 / expectedPages));
    }

    private String shortStatus(String status, boolean saved, boolean running, boolean failed) {
        if (saved) return "Tersimpan";
        if (failed) return "Gagal";
        if (running || (status != null && status.startsWith("Mengunduh"))) return "Mengunduh";
        return "Belum diunduh";
    }

    private int statusColor(boolean saved, boolean running, boolean failed) {
        if (saved || running) return palette.accent;
        if (failed) return Color.rgb(190, 60, 60);
        return palette.muted;
    }

    private View buildSelectionBar() {
        selectionBar = new LinearLayout(this);
        selectionBar.setOrientation(LinearLayout.HORIZONTAL);
        selectionBar.setGravity(Gravity.CENTER_VERTICAL);
        selectionBar.setTag("shinigami_selection_bar");
        selectionBar.setVisibility(View.GONE);
        selectionBar.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        selectionBar.setBackground(Ui.rounded(palette.surface, palette.divider, 10, this));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barParams.topMargin = Ui.dp(this, 10);
        selectionBar.setLayoutParams(barParams);

        selectionCount = Ui.label(this, "", 13, palette.text);
        selectionCount.setTag("shinigami_selection_count");
        selectionBar.addView(selectionCount, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button cancel = Ui.chip(this, "Batal", palette);
        cancel.setTag("shinigami_selection_cancel");
        cancel.setOnClickListener(v -> {
            selected.clear();
            renderChapters();
        });
        selectionBar.addView(cancel);

        selectionDownload = Ui.chip(this, "Unduh", palette);
        selectionDownload.setTag("shinigami_selection_download");
        selectionDownload.setOnClickListener(v -> downloadSelected());
        LinearLayout.LayoutParams downloadParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        downloadParams.leftMargin = Ui.dp(this, 6);
        selectionBar.addView(selectionDownload, downloadParams);
        return selectionBar;
    }

    private void toggleSelection(String chapterId) {
        if (chapterId == null || chapterId.isEmpty()) return;
        if (!selected.remove(chapterId)) selected.add(chapterId);
        renderChapters();
    }

    private void downloadSelected() {
        if (selected.isEmpty()) return;
        ensureDownloadAllowed(() -> {
            List<ShinigamiChapter> chosen = new ArrayList<>();
            for (ShinigamiChapter chapter : chapters) {
                if (selected.contains(chapter.id)) chosen.add(chapter);
            }
            recordOwners(chosen);
            int queued = ShinigamiDownloadQueue.enqueueChapters(this, mangaId, title,
                    new ArrayList<>(selected));
            selected.clear();
            renderChapters();
            Toast.makeText(this, queued == 0
                    ? "Chapter terpilih sudah tersimpan offline."
                    : "Mengunduh " + queued + " chapter.", Toast.LENGTH_SHORT).show();
        });
    }

    private void onDownloadAction(ShinigamiChapter chapter, String status) {
        int expectedPages = store.expectedPages(chapter.id);
        if (downloader.isDownloaded(chapter.id, expectedPages)) {
            confirmDelete(chapter);
            return;
        }
        int state = ShinigamiDownloadStatus.state(chapter.id);
        boolean running = state == ShinigamiDownloadStatus.QUEUED
                || state == ShinigamiDownloadStatus.DOWNLOADING
                || state == ShinigamiDownloadStatus.VERIFYING
                || downloader.isRunning(chapter.id);
        if (running) {
            ShinigamiDownloadQueue.cancelChapter(this, chapter.id);
            Toast.makeText(this, "Unduhan dihentikan. Halaman yang sudah lengkap tetap tersimpan.",
                    Toast.LENGTH_SHORT).show();
            renderChapters();
            return;
        }
        if (!ShinigamiDownloadPolicy.allowed(this)) {
            ensureDownloadAllowed(() -> {
                recordOwners(java.util.Collections.singletonList(chapter));
                if (state == ShinigamiDownloadStatus.FAILED) {
                    ShinigamiDownloadQueue.retryChapter(this, mangaId, title, chapter.id);
                } else {
                    ShinigamiDownloadQueue.enqueueChapter(this, chapter.id);
                }
                renderChapters();
            });
            return;
        }
        recordOwners(java.util.Collections.singletonList(chapter));
        if (state == ShinigamiDownloadStatus.FAILED) {
            ShinigamiDownloadQueue.retryChapter(this, mangaId, title, chapter.id);
        } else {
            ShinigamiDownloadQueue.enqueueChapter(this, chapter.id);
        }
        renderChapters();
    }

    private void confirmDelete(ShinigamiChapter chapter) {
        new AlertDialog.Builder(this)
                .setTitle("Hapus unduhan")
                .setMessage("Hapus file offline " + chapter.label() + "?\n\n"
                        + "Progres membaca tidak ikut terhapus, jadi chapter ini tetap tercatat "
                        + "di riwayat dan kembali dibaca dari posisi terakhir.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus", (dialog, which) -> {
                    ShinigamiDownloadQueue.deleteChapter(this, chapter.id);
                    renderChapters();
                    Toast.makeText(this, "Unduhan dihapus. Progres membaca tetap tersimpan.",
                            Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    static String downloadActionLabel(String status) {
        if (ShinigamiDownloader.LABEL_SAVED.equals(status)) return "Hapus";
        if (ShinigamiDownloader.LABEL_FAILED.equals(status)) return "Ulangi";
        if (status != null && status.startsWith("Mengunduh")) return "Batal";
        return "Unduh";
    }

    private int downloadStateColor(String status) {
        if (ShinigamiDownloader.LABEL_SAVED.equals(status)) return palette.accent;
        if (ShinigamiDownloader.LABEL_FAILED.equals(status)) return Color.rgb(190, 60, 60);
        if (status != null && status.startsWith("Mengunduh")) return palette.accent;
        return palette.muted;
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

    /**
     * Unduhan hanya berjalan setelah pengguna membaca pemberitahuan dan
     * menyetujui penyimpanan untuk pemakaian pribadi. Persetujuan disimpan,
     * jadi dialog tidak muncul berulang.
     */
    private void ensureDownloadAllowed(Runnable action) {
        if (ShinigamiDownloadPolicy.allowed(this)) {
            action.run();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Unduhan offline")
                .setMessage(ShinigamiDownloadPolicy.notice())
                .setNegativeButton("Batal", null)
                .setPositiveButton("Saya mengerti", (dialog, which) -> {
                    ShinigamiDownloadPolicy.acknowledge(this);
                    action.run();
                })
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
        int currentResolved = new Prefs(this).resolvedTheme();
        if (activeResolvedTheme != -1 && currentResolved != activeResolvedTheme) {
            recreate();
            return;
        }
        if (store == null || chapterList == null || chapters.isEmpty()) return;
        renderChapters();
        // Status unduhan bisa berjalan di WorkManager saat halaman ini
        // ditinggalkan; selaraskan lagi supaya ring tidak menampilkan angka basi.
        List<String> ids = new ArrayList<>();
        for (ShinigamiChapter chapter : chapters) ids.add(chapter.id);
        worker.execute(() -> {
            ShinigamiDownloadQueue.refreshStatus(this, ids);
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                renderChapters();
            });
        });
    }

    /** Catat pemilik chapter supaya tab Unduhan bisa mengelompokkannya. */
    private void recordOwners(List<ShinigamiChapter> chosen) {
        for (ShinigamiChapter chapter : chosen) {
            store.setChapterOwner(chapter.id, mangaId, title, coverUrl,
                    chapter.number, chapter.label());
        }
    }

    @Override
    protected void onDestroy() {
        ShinigamiDownloadStatus.removeListener(downloadStateListener);
        main.removeCallbacks(deepSearchTask);
        worker.shutdownNow();
        super.onDestroy();
    }
}