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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.RatioFrameLayout;
import id.shuyasal.kuromaku.ui.SectionDrawer;
import id.shuyasal.kuromaku.ui.Ui;

public class ShinigamiActivity extends AppCompatActivity {

    private static final String STATE_QUERY = "shinigami_query";
    private static final String STATE_PAGE = "shinigami_page";
    private static final String STATE_SORT = "shinigami_sort";
    private static final String STATE_GENRE = "shinigami_genre";
    private static final String STATE_STATUS = "shinigami_status";

    private enum Mode {
        CATALOGUE,
        NEWEST_CHAPTERS,
        HISTORY,
        FAVORITES,
        DOWNLOADS
    }

    private static final class Row {
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
    private final List<ShinigamiManga> items = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
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

    private int page = 1;
    private int totalPages = 1;
    private boolean loading;
    private int requestGeneration;
    private int filterGeneration;
    private String query = "";
    private ShinigamiSource.Sort sort = ShinigamiSource.Sort.LATEST;
    private String genre = "";
    private String status = "";
    private Mode mode = Mode.CATALOGUE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        palette = Palette.of(new Prefs(this).theme());
        source = createSource();
        store = new ShinigamiStore(this);

        if (savedInstanceState != null) {
            query = savedInstanceState.getString(STATE_QUERY, "");
            page = savedInstanceState.getInt(STATE_PAGE, 1);
            sort = "rank".equals(savedInstanceState.getString(STATE_SORT))
                    ? ShinigamiSource.Sort.RANK : ShinigamiSource.Sort.LATEST;
            genre = savedInstanceState.getString(STATE_GENRE, "");
            status = savedInstanceState.getString(STATE_STATUS, "");
        }

        buildUi();
        searchField.setText(query);
        loadPage(1, false);
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
        searchField.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchField.setOnEditorActionListener((v, actionId, event) -> {
            runSearch();
            return true;
        });
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
        root.addView(searchRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 6));
        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        filterScroll.addView(filterRow, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(filterScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusLine = Ui.label(this, "", 13, palette.muted);
        statusLine.setTag("shinigami_status");
        statusLine.setPadding(Ui.dp(this, 14), Ui.dp(this, 2), Ui.dp(this, 14), Ui.dp(this, 6));
        root.addView(statusLine);

        FrameLayout body = new FrameLayout(this);
        list = new RecyclerView(this);
        list.setTag("shinigami_grid");
        list.setBackgroundColor(palette.background);
        adapter = new MangaAdapter();
        rowAdapter = new RowAdapter();
        downloadsAdapter = new DownloadsAdapter();
        list.setAdapter(adapter);
        list.setLayoutManager(new GridLayoutManager(this, 3));
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView view, int dx, int dy) {
                if (dy <= 0) return;
                if (mode != Mode.CATALOGUE) return;
                GridLayoutManager manager = (GridLayoutManager) view.getLayoutManager();
                if (manager == null) return;
                int last = manager.findLastVisibleItemPosition();
                if (last >= adapter.getItemCount() - 6) loadMore();
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

        TextView heading = Ui.label(this, "Manga Shinigami", 20, palette.text);
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

        android.widget.ImageButton settings = Ui.iconButton(this,
                id.shuyasal.kuromaku.R.drawable.ic_settings, "Atur domain sumber", palette);
        settings.setTag("shinigami_settings");
        settings.setOnClickListener(v -> showDomainDialog());
        header.addView(settings, new LinearLayout.LayoutParams(
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
        addChip(sort == ShinigamiSource.Sort.RANK ? "Terpopuler" : "Manga terbaru", mode == Mode.CATALOGUE, () -> {
            sort = sort == ShinigamiSource.Sort.LATEST
                    ? ShinigamiSource.Sort.RANK : ShinigamiSource.Sort.LATEST;
            buildFilterChips();
            loadPage(1, false);
        });
        addChip("Chapter terbaru", mode == Mode.NEWEST_CHAPTERS, () -> showNewestChapters());
        addChip("Riwayat", mode == Mode.HISTORY, () -> showHistory());
        addChip("Favorit", mode == Mode.FAVORITES, () -> showFavorites());
        addChip("Unduhan", mode == Mode.DOWNLOADS, () -> showDownloads());
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
        if (mode != Mode.CATALOGUE) return;
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

    private void runSearch() {
        query = searchField.getText().toString().trim();
        loadPage(1, false);
    }

    private void loadMore() {
        if (loading) return;
        if (page >= totalPages) return;
        if (mode == Mode.CATALOGUE || mode == Mode.NEWEST_CHAPTERS)
            loadRemote(page + 1, true, mode == Mode.NEWEST_CHAPTERS);
    }

    private void reload() {
        switch (mode) {
            case HISTORY:
                showHistory();
                break;
            case FAVORITES:
                showFavorites();
                break;
            case NEWEST_CHAPTERS:
                showNewestChapters();
                break;
            case DOWNLOADS:
                showDownloads();
                break;
            default:
                loadPage(1, false);
                break;
        }
    }

    private void showNewestChapters() {
        loadRemote(1, false, true);
    }

    private void cancelRemoteView() {
        requestGeneration++;
        loading = false;
        spinner.setVisibility(View.GONE);
    }

    private void showHistory() {
        cancelRemoteView();
        mode = Mode.HISTORY;
        rows.clear();
        useRowAdapter();
        for (ShinigamiStore.HistoryEntry entry : store.history()) {
            rows.add(new Row(entry.mangaId, entry.title, entry.coverUrl, entry.chapterId,
                    entry.chapterNumber, ShinigamiDates.format(entry.timestamp)));
        }
        rowAdapter.notifyDataSetChanged();
        statusLine.setText(rows.size() + " manga dalam riwayat");
        hideError();
    }

    /**
     * Tab Unduhan: hanya judul yang punya berkas halaman nyata di disk.
     * Pemindaian dilakukan di luar main thread karena memvalidasi setiap halaman.
     */
    private void showDownloads() {
        cancelRemoteView();
        mode = Mode.DOWNLOADS;
        buildFilterChips();
        downloads.clear();
        downloadsAdapter.notifyDataSetChanged();
        list.setLayoutManager(new GridLayoutManager(this, 2));
        list.setAdapter(downloadsAdapter);
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
        cancelRemoteView();
        mode = Mode.FAVORITES;
        items.clear();
        useGridAdapter();
        for (String id : store.favorites()) {
            items.add(new ShinigamiManga(id, store.mangaTitle(id), "", store.mangaCover(id),
                    "", -1, "", 0d, 0L, 0, "", new ArrayList<String>(),
                    new ArrayList<String>(), ""));
        }
        adapter.notifyDataSetChanged();
        statusLine.setText(items.size() + " manga favorit");
        hideError();
    }

    private void useRowAdapter() {
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(rowAdapter);
    }

    private void useGridAdapter() {
        list.setLayoutManager(new GridLayoutManager(this, 3));
        list.setAdapter(adapter);
    }

    private void loadPage(int targetPage, boolean append) {
        loadRemote(targetPage, append, false);
    }

    private void loadRemote(int targetPage, boolean append, boolean newest) {
        if (loading && append) return;
        mode = newest ? Mode.NEWEST_CHAPTERS : Mode.CATALOGUE;
        final int generation = ++requestGeneration;
        loading = true;
        spinner.setVisibility(View.VISIBLE);
        statusLine.setText("Memuat...");

        final String wantedQuery = query;
        final ShinigamiSource.Sort wantedSort = sort;
        final String wantedGenre = genre;
        final String wantedStatus = status;

        worker.execute(() -> {
            ShinigamiSource.Page result = null;
            String failure = null;
            try {
                if (newest) {
                    result = source.search(wantedQuery, targetPage, ShinigamiSource.Sort.LATEST,
                            wantedGenre, wantedStatus);
                } else if (!wantedQuery.isEmpty()) {
                    result = source.search(wantedQuery, targetPage, wantedSort, wantedGenre, wantedStatus);
                } else {
                    result = source.catalog(targetPage, wantedSort, wantedGenre, wantedStatus);
                }
            } catch (ShinigamiSource.SourceException error) {
                failure = error.getMessage();
            }
            final ShinigamiSource.Page page_result = result;
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
                hideError();
                if (page_result == null) {
                    showError("Sumber tidak mengembalikan data.");
                    return;
                }
                if (newest) {
                    useRowAdapter();
                    if (!append) rows.clear();
                    java.util.Set<String> known = new java.util.HashSet<>();
                    for (Row row : rows) known.add(row.mangaId);
                    for (ShinigamiManga manga : page_result.items) {
                        if (manga.latestChapterNumber <= 0 || !known.add(manga.id)) continue;
                        String date = manga.latestChapterTimeMillis > 0
                                ? "Rilis: " + ShinigamiDates.format(manga.latestChapterTimeMillis)
                                : manga.updatedAtMillis > 0 ? "Diperbarui: " + ShinigamiDates.format(manga.updatedAtMillis)
                                : "Tanggal belum tersedia";
                        rows.add(new Row(manga.id, manga.title, manga.coverUrl, "",
                                manga.latestChapterNumber, date));
                    }
                    rowAdapter.notifyDataSetChanged();
                } else {
                    useGridAdapter();
                    java.util.LinkedHashMap<String, ShinigamiManga> merged = new java.util.LinkedHashMap<>();
                    if (append) for (ShinigamiManga manga : items) merged.put(manga.id, manga);
                    for (ShinigamiManga manga : page_result.items) merged.put(manga.id, manga);
                    items.clear();
                    items.addAll(merged.values());
                    adapter.notifyDataSetChanged();
                }
                page = targetPage;
                totalPages = Math.max(1, page_result.totalPages);
                long total = page_result.totalRecords;
                statusLine.setText((newest ? rows.size() + " chapter terbaru" : items.size() + " manga")
                        + (total > 0 ? " dari " + total : "")
                        + (wantedQuery.isEmpty() ? "" : " untuk \"" + wantedQuery + "\""));
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

    private void showDomainDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 4));

        EditText api = new EditText(this);
        api.setHint("Alamat API (https://...)");
        api.setText(source.config().apiBase());
        api.setTextColor(palette.text);
        api.setHintTextColor(palette.muted);
        api.setSingleLine(true);
        api.setTag("shinigami_api_field");
        box.addView(api);

        EditText site = new EditText(this);
        site.setHint("Alamat situs (https://...)");
        site.setText(source.config().siteBase());
        site.setTextColor(palette.text);
        site.setHintTextColor(palette.muted);
        site.setSingleLine(true);
        site.setTag("shinigami_site_field");
        box.addView(site);

        TextView note = Ui.label(this,
                "Alamat harus memakai HTTPS dan berada di domain sumber yang dikenal.",
                12, palette.muted);
        note.setTag("shinigami_domain_note");
        box.addView(note);

        TextView problem = Ui.label(this, "", 13, Color.rgb(190, 60, 60));
        problem.setTag("shinigami_domain_problem");
        problem.setVisibility(View.GONE);
        box.addView(problem);

        new android.app.AlertDialog.Builder(this)
                .setTitle("Domain sumber")
                .setView(box)
                .setPositiveButton("Simpan", (d, which) -> {
                    if (source.config().setBases(api.getText().toString(), site.getText().toString())) {
                        Toast.makeText(this, "Domain sumber disimpan.", Toast.LENGTH_SHORT).show();
                        loadPage(1, false);
                    } else {
                        problem.setText("Alamat ditolak: harus HTTPS dan host yang valid.");
                        problem.setVisibility(View.VISIBLE);
                        Toast.makeText(this,
                                "Alamat ditolak: harus HTTPS dan host yang valid.",
                                Toast.LENGTH_LONG).show();
                    }
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
            info.setMaxLines(3);
            info.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(info);

            return new Holder(card);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            ShinigamiManga manga = items.get(position);
            holder.title.setText(manga.title);
            StringBuilder info = new StringBuilder();
            if (manga.latestChapterNumber > 0) {
                info.append("Chapter terbaru: ").append(ShinigamiManga.formatNumber(manga.latestChapterNumber));
            }
            if (!manga.genres.isEmpty()) {
                if (info.length() > 0) info.append(" - ");
                info.append(manga.genres.get(0));
            }
            info.append("\nUpdate terakhir: ").append(manga.latestChapterTimeMillis > 0
                    ? ShinigamiDates.format(manga.latestChapterTimeMillis)
                    : manga.updatedAtMillis > 0 ? ShinigamiDates.format(manga.updatedAtMillis) + " (Diperbarui)"
                    : "Tanggal belum tersedia");
            holder.subtitle.setText(info.toString());
            holder.coverBox.setOnClickListener(v -> openDetail(manga.id, manga.title, manga.coverUrl));
            Glide.with(ShinigamiActivity.this)
                    .load(manga.coverUrl)
                    .centerCrop()
                    .into(holder.cover);
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

            return new Holder(card);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            ShinigamiDownloads.Manga manga = downloads.get(position);
            holder.title.setText(manga.title.isEmpty() ? "Tanpa judul" : manga.title);
            holder.subtitle.setText(manga.chapters.size() + " chapter offline - "
                    + ShinigamiDownloads.formatBytes(manga.bytes()));
            if (manga.coverUrl.isEmpty()) {
                holder.cover.setImageDrawable(null);
            } else {
                Glide.with(ShinigamiActivity.this).load(manga.coverUrl).centerCrop().into(holder.cover);
            }
            holder.itemView.setOnClickListener(v -> openDetail(manga.mangaId, manga.title, manga.coverUrl));
        }

        @Override
        public int getItemCount() {
            return downloads.size();
        }
    }

    private final class RowAdapter extends RecyclerView.Adapter<RowAdapter.Holder> {

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
            return new Holder(row);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
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
            Glide.with(ShinigamiActivity.this).load(row.coverUrl).centerCrop().into(holder.cover);
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
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
