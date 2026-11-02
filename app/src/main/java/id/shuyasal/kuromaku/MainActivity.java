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

import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.LibraryBuilder;
import id.shuyasal.kuromaku.net.FeedClient;
import id.shuyasal.kuromaku.net.FeedException;
import id.shuyasal.kuromaku.ui.LibraryPage;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.Ui;

/**
 * Library screen: novel detail (cover, title, verified metadata, synopsis)
 * followed by an accordion of volumes and chapters.
 *
 * <p>The page body is built by {@link LibraryPage} so its geometry can be
 * measured in tests. Nothing here loads the source website: the catalogue comes
 * from the label feed and chapters are extracted by {@code ChapterParser}, so
 * there is no Blogspot chrome and no advertising.
 */
public class MainActivity extends AppCompatActivity {

    private Prefs prefs;
    private Palette palette;
    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout barBox;
    private LinearLayout spinnerRow;
    private TextView status;
    private ScrollView scroller;
    private Catalog catalog;
    private boolean loading;
    private boolean synopsisOpen;
    private int insetTop;
    private int insetBottom;
    private int pendingScrollY;

    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        palette = Palette.of(prefs.theme());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        buildShell();
        loadLibrary(false);
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

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.label(this, "Kuromaku Reader", 19, palette.text);
        title.setTag("app_title");
        title.setTypeface(null, Typeface.BOLD);
        titles.addView(title);
        titles.addView(Ui.label(this, "Monogatari no Kuromaku", 12, palette.muted));
        barBox.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button refresh = Ui.chip(this, "Muat ulang", palette);
        refresh.setTextSize(12);
        refresh.setTag("refresh");
        refresh.setOnClickListener(v -> loadLibrary(true));
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
        if (catalog == null) {
            loadLibrary(false);
        } else {
            render();
        }
    }

    private void applySystemBars() {
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(!palette.darkIcons);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(!palette.darkIcons);
    }

    private void loadLibrary(boolean manual) {
        if (loading) return;
        loading = true;
        spinnerRow.setVisibility(View.VISIBLE);
        status.setVisibility(View.VISIBLE);
        status.setText(manual ? "Memuat ulang katalog lengkap…"
                : "Memuat katalog dari Kaori Translation…");

        new Thread(() -> {
            try {
                FeedClient.Result result = FeedClient.fetchEntries();
                Catalog built = LibraryBuilder.build(result.entries);
                if (built.size() == 0) {
                    throw new IllegalStateException(
                            "Tidak ada chapter yang ditemukan pada feed.");
                }
                final String warning = result.warning;
                final String summary = "Dibaca " + result.pagesRead + " halaman feed · "
                        + result.entries.size() + "/"
                        + (result.expectedTotal > 0 ? result.expectedTotal : result.entries.size())
                        + " postingan";
                main.post(() -> {
                    loading = false;
                    catalog = built;
                    Session.setCatalog(built);
                    render();
                    if (warning != null && result.entries.size() > 0) {
                        showWarning(warning);
                    } else if (manual) {
                        Toast.makeText(this, summary, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception error) {
                main.post(() -> {
                    loading = false;
                    showFailure(error);
                });
            }
        }).start();
    }

    /** Partial crawl: keep the list but say plainly what is missing. */
    private void showWarning(String message) {
        TextView warning = Ui.label(this, "⚠ " + message, 12, palette.accent);
        warning.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 10));
        content.addView(warning, 0);
    }

    private void showFailure(Exception error) {
        spinnerRow.setVisibility(View.GONE);
        content.removeAllViews();
        status.setVisibility(View.GONE);

        TextView heading = Ui.label(this, "Katalog gagal dimuat", 17, palette.text);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 8));
        content.addView(heading);

        String reason;
        if (error instanceof FeedException && error.getCause() != null) {
            reason = error.getMessage() + " Sebab: " + error.getCause().getMessage();
        } else {
            reason = error.getMessage();
        }
        if (reason == null || reason.isEmpty()) reason = error.getClass().getSimpleName();

        TextView detail = Ui.label(this, reason
                + "\n\nPeriksa koneksi internet, lalu tekan Muat ulang.", 13, palette.muted);
        detail.setPadding(0, 0, 0, Ui.dp(this, 16));
        content.addView(detail);

        Button retry = Ui.chip(this, "Coba lagi", palette);
        retry.setOnClickListener(v -> loadLibrary(true));
        content.addView(retry);

        Button source = Ui.chip(this, "Buka sumber di browser", palette);
        source.setOnClickListener(v -> openExternal(FeedClient.LABEL_URL));
        content.addView(source);
    }

    /** Draws the whole library page: detail block first, then the volumes. */
    private void render() {
        spinnerRow.setVisibility(View.GONE);
        status.setVisibility(View.GONE);

        int keepScroll = pendingScrollY > 0 ? pendingScrollY : scroller.getScrollY();
        content.removeAllViews();

        if (catalog == null || catalog.size() == 0) {
            showFailure(new IllegalStateException("Katalog kosong."));
            return;
        }

        LibraryPage page = new LibraryPage(this, prefs, palette, catalog, synopsisOpen,
                new LibraryPage.Listener() {
                    @Override
                    public void onOpenChapter(Chapter chapter) {
                        openChapter(chapter);
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
                        prefs.toggleExpanded(volumeKey);
                        render();
                    }

                    @Override
                    public void onOpenSource() {
                        openExternal(FeedClient.LABEL_URL);
                    }
                });

        content.addView(page.build(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (keepScroll > 0) {
            pendingScrollY = 0;
            scroller.post(() -> scroller.scrollTo(0, keepScroll));
        }
    }

    private void keepScrollHere() {
        if (scroller != null) pendingScrollY = scroller.getScrollY();
    }

    private void openChapter(Chapter chapter) {
        Session.setCatalog(catalog);
        Intent intent = new Intent(this, ReaderActivity.class);
        intent.putExtra(ReaderActivity.EXTRA_URL, chapter.url);
        startActivity(intent);
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (Exception error) {
            Toast.makeText(this, "Tidak ada browser tersedia", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (catalog != null) {
            // Reading progress may have changed in the reader; repaint in place
            // so the library keeps its scroll position.
            Palette current = Palette.of(prefs.theme());
            if (current.label.equals(palette.label)) {
                keepScrollHere();
                render();
            } else {
                palette = current;
                rebuildShell();
            }
        }
    }
}
