package id.shuyasal.kuromaku;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.Locale;
import id.shuyasal.kuromaku.manga.MangaAdFilter;
import id.shuyasal.kuromaku.shinigami.ShinigamiActivity;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.SectionDrawer;

public class MangaActivity extends AppCompatActivity {
    private static final String LANDING = "https://voratoon.id/";
    private WebView web;
    private ProgressBar progress;
    private TextView address;
    private String lastTrusted = LANDING;
    private LinearLayout root, header;
    private TextView addressBar;
    private boolean fullscreen = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        LinearLayout layout = new LinearLayout(this);
        root = layout;
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.rgb(19, 21, 28));
        LinearLayout header = new LinearLayout(this);
        this.header = header;
        header.setTag("manga_header");
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(8), dp(8), dp(8));
        Button menu = button("☰");
        menu.setContentDescription("Menu Novel dan Manga");
        menu.setTag("section_menu");
        menu.setOnClickListener(v -> showSectionDrawer());
        header.addView(menu);
        TextView heading = new TextView(this);
        heading.setText("Manga"); heading.setTextSize(20); heading.setTextColor(Color.WHITE);
        heading.setPadding(dp(12), 0, 0, 0);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Button home = button("Sumber");
        home.setOnClickListener(v -> web.loadUrl(LANDING));
        header.addView(home);
        Button expand = button("⛶");
        expand.setTag("manga_fullscreen");
        expand.setContentDescription("Aktifkan atau nonaktifkan layar penuh");
        expand.setOnClickListener(v -> setFullscreen(!fullscreen));
        header.addView(expand);
        layout.addView(header);
        address = new TextView(this);
        addressBar = address;
        address.setTag("manga_address");
        address.setTextColor(0xffaaaaaa);
        address.setTextSize(12);
        address.setSingleLine(true);
        address.setEllipsize(TextUtils.TruncateAt.END);
        address.setPadding(dp(16), dp(5), dp(16), dp(8));
        address.setText("Sumber: voratoon.id");
        address.setOnClickListener(v -> showUrlDetailsDialog(lastTrusted));
        layout.addView(address);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        layout.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setBuiltInZoomControls(true);
        web.getSettings().setDisplayZoomControls(false);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri url = request.getUrl();
                if (MangaAdFilter.isGoogleAuth(url)) {
                    launchGoogleAuth(url);
                    return true;
                }
                if (trusted(url)) {
                    lastTrusted = url.toString();
                    updateAddressDisplay(lastTrusted);
                    return false;
                }
                Toast.makeText(MangaActivity.this, "Tautan luar diblokir. Buka melalui browser jika diperlukan.", Toast.LENGTH_LONG).show();
                return true;
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    Uri uri = request.getUrl();
                    if (MangaAdFilter.isBlockedAd(uri)) {
                        return new WebResourceResponse("text/plain", "UTF-8", 204, "No Content", Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
                updateAddressDisplay(url);
            }

            @Override public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                updateAddressDisplay(url);
            }
        });
        layout.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);
        ViewCompat.setOnApplyWindowInsetsListener(layout, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, fullscreen ? 0 : bars.top, bars.right, fullscreen ? 0 : bars.bottom);
            return insets;
        });
        if (state != null && state.getBoolean("manga_fullscreen", false)) setFullscreen(true);
        web.loadUrl(state == null ? LANDING : state.getString("manga_url", LANDING));
    }

    private void updateAddressDisplay(String rawUrl) {
        if (rawUrl != null && !rawUrl.isEmpty()) {
            lastTrusted = rawUrl;
        }
        if (address != null) {
            address.setText(MangaAdFilter.formatDisplayHost(rawUrl));
        }
    }

    private void launchGoogleAuth(Uri uri) {
        try {
            CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build();
            customTabsIntent.launchUrl(this, uri);
            Toast.makeText(this, "Membuka login Google di browser eksternal...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                Toast.makeText(this, "Membuka login Google di browser...", Toast.LENGTH_SHORT).show();
            } catch (Exception fallbackError) {
                Toast.makeText(this, "Tidak ada browser yang dapat membuka tautan autentikasi.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showUrlDetailsDialog(String currentUrl) {
        final String targetUrl = (currentUrl == null || currentUrl.isEmpty()) ? LANDING : currentUrl;
        int theme = new Prefs(this, null).resolvedTheme();
        Palette palette = Palette.of(theme);

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(palette.surface);
        content.setPadding(dp(20), dp(18), dp(20), dp(18));

        TextView title = new TextView(this);
        title.setText("Alamat Halaman");
        title.setTextSize(16);
        title.setTextColor(palette.text);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(10));
        content.addView(title);

        TextView hostLabel = new TextView(this);
        hostLabel.setText(MangaAdFilter.formatDisplayHost(targetUrl));
        hostLabel.setTextSize(13);
        hostLabel.setTextColor(palette.accent);
        hostLabel.setPadding(0, 0, 0, dp(8));
        content.addView(hostLabel);

        ScrollView scroll = new ScrollView(this);
        TextView urlBox = new TextView(this);
        urlBox.setText(targetUrl);
        urlBox.setTextSize(12);
        urlBox.setTextColor(palette.muted);
        urlBox.setTextIsSelectable(true);
        urlBox.setBackgroundColor(palette.background);
        urlBox.setPadding(dp(12), dp(10), dp(12), dp(10));
        scroll.addView(urlBox);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, dp(90)));

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.END);
        buttonRow.setPadding(0, dp(14), 0, 0);

        Button copyBtn = new Button(this);
        copyBtn.setText("Salin URL");
        copyBtn.setAllCaps(false);
        copyBtn.setTextColor(Color.WHITE);
        copyBtn.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        copyBtn.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("URL Manga", targetUrl));
                Toast.makeText(MangaActivity.this, "URL disalin ke clipboard", Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });
        buttonRow.addView(copyBtn);

        Button openBtn = new Button(this);
        openBtn.setText("Buka di Browser");
        openBtn.setAllCaps(false);
        openBtn.setTextColor(palette.text);
        openBtn.setBackgroundTintList(ColorStateList.valueOf(palette.divider));
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(-2, -2);
        openParams.setMargins(dp(8), 0, 0, 0);
        openBtn.setLayoutParams(openParams);
        openBtn.setOnClickListener(v -> {
            try {
                CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
                customTabsIntent.launchUrl(MangaActivity.this, Uri.parse(targetUrl));
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)));
                } catch (Exception ignored) {
                }
            }
            dialog.dismiss();
        });
        buttonRow.addView(openBtn);

        Button closeBtn = new Button(this);
        closeBtn.setText("Tutup");
        closeBtn.setAllCaps(false);
        closeBtn.setTextColor(palette.text);
        closeBtn.setBackgroundTintList(ColorStateList.valueOf(palette.divider));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(-2, -2);
        closeParams.setMargins(dp(8), 0, 0, 0);
        closeBtn.setLayoutParams(closeParams);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        buttonRow.addView(closeBtn);

        content.addView(buttonRow);

        dialog.setContentView(content);
        Window window = dialog.getWindow();
        if (window != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90f);
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.55f);
        }
        dialog.show();
    }

    private void setFullscreen(boolean enabled) {
        fullscreen = enabled;
        header.setVisibility(enabled ? View.GONE : View.VISIBLE);
        addressBar.setVisibility(enabled ? View.GONE : View.VISIBLE);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), root);
        if (enabled) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars());
        }
        ViewCompat.requestApplyInsets(root);
    }

    static boolean trusted(Uri uri) {
        if (uri == null) return false;
        if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("voratoon.id") || host.endsWith(".voratoon.id")
                || host.equals("voratoon.com") || host.endsWith(".voratoon.com")
                || host.equals("voratoon.co") || host.endsWith(".voratoon.co");
    }

    private void showSectionDrawer() {
        int theme = new Prefs(this, null).resolvedTheme();
        Palette palette = Palette.of(theme);
        SectionDrawer.show(this, SectionDrawer.Section.MANGA_VORATON, palette, new SectionDrawer.Listener() {
            @Override
            public void onHome() {
                startActivity(new Intent(MangaActivity.this, SourceHubActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                finish();
            }

            @Override
            public void onNovel() {
                startActivity(new Intent(MangaActivity.this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                finish();
            }

            @Override
            public void onMangaVoratoon() {
            }

            @Override
            public void onShinigami() {
                startActivity(new Intent(MangaActivity.this, ShinigamiActivity.class));
                finish();
            }

            @Override
            public void onAnimeOtakudesu() {
                startActivity(new Intent(MangaActivity.this,
                        id.shuyasal.kuromaku.anime.OtakudesuActivity.class));
                finish();
            }

            @Override
            public void onBackup() {
                openNovelDataAction("backup");
            }

            @Override
            public void onRestore() {
                openNovelDataAction("restore");
            }
        });
    }

    private void openNovelDataAction(String action) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("kuromaku_data_action", action);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(ColorStateList.valueOf(0xff303440));
        return b;
    }

    private int dp(int value) {
        return (int) (getResources().getDisplayMetrics().density * value + .5f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        CookieManager.getInstance().flush();
    }

    @Override
    public void onBackPressed() {
        if (fullscreen) setFullscreen(false);
        else if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("manga_url", lastTrusted);
        state.putBoolean("manga_fullscreen", fullscreen);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
