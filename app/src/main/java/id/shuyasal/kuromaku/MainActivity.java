package id.shuyasal.kuromaku;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.PopupMenu;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.content.SharedPreferences;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
import id.shuyasal.kuromaku.data.Library;
import id.shuyasal.kuromaku.data.Novel;
import id.shuyasal.kuromaku.data.ReadingBackup;
import id.shuyasal.kuromaku.net.CatalogLoader;
import id.shuyasal.kuromaku.net.NovelUrl;
import id.shuyasal.kuromaku.parser.NovelDiscovery;
import id.shuyasal.kuromaku.shinigami.ShinigamiActivity;
import id.shuyasal.kuromaku.shinigami.ShinigamiStore;
import id.shuyasal.kuromaku.offline.DownloadQueue;
import id.shuyasal.kuromaku.offline.DownloadStatusStore;
import id.shuyasal.kuromaku.offline.OfflineStore;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.ui.AddNovelPage;
import id.shuyasal.kuromaku.ui.DownloadsPage;
import id.shuyasal.kuromaku.ui.LibraryPage;
import id.shuyasal.kuromaku.ui.Motion;
import id.shuyasal.kuromaku.ui.NovelListPage;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.SectionDrawer;
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

    private static final String TAG = "KuromakuLibrary";

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
    private ImageButton sectionMenu;
    private ImageButton refreshButton;
    private ImageButton downloadsButton;
    private ImageButton themeButton;
    private ScrollView scroller;

    /** Novel whose detail page is open; null shows the library list. */
    private Novel selectedNovel;
    /** True while the "Unduhan" page is open instead of the library list. */
    private boolean showingDownloads;
    /** True while the "Tambah Novel" page is open. */
    private boolean showingAdd;
    /** State of the add page: typed URL, loading flag, error, preview. */
    private AddNovelPage.State addState = new AddNovelPage.State();
    /** The user's shelf: database-backed, no longer a hardcoded list. */
    private Library library;
    /** Scroll offset of the detail page, restored when coming back from Unduhan. */
    private int detailScrollY;
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
        palette = Palette.of(prefs.resolvedTheme());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        buildShell();
        handleDataAction(getIntent());
        // Paint whatever is already in memory first, then fetch in the
        // background: a returning user sees their library immediately instead
        // of an empty screen with a spinner.
        render();
        loadLibraries(false);
    }

    /** Reads the shelf. Synchronous on purpose: a handful of rows, no flash. */
    private Library library() {
        if (library == null) library = Library.get(this);
        return library;
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

        sectionMenu = Ui.iconButton(this, R.drawable.ic_menu, "Buka menu Novel dan Manga", palette);
        sectionMenu.setContentDescription("Menu Novel dan Manga");
        sectionMenu.setTag("section_menu");
        sectionMenu.setOnClickListener(v -> showSectionDrawer());
        toolbarRow.addView(sectionMenu, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));

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

        // Tema dan muat ulang dipindah ke menu titik tiga: toolbar beranda tidak
        // lagi memadati empat ikon, dan polanya sama dengan beranda lain.
        ImageButton overflow = Ui.iconButton(this, R.drawable.ic_more,
                "Menu Novel", palette);
        overflow.setTag("novel_overflow");
        overflow.setContentDescription("Menu Novel");
        overflow.setOnClickListener(v -> showNovelOverflow());
        LinearLayout.LayoutParams overflowParams = new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP));
        overflowParams.setMargins(Ui.dp(this, 2), 0, 0, 0);
        toolbarRow.addView(overflow, overflowParams);

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
        return library().all().size() + " novel · Kaori Translation";
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

    static final int REQUEST_BACKUP = 7121;
    static final int REQUEST_RESTORE = 7122;

    private void showSectionDrawer() {
        SectionDrawer.show(this, SectionDrawer.Section.NOVEL, palette, new SectionDrawer.Listener() {
            @Override
            public void onHome() {
                // Beranda = Source Hub; CLEAR_TOP mencegah tumpukan activity.
                startActivity(new Intent(MainActivity.this, SourceHubActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                finish();
            }

            @Override
            public void onNovel() {
                showLibrary();
            }

            @Override
            public void onMangaVoratoon() {
                startActivity(new Intent(MainActivity.this, MangaActivity.class));
            }

            @Override
            public void onShinigami() {
                startActivity(new Intent(MainActivity.this, ShinigamiActivity.class));
            }

            @Override
            public void onAnimeOtakudesu() {
                startActivity(new Intent(MainActivity.this,
                        id.shuyasal.kuromaku.anime.OtakudesuActivity.class));
            }

            @Override
            public void onKomiku() {
                startActivity(new Intent(MainActivity.this,
                        id.shuyasal.kuromaku.komiku.KomikuActivity.class));
                finish();
            }

            @Override
            public void onBackup() {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "kuromaku-reading-backup.json");
                startActivityForResult(intent, REQUEST_BACKUP);
            }

            @Override
            public void onRestore() {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                startActivityForResult(intent, REQUEST_RESTORE);
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDataAction(intent);
    }

    private void handleDataAction(Intent intent) {
        if (intent == null) return;
        String action = intent.getStringExtra("kuromaku_data_action");
        if (action == null) return;
        intent.removeExtra("kuromaku_data_action");
        if ("backup".equals(action)) {
            Intent doc = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            doc.addCategory(Intent.CATEGORY_OPENABLE);
            doc.setType("application/json");
            doc.putExtra(Intent.EXTRA_TITLE, "kuromaku-reading-backup.json");
            startActivityForResult(doc, REQUEST_BACKUP);
        } else if ("restore".equals(action)) {
            Intent doc = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            doc.addCategory(Intent.CATEGORY_OPENABLE);
            doc.setType("application/json");
            startActivityForResult(doc, REQUEST_RESTORE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        // The format and the type tags live in ReadingBackup so they can be tested
        // without an activity: a restore that drops a type silently resets progress,
        // and that is exactly the failure a backup feature must not have.
        try {
            if (requestCode == REQUEST_BACKUP) {
                SharedPreferences store = getSharedPreferences(Prefs.FILE_NAME, MODE_PRIVATE);
                SharedPreferences manga = getSharedPreferences(ShinigamiStore.FILE_NAME, MODE_PRIVATE);
                SharedPreferences anime = getSharedPreferences(
                        id.shuyasal.kuromaku.anime.OtakudesuStore.FILE_NAME, MODE_PRIVATE);
                String document = ReadingBackup.export(store, manga, anime);
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out == null) throw new java.io.IOException("Berkas tujuan tidak bisa dibuka");
                    out.write(document.getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this, "Backup progres berhasil disimpan", Toast.LENGTH_LONG).show();
            } else if (requestCode == REQUEST_RESTORE) {
                byte[] bytes;
                try (InputStream in = getContentResolver().openInputStream(data.getData());
                     java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                    if (in == null) throw new java.io.IOException("Berkas backup tidak bisa dibuka");
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        out.write(buffer, 0, count);
                        // Stop reading rather than hold an unbounded file in memory.
                        if (out.size() > ReadingBackup.MAX_BYTES) {
                            throw new java.io.IOException("Berkas backup terlalu besar");
                        }
                    }
                    bytes = out.toByteArray();
                }
                SharedPreferences store = getSharedPreferences(Prefs.FILE_NAME, MODE_PRIVATE);
                SharedPreferences manga = getSharedPreferences(ShinigamiStore.FILE_NAME, MODE_PRIVATE);
                SharedPreferences anime = getSharedPreferences(
                        id.shuyasal.kuromaku.anime.OtakudesuStore.FILE_NAME, MODE_PRIVATE);
                int restored = ReadingBackup.restore(store,
                        new String(bytes, StandardCharsets.UTF_8), manga, anime);
                Toast.makeText(this, restored + " progres dipulihkan", Toast.LENGTH_LONG).show();
                // No catalogue reload needed: progress is read from the store while
                // rendering, so going back to the shelf shows the restored bookmark
                // immediately. Clearing the catalogue cache here would force a
                // network fetch and make a successful restore fail when offline.
                showLibrary();
            }
        } catch (Exception error) {
            String reason = error.getMessage();
            Toast.makeText(this, "Gagal: " + (reason == null ? error.getClass().getSimpleName() : reason),
                    Toast.LENGTH_LONG).show();
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
            for (Novel novel : library().all()) {
                try {
                    CatalogLoader.Loaded loaded = CatalogLoader.load(novel);
                    Session.setCatalog(loaded.catalog);
                    // Covers found on this parse are stored, so the detail page can
                    // draw them without re-reading the catalogue page. A volume the
                    // source did not describe simply has no row.
                    try {
                        library().saveVolumeCovers(novel.id, loaded.catalog);
                    } catch (RuntimeException ignored) {
                        // A full disk or a closed database must not fail the reload:
                        // the catalogue in memory is still correct.
                    }
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
        for (Novel novel : library().all()) {
            if (Session.catalog(novel.id) == null) return false;
        }
        return true;
    }

    /**
     * Draws the current page: library list, novel detail, or the offline library.
     *
     * <p>BUG (2.7): this used to call {@code content.removeAllViews()} and then
     * build the page. Any exception thrown while building — a corrupt download
     * index, a state object that could not resolve a chapter, a repaint triggered
     * from the download worker's thread — therefore left the screen with the
     * toolbar intact and <em>nothing</em> below it: exactly the reported blank
     * page, with no error and no way back. The page is now built first and only
     * swapped in once it exists; a failure keeps the previous page and shows a
     * readable message plus a retry instead of erasing everything.
     */
    private void render() {
        spinnerRow.setVisibility(loading ? View.VISIBLE : View.GONE);
        status.setVisibility(loading ? View.VISIBLE : View.GONE);

        int keepScroll = pendingScrollY > 0 ? pendingScrollY : scroller.getScrollY();

        boolean downloads = showingDownloads;
        boolean adding = showingAdd;
        boolean isLibrary = selectedNovel == null && !downloads && !adding;
        sectionMenu.setVisibility(isLibrary ? View.VISIBLE : View.GONE);
        backButton.setVisibility(isLibrary ? View.GONE : View.VISIBLE);
        titleText.setText(downloads ? "Unduhan"
                : adding ? "Tambah Novel"
                : selectedNovel == null ? "Perpustakaan" : "Detail Novel");
        subtitleView.setText(downloads ? "Tersimpan di perangkat ini"
                : adding ? "Dari halaman katalog Kaori Translation"
                : subtitleText());

        View page;
        try {
            page = buildPageContent();
        } catch (RuntimeException error) {
            Log.e(TAG, "gagal membangun halaman " + currentPageName(), error);
            page = buildPageFailure(error);
        }

        // Only now that the new page exists is the old one discarded, so a build
        // failure can never leave an empty screen behind a live toolbar.
        content.removeAllViews();
        if (!failed.isEmpty()) {
            TextView warning = Ui.label(this, "⚠ " + failed.get(0), 12, palette.accent);
            warning.setTag("load_warning");
            warning.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 10));
            content.addView(warning);
        }
        content.addView(page, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (keepScroll > 0) {
            pendingScrollY = 0;
            scroller.post(() -> scroller.scrollTo(0, keepScroll));
        } else {
            scroller.post(() -> scroller.scrollTo(0, 0));
        }
    }

    /** Name of the page currently open, for error messages. */
    private String currentPageName() {
        if (showingDownloads) return "Unduhan";
        if (showingAdd) return "Tambah Novel";
        if (selectedNovel != null) return "Detail Novel";
        return "Perpustakaan";
    }

    /**
     * Test seam: forces page building to fail so the "never leave a blank page"
     * guarantee can be exercised. Always null in production.
     */
    static volatile Runnable pageFault;

    /** Builds the body of the open page without touching the live view tree. */
    private View buildPageContent() {
        Runnable fault = pageFault;
        if (fault != null) fault.run();
        if (showingDownloads) return buildDownloadsPage();
        if (showingAdd) {
            return new AddNovelPage(this, palette, addState, addListener()).build();
        }
        if (selectedNovel == null) {
            return new NovelListPage(this, prefs, palette, library().all(), this::showNovel)
                    .withMenu(novelMenu())
                    .withAddNovel(this::showAddNovel)
                    .build();
        }
        Catalog catalog = Session.catalog(selectedNovel.id);
        if (catalog != null) {
            // Covers found on an earlier parse are stored, so opening the detail page
            // still shows real artwork when the catalogue came from cache or when the
            // source page is temporarily unreachable. Volumes that already carry a
            // cover keep it; nothing is borrowed from another volume.
            try {
                catalog = catalog.withStoredCovers(
                        library().storedVolumeCovers(selectedNovel.id));
            } catch (RuntimeException ignored) {
                // A database problem must not stop the page from rendering.
            }
        }
        // Scoped prefs: the continue button and chapter progress on this page
        // belong to this novel only.
        return new LibraryPage(this, prefs.scoped(selectedNovel.id), palette,
                selectedNovel, catalog,
                synopsisOpen, detailListener(selectedNovel))
                .setDownload(downloadListener(selectedNovel), downloadState(selectedNovel))
                .build();
    }

    /**
     * Shown instead of a blank screen when a page cannot be built at all. The
     * user sees what failed and gets a way out, rather than a view-less page.
     */
    private View buildPageFailure(RuntimeException error) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setTag("page_failure");

        TextView heading = Ui.label(this, "Halaman " + currentPageName() + " gagal dimuat",
                16, palette.text);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        box.addView(heading);

        String reason = error.getMessage() == null
                ? error.getClass().getSimpleName() : error.getMessage();
        TextView detail = Ui.label(this, reason, 12, palette.muted);
        detail.setTag("page_failure_reason");
        detail.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 12));
        box.addView(detail);

        Button retry = Ui.chip(this, "Coba lagi", palette);
        retry.setTag("page_failure_retry");
        retry.setMinimumHeight(Ui.dp(this, Ui.TOUCH_DP));
        retry.setOnClickListener(v -> render());
        box.addView(retry);
        return box;
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
    /**
     * Menu titik tiga beranda Novel: tema, muat ulang, unduhan.
     *
     * <p>Isinya hanya aksi yang dulu tersebar sebagai ikon di toolbar; tidak ada
     * fungsi yang hilang. Membuka menu tidak menyentuh tab, katalog, posisi
     * gulir, atau unduhan yang sedang berjalan.
     */
    private void showNovelOverflow() {
        boolean adaLaporan = id.shuyasal.kuromaku.offline.CrashLog.exists(this);
        String[] options = adaLaporan
                ? new String[]{"Tema tampilan", "Muat ulang katalog", "Unduhan offline",
                        "Laporan crash terakhir"}
                : new String[]{"Tema tampilan", "Muat ulang katalog", "Unduhan offline"};
        new AlertDialog.Builder(this)
                .setTitle("Menu Novel")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showNovelThemeDialog();
                    else if (which == 1) loadLibraries(true);
                    else if (which == 2) showDownloads();
                    else showCrashReport();
                })
                .setNegativeButton("Tutup", null)
                .show();
    }

    /**
     * Tampilkan laporan crash terakhir.
     *
     * <p>Ada di dalam aplikasi supaya pengguna bisa mengirim penyebab force close
     * tanpa perlu adb atau komputer.
     */
    private void showCrashReport() {
        String laporan = id.shuyasal.kuromaku.offline.CrashLog.read(this);
        if (laporan.isEmpty()) {
            Toast.makeText(this, "Tidak ada laporan crash.", Toast.LENGTH_SHORT).show();
            return;
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        android.widget.TextView text = new android.widget.TextView(this);
        text.setText(laporan);
        text.setTextSize(11f);
        text.setTypeface(android.graphics.Typeface.MONOSPACE);
        text.setTextIsSelectable(true);
        int pad = Ui.dp(this, 16);
        text.setPadding(pad, pad, pad, pad);
        scroll.addView(text);
        new AlertDialog.Builder(this)
                .setTitle("Laporan crash terakhir")
                .setView(scroll)
                .setPositiveButton("Salin", (dialog, which) -> {
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("crash", laporan));
                        Toast.makeText(this, "Laporan disalin.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Hapus", (dialog, which) -> {
                    id.shuyasal.kuromaku.offline.CrashLog.clear(this);
                    Toast.makeText(this, "Laporan dihapus.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Tutup", null)
                .show();
    }

    /** Terapkan tema pilihan pengguna tanpa memuat ulang katalog. */
    void applyThemeChoice(int which) {
        prefs.setTheme(which);
        palette = Palette.of(prefs.resolvedTheme());
        rebuildShell();
    }

    /** Tema terang, gelap, atau mengikuti sistem. */
    private void showNovelThemeDialog() {
        String[] names = {"Terang", "Gelap", "Ikuti sistem"};
        int current = Math.max(0, Math.min(2, prefs.theme()));
        new AlertDialog.Builder(this)
                .setTitle("Tema tampilan")
                .setSingleChoiceItems(names, current, (dialog, which) -> {
                    applyThemeChoice(which);
                    dialog.dismiss();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void showDownloads() {
        if (showingDownloads) return;
        keepScrollHere();
        showingDownloads = true;
        // The open novel stays selected: it is what "Lihat Chapter" and Back
        // return to. Clearing it here was why the detail page could come back
        // empty — the activity had forgotten which book it was showing.
        detailScrollY = scroller.getScrollY();
        pendingScrollY = 0;
        render();
        scroller.post(() -> scroller.scrollTo(0, 0));
        Motion.enterFromRight(pageHost);
    }

    /** Closes the offline page and puts the user back where they came from. */
    private void closeDownloads() {
        if (!showingDownloads) return;
        Motion.exitToRight(pageHost, () -> {
            showingDownloads = false;
            pendingScrollY = detailScrollY;
            render();
            Motion.enterFromLeft(pageHost);
        });
    }

    /**
     * Back one level: downloads → the detail page it was opened from, detail →
     * the library list. Each step keeps the state of the page it returns to, so
     * nothing the user was looking at disappears.
     */
    private void goBackOneLevel() {
        if (showingAdd) {
            Motion.exitToRight(pageHost, () -> {
                showingAdd = false;
                addState = new AddNovelPage.State();
                pendingScrollY = 0;
                render();
                scroller.post(() -> scroller.scrollTo(0, 0));
                Motion.enterFromLeft(pageHost);
            });
            return;
        }
        if (showingDownloads && selectedNovel != null) {
            closeDownloads();
            return;
        }
        if (showingDownloads || selectedNovel != null) {
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
    }

    /** Returns to the library list: the detail page slides right and fades out. */
    private void showLibrary() {
        goBackOneLevel();
    }

    // ------------------------------------------------------- adding and removing

    /** Opens the "Tambah Novel" page with a clean state. */
    private void showAddNovel() {
        if (showingAdd) return;
        keepScrollHere();
        addState = new AddNovelPage.State();
        addState.url = "";
        showingAdd = true;
        pendingScrollY = 0;
        render();
        scroller.post(() -> scroller.scrollTo(0, 0));
        Motion.enterFromRight(pageHost);
    }

    /** Keeps what the user typed when the page is rebuilt for loading/error/preview. */
    private void repaintAddPage(boolean keepScroll) {
        if (keepScroll) keepScrollHere();
        render();
    }

    private AddNovelPage.Listener addListener() {
        return new AddNovelPage.Listener() {
            @Override
            public void onFetch(String url) {
                startInspect(url);
            }

            @Override
            public void onRetry() {
                startInspect(addState.url);
            }

            @Override
            public void onSave() {
                savePreview();
            }

            @Override
            public void onCancelPreview() {
                addState = new AddNovelPage.State();
                repaintAddPage(false);
                scroller.post(() -> scroller.scrollTo(0, 0));
            }

            @Override
            public void onTogglePicking() {
                addState.picking = !addState.picking;
                if (addState.picking && addState.preview != null && addState.selected.isEmpty()) {
                    // Start from what the parser already accepted, so the user only
                    // has to correct it instead of rebuilding the list by hand.
                    for (NovelDiscovery.Candidate candidate : addState.preview.candidates) {
                        if (candidate.accepted) addState.selected.add(candidate.url);
                    }
                }
                repaintAddPage(true);
            }

            @Override
            public void onApplySelection(java.util.List<String> urls) {
                applySelection(urls);
            }
        };
    }

    /**
     * Rebuilds the preview from the links the user ticked.
     *
     * <p>The URLs are matched against the candidates the parser produced for this
     * exact page, so a link that was never on the page cannot be introduced here.
     * When nothing is ticked the preview is left as it was and the user is told
     * why, rather than being shown an empty catalogue.
     */
    private void applySelection(java.util.List<String> urls) {
        NovelDiscovery.Result base = addState.preview;
        if (base == null) return;
        try {
            NovelDiscovery.Result picked = NovelDiscovery.reselect(base, urls);
            addState.preview = picked;
            addState.selected.clear();
            addState.selected.addAll(urls);
            addState.picking = true;
            addState.duplicate = library().byUrl(picked.sourceUrl) != null;
            repaintAddPage(true);
            Toast.makeText(this, picked.chapterCount() + " chapter dipakai dari pilihan Tuan.",
                    Toast.LENGTH_SHORT).show();
        } catch (NovelDiscovery.Unreadable error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Reads a catalogue URL in the background and turns the result into a preview.
     *
     * <p>Three things are decided before any network call: the text must be a Kaori
     * Translation article page (so a random site is refused with a readable reason
     * rather than a mystery timeout), the URL is normalised so {@code ?m=1} and a
     * trailing slash cannot create a second novel, and an already-shelved URL is
     * reported as a duplicate instead of being fetched again.
     */
    private void startInspect(String rawUrl) {
        final String normalised;
        try {
            normalised = NovelUrl.normalize(rawUrl);
        } catch (NovelUrl.Invalid error) {
            addState.url = rawUrl == null ? "" : rawUrl.trim();
            addState.loading = false;
            addState.preview = null;
            addState.error = error.getMessage();
            addState.duplicate = false;
            repaintAddPage(false);
            return;
        }

        addState.url = normalised;
        addState.loading = true;
        addState.error = "";
        addState.preview = null;
        addState.duplicate = false;
        // A new address means a new list: keeping the previous ticks would apply
        // one page's links to another page.
        addState.selected.clear();
        addState.picking = false;
        repaintAddPage(false);

        new Thread(() -> {
            Novel existing = library().byUrl(normalised);
            if (existing != null) {
                main.post(() -> {
                    addState.loading = false;
                    addState.duplicate = true;
                    addState.error = "";
                    addState.preview = previewOf(existing);
                    repaintAddPage(false);
                });
                return;
            }
            try {
                NovelDiscovery.Result result = CatalogLoader.inspect(normalised);
                main.post(() -> {
                    addState.loading = false;
                    addState.error = "";
                    addState.preview = result;
                    addState.duplicate = library().byUrl(normalised) != null;
                    repaintAddPage(false);
                    scroller.post(() -> scroller.scrollTo(0, 0));
                });
            } catch (Exception error) {
                final String reason = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                main.post(() -> {
                    addState.loading = false;
                    addState.preview = null;
                    addState.error = reason;
                    repaintAddPage(false);
                });
            }
        }).start();
    }

    /**
     * Preview for a novel that is already on the shelf.
     *
     * <p>The stored metadata is shown instead of an empty page, so the user sees
     * which book they already have. Its catalogue is read from the session copy
     * when one is loaded, without troubling the network again.
     */
    private NovelDiscovery.Result previewOf(Novel existing) {
        Catalog cached = Session.catalog(existing.id);
        java.util.List<Volume> volumes = cached == null
                ? new ArrayList<Volume>() : cached.volumes;
        return NovelDiscovery.fromStored(existing, volumes);
    }

    /** Writes the previewed novel to the shelf. */
    private void savePreview() {
        NovelDiscovery.Result preview = addState.preview;
        if (preview == null) return;

        String id = NovelUrl.id(preview.sourceUrl);
        NovelUrl.Source src = NovelUrl.sourceOf(preview.sourceUrl);
        String sourceName = src != null ? src.displayName : "Kaori Translation";
        Novel novel = new Novel(
                id,
                preview.title,
                preview.titleJp,
                preview.fullTitle,
                preview.author,
                preview.illustrator,
                preview.genres,
                Novel.statusOrDefault(preview.status),
                sourceName,
                preview.sourceUrl,
                preview.sourceUrl,
                preview.coverUrl,
                preview.coverRatio,
                preview.synopsis,
                Math.min(2, Math.max(1, preview.synopsis.length)),
                Novel.CATALOG_DISCOVERED,
                "",
                "",
                false);

        Library.Result result = library().add(novel);
        if (result.status == Library.Added.DUPLICATE) {
            addState.duplicate = true;
            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
            repaintAddPage(false);
            return;
        }
        if (result.status == Library.Added.REFUSED) {
            addState.error = result.message;
            repaintAddPage(false);
            return;
        }

        // The catalogue that was just parsed is kept, so opening the novel works
        // immediately even without a second fetch.
        Catalog added = new Catalog(result.novel.id, preview.volumes);
        Session.setCatalog(added);
        // Covers found while parsing this page are stored now: waiting for a
        // reload would leave the volume rows showing "Vol 1", "Vol 2", "Vol 3"
        // until the user pressed Muat Ulang.
        try {
            library().saveVolumeCovers(result.novel.id, added);
        } catch (RuntimeException ignored) {
            // Storing is an optimisation; the catalogue already carries the covers.
        }
        showingAdd = false;
        addState = new AddNovelPage.State();
        failed.clear();
        pendingScrollY = 0;
        render();
        scroller.post(() -> scroller.scrollTo(0, 0));
        Toast.makeText(this, "“" + result.novel.title + "” ditambahkan ke perpustakaan.",
                Toast.LENGTH_LONG).show();
    }

    /** Three-dot menu on a library card. */
    private NovelListPage.MenuListener novelMenu() {
        return new NovelListPage.MenuListener() {
            @Override
            public void onOpenNovel(Novel novel) {
                showNovel(novel);
            }

            @Override
            public void onRefreshNovel(Novel novel) {
                refreshNovel(novel);
            }

            @Override
            public void onDeleteNovel(Novel novel) {
                confirmDeleteNovel(novel);
            }
        };
    }

    /**
     * Refreshes one novel: re-reads its catalogue and its metadata, then updates
     * the row.
     *
     * <p>The refresh never replaces identity: the stored id, URL and library
     * position stay as they are, and an empty field from the page cannot erase a
     * value that is already known. That is what keeps reading progress attached to
     * the same novel.
     */
    private void refreshNovel(Novel novel) {
        if (novel == null) return;
        Toast.makeText(this, "Memperbarui " + novel.title + "…", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            String problem = "";
            NovelDiscovery.Result preview = null;
            try {
                CatalogLoader.Loaded loaded = CatalogLoader.load(novel);
                Session.setCatalog(loaded.catalog);
                if (loaded.warning != null) problem = loaded.warning;
                // The catalogue read already fetched the page; the discovery result
                // rides along so a refresh costs exactly one request, never two.
                preview = loaded.discovery;
            } catch (Exception error) {
                problem = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
            }

            final String reason = problem;
            final NovelDiscovery.Result metadata = preview;
            main.post(() -> {
                if (metadata != null) {
                    library().updateMetadata(novelFrom(novel, metadata));
                }
                if (!reason.isEmpty()) {
                    failed.clear();
                    failed.add(novel.title + ": " + reason);
                }
                keepScrollHere();
                render();
            });
        }).start();
    }

    /** Stored novel updated with whatever the page printed; empty fields are kept. */
    private Novel novelFrom(Novel stored, NovelDiscovery.Result preview) {
        return new Novel(
                stored.id,
                preview.title.isEmpty() ? stored.title : preview.title,
                preview.titleJp.isEmpty() ? stored.titleJp : preview.titleJp,
                preview.fullTitle.isEmpty() ? stored.fullTitle : preview.fullTitle,
                preview.author.isEmpty() ? stored.author : preview.author,
                preview.illustrator.isEmpty() ? stored.illustrator : preview.illustrator,
                preview.genres.isEmpty() ? stored.genres : preview.genres,
                stored.status,
                stored.translation,
                stored.sourceLabelUrl,
                stored.landingUrl,
                preview.coverUrl.isEmpty() ? stored.coverUrl : preview.coverUrl,
                preview.coverRatio > 0 ? preview.coverRatio : stored.coverRatio,
                preview.synopsis.length > 0 ? preview.synopsis : stored.synopsis,
                stored.synopsisPreview,
                stored.catalogMode,
                stored.slugPrefix,
                stored.feedLabel,
                stored.builtIn);
    }

    /**
     * Delete dialog with the two documented choices.
     *
     * <p>Two independent checkboxes, because the user asked for them separately:
     * removing the shelf entry always keeps downloads and progress unless the
     * boxes say otherwise, and the progress checkbox only applies when the
     * downloads are being removed too.
     */
    private void confirmDeleteNovel(Novel novel) {
        if (novel == null) return;
        final OfflineStore store = OfflineStore.get(this);
        final int stored = store.savedCount(novel.id);
        final String size = OfflineStore.formatSize(store.bytesOf(novel.id));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setTag("delete_novel_dialog");
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 6), Ui.dp(this, 20), 0);

        TextView message = Ui.label(this, "“" + novel.title + "” akan dihapus dari "
                + "perpustakaan. Pilih apa yang ikut dihapus:", 13, palette.text);
        message.setTag("delete_novel_message");
        box.addView(message);

        final android.widget.CheckBox removeOffline = new android.widget.CheckBox(this);
        removeOffline.setText("Hapus juga unduhan offline ("
                + (stored == 0 ? "tidak ada unduhan" : stored + " chapter · " + size) + ")");
        removeOffline.setTag("delete_novel_offline");
        removeOffline.setTextColor(palette.text);
        removeOffline.setTextSize(12);
        removeOffline.setChecked(false);
        removeOffline.setEnabled(stored > 0);
        removeOffline.setMinHeight(Ui.dp(this, Ui.TOUCH_DP));
        box.addView(removeOffline);

        final android.widget.CheckBox removeProgress = new android.widget.CheckBox(this);
        removeProgress.setText("Hapus juga progres membaca dan bookmark");
        removeProgress.setTag("delete_novel_progress");
        removeProgress.setTextColor(palette.text);
        removeProgress.setTextSize(12);
        removeProgress.setChecked(false);
        removeProgress.setMinHeight(Ui.dp(this, Ui.TOUCH_DP));
        box.addView(removeProgress);

        TextView keep = Ui.label(this,
                "Jika tidak dicentang, unduhan dan progres membaca tetap tersimpan di "
                        + "perangkat dan tidak mengganggu novel lain.", 11, palette.muted);
        keep.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        box.addView(keep);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Hapus novel?")
                .setView(box)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus", (dialog, which) ->
                        deleteNovel(novel, removeOffline.isChecked(), removeProgress.isChecked()))
                .show();
    }

    /**
     * Performs the deletion the user chose.
     *
     * <p>Order matters: the shelf row is removed last, so a failure while deleting
     * files leaves the novel visible and retryable instead of silently losing the
     * only pointer to its content. Nothing here touches another novel: the offline
     * store is namespaced per novel and the preferences are scoped per novel.
     */
    private void deleteNovel(Novel novel, boolean offline, boolean progress) {
        if (offline) DownloadQueue.deleteNovel(this, novel);
        if (progress) prefs.scoped(novel.id).clearNovelData();
        library().remove(novel.id);
        Session.forget(novel.id);

        if (selectedNovel != null && selectedNovel.id.equals(novel.id)) {
            selectedNovel = null;
        }
        failed.clear();
        pendingScrollY = 0;
        render();
        scroller.post(() -> scroller.scrollTo(0, 0));

        String detail = offline
                ? (progress ? " beserta unduhan dan progres membaca." : " beserta unduhannya.")
                : (progress ? " beserta progres membaca." : "; unduhan dan progres tetap tersimpan.");
        Toast.makeText(this, "“" + novel.title + "” dihapus dari perpustakaan" + detail,
                Toast.LENGTH_LONG).show();
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
                // Downloading a whole volume is many chapters at once, so it is
                // never started silently: the user sees how many chapters and how
                // much of it is already stored before anything is fetched.
                final int total = volume.chapterCount();
                final int saved = OfflineStore.get(MainActivity.this)
                        .savedCountInVolume(novel.id, volume.number);
                final int missing = Math.max(0, total - saved);

                if (missing == 0) {
                    Toast.makeText(MainActivity.this,
                            "Volume ini sudah tersimpan semua.", Toast.LENGTH_SHORT).show();
                    repaintDownloadState();
                    return;
                }
                if (total <= 1) {
                    startVolumeDownload(novel, volume);
                    return;
                }

                confirm("Unduh " + volume.title() + "?",
                        missing + " dari " + total + " chapter akan diunduh beserta seluruh "
                                + "ilustrasinya, lalu bisa dibaca tanpa internet. "
                                + "Proses berjalan di latar belakang dan bisa dibatalkan.",
                        "Unduh", () -> startVolumeDownload(novel, volume));
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

    /** Starts a volume job and reports how many chapters were queued. */
    private void startVolumeDownload(Novel novel, Volume volume) {
        int queued = DownloadQueue.enqueueVolume(this, novel, volume.number, volume.chapters);
        Toast.makeText(this,
                queued == 0
                        ? "Volume ini sudah tersimpan semua."
                        : "Mengunduh " + queued + " chapter " + volume.title() + "…",
                Toast.LENGTH_SHORT).show();
        repaintDownloadState();
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
            public int bytePercent(Chapter chapter) {
                // Straight from the downloader's byte counter. -1 means the
                // server declared no size, so the page spins instead of guessing.
                DownloadStatusStore.Bytes bytes = DownloadStatusStore.bytes(
                        DownloadStatusStore.key(novel.id, chapter.url));
                return bytes == null ? -1 : bytes.percent;
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
        for (Novel novel : library().all()) {
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
                    public void onBrowseChapters() {
                        closeDownloads();
                    }

                    @Override
                    public void onRetryJob(DownloadsPage.Job job) {
                        if (job == null || job.novel == null) return;
                        Catalog catalog = Session.catalog(job.novel.id);
                        Volume volume = catalog == null ? null : catalog.volume(job.volume);
                        if (volume == null) {
                            Toast.makeText(MainActivity.this,
                                    "Katalog belum siap, coba muat ulang.",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        int queued = DownloadQueue.enqueueVolume(MainActivity.this, job.novel,
                                volume.number, volume.chapters);
                        Toast.makeText(MainActivity.this,
                                queued == 0
                                        ? "Semua chapter volume ini sudah tersimpan."
                                        : "Mengunduh ulang " + queued + " chapter…",
                                Toast.LENGTH_SHORT).show();
                        render();
                    }

                    @Override
                    public void onCancelJob(DownloadsPage.Job job) {
                        if (job == null || job.novel == null) return;
                        DownloadQueue.cancelVolume(MainActivity.this, job.novel, job.volume);
                        Toast.makeText(MainActivity.this,
                                "Unduhan dibatalkan. Chapter yang sudah selesai tetap tersimpan.",
                                Toast.LENGTH_LONG).show();
                        render();
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
                }).withLabels(labels).withJobs(downloadJobs()).withNovels(library().all()).build();
    }

    /**
     * Live queue state for the offline page, read from the same store the worker
     * writes to. Nothing here is invented: a volume counts as active only while a
     * chapter of it is queued or downloading, and as failed only when nothing is
     * in flight and something actually failed.
     */
    private DownloadsPage.Jobs downloadJobs() {
        return new DownloadsPage.Jobs() {
            @Override
            public List<DownloadsPage.Job> active() {
                return jobsOf(true);
            }

            @Override
            public List<DownloadsPage.Job> failed() {
                return jobsOf(false);
            }
        };
    }

    private List<DownloadsPage.Job> jobsOf(boolean active) {
        List<DownloadsPage.Job> jobs = new ArrayList<>();
        OfflineStore store = OfflineStore.get(this);
        for (Novel novel : library().all()) {
            Catalog catalog = Session.catalog(novel.id);
            if (catalog == null) continue;
            for (Volume volume : catalog.volumes) {
                int inFlight = 0;
                int failed = 0;
                String message = "";
                for (Chapter chapter : volume.chapters) {
                    String key = DownloadStatusStore.key(novel.id, chapter.url);
                    int state = DownloadStatusStore.state(key);
                    if (state == DownloadStatusStore.QUEUED
                            || state == DownloadStatusStore.DOWNLOADING) {
                        inFlight++;
                    } else if (state == DownloadStatusStore.FAILED) {
                        failed++;
                        if (message.isEmpty()) message = DownloadStatusStore.message(key);
                    }
                }
                int total = volume.chapterCount();
                int stored = store.savedCountInVolume(novel.id, volume.number);
                if (active) {
                    if (inFlight == 0) continue;
                    jobs.add(new DownloadsPage.Job(novel, volume.number, stored, total, failed,
                            ""));
                } else {
                    if (inFlight > 0 || failed == 0) continue;
                    jobs.add(new DownloadsPage.Job(novel, volume.number, stored, total, failed,
                            message));
                }
            }
        }
        return jobs;
    }

    /** Repaints the current page so download status changes appear immediately. */
    private void repaintDownloadState() {
        if (loading) return;
        keepScrollHere();
        render();
    }

    /** Two-button confirmation. Deleting offline data never touches progress. */
    private void confirmDelete(String title, String message, Runnable confirmed) {
        confirm(title, message, "Hapus", confirmed);
    }

    /** Asks before doing something the user cannot trivially undo. */
    private void confirm(String title, String message, String positive, Runnable confirmed) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Batal", null)
                .setPositiveButton(positive, (dialog, which) -> confirmed.run())
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
