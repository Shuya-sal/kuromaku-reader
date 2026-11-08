package id.shuyasal.kuromaku.anime;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import id.shuyasal.kuromaku.R;
import id.shuyasal.kuromaku.MainActivity;
import id.shuyasal.kuromaku.MangaActivity;
import id.shuyasal.kuromaku.SourceHubActivity;
import id.shuyasal.kuromaku.data.ReadingBackup;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.SectionDrawer;
import id.shuyasal.kuromaku.ui.RatioFrameLayout;
import id.shuyasal.kuromaku.ui.ThemeManager;
import id.shuyasal.kuromaku.ui.Ui;

/**
 * Beranda Anime Otakudesu.
 *
 * <p>Katalog native: pencarian, Ongoing, Complete, Jadwal Rilis, Riwayat, dan
 * Favorit. Default adalah Ongoing. Setiap tab menyimpan daftar, kursor
 * pagination, dan posisi scroll sendiri sehingga berpindah tab tidak memuat
 * ulang data yang masih valid.
 */
public class OtakudesuActivity extends AppCompatActivity {

    private static final long SEARCH_DEBOUNCE_MS = 350L;

    enum Tab {
        ONGOING("Ongoing"),
        COMPLETE("Complete"),
        SCHEDULE("Jadwal Rilis"),
        HISTORY("Riwayat"),
        FAVORITES("Favorit");

        final String label;
        Tab(String label) { this.label = label; }

        boolean isCatalogue() {
            return this == ONGOING || this == COMPLETE;
        }
    }

    static final class TabState {
        final List<OtakudesuAnime> items = new ArrayList<>();
        int page = 1;
        int totalPages = 1;
        boolean loaded;
        int scrollPosition;
        String statusText = "";
    }

    enum Mode { CATALOGUE, SEARCH, SCHEDULE, HISTORY, FAVORITES, GENRE }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final java.util.EnumMap<Tab, TabState> states = new java.util.EnumMap<>(Tab.class);
    /** Halaman genre punya state sendiri, terpisah dari tab status. */
    private final TabState genreState = new TabState();
    private final List<OtakudesuAnime> items = new ArrayList<>();
    private final List<OtakudesuStore.HistoryEntry> history = new ArrayList<>();

    private Palette palette;
    private OtakudesuSource source;
    private OtakudesuStore store;
    private RecyclerView list;
    private AnimeAdapter adapter;
    private LinearLayout filterRow;
    private TextView statusLine;
    private TextView errorText;
    private Button retryButton;
    private ProgressBar spinner;
    private EditText searchField;
    private LinearLayout rail;
    private RecyclerView railList;
    private HistoryAdapter railAdapter;

    private Tab tab = Tab.ONGOING;
    private Mode mode = Mode.CATALOGUE;
    private String query = "";
    private boolean loading;
    private int requestGeneration;
    private int activeResolvedTheme = -1;
    private View rootView;
    private LinearLayout topSection;
    private boolean topHidden;
    private int topSectionHeight;
    private String genreSlug = "";
    private String genreName = "";
    private final List<OtakudesuSource.Genre> genres = new ArrayList<>();
    private boolean genresLoading;
    private final Runnable searchRunnable = this::runSearch;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeManager.applyTheme(this);
        activeResolvedTheme = new Prefs(this).resolvedTheme();
        palette = Palette.of(activeResolvedTheme);
        source = createSource();
        store = new OtakudesuStore(this);

        buildUi();
        loadTab(tab, 1, false);
    }

    protected OtakudesuSource createSource() {
        return new OtakudesuSource();
    }

    // ------------------------------------------------------------------- UI

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);

        root.addView(buildToolbar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        searchField = new EditText(this);
        searchField.setHint("Cari anime");
        searchField.setTextColor(palette.text);
        searchField.setHintTextColor(palette.muted);
        searchField.setSingleLine(true);
        searchField.setTag("otakudesu_search");
        searchField.setBackground(Ui.rounded(palette.surface, palette.divider, 16, this));
        searchField.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        searchField.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchField.setOnEditorActionListener((v, action, event) -> {
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
        search.setTag("otakudesu_search_button");
        search.setOnClickListener(v -> runSearch());
        searchRow.addView(search);

        // Seluruh area atas (cari, lanjutkan menonton, filter) berada dalam satu
        // wadah supaya bisa menyusut bersama saat daftar digulir ke bawah.
        topSection = new LinearLayout(this);
        topSection.setOrientation(LinearLayout.VERTICAL);
        topSection.setTag("otakudesu_top_section");
        topSection.addView(searchRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        rail = buildRail();
        topSection.addView(rail, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 6));
        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        filterScroll.addView(filterRow, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        topSection.addView(filterScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(topSection, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusLine = Ui.label(this, "", 13, palette.muted);
        statusLine.setTag("otakudesu_status");
        statusLine.setPadding(Ui.dp(this, 14), Ui.dp(this, 2), Ui.dp(this, 14), Ui.dp(this, 6));
        root.addView(statusLine);

        FrameLayout body = new FrameLayout(this);
        list = new RecyclerView(this);
        list.setTag("otakudesu_grid");
        list.setBackgroundColor(palette.background);
        list.setItemAnimator(null);
        adapter = new AnimeAdapter();
        list.setLayoutManager(new GridLayoutManager(this, gridSpan()));
        list.setAdapter(adapter);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView view, int dx, int dy) {
                if (dy > 6) {
                    hideTopSection();
                    loadMoreIfNeeded();
                } else if (dy < -6) {
                    showTopSection();
                }
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

        LinearLayout errorBox = new LinearLayout(this);
        errorBox.setOrientation(LinearLayout.VERTICAL);
        errorBox.setGravity(Gravity.CENTER);
        errorBox.setPadding(Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24));
        errorBox.setVisibility(View.GONE);
        errorText = Ui.label(this, "", 14, palette.text);
        errorText.setGravity(Gravity.CENTER);
        errorText.setTag("otakudesu_error");
        errorBox.addView(errorText);
        retryButton = Ui.chip(this, "Coba lagi", palette);
        retryButton.setTag("otakudesu_retry");
        retryButton.setOnClickListener(v -> reload());
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        retryParams.topMargin = Ui.dp(this, 12);
        errorBox.addView(retryButton, retryParams);
        body.addView(errorBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        this.errorBox = errorBox;

        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        rootView = root;
        setContentView(root);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                            | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        buildTabs();
        refreshRail();
    }

    private LinearLayout errorBox;

    /** Menu titik tiga: tema dan genre. */
    private void showOverflow() {
        String[] items = {"Tema tampilan", "Genre", "Muat ulang"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Anime Otakudesu")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) showThemeDialog();
                    else if (which == 1) showGenreSheet();
                    else reload();
                })
                .show();
    }

    /**
     * Kembali dari beranda anime menuju Source Hub, bukan ke layar sebelumnya
     * yang kebetulan ada di tumpukan (mis. Novel). Toolbar sendiri tidak punya
     * panah kembali: beranda ini adalah pintu masuk cabang Anime.
     */
    @Override public void onBackPressed() {
        startActivity(new android.content.Intent(this, id.shuyasal.kuromaku.SourceHubActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    private LinearLayout buildToolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        bar.setBackgroundColor(palette.surface);

        android.widget.ImageButton menu = Ui.iconButton(this,
                R.drawable.ic_menu, "Menu", palette);
        menu.setTag("otakudesu_menu");
        menu.setOnClickListener(v -> showDrawer());
        LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP));
        menuParams.leftMargin = Ui.dp(this, 4);
        bar.addView(menu, menuParams);

        TextView title = Ui.label(this, "Anime Otakudesu", 20, palette.text);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.leftMargin = Ui.dp(this, 8);
        bar.addView(title, params);

        // Unduhan: di kanan atas, sasaran sentuh 48dp.
        android.widget.ImageButton downloads = Ui.iconButton(this, R.drawable.ic_download,
                "Unduhan", palette);
        downloads.setTag("otakudesu_downloads");
        downloads.setMinimumWidth(Ui.dp(this, Ui.TOUCH_DP));
        downloads.setMinimumHeight(Ui.dp(this, Ui.TOUCH_DP));
        downloads.setOnClickListener(v -> startActivity(
                new android.content.Intent(this, AnimeDownloadsActivity.class)));
        bar.addView(downloads, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));

        android.widget.ImageButton more = Ui.iconButton(this, R.drawable.ic_more,
                "Menu lain", palette);
        more.setTag("otakudesu_overflow");
        more.setOnClickListener(v -> showOverflow());
        bar.addView(more, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));
        return bar;
    }

    private LinearLayout buildRail() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setTag("otakudesu_continue_rail");
        box.setVisibility(View.GONE);
        box.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 2));

        TextView label = Ui.label(this, "Lanjutkan Menonton", 14, palette.text);
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        label.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 4));
        box.addView(label);

        railList = new RecyclerView(this);
        railList.setTag("otakudesu_continue_cards");
        railList.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        railList.setClipToPadding(false);
        railList.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        railAdapter = new HistoryAdapter();
        railList.setAdapter(railAdapter);
        box.addView(railList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private void refreshRail() {
        history.clear();
        history.addAll(store.history());
        if (railAdapter != null) railAdapter.notifyDataSetChanged();
        if (rail != null) rail.setVisibility(history.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void buildTabs() {
        filterRow.removeAllViews();
        for (Tab value : Tab.values()) {
            addTab(value.label, mode == modeFor(value), () -> selectTab(value));
        }
        // Genre berdiri sendiri: menu daftar genre sumber, dengan penanda nama
        // genre yang sedang aktif.
        addTabWithTag(genreSlug.isEmpty() ? "Genre" : "Genre: " + genreName,
                mode == Mode.GENRE, this::showGenreSheet, "Genre");
    }

    private void addTabWithTag(String text, boolean active, Runnable action, String tag) {
        Button chip = Ui.chip(this, text, palette);
        chip.setTag("otakudesu_chip_" + tag);
        chip.setSelected(active);
        chip.setAlpha(active ? 1f : 0.7f);
        chip.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = Ui.dp(this, 8);
        filterRow.addView(chip, params);
    }

    // ------------------------------------------------- top section menyusut

    /**
     * Menyusutkan area atas (cari, lanjutkan menonton, filter) saat daftar
     * digulir ke bawah, dan memunculkannya lagi saat digulir ke atas.
     *
     * <p>Tingginya dianimasikan, bukan disembunyikan sekaligus, supaya daftar
     * tidak melompat. Tinggi semula diingat agar pemulihannya tepat.
     */
    private void hideTopSection() {
        if (topHidden || topSection == null) return;
        int height = topSection.getHeight();
        if (height <= 0) height = topSection.getMeasuredHeight();
        topHidden = true;
        if (height <= 0) {
            // Belum pernah ditata (layar belum terukur): sembunyikan langsung
            // daripada gagal menyusut sama sekali.
            topSection.setVisibility(View.GONE);
            return;
        }
        topSectionHeight = height;
        animateTopSection(height, 0, true);
    }

    private void showTopSection() {
        if (!topHidden || topSection == null) return;
        topHidden = false;
        topSection.setVisibility(View.VISIBLE);
        int target = topSectionHeight > 0 ? topSectionHeight : Ui.dp(this, 150);
        animateTopSection(0, target, false);
    }

    private void animateTopSection(int from, int to, final boolean collapseAtEnd) {
        android.animation.ValueAnimator animator =
                android.animation.ValueAnimator.ofInt(from, to);
        animator.setDuration(180);
        animator.addUpdateListener(animation -> {
            if (topSection == null) return;
            ViewGroup.LayoutParams params = topSection.getLayoutParams();
            params.height = (int) animation.getAnimatedValue();
            topSection.setLayoutParams(params);
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (topSection == null) return;
                if (collapseAtEnd) {
                    if (topHidden) topSection.setVisibility(View.GONE);
                } else {
                    ViewGroup.LayoutParams params = topSection.getLayoutParams();
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    topSection.setLayoutParams(params);
                }
            }
        });
        animator.start();
    }

    private void addTab(String text, boolean active, Runnable action) {
        Button chip = Ui.chip(this, text, palette);
        chip.setTag("otakudesu_chip_" + text);
        chip.setSelected(active);
        chip.setAlpha(active ? 1f : 0.7f);
        chip.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = Ui.dp(this, 8);
        filterRow.addView(chip, params);
    }

    // ------------------------------------------------------------- genre

    /** Buka daftar genre; dimuat dari sumber sekali lalu dipakai ulang. */
    private void showGenreSheet() {
        if (!genres.isEmpty()) {
            showGenreDialog();
            return;
        }
        if (genresLoading) return;
        genresLoading = true;
        statusLine.setText("Memuat daftar genre…");
        runInBackground(() -> {
            List<OtakudesuSource.Genre> result = null;
            String failure = null;
            try {
                result = source.genres();
            } catch (OtakudesuSource.SourceException error) {
                failure = error.getMessage();
            } catch (RuntimeException error) {
                failure = "Daftar genre tidak bisa dibaca.";
            }
            final List<OtakudesuSource.Genre> payload = result;
            final String message = failure;
            main.post(() -> {
                genresLoading = false;
                if (isFinishing() || isDestroyed()) return;
                if (message != null || payload == null || payload.isEmpty()) {
                    statusLine.setText(items.size() + " anime");
                    Toast.makeText(this, message == null
                            ? "Daftar genre kosong." : message, Toast.LENGTH_SHORT).show();
                    return;
                }
                genres.clear();
                genres.addAll(payload);
                statusLine.setText(items.size() + " anime");
                showGenreDialog();
            });
        });
    }

    private void showGenreDialog() {
        final List<String> labels = new ArrayList<>();
        labels.add("Semua anime");
        for (OtakudesuSource.Genre genre : genres) labels.add(genre.name);
        int checked = 0;
        for (int i = 0; i < genres.size(); i++) {
            if (genres.get(i).matches(genreSlug)) checked = i + 1;
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Pilih genre")
                .setSingleChoiceItems(labels.toArray(new String[0]), checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) clearGenre();
                    else selectGenre(genres.get(which - 1));
                })
                .setNegativeButton("Tutup", null)
                .show();
    }

    /** Terapkan satu genre: daftar dimuat ulang dengan filter genre. */
    void selectGenre(OtakudesuSource.Genre genre) {
        if (genre == null || genre.slug.isEmpty()) return;
        rememberScroll();
        genreSlug = genre.slug;
        genreName = genre.name;
        mode = Mode.GENRE;
        genreState.loaded = false;
        genreState.page = 1;
        genreState.totalPages = 1;
        buildTabs();
        hideError();
        items.clear();
        adapter.notifyDataSetChanged();
        loadGenre(1, false);
    }

    /** Kembali ke seluruh anime tanpa filter genre. */
    void clearGenre() {
        if (genreSlug.isEmpty() && mode != Mode.GENRE) return;
        genreSlug = "";
        genreName = "";
        mode = modeFor(tab);
        buildTabs();
        hideError();
        state(tab).loaded = false;
        loadTab(tab, 1, false);
    }

    // -------------------------------------------------------------- tema

    /**
     * Ganti tema tanpa memuat ulang data.
     *
     * <p>Warna di aplikasi ini dipakai lewat {@link Palette}, bukan atribut XML,
     * jadi tema bisa berganti di tempat: seluruh view dibangun ulang dengan
     * palet baru sementara daftar, tab aktif, genre aktif, dan posisi gulir
     * dipertahankan. Tidak ada permintaan jaringan yang diulang.
     */
    private void applyThemeInPlace() {
        int keepScroll = firstVisiblePosition();
        int previousResolved = activeResolvedTheme;
        activeResolvedTheme = new Prefs(this).resolvedTheme();
        palette = Palette.of(activeResolvedTheme);
        if (activeResolvedTheme == previousResolved && rootView != null) return;

        buildUi();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
            list.scrollToPosition(Math.max(0, keepScroll));
        }
        if (rootView != null) {
            androidx.core.view.WindowCompat.getInsetsController(getWindow(), rootView)
                    .setAppearanceLightStatusBars(!palette.darkIcons);
        }
    }

    private int firstVisiblePosition() {
        if (list == null) return 0;
        RecyclerView.LayoutManager manager = list.getLayoutManager();
        if (!(manager instanceof LinearLayoutManager)) return 0;
        return Math.max(0, ((LinearLayoutManager) manager).findFirstVisibleItemPosition());
    }

    private void showThemeDialog() {
        final String[] options = {"Terang (Light)", "Gelap (Dark)", "Ikuti sistem (System)"};
        int checked = new Prefs(this).theme();
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Tema Anime Otakudesu")
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    dialog.dismiss();
                    Prefs prefs = new Prefs(this);
                    prefs.setTheme(which);
                    // Tema hanya diganti di tempat: data dan posisi gulir tetap.
                    activeResolvedTheme = -1;
                    applyThemeInPlace();
                    Toast.makeText(this, "Tema disimpan: " + options[which],
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        if (new Prefs(this).resolvedTheme() != activeResolvedTheme) applyThemeInPlace();
    }

    // -------------------------------------------------------------- interaksi

    private void runSearch() {
        main.removeCallbacks(searchRunnable);
        if (searchField == null) return;
        String wanted = searchField.getText().toString().trim();
        if (wanted.equals(query)) return;
        query = wanted;
        if (wanted.isEmpty()) {
            mode = modeFor(tab);
            state(tab).loaded = false;
            loadTab(tab, 1, false);
            return;
        }
        mode = Mode.SEARCH;
        loadSearch(1, false);
    }

    private void selectTab(Tab target) {
        if (tab == target && mode != Mode.GENRE) return;
        rememberScroll();
        // Sumber tidak menyediakan gabungan status + genre; memilih status
        // berarti melepas filter genre supaya hasilnya tidak menyesatkan.
        if (mode == Mode.GENRE) {
            genreSlug = "";
            genreName = "";
        }
        tab = target;
        mode = modeFor(target);
        buildTabs();
        hideError();

        TabState next = state(target);
        items.clear();
        if (target == Tab.HISTORY) {
            cancelPending();
            for (OtakudesuStore.HistoryEntry entry : store.history()) {
                items.add(new OtakudesuAnime(entry.animeId, entry.title, "", entry.posterUrl,
                        entry.episodeLabel, "", relativeTime(entry.timestamp),
                        new ArrayList<String>()));
            }
            adapter.notifyDataSetChanged();
            statusLine.setText(items.size() + " anime di riwayat");
            list.scrollToPosition(0);
            return;
        }
        if (target == Tab.FAVORITES) {
            cancelPending();
            for (OtakudesuStore.Favorite favorite : store.favoriteEntries()) {
                String title = favorite.title.isEmpty()
                        ? favorite.animeId.replace('-', ' ') : favorite.title;
                items.add(new OtakudesuAnime(favorite.animeId, title, "", favorite.posterUrl,
                        "", "", "", new ArrayList<String>()));
            }
            adapter.notifyDataSetChanged();
            statusLine.setText(items.size() + " anime favorit");
            list.scrollToPosition(0);
            return;
        }
        if (target == Tab.SCHEDULE) {
            loadSchedule();
            return;
        }
        if (next.loaded) {
            items.addAll(next.items);
            adapter.notifyDataSetChanged();
            statusLine.setText(next.statusText);
            final int position = next.scrollPosition;
            if (position > 0) list.post(() -> list.scrollToPosition(position));
            return;
        }
        loadTab(target, 1, false);
    }

    private Mode modeFor(Tab value) {
        switch (value) {
            case COMPLETE:
            case ONGOING: return query.isEmpty() ? Mode.CATALOGUE : Mode.SEARCH;
            case SCHEDULE: return Mode.SCHEDULE;
            case HISTORY: return Mode.HISTORY;
            default: return Mode.FAVORITES;
        }
    }

    private TabState state(Tab which) {
        TabState found = states.get(which);
        if (found == null) {
            found = new TabState();
            states.put(which, found);
        }
        return found;
    }

    private void rememberScroll() {
        if (list == null || !(list.getLayoutManager() instanceof LinearLayoutManager)) return;
        int position = ((LinearLayoutManager) list.getLayoutManager())
                .findFirstVisibleItemPosition();
        if (position >= 0) state(tab).scrollPosition = position;
    }

    private void loadMoreIfNeeded() {
        if (loading) return;
        if (mode == Mode.GENRE) {
            if (genreState.page >= genreState.totalPages) return;
            RecyclerView.LayoutManager genreManager = list.getLayoutManager();
            if (!(genreManager instanceof LinearLayoutManager)) return;
            int lastGenre = ((LinearLayoutManager) genreManager).findLastVisibleItemPosition();
            int totalGenre = adapter.getItemCount();
            if (totalGenre > 0 && lastGenre >= totalGenre - 6) {
                loadGenre(genreState.page + 1, true);
            }
            return;
        }
        if (!tab.isCatalogue() || mode == Mode.SCHEDULE) return;
        TabState current = state(tab);
        if (current.page >= current.totalPages) return;
        RecyclerView.LayoutManager manager = list.getLayoutManager();
        if (!(manager instanceof LinearLayoutManager)) return;
        int last = ((LinearLayoutManager) manager).findLastVisibleItemPosition();
        int total = adapter.getItemCount();
        if (total > 0 && last >= total - 6) {
            if (mode == Mode.SEARCH) loadSearch(current.page + 1, true);
            else loadTab(tab, current.page + 1, true);
        }
    }

    private void reload() {
        hideError();
        if (mode == Mode.GENRE && !genreSlug.isEmpty()) {
            loadGenre(1, false);
            return;
        }
        if (mode == Mode.SEARCH && !query.isEmpty()) {
            loadSearch(1, false);
            return;
        }
        if (tab == Tab.SCHEDULE) {
            loadSchedule();
            return;
        }
        state(tab).loaded = false;
        loadTab(tab, 1, false);
    }

    private void cancelPending() {
        requestGeneration++;
        loading = false;
        if (spinner != null) spinner.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------------ muat

    private void loadTab(Tab target, int page, boolean append) {
        if (loading && append) return;
        if (!target.isCatalogue()) return;
        final int generation = ++requestGeneration;
        loading = true;
        spinner.setVisibility(View.VISIBLE);
        if (!append) statusLine.setText("Memuat...");

        runInBackground(() -> {
            OtakudesuAnime.Page result = null;
            String failure = null;
            try {
                result = source.catalog(target == Tab.COMPLETE
                        ? OtakudesuSource.Feed.COMPLETE : OtakudesuSource.Feed.ONGOING, page);
            } catch (OtakudesuSource.SourceException error) {
                failure = error.getMessage();
            }
            final OtakudesuAnime.Page payload = result;
            final String message = failure;
            main.post(() -> applyPage(target, page, append, payload, message, generation, false));
        });
    }

    /** Muat satu halaman genre; state-nya terpisah dari tab status. */
    private void loadGenre(int page, boolean append) {
        if (loading && append) return;
        if (genreSlug.isEmpty()) return;
        final int generation = ++requestGeneration;
        final String slug = genreSlug;
        loading = true;
        spinner.setVisibility(View.VISIBLE);
        if (!append) statusLine.setText("Memuat genre " + genreName + "…");

        runInBackground(() -> {
            OtakudesuAnime.Page result = null;
            String failure = null;
            try {
                result = source.genre(slug, page);
            } catch (OtakudesuSource.SourceException error) {
                failure = error.getMessage();
            }
            final OtakudesuAnime.Page payload = result;
            final String message = failure;
            main.post(() -> applyPage(tab, page, append, payload, message, generation, false));
        });
    }

    private void loadSearch(int page, boolean append) {
        if (loading && append) return;
        final int generation = ++requestGeneration;
        final String wanted = query;
        loading = true;
        spinner.setVisibility(View.VISIBLE);
        if (!append) statusLine.setText("Mencari \"" + wanted + "\"...");

        runInBackground(() -> {
            OtakudesuAnime.Page result = null;
            String failure = null;
            try {
                result = source.search(wanted, page);
            } catch (OtakudesuSource.SourceException error) {
                failure = error.getMessage();
            }
            final OtakudesuAnime.Page payload = result;
            final String message = failure;
            main.post(() -> applyPage(tab, page, append, payload, message, generation, true));
        });
    }

    private void applyPage(Tab target, int page, boolean append, OtakudesuAnime.Page payload,
                           String message, int generation, boolean search) {
        if (isFinishing() || isDestroyed() || generation != requestGeneration) return;
        loading = false;
        spinner.setVisibility(View.GONE);
        if (message != null) {
            showError(message);
            return;
        }
        if (payload == null) {
            showError("Sumber tidak mengembalikan data.");
            return;
        }
        hideError();
        TabState current = mode == Mode.GENRE ? genreState : state(target);
        if (!append) {
            current.items.clear();
        }
        java.util.Set<String> known = new java.util.HashSet<>();
        for (OtakudesuAnime item : current.items) known.add(item.animeId);
        final int start = current.items.size();
        for (OtakudesuAnime item : payload.items) {
            if (known.add(item.animeId)) current.items.add(item);
        }
        final int added = current.items.size() - start;
        current.page = page;
        current.totalPages = payload.totalPages;
        current.loaded = true;
        String prefix = search ? "Hasil untuk \"" + query + "\": "
                : (mode == Mode.GENRE ? "Genre " + genreName + ": " : "");
        current.statusText = prefix + current.items.size() + " anime";

        if (target != tab) return;
        if (!append) items.clear();
        items.addAll(current.items);
        if (append && added > 0) adapter.notifyItemRangeInserted(start, added);
        else adapter.notifyDataSetChanged();
        statusLine.setText(current.statusText);
        if (!append) {
            list.scrollToPosition(0);
            // Daftar baru: area atas ditampilkan lagi supaya pengguna melihat
            // filter yang sedang berlaku.
            if (topHidden) showTopSection();
        }
    }

    private void loadSchedule() {
        final int generation = ++requestGeneration;
        loading = true;
        spinner.setVisibility(View.VISIBLE);
        statusLine.setText("Memuat jadwal...");
        runInBackground(() -> {
            List<OtakudesuAnime> result = null;
            String failure = null;
            try {
                result = source.schedule();
            } catch (OtakudesuSource.SourceException error) {
                failure = error.getMessage();
            }
            final List<OtakudesuAnime> payload = result;
            final String message = failure;
            main.post(() -> {
                if (isFinishing() || isDestroyed() || generation != requestGeneration) return;
                loading = false;
                spinner.setVisibility(View.GONE);
                if (message != null) {
                    showError(message);
                    return;
                }
                hideError();
                items.clear();
                if (payload != null) items.addAll(payload);
                adapter.notifyDataSetChanged();
                statusLine.setText(items.size() + " anime dalam jadwal rilis");
                list.scrollToPosition(0);
            });
        });
    }

    private void showError(String message) {
        errorText.setText(message);
        errorBox.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.VISIBLE);
        statusLine.setText("Gagal memuat");
    }

    private void hideError() {
        errorBox.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
    }

    // --------------------------------------------------------------- adapter

    private int gridSpan() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        if (widthDp <= 0) {
            widthDp = getResources().getDisplayMetrics().widthPixels
                    / Math.max(1, getResources().getDisplayMetrics().densityDpi / 160);
        }
        return Math.max(2, Math.min(4, widthDp / 150));
    }

    private int coverPx() {
        int usable = getResources().getDisplayMetrics().widthPixels
                - Ui.dp(this, 8) * Math.max(1, gridSpan());
        return Math.max(Ui.dp(this, 120), usable / Math.max(1, gridSpan()));
    }

    private void loadCover(ImageView target, String url) {
        if (url == null || url.isEmpty()) {
            Glide.with(this).clear(target);
            target.setImageDrawable(null);
            return;
        }
        int width = coverPx();
        Glide.with(this).load(url).centerCrop()
                .override(width, Math.round(width * 1.42f))
                .placeholder(palette.surface).error(palette.surface)
                .into(target);
    }

    private void openDetail(OtakudesuAnime anime) {
        Intent intent = new Intent(this, OtakudesuDetailActivity.class);
        intent.putExtra(OtakudesuDetailActivity.EXTRA_ANIME_ID, anime.animeId);
        intent.putExtra(OtakudesuDetailActivity.EXTRA_ANIME_TITLE, anime.title);
        intent.putExtra(OtakudesuDetailActivity.EXTRA_ANIME_POSTER, anime.posterUrl);
        startActivity(intent);
    }

    /** Drawer empat sumber; halaman ini adalah Anime Otakudesu. */
    private void showDrawer() {
        SectionDrawer.show(this, SectionDrawer.Section.ANIME_OTAKUDESU, palette,
                new SectionDrawer.Listener() {
                    @Override public void onHome() {
                        startActivity(new Intent(OtakudesuActivity.this, SourceHubActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                        finish();
                    }

                    @Override public void onNovel() {
                        startActivity(new Intent(OtakudesuActivity.this, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                        finish();
                    }

                    @Override public void onMangaVoratoon() {
                        startActivity(new Intent(OtakudesuActivity.this, MangaActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                        finish();
                    }

                    @Override public void onShinigami() {
                        startActivity(new Intent(OtakudesuActivity.this,
                                id.shuyasal.kuromaku.shinigami.ShinigamiActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                        finish();
                    }

                    @Override public void onAnimeOtakudesu() {
                        // Sudah di halaman ini.
                    }

                    @Override public void onBackup() {
                        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("application/json");
                        intent.putExtra(Intent.EXTRA_TITLE, "kuromaku-reading-backup.json");
                        startActivityForResult(intent, REQUEST_BACKUP);
                    }

                    @Override public void onRestore() {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("application/json");
                        startActivityForResult(intent, REQUEST_RESTORE);
                    }
                });
    }

    private static final int REQUEST_BACKUP = 7121;
    private static final int REQUEST_RESTORE = 7122;

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            if (requestCode == REQUEST_BACKUP) {
                SharedPreferences novel = getSharedPreferences(Prefs.FILE_NAME, MODE_PRIVATE);
                SharedPreferences manga = getSharedPreferences(
                        id.shuyasal.kuromaku.shinigami.ShinigamiStore.FILE_NAME, MODE_PRIVATE);
                String document = ReadingBackup.export(novel, manga, store.preferences());
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out == null) throw new java.io.IOException("Berkas tujuan tidak bisa dibuka");
                    out.write(document.getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this, "Backup tersimpan", Toast.LENGTH_LONG).show();
            } else if (requestCode == REQUEST_RESTORE) {
                byte[] bytes;
                try (InputStream in = getContentResolver().openInputStream(data.getData());
                     java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                    if (in == null) throw new java.io.IOException("Berkas backup tidak bisa dibuka");
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        out.write(buffer, 0, count);
                        if (out.size() > ReadingBackup.MAX_BYTES) {
                            throw new java.io.IOException("Berkas backup terlalu besar");
                        }
                    }
                    bytes = out.toByteArray();
                }
                SharedPreferences novel = getSharedPreferences(Prefs.FILE_NAME, MODE_PRIVATE);
                SharedPreferences manga = getSharedPreferences(
                        id.shuyasal.kuromaku.shinigami.ShinigamiStore.FILE_NAME, MODE_PRIVATE);
                int restored = ReadingBackup.restore(novel,
                        new String(bytes, StandardCharsets.UTF_8), manga, store.preferences());
                Toast.makeText(this, restored + " progres dipulihkan", Toast.LENGTH_LONG).show();
                refreshRail();
                if (tab == Tab.HISTORY || tab == Tab.FAVORITES) selectTab(tab);
            }
        } catch (Exception error) {
            String reason = error.getMessage();
            Toast.makeText(this, "Gagal: " + (reason == null
                    ? error.getClass().getSimpleName() : reason), Toast.LENGTH_LONG).show();
        }
    }

    private void openFromHistory(OtakudesuStore.HistoryEntry entry) {
        if (entry == null) return;
        if (entry.episodeUrl.isEmpty()) {
            Intent intent = new Intent(this, OtakudesuDetailActivity.class);
            intent.putExtra(OtakudesuDetailActivity.EXTRA_ANIME_ID, entry.animeId);
            intent.putExtra(OtakudesuDetailActivity.EXTRA_ANIME_TITLE, entry.title);
            intent.putExtra(OtakudesuDetailActivity.EXTRA_ANIME_POSTER, entry.posterUrl);
            startActivity(intent);
            return;
        }
        startActivity(new Intent(this, AnimePlayerActivity.class)
                .putExtra(AnimePlayerActivity.EXTRA_ANIME_ID, entry.animeId)
                .putExtra(AnimePlayerActivity.EXTRA_ANIME_TITLE, entry.title)
                .putExtra(AnimePlayerActivity.EXTRA_ANIME_POSTER, entry.posterUrl)
                .putExtra(AnimePlayerActivity.EXTRA_EPISODE_URL, entry.episodeUrl)
                .putExtra(AnimePlayerActivity.EXTRA_EPISODE_LABEL, entry.episodeLabel)
                .putExtra(AnimePlayerActivity.EXTRA_EPISODE_NUMBER, entry.number)
                .putExtra(AnimePlayerActivity.EXTRA_EPISODE_TOTAL, entry.total));
    }

    static boolean isDayName(String value) {
        if (value == null) return false;
        String day = value.trim().toLowerCase(java.util.Locale.ROOT);
        return day.equals("senin") || day.equals("selasa") || day.equals("rabu")
                || day.equals("kamis") || day.equals("jumat") || day.equals("sabtu")
                || day.equals("minggu") || day.equals("random");
    }

    /** "2 jam lalu" dari stempel waktu; kosong bila tidak diketahui. */
    static String relativeTime(long timestamp) {
        if (timestamp <= 0) return "";
        long delta = System.currentTimeMillis() - timestamp;
        if (delta < 0) return "baru saja";
        long minutes = delta / 60_000L;
        if (minutes < 1) return "baru saja";
        if (minutes < 60) return minutes + " menit lalu";
        long hours = minutes / 60;
        if (hours < 24) return hours + " jam lalu";
        long days = hours / 24;
        if (days < 30) return days + " hari lalu";
        long months = days / 30;
        if (months < 12) return months + " bulan lalu";
        return (months / 12) + " tahun lalu";
    }

    private final class AnimeAdapter extends RecyclerView.Adapter<AnimeAdapter.Holder> {

        AnimeAdapter() { setHasStableIds(true); }

        @Override public long getItemId(int position) {
            return position < 0 || position >= items.size()
                    ? RecyclerView.NO_ID : items.get(position).animeId.hashCode();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final RatioFrameLayout box;
            final ImageView cover;
            final TextView title;
            final TextView meta;

            Holder(View item) {
                super(item);
                box = item.findViewWithTag("otakudesu_cover_box");
                cover = item.findViewWithTag("otakudesu_cover");
                title = item.findViewWithTag("otakudesu_title");
                meta = item.findViewWithTag("otakudesu_meta");
            }
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(OtakudesuActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(Ui.dp(OtakudesuActivity.this, 4), Ui.dp(OtakudesuActivity.this, 4),
                    Ui.dp(OtakudesuActivity.this, 4), Ui.dp(OtakudesuActivity.this, 8));

            RatioFrameLayout box = new RatioFrameLayout(OtakudesuActivity.this);
            box.setRatio(0.70f);
            box.setTag("otakudesu_cover_box");
            ImageView image = new ImageView(OtakudesuActivity.this);
            image.setTag("otakudesu_cover");
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(palette.surface);
            box.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            card.addView(box, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView name = Ui.label(OtakudesuActivity.this, "", 12, palette.text);
            name.setTag("otakudesu_title");
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(name);

            TextView meta = Ui.label(OtakudesuActivity.this, "", 11, palette.muted);
            meta.setTag("otakudesu_meta");
            meta.setMaxLines(3);
            meta.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(meta);

            return new Holder(card);
        }

        @Override public void onBindViewHolder(Holder holder, int position) {
            OtakudesuAnime anime = items.get(position);
            holder.title.setText(anime.title);
            StringBuilder meta = new StringBuilder();
            if (!anime.latestEpisode.isEmpty()) meta.append(anime.latestEpisode);
            if (!anime.day.isEmpty()) {
                if (meta.length() > 0) meta.append(" · ");
                // Di halaman Complete, kolom yang sama berisi skor ("8.20"),
                // bukan hari tayang. Jangan campur keduanya.
                meta.append(isDayName(anime.day) ? anime.day : "★ " + anime.day);
            }
            if (!anime.updated.isEmpty()) {
                if (meta.length() > 0) meta.append("\n");
                meta.append("Update ").append(anime.updated);
            }
            holder.meta.setText(meta.toString());
            loadCover(holder.cover, anime.posterUrl);
            holder.itemView.setOnClickListener(v -> openDetail(anime));
        }

        @Override public void onViewRecycled(Holder holder) {
            Glide.with(OtakudesuActivity.this).clear(holder.cover);
            super.onViewRecycled(holder);
        }

        @Override public int getItemCount() { return items.size(); }
    }

    private final class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder> {
        final class Holder extends RecyclerView.ViewHolder {
            final ImageView cover;
            final TextView title;
            final TextView meta;

            Holder(View item) {
                super(item);
                cover = item.findViewWithTag("otakudesu_continue_cover");
                title = item.findViewWithTag("otakudesu_continue_title");
                meta = item.findViewWithTag("otakudesu_continue_meta");
            }
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(OtakudesuActivity.this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(Ui.dp(OtakudesuActivity.this, 6), Ui.dp(OtakudesuActivity.this, 6),
                    Ui.dp(OtakudesuActivity.this, 8), Ui.dp(OtakudesuActivity.this, 6));
            card.setBackground(Ui.ripple(OtakudesuActivity.this, palette.surface,
                    palette.divider, 14, palette.ripple));

            FrameLayout box = new FrameLayout(OtakudesuActivity.this);
            box.setBackground(Ui.rounded(palette.surface, palette.divider, 10,
                    OtakudesuActivity.this));
            box.setClipToOutline(true);
            ImageView image = new ImageView(OtakudesuActivity.this);
            image.setTag("otakudesu_continue_cover");
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            box.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            card.addView(box, new LinearLayout.LayoutParams(
                    Ui.dp(OtakudesuActivity.this, 64), Ui.dp(OtakudesuActivity.this, 90)));

            LinearLayout text = new LinearLayout(OtakudesuActivity.this);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setPadding(Ui.dp(OtakudesuActivity.this, 8), 0, 0, 0);

            TextView name = Ui.label(OtakudesuActivity.this, "", 12, palette.text);
            name.setTag("otakudesu_continue_title");
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            text.addView(name);

            TextView info = Ui.label(OtakudesuActivity.this, "", 10, palette.muted);
            info.setTag("otakudesu_continue_meta");
            info.setSingleLine(true);
            info.setEllipsize(TextUtils.TruncateAt.END);
            text.addView(info);

            Button resume = Ui.chip(OtakudesuActivity.this, "Lanjutkan", palette);
            resume.setTag("otakudesu_continue_button");
            LinearLayout.LayoutParams resumeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            resumeParams.topMargin = Ui.dp(OtakudesuActivity.this, 4);
            text.addView(resume, resumeParams);
            resume.setOnClickListener(v -> openFromHistory((OtakudesuStore.HistoryEntry)
                    v.getTag(R.id.otakudesu_resume_tag)));

            card.addView(text, new LinearLayout.LayoutParams(
                    Ui.dp(OtakudesuActivity.this, 132), ViewGroup.LayoutParams.WRAP_CONTENT));
            return new Holder(card);
        }

        @Override public void onBindViewHolder(Holder holder, int position) {
            OtakudesuStore.HistoryEntry entry = history.get(position);
            holder.title.setText(entry.title.isEmpty() ? entry.animeId : entry.title);
            StringBuilder meta = new StringBuilder();
            if (!entry.episodeLabel.isEmpty()) meta.append(entry.episodeLabel);
            // Utamakan posisi pemutaran native; nomor episode hanya cadangan.
            OtakudesuStore.Progress progress = store.progress(entry.animeId,
                    OtakudesuSource.episodeId(entry.episodeUrl));
            int percent = progress != null ? progress.percent() : entry.progressPercent();
            if (progress != null && progress.finished) {
                if (meta.length() > 0) meta.append(" · ");
                meta.append("Selesai");
            } else if (percent >= 0) {
                if (meta.length() > 0) meta.append(" · ");
                meta.append(percent).append('%');
            }
            if (entry.timestamp > 0) {
                if (meta.length() > 0) meta.append(" · ");
                meta.append(relativeTime(entry.timestamp));
            }
            holder.meta.setText(meta.length() == 0 ? "Episoden terakhir dibuka" : meta.toString());
            loadCover(holder.cover, entry.posterUrl);
            Button resume = holder.itemView.findViewWithTag("otakudesu_continue_button");
            if (resume != null) resume.setTag(R.id.otakudesu_resume_tag, entry);
            holder.itemView.setOnClickListener(v -> {
                if (!entry.episodeUrl.isEmpty()) {
                    openFromHistory(entry);
                } else {
                    Intent intent = new Intent(OtakudesuActivity.this,
                            OtakudesuDetailActivity.class);
                    intent.putExtra(OtakudesuDetailActivity.EXTRA_ANIME_ID, entry.animeId);
                    intent.putExtra(OtakudesuDetailActivity.EXTRA_ANIME_TITLE, entry.title);
                    startActivity(intent);
                }
            });
        }

        @Override public int getItemCount() { return history.size(); }
    }

    @Override protected void onResume() {
        super.onResume();
        int currentResolved = new Prefs(this).resolvedTheme();
        if (activeResolvedTheme != -1 && currentResolved != activeResolvedTheme) {
            // Ganti tema tidak boleh memuat ulang katalog: data yang sudah ada
            // dipakai kembali, hanya tampilan yang dibangun ulang.
            applyThemeInPlace(currentResolved);
            return;
        }
        refreshRail();
    }

    /**
     * Membangun ulang tampilan dengan palet baru tanpa menyentuh data.
     *
     * <p>Daftar item, kursor pagination, dan state tab tetap; tidak ada
     * permintaan jaringan baru, sehingga katalog tidak berkedip atau kembali ke
     * halaman satu hanya karena tema berubah.
     */
    private void applyThemeInPlace(int resolved) {
        activeResolvedTheme = resolved;
        palette = Palette.of(resolved);
        int scroll = state(tab).scrollPosition;
        boolean hadItems = !items.isEmpty();
        String status = state(tab).statusText;
        String keepQuery = query;

        buildUi();
        if (!keepQuery.isEmpty() && searchField != null) searchField.setText(keepQuery);
        if (adapter != null) adapter.notifyDataSetChanged();
        if (!status.isEmpty()) statusLine.setText(status);
        if (hadItems && scroll > 0) list.post(() -> list.scrollToPosition(scroll));
    }

    @Override protected void onDestroy() {
        main.removeCallbacks(searchRunnable);
        worker.shutdownNow();
        super.onDestroy();
    }

    /**
     * Jalankan tugas latar hanya bila layar masih hidup.
     *
     * <p>Callback yang sudah mengantre bisa tiba setelah {@code onDestroy};
     * menyerahkan tugas baru ke executor yang sudah dimatikan akan melempar
     * {@code RejectedExecutionException}. Di sini kasus itu diabaikan dengan
     * sengaja: tidak ada lagi yang perlu dimuat.
     */
    private void runInBackground(Runnable task) {
        if (isFinishing() || isDestroyed()) return;
        try {
            worker.execute(task);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // Executor sudah ditutup: layar memang sudah tidak ada.
        }
    }
}
