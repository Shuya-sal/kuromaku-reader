package id.shuyasal.kuromaku;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Block;
import id.shuyasal.kuromaku.net.Http;
import id.shuyasal.kuromaku.offline.OfflineStore;
import id.shuyasal.kuromaku.parser.ChapterContent;
import id.shuyasal.kuromaku.parser.ChapterParser;
import id.shuyasal.kuromaku.parser.ContentCache;
import id.shuyasal.kuromaku.parser.ParseException;
import id.shuyasal.kuromaku.ui.BlockAdapter;
import id.shuyasal.kuromaku.ui.Motion;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.ScrollMath;
import id.shuyasal.kuromaku.ui.SystemBarInsets;
import id.shuyasal.kuromaku.ui.Ui;

/**
 * Reader Mode: shows only the extracted article.
 *
 * <p>Layout note (BUG 2): the reading list and the navigation panel are siblings
 * in a vertical LinearLayout, so the panel never overlays the text. The list
 * takes weight 1 and is clipped to its own bounds, and the last block carries
 * extra bottom padding plus the system navigation inset so the final paragraph
 * can always be scrolled above the panel.
 */
public class ReaderActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "chapter_url";
    /** Which novel this chapter belongs to, so navigation never crosses books. */
    public static final String EXTRA_NOVEL = "novel_id";

    private static final int PANEL_NONE = 0;
    private static final int PANEL_SETTINGS = 1;

    private Prefs prefs;
    private Palette palette;
    private Catalog catalog;
    private String novelId = "";

    private LinearLayout root;
    private LinearLayout topBar;
    private LinearLayout bottomBar;
    private LinearLayout panelBox;
    private RecyclerView list;
    private BlockAdapter adapter;
    private ProgressBar spinner;
    private LinearLayout errorBox;
    private ScrollView errorScroll;
    private TextView headerTitle;
    private TextView headerMeta;
    private Button backButton;
    private TextView bottomMeta;

    private String chapterUrl;
    private ChapterContent content;
    private int panel = PANEL_NONE;

    /**
     * Single source of truth for window insets (BUG 2.6).
     *
     * <p>The previous pair of int fields was only updated "when it changed", and a
     * layout-only inset dispatch that reports no system bars used to overwrite the
     * real value with 0 — after which the toolbar lost its status bar padding and
     * slid under the clock. This app never hides the system bars, so a dispatch
     * that reports nothing while we already know the real height is treated as
     * "no information yet" and ignored.
     */
    private final SystemBarInsets insets = new SystemBarInsets();
    private int pendingRestorePosition = -1;
    private int pendingRestoreOffset;
    private boolean progressSaved;

    // Captured before the adapter is filled, so the layout pass triggered by
    // setContent cannot overwrite the saved position with 0.
    private boolean restoring;
    private boolean restoreHasScroll;
    private int restorePosition;
    private int restoreOffset;
    private int restorePercent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        palette = Palette.of(prefs.theme());

        novelId = getIntent().getStringExtra(EXTRA_NOVEL);
        if (novelId == null || novelId.isEmpty()) novelId = id.shuyasal.kuromaku.data.Novels.MONOGATARI;
        // Progress, bookmarks and scroll offsets are stored per novel: two books
        // can have chapters with the same name without overwriting each other.
        prefs = prefs.scoped(novelId);
        catalog = Session.catalog(novelId);

        chapterUrl = getIntent().getStringExtra(EXTRA_URL);
        if (chapterUrl == null) chapterUrl = prefs.lastUrl();
        if (chapterUrl == null || chapterUrl.isEmpty()) {
            finish();
            return;
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        buildUi();
        openChapter(chapterUrl);
    }

    /**
     * Opens a chapter with the offline copy taking priority.
     *
     * <p>Order is deliberate: the memory cache (fastest, same session), then the
     * downloaded copy on disk, and only then the network. A downloaded chapter
     * therefore opens with no connection at all, and an online one behaves exactly
     * as before. Nothing here re-downloads anything.
     */
    private void openChapter(String url) {
        ChapterContent cached = ContentCache.get(url);
        if (cached != null) {
            show(cached);
            return;
        }
        ChapterContent stored = localChapter(url);
        if (stored != null) {
            ContentCache.put(url, stored);
            show(stored);
            return;
        }
        load(url);
    }

    /** Chapter from the offline store, or null when it is not downloaded. */
    private ChapterContent localChapter(String url) {
        try {
            return OfflineStore.get(this).load(novelId, url);
        } catch (Exception error) {
            // A damaged file must not crash the reader; fall through to network.
            return null;
        }
    }

    /** True when this device currently has a usable connection. */
    private boolean online() {
        try {
            android.net.ConnectivityManager manager =
                    (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (manager == null) return true;
            android.net.Network active = manager.getActiveNetwork();
            if (active == null) return false;
            android.net.NetworkCapabilities caps = manager.getNetworkCapabilities(active);
            return caps != null && caps.hasCapability(
                    android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception error) {
            return true;
        }
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);

        root.addView(buildTopBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout host = new FrameLayout(this);
        host.setBackgroundColor(palette.background);

        spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        host.addView(spinner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        list = new RecyclerView(this);
        LinearLayoutManager manager = new LinearLayoutManager(this);
        manager.setStackFromEnd(false);
        list.setLayoutManager(manager);
        list.setBackgroundColor(palette.background);
        list.setVerticalScrollBarEnabled(true);
        // The extra padding belongs to the scrollable area, not the stack bottom,
        // so the last paragraph can travel past the navigation panel.
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, Ui.dp(this, 12));
        adaptInsets();
        adapter = new BlockAdapter(this, prefs, palette);
        adapter.setOnOpenSource(this::openSource);
        adapter.setOnImageTap(this::openIllustration);
        list.setAdapter(adapter);
        list.setVisibility(View.GONE);
        host.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        errorBox = new LinearLayout(this);
        errorBox.setOrientation(LinearLayout.VERTICAL);
        errorBox.setPadding(Ui.dp(this, 24), Ui.dp(this, 32), Ui.dp(this, 24), Ui.dp(this, 24));
        errorBox.setVisibility(View.GONE);
        // BUG 2 root cause: this ScrollView used to be added full size and left
        // VISIBLE, so it sat on top of the RecyclerView and swallowed every drag.
        errorScroll = new ScrollView(this);
        errorScroll.setClipToPadding(false);
        errorScroll.addView(errorBox);
        errorScroll.setVisibility(View.GONE);
        host.addView(errorScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Weight 1: the reading area keeps whatever height the bars do not use.
        root.addView(host, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        panelBox = new LinearLayout(this);
        panelBox.setOrientation(LinearLayout.VERTICAL);
        panelBox.setVisibility(View.GONE);
        root.addView(panelBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(buildBottomBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        applySystemBars();
        // Apply the insets we already know before the next dispatch arrives, so a
        // repainted or re-created reader never flashes with the bar under the clock.
        adaptInsets();
        applyInsetsListener();

        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                saveProgress();
                updateProgressLabel();
            }
        });
    }

    /**
     * Edge-to-edge is mandatory from targetSdk 35, so the bars must inset
     * themselves instead of being covered by the status bar and gesture bar.
     */
    private void applyInsetsListener() {
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            // A layout-only dispatch can report no bars at all; the app never
            // hides them, so SystemBarInsets keeps the last real height instead of
            // accepting the zero and stripping the toolbar's padding.
            insets.accept(windowInsets);
            adaptInsets();
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    /** Applies the current insets to bars, list and footer padding. */
    private void adaptInsets() {
        if (topBar != null) {
            // Status bar inset belongs to the toolbar container only, so its
            // surface colour still paints behind the bar and nothing else needs
            // its own copy of the value.
            topBar.setPadding(0, insets.get().top, 0, 0);
        }
        if (bottomBar != null) {
            bottomBar.setPadding(0, 0, 0, insets.get().bottom);
        }
        if (panelBox != null) {
            panelBox.setPadding(0, 0, 0, 0);
        }
        if (list != null) {
            list.setPadding(0, 0, 0, Ui.dp(this, 12));
        }
        if (adapter != null) {
            // Space the last block must clear: panel height is not an overlay,
            // but the gesture bar plus a comfortable gap is.
            adapter.setBottomPadding(Math.max(Ui.dp(this, 28),
                    ScrollMath.footerPadding(Ui.dp(this, 28), insets.get().bottom)));
            adapter.notifyDataSetChanged();
        }
    }

    /**
     * Compact reader toolbar: a 56 dp row under the status bar inset holding the
     * "Kembali" button, the chapter title on one line, and the appearance icon.
     * The row is measured at a fixed height so a long chapter title ellipsizes
     * instead of pushing the reading area down.
     */
    private View buildTopBar() {
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.VERTICAL);
        topBar.setTag("reader_toolbar");
        topBar.setBackgroundColor(palette.surface);
        int pad = Ui.dp(this, 6);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(pad, 0, pad, 0);

        backButton = Ui.backButton(this, palette);
        backButton.setOnClickListener(v -> finishWithTransition());
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, Ui.TOUCH_DP));
        backParams.setMargins(0, 0, Ui.dp(this, 6), 0);
        row.addView(backButton, backParams);

        headerTitle = Ui.label(this, "", 13, palette.text);
        headerTitle.setTag("reader_title");
        headerTitle.setSingleLine(true);
        headerTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        headerTitle.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleParams.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 8), 0);
        row.addView(headerTitle, titleParams);

        ImageButton settings = Ui.iconButton(this, R.drawable.ic_sun, "Pengaturan tampilan",
                palette);
        settings.setTag("appearance");
        settings.setOnClickListener(v -> togglePanel());
        row.addView(settings, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));
        topBar.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 56)));

        headerMeta = Ui.label(this, "", 11, palette.muted);
        headerMeta.setTag("reader_meta");
        headerMeta.setSingleLine(true);
        headerMeta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        headerMeta.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 6));
        topBar.addView(headerMeta);
        return topBar;
    }

    /** Slides the reader away to the right so it matches the list's transition. */
    private void finishWithTransition() {
        finish();
        if (Motion.enabled(this)) {
            overridePendingTransition(R.anim.kuromaku_enter_left, R.anim.kuromaku_exit_right);
        }
    }

    private View buildBottomBar() {
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setBackgroundColor(palette.surface);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(this, 10);
        inner.setPadding(pad, Ui.dp(this, 8), pad, Ui.dp(this, 8));
        inner.setTag("inner");

        Button previous = Ui.chip(this, "‹ Sebelumnya", palette);
        previous.setTextSize(12);
        previous.setTag("previous");
        previous.setOnClickListener(v -> navigate(-1));
        inner.addView(previous, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        bottomMeta = Ui.label(this, "", 11, palette.muted);
        bottomMeta.setGravity(Gravity.CENTER);
        inner.addView(bottomMeta, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button next = Ui.chip(this, "Berikutnya ›", palette);
        next.setTextSize(12);
        next.setTag("next");
        next.setOnClickListener(v -> navigate(1));
        inner.addView(next, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        bottomBar.addView(inner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return bottomBar;
    }

    private LinearLayout bottomInner() {
        View found = bottomBar.findViewWithTag("inner");
        return found instanceof LinearLayout ? (LinearLayout) found : bottomBar;
    }

    private void applySystemBars() {
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(!palette.darkIcons);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(!palette.darkIcons);
    }

    private void load(final String url) {
        spinner.setVisibility(View.VISIBLE);
        list.setVisibility(View.GONE);
        errorScroll.setVisibility(View.GONE);
        headerTitle.setText("Memuat…");
        headerMeta.setText(describeChapter(url));

        new Thread(() -> {
            try {
                String html = id.shuyasal.kuromaku.net.Fetcher.get().page(url);
                ChapterContent parsed = ChapterParser.parse(html, url);
                ContentCache.put(url, parsed);
                runOnUiThread(() -> show(parsed));
            } catch (Exception error) {
                // A chapter that is already stored still wins over a failed
                // request: the connection dropping mid-open must not hide text
                // the device already has.
                ChapterContent stored = localChapter(url);
                if (stored != null) {
                    ContentCache.put(url, stored);
                    runOnUiThread(() -> show(stored));
                    return;
                }
                runOnUiThread(() -> showError(url, error));
            }
        }).start();
    }

    private String describeChapter(String url) {
        Chapter chapter = catalog == null ? null : catalog.findByUrl(url);
        if (chapter == null) return "Sumber: Kaori Translation";
        String volume = chapter.volume > 0 ? "Volume " + chapter.volume + " · " : "";
        return volume + chapter.published;
    }

    private void show(ChapterContent parsed) {
        content = parsed;
        progressSaved = false;
        spinner.setVisibility(View.GONE);
        // Hiding the error pane restores the reader as the topmost view.
        errorScroll.setVisibility(View.GONE);
        list.setVisibility(View.VISIBLE);

        // Read the saved position BEFORE the adapter is filled: filling it
        // triggers a layout pass, and the scroll listener would otherwise write
        // the temporary position 0 over the reader's real bookmark.
        captureSavedPosition();
        adapter.setContent(parsed);
        headerTitle.setText(parsed.title());
        headerMeta.setText(describeChapter(chapterUrl)
                + " · " + parsed.textCount() + " paragraf · " + parsed.imageCount() + " ilustrasi");

        prefs.setLastUrl(chapterUrl);
        updateNavigationLabels();

        restorePosition();
        updateProgressLabel();
    }

    /** Snapshots the bookmark so later layout passes cannot overwrite it. */
    private void captureSavedPosition() {
        if (prefs.hasScroll(chapterUrl)) {
            restoring = true;
            restoreHasScroll = true;
            restorePosition = prefs.scrollPosition(chapterUrl);
            restoreOffset = prefs.scrollOffset(chapterUrl);
            restorePercent = prefs.progress(chapterUrl);
        } else {
            int percent = prefs.progress(chapterUrl);
            restoring = percent > 0;
            restoreHasScroll = false;
            restorePosition = 0;
            restoreOffset = 0;
            restorePercent = percent;
        }
    }

    /**
     * Restores the exact block and pixel offset saved for this chapter. Falls
     * back to the percentage for chapters saved by an older app version.
     */
    private void restorePosition() {
        int total = adapter.blockCount();
        if (total <= 0) {
            restoring = false;
            return;
        }

        int position;
        int offset;
        if (restoreHasScroll) {
            position = restorePosition;
            offset = restoreOffset;
        } else {
            int percent = restorePercent;
            if (percent <= 0) return;
            position = ScrollMath.restoreIndex(percent, total);
            offset = 0;
        }

        if (position <= 0 && offset <= 0) {
            restoring = false;
            return;
        }
        position = Math.min(position, total - 1);
        pendingRestorePosition = position;
        pendingRestoreOffset = offset;

        list.post(() -> {
            RecyclerView.LayoutManager manager = list.getLayoutManager();
            if (manager instanceof LinearLayoutManager && pendingRestorePosition >= 0) {
                ((LinearLayoutManager) manager)
                        .scrollToPositionWithOffset(pendingRestorePosition, -pendingRestoreOffset);
            }
            pendingRestorePosition = -1;
            restoring = false;
        });

        int percent = restorePercent;
        if (percent > 0) {
            Toast.makeText(this, "Melanjutkan dari " + percent + "%", Toast.LENGTH_SHORT).show();
        }
    }

    /** Updates the percentage chip in the bottom bar as the user scrolls. */
    private void updateProgressLabel() {
        if (bottomMeta == null || content == null) return;
        int total = adapter.blockCount();
        if (total <= 0) return;
        RecyclerView.LayoutManager manager = list.getLayoutManager();
        if (!(manager instanceof LinearLayoutManager)) return;
        int last = ((LinearLayoutManager) manager).findLastVisibleItemPosition();
        int percent = ScrollMath.percent(last, total);
        String position = "";
        if (catalog != null) {
            int index = catalog.indexOf(chapterUrl);
            if (index >= 0) position = (index + 1) + "/" + catalog.size() + " · ";
        }
        bottomMeta.setText(position + percent + "%");
    }

    private void showError(final String url, Exception error) {
        content = null;
        spinner.setVisibility(View.GONE);
        list.setVisibility(View.GONE);
        errorScroll.setVisibility(View.VISIBLE);
        errorBox.setVisibility(View.VISIBLE);
        errorBox.removeAllViews();
        headerTitle.setText("Gagal memuat chapter");
        headerMeta.setText(describeChapter(url));

        // Offline + not downloaded is the one case where the message must be
        // explicit instead of generic: the reader needs to know the chapter is
        // simply not on this device yet, not that the app is broken.
        boolean offline = !online();
        boolean downloaded = OfflineStore.get(this).isSaved(novelId, url);

        TextView heading = Ui.label(this,
                offline && !downloaded
                        ? "Chapter ini belum diunduh"
                        : "Isi chapter tidak dapat diekstrak", 18, palette.text);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        errorBox.addView(heading);

        String explanationText;
        if (offline && !downloaded) {
            explanationText = "Tidak ada koneksi internet dan chapter ini belum tersimpan di "
                    + "perangkat. Sambungkan internet lalu unduh chapter ini (atau seluruh "
                    + "volumenya) dari daftar chapter, setelah itu chapter bisa dibaca "
                    + "sepenuhnya tanpa jaringan.";
        } else {
            String reason = error instanceof ParseException
                    ? error.getMessage()
                    : "Koneksi ke sumber gagal: " + (error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage());
            explanationText = reason
                    + "\n\nAplikasi tidak akan membuka tampilan website penuh secara diam-diam. "
                    + "Silakan coba lagi, atau buka halaman aslinya di browser.";
        }

        TextView explanation = Ui.label(this, explanationText, 14, palette.muted);
        explanation.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 18));
        errorBox.addView(explanation);

        Button retry = Ui.chip(this, "Coba lagi", palette);
        retry.setOnClickListener(v -> load(url));
        errorBox.addView(retry);

        Button source = Ui.chip(this, "Buka halaman sumber", palette);
        source.setOnClickListener(v -> openUrl(url));
        errorBox.addView(source);

        TextView link = Ui.label(this, url, 11, palette.accent);
        link.setPadding(0, Ui.dp(this, 14), 0, 0);
        link.setTextIsSelectable(true);
        errorBox.addView(link);

        if (bottomMeta != null) bottomMeta.setText("");
    }

    private void togglePanel() {
        panel = panel == PANEL_SETTINGS ? PANEL_NONE : PANEL_SETTINGS;
        renderPanel();
    }

    private void renderPanel() {
        panelBox.removeAllViews();
        if (panel == PANEL_NONE) {
            panelBox.setVisibility(View.GONE);
            return;
        }
        panelBox.setVisibility(View.VISIBLE);
        panelBox.setBackgroundColor(palette.surface);
        panelBox.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 12));

        panelBox.addView(sectionLabel("Tema"));
        LinearLayout themes = new LinearLayout(this);
        themes.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"Terang", "Gelap", "Sepia"};
        for (int i = 0; i < names.length; i++) {
            final int theme = i;
            Button button = Ui.chip(this, names[i], palette);
            if (prefs.theme() == i) button.setTextColor(palette.accent);
            button.setOnClickListener(v -> {
                prefs.setTheme(theme);
                palette = Palette.of(theme);
                repaint();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            params.setMargins(0, 0, Ui.dp(this, 6), 0);
            themes.addView(button, params);
        }
        panelBox.addView(themes);

        panelBox.addView(sectionLabel("Ukuran huruf · " + prefs.fontSize() + "sp"));
        LinearLayout fonts = new LinearLayout(this);
        fonts.setOrientation(LinearLayout.HORIZONTAL);
        Button smaller = Ui.chip(this, "A−", palette);
        smaller.setOnClickListener(v -> {
            prefs.setFontSize(prefs.fontSize() - 1);
            applyTypography();
        });
        Button larger = Ui.chip(this, "A+", palette);
        larger.setOnClickListener(v -> {
            prefs.setFontSize(prefs.fontSize() + 1);
            applyTypography();
        });
        fonts.addView(smaller, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        fonts.addView(larger, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        panelBox.addView(fonts);

        panelBox.addView(sectionLabel("Spasi baris · "
                + String.format(java.util.Locale.US, "%.2f", prefs.lineSpacing())));
        LinearLayout spacing = new LinearLayout(this);
        spacing.setOrientation(LinearLayout.HORIZONTAL);
        Button tighter = Ui.chip(this, "Rapat", palette);
        tighter.setOnClickListener(v -> {
            prefs.setLineSpacing(prefs.lineSpacing() - 0.1f);
            applyTypography();
        });
        Button looser = Ui.chip(this, "Renggang", palette);
        looser.setOnClickListener(v -> {
            prefs.setLineSpacing(prefs.lineSpacing() + 0.1f);
            applyTypography();
        });
        spacing.addView(tighter, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        spacing.addView(looser, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        panelBox.addView(spacing);

        Button justify = Ui.chip(this,
                prefs.justify() ? "Rata kanan-kiri: aktif" : "Rata kanan-kiri: mati", palette);
        justify.setOnClickListener(v -> {
            prefs.setJustify(!prefs.justify());
            applyTypography();
        });
        panelBox.addView(justify);

        Button source = Ui.chip(this, "Buka halaman sumber", palette);
        source.setOnClickListener(v -> openSource());
        panelBox.addView(source);
    }

    private TextView sectionLabel(String text) {
        TextView label = Ui.label(this, text, 11, palette.muted);
        label.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 4));
        return label;
    }

    /**
     * Typography changes alter item heights, so keep the reader near the block
     * it was showing instead of jumping back to the top.
     */
    private void applyTypography() {
        int anchor = currentFirstPosition();
        int offset = currentTopOffset();
        adapter.notifyDataSetChanged();
        if (anchor >= 0) {
            list.post(() -> {
                RecyclerView.LayoutManager manager = list.getLayoutManager();
                if (manager instanceof LinearLayoutManager) {
                    ((LinearLayoutManager) manager).scrollToPositionWithOffset(anchor, offset);
                }
            });
        }
        renderPanel();
    }

    private int currentFirstPosition() {
        RecyclerView.LayoutManager manager = list.getLayoutManager();
        if (manager instanceof LinearLayoutManager) {
            return ((LinearLayoutManager) manager).findFirstVisibleItemPosition();
        }
        return -1;
    }

    private int currentTopOffset() {
        int position = currentFirstPosition();
        if (position < 0) return 0;
        View child = list.getLayoutManager().findViewByPosition(position);
        return child == null ? 0 : child.getTop() - list.getPaddingTop();
    }

    private void repaint() {
        applySystemBars();
        // Re-assert the insets after any theme repaint: a repaint must never be
        // able to leave the toolbar above the status bar.
        adaptInsets();
        root.setBackgroundColor(palette.background);
        topBar.setBackgroundColor(palette.surface);
        bottomBar.setBackgroundColor(palette.surface);
        list.setBackgroundColor(palette.background);
        headerTitle.setTextColor(palette.text);
        headerMeta.setTextColor(palette.muted);
        bottomMeta.setTextColor(palette.muted);
        if (backButton != null) {
            backButton.setTextColor(palette.text);
            backButton.setBackground(Ui.ripple(this, palette.surface, palette.divider, 12,
                    palette.ripple));
            backButton.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    Ui.tinted(this, R.drawable.ic_arrow_back, palette.text), null, null, null);
        }
        adapter.setPalette(palette);
        if (panel != PANEL_NONE) renderPanel();
        if (content == null) {
            showError(chapterUrl, new ParseException("Isi chapter tidak dapat diekstrak."));
        }
    }

    private void updateNavigationLabels() {
        if (catalog == null) {
            bottomMeta.setText("Perpustakaan tidak tersedia");
            return;
        }
        int index = catalog.indexOf(chapterUrl);
        bottomMeta.setText((index + 1) + "/" + catalog.size());

        View previousButton = bottomInner().findViewWithTag("previous");
        View nextButton = bottomInner().findViewWithTag("next");
        if (previousButton == null || nextButton == null) return;

        Chapter previous = catalog.chapterAt(index - 1);
        Chapter next = catalog.chapterAt(index + 1);
        previousButton.setEnabled(previous != null);
        previousButton.setAlpha(previous == null ? 0.4f : 1f);
        nextButton.setEnabled(next != null);
        nextButton.setAlpha(next == null ? 0.4f : 1f);
        if (nextButton instanceof Button) {
            ((Button) nextButton).setText(next == null ? "Selesai" : "Berikutnya ›");
        }
    }

    private void navigate(int direction) {
        if (catalog == null) return;
        int index = catalog.indexOf(chapterUrl);
        Chapter target = catalog.chapterAt(index + direction);
        if (target == null) {
            Toast.makeText(this, direction > 0
                    ? "Ini chapter terakhir." : "Ini chapter pertama.", Toast.LENGTH_SHORT).show();
            return;
        }
        saveProgress();
        chapterUrl = target.url;
        content = null;
        progressSaved = false;
        getIntent().putExtra(EXTRA_URL, chapterUrl);

        // Previous/Next must work for downloaded chapters too, so this uses the
        // same offline-first order as opening a chapter.
        ChapterContent cached = ContentCache.get(chapterUrl);
        if (cached != null) {
            show(cached);
            return;
        }
        ChapterContent stored = localChapter(chapterUrl);
        if (stored != null) {
            ContentCache.put(chapterUrl, stored);
            show(stored);
            return;
        }
        list.scrollToPosition(0);
        load(chapterUrl);
    }

    /** Stores the exact visible block and offset, plus a coarse percentage. */
    private void saveProgress() {
        // While a bookmark is being restored the list is still laying out, so the
        // reported position is not the reader's real position yet.
        if (content == null || restoring) return;
        RecyclerView.LayoutManager manager = list.getLayoutManager();
        if (!(manager instanceof LinearLayoutManager)) return;

        LinearLayoutManager linear = (LinearLayoutManager) manager;
        int total = adapter.blockCount();
        if (total <= 0) return;

        int first = linear.findFirstVisibleItemPosition();
        int last = linear.findLastVisibleItemPosition();
        if (first < 0) return;

        prefs.setScroll(chapterUrl, first, Math.max(0, currentTopOffset()));
        prefs.setProgress(chapterUrl, ScrollMath.percent(last, total));
        progressSaved = true;
    }

    private void openSource() {
        if (content != null && content.sourceUrl() != null && !content.sourceUrl().isEmpty()) {
            openUrl(content.sourceUrl());
        } else {
            openUrl(chapterUrl);
        }
    }

    /** Opens an illustration full screen, where it can be pinched to zoom. */
    private void openIllustration(Block block) {
        if (block == null) return;
        // Prefer the downloaded file so the full-screen viewer works offline too.
        String source = block.imageSource();
        if (source == null || source.isEmpty()) return;
        Intent intent = new Intent(this, ImageViewerActivity.class);
        intent.putExtra(ImageViewerActivity.EXTRA_URL, source);
        String caption = content != null && content.title() != null
                ? content.title() : "Ilustrasi";
        intent.putExtra(ImageViewerActivity.EXTRA_CAPTION, caption);
        try {
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(this, "Penampil gambar tidak tersedia", Toast.LENGTH_SHORT).show();
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception error) {
            Toast.makeText(this, "Tidak ada browser tersedia", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        saveProgress();
        super.onPause();
    }

    @Override
    protected void onStop() {
        saveProgress();
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        if (panel != PANEL_NONE) {
            panel = PANEL_NONE;
            renderPanel();
            return;
        }
        super.onBackPressed();
    }
}
