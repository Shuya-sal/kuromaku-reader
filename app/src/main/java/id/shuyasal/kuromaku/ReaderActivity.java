package id.shuyasal.kuromaku;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.net.Http;
import id.shuyasal.kuromaku.parser.ChapterContent;
import id.shuyasal.kuromaku.parser.ChapterParser;
import id.shuyasal.kuromaku.parser.ContentCache;
import id.shuyasal.kuromaku.parser.ParseException;
import id.shuyasal.kuromaku.ui.BlockAdapter;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.Ui;

/**
 * Reader Mode: shows only the extracted article. If extraction fails the screen
 * explains the problem and offers the original link instead of falling back to
 * a full website view.
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
    private TextView headerTitle;
    private TextView headerMeta;
    private TextView bottomMeta;

    private String chapterUrl;
    private ChapterContent content;
    private int panel = PANEL_NONE;
    private boolean restoring;

    private final Handler main = new Handler(Looper.getMainLooper());

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

        FrameHost host = new FrameHost();
        host.frame = new android.widget.FrameLayout(this);
        host.frame.setBackgroundColor(palette.background);

        spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        android.widget.FrameLayout.LayoutParams spinnerParams =
                new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        host.frame.addView(spinner, spinnerParams);

        list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setBackgroundColor(palette.background);
        adapter = new BlockAdapter(this, prefs, palette);
        adapter.setOnOpenSource(this::openSource);
        list.setAdapter(adapter);
        list.setVisibility(View.GONE);
        host.frame.addView(list, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        errorBox = new LinearLayout(this);
        errorBox.setOrientation(LinearLayout.VERTICAL);
        errorBox.setPadding(Ui.dp(this, 24), Ui.dp(this, 32), Ui.dp(this, 24), Ui.dp(this, 24));
        errorBox.setVisibility(View.GONE);
        ScrollView errorScroll = new ScrollView(this);
        errorScroll.addView(errorBox);
        host.frame.addView(errorScroll, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(host.frame, new LinearLayout.LayoutParams(
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

        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                saveProgress();
            }
        });
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
        int pad = Ui.dp(this, 10);
        bottomBar.setPadding(pad, Ui.dp(this, 8), pad, Ui.dp(this, 8));

        Button previous = Ui.chip(this, "‹ Sebelumnya", palette);
        previous.setTextSize(12);
        previous.setOnClickListener(v -> navigate(-1));
        bottomBar.addView(previous, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        bottomMeta = Ui.label(this, "", 11, palette.muted);
        bottomMeta.setGravity(Gravity.CENTER);
        bottomBar.addView(bottomMeta, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button next = Ui.chip(this, "Berikutnya ›", palette);
        next.setTextSize(12);
        next.setOnClickListener(v -> navigate(1));
        bottomBar.addView(next, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return bottomBar;
    }

    private void applySystemBars() {
        getWindow().setStatusBarColor(palette.surface);
        getWindow().setNavigationBarColor(palette.surface);
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(palette.darkIcons ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    private void load(final String url) {
        spinner.setVisibility(View.VISIBLE);
        list.setVisibility(View.GONE);
        errorBox.setVisibility(View.GONE);
        headerTitle.setText("Memuat…");
        headerMeta.setText(describeChapter(url));

        new Thread(() -> {
            try {
                String html = Http.get(url);
                ChapterContent parsed = ChapterParser.parse(html, url);
                ContentCache.put(url, parsed);
                main.post(() -> show(parsed));
            } catch (Exception error) {
                main.post(() -> showError(url, error));
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
        spinner.setVisibility(View.GONE);
        errorBox.setVisibility(View.GONE);
        list.setVisibility(View.VISIBLE);

        adapter.setContent(parsed);
        headerTitle.setText(parsed.title());
        headerMeta.setText(describeChapter(chapterUrl)
                + " · " + parsed.textCount() + " paragraf · " + parsed.imageCount() + " ilustrasi");

        prefs.setLastUrl(chapterUrl);
        updateNavigationLabels();

        int saved = prefs.progress(chapterUrl);
        restoring = saved > 0;
        if (saved > 0 && adapter.blockCount() > 0) {
            int target = Math.round(adapter.blockCount() * (saved / 100f));
            if (target > 0) list.scrollToPosition(Math.min(target, adapter.blockCount() - 1));
            Toast.makeText(this, "Melanjutkan dari " + saved + "%", Toast.LENGTH_SHORT).show();
        }
    }

    private void showError(final String url, Exception error) {
        content = null;
        spinner.setVisibility(View.GONE);
        list.setVisibility(View.GONE);
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

    private void applyTypography() {
        adapter.notifyDataSetChanged();
        renderPanel();
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
        if (content == null) showError(chapterUrl,
                new ParseException("Isi chapter tidak dapat diekstrak."));
    }

    private void updateNavigationLabels() {
        if (catalog == null) {
            bottomMeta.setText("Perpustakaan tidak tersedia");
            return;
        }
        int index = catalog.indexOf(chapterUrl);
        Chapter current = catalog.chapterAt(index);
        int position = index + 1;
        bottomMeta.setText(position + "/" + catalog.size());
        if (current == null) return;

        Chapter previous = catalog.chapterAt(index - 1);
        Chapter next = catalog.chapterAt(index + 1);

        Button previousButton = (Button) bottomBar.getChildAt(0);
        Button nextButton = (Button) bottomBar.getChildAt(2);
        previousButton.setEnabled(previous != null);
        previousButton.setAlpha(previous == null ? 0.4f : 1f);
        nextButton.setEnabled(next != null);
        nextButton.setAlpha(next == null ? 0.4f : 1f);
        nextButton.setText(next == null ? "Selesai" : "Berikutnya ›");
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
        getIntent().putExtra(EXTRA_URL, chapterUrl);
        ChapterContent cached = ContentCache.get(chapterUrl);
        if (cached != null) {
            show(cached);
        } else {
            load(chapterUrl);
        }
        list.scrollToPosition(0);
    }

    private void saveProgress() {
        if (content == null) return;
        RecyclerView.LayoutManager manager = list.getLayoutManager();
        if (!(manager instanceof LinearLayoutManager)) return;
        int total = adapter.blockCount();
        if (total <= 0) return;
        int first = ((LinearLayoutManager) manager).findFirstVisibleItemPosition();
        int visible = ((LinearLayoutManager) manager).findLastVisibleItemPosition();
        int percent = Math.min(100, Math.round((visible + 1) * 100f / total));
        if (first < 0) return;
        if (!restoring || percent > prefs.progress(chapterUrl)) {
            prefs.setProgress(chapterUrl, percent);
        }
        restoring = false;
    }

    private void openSource() {
        if (content != null && content.sourceUrl() != null && !content.sourceUrl().isEmpty()) {
            openUrl(content.sourceUrl());
        } else {
            openUrl(chapterUrl);
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
    public void onBackPressed() {
        if (panel != PANEL_NONE) {
            panel = PANEL_NONE;
            renderPanel();
            return;
        }
        super.onBackPressed();
    }

    private static final class FrameHost {
        android.widget.FrameLayout frame;
    }
}
