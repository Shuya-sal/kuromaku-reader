package id.shuyasal.kuromaku;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
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
import java.util.List;

import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Novel;
import id.shuyasal.kuromaku.data.Novels;
import id.shuyasal.kuromaku.net.CatalogLoader;
import id.shuyasal.kuromaku.ui.LibraryPage;
import id.shuyasal.kuromaku.ui.NovelListPage;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.Ui;

/**
 * Library screen.
 *
 * <p>Two levels in one activity: the home list of novels (cover + title cards),
 * and the detail page of the novel that was tapped (cover, verified metadata,
 * synopsis, continue reading, and the volume list where every volume carries its
 * own cover). A back button returns to the list.
 *
 * <p>Nothing here loads the source website as a page: catalogues come from each
 * novel's own source shape and chapters are extracted by {@code ChapterParser},
 * so there is no Blogspot chrome and no advertising.
 */
public class MainActivity extends AppCompatActivity {

    private Prefs prefs;
    private Palette palette;
    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout barBox;
    private LinearLayout spinnerRow;
    private TextView status;
    private TextView barSubtitle;
    private Button backButton;
    private ScrollView scroller;

    /** Novel whose detail page is open; null shows the library list. */
    private Novel selectedNovel;
    private boolean loading;
    private boolean synopsisOpen;
    private int insetTop;
    private int insetBottom;
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
        loadLibraries();
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);

        barBox = new LinearLayout(this);
        barBox.setOrientation(LinearLayout.HORIZONTAL);
        barBox.setGravity(Gravity.CENTER_VERTICAL);
        barBox.setBackgroundColor(palette.surface);
        int pad = Ui.dp(this, 14);
        barBox.setPadding(pad, Ui.dp(this, 12), pad, Ui.dp(this, 12));

        backButton = Ui.chip(this, "‹", palette);
        backButton.setTag("back_to_library");
        backButton.setTextSize(14);
        backButton.setOnClickListener(v -> showLibrary());
        backButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        backParams.setMargins(0, 0, Ui.dp(this, 8), 0);
        barBox.addView(backButton, backParams);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.label(this, "Kuromaku Reader", 19, palette.text);
        title.setTag("app_title");
        title.setTypeface(null, Typeface.BOLD);
        titles.addView(title);
        barSubtitle = Ui.label(this, subtitleText(), 12, palette.muted);
        barSubtitle.setTag("app_subtitle");
        titles.addView(barSubtitle);
        barBox.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button refresh = Ui.chip(this, "Muat ulang", palette);
        refresh.setTextSize(12);
        refresh.setTag("refresh");
        refresh.setOnClickListener(v -> loadLibraries());
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        refreshParams.setMargins(0, 0, Ui.dp(this, 6), 0);
        barBox.addView(refresh, refreshParams);

        Button themeButton = Ui.chip(this, palette.label, palette);
        themeButton.setTag("theme");
        themeButton.setOnClickListener(v -> {
            prefs.setTheme((prefs.theme() + 1) % 3);
            palette = Palette.of(prefs.theme());
            rebuildShell();
        });
        barBox.addView(themeButton);

        root.addView(barBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        spinnerRow = new LinearLayout(this);
        spinnerRow.setGravity(Gravity.CENTER);
        spinnerRow.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 8));
        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        spinnerRow.addView(spinner);
        root.addView(spinnerRow);

        status = Ui.label(this, "Memuat katalog dari Kaori Translation…", 12, palette.muted);
        status.setGravity(Gravity.CENTER);
        status.setPadding(pad, 0, pad, Ui.dp(this, 10));
        root.addView(status);

        scroller = new ScrollView(this);
        scroller.setTag("library_scroller");
        scroller.setClipToPadding(false);
        scroller.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, Ui.dp(this, 4), pad, Ui.dp(this, 32));
        scroller.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        applySystemBars();
        applyInsetsListener();
    }

    private String subtitleText() {
        if (selectedNovel != null) return selectedNovel.title;
        return Novels.all().size() + " novel · Kaori Translation";
    }

    private void applyInsetsListener() {
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            if (bars.top != insetTop || bars.bottom != insetBottom) {
                insetTop = bars.top;
                insetBottom = bars.bottom;
                applyInsets();
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void applyInsets() {
        if (barBox != null) barBox.setPadding(Ui.dp(this, 14), insetTop + Ui.dp(this, 12),
                Ui.dp(this, 14), Ui.dp(this, 12));
        if (content != null) {
            content.setPadding(Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 14),
                    Ui.dp(this, 32) + insetBottom);
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
    private void loadLibraries() {
        if (loading) return;
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

    /** Draws either the novel list or the open novel's detail page. */
    private void render() {
        spinnerRow.setVisibility(loading ? View.VISIBLE : View.GONE);
        status.setVisibility(loading ? View.VISIBLE : View.GONE);

        int keepScroll = pendingScrollY > 0 ? pendingScrollY : scroller.getScrollY();
        content.removeAllViews();

        backButton.setVisibility(selectedNovel == null ? View.GONE : View.VISIBLE);
        barSubtitle.setText(subtitleText());

        if (!failed.isEmpty()) {
            TextView warning = Ui.label(this, "⚠ " + failed.get(0), 12, palette.accent);
            warning.setTag("load_warning");
            warning.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 10));
            content.addView(warning);
        }

        if (selectedNovel == null) {
            content.addView(new NovelListPage(this, prefs, palette, Novels.all(),
                    this::showNovel).build(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            Catalog catalog = Session.catalog(selectedNovel.id);
            // Scoped prefs: the continue button and chapter progress on this page
            // belong to this novel only.
            content.addView(new LibraryPage(this, prefs.scoped(selectedNovel.id), palette,
                    selectedNovel, catalog,
                    synopsisOpen, detailListener(selectedNovel)).build(),
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
                keepScrollHere();
                scoped.toggleExpanded(volumeKey);
                render();
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

    /** Opens one novel's detail page. */
    private void showNovel(Novel novel) {
        selectedNovel = novel;
        synopsisOpen = false;
        pendingScrollY = 0;
        if (Session.catalog(novel.id) == null) {
            loadLibraries();
        } else {
            render();
            scroller.post(() -> scroller.scrollTo(0, 0));
        }
    }

    private void showLibrary() {
        selectedNovel = null;
        synopsisOpen = false;
        pendingScrollY = 0;
        render();
        scroller.post(() -> scroller.scrollTo(0, 0));
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
    }

    private void openImage(String url, String caption) {
        if (url == null || url.isEmpty()) return;
        Intent intent = new Intent(this, ImageViewerActivity.class);
        intent.putExtra(ImageViewerActivity.EXTRA_URL, url);
        intent.putExtra(ImageViewerActivity.EXTRA_CAPTION, caption);
        try {
            startActivity(intent);
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

    @Override
    public void onBackPressed() {
        if (selectedNovel != null) {
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
        if (!loading && selectedNovel != null && Session.catalog(selectedNovel.id) != null) {
            keepScrollHere();
            render();
        }
    }
}
