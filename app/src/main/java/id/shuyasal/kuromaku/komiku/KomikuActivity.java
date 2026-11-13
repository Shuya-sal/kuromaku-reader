package id.shuyasal.kuromaku.komiku;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import id.shuyasal.kuromaku.R;
import id.shuyasal.kuromaku.sync.SyncBridge;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.RatioFrameLayout;
import id.shuyasal.kuromaku.ui.ThemeManager;
import id.shuyasal.kuromaku.ui.Ui;

/**
 * Beranda Manga Server 3 (Komiku) — native, bukan WebView.
 *
 * <p>Susunannya sengaja dibuat sama dengan beranda Manga Server 2: bar atas
 * dengan menu, judul, tombol unduhan, dan menu titik tiga; kolom pencarian +
 * tombol Cari; deretan chip yang bisa digeser; baris jumlah; rail
 * <b>Lanjutkan Membaca</b> yang ikut tergulir; lalu daftar. Chapter Terbaru dan
 * Riwayat tampil sebagai baris, katalog dan pencarian tampil sebagai grid.
 * Yang berbeda hanya sumber datanya.
 *
 * <p>Semua isi berasal dari halaman Komiku yang benar-benar dibaca saat itu.
 * Tidak ada katalog contoh yang ditanam di aplikasi.
 */
public class KomikuActivity extends id.shuyasal.kuromaku.ui.TvActivity {

    private static final int SEARCH_DEBOUNCE_MS = 350;
    /** Jumlah baris yang ditambahkan sekali gulir. */
    private static final int FEED_PAGE_SIZE = 24;

    /**
     * Sasaran isi awal umpan.
     *
     * <p>Beranda hanya memberi 40 judul, jadi setelah 40 itu tampil daftar
     * dilanjutkan sendiri sampai sekitar seratus baris — pengguna langsung
     * melihat bahwa sumbernya jauh lebih besar dari satu halaman beranda,
     * tanpa harus menggulir dulu.
     */
    private static final int FEED_PREFILL = 96;

    /**
     * Berapa halaman kosong berturut-turut sebelum umpan dianggap habis.
     *
     * <p>Empat halaman pertama katalog mengulang 40 judul beranda, jadi ambang
     * ini harus lebih besar dari empat.
     */
    private static final int FEED_MAX_EMPTY_PAGES = 6;

    /** Katalog dianggap perlu diperbarui setelah selama ini. */
    private static final long CATALOG_MAX_AGE_MS = 12 * 60 * 60 * 1000L;

    private static final int CONTINUE_LIMIT = 12;
    private static final int CHROME_ANIM_MS = 180;
    private static final int CONTINUE_TEXT_DP = 150;

    /** Bentuk isi daftar yang sedang tampil. */
    private enum Mode {ROWS, GRID}

    /** Tab daftar; urutan dan labelnya sama dengan Manga Server 2. */
    private enum Tab {
        NEW_CHAPTERS("Chapter Terbaru"),
        POPULAR("Terpopuler"),
        NEW_TITLES("Manga Terbaru"),
        HISTORY("Riwayat"),
        FAVORITES("Favorit");

        final String label;

        Tab(String label) {
            this.label = label;
        }

        boolean usesRows() {
            return this == NEW_CHAPTERS || this == HISTORY;
        }
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<KomikuManga> items = new ArrayList<>();

    private Palette palette;
    private KomikuSource source;
    private KomikuStore store;
    private KomikuCatalog catalog;
    /** Thread khusus katalog: pengambilan 800+ halaman tidak boleh menahan tab lain. */
    private final ExecutorService catalogWorker = Executors.newSingleThreadExecutor();
    private volatile boolean catalogFetching;
    private volatile boolean catalogStop;
    /** Benar bila daftar yang tampil adalah hasil pencarian. */
    private boolean searchMode;
    /** Halaman katalog berikutnya yang akan ditambahkan ke umpan. */
    private int feedNextPage = 1;
    private boolean feedLoading;
    private boolean feedEnded;
    /** Katalog tersimpan di memori saat dipakai sebagai sumber umpan. */
    private List<KomikuManga> feedSource;
    /**
     * Halaman berturut-turut yang tidak menambah judul baru.
     *
     * <p>Empat halaman pertama katalog sama dengan 40 judul beranda (urutan
     * terbaru), jadi halaman-halaman itu memang tidak menambah apa pun. Umpan
     * baru boleh dianggap habis setelah beberapa halaman kosong berturut-turut.
     */
    private int feedEmptyStreak;
    private id.shuyasal.kuromaku.sync.SyncBridge syncBridge;
    private int activeTheme;

    private EditText searchField;
    private LinearLayout chrome;
    private LinearLayout filterRow;
    private TextView statusLine;
    private RecyclerView list;
    private ProgressBar spinner;
    private LinearLayout errorBox;
    private TextView errorText;
    private Button retryButton;

    private View continueRail;
    private RecyclerView continueList;
    private ContinueAdapter continueAdapter;
    private HeaderAdapter headerAdapter;

    private RowAdapter rowAdapter;
    private GridAdapter gridAdapter;
    private ConcatAdapter shownAdapter;
    private RecyclerView.Adapter<?> shownContent;
    private Mode mode = Mode.ROWS;

    private Tab tab = Tab.NEW_CHAPTERS;
    private int generation;
    private boolean chromeHidden;
    private boolean chromeBusy;
    private int chromeHeight;
    private int scrollDown;
    private int scrollUp;
    private android.animation.ValueAnimator chromeAnimator;

    private final Runnable searchRunnable = this::runSearch;
    private final Runnable chromeRelease = () -> chromeBusy = false;

    /**
     * Satu kartu di rail "Lanjutkan Membaca".
     *
     * <p>Ada dua asal: riwayat di sumber ini (punya alamat chapter, bisa
     * langsung dibuka) dan judul yang baru dibaca di Manga Server 2 (hanya
     * punya identitas judul, jadi membukanya menuju halaman detail — bukan
     * alamat chapter yang ditebak).
     */
    private static final class ContinueItem {
        final String mangaId;
        final String title;
        final String coverUrl;
        final String chapterLabel;
        final String chapterUrl;
        final int percent;
        final boolean fromPartner;

        ContinueItem(String mangaId, String title, String coverUrl, String chapterLabel,
                     String chapterUrl, int percent, boolean fromPartner) {
            this.mangaId = mangaId == null ? "" : mangaId;
            this.title = title == null ? "" : title;
            this.coverUrl = coverUrl == null ? "" : coverUrl;
            this.chapterLabel = chapterLabel == null ? "" : chapterLabel;
            this.chapterUrl = chapterUrl == null ? "" : chapterUrl;
            this.percent = percent;
            this.fromPartner = fromPartner;
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeManager.applyTheme(this);
        palette = Palette.of(new Prefs(this).resolvedTheme());
        activeTheme = new Prefs(this).resolvedTheme();
        source = new KomikuSource();
        store = new KomikuStore(this);
        catalog = new KomikuCatalog(this);
        syncBridge = new id.shuyasal.kuromaku.sync.SyncBridge(this);
        buildUi();
        loadTab(tab);
    }

    // ------------------------------------------------------------------ UI

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);

        root.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        chrome = new LinearLayout(this);
        chrome.setOrientation(LinearLayout.VERTICAL);
        chrome.setTag("komiku_chrome");

        searchField = new EditText(this);
        searchField.setHint("Cari judul manga");
        searchField.setTextColor(palette.text);
        searchField.setHintTextColor(palette.muted);
        searchField.setSingleLine(true);
        searchField.setTag("komiku_search");
        searchField.setBackground(Ui.rounded(palette.surface, palette.divider, 16, this));
        searchField.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        searchField.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchField.setOnEditorActionListener((v, actionId, event) -> {
            runSearch();
            return true;
        });
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }

            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override public void afterTextChanged(Editable text) {
                main.removeCallbacks(searchRunnable);
                main.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
            }
        });

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4));
        searchRow.addView(searchField, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button search = Ui.chip(this, "Cari", palette);
        search.setTag("komiku_search_button");
        search.setOnClickListener(v -> runSearch());
        searchRow.addView(search);
        chrome.addView(searchRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setTag("komiku_filter");
        filterRow.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 6));
        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        filterScroll.addView(filterRow, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        chrome.addView(filterScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusLine = Ui.label(this, "", 13, palette.muted);
        statusLine.setTag("komiku_status");
        statusLine.setPadding(Ui.dp(this, 14), Ui.dp(this, 2), Ui.dp(this, 14), Ui.dp(this, 6));
        chrome.addView(statusLine);

        root.addView(chrome, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Rail riwayat menjadi item pertama di dalam daftar, jadi ikut
        // tergulir bersama isinya — sama seperti Manga Server 2.
        continueRail = buildContinueRail();

        FrameLayout body = new FrameLayout(this);
        list = new RecyclerView(this);
        list.setTag("komiku_list");
        list.setBackgroundColor(palette.background);
        list.setClipToPadding(false);
        list.setItemAnimator(null);
        // Televisi: kartu dibuat lebih besar, jadi jumlah yang terlihat di layar
        // sedikit. Menyimpan tampilan item di luar layar hanya menghabiskan
        // memori tanpa mempercepat apa pun yang terlihat dari sofa.
        if (id.shuyasal.kuromaku.ui.TvMode.isTelevision(this)) {
            list.setItemViewCacheSize(2);
        }
        rowAdapter = new RowAdapter();
        gridAdapter = new GridAdapter();
        list.setLayoutManager(new LinearLayoutManager(this));
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView view, int dx, int dy) {
                updateChrome(dy);
                maybeLoadMoreFeed();
            }
        });
        body.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        spinner.setVisibility(View.GONE);
        body.addView(spinner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        errorBox = new LinearLayout(this);
        errorBox.setOrientation(LinearLayout.VERTICAL);
        errorBox.setGravity(Gravity.CENTER);
        errorBox.setPadding(Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24));
        errorBox.setVisibility(View.GONE);
        errorText = Ui.label(this, "", 14, palette.text);
        errorText.setGravity(Gravity.CENTER);
        errorText.setTag("komiku_error");
        errorBox.addView(errorText);
        retryButton = Ui.chip(this, "Coba lagi", palette);
        retryButton.setTag("komiku_retry");
        retryButton.setOnClickListener(v -> loadTab(tab));
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        retryParams.topMargin = Ui.dp(this, 12);
        errorBox.addView(retryButton, retryParams);
        body.addView(errorBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        applyInsets(root);
        buildFilterChips();
        switchContent(rowAdapter);
        refreshContinueRail();
    }

    private LinearLayout buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        header.setBackgroundColor(palette.surface);

        android.widget.ImageButton menu = Ui.iconButton(this, R.drawable.ic_menu,
                "Buka menu sumber", palette);
        menu.setTag("section_menu");
        menu.setOnClickListener(v -> KomikuNavigation.showDrawer(this, palette));
        header.addView(menu, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));

        TextView heading = Ui.label(this, "Manga Server 3", 20, palette.text);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        heading.setTag("komiku_heading");
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        headingParams.leftMargin = Ui.dp(this, 8);
        header.addView(heading, headingParams);

        // Unduhan offline Komiku belum ada. Ikonnya ditampilkan redup dengan
        // penjelasan yang jujur, bukan tombol yang terlihat berfungsi.
        android.widget.ImageButton downloads = Ui.iconButton(this, R.drawable.ic_download,
                "Unduhan Manga Server 3", palette);
        downloads.setTag("komiku_downloads_button");
        downloads.setAlpha(0.45f);
        downloads.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Unduhan Manga Server 3")
                .setMessage("Menyimpan chapter Komiku untuk dibaca offline belum tersedia "
                        + "di versi ini. Buka chapter langsung dari jaringan; unduhan "
                        + "Manga Server 2 tetap berjalan seperti biasa.")
                .setPositiveButton("Mengerti", null)
                .show());
        header.addView(downloads, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));

        android.widget.ImageButton more = Ui.iconButton(this, R.drawable.ic_more,
                "Menu Manga Server 3", palette);
        more.setTag("komiku_overflow");
        more.setOnClickListener(v -> showOverflowMenu());
        header.addView(more, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));
        return header;
    }

    private void buildFilterChips() {
        filterRow.removeAllViews();
        for (Tab value : Tab.values()) {
            addChip(value.label, tab == value, () -> selectTab(value));
        }
    }

    private void addChip(String text, boolean active, Runnable action) {
        Button chip = Ui.chip(this, text, palette);
        chip.setTag("komiku_chip_" + text);
        chip.setSelected(active);
        chip.setAlpha(active ? 1f : 0.7f);
        chip.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = Ui.dp(this, 8);
        filterRow.addView(chip, params);
    }

    /** Rail "Lanjutkan Membaca": kartu horizontal dari riwayat Komiku. */
    private View buildContinueRail() {
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setTag("komiku_continue_rail");
        rail.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 2));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 8), 0);

        TextView label = Ui.label(this, "Lanjutkan Membaca", 14, palette.text);
        label.setTypeface(label.getTypeface(), Typeface.BOLD);
        label.setTag("komiku_continue_label");
        head.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView all = Ui.label(this, "Lihat semua", 12, palette.accent);
        all.setTag("komiku_continue_all");
        all.setGravity(Gravity.CENTER_VERTICAL);
        all.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
        all.setOnClickListener(v -> selectTab(Tab.HISTORY));
        head.addView(all, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rail.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        continueList = new RecyclerView(this);
        continueList.setTag("komiku_continue_cards");
        continueList.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        continueList.setClipToPadding(false);
        continueList.setPadding(Ui.dp(this, 14), Ui.dp(this, 2), Ui.dp(this, 14), 0);
        continueList.setOverScrollMode(View.OVER_SCROLL_NEVER);
        continueAdapter = new ContinueAdapter();
        continueList.setAdapter(continueAdapter);
        rail.addView(continueList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return rail;
    }

    /**
     * Isi rail "Lanjutkan Membaca".
     *
     * <p>Dua bagian: riwayat di sumber ini (bisa langsung dibuka), lalu judul
     * yang sudah dibaca di Manga Server 2 dan sudah dipasangkan di layar detail
     * (dibuka lewat halaman detail, karena alamat chapter-nya belum diketahui).
     * Tanpa tautan, judul server lain memang tidak boleh muncul — itu bukan
     * riwayat sumber ini.
     */
    private void refreshContinueRail() {
        if (continueRail == null || continueAdapter == null || store == null) return;
        List<ContinueItem> recent = new ArrayList<>();
        java.util.LinkedHashSet<String> localIds = new java.util.LinkedHashSet<>();
        for (KomikuStore.Entry entry : store.history()) {
            if (recent.size() >= CONTINUE_LIMIT) break;
            if (entry.chapterUrl.isEmpty()) continue;
            localIds.add(entry.mangaId);
            recent.add(new ContinueItem(entry.mangaId, entry.title, entry.coverUrl,
                    chapterShort(entry.chapterLabel), entry.chapterUrl,
                    (int) Math.max(0, Math.min(100, Math.round(entry.fraction * 100))), false));
        }
        if (syncBridge != null && recent.size() < CONTINUE_LIMIT) {
            for (SyncBridge.PartnerEntry partner : syncBridge.pendingFrom(
                    SyncBridge.SOURCE_KOMIKU, SyncBridge.SOURCE_SHINIGAMI, localIds)) {
                if (recent.size() >= CONTINUE_LIMIT) break;
                if (partner.mangaId.isEmpty()) continue;
                recent.add(new ContinueItem(partner.mangaId, partner.title, "",
                        chapterShort(partner.chapterLabel()), "", 0, true));
            }
        }
        boolean wasEmpty = continueAdapter.getItemCount() == 0;
        continueAdapter.submit(recent);
        continueRail.setVisibility(recent.isEmpty() ? View.GONE : View.VISIBLE);
        if (headerAdapter != null && wasEmpty != recent.isEmpty()) {
            headerAdapter.notifyDataSetChanged();
        }
    }

    private void applyInsets(View root) {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                            | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    // -------------------------------------------------------------- chrome

    /**
     * Sembunyikan pencarian + chip + jumlah saat menggulir turun, tampilkan
     * kembali saat naik. Ambang asimetris dan penguncian selama animasi
     * mencegah header berkedip — pola yang sama dengan Manga Server 2.
     */
    private void updateChrome(int dy) {
        if (chrome == null || chromeBusy) return;
        if (dy > 0) {
            scrollDown += dy;
            scrollUp = 0;
        } else if (dy < 0) {
            scrollUp -= dy;
            scrollDown = 0;
        } else {
            return;
        }
        int hideThreshold = Ui.dp(this, 48);
        int showThreshold = Ui.dp(this, 24);
        if (!chromeHidden && scrollDown >= hideThreshold) {
            setChromeHidden(true);
        } else if (chromeHidden && scrollUp >= showThreshold) {
            setChromeHidden(false);
        }
    }

    private void setChromeHidden(boolean hide) {
        if (chrome == null || chromeHidden == hide) return;
        chromeHidden = hide;
        scrollDown = 0;
        scrollUp = 0;
        if (chromeHeight <= 0) chromeHeight = chrome.getHeight();
        if (chromeHeight <= 0) {
            chrome.measure(
                    View.MeasureSpec.makeMeasureSpec(
                            getResources().getDisplayMetrics().widthPixels,
                            View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            chromeHeight = chrome.getMeasuredHeight();
        }
        if (chromeHeight <= 0) chromeHeight = Ui.dp(this, 96);

        ViewGroup.LayoutParams params = chrome.getLayoutParams();
        int from = params.height > 0 ? params.height : (hide ? chromeHeight : 0);
        if (!hide) {
            chrome.setVisibility(View.VISIBLE);
            params.height = chromeHeight;
            chrome.setLayoutParams(params);
            from = 0;
        }
        chromeBusy = true;
        main.removeCallbacks(chromeRelease);
        if (chromeAnimator != null) {
            chromeAnimator.removeAllListeners();
            chromeAnimator.cancel();
            chromeAnimator = null;
        }
        android.animation.ValueAnimator animator =
                android.animation.ValueAnimator.ofInt(from, hide ? 0 : chromeHeight);
        animator.setDuration(CHROME_ANIM_MS);
        animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            ViewGroup.LayoutParams current = chrome.getLayoutParams();
            current.height = (int) animation.getAnimatedValue();
            chrome.setLayoutParams(current);
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                ViewGroup.LayoutParams end = chrome.getLayoutParams();
                end.height = hide ? 0 : chromeHeight;
                chrome.setLayoutParams(end);
                main.postDelayed(chromeRelease, 60);
            }
        });
        chromeAnimator = animator;
        animator.start();
    }

    // ---------------------------------------------------------------- data

    private void selectTab(Tab target) {
        if (tab == target) return;
        tab = target;
        buildFilterChips();
        loadTab(tab);
    }

    private void runSearch() {
        String query = searchField.getText().toString().trim();
        if (query.isEmpty()) {
            loadTab(tab);
            return;
        }
        final int request = ++generation;
        showLoading("Mencari \"" + query + "\"…");
        worker.execute(() -> {
            List<KomikuManga> found;
            String error = null;
            try {
                found = searchEverything(query);
            } catch (Throwable failure) {
                found = new ArrayList<>();
                error = failure.getMessage();
            }
            final List<KomikuManga> result = found;
            final String message = error;
            main.post(() -> {
                if (request != generation || isFinishing()) return;
                hideLoading();
                // Hasil pencarian memakai daftar baris — sama seperti daftar
                // chapter terbaru, bukan grid kartu besar. Judul yang panjang
                // tetap terbaca dua baris, dan keterangan chapter/waktu ada di
                // bawahnya.
                mode = Mode.ROWS;
                items.clear();
                items.addAll(result);
                searchMode = true;
                switchContent(rowAdapter);
                publishItems();
                refreshContinueRail();
                if (message != null) {
                    statusLine.setText("Pencarian gagal: " + message);
                } else if (result.isEmpty()) {
                    statusLine.setText("Tidak ada hasil untuk \"" + query + "\".");
                } else {
                    statusLine.setText(result.size() + " hasil untuk \"" + query + "\"");
                }
            });
        });
    }


    /**
     * Judul untuk tab "Manga Terbaru".
     *
     * <p>Bila katalog lengkap sudah pernah diambil, seluruh katalog dipakai —
     * bukan hanya 40 judul beranda. Bila belum, beranda tetap dipakai supaya
     * tab ini tidak kosong sebelum pengambilan pertama.
     */
    private List<KomikuManga> catalogTitles() throws KomikuSource.SourceException {
        if (catalog != null && catalog.exists()) {
            List<KomikuManga> all = catalog.load();
            if (!all.isEmpty()) return all;
        }
        return source.newest();
    }

    /**
     * Cari di katalog tersimpan lebih dulu, lalu di situs.
     *
     * <p>Katalog memuat ribuan judul yang tidak ada di beranda, jadi pencarian
     * yang hanya memakai halaman {@code ?s=} akan melewatkan banyak judul.
     * Hasil katalog dan hasil situs digabung tanpa judul ganda; bila katalog
     * belum ada, hasil situs tetap dipakai.
     */
    private List<KomikuManga> searchEverything(String query) throws KomikuSource.SourceException {
        Map<String, KomikuManga> merged = new LinkedHashMap<>();
        String needle = query.toLowerCase(java.util.Locale.ROOT);
        if (catalog != null && catalog.exists()) {
            for (KomikuManga manga : catalog.load()) {
                if (manga.title.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                    merged.put(manga.id, manga);
                }
            }
        }
        // Katalog tersimpan sudah punya hasil: jangan menunggu jaringan.
        // Selain lebih cepat, ini membuat pencarian tetap bekerja saat sinyal
        // buruk — dan tidak menggantungkan hasil pada permintaan yang bisa
        // lambat atau gagal.
        if (!merged.isEmpty()) return new ArrayList<>(merged.values());
        try {
            for (KomikuManga manga : source.search(query)) merged.putIfAbsent(manga.id, manga);
        } catch (KomikuSource.SourceException failure) {
            if (merged.isEmpty()) throw failure;
        }
        return new ArrayList<>(merged.values());
    }


    // ------------------------------------------------- umpan berkelanjutan

    /**
     * Tambah halaman berikutnya ke "Chapter Terbaru" saat pengguna mendekati
     * bawah daftar.
     *
     * <p>Beranda Komiku hanya memuat 40 judul. Katalog lengkap situs punya
     * urutan yang sama (terbaru di atas), jadi halaman katalog dipakai sebagai
     * lanjutan umpan: daftar terus bertambah selama pengguna menggulir, dan
     * berhenti sendiri saat situs tidak lagi menyediakan halaman berikutnya.
     *
     * <p>Dimulai dari halaman 2 karena halaman 1 sudah tercakup 40 judul
     * beranda — memuatnya lagi hanya akan menggandakan baris yang sama.
     */
    private void maybeLoadMoreFeed() {
        if (tab != Tab.NEW_CHAPTERS || feedLoading || feedEnded) return;
        if (searchField != null && !searchField.getText().toString().trim().isEmpty()) return;
        if (!(list.getLayoutManager() instanceof LinearLayoutManager)) return;
        LinearLayoutManager layout = (LinearLayoutManager) list.getLayoutManager();
        int lastVisible = layout.findLastVisibleItemPosition();
        int total = shownAdapter == null ? 0 : shownAdapter.getItemCount();
        // Muat lebih awal (lima baris sebelum ujung) supaya tidak terasa kosong.
        if (lastVisible < total - 5) return;
        loadMoreFeed();
    }

    /** Ambil satu halaman katalog berikutnya dan tambahkan ke daftar. */
    private void loadMoreFeed() {
        final int page = feedNextPage;
        feedLoading = true;
        worker.execute(() -> {
            // Katalog tersimpan lebih dulu: tidak perlu jaringan sama sekali,
            // dan isinya ribuan judul jauh melampaui 40 beranda.
            if (feedSource == null && catalog != null && catalog.exists()) {
                feedSource = catalog.load();
            }
            if (feedSource != null && !feedSource.isEmpty() && items.isEmpty()) {
                // Daftar belum berisi apa pun (mis. dibuka dari tab lain):
                // seluruh katalog tersimpan langsung jadi isi umpan.
                final List<KomikuManga> all = feedSource;
                main.post(() -> {
                    feedLoading = false;
                    if (isFinishing() || tab != Tab.NEW_CHAPTERS) return;
                    appendFeed(all);
                    feedEnded = true;
                });
                return;
            }
            if (feedSource != null && !feedSource.isEmpty()) {
                int from = Math.min(items.size(), feedSource.size());
                List<KomikuManga> slice = new ArrayList<>();
                int until = Math.min(from + FEED_PAGE_SIZE, feedSource.size());
                for (int i = from; i < until; i++) slice.add(feedSource.get(i));
                final List<KomikuManga> local = slice;
                final boolean done = until >= feedSource.size();
                main.post(() -> {
                    feedLoading = false;
                    if (isFinishing() || tab != Tab.NEW_CHAPTERS) return;
                    if (local.isEmpty()) {
                        feedEnded = false; // katalog masih ada; tunggu diisi ulang
                        return;
                    }
                    appendFeed(local);
                    feedEnded = done;
                    prefillFeed();
                });
                return;
            }
            List<KomikuManga> more = new ArrayList<>();
            int next = 0;
            String error = null;
            try {
                KomikuSource.CatalogPage batch = source.catalogPageWithNext(page);
                more = batch.items;
                next = batch.nextPage;
            } catch (Throwable failure) {
                error = failure.getMessage();
            }
            final List<KomikuManga> loaded = more;
            final int following = next;
            final String message = error;
            main.post(() -> {
                feedLoading = false;
                if (isFinishing() || tab != Tab.NEW_CHAPTERS) return;
                if (message != null) {
                    // Jaringan gagal: berhenti mencoba sampai pengguna memuat
                    // ulang, supaya tidak membanjiri sumber dengan percobaan.
                    feedEnded = true;
                    return;
                }
                if (loaded.isEmpty()) {
                    feedEnded = true;
                    return;
                }
                int added = appendFeed(loaded);
                feedNextPage = following > page ? following : page + 1;
                if (added == 0) {
                    feedEmptyStreak++;
                    if (following == 0 || feedEmptyStreak >= FEED_MAX_EMPTY_PAGES) {
                        feedEnded = true;
                        return;
                    }
                    // Halaman ini hanya mengulang judul yang sudah tampil;
                    // lanjut ke halaman berikutnya tanpa mengakhiri umpan.
                    prefillFeed();
                    return;
                }
                feedEmptyStreak = 0;
                if (following == 0) feedEnded = true;
                prefillFeed();
            });
        });
    }


    /**
     * Tambahkan judul baru ke umpan tanpa menggandakan baris.
     *
     * <p>Batas antar halaman bisa tumpang tindih saat ada judul baru masuk di
     * atas, jadi judul yang sudah tampil dilewati. Perubahan dikirim ke adapter
     * sebagai rentang sisipan — bukan {@code notifyDataSetChanged()} — supaya
     * posisi gulir pengguna tidak melompat.
     *
     * @return jumlah baris yang benar-benar ditambahkan
     */
    private int appendFeed(List<KomikuManga> loaded) {
        Set<String> shown = new HashSet<>();
        for (KomikuManga manga : items) shown.add(manga.id);
        int start = items.size();
        for (KomikuManga manga : loaded) {
            if (manga == null || manga.id.isEmpty()) continue;
            if (!shown.add(manga.id)) continue;
            items.add(manga);
        }
        int added = items.size() - start;
        if (added > 0) {
            rowAdapter.submitAndNotifyInsert(items, start, added);
            if (tab == Tab.NEW_CHAPTERS && mode == Mode.ROWS) {
                statusLine.setText(items.size() + " chapter terbaru");
            }
        }
        return added;
    }


    /**
     * Lanjutkan isi awal umpan sampai {@link #FEED_PREFILL}.
     *
     * <p>Dipanggil sekali dari ujung pemuatan: berhenti sendiri saat jumlah
     * tercapai, saat situs tidak punya halaman berikutnya, atau saat katalog
     * tersimpan sudah habis.
     */
    private void prefillFeed() {
        if (feedEnded || feedLoading || isFinishing()) return;
        if (tab != Tab.NEW_CHAPTERS) return;
        // Saat pengguna mencari, daftar berisi hasil pencarian — bukan umpan.
        // Menambahkan umpan di sini akan mencampur hasil dan menimpa baris
        // status "N hasil untuk ...".
        if (searchField != null && !searchField.getText().toString().trim().isEmpty()) return;
        if (items.size() >= FEED_PREFILL) return;
        list.post(this::loadMoreFeed);
    }

    /** Siapkan umpan dari awal: 40 judul beranda, lanjut halaman katalog 2. */
    private void resetFeed() {
        feedNextPage = 2;
        feedLoading = false;
        feedEnded = false;
        feedSource = null;
        feedEmptyStreak = 0;
    }

    private void loadTab(Tab which) {
        String query = searchField == null ? "" : searchField.getText().toString().trim();
        if (!query.isEmpty()) {
            runSearch();
            return;
        }
        final int request = ++generation;
        showLoading("Memuat…");
        worker.execute(() -> {
            List<KomikuManga> result = new ArrayList<>();
            String error = null;
            try {
                if (which == Tab.POPULAR) result = source.popular();
                else if (which == Tab.NEW_TITLES) result = catalogTitles();
                else if (which == Tab.FAVORITES) result = store.favorites();
                else if (which == Tab.HISTORY) result = historyItems();
                else result = source.latest();
            } catch (Throwable failure) {
                error = failure.getMessage();
            }
            final List<KomikuManga> loaded = result;
            final String message = error;
            main.post(() -> {
                if (request != generation || isFinishing()) return;
                hideLoading();
                mode = which.usesRows() ? Mode.ROWS : Mode.GRID;
                searchMode = false;
                items.clear();
                items.addAll(loaded);
                if (which == Tab.NEW_CHAPTERS) resetFeed();
                switchContent(mode == Mode.ROWS ? rowAdapter : gridAdapter);
                publishItems();
                refreshContinueRail();
                if (message != null) {
                    errorText.setText("Gagal memuat: " + message);
                    errorBox.setVisibility(View.VISIBLE);
                    statusLine.setText("Gagal memuat");
                } else if (loaded.isEmpty()) {
                    statusLine.setText(which == Tab.FAVORITES ? "Belum ada judul favorit."
                            : which == Tab.HISTORY ? "Belum ada riwayat baca."
                            : "Daftar kosong dari sumber.");
                } else if (which == Tab.NEW_CHAPTERS) {
                    statusLine.setText(loaded.size() + " chapter terbaru");
                    ensureCatalog();
                    // Beranda hanya 40 judul: lanjutkan sendiri. Katalog
                    // tersimpan (bila ada) dipakai lebih dulu supaya daftar
                    // langsung berisi ribuan judul tanpa menunggu jaringan.
                    prefillFeed();
                } else if (which == Tab.NEW_TITLES && catalog != null && catalog.exists()) {
                    statusLine.setText(loaded.size() + " judul dari katalog lengkap");
                } else if (which == Tab.FAVORITES) {
                    statusLine.setText(loaded.size() + " manga favorit");
                } else if (which == Tab.HISTORY) {
                    statusLine.setText(loaded.size() + " manga dalam riwayat");
                } else {
                    statusLine.setText(loaded.size() + " judul");
                }
            });
        });
    }

    /** Riwayat baca sebagai baris daftar, terbaru di atas. */
    private List<KomikuManga> historyItems() {
        List<KomikuManga> out = new ArrayList<>();
        for (KomikuStore.Entry entry : store.history()) {
            if (entry.mangaId.isEmpty()) continue;
            out.add(new KomikuManga(entry.mangaId, entry.title,
                    KomikuConfig.BASE + "/manga/" + entry.mangaId + "/", entry.coverUrl,
                    "", entry.chapterLabel, ""));
        }
        return out;
    }

    private void showLoading(String text) {
        spinner.setVisibility(View.VISIBLE);
        errorBox.setVisibility(View.GONE);
        statusLine.setText(text);
    }

    private void hideLoading() {
        spinner.setVisibility(View.GONE);
    }

    /**
     * Berikan daftar ke adapter yang sedang tampil.
     *
     * <p>Urutannya penting: data diserahkan ke adapter <b>sebelum</b> adapter
     * dipasang, dan kedua adapter selalu punya salinan sendiri — jadi tidak ada
     * saat di mana RecyclerView meminta posisi yang belum ada datanya.
     */
    private void publishItems() {
        if (shownContent == gridAdapter) gridAdapter.submit(items);
        else rowAdapter.submit(items);
        if (headerAdapter != null) headerAdapter.notifyDataSetChanged();
    }

    /** Pasang rail riwayat + isi daftar tanpa mengganti adapter bila sudah aktif. */
    private void switchContent(RecyclerView.Adapter<?> content) {
        if (shownContent == content) return;
        shownContent = content;
        headerAdapter = new HeaderAdapter();
        shownAdapter = new ConcatAdapter(headerAdapter, content);
        list.setAdapter(shownAdapter);
    }

    private void open(KomikuManga manga) {
        KomikuStore.Entry last = store.lastFor(manga.id);
        // Hasil pencarian selalu membuka halaman detail: pengguna sedang
        // mencari judul, bukan melanjutkan chapter yang pernah dibaca.
        if (searchMode) {
            openDetailFor(manga);
            return;
        }
        if (tab.usesRows() && last != null && !last.chapterUrl.isEmpty()) {
            openChapterDirect(manga, last.chapterUrl, last.chapterLabel);
            return;
        }
        Intent intent = new Intent(this, KomikuDetailActivity.class);
        intent.putExtra(KomikuDetailActivity.EXTRA_URL, manga.url);
        intent.putExtra(KomikuDetailActivity.EXTRA_TITLE, manga.title);
        intent.putExtra(KomikuDetailActivity.EXTRA_COVER, manga.coverUrl);
        startActivity(intent);
    }

    private void openChapterDirect(KomikuManga manga, String chapterUrl, String chapterLabel) {
        if (chapterUrl == null || chapterUrl.isEmpty()) {
            openDetailFor(manga);
            return;
        }
        Intent intent = new Intent(this, KomikuReaderActivity.class);
        intent.putExtra(KomikuReaderActivity.EXTRA_CHAPTER_URL, chapterUrl);
        intent.putExtra(KomikuReaderActivity.EXTRA_CHAPTER_LABEL, chapterLabel);
        intent.putExtra(KomikuReaderActivity.EXTRA_MANGA_ID, manga.id);
        intent.putExtra(KomikuReaderActivity.EXTRA_MANGA_TITLE, manga.title);
        intent.putExtra(KomikuReaderActivity.EXTRA_COVER, manga.coverUrl);
        startActivity(intent);
    }

    private void openDetailFor(KomikuManga manga) {
        Intent intent = new Intent(this, KomikuDetailActivity.class);
        intent.putExtra(KomikuDetailActivity.EXTRA_URL, manga.url);
        intent.putExtra(KomikuDetailActivity.EXTRA_TITLE, manga.title);
        intent.putExtra(KomikuDetailActivity.EXTRA_COVER, manga.coverUrl);
        startActivity(intent);
    }

    private void showOverflowMenu() {
        // Tidak ada lagi "Ambil/Perbarui katalog": katalog berjalan sendiri di
        // latar, jadi tombol itu hanya membebani pengguna dengan keputusan yang
        // tidak perlu.
        String[] options = opsiMenuUntukTes();
        new AlertDialog.Builder(this)
                .setTitle("Menu Manga Server 3")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showPreferences();
                    else {
                        searchField.setText("");
                        loadTab(tab);
                    }
                })
                .setNegativeButton("Tutup", null)
                .show();
    }

    /**
     * Ambil seluruh katalog, halaman demi halaman, lalu simpan.
     *
     * <p>Berjalan sendiri: dipanggil saat beranda dibuka bila katalog belum ada
     * atau sudah lama. Tidak ada tombol dan tidak ada dialog — pengguna tetap
     * bisa membaca daftar yang sudah tampil sambil menunggu.
     */
    private void fetchWholeCatalog(int startPage, java.util.List<KomikuManga> seed) {
        if (catalogFetching) return;
        catalogFetching = true;
        catalogStop = false;
        catalogWorker.execute(() -> {
            KomikuCatalog.Result result = catalog.fetchAll(source,
                    (page, collected, hasNext) -> {
                        if (catalogStop || isFinishing()) return false;
                        if (page % 20 == 0) {
                            try {
                                // Disimpan sebagai belum lengkap: kalau pengambilan
                                // terhenti, sisa halaman dilanjutkan lain waktu
                                // alih-alih diulang dari halaman satu.
                                catalog.save(collected, page, false);
                            } catch (Exception ignored) {
                                // Percobaan simpan berikutnya akan menggantinya.
                            }
                        }
                        return true;
                    }, startPage, seed);
            try {
                if (!result.items.isEmpty()) {
                    catalog.save(result.items, result.pagesRead, result.complete);
                }
            } catch (Exception ignored) {
                // Katalog lama tetap dipakai bila penyimpanan gagal.
            }
            main.post(() -> {
                catalogFetching = false;
                if (isFinishing()) return;
                feedSource = null;
                // Saat pengguna sedang mencari, daftar berisi hasil pencarian.
                // Menulis baris status di sini akan menimpanya menjadi
                // "N chapter terbaru" padahal yang tampil adalah hasil
                // pencarian.
                if (searchField != null
                        && !searchField.getText().toString().trim().isEmpty()) {
                    return;
                }
                if (tab == Tab.NEW_CHAPTERS) {
                    statusLine.setText(items.size() + " chapter terbaru");
                    prefillFeed();
                }
            });
        });
    }

    /**
     * Mulai pengambilan katalog bila perlu.
     *
     * <p>Perlu bila belum ada, atau bila berkasnya lebih tua dari
     * {@link #CATALOG_MAX_AGE_MS}. Berjalan di latar dan tidak pernah
     * menghalangi tampilan.
     */
    private void ensureCatalog() {
        if (catalog == null || catalogFetching) return;
        KomikuCatalog.Stats stats = catalog.stats();
        if (stats.isEmpty()) {
            fetchWholeCatalog(1, null);
            return;
        }
        boolean fresh = System.currentTimeMillis() - stats.updatedAt < CATALOG_MAX_AGE_MS;
        if (!stats.complete) {
            // Pengambilan sebelumnya terhenti: lanjutkan dari halaman terakhir,
            // jangan mengulang dari awal dan jangan mengirim ulang permintaan.
            fetchWholeCatalog(stats.pages + 1, catalog.load());
            return;
        }
        if (fresh) return;
        fetchWholeCatalog(1, null);
    }

    private void showPreferences() {
        String[] options = {"Tema tampilan", "Tentang Manga Server 3"};
        new AlertDialog.Builder(this)
                .setTitle("Pengaturan Manga Server 3")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showThemeDialog();
                    else showAbout();
                })
                .setNegativeButton("Tutup", null)
                .show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("Tentang Manga Server 3")
                .setMessage("Sumber: Komiku (komiku.org)\n\n"
                        + "Katalog, detail, dan halaman chapter dibaca langsung dari "
                        + "halaman situs saat dibuka. Riwayat dan progres membaca "
                        + "disimpan di perangkat, dan bisa disinkronkan dengan "
                        + "Manga Server 2 lewat bar Tersinkron di layar detail.\n\n"
                        + "Unduhan offline belum tersedia untuk sumber ini.")
                .setPositiveButton("Tutup", null)
                .show();
    }

    private void showThemeDialog() {
        // Urutan sama dengan nilai Prefs.THEME_* supaya indeks bisa dipakai
        // langsung dan "Sepia" tidak tampil sebagai "Ikuti Sistem".
        String[] options = {"Terang (Light)", "Gelap (Dark)", "Sepia", "Ikuti Sistem (System)"};
        int checked = new Prefs(this).theme();
        if (checked < Prefs.THEME_LIGHT || checked > Prefs.THEME_SYSTEM) {
            checked = Prefs.THEME_SYSTEM;
        }
        new AlertDialog.Builder(this)
                .setTitle("Tema Manga Server 3")
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    new Prefs(this).setTheme(which);
                    dialog.dismiss();
                    recreate();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    // --------------------------------------------------------------- siklus

    @Override protected void onResume() {
        super.onResume();
        int current = new Prefs(this).resolvedTheme();
        if (activeTheme != -1 && current != activeTheme) {
            recreate();
            return;
        }
        if (store == null) return;
        refreshContinueRail();
        if (tab == Tab.HISTORY && mode == Mode.ROWS) {
            items.clear();
            items.addAll(historyItems());
            rowAdapter.submit(items);
            statusLine.setText(items.size() + " manga dalam riwayat");
        }
    }

    @Override protected void onStop() {
        super.onStop();
        // Layar ditinggalkan: pengambilan katalog berhenti, supaya tidak ada
        // ratusan permintaan yang berjalan di latar tanpa sepengetahuan Master.
        // Sisa halaman dilanjutkan saat beranda dibuka lagi.
        catalogStop = true;
    }

    @Override protected void onDestroy() {
        main.removeCallbacks(searchRunnable);
        main.removeCallbacks(chromeRelease);
        catalogStop = true;
        worker.shutdownNow();
        catalogWorker.shutdownNow();
        super.onDestroy();
    }

    // -------------------------------------------------------------- adapter

    /** Satu item header berisi rail riwayat; tidak didaur ulang. */
    private final class HeaderAdapter extends RecyclerView.Adapter<HeaderAdapter.Holder> {
        final class Holder extends RecyclerView.ViewHolder {
            Holder(View item) {
                super(item);
                setIsRecyclable(false);
            }
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            ViewGroup.LayoutParams params = continueRail.getLayoutParams();
            params = params == null
                    ? new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
                    : new RecyclerView.LayoutParams(params);
            continueRail.setLayoutParams(params);
            id.shuyasal.kuromaku.ui.TvFocus.focusable(continueRail, palette);
            return new Holder(continueRail);
        }

        @Override public void onBindViewHolder(Holder holder, int position) {
            // Isi rail diperbarui lewat refreshContinueRail().
            //
            // Rail SENGAJA tidak disorot sebagai satu kotak: isinya daftar
            // horizontal yang harus bisa dipilih satu per satu dengan remote.
            // Kalau rail diblokir, seluruh judul di dalamnya menjadi tidak bisa
            // dijangkau.
        }

        @Override public int getItemCount() {
            return continueAdapter == null || continueAdapter.getItemCount() == 0 ? 0 : 1;
        }
    }

    /** Kartu ringkas: cover 64x90 + judul 2 baris + "Ch. X · Y%" + bar. */
    private final class ContinueAdapter extends RecyclerView.Adapter<ContinueAdapter.Holder> {
        private final List<ContinueItem> entries = new ArrayList<>();

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView cover;
            final TextView title;
            final TextView meta;
            final ProgressBar progress;

            Holder(View item) {
                super(item);
                cover = item.findViewWithTag("komiku_continue_cover");
                title = item.findViewWithTag("komiku_continue_title");
                meta = item.findViewWithTag("komiku_continue_meta");
                progress = item.findViewWithTag("komiku_continue_progress");
            }
        }

        void submit(List<ContinueItem> recent) {
            entries.clear();
            entries.addAll(recent);
            notifyDataSetChanged();
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(KomikuActivity.this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(Ui.dp(KomikuActivity.this, 6), Ui.dp(KomikuActivity.this, 6),
                    Ui.dp(KomikuActivity.this, 8), Ui.dp(KomikuActivity.this, 6));
            card.setBackground(Ui.ripple(KomikuActivity.this, palette.surface,
                    palette.divider, 14, palette.ripple));
            // Televisi: kartu adalah tujuan fokus, bukan teks di dalamnya.
            card.setFocusable(true);
            card.setFocusableInTouchMode(false);
            card.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            id.shuyasal.kuromaku.ui.TvFocus.focusable(card, palette);

            FrameLayout box = new FrameLayout(KomikuActivity.this);
            box.setBackground(Ui.rounded(palette.surface, palette.divider, 10,
                    KomikuActivity.this));
            box.setClipToOutline(true);
            ImageView image = new ImageView(KomikuActivity.this);
            image.setTag("komiku_continue_cover");
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            box.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            card.addView(box, new LinearLayout.LayoutParams(
                    Ui.dp(KomikuActivity.this, 64), Ui.dp(KomikuActivity.this, 90)));

            LinearLayout text = new LinearLayout(KomikuActivity.this);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setPadding(Ui.dp(KomikuActivity.this, 8), 0, 0, 0);

            TextView name = Ui.label(KomikuActivity.this, "", 12, palette.text);
            name.setTag("komiku_continue_title");
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            text.addView(name);

            TextView info = Ui.label(KomikuActivity.this, "", 10, palette.muted);
            info.setTag("komiku_continue_meta");
            info.setSingleLine(true);
            info.setEllipsize(TextUtils.TruncateAt.END);
            info.setPadding(0, Ui.dp(KomikuActivity.this, 2), 0, 0);
            text.addView(info);

            ProgressBar bar = new ProgressBar(KomikuActivity.this, null,
                    android.R.attr.progressBarStyleHorizontal);
            bar.setTag("komiku_continue_progress");
            bar.setMax(100);
            bar.setIndeterminate(false);
            bar.setProgressTintList(ColorStateList.valueOf(palette.accent));
            bar.setProgressBackgroundTintList(ColorStateList.valueOf(palette.divider));
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(KomikuActivity.this, 4));
            barParams.topMargin = Ui.dp(KomikuActivity.this, 6);
            text.addView(bar, barParams);

            card.addView(text, new LinearLayout.LayoutParams(
                    Ui.dp(KomikuActivity.this, CONTINUE_TEXT_DP),
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            id.shuyasal.kuromaku.ui.TvFocus.focusable(card, palette);
            return new Holder(card);
        }

        @Override public void onBindViewHolder(Holder holder, int position) {
            // Televisi: sorotan dipasang lagi setelah latar kartu diganti
            // saat item dipakai ulang, supaya tidak pernah hilang.
            id.shuyasal.kuromaku.ui.TvFocus.keepRing(holder.itemView, palette, 14);
            if (position < 0 || position >= entries.size()) return;
            ContinueItem entry = entries.get(position);
            holder.title.setText(entry.title);
            holder.title.setTextColor(palette.text);
            holder.meta.setTextColor(palette.muted);
            // Judul dari server pasangan belum punya persen di sumber ini, jadi
            // yang ditampilkan hanya chapter-nya — bukan "0%" yang menyesatkan.
            if (entry.fromPartner) {
                holder.meta.setText(entry.chapterLabel.isEmpty()
                        ? "Dari Manga Server 2" : entry.chapterLabel + " · Manga Server 2");
            } else {
                holder.meta.setText(entry.chapterLabel.isEmpty()
                        ? entry.percent + "%" : entry.chapterLabel + " · " + entry.percent + "%");
            }
            holder.progress.setProgress(entry.percent);
            holder.progress.setVisibility(entry.fromPartner ? View.INVISIBLE : View.VISIBLE);
            KomikuImages.load(entry.coverUrl, holder.cover);
            holder.itemView.setOnClickListener(v -> {
                KomikuManga manga = new KomikuManga(entry.mangaId, entry.title,
                        KomikuConfig.BASE + "/manga/" + entry.mangaId + "/", entry.coverUrl,
                        "", "", "");
                if (entry.fromPartner || entry.chapterUrl.isEmpty()) {
                    openDetailFor(manga);
                } else {
                    openChapterDirect(manga, entry.chapterUrl, entry.chapterLabel);
                }
            });
        }

        @Override public int getItemCount() {
            return entries.size();
        }
    }

    /**
     * Baris daftar: cover 48x70 + judul + "Ch. N · waktu".
     *
     * <p>Adapter menyimpan salinan datanya sendiri. Sebelumnya adapter membaca
     * daftar milik Activity yang dikosongkan lebih dulu lalu diisi ulang; saat
     * RecyclerView menata di antara kedua langkah itu, posisi yang diminta
     * sudah tidak ada dan aplikasi berhenti dengan IndexOutOfBoundsException.
     */
    private final class RowAdapter extends RecyclerView.Adapter<RowAdapter.Holder> {
        private final List<KomikuManga> entries = new ArrayList<>();

        RowAdapter() {
            setHasStableIds(true);
        }

        void submit(List<KomikuManga> data) {
            entries.clear();
            entries.addAll(data);
            notifyDataSetChanged();
        }

        /**
         * Tambahkan rentang di ujung tanpa menggulang seluruh daftar.
         *
         * <p>Dipakai umpan berkelanjutan: posisi gulir pengguna dan isi baris
         * yang sudah terpasang dibiarkan apa adanya, hanya baris baru yang
         * diberitahukan.
         */
        void submitAndNotifyInsert(List<KomikuManga> data, int start, int count) {
            entries.clear();
            entries.addAll(data);
            if (start < 0 || count <= 0) {
                notifyDataSetChanged();
                return;
            }
            notifyItemRangeInserted(start, count);
        }

        @Override public long getItemId(int position) {
            return position < 0 || position >= entries.size()
                    ? RecyclerView.NO_ID : entries.get(position).id.hashCode();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView cover;
            final TextView title;
            final TextView subtitle;

            Holder(View item) {
                super(item);
                cover = item.findViewWithTag("komiku_row_cover");
                title = item.findViewWithTag("komiku_row_title");
                subtitle = item.findViewWithTag("komiku_row_subtitle");
            }
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(KomikuActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(KomikuActivity.this, 10), Ui.dp(KomikuActivity.this, 8),
                    Ui.dp(KomikuActivity.this, 10), Ui.dp(KomikuActivity.this, 8));

            RatioFrameLayout box = new RatioFrameLayout(KomikuActivity.this);
            box.setRatio(0.68f);
            ImageView image = new ImageView(KomikuActivity.this);
            image.setTag("komiku_row_cover");
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(palette.surface);
            box.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            row.addView(box, new LinearLayout.LayoutParams(
                    Ui.dp(KomikuActivity.this, 48), Ui.dp(KomikuActivity.this, 70)));

            LinearLayout text = new LinearLayout(KomikuActivity.this);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setPadding(Ui.dp(KomikuActivity.this, 12), 0, 0, 0);

            TextView name = Ui.label(KomikuActivity.this, "", 14, palette.text);
            name.setTag("komiku_row_title");
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            text.addView(name);

            TextView info = Ui.label(KomikuActivity.this, "", 12, palette.muted);
            info.setTag("komiku_row_subtitle");
            info.setMaxLines(2);
            info.setEllipsize(TextUtils.TruncateAt.END);
            info.setPadding(0, Ui.dp(KomikuActivity.this, 2), 0, 0);
            text.addView(info);

            row.addView(text, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            id.shuyasal.kuromaku.ui.TvFocus.focusable(row, palette);
            return new Holder(row);
        }

        @Override public void onBindViewHolder(Holder holder, int position) {
            // Televisi: sorotan dipasang lagi setelah latar kartu diganti
            // saat item dipakai ulang, supaya tidak pernah hilang.
            id.shuyasal.kuromaku.ui.TvFocus.keepRing(holder.itemView, palette, 14);
            if (position < 0 || position >= entries.size()) return;
            KomikuManga manga = entries.get(position);
            holder.title.setText(manga.title);
            holder.title.setTextColor(palette.text);
            holder.subtitle.setTextColor(palette.muted);
            StringBuilder info = new StringBuilder();
            String chapter = chapterShort(manga.latestChapter);
            if (!chapter.isEmpty()) info.append(chapter);
            if (!manga.updated.isEmpty()) {
                if (info.length() > 0) info.append(" · ");
                info.append(manga.updated);
            }
            if (info.length() == 0) info.append("Chapter terbaru belum tersedia");
            holder.subtitle.setText(info.toString());
            holder.itemView.setOnClickListener(v -> open(manga));
            KomikuImages.load(manga.coverUrl, holder.cover);
        }

        @Override public int getItemCount() {
            return entries.size();
        }
    }

    /**
     * Grid katalog: cover rasio 0,68 + judul 2 baris + chapter/waktu.
     *
     * <p>Sama seperti {@link RowAdapter}, adapter memegang salinan datanya
     * sendiri supaya pengosongan daftar di Activity tidak pernah terbaca
     * sebagai "posisi tidak ada".
     */
    private final class GridAdapter extends RecyclerView.Adapter<GridAdapter.Holder> {
        private final List<KomikuManga> entries = new ArrayList<>();

        GridAdapter() {
            setHasStableIds(true);
        }

        void submit(List<KomikuManga> data) {
            entries.clear();
            entries.addAll(data);
            notifyDataSetChanged();
        }

        @Override public long getItemId(int position) {
            return position < 0 || position >= entries.size()
                    ? RecyclerView.NO_ID : entries.get(position).id.hashCode();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final View coverBox;
            final ImageView cover;
            final TextView title;
            final TextView subtitle;

            Holder(View item) {
                super(item);
                coverBox = item.findViewWithTag("komiku_cover_box");
                cover = item.findViewWithTag("komiku_cover");
                title = item.findViewWithTag("komiku_title");
                subtitle = item.findViewWithTag("komiku_subtitle");
            }
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(KomikuActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(Ui.dp(KomikuActivity.this, 4), Ui.dp(KomikuActivity.this, 4),
                    Ui.dp(KomikuActivity.this, 4), Ui.dp(KomikuActivity.this, 8));

            RatioFrameLayout box = new RatioFrameLayout(KomikuActivity.this);
            box.setRatio(0.68f);
            box.setTag("komiku_cover_box");
            ImageView image = new ImageView(KomikuActivity.this);
            image.setTag("komiku_cover");
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(palette.surface);
            box.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            card.addView(box, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView name = Ui.label(KomikuActivity.this, "", 12, palette.text);
            name.setTag("komiku_title");
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(name);

            TextView info = Ui.label(KomikuActivity.this, "", 11, palette.muted);
            info.setTag("komiku_subtitle");
            info.setMaxLines(3);
            info.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(info);
            id.shuyasal.kuromaku.ui.TvFocus.focusable(card, palette);
            return new Holder(card);
        }

        @Override public void onBindViewHolder(Holder holder, int position) {
            // Televisi: sorotan dipasang lagi setelah latar kartu diganti
            // saat item dipakai ulang, supaya tidak pernah hilang.
            id.shuyasal.kuromaku.ui.TvFocus.keepRing(holder.itemView, palette, 14);
            if (position < 0 || position >= entries.size()) return;
            KomikuManga manga = entries.get(position);
            holder.title.setText(manga.title);
            holder.title.setTextColor(palette.text);
            holder.subtitle.setTextColor(palette.muted);
            StringBuilder info = new StringBuilder();
            String chapter = chapterShort(manga.latestChapter);
            if (!chapter.isEmpty()) info.append(chapter);
            if (!manga.updated.isEmpty()) {
                if (info.length() > 0) info.append("\n");
                info.append(manga.updated);
            }
            holder.subtitle.setText(info.toString());
            holder.coverBox.setOnClickListener(v -> open(manga));
            KomikuImages.load(manga.coverUrl, holder.cover);
        }

        @Override public int getItemCount() {
            return entries.size();
        }
    }



    // ------------------------------------------------ kait pengujian



    /** Hanya untuk pengujian: isi 40 baris pertama seperti beranda situs. */
    void isiUmpanAwalUntukTes() {
        List<KomikuManga> awal = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            String slug = "komik-nomor-" + (i + 1);
            awal.add(new KomikuManga(slug, "Komik Nomor " + (i + 1),
                    KomikuConfig.BASE + "/manga/" + slug + "/", "", "Manhwa", "", ""));
        }
        publishForTest(awal, true);
        resetFeed();
    }

    /** Hanya untuk pengujian: siapkan umpan lalu isi sampai target awal. */
    void muatUmpanUntukTes() {
        resetFeed();
        prefillFeed();
    }

    /** Hanya untuk pengujian: ambil satu batch umpan berikutnya. */
    void tambahUmpanUntukTes() {
        if (feedEnded || feedLoading) return;
        loadMoreFeed();
    }

    /** Hanya untuk pengujian: jumlah judul unik yang tampil. */
    int jumlahItemUnikUntukTes() {
        Set<String> ids = new HashSet<>();
        for (KomikuManga manga : items) ids.add(manga.id);
        return ids.size();
    }


    /** Hanya untuk pengujian: apakah daftar memakai mode baris. */
    boolean modeBarisUntukTes() {
        return mode == Mode.ROWS;
    }

    /** Hanya untuk pengujian: daftar opsi menu titik tiga. */
    static String[] opsiMenuUntukTes() {
        return new String[]{"Pengaturan", "Muat ulang"};
    }

    /** Hanya untuk pengujian: ketik di kolom pencarian seperti pengguna. */
    void ketikUntukTes(String query) {
        if (searchField != null) searchField.setText(query);
    }

    /** Hanya untuk pengujian: palet yang sedang dipakai. */
    Palette paletteUntukTes() {
        return palette;
    }

    /** Hanya untuk pengujian: pilih tab menurut urutan chip. */
    void pilihTabUntukTes(int index) {
        Tab[] all = Tab.values();
        if (index < 0 || index >= all.length) return;
        selectTab(all[index]);
    }

    /** Hanya untuk pengujian: jumlah baris yang sedang tampil. */
    int jumlahItemUntukTes() {
        return shownContent == null ? 0 : shownContent.getItemCount();
    }

    /** Hanya untuk pengujian: teks baris status. */
    String statusUntukTes() {
        return statusLine == null ? "" : statusLine.getText().toString();
    }

    /** Hanya untuk pengujian: jalankan pencarian seperti pengguna mengetik. */
    void cariUntukTes(String query) {
        if (searchField != null) searchField.setText(query);
        runSearch();
    }

    /** Hanya untuk pengujian: daftar beranda. */
    RecyclerView cariDaftarUntukTes() {
        return list;
    }

    /** Hanya untuk pengujian: terbitkan daftar seperti hasil jaringan. */
    void publishForTest(List<KomikuManga> data, boolean rows) {
        mode = rows ? Mode.ROWS : Mode.GRID;
        items.clear();
        items.addAll(data);
        switchContent(rows ? rowAdapter : gridAdapter);
        publishItems();
    }

    // ------------------------------------------------------------- bantuan

    /** "Chapter 130" menjadi "Ch. 130"; label lain dikembalikan apa adanya. */
    static String chapterShort(String label) {
        if (label == null) return "";
        String text = label.trim();
        if (text.isEmpty()) return "";
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("chapter ")) return "Ch." + text.substring("chapter".length());
        return text;
    }
}