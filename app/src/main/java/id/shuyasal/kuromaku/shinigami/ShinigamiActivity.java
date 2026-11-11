package id.shuyasal.kuromaku.shinigami;

import android.graphics.Typeface;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.RatioFrameLayout;
import id.shuyasal.kuromaku.ui.SectionDrawer;
import id.shuyasal.kuromaku.ui.ThemeManager;
import id.shuyasal.kuromaku.ui.Ui;

public class ShinigamiActivity extends id.shuyasal.kuromaku.ui.TvActivity {

    private static final String STATE_QUERY = "shinigami_query";
    private static final String STATE_PAGE = "shinigami_page";
    private static final String STATE_SORT = "shinigami_sort";
    private static final String STATE_GENRE = "shinigami_genre";
    private static final String STATE_STATUS = "shinigami_status";

    /** Jumlah kartu "Lanjutkan Membaca" yang tampil di beranda. */
    private static final int CONTINUE_LIMIT = 8;
    private static final int CONTINUE_TEXT_DP = 104;
    /** Jeda pencarian sebelum memanggil sumber. */
    private static final long SEARCH_DEBOUNCE_MS = 350L;
    /** Durasi animasi sembunyikan/tampilkan header beranda. */
    private static final long CHROME_ANIM_MS = 180L;

    enum Mode {
        CATALOGUE,
        NEWEST_CHAPTERS,
        HISTORY,
        FAVORITES,
        DOWNLOADS
    }

    /**
     * Tab beranda. Labelnya permanen: menekan tab tidak pernah mengubah teks
     * tombol, dan tiap tab meminta jenis data yang berbeda ke sumber.
     */
    enum Tab {
        /** Default beranda: manga yang chapter-nya paling baru diperbarui. */
        NEW_CHAPTERS("Chapter Terbaru"),
        POPULAR("Terpopuler"),
        NEW_TITLES("Manga Terbaru"),
        HISTORY("Riwayat"),
        FAVORITES("Favorit"),
        DOWNLOADS("Unduhan");

        final String label;
        Tab(String label) { this.label = label; }

        boolean isCatalogue() {
            return this == POPULAR || this == NEW_TITLES || this == NEW_CHAPTERS;
        }
    }

    /**
     * Data satu tab: daftar, kursor pagination, dan posisi scroll disimpan
     * terpisah supaya berpindah tab tidak mencampur halaman atau mengembalikan
     * pengguna ke atas.
     */
    static final class TabState {
        final List<ShinigamiManga> items = new ArrayList<>();
        final List<Row> rows = new ArrayList<>();
        int page = 1;
        int totalPages = 1;
        long totalRecords;
        boolean loaded;
        int scrollPosition;
        int scrollOffset;
        String statusText = "";
    }

    static final class Row {
        final String mangaId;
        final String title;
        final String coverUrl;
        final String chapterId;
        final double chapterNumber;
        final String timestamp;

        Row(String mangaId, String title, String coverUrl, String chapterId,
            double chapterNumber, String timestamp) {
            this.mangaId = mangaId == null ? "" : mangaId;
            this.title = title == null ? "" : title;
            this.coverUrl = coverUrl == null ? "" : coverUrl;
            this.chapterId = chapterId == null ? "" : chapterId;
            this.chapterNumber = chapterNumber;
            this.timestamp = timestamp == null ? "" : timestamp;
        }
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    /** Menunjuk ke daftar milik tab aktif; diganti saat berpindah tab. */
    private List<ShinigamiManga> items = new ArrayList<>();
    private List<Row> rows = new ArrayList<>();
    private final java.util.EnumMap<Tab, TabState> tabStates =
            new java.util.EnumMap<>(Tab.class);
    /** Beranda dibuka pada Chapter Terbaru. */
    private Tab tab = Tab.NEW_CHAPTERS;
    private final List<ShinigamiDownloads.Manga> downloads = new ArrayList<>();

    private Palette palette;
    private ShinigamiSource source;
    private ShinigamiStore store;
    private RecyclerView list;
    private MangaAdapter adapter;
    private RowAdapter rowAdapter;
    private DownloadsAdapter downloadsAdapter;
    private LinearLayout downloadsEmpty;
    private int downloadsGeneration;
    private TextView statusLine;
    private TextView errorText;
    private Button retryButton;
    private ProgressBar spinner;
    private EditText searchField;
    private LinearLayout filterRow;
    /** Pencarian + tab + jumlah manga: disembunyikan saat pengguna scroll turun. */
    private LinearLayout chrome;
    private boolean chromeHidden;
    /** True selama animasi + satu putaran layout; onScrolled diabaikan. */
    private boolean chromeBusy;
    private int chromeHeight;
    private int scrollDown;
    private int scrollUp;
    private final Runnable searchRunnable = this::runSearch;
    private View continueRail;
    private RecyclerView continueList;
    private ContinueAdapter continueAdapter;
    /**
     * Satu ConcatAdapter per jenis tampilan (grid, baris, unduhan). Instance-nya
     * dibuat sekali dan dipakai ulang; pagination tidak pernah memasang adapter
     * baru, karena itu yang membuat RecyclerView membuang posisi scroll.
     */
    private ConcatAdapter gridStack;
    private ConcatAdapter rowStack;
    private ConcatAdapter downloadsStack;
    private RecyclerView.Adapter<?> activeContent;
    private HeaderAdapter activeHeader;

    private int page = 1;
    private int totalPages = 1;
    private boolean loading;
    private int requestGeneration;
    private int filterGeneration;
    private String query = "";
    private ShinigamiSource.Sort sort = ShinigamiSource.Sort.LATEST;
    private String genre = "";
    private String status = "";
    private Mode mode = Mode.NEWEST_CHAPTERS;
    private int activeResolvedTheme = -1;

    static final class RetainedState {
        final List<ShinigamiManga> items;
        final List<Row> rows;
        final List<ShinigamiDownloads.Manga> downloads;
        final int page;
        final int totalPages;
        final String query;
        final ShinigamiSource.Sort sort;
        final String genre;
        final String status;
        final Mode mode;
        final int scrollPosition;
        final int scrollOffset;
        final Tab tab;
        final java.util.EnumMap<Tab, TabState> states;

        RetainedState(List<ShinigamiManga> items, List<Row> rows,
                      List<ShinigamiDownloads.Manga> downloads,
                      int page, int totalPages,
                      String query, ShinigamiSource.Sort sort, String genre,
                      String status, Mode mode, int scrollPosition, int scrollOffset) {
            this(items, rows, downloads, page, totalPages, query, sort, genre, status, mode,
                    scrollPosition, scrollOffset, null, null);
        }

        RetainedState(List<ShinigamiManga> items, List<Row> rows,
                      List<ShinigamiDownloads.Manga> downloads,
                      int page, int totalPages,
                      String query, ShinigamiSource.Sort sort, String genre,
                      String status, Mode mode, int scrollPosition, int scrollOffset,
                      Tab tab, java.util.EnumMap<Tab, TabState> states) {
            this.items = items;
            this.rows = rows;
            this.downloads = downloads;
            this.page = page;
            this.totalPages = totalPages;
            this.query = query;
            this.sort = sort;
            this.genre = genre;
            this.status = status;
            this.mode = mode;
            this.scrollPosition = scrollPosition;
            this.scrollOffset = scrollOffset;
            this.tab = tab;
            this.states = states;
        }
    }

    private static RetainedState sCachedState;

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return captureRetainedState();
    }

    private RetainedState captureRetainedState() {
        int pos = 0;
        int offset = 0;
        if (list != null && list.getLayoutManager() instanceof LinearLayoutManager) {
            LinearLayoutManager lm = (LinearLayoutManager) list.getLayoutManager();
            pos = lm.findFirstVisibleItemPosition();
            View first = lm.findViewByPosition(pos);
            offset = (first == null) ? 0 : (first.getTop() - list.getPaddingTop());
        }
        return new RetainedState(
                new ArrayList<>(items), new ArrayList<>(rows),
                new ArrayList<>(downloads), page, totalPages,
                query, sort, genre, status, mode, pos, offset,
                tab, new java.util.EnumMap<>(tabStates));
    }

    /**
     * Terapkan tema yang baru dipilih.
     *
     * AppCompatDelegate sudah menjadwalkan pembuatan ulang Activity sendiri
     * begitu mode malam berubah. Memanggil {@code recreate()} langsung membuat
     * Activity dibuat ulang dua kali (state bisa hilang pada putaran kedua),
     * jadi di sini hanya dipasang jaring aman: bila setelah satu putaran main
     * thread tema efektif masih berbeda, barulah recreate manual dijalankan.
     */
    private void applyThemeSelection() {
        sCachedState = captureRetainedState();
        main.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (new Prefs(this).resolvedTheme() != activeResolvedTheme) recreate();
        }, 150);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.applyTheme(this);
        activeResolvedTheme = new Prefs(this).resolvedTheme();
        palette = Palette.of(activeResolvedTheme);
        source = createSource();
        store = new ShinigamiStore(this);

        RetainedState retained = (RetainedState) getLastCustomNonConfigurationInstance();
        if (retained == null) {
            retained = sCachedState;
        }
        sCachedState = null;

        if (retained != null && (!retained.items.isEmpty() || !retained.rows.isEmpty()
                || retained.mode == Mode.DOWNLOADS || retained.tab != null)) {
            downloads.clear();
            downloads.addAll(retained.downloads);
            page = retained.page;
            totalPages = retained.totalPages;
            query = retained.query;
            sort = retained.sort;
            genre = retained.genre;
            status = retained.status;
            mode = retained.mode;
            if (retained.states != null) tabStates.putAll(retained.states);
            if (retained.tab != null) tab = retained.tab;
            TabState restored = state(tab);
            restored.items.clear();
            restored.items.addAll(retained.items);
            restored.rows.clear();
            restored.rows.addAll(retained.rows);
            restored.page = retained.page;
            restored.totalPages = retained.totalPages;
            restored.loaded = !retained.items.isEmpty() || !retained.rows.isEmpty();
            items = restored.items;
            rows = restored.rows;

            buildUi();
            searchField.setText(query);
            refreshContinueRail();
            buildFilterChips();

            if (mode == Mode.HISTORY) {
                useRowAdapter();
                rowAdapter.notifyDataSetChanged();
                statusLine.setText(rows.size() + " manga dalam riwayat");
            } else if (mode == Mode.DOWNLOADS) {
                showDownloads();
            } else if (mode == Mode.FAVORITES) {
                useGridAdapter();
                adapter.notifyDataSetChanged();
                statusLine.setText(items.size() + " manga favorit");
            } else if (mode == Mode.NEWEST_CHAPTERS) {
                useRowAdapter();
                rowAdapter.notifyDataSetChanged();
            } else {
                useGridAdapter();
                if (adapter != null) adapter.notifyDataSetChanged();
            }
            restoreChromeState();

            final int targetPos = retained.scrollPosition;
            final int targetOffset = retained.scrollOffset;
            list.post(() -> {
                if (list.getLayoutManager() instanceof LinearLayoutManager) {
                    ((LinearLayoutManager) list.getLayoutManager())
                            .scrollToPositionWithOffset(targetPos, targetOffset);
                }
            });
        } else {
            if (savedInstanceState != null) {
                query = savedInstanceState.getString(STATE_QUERY, "");
                page = savedInstanceState.getInt(STATE_PAGE, 1);
                sort = "rank".equals(savedInstanceState.getString(STATE_SORT))
                        ? ShinigamiSource.Sort.RANK : ShinigamiSource.Sort.LATEST;
                genre = savedInstanceState.getString(STATE_GENRE, "");
                status = savedInstanceState.getString(STATE_STATUS, "");
            }

            // Migrasi preferensi lama: dulu tombol "Terpopuler" menyimpan
            // sort=rank. Bila state lama memintanya, buka tab Terpopuler alih-alih
            // mengabaikan pilihan pengguna. Selain itu default tetap Chapter Terbaru.
            if (savedInstanceState != null
                    && "rank".equals(savedInstanceState.getString(STATE_SORT))) {
                tab = Tab.POPULAR;
            }
            mode = modeOf(tab);
            buildUi();
            searchField.setText(query);
            refreshContinueRail();
            // Adapter membaca field items/rows; arahkan ke daftar milik tab aktif
            // sebelum halaman pertama dimuat.
            items = state(tab).items;
            rows = state(tab).rows;
            loadPage(1, false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        int currentResolved = new Prefs(this).resolvedTheme();
        if (activeResolvedTheme != -1 && currentResolved != activeResolvedTheme) {
            applyThemeSelection();
            return;
        }
        // Progres/riwayat bisa berubah setelah membaca; rail ikut segar.
        if (store != null) refreshContinueRail();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);

        root.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        searchField = new EditText(this);
        searchField.setHint("Cari judul manga");
        searchField.setTextColor(palette.text);
        searchField.setHintTextColor(palette.muted);
        searchField.setSingleLine(true);
        searchField.setTag("shinigami_search");
        searchField.setBackground(Ui.rounded(palette.surface, palette.divider, 16, this));
        searchField.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        searchField.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchField.setOnEditorActionListener((v, actionId, event) -> {
            runSearch();
            return true;
        });
        // chrome = pencarian + tab + jumlah manga. Ketiganya menyusut menjadi
        // nol tinggi saat pengguna menggulir turun, lalu kembali saat naik.
        chrome = new LinearLayout(this);
        chrome.setOrientation(LinearLayout.VERTICAL);
        chrome.setTag("shinigami_chrome");

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4));
        searchRow.addView(searchField, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button search = Ui.chip(this, "Cari", palette);
        search.setTag("shinigami_search_button");
        search.setOnClickListener(v -> runSearch());
        searchRow.addView(search);
        chrome.addView(searchRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Pencarian diketik -> tunggu 350 ms supaya tidak membanjiri sumber.
        searchField.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable text) {
                main.removeCallbacks(searchRunnable);
                main.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
            }
        });

        // Rail tidak ditempel di atas daftar: ia menjadi item pertama di dalam
        // RecyclerView supaya ikut tergulir bersama katalog.
        continueRail = buildContinueRail();

        filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 6));
        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        filterScroll.addView(filterRow, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        chrome.addView(filterScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusLine = Ui.label(this, "", 13, palette.muted);
        statusLine.setTag("shinigami_status");
        statusLine.setPadding(Ui.dp(this, 14), Ui.dp(this, 2), Ui.dp(this, 14), Ui.dp(this, 6));
        chrome.addView(statusLine);

        root.addView(chrome, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout body = new FrameLayout(this);
        list = new RecyclerView(this);
        list.setTag("shinigami_grid");
        list.setBackgroundColor(palette.background);
        adapter = new MangaAdapter();
        rowAdapter = new RowAdapter();
        downloadsAdapter = new DownloadsAdapter();
        list.setLayoutManager(new GridLayoutManager(this, gridSpan()));
        useGridAdapter();
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView view, int dx, int dy) {
                updateChrome(dy);
                if (dy <= 0) return;
                if (!tab.isCatalogue()) return;
                RecyclerView.LayoutManager manager = view.getLayoutManager();
                if (manager == null) return;
                int last = manager instanceof LinearLayoutManager
                        ? ((LinearLayoutManager) manager).findLastVisibleItemPosition() : -1;
                int total = list.getAdapter() == null ? 0 : list.getAdapter().getItemCount();
                if (total > 0 && last >= total - 6) loadMore();
            }
        });
        body.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        spinner.setVisibility(View.GONE);
        FrameLayout.LayoutParams spinnerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        body.addView(spinner, spinnerParams);

        LinearLayout errorBox = new LinearLayout(this);
        errorBox.setOrientation(LinearLayout.VERTICAL);
        errorBox.setGravity(Gravity.CENTER);
        errorBox.setPadding(Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24));
        errorBox.setVisibility(View.GONE);

        errorText = Ui.label(this, "", 14, palette.text);
        errorText.setGravity(Gravity.CENTER);
        errorText.setTag("shinigami_error");
        errorBox.addView(errorText);

        retryButton = Ui.chip(this, "Coba lagi", palette);
        retryButton.setTag("shinigami_retry");
        retryButton.setOnClickListener(v -> reload());
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        retryParams.topMargin = Ui.dp(this, 12);
        errorBox.addView(retryButton, retryParams);
        body.addView(errorBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(Ui.dp(this, 32), Ui.dp(this, 32), Ui.dp(this, 32), Ui.dp(this, 32));
        empty.setVisibility(View.GONE);
        empty.setTag("shinigami_downloads_empty");
        TextView emptyTitle = Ui.label(this, "Belum ada unduhan", 16, palette.text);
        emptyTitle.setGravity(Gravity.CENTER);
        empty.addView(emptyTitle);
        TextView emptyHint = Ui.label(this,
                "Buka manga, lalu tekan Unduh pada chapter untuk membacanya tanpa internet.",
                13, palette.muted);
        emptyHint.setGravity(Gravity.CENTER);
        emptyHint.setPadding(0, Ui.dp(this, 8), 0, 0);
        empty.addView(emptyHint);
        body.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        downloadsEmpty = empty;

        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        applyInsets(root);

        buildFilterChips();
    }

    /**
     * Rail "Lanjutkan Membaca": RecyclerView horizontal ringkas (tinggi ±150 dp)
     * yang ikut tergulir keluar layar bersama katalog, jadi bukan sticky header.
     * Datanya hanya dari ShinigamiStore (riwayat + progres).
     */
    private View buildContinueRail() {
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setTag("shinigami_continue_rail");
        rail.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 2));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 8), 0);

        TextView label = Ui.label(this, getString(
                id.shuyasal.kuromaku.R.string.shinigami_continue_title), 14, palette.text);
        label.setTypeface(label.getTypeface(), Typeface.BOLD);
        label.setTag("shinigami_continue_label");
        head.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView all = Ui.label(this, getString(
                id.shuyasal.kuromaku.R.string.shinigami_continue_all), 12, palette.accent);
        all.setTag("shinigami_continue_all");
        all.setGravity(Gravity.CENTER_VERTICAL);
        all.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
        all.setOnClickListener(v -> {
            showHistory();
            list.scrollToPosition(0);
        });
        head.addView(all, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rail.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        continueList = new RecyclerView(this);
        continueList.setTag("shinigami_continue_cards");
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

    private void refreshContinueRail() {
        if (continueRail == null || continueAdapter == null) return;
        List<ShinigamiStore.HistoryEntry> recent = new ArrayList<>();
        for (ShinigamiStore.HistoryEntry entry : store.history()) {
            if (recent.size() >= CONTINUE_LIMIT) break;
            if (entry.chapterId.isEmpty()) continue;
            recent.add(entry);
        }
        final int before = continueAdapter.getItemCount();
        continueAdapter.submit(recent);
        // Tanpa riwayat item header hilang; katalog naik mendekati pencarian.
        continueRail.setVisibility(recent.isEmpty() ? View.GONE : View.VISIBLE);
        // Jumlah item header hanya berubah saat riwayat pertama kali muncul.
        // Memberitahu daftar setiap kali Activity kembali dari reader membuat
        // katalog dirender ulang tanpa alasan; sekarang hanya bila perlu.
        if (activeHeader != null && before != continueAdapter.getItemCount()) {
            activeHeader.notifyDataSetChanged();
        }
    }

    private void openHistoryEntry(ShinigamiStore.HistoryEntry entry) {
        Row row = new Row(entry.mangaId, entry.title, entry.coverUrl, entry.chapterId,
                entry.chapterNumber, ShinigamiDates.format(entry.timestamp));
        openChapter(row);
    }

    /**
     * Satu item header berisi rail riwayat. View-nya tidak didaur ulang supaya
     * posisi dan isi rail tidak hilang saat katalog digulir.
     */
    private final class HeaderAdapter extends RecyclerView.Adapter<HeaderAdapter.Holder> {
        final class Holder extends RecyclerView.ViewHolder {
            Holder(View item) {
                super(item);
                setIsRecyclable(false);
            }
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            android.view.ViewGroup.LayoutParams params = continueRail.getLayoutParams();
            if (params == null) {
                params = new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            } else {
                params = new RecyclerView.LayoutParams(params);
            }
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

    /** Kartu ringkas: cover 64 dp + judul 2 baris + progres kecil. */
    private final class ContinueAdapter extends RecyclerView.Adapter<ContinueAdapter.Holder> {
        private final List<ShinigamiStore.HistoryEntry> entries = new ArrayList<>();

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView cover;
            final TextView title;
            final TextView meta;
            final android.widget.ProgressBar progress;

            Holder(View item) {
                super(item);
                cover = item.findViewWithTag("shinigami_continue_cover");
                title = item.findViewWithTag("shinigami_continue_title");
                meta = item.findViewWithTag("shinigami_continue_meta");
                progress = item.findViewWithTag("shinigami_continue_progress");
            }
        }

        void submit(List<ShinigamiStore.HistoryEntry> recent) {
            entries.clear();
            entries.addAll(recent);
            notifyDataSetChanged();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(ShinigamiActivity.this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(Ui.dp(ShinigamiActivity.this, 6), Ui.dp(ShinigamiActivity.this, 6),
                    Ui.dp(ShinigamiActivity.this, 8), Ui.dp(ShinigamiActivity.this, 6));
            card.setBackground(Ui.ripple(ShinigamiActivity.this, palette.surface,
                    palette.divider, 14, palette.ripple));
            // Televisi: kartu adalah tujuan fokus, bukan teks di dalamnya.
            card.setFocusable(true);
            card.setFocusableInTouchMode(false);
            card.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            id.shuyasal.kuromaku.ui.TvFocus.focusable(card, palette);

            FrameLayout box = new FrameLayout(ShinigamiActivity.this);
            box.setBackground(Ui.rounded(palette.surface, palette.divider, 10,
                    ShinigamiActivity.this));
            box.setClipToOutline(true);
            ImageView image = new ImageView(ShinigamiActivity.this);
            image.setTag("shinigami_continue_cover");
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            box.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(
                    Ui.dp(ShinigamiActivity.this, 64), Ui.dp(ShinigamiActivity.this, 90));
            card.addView(box, coverParams);

            LinearLayout text = new LinearLayout(ShinigamiActivity.this);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setPadding(Ui.dp(ShinigamiActivity.this, 8), 0, 0, 0);

            TextView name = Ui.label(ShinigamiActivity.this, "", 12, palette.text);
            name.setTag("shinigami_continue_title");
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            text.addView(name);

            TextView info = Ui.label(ShinigamiActivity.this, "", 10, palette.muted);
            info.setTag("shinigami_continue_meta");
            info.setSingleLine(true);
            info.setEllipsize(TextUtils.TruncateAt.END);
            info.setPadding(0, Ui.dp(ShinigamiActivity.this, 2), 0, 0);
            text.addView(info);

            android.widget.ProgressBar bar = new android.widget.ProgressBar(ShinigamiActivity.this,
                    null, android.R.attr.progressBarStyleHorizontal);
            bar.setTag("shinigami_continue_progress");
            bar.setMax(100);
            bar.setIndeterminate(false);
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(palette.accent));
            bar.setProgressBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(palette.divider));
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ShinigamiActivity.this, 4));
            barParams.topMargin = Ui.dp(ShinigamiActivity.this, 6);
            text.addView(bar, barParams);

            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    Ui.dp(ShinigamiActivity.this, CONTINUE_TEXT_DP),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            card.addView(text, textParams);
            id.shuyasal.kuromaku.ui.TvFocus.focusable(card, palette);
            return new Holder(card);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            // Televisi: sorotan dipasang lagi setelah latar kartu diganti
            // saat item dipakai ulang, supaya tidak pernah hilang.
            id.shuyasal.kuromaku.ui.TvFocus.keepRing(holder.itemView, palette, 14);
            ShinigamiStore.HistoryEntry entry = entries.get(position);
            holder.title.setText(entry.title);
            holder.title.setTextColor(palette.text);
            holder.meta.setTextColor(palette.muted);
            int percent = Math.max(0, Math.min(100, store.progress(entry.chapterId)));
            holder.meta.setText(getString(id.shuyasal.kuromaku.R.string.shinigami_continue_progress,
                    ShinigamiManga.formatNumber(entry.chapterNumber), percent));
            holder.progress.setProgress(percent);
            Glide.with(ShinigamiActivity.this)
                    .load(entry.coverUrl)
                    .centerCrop()
                    .override(Ui.dp(ShinigamiActivity.this, 64), Ui.dp(ShinigamiActivity.this, 90))
                    .into(holder.cover);
            holder.itemView.setOnClickListener(v -> openHistoryEntry(entry));
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }
    }

    private LinearLayout buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        header.setBackgroundColor(palette.surface);

        android.widget.ImageButton menu = Ui.iconButton(this, id.shuyasal.kuromaku.R.drawable.ic_menu,
                "Buka menu Novel dan Manga", palette);
        menu.setTag("section_menu");
        menu.setOnClickListener(v -> showSectionDrawer());
        header.addView(menu, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));

        TextView heading = Ui.label(this, "Manga Server 2", 20, palette.text);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        headingParams.leftMargin = Ui.dp(this, 8);
        header.addView(heading, headingParams);

        android.widget.ImageButton downloads = Ui.iconButton(this,
                id.shuyasal.kuromaku.R.drawable.ic_download,
                getString(id.shuyasal.kuromaku.R.string.shinigami_downloads_open), palette);
        downloads.setTag("shinigami_downloads_button");
        downloads.setOnClickListener(v -> startActivity(new android.content.Intent(
                ShinigamiActivity.this, ShinigamiDownloadsActivity.class)));
        header.addView(downloads, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));

        // Menu seragam: titik tiga, sama seperti beranda Anime dan Novel.
        android.widget.ImageButton more = Ui.iconButton(this,
                id.shuyasal.kuromaku.R.drawable.ic_more, "Menu Manga Server 2", palette);
        more.setTag("shinigami_overflow");
        more.setContentDescription("Menu Manga Server 2");
        more.setOnClickListener(v -> showOverflowMenu());
        header.addView(more, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));
        return header;
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

    private void buildFilterChips() {
        filterGeneration++;
        filterRow.removeAllViews();
        // Label tab tidak pernah berubah: tidak ada lagi tombol yang berganti
        // teks dari "Terpopuler" menjadi "Manga terbaru" saat ditekan.
        // Urutan tetap: Chapter Terbaru selalu pertama dan menjadi default.
        addChip(Tab.NEW_CHAPTERS.label, tab == Tab.NEW_CHAPTERS, () -> selectTab(Tab.NEW_CHAPTERS));
        addChip(Tab.POPULAR.label, tab == Tab.POPULAR, () -> selectTab(Tab.POPULAR));
        addChip(Tab.NEW_TITLES.label, tab == Tab.NEW_TITLES, () -> selectTab(Tab.NEW_TITLES));
        addChip(Tab.HISTORY.label, tab == Tab.HISTORY, () -> selectTab(Tab.HISTORY));
        addChip(Tab.FAVORITES.label, tab == Tab.FAVORITES, () -> selectTab(Tab.FAVORITES));
        addChip(Tab.DOWNLOADS.label, tab == Tab.DOWNLOADS, () -> selectTab(Tab.DOWNLOADS));
        addChip("Berjalan", status.equals("ongoing"), () -> {
            status = status.equals("ongoing") ? "" : "ongoing";
            buildFilterChips();
            loadPage(1, false);
        });
        addChip("Tamat", status.equals("completed"), () -> {
            status = status.equals("completed") ? "" : "completed";
            buildFilterChips();
            loadPage(1, false);
        });
        addChip("Hiatus", status.equals("hiatus"), () -> {
            status = status.equals("hiatus") ? "" : "hiatus";
            buildFilterChips();
            loadPage(1, false);
        });
        loadGenres();
    }

    private TabState state(Tab which) {
        TabState found = tabStates.get(which);
        if (found == null) {
            found = new TabState();
            tabStates.put(which, found);
        }
        return found;
    }

    private static Mode modeOf(Tab which) {
        switch (which) {
            case HISTORY: return Mode.HISTORY;
            case FAVORITES: return Mode.FAVORITES;
            case DOWNLOADS: return Mode.DOWNLOADS;
            case NEW_CHAPTERS: return Mode.NEWEST_CHAPTERS;
            default: return Mode.CATALOGUE;
        }
    }

    /**
     * Pindah tab tanpa memuat ulang data yang masih valid.
     *
     * <p>Posisi scroll tab lama disimpan lebih dulu, lalu daftar tab tujuan
     * dipasang kembali apa adanya (cache di memori). Hanya tab yang belum pernah
     * dimuat yang meminta data ke sumber.
     */
    private void selectTab(Tab target) {
        if (tab == target) return;
        rememberScroll();
        tab = target;
        mode = modeOf(target);
        buildFilterChips();

        TabState next = state(target);
        items = next.items;
        rows = next.rows;
        hideError();

        if (target == Tab.DOWNLOADS) {
            showDownloads();
            return;
        }
        if (target == Tab.HISTORY) {
            cancelRemoteView();
            fillHistoryRows();
            useRowAdapter();
            rowAdapter.notifyDataSetChanged();
            next.statusText = rows.size() + " manga dalam riwayat";
            statusLine.setText(next.statusText);
            restoreScroll(next);
            return;
        }
        if (target == Tab.FAVORITES) {
            cancelRemoteView();
            fillFavoriteItems();
            useGridAdapter();
            adapter.notifyDataSetChanged();
            next.statusText = items.size() + " manga favorit";
            statusLine.setText(next.statusText);
            restoreScroll(next);
            return;
        }
        if (target == Tab.NEW_CHAPTERS) {
            useRowAdapter();
        } else {
            useGridAdapter();
        }
        if (next.loaded) {
            statusLine.setText(next.statusText);
            if (target == Tab.NEW_CHAPTERS) rowAdapter.notifyDataSetChanged();
            else adapter.notifyDataSetChanged();
            restoreScroll(next);
        } else {
            loadFeed(target, 1, false);
        }
    }

    private void rememberScroll() {
        if (list == null || !(list.getLayoutManager() instanceof LinearLayoutManager)) return;
        LinearLayoutManager manager = (LinearLayoutManager) list.getLayoutManager();
        int position = manager.findFirstVisibleItemPosition();
        if (position < 0) return;
        View first = manager.findViewByPosition(position);
        TabState current = state(tab);
        current.scrollPosition = position;
        current.scrollOffset = first == null ? 0 : first.getTop() - list.getPaddingTop();
    }

    private void restoreScroll(TabState wanted) {
        final int position = wanted.scrollPosition;
        final int offset = wanted.scrollOffset;
        if (position <= 0 && offset == 0) return;
        list.post(() -> {
            if (list.getLayoutManager() instanceof LinearLayoutManager) {
                ((LinearLayoutManager) list.getLayoutManager())
                        .scrollToPositionWithOffset(position, offset);
            }
        });
    }

    private void fillHistoryRows() {
        rows.clear();
        for (ShinigamiStore.HistoryEntry entry : store.history()) {
            rows.add(new Row(entry.mangaId, entry.title, entry.coverUrl, entry.chapterId,
                    entry.chapterNumber, ShinigamiDates.format(entry.timestamp)));
        }
    }

    private void fillFavoriteItems() {
        items.clear();
        for (String id : store.favorites()) {
            items.add(new ShinigamiManga(id, store.mangaTitle(id), "", store.mangaCover(id),
                    "", -1, "", 0d, 0L, 0d, "", new ArrayList<String>(),
                    new ArrayList<String>(), ""));
        }
    }

    /**
     * Auto-hide header beranda sebagai satu state machine.
     *
     * <p>Penyebab top bar berkedip sebelumnya: tinggi container dianimasikan,
     * setiap frame mengubah tinggi RecyclerView, dan RecyclerView memancarkan
     * {@code onScrolled} lagi karena offset-nya bergeser. Listener mengira
     * pengguna bergerak, lalu membalik arah animasi — berulang sampai animasi
     * saling membatalkan. Sekarang:
     * <ul>
     *   <li>selama transisi, {@code onScrolled} diabaikan ({@code chromeBusy});</li>
     *   <li>arah harus terakumulasi melewati ambang, dengan hysteresis 2×;</li>
     *   <li>visibilitas tidak pernah diubah ke GONE, hanya tinggi 0 → tidak ada
     *       lompatan layout yang memicu scroll tambahan;</li>
     *   <li>animasi lama dibatalkan sebelum transisi baru dijalankan;</li>
     *   <li>pagination/refresh tidak pernah menyentuh state header.</li>
     * </ul>
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
            // Belum pernah diukur (misalnya sesudah recreate): ukur sekali.
            chrome.measure(
                    View.MeasureSpec.makeMeasureSpec(getResources().getDisplayMetrics().widthPixels,
                            View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            chromeHeight = chrome.getMeasuredHeight();
        }
        if (chromeHeight <= 0) chromeHeight = Ui.dp(this, 96);

        final ViewGroup.LayoutParams params = chrome.getLayoutParams();
        int from = params.height > 0 ? params.height : (hide ? chromeHeight : 0);
        if (!hide) {
            // Kembali tampil: kunci tinggi dulu supaya animasi tidak melompat.
            chrome.setVisibility(View.VISIBLE);
            params.height = chromeHeight;
            chrome.setLayoutParams(params);
            from = 0;
        }
        chromeBusy = true;
        main.removeCallbacks(chromeRelease);
        // Animasi lama diselesaikan lebih dulu: dua animator yang berjalan
        // bersamaan pada properti layout yang sama adalah sumber kedipan.
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
                if (chrome == null) return;
                ViewGroup.LayoutParams done = chrome.getLayoutParams();
                // Tetap VISIBLE dengan tinggi 0: tidak ada GONE/VISIBLE berulang.
                done.height = hide ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT;
                chrome.setLayoutParams(done);
                // Beri satu putaran layout untuk meredam onScrolled programatis
                // sebelum gestur pengguna berikutnya dihitung.
                main.postDelayed(chromeRelease, 220L);
            }
        });
        chromeAnimator = animator;
        animator.start();
    }

    private android.animation.ValueAnimator chromeAnimator;

    private final Runnable chromeRelease = () -> chromeBusy = false;

    /** Setelah Activity dibuat ulang (tema), chrome selalu tampil penuh. */
    private void restoreChromeState() {
        if (chrome == null) return;
        if (chromeAnimator != null) {
            chromeAnimator.removeAllListeners();
            chromeAnimator.cancel();
            chromeAnimator = null;
        }
        chromeHidden = false;
        chromeBusy = false;
        scrollDown = 0;
        scrollUp = 0;
        chrome.animate().cancel();
        ViewGroup.LayoutParams params = chrome.getLayoutParams();
        if (params != null) {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            chrome.setLayoutParams(params);
        }
        chrome.setVisibility(View.VISIBLE);
    }

    private void addChip(String text, boolean active, Runnable action) {
        Button chip = Ui.chip(this, text, palette);
        chip.setTag("shinigami_chip_" + text);
        chip.setSelected(active);
        chip.setAlpha(active ? 1f : 0.7f);
        chip.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = Ui.dp(this, 8);
        filterRow.addView(chip, params);
    }

    private void loadGenres() {
        // Genre hanya relevan untuk katalog; tab Unduhan/Riwayat/Favorit harus
        // tetap bisa dibuka tanpa jaringan.
        if (!tab.isCatalogue()) return;
        final int generation = filterGeneration;
        worker.execute(() -> {
            List<ShinigamiSource.Genre> loaded;
            try {
                loaded = source.genres();
            } catch (ShinigamiSource.SourceException error) {
                loaded = new ArrayList<>();
            }
            final List<ShinigamiSource.Genre> genres = loaded;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (generation != filterGeneration) return;
                for (ShinigamiSource.Genre item : genres) {
                    addChip(item.name, item.slug.equals(genre), () -> {
                        genre = item.slug.equals(genre) ? "" : item.slug;
                        buildFilterChips();
                        loadPage(1, false);
                    });
                }
            });
        });
    }

    /** Dipanggil setelah jeda ketik, atau langsung dari tombol Cari. */
    private void runSearch() {
        main.removeCallbacks(searchRunnable);
        if (searchField == null) return;
        String wanted = searchField.getText().toString().trim();
        if (wanted.equals(query) && state(tab).loaded) return;
        query = wanted;
        if (!tab.isCatalogue()) {
            tab = Tab.POPULAR;
            mode = Mode.CATALOGUE;
            buildFilterChips();
            items = state(tab).items;
            rows = state(tab).rows;
        }
        TabState current = state(tab);
        current.loaded = false;
        loadFeed(tab, 1, false);
    }

    private void loadMore() {
        if (loading) return;
        TabState current = state(tab);
        if (current.page >= current.totalPages) return;
        if (!tab.isCatalogue()) return;
        loadFeed(tab, current.page + 1, true);
    }

    private void reload() {
        switch (mode) {
            case HISTORY:
                showHistory();
                break;
            case FAVORITES:
                showFavorites();
                break;
            case DOWNLOADS:
                showDownloads();
                break;
            default:
                loadFeed(tab, 1, false);
                break;
        }
    }

    private void showNewestChapters() {
        selectTab(Tab.NEW_CHAPTERS);
    }

    private void cancelRemoteView() {
        requestGeneration++;
        loading = false;
        spinner.setVisibility(View.GONE);
    }

    private void showHistory() {
        if (tab != Tab.HISTORY) {
            if (list != null) rememberScroll();
            tab = Tab.HISTORY;
        }
        cancelRemoteView();
        mode = Mode.HISTORY;
        buildFilterChips();
        rows = state(tab).rows;
        fillHistoryRows();
        useRowAdapter();
        rowAdapter.notifyDataSetChanged();
        statusLine.setText(rows.size() + " manga dalam riwayat");
        hideError();
    }

    /**
     * Tab Unduhan: hanya judul yang punya berkas halaman nyata di disk.
     * Pemindaian dilakukan di luar main thread karena memvalidasi setiap halaman.
     */
    private void showDownloads() {
        if (tab != Tab.DOWNLOADS) {
            if (list != null) rememberScroll();
            tab = Tab.DOWNLOADS;
        }
        cancelRemoteView();
        mode = Mode.DOWNLOADS;
        buildFilterChips();
        downloads.clear();
        downloadsAdapter.notifyDataSetChanged();
        switchContent(downloadsAdapter);
        applyGridManager();
        downloadsEmpty.setVisibility(View.GONE);
        statusLine.setText("Memindai unduhan...");
        final int generation = ++downloadsGeneration;
        worker.execute(() -> {
            List<ShinigamiDownloads.Manga> found = ShinigamiDownloads.scan(this);
            long bytes = ShinigamiDownloads.totalBytes(found);
            main.post(() -> {
                if (isFinishing() || isDestroyed() || generation != downloadsGeneration) return;
                downloads.clear();
                downloads.addAll(found);
                downloadsAdapter.notifyDataSetChanged();
                if (found.isEmpty()) {
                    statusLine.setText("Belum ada chapter tersimpan");
                    downloadsEmpty.setVisibility(View.VISIBLE);
                } else {
                    int chapters = 0;
                    for (ShinigamiDownloads.Manga manga : found) chapters += manga.chapters.size();
                    statusLine.setText(found.size() + " manga - " + chapters + " chapter - "
                            + ShinigamiDownloads.formatBytes(bytes));
                    downloadsEmpty.setVisibility(View.GONE);
                }
                hideError();
            });
        });
    }

    private void showFavorites() {
        if (tab != Tab.FAVORITES) {
            if (list != null) rememberScroll();
            tab = Tab.FAVORITES;
        }
        cancelRemoteView();
        mode = Mode.FAVORITES;
        buildFilterChips();
        items = state(tab).items;
        useGridAdapter();
        fillFavoriteItems();
        adapter.notifyDataSetChanged();
        statusLine.setText(items.size() + " manga favorit");
        hideError();
    }

    private void useRowAdapter() {
        if (!(list.getLayoutManager() instanceof LinearLayoutManager)
                || list.getLayoutManager() instanceof GridLayoutManager) {
            list.setLayoutManager(new LinearLayoutManager(this));
        }
        switchContent(rowAdapter);
    }

    private void useGridAdapter() {
        // switchContent lebih dulu: span lookup membaca activeHeader, dan rail
        // riwayat harus direntang penuh sejak layout pertama.
        switchContent(adapter);
        applyGridManager();
    }

    /** Grid dua kolom dengan rail riwayat sebagai satu baris penuh. */
    private void applyGridManager() {
        if (!(list.getLayoutManager() instanceof GridLayoutManager)) {
            list.setLayoutManager(new GridLayoutManager(this, gridSpan()));
        }
        GridLayoutManager manager = (GridLayoutManager) list.getLayoutManager();
        final int span = manager.getSpanCount();
        manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                // Item pertama adalah rail riwayat: satu baris penuh.
                return activeHeader != null && position < activeHeader.getItemCount()
                        ? span : 1;
            }
        });
    }

    /**
     * Pasang tampilan yang diminta tanpa membuat adapter baru bila sudah aktif.
     *
     * <p>Penyebab katalog melompat ke atas: setiap halaman memanggil
     * {@code list.setAdapter(new ConcatAdapter(...))}. RecyclerView membuang
     * posisi scroll setiap kali adapter diganti, jadi begitu pengguna sampai di
     * dasar daftar dan halaman berikutnya tiba, daftar kembali ke item pertama.
     * Sekarang penggantian hanya terjadi saat berganti jenis tampilan; pagination
     * cukup menambah item pada adapter yang sama.
     */
    private void switchContent(RecyclerView.Adapter<?> content) {
        if (activeContent == content) return;
        ConcatAdapter target;
        if (content == rowAdapter) {
            if (rowStack == null) rowStack = new ConcatAdapter(new HeaderAdapter(), rowAdapter);
            target = rowStack;
        } else if (content == downloadsAdapter) {
            if (downloadsStack == null) {
                downloadsStack = new ConcatAdapter(new HeaderAdapter(), downloadsAdapter);
            }
            target = downloadsStack;
        } else {
            if (gridStack == null) gridStack = new ConcatAdapter(new HeaderAdapter(), adapter);
            target = gridStack;
        }
        activeContent = content;
        activeHeader = (HeaderAdapter) target.getAdapters().get(0);
        if (list.getAdapter() != target) list.setAdapter(target);
    }

    private void loadPage(int targetPage, boolean append) {
        loadFeed(tab, targetPage, append);
    }

    /**
     * Muat satu halaman untuk tab tertentu.
     *
     * <p>Hasil selalu disimpan ke {@link TabState} milik tab asal, jadi respons
     * yang datang terlambat tidak pernah menimpa daftar tab lain. Adapter hanya
     * disentuh bila tab itu masih aktif, dan halaman berikutnya ditambahkan
     * dengan {@code notifyItemRangeInserted} — bukan dengan mengganti adapter
     * atau mengosongkan daftar, yang membuat posisi scroll melompat ke atas.
     */
    private void loadFeed(Tab target, int targetPage, boolean append) {
        if (loading && append) return;
        final Tab wantedTab = target;
        final int generation = ++requestGeneration;
        loading = true;
        spinner.setVisibility(View.VISIBLE);
        if (!append) statusLine.setText("Memuat...");

        final String wantedQuery = query;
        final String wantedGenre = genre;
        final String wantedStatus = status;

        worker.execute(() -> {
            ShinigamiSource.Page result = null;
            String failure = null;
            try {
                ShinigamiSource.Feed feed = wantedTab == Tab.NEW_TITLES
                        ? ShinigamiSource.Feed.NEW_TITLES
                        : wantedTab == Tab.NEW_CHAPTERS
                        ? ShinigamiSource.Feed.NEW_CHAPTERS
                        : ShinigamiSource.Feed.POPULAR;
                result = source.feed(feed, targetPage, wantedQuery, wantedGenre, wantedStatus);
            } catch (ShinigamiSource.SourceException error) {
                failure = error.getMessage();
            }
            final ShinigamiSource.Page pageResult = result;
            final String message = failure;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (generation != requestGeneration) return;
                loading = false;
                spinner.setVisibility(View.GONE);
                if (message != null) {
                    showError(message);
                    return;
                }
                if (pageResult == null) {
                    showError("Sumber tidak mengembalikan data.");
                    return;
                }
                hideError();
                TabState state = state(wantedTab);
                final boolean active = tab == wantedTab;
                int added;
                if (wantedTab == Tab.NEW_CHAPTERS) {
                    if (!append) state.rows.clear();
                    java.util.Set<String> known = new java.util.HashSet<>();
                    for (Row row : state.rows) known.add(row.mangaId);
                    final int rowStart = state.rows.size();
                    for (ShinigamiManga manga : pageResult.items) {
                        if (manga.latestChapterNumber <= 0 || !known.add(manga.id)) continue;
                        String date = manga.latestChapterTimeMillis > 0
                                ? "Rilis: " + ShinigamiDates.format(manga.latestChapterTimeMillis)
                                : manga.updatedAtMillis > 0
                                ? "Diperbarui: " + ShinigamiDates.format(manga.updatedAtMillis)
                                : "Tanggal belum tersedia";
                        state.rows.add(new Row(manga.id, manga.title, manga.coverUrl, "",
                                manga.latestChapterNumber, date));
                    }
                    added = state.rows.size() - rowStart;
                    if (active) {
                        useRowAdapter();
                        if (append && added > 0) rowAdapter.notifyItemRangeInserted(rowStart, added);
                        else rowAdapter.notifyDataSetChanged();
                    }
                } else {
                    if (!append) state.items.clear();
                    final int start = state.items.size();
                    java.util.LinkedHashMap<String, ShinigamiManga> merged =
                            new java.util.LinkedHashMap<>();
                    if (append) for (ShinigamiManga manga : state.items) merged.put(manga.id, manga);
                    for (ShinigamiManga manga : pageResult.items) merged.put(manga.id, manga);
                    state.items.clear();
                    state.items.addAll(merged.values());
                    added = state.items.size() - start;
                    if (active) {
                        useGridAdapter();
                        if (append && added > 0) adapter.notifyItemRangeInserted(start, added);
                        else adapter.notifyDataSetChanged();
                    }
                }
                state.page = targetPage;
                state.totalPages = Math.max(1, pageResult.totalPages);
                state.totalRecords = pageResult.totalRecords;
                state.loaded = true;
                long total = pageResult.totalRecords;
                state.statusText = (wantedTab == Tab.NEW_CHAPTERS
                        ? state.rows.size() + " chapter terbaru"
                        : state.items.size() + " manga")
                        + (total > 0 ? " dari " + total : "")
                        + (wantedQuery.isEmpty() ? "" : " untuk \"" + wantedQuery + "\"");
                if (!active) return;
                statusLine.setText(state.statusText);
                // Hanya penggantian daftar (cari/filter/muat ulang manual) yang
                // kembali ke atas. Pagination tidak pernah menggeser posisi.
                if (!append) list.scrollToPosition(0);
            });
        });
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.VISIBLE);
        statusLine.setText("Gagal memuat");
    }

    private void hideError() {
        errorText.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
    }

    private void showSectionDrawer() {
        ShinigamiNavigation.showDrawer(this, palette, true);
    }

    private void openDataAction(String action) {
        Intent intent = new Intent(this, id.shuyasal.kuromaku.MainActivity.class);
        intent.putExtra("kuromaku_data_action", action);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    /**
     * Menu titik tiga: jalan masuk ke pengaturan dan unduhan.
     *
     * <p>Pola ini sama dengan beranda Anime dan Novel. Pengaturan sendiri hanya
     * memuat dua hal yang benar-benar diatur pengguna; alamat server tidak lagi
     * tampil di sini karena itu urusan internal sumber, bukan pilihan pembaca.
     */
    private void showOverflowMenu() {
        String[] options = {"Pengaturan", "Unduhan", "Muat ulang"};
        new AlertDialog.Builder(this)
                .setTitle("Menu Manga Server 2")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showPreferences();
                    else if (which == 1) startActivity(new android.content.Intent(
                            ShinigamiActivity.this, ShinigamiDownloadsActivity.class));
                    else reload();
                })
                .setNegativeButton("Tutup", null)
                .show();
    }

    /** Pengaturan: tema dan indikator progres membaca. Tidak ada yang lain. */
    private void showPreferences() {
        String[] options = {"Tema tampilan", "Indikator progres membaca"};
        new AlertDialog.Builder(this)
                .setTitle("Pengaturan Manga Server 2")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showThemeDialog();
                    else showReadingProgressDialog();
                })
                .setNegativeButton("Tutup", null)
                .show();
    }

    /**
     * Pengaturan indikator progres membaca.
     *
     * <p>Progres <em>membaca</em> terpisah dari progres <em>unduhan</em>: yang
     * diatur di sini hanya tampilan bar di bawah reader, bukan antrean unduhan.
     */
    private void showReadingProgressDialog() {
        ShinigamiStore store = new ShinigamiStore(this);
        String[] opsi = {"Bar progres membaca", "Angka persen di samping bar"};
        boolean[] aktif = {store.showReadingProgress(), store.showReadingPercent()};
        new AlertDialog.Builder(this)
                .setTitle("Indikator progres membaca")
                .setMultiChoiceItems(opsi, aktif, (dialog, which, checked) -> {
                    if (which == 0) store.setShowReadingProgress(checked);
                    else store.setShowReadingPercent(checked);
                })
                .setPositiveButton("Simpan", null)
                .show();
    }

    /** Pilihan tema Light/Dark/System; tersimpan dan langsung diterapkan dengan state retained. */
    private void showThemeDialog() {
        // Urutan pilihan sengaja sama dengan nilai Prefs.THEME_* supaya indeks
        // dialog bisa dipakai langsung, dan tema Sepia tidak lagi tampil
        // sebagai "Ikuti Sistem" pada daftar pilihan.
        String[] options = {"Terang (Light)", "Gelap (Dark)", "Sepia", "Ikuti Sistem (System)"};
        int checked = new Prefs(this).theme();
        if (checked < Prefs.THEME_LIGHT || checked > Prefs.THEME_SYSTEM) checked = Prefs.THEME_SYSTEM;
        new AlertDialog.Builder(this)
                .setTitle("Tema Manga Server 2")
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    Prefs prefs = new Prefs(this);
                    prefs.setTheme(which);
                    dialog.dismiss();
                    Toast.makeText(this, "Tema disimpan: " + options[which], Toast.LENGTH_SHORT).show();
                    applyThemeSelection();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_QUERY, query);
        outState.putInt(STATE_PAGE, page);
        outState.putString(STATE_SORT, sort.wire);
        outState.putString(STATE_GENRE, genre);
        outState.putString(STATE_STATUS, status);
    }

    protected ShinigamiSource createSource() { return new ShinigamiSource(this); }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private final class MangaAdapter extends RecyclerView.Adapter<MangaAdapter.Holder> {

        MangaAdapter() {
            setHasStableIds(true);
        }

        /** ID stabil: RecyclerView tidak perlu membuang posisi saat item ditambah. */
        @Override public long getItemId(int position) {
            return position < 0 || position >= items.size()
                    ? RecyclerView.NO_ID : items.get(position).id.hashCode();
        }

        class Holder extends RecyclerView.ViewHolder {
            final RatioFrameLayout coverBox;
            final ImageView cover;
            final TextView title;
            final TextView subtitle;

            Holder(View item) {
                super(item);
                coverBox = item.findViewWithTag("shinigami_cover_box");
                cover = item.findViewWithTag("shinigami_cover");
                title = item.findViewWithTag("shinigami_title");
                subtitle = item.findViewWithTag("shinigami_subtitle");
            }
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(ShinigamiActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(Ui.dp(ShinigamiActivity.this, 4), Ui.dp(ShinigamiActivity.this, 4),
                    Ui.dp(ShinigamiActivity.this, 4), Ui.dp(ShinigamiActivity.this, 8));

            RatioFrameLayout box = new RatioFrameLayout(ShinigamiActivity.this);
            box.setRatio(0.68f);
            box.setTag("shinigami_cover_box");
            ImageView image = new ImageView(ShinigamiActivity.this);
            image.setTag("shinigami_cover");
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(palette.surface);
            box.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            card.addView(box, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView name = Ui.label(ShinigamiActivity.this, "", 12, palette.text);
            name.setTag("shinigami_title");
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(name);

            TextView info = Ui.label(ShinigamiActivity.this, "", 11, palette.muted);
            info.setTag("shinigami_subtitle");
            info.setMaxLines(4);
            info.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(info);

            id.shuyasal.kuromaku.ui.TvFocus.focusable(card, palette);
            return new Holder(card);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            // Televisi: sorotan dipasang lagi setelah latar kartu diganti
            // saat item dipakai ulang, supaya tidak pernah hilang.
            id.shuyasal.kuromaku.ui.TvFocus.keepRing(holder.itemView, palette, 14);
            ShinigamiManga manga = items.get(position);
            holder.title.setText(manga.title);
            holder.title.setTextColor(palette.text);
            holder.subtitle.setTextColor(palette.muted);
            StringBuilder info = new StringBuilder();
            if (manga.latestChapterNumber > 0) {
                info.append("Chapter terbaru: ").append(ShinigamiManga.formatNumber(manga.latestChapterNumber));
            }
            if (!manga.genres.isEmpty()) {
                if (info.length() > 0) info.append(" - ");
                info.append(manga.genres.get(0));
            }
            long updated = manga.latestChapterTimeMillis > 0
                    ? manga.latestChapterTimeMillis : manga.updatedAtMillis;
            // Baris sendiri + format ringkas: tanggal tidak lagi terpotong pada
            // kartu dua kolom.
            info.append("\n").append(updated > 0
                    ? getString(id.shuyasal.kuromaku.R.string.shinigami_update_line,
                            ShinigamiDates.formatShort(updated))
                    : "Tanggal belum tersedia");
            holder.subtitle.setText(info.toString());
            holder.coverBox.setOnClickListener(v -> openDetail(manga.id, manga.title, manga.coverUrl));
            loadCover(holder.cover, manga.coverUrl);
        }

        @Override public void onViewRecycled(Holder holder) {
            // Permintaan gambar milik item yang sudah keluar layar dibatalkan.
            Glide.with(ShinigamiActivity.this).clear(holder.cover);
            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private final class DownloadsAdapter extends RecyclerView.Adapter<DownloadsAdapter.Holder> {

        class Holder extends RecyclerView.ViewHolder {
            final ImageView cover;
            final TextView title;
            final TextView subtitle;

            Holder(View item) {
                super(item);
                cover = item.findViewWithTag("shinigami_download_cover");
                title = item.findViewWithTag("shinigami_download_title");
                subtitle = item.findViewWithTag("shinigami_download_subtitle");
            }
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(ShinigamiActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(Ui.dp(ShinigamiActivity.this, 6), Ui.dp(ShinigamiActivity.this, 6),
                    Ui.dp(ShinigamiActivity.this, 6), Ui.dp(ShinigamiActivity.this, 10));

            RatioFrameLayout box = new RatioFrameLayout(ShinigamiActivity.this);
            box.setRatio(0.68f);
            ImageView image = new ImageView(ShinigamiActivity.this);
            image.setTag("shinigami_download_cover");
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(palette.surface);
            box.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            card.addView(box, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView name = Ui.label(ShinigamiActivity.this, "", 14, palette.text);
            name.setTag("shinigami_download_title");
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setPadding(0, Ui.dp(ShinigamiActivity.this, 6), 0, 0);
            card.addView(name);

            TextView info = Ui.label(ShinigamiActivity.this, "", 12, palette.muted);
            info.setTag("shinigami_download_subtitle");
            info.setMaxLines(2);
            info.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(info);

            id.shuyasal.kuromaku.ui.TvFocus.focusable(card, palette);
            return new Holder(card);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            // Televisi: sorotan dipasang lagi setelah latar kartu diganti
            // saat item dipakai ulang, supaya tidak pernah hilang.
            id.shuyasal.kuromaku.ui.TvFocus.keepRing(holder.itemView, palette, 14);
            ShinigamiDownloads.Manga manga = downloads.get(position);
            holder.title.setText(manga.title.isEmpty() ? "Tanpa judul" : manga.title);
            holder.subtitle.setText(manga.chapters.size() + " chapter offline - "
                    + ShinigamiDownloads.formatBytes(manga.bytes()));
            loadCover(holder.cover, manga.coverUrl);
            holder.itemView.setOnClickListener(v -> openDetail(manga.mangaId, manga.title, manga.coverUrl));
        }

        @Override
        public int getItemCount() {
            return downloads.size();
        }
    }

    private final class RowAdapter extends RecyclerView.Adapter<RowAdapter.Holder> {

        RowAdapter() {
            setHasStableIds(true);
        }

        @Override public long getItemId(int position) {
            return position < 0 || position >= rows.size()
                    ? RecyclerView.NO_ID : rows.get(position).mangaId.hashCode();
        }

        class Holder extends RecyclerView.ViewHolder {
            final ImageView cover;
            final TextView title;
            final TextView subtitle;

            Holder(View item) {
                super(item);
                cover = item.findViewWithTag("shinigami_row_cover");
                title = item.findViewWithTag("shinigami_row_title");
                subtitle = item.findViewWithTag("shinigami_row_subtitle");
            }
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(ShinigamiActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(ShinigamiActivity.this, 10), Ui.dp(ShinigamiActivity.this, 8),
                    Ui.dp(ShinigamiActivity.this, 10), Ui.dp(ShinigamiActivity.this, 8));

            RatioFrameLayout box = new RatioFrameLayout(ShinigamiActivity.this);
            box.setRatio(0.68f);
            ImageView image = new ImageView(ShinigamiActivity.this);
            image.setTag("shinigami_row_cover");
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(palette.surface);
            box.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            row.addView(box, new LinearLayout.LayoutParams(Ui.dp(ShinigamiActivity.this, 48),
                    Ui.dp(ShinigamiActivity.this, 70)));

            LinearLayout text = new LinearLayout(ShinigamiActivity.this);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setPadding(Ui.dp(ShinigamiActivity.this, 12), 0, 0, 0);

            TextView name = Ui.label(ShinigamiActivity.this, "", 14, palette.text);
            name.setTag("shinigami_row_title");
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            text.addView(name);

            TextView info = Ui.label(ShinigamiActivity.this, "", 12, palette.muted);
            info.setTag("shinigami_row_subtitle");
            info.setMaxLines(3);
            info.setEllipsize(TextUtils.TruncateAt.END);
            text.addView(info);

            row.addView(text, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            id.shuyasal.kuromaku.ui.TvFocus.focusable(row, palette);
            return new Holder(row);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            // Televisi: sorotan dipasang lagi setelah latar kartu diganti
            // saat item dipakai ulang, supaya tidak pernah hilang.
            id.shuyasal.kuromaku.ui.TvFocus.keepRing(holder.itemView, palette, 14);
            Row row = rows.get(position);
            holder.title.setText(row.title);
            StringBuilder info = new StringBuilder();
            if (row.chapterNumber > 0) {
                info.append("Chapter ").append(ShinigamiManga.formatNumber(row.chapterNumber));
            }
            if (!row.timestamp.isEmpty()) {
                if (info.length() > 0) info.append(" - ");
                info.append(row.timestamp);
            }
            holder.subtitle.setText(info.toString());
            holder.itemView.setOnClickListener(v -> {
                if (mode == Mode.HISTORY && !row.chapterId.isEmpty()) {
                    openChapter(row);
                } else {
                    openDetail(row.mangaId, row.title, row.coverUrl);
                }
            });
            loadCover(holder.cover, row.coverUrl);
        }

        @Override public void onViewRecycled(Holder holder) {
            Glide.with(ShinigamiActivity.this).clear(holder.cover);
            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    /**
     * Jumlah kolom adaptif: kartu tidak lebih sempit dari 150 dp supaya judul
     * dua baris tetap terbaca, dan tidak lebih lebar dari 220 dp supaya tablet
     * tidak menampilkan dua kartu raksasa.
     */
    private int gridSpan() {
        // Di televisi kartu dibuat lebih besar: kendalinya tombol arah dan
        // sorotan harus terlihat dari jarak tonton, jadi kolom lebih sedikit.
        return id.shuyasal.kuromaku.ui.TvMode.gridColumns(this);
    }

    /** Lebar satu kartu dalam piksel, dipakai agar cover tidak didekode penuh. */
    private int coverWidthPx() {
        int usable = getResources().getDisplayMetrics().widthPixels
                - Ui.dp(this, 8) * Math.max(1, gridSpan());
        return Math.max(Ui.dp(this, 120), usable / Math.max(1, gridSpan()));
    }

    /**
     * Cover dimuat pada ukuran kartu, bukan resolusi penuh: Glide menyimpan
     * bitmap sebesar yang dipakai (memori turun drastis untuk daftar panjang),
     * permintaan sebelumnya dibatalkan saat view didaur ulang, dan hasilnya
     * memakai cache disk Glide yang sudah dibatasi.
     */
    private void loadCover(ImageView target, String url) {
        if (target == null) return;
        if (url == null || url.isEmpty()) {
            Glide.with(this).clear(target);
            target.setImageDrawable(null);
            return;
        }
        int width = coverWidthPx();
        int height = Math.round(width * 1.45f);
        Glide.with(this)
                .load(url)
                .centerCrop()
                .override(width, height)
                .placeholder(palette.surface)
                .error(palette.surface)
                .into(target);
    }

    private void openDetail(String mangaId, String title, String coverUrl) {
        Intent intent = new Intent(this, ShinigamiDetailActivity.class);
        intent.putExtra(ShinigamiDetailActivity.EXTRA_MANGA_ID, mangaId);
        intent.putExtra(ShinigamiDetailActivity.EXTRA_MANGA_TITLE, title);
        intent.putExtra(ShinigamiDetailActivity.EXTRA_MANGA_COVER, coverUrl);
        startActivity(intent);
    }

    private void openChapter(Row row) {
        Intent intent = new Intent(this, ShinigamiReaderActivity.class);
        intent.putExtra(ShinigamiReaderActivity.EXTRA_MANGA_ID, row.mangaId);
        intent.putExtra(ShinigamiReaderActivity.EXTRA_MANGA_TITLE, row.title);
        intent.putExtra(ShinigamiReaderActivity.EXTRA_MANGA_COVER, row.coverUrl);
        intent.putExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_ID, row.chapterId);
        intent.putExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_NUMBER, row.chapterNumber);
        startActivity(intent);
    }
}
