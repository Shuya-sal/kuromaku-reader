package id.shuyasal.kuromaku;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Novel;
import id.shuyasal.kuromaku.data.Novels;
import id.shuyasal.kuromaku.net.CatalogLoader;
import id.shuyasal.kuromaku.offline.DownloadQueue;
import id.shuyasal.kuromaku.offline.DownloadStatusStore;
import id.shuyasal.kuromaku.offline.OfflineStore;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.ui.DownloadsPage;
import id.shuyasal.kuromaku.ui.LibraryPage;
import id.shuyasal.kuromaku.ui.Motion;
import id.shuyasal.kuromaku.ui.NovelListPage;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.SystemBarInsets;
import id.shuyasal.kuromaku.ui.Ui;

/**
 * Library screen.
 *
 * <p>Two levels in one activity: the home list of novels (cover + title cards),
 * and the detail page of the novel that was tapped (cover, verified metadata,
 * synopsis, continue reading, and the volume list where every volume carries its
 * own cover). A back button returns to the list.
 *
 * <p>Header rules: the toolbar is one fixed 58 dp row placed <em>below</em> the
 * status bar inset, so its title can never collide with the clock, and a long
 * title can only ellipsize — it can never wrap and make the bar taller. The bar
 * names the current page ("Perpustakaan" / "Detail Novel"); the application name
 * appears once inside the page content instead of being repeated.
 *
 * <p>Nothing here loads the source website as a page: catalogues come from each
 * novel's own source shape and chapters are extracted by {@code ChapterParser},
 * so there is no Blogspot chrome and no advertising.
 */
public class MainActivity extends AppCompatActivity {

    /** Toolbar height in dp, excluding the system inset. Comfortably 56–64 dp. */
    private static final int TOOLBAR_DP = 58;

    private Prefs prefs;
    private Palette palette;
    private LinearLayout root;
    private LinearLayout toolbar;
    private LinearLayout toolbarRow;
    private FrameLayout pageHost;
    private LinearLayout content;
    private LinearLayout spinnerRow;
    private TextView status;
    private TextView titleText;
    private TextView subtitleView;
    private Button backButton;
    private ImageButton refreshButton;
    private ImageButton downloadsButton;
    private ImageButton themeButton;
    private ScrollView scroller;

    /** Novel whose detail page is open; null shows the library list. */
    private Novel selectedNovel;
    /** True while the "Unduhan" page is open instead of the library list. */
    private boolean showingDownloads;
    private boolean loading;
    private boolean synopsisOpen;
    /**
     * Single source of truth for window insets.
     *
     * <p>BUG (2.6): this used to be two int fields updated inside the inset
     * listener <em>only when they changed</em>. A theme change rebuilds the whole
     * view tree ({@link #rebuildShell()}), so the freshly created toolbar started
     * at padding 0 while the fields still held the previous values — the listener
     * then decided "nothing changed" and never applied the status bar padding.
     * The title and the Kembali/refresh/theme controls ended up under the clock,
     * and only after a theme switch. Keeping the real inset value in one place and
     * applying it unconditionally fixes that: {@code setPadding} is idempotent, so
     * re-applying can never double the inset.
     */
    private final SystemBarInsets insets = new SystemBarInsets();
    private int pendingScrollY;

    /** Loaded catalogues, one per novel. Never mixed. */
    private final List<String> failed = new ArrayList<>();

    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        palette = Palette.of(prefs.theme());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        buildShell();
        // Paint whatever is already in memory first, then fetch in the
        // background: a returning user sees their library immediately instead
        // of an empty screen with a spinner.
        render();
        loadLibraries(false);
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);

        root.addView(buildToolbar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        spinnerRow = new LinearLayout(this);
        spinnerRow.setGravity(Gravity.CENTER);
        spinnerRow.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 6));
        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        spinnerRow.addView(spinner);
        root.addView(spinnerRow);

        status = Ui.label(this, "Memuat katalog dari Kaori Translation…", 12, palette.muted);
        status.setTag("load_status");
        status.setGravity(Gravity.CENTER);
        status.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 10));
        root.addView(status);

        // Only the page area moves during navigation: the toolbar and the status
        // bar inset stay perfectly still, so nothing ever slides under the clock.
        pageHost = new FrameLayout(this);
        pageHost.setTag("page_host");
        pageHost.setBackgroundColor(palette.background);

        scroller = new ScrollView(this);
        scroller.setTag("library_scroller");
        scroller.setClipToPadding(false);
        scroller.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setTag("page_content");
        content.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 32));
        scroller.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        pageHost.addView(scroller, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(pageHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        applySystemBars();
        // Apply what we already know BEFORE the next dispatch arrives: a rebuilt
        // shell must never flash (or stay) with the toolbar under the status bar.
        applyInsets();
        applyInsetsListener();
    }

    /**
     * Compact toolbar: optional back button, one-line page title, refresh and
     * theme icons. Every control is at least 48 dp and spaced evenly, and the row
     * height is fixed so wrapped text can never inflate the bar.
     */
    private View buildToolbar() {
        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setTag("toolbar");
        toolbar.setBackgroundColor(palette.surface);

        toolbarRow = new LinearLayout(this);
        toolbarRow.setOrientation(LinearLayout.HORIZONTAL);
        toolbarRow.setGravity(Gravity.CENTER_VERTICAL);
        toolbarRow.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);

        backButton = Ui.backButton(this, palette);
        backButton.setTag("back_to_library");
        backButton.setOnClickListener(v -> showLibrary());
        backButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, Ui.TOUCH_DP));
        backParams.setMargins(0, 0, Ui.dp(this, 6), 0);
        toolbarRow.addView(backButton, backParams);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_VERTICAL);
        titles.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);

        titleText = Ui.label(this, "Perpustakaan", 17, palette.text);
        titleText.setTag("app_title");
        titleText.setTypeface(null, Typeface.BOLD);
        titleText.setSingleLine(true);
        titleText.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(titleText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        subtitleView = Ui.label(this, subtitleText(), 11, palette.muted);
        subtitleView.setTag("app_subtitle");
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(subtitleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        toolbarRow.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        // Offline library entry point: always reachable from the toolbar, and it
        // works without a connection because the page reads local storage only.
        downloadsButton = Ui.iconButton(this, R.drawable.ic_download,
                "Buka unduhan offline", palette);
        downloadsButton.setTag("open_downloads");
        downloadsButton.setOnClickListener(v -> showDownloads());
        toolbarRow.addView(downloadsButton, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));

        refreshButton = Ui.iconButton(this, R.drawable.ic_refresh, "Muat ulang katalog", palette);
        refreshButton.setTag("refresh");
        refreshButton.setOnClickListener(v -> loadLibraries(true));
        toolbarRow.addView(refreshButton, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));

        themeButton = Ui.iconButton(this, palette.themeIcon(), themeDescription(), palette);
        themeButton.setTag("theme");
        themeButton.setOnClickListener(v -> {
            prefs.setTheme((prefs.theme() + 1) % 3);
            palette = Palette.of(prefs.theme());
            rebuildShell();
        });
        LinearLayout.LayoutParams themeParams = new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP));
        themeParams.setMargins(Ui.dp(this, 2), 0, 0, 0);
        toolbarRow.addView(themeButton, themeParams);

        toolbar.addView(toolbarRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, TOOLBAR_DP)));

        View divider = new View(this);
        divider.setTag("toolbar_divider");
        divider.setBackgroundColor(palette.divider);
        toolbar.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 1))));
        return toolbar;
    }

    private String themeDescription() {
        return palette.darkIcons ? "Ganti ke tema terang" : "Ganti ke tema gelap";
    }

    private String subtitleText() {
        // The novel's own name only; the application name is not repeated here.
        if (selectedNovel != null) return selectedNovel.title;
        return Novels.all().size() + " novel · Kaori Translation";
    }

    private void applyInsetsListener() {
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            // Accept the real value once, then always re-apply. Two failures made
            // the header slide under the clock after a theme switch: a "changed?"
            // guard that skipped the rebuilt tree, and an empty dispatch that
            // erased the known height. SystemBarInsets rejects the second and the
            // unconditional re-apply fixes the first.
            insets.accept(windowInsets);
            applyInsets();
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    /**
     * Applies the window insets from the single {@link SystemBarInsets} source.
     *
     * <p>Each inset is consumed by exactly one layout level, never by several:
     * the <b>top</b> inset is padding on the toolbar container (so its surface
     * colour still paints behind the status bar while the 58 dp row starts below
     * the clock), and the <b>bottom</b> inset is padding inside the scrolling
     * content (so the last element clears the gesture/navigation bar). Nothing
     * else reads insets, and no hardcoded "24dp status bar" exists anywhere.
     */
    private void applyInsets() {
        int top = insets.get().top;
        int bottom = insets.get().bottom;
        if (toolbar != null) {
            toolbar.setPadding(0, top, 0, 0);
        }
        if (content != null) {
            content.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14),
                    Ui.dp(this, 32) + bottom);
        }
    }

    /** Rebuilds the shell for a theme change without losing the reading spot. */
    private void rebuildShell() {
        keepScrollHere();
        buildShell();
        if (loading) return;
        render();
    }

    private void applySystemBars() {
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(!palette.darkIcons);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(!palette.darkIcons);
    }

    /**
     * Loads every novel's catalogue in the background. One failing novel must not
     * hide the other, so failures are collected and reported alongside the list.
     */
    private void loadLibraries(boolean force) {
        if (loading) return;
        if (!force && allCatalogsCached()) {
            // Nothing to fetch: catalogs from this session are already on screen.
            spinnerRow.setVisibility(View.GONE);
            status.setVisibility(View.GONE);
            render();
            return;
        }
        loading = true;
        failed.clear();
        spinnerRow.setVisibility(View.VISIBLE);
        status.setVisibility(View.VISIBLE);
        status.setText(selectedNovel == null
                ? "Memuat katalog dari Kaori Translation…"
                : "Memuat ulang " + selectedNovel.title + "…");

        new Thread(() -> {
            for (Novel novel : Novels.all()) {
                try {
                    CatalogLoader.Loaded loaded = CatalogLoader.load(novel);
                    Session.setCatalog(loaded.catalog);
                    final String warning = loaded.warning;
                    if (warning != null) {
                        main.post(() -> Toast.makeText(this, warning, Toast.LENGTH_LONG).show());
                    }
                } catch (Exception error) {
                    String reason = error.getMessage();
                    failed.add(novel.title + ": "
                            + (reason == null || reason.isEmpty()
                            ? error.getClass().getSimpleName() : reason));
                }
            }
            main.post(() -> {
                loading = false;
                render();
            });
        }).start();
    }

    private boolean allCatalogsCached() {
        for (Novel novel : Novels.all()) {
            if (Session.catalog(novel.id) == null) return false;
        }
        return true;
    }

    /** Draws either the novel list or the open novel's detail page. */
    private void render() {
        spinnerRow.setVisibility(loading ? View.VISIBLE : View.GONE);
        status.setVisibility(loading ? View.VISIBLE : View.GONE);

        int keepScroll = pendingScrollY > 0 ? pendingScrollY : scroller.getScrollY();
        content.removeAllViews();

        boolean downloads = showingDownloads;
        backButton.setVisibility(selectedNovel == null && !downloads
                ? View.GONE : View.VISIBLE);
        titleText.setText(downloads ? "Unduhan"
                : selectedNovel == null ? "Perpustakaan" : "Detail Novel");
        subtitleView.setText(downloads ? "Tersimpan di perangkat ini" : subtitleText());

        if (!failed.isEmpty()) {
            TextView warning = Ui.label(this, "⚠ " + failed.get(0), 12, palette.accent);
            warning.setTag("load_warning");
            warning.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 10));
            content.addView(warning);
        }

        if (showingDownloads) {
            content.addView(buildDownloadsPage(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else if (selectedNovel == null) {
            content.addView(new NovelListPage(this, prefs, palette, Novels.all(),
                    this::showNovel).build(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            Catalog catalog = Session.catalog(selectedNovel.id);
            // Scoped prefs: the continue button and chapter progress on this page
            // belong to this novel only.
            content.addView(new LibraryPage(this, prefs.scoped(selectedNovel.id), palette,
                    selectedNovel, catalog,
                    synopsisOpen, detailListener(selectedNovel))
                    .setDownload(downloadListener(selectedNovel), downloadState(selectedNovel))
                    .build(),
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        if (keepScroll > 0) {
            pendingScrollY = 0;
            scroller.post(() -> scroller.scrollTo(0, keepScroll));
        } else {
            scroller.post(() -> scroller.scrollTo(0, 0));
        }
    }

    private LibraryPage.Listener detailListener(final Novel novel) {
        final Prefs scoped = prefs.scoped(novel.id);
        return new LibraryPage.Listener() {
            @Override
            public void onOpenChapter(Chapter chapter) {
                openChapter(novel, chapter);
            }

            @Override
            public void onToggleSynopsis() {
                keepScrollHere();
                synopsisOpen = !synopsisOpen;
                render();
            }

            @Override
            public void onToggleVolume(String volumeKey) {
                toggleVolume(scoped, volumeKey);
            }

            @Override
            public void onOpenSource() {
                openExternal(novel.sourceLabelUrl);
            }

            @Override
            public void onOpenCover(String url, String caption) {
                openImage(url, caption);
            }
        };
    }

    /**
     * Volume accordion with real motion: a closing volume collapses to zero
     * height before the page is rebuilt, an opening one expands from zero up to
     * its natural height. With motion disabled the state just changes.
     */
    private void toggleVolume(Prefs scoped, String volumeKey) {
        boolean open = scoped.isExpanded(volumeKey);
        keepScrollHere();

        if (open) {
            View body = content.findViewWithTag("chapters:" + volumeKey);
            if (body != null && Motion.enabled(this)) {
                Motion.collapse(body, () -> {
                    scoped.toggleExpanded(volumeKey);
                    render();
                });
                return;
            }
            scoped.toggleExpanded(volumeKey);
            render();
            return;
        }

        scoped.toggleExpanded(volumeKey);
        render();
        View opened = content.findViewWithTag("chapters:" + volumeKey);
        if (opened != null && Motion.enabled(this)) {
            opened.post(() -> Motion.expand(opened));
        }
    }

    /** Opens one novel's detail page: the new page fades in from the right. */
    private void showNovel(Novel novel) {
        selectedNovel = novel;
        synopsisOpen = false;
        pendingScrollY = 0;
        render();
        scroller.post(() -> scroller.scrollTo(0, 0));
        if (Session.catalog(novel.id) == null) loadLibraries(true);
        Motion.enterFromRight(pageHost);
    }

    /** Opens the offline library. Reads local storage only, so it works offline. */
    private void showDownloads() {
        if (showingDownloads) return;
        keepScrollHere();
        showingDownloads = true;
        selectedNovel = null;
        synopsisOpen = false;
        pendingScrollY = 0;
        render();
        scroller.post(() -> scroller.scrollTo(0, 0));
        Motion.enterFromRight(pageHost);
    }

    /** Returns to the library list: the detail page slides right and fades out. */
    private void showLibrary() {
        if (selectedNovel == null && !showingDownloads) return;
        Motion.exitToRight(pageHost, () -> {
            selectedNovel = null;
            showingDownloads = false;
            synopsisOpen = false;
            pendingScrollY = 0;
            render();
            scroller.post(() -> scroller.scrollTo(0, 0));
            Motion.enterFromLeft(pageHost);
        });
    }

    // --------------------------------------------------------------- offline UI

    /**
     * Download actions offered on the detail page.
     *
     * <p>Only user intent lives here; the queue, retries and cancellation belong
     * to {@link DownloadQueue}, so this activity can be destroyed mid-download.
     */
    private LibraryPage.DownloadListener downloadListener(final Novel novel) {
        return new LibraryPage.DownloadListener() {
            @Override
            public void onDownloadChapter(Chapter chapter) {
                DownloadQueue.enqueueChapter(MainActivity.this, novel, chapter);
                Toast.makeText(MainActivity.this,
                        "Mengunduh " + chapter.label + "…", Toast.LENGTH_SHORT).show();
                repaintDownloadState();
            }

            @Override
            public void onDeleteChapter(Chapter chapter) {
                confirmDelete("Hapus unduhan " + chapter.label + "?",
                        "Teks dan ilustrasi chapter ini dihapus dari perangkat. "
                                + "Progres membaca dan bookmark tetap tersimpan.",
                        () -> {
                            DownloadQueue.deleteChapter(MainActivity.this, novel, chapter);
                            // Downloads never touch progress: repaint only.
                            repaintDownloadState();
                        });
            }

            @Override
            public void onDownloadVolume(Volume volume) {
                int queued = DownloadQueue.enqueueVolume(MainActivity.this, novel,
                        volume.number, volume.chapters);
                Toast.makeText(MainActivity.this,
                        queued == 0
                                ? "Volume ini sudah tersimpan semua."
                                : "Mengunduh " + queued + " chapter " + volume.title() + "…",
                        Toast.LENGTH_SHORT).show();
                repaintDownloadState();
            }

            @Override
            public void onCancelVolume(Volume volume) {
                DownloadQueue.cancelVolume(MainActivity.this, novel, volume.number);
                Toast.makeText(MainActivity.this,
                        "Unduhan " + volume.title() + " dibatalkan. Chapter yang sudah selesai "
                                + "tetap tersimpan.", Toast.LENGTH_LONG).show();
                repaintDownloadState();
            }
        };
    }

    /** Read-only view of the download layer for the detail page. */
    private LibraryPage.DownloadState downloadState(final Novel novel) {
        final OfflineStore store = OfflineStore.get(this);
        return new LibraryPage.DownloadState() {
            @Override
            public int state(Chapter chapter) {
                return DownloadStatusStore.resolve(novel.id, chapter.url, store);
            }

            @Override
            public String statusLabel(Chapter chapter) {
                return DownloadQueue.statusLabel(MainActivity.this, novel, chapter);
            }

            @Override
            public int[] volumeProgress(Volume volume) {
                return DownloadQueue.volumeProgress(MainActivity.this, novel,
                        volume.number, volume.chapters);
            }

            @Override
            public boolean isVolumeRunning(Volume volume) {
                int[] progress = DownloadStatusStore.progress(novel.id, volume.number);
                if (progress[1] <= 0) return false;
                return DownloadStatusStore.state(DownloadStatusStore.key(novel.id,
                        volume.chapters.isEmpty() ? "" : volume.chapters.get(0).url))
                        == DownloadStatusStore.DOWNLOADING
                        || DownloadStatusStore.state(DownloadStatusStore.key(novel.id,
                        volume.chapters.isEmpty() ? "" : volume.chapters.get(0).url))
                        == DownloadStatusStore.QUEUED
                        || progressInFlight(novel, volume, progress);
            }
        };
    }

    /** True when any chapter of the volume is still queued or downloading. */
    private boolean progressInFlight(Novel novel, Volume volume, int[] progress) {
        if (progress[1] <= 0 || progress[0] >= progress[1]) return false;
        for (Chapter chapter : volume.chapters) {
            int state = DownloadStatusStore.state(DownloadStatusStore.key(novel.id, chapter.url));
            if (state == DownloadStatusStore.QUEUED || state == DownloadStatusStore.DOWNLOADING) {
                return true;
            }
        }
        return false;
    }

    /** Builds the "Unduhan" page with real chapter labels where the catalogue has them. */
    private View buildDownloadsPage() {
        Map<String, String> labels = new LinkedHashMap<>();
        for (Novel novel : Novels.all()) {
            Catalog catalog = Session.catalog(novel.id);
            if (catalog == null) continue;
            for (Chapter chapter : catalog.flat()) labels.put(chapter.url, chapter.label);
        }

        return new DownloadsPage(this, palette, OfflineStore.get(this),
                new DownloadsPage.Listener() {
                    @Override
                    public void onDeleteChapter(Novel novel, Chapter chapter) {
                        confirmDelete("Hapus unduhan " + chapter.label + "?",
                                "Chapter ini dihapus dari perangkat. Progres membaca dan "
                                        + "bookmark tetap tersimpan.",
                                () -> {
                                    DownloadQueue.deleteChapter(MainActivity.this, novel, chapter);
                                    render();
                                });
                    }

                    @Override
                    public void onDeleteVolume(Novel novel, int volume, int chapterCount) {
                        confirmDelete("Hapus " + chapterCount + " chapter Volume " + volume + "?",
                                "Seluruh chapter tersimpan pada volume ini dihapus dari perangkat. "
                                        + "Progres membaca dan bookmark tetap tersimpan.",
                                () -> {
                                    DownloadQueue.deleteVolume(MainActivity.this, novel, volume);
                                    render();
                                });
                    }

                    @Override
                    public void onOpenStoredChapter(Novel novel, Chapter chapter) {
                        // Opening from the offline page must not require a catalogue:
                        // the reader resolves the stored copy first anyway.
                        Session.setCatalog(new Catalog(novel.id,
                                Session.catalog(novel.id) == null
                                        ? new ArrayList<Volume>()
                                        : Session.catalog(novel.id).volumes));
                        Intent intent = new Intent(MainActivity.this, ReaderActivity.class);
                        intent.putExtra(ReaderActivity.EXTRA_NOVEL, novel.id);
                        intent.putExtra(ReaderActivity.EXTRA_URL, chapter.url);
                        startActivity(intent);
                        if (Motion.enabled(MainActivity.this)) {
                            overridePendingTransition(R.anim.kuromaku_enter_right,
                                    R.anim.kuromaku_exit_left);
                        }
                    }
                }).withLabels(labels).build();
    }

    /** Repaints the current page so download status changes appear immediately. */
    private void repaintDownloadState() {
        if (loading) return;
        keepScrollHere();
        render();
    }

    /** Two-button confirmation. Deleting offline data never touches progress. */
    private void confirmDelete(String title, String message, Runnable confirmed) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus", (dialog, which) -> confirmed.run())
                .show();
    }

    private void keepScrollHere() {
        if (scroller != null) pendingScrollY = scroller.getScrollY();
    }

    private void openChapter(Novel novel, Chapter chapter) {
        Catalog catalog = Session.catalog(novel.id);
        if (catalog == null) {
            Toast.makeText(this, "Katalog belum siap", Toast.LENGTH_SHORT).show();
            return;
        }
        Session.setCatalog(catalog);
        Intent intent = new Intent(this, ReaderActivity.class);
        intent.putExtra(ReaderActivity.EXTRA_NOVEL, novel.id);
        intent.putExtra(ReaderActivity.EXTRA_URL, chapter.url);
        startActivity(intent);
        // Chapter list → Reader Mode: fade in plus a light slide from the right.
        if (Motion.enabled(this)) {
            overridePendingTransition(R.anim.kuromaku_enter_right, R.anim.kuromaku_exit_left);
        }
    }

    private void openImage(String url, String caption) {
        if (url == null || url.isEmpty()) return;
        Intent intent = new Intent(this, ImageViewerActivity.class);
        intent.putExtra(ImageViewerActivity.EXTRA_URL, url);
        intent.putExtra(ImageViewerActivity.EXTRA_CAPTION, caption);
        try {
            startActivity(intent);
            if (Motion.enabled(this)) {
                overridePendingTransition(R.anim.kuromaku_fade_in, R.anim.kuromaku_fade_out);
            }
        } catch (Exception error) {
            Toast.makeText(this, "Penampil gambar tidak tersedia", Toast.LENGTH_SHORT).show();
        }
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (Exception error) {
            Toast.makeText(this, "Tidak ada browser tersedia", Toast.LENGTH_SHORT).show();
        }
    }

    /** Android Back: detail page → library list, library → leave the app. */
    @Override
    public void onBackPressed() {
        if (selectedNovel != null || showingDownloads) {
            showLibrary();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reading progress may have changed in the reader; repaint in place so
        // the library keeps its scroll position.
        Palette current = Palette.of(prefs.theme());
        if (!current.label.equals(palette.label)) {
            palette = current;
            rebuildShell();
            return;
        }
        if (showingDownloads) {
            // A download may have finished while the reader was open.
            keepScrollHere();
            render();
            return;
        }
        if (!loading && selectedNovel != null && Session.catalog(selectedNovel.id) != null) {
            keepScrollHere();
            render();
        }
    }

    /** Live download status: repaint the detail page whenever a state changes. */
    private final DownloadStatusStore.Listener downloadWatcher =
            this::repaintDownloadState;

    @Override
    protected void onStart() {
        super.onStart();
        DownloadStatusStore.addListener(downloadWatcher);
    }

    @Override
    protected void onStop() {
        DownloadStatusStore.removeListener(downloadWatcher);
        super.onStop();
    }
}
