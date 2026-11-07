package id.shuyasal.kuromaku.anime;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.ArrayList;
import java.util.List;

import id.shuyasal.kuromaku.R;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.ThemeManager;
import id.shuyasal.kuromaku.ui.Ui;

/**
 * Pemutar episode Anime Otakudesu.
 *
 * <p>Video tidak diekstrak dari sumber. Halaman episode Otakudesu memuat
 * pemutar milik penyedia (iframe pihak ketiga), dan tidak ada berkas media
 * resmi yang boleh diputar aplikasi lain. Karena itu halaman episode resmi
 * dimuat utuh di sini — pemutaran tetap terjadi di pemutar penyedia, dengan
 * iklan dan pembatasannya — dan tombol "Situs" membuka halaman yang sama di
 * browser bila pengguna menginginkannya.
 *
 * <p>Konsekuensinya, kontrol seperti seek, pilihan kualitas, subtitle, dan
 * kecepatan adalah milik pemutar penyedia, bukan kontrol native. Aplikasi tidak
 * menampilkan tombol palsu untuk fitur yang tidak dimilikinya.
 */
public class OtakudesuPlayerActivity extends AppCompatActivity {

    public static final String EXTRA_ANIME_ID = "anime_id";
    public static final String EXTRA_ANIME_TITLE = "anime_title";
    public static final String EXTRA_ANIME_POSTER = "anime_poster";
    public static final String EXTRA_EPISODE_URL = "episode_url";
    public static final String EXTRA_EPISODE_LABEL = "episode_label";
    public static final String EXTRA_EPISODE_NUMBER = "episode_number";
    public static final String EXTRA_EPISODE_TOTAL = "episode_total";
    public static final String EXTRA_EPISODES = "episodes";

    private Palette palette;
    private OtakudesuStore store;
    private WebView web;
    private ProgressBar progress;
    private LinearLayout toolbar;
    private LinearLayout controls;
    private TextView titleView;
    private Button previous;
    private Button next;

    private String animeId = "";
    private String animeTitle = "";
    private String animePoster = "";
    private String episodeUrl = "";
    private String episodeLabel = "";
    private int episodeNumber;
    private int episodeTotal;
    private final List<OtakudesuAnime.Episode> episodes = new ArrayList<>();

    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeManager.applyTheme(this);
        palette = Palette.of(new Prefs(this).resolvedTheme());
        store = new OtakudesuStore(this);

        Intent intent = getIntent();
        animeId = safe(intent.getStringExtra(EXTRA_ANIME_ID));
        animeTitle = safe(intent.getStringExtra(EXTRA_ANIME_TITLE));
        animePoster = safe(intent.getStringExtra(EXTRA_ANIME_POSTER));
        episodeUrl = safe(intent.getStringExtra(EXTRA_EPISODE_URL));
        episodeLabel = safe(intent.getStringExtra(EXTRA_EPISODE_LABEL));
        episodeNumber = intent.getIntExtra(EXTRA_EPISODE_NUMBER, 0);
        episodeTotal = intent.getIntExtra(EXTRA_EPISODE_TOTAL, 0);
        episodes.addAll(OtakudesuEpisodes.fromJson(intent.getStringExtra(EXTRA_EPISODES)));

        if (episodeUrl.isEmpty()) {
            // Tanpa alamat episode tidak ada yang bisa diputar; jangan tampilkan
            // layar hitam seolah player siap.
            finish();
            return;
        }

        buildUi();
        openEpisode(episodeUrl, episodeLabel);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    // ------------------------------------------------------------------- UI

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);

        toolbar = buildToolbar();
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout stage = new FrameLayout(this);
        web = new WebView(this);
        web.setTag("otakudesu_player_web");
        web.setBackgroundColor(palette.background);
        configureWeb();
        stage.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setTag("otakudesu_player_progress");
        stage.addView(progress, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 3), Gravity.TOP));

        root.addView(stage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        controls = buildControls();
        root.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    private LinearLayout buildToolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        bar.setBackgroundColor(palette.surface);

        android.widget.ImageButton back = Ui.iconButton(this,
                R.drawable.ic_arrow_back, "Kembali", palette);
        back.setTag("otakudesu_player_back");
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));

        titleView = Ui.label(this, "", 15, palette.text);
        titleView.setTag("otakudesu_player_title");
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.leftMargin = Ui.dp(this, 8);
        bar.addView(titleView, params);

        Button site = Ui.chip(this, "Situs", palette);
        site.setTag("otakudesu_player_site");
        site.setContentDescription("Tonton di Website");
        site.setOnClickListener(v -> {
            if (!episodeUrl.isEmpty()) openExternally(episodeUrl);
        });
        bar.addView(site);

        Button full = Ui.chip(this, "Layar penuh", palette);
        full.setTag("otakudesu_player_fullscreen");
        full.setOnClickListener(v -> toggleImmersive());
        LinearLayout.LayoutParams fullParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fullParams.leftMargin = Ui.dp(this, 6);
        bar.addView(full, fullParams);
        return bar;
    }

    private LinearLayout buildControls() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 10));
        row.setBackgroundColor(palette.surface);

        previous = Ui.chip(this, "‹ Sebelumnya", palette);
        previous.setTag("otakudesu_player_previous");
        previous.setOnClickListener(v -> step(-1));
        row.addView(previous, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        next = Ui.chip(this, "Berikutnya ›", palette);
        next.setTag("otakudesu_player_next");
        next.setOnClickListener(v -> step(1));
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        nextParams.leftMargin = Ui.dp(this, 8);
        row.addView(next, nextParams);
        return row;
    }

    private void configureWeb() {
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        // Halaman sumber memuat iframe pemutar dari domain lain; subframe tidak
        // perlu diblokir, tetapi jendela popup baru tetap dibuka di browser.
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri == null ? "" : uri.getScheme();
                if (scheme == null) return true;
                if (scheme.equals("http") || scheme.equals("https")) {
                    String host = uri.getHost() == null ? "" : uri.getHost();
                    // Navigasi keluar dari halaman episode dikirim ke browser agar
                    // pemutar tidak tertutup iklan atau situs lain.
                    if (!host.contains("otakudesu")) {
                        openExternally(uri.toString());
                        return true;
                    }
                    return false;
                }
                // intent://, market://, dan skema lain tidak dijalankan di sini.
                return true;
            }

            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
            }

            @Override public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreenView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                fullscreenView = view;
                fullscreenCallback = callback;
                FrameLayout decor = (FrameLayout) getWindow().getDecorView();
                decor.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                toolbar.setVisibility(View.GONE);
                controls.setVisibility(View.GONE);
                web.setVisibility(View.GONE);
                hideSystemBars();
            }

            @Override
            public void onHideCustomView() {
                if (fullscreenView == null) return;
                FrameLayout decor = (FrameLayout) getWindow().getDecorView();
                decor.removeView(fullscreenView);
                fullscreenView = null;
                if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
                fullscreenCallback = null;
                toolbar.setVisibility(View.VISIBLE);
                controls.setVisibility(View.VISIBLE);
                web.setVisibility(View.VISIBLE);
                showSystemBars();
            }
        });
    }

    private void toggleImmersive() {
        if (toolbar.getVisibility() == View.VISIBLE) {
            toolbar.setVisibility(View.GONE);
            controls.setVisibility(View.GONE);
            hideSystemBars();
        } else {
            toolbar.setVisibility(View.VISIBLE);
            controls.setVisibility(View.VISIBLE);
            showSystemBars();
        }
    }

    private void hideSystemBars() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }

    private void showSystemBars() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.show(WindowInsetsCompat.Type.systemBars());
    }

    // --------------------------------------------------------------- episode

    private void openEpisode(String url, String label) {
        episodeUrl = safe(url);
        episodeLabel = safe(label);
        titleView.setText(episodeLabel.isEmpty() ? animeTitle : episodeLabel);
        web.loadUrl(episodeUrl);
        updateNeighbors();
        record();
    }

    private void step(int delta) {
        OtakudesuAnime.Episode target = OtakudesuEpisodes.neighbor(episodes, episodeUrl, delta);
        if (target == null) return;
        openEpisode(target.url, target.label);
    }

    private void updateNeighbors() {
        if (previous == null || next == null) return;
        boolean hasPrevious = OtakudesuEpisodes.neighbor(episodes, episodeUrl, -1) != null;
        boolean hasNext = OtakudesuEpisodes.neighbor(episodes, episodeUrl, 1) != null;
        previous.setEnabled(hasPrevious);
        previous.setAlpha(hasPrevious ? 1f : 0.45f);
        next.setEnabled(hasNext);
        next.setAlpha(hasNext ? 1f : 0.45f);
    }

    private void record() {
        if (animeId.isEmpty() || episodeUrl.isEmpty()) return;
        int number = episodeNumber;
        if (number <= 0) {
            OtakudesuAnime.Episode current = episodes.isEmpty() ? null
                    : episodes.get(Math.max(0, OtakudesuEpisodes.indexOf(episodes, episodeUrl)));
            if (current != null && current.number > 0) number = (int) Math.round(current.number);
        }
        store.recordWatched(animeId, animeTitle, animePoster, episodeLabel, episodeUrl,
                number, episodeTotal);
    }

    private void openExternally(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            new CustomTabsIntent.Builder().setShowTitle(true).build()
                    .launchUrl(this, Uri.parse(url));
        } catch (Exception error) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
                // Tidak ada browser untuk menangani tautan.
            }
        }
    }

    @Override public void onBackPressed() {
        if (fullscreenView != null) {
            if (web.getWebChromeClient() != null) {
                // Keluar dari fullscreen HTML5 sebelum menutup halaman.
                ((WebChromeClient) web.getWebChromeClient()).onHideCustomView();
            }
            return;
        }
        if (web.canGoBack()) {
            web.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override protected void onDestroy() {
        // Lepaskan resource pemutar: tanpa ini WebView (dan audio yang masih
        // berjalan) tetap hidup setelah Activity ditutup.
        if (fullscreenView != null) {
            try {
                FrameLayout decor = (FrameLayout) getWindow().getDecorView();
                decor.removeView(fullscreenView);
            } catch (Exception ignored) {
                // Dekorasi sudah dilepas.
            }
            fullscreenView = null;
        }
        if (web != null) {
            web.loadUrl("about:blank");
            web.stopLoading();
            web.removeAllViews();
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}