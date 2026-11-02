package id.shuyasal.kuromaku;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Block;
import id.shuyasal.kuromaku.net.Http;
import id.shuyasal.kuromaku.parser.ChapterContent;
import id.shuyasal.kuromaku.parser.ChapterParser;
import id.shuyasal.kuromaku.parser.ContentCache;
import id.shuyasal.kuromaku.parser.ParseException;
import id.shuyasal.kuromaku.ui.BlockAdapter;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.ScrollMath;
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

    private static final int PANEL_NONE = 0;
    private static final int PANEL_SETTINGS = 1;

    private Prefs prefs;
    private Palette palette;
    private Catalog catalog;

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
    private TextView bottomMeta;

    private String chapterUrl;
    private ChapterContent content;
    private int panel = PANEL_NONE;

    private int insetTop;
    private int insetBottom;
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
        catalog = Session.catalog();

        chapterUrl = getIntent().getStringExtra(EXTRA_URL);
        if (chapterUrl == null) chapterUrl = prefs.lastUrl();
        if (chapterUrl == null || chapterUrl.isEmpty()) {
            finish();
            return;
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        buildUi();
        ChapterContent cached = ContentCache.get(chapterUrl);
        if (cached != null) {
            show(cached);
        } else {
            load(chapterUrl);
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
            Insets systemBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            if (systemBars.top != insetTop || systemBars.bottom != insetBottom) {
                insetTop = systemBars.top;
                insetBottom = systemBars.bottom;
                adaptInsets();
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    /** Applies the current insets to bars, list and footer padding. */
    private void adaptInsets() {
        if (topBar != null) {
            topBar.setPadding(0, insetTop, 0, 0);
        }
        if (bottomBar != null) {
            bottomBar.setPadding(0, 0, 0, insetBottom);
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
                    ScrollMath.footerPadding(Ui.dp(this, 28), insetBottom)));
            adapter.notifyDataSetChanged();
        }
    }

    private View buildTopBar() {
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.VERTICAL);
        topBar.setBackgroundColor(palette.surface);
        int pad = Ui.dp(this, 14);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(pad, Ui.dp(this, 8), pad, Ui.dp(this, 4));

        Button back = Ui.chip(this, "‹ Perpustakaan", palette);
        back.setTextSize(12);
        back.setOnClickListener(v -> finish());
        row.addView(back);

        headerTitle = Ui.label(this, "", 13, palette.text);
        headerTitle.setSingleLine(true);
        headerTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        headerTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleParams.setMargins(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        row.addView(headerTitle, titleParams);

        Button settings = Ui.chip(this, "Aa", palette);
        settings.setTextSize(12);
        settings.setOnClickListener(v -> togglePanel());
        row.addView(settings);
        topBar.addView(row);

        headerMeta = Ui.label(this, "", 11, palette.muted);
        headerMeta.setPadding(pad, 0, pad, Ui.dp(this, 8));
        topBar.addView(headerMeta);
        return topBar;
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
                String html = Http.get(url);
                ChapterContent parsed = ChapterParser.parse(html, url);
                ContentCache.put(url, parsed);
                runOnUiThread(() -> show(parsed));
            } catch (Exception error) {
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

        TextView heading = Ui.label(this, "Isi chapter tidak dapat diekstrak", 18, palette.text);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        errorBox.addView(heading);

        String reason = error instanceof ParseException
                ? error.getMessage()
                : "Koneksi ke sumber gagal: " + (error.getMessage() == null
                ? error.getClass().getSimpleName() : error.getMessage());

        TextView explanation = Ui.label(this,
                reason + "\n\nAplikasi tidak akan membuka tampilan website penuh secara diam-diam. "
                        + "Silakan coba lagi, atau buka halaman aslinya di browser.", 14, palette.muted);
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
        root.setBackgroundColor(palette.background);
        topBar.setBackgroundColor(palette.surface);
        bottomBar.setBackgroundColor(palette.surface);
        list.setBackgroundColor(palette.background);
        headerTitle.setTextColor(palette.text);
        headerMeta.setTextColor(palette.muted);
        bottomMeta.setTextColor(palette.muted);
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

        ChapterContent cached = ContentCache.get(chapterUrl);
        if (cached != null) {
            show(cached);
        } else {
            list.scrollToPosition(0);
            load(chapterUrl);
        }
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
        if (block == null || block.imageUrl == null || block.imageUrl.isEmpty()) return;
        Intent intent = new Intent(this, ImageViewerActivity.class);
        intent.putExtra(ImageViewerActivity.EXTRA_URL, block.imageUrl);
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
