package id.shuyasal.kuromaku;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.SectionDrawer;

/** Manga entry point. The landing page discovers the current Voratoon domain;
 * never pin a numbered subdomain such as 12.voratoon.com. */
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
        address.setPadding(dp(16), dp(5), dp(16), dp(8));
        address.setText("Sumber: voratoon.id");
        layout.addView(address);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        layout.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true); // site navigation needs JS
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setBuiltInZoomControls(true);
        web.getSettings().setDisplayZoomControls(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri url = request.getUrl();
                if (trusted(url)) { lastTrusted = url.toString(); return false; }
                // Never silently follow ad, VPN, or untrusted domain redirects.
                Toast.makeText(MangaActivity.this, "Tautan luar diblokir. Buka melalui browser jika diperlukan.", Toast.LENGTH_LONG).show();
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
                address.setText(url);
            }
            @Override public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                address.setText(url);
            }
        });
        layout.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);
        // Edge-to-edge safe area: never place toolbar behind status bar or
        // bottom content behind gesture navigation in normal mode.
        ViewCompat.setOnApplyWindowInsetsListener(layout, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, fullscreen ? 0 : bars.top, bars.right, fullscreen ? 0 : bars.bottom);
            return insets;
        });
        if (state != null && state.getBoolean("manga_fullscreen", false)) setFullscreen(true);
        web.loadUrl(state == null ? LANDING : state.getString("manga_url", LANDING));
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

    /**
     * True only for the pages this screen is allowed to show. Package-private and
     * static so a test can point at it directly: this is the one rule that keeps
     * an advert or a hop through a redirect from taking over the reader.
     */
    static boolean trusted(Uri uri) {
        if (uri == null) return false;
        if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("voratoon.id") || host.endsWith(".voratoon.id")
                || host.equals("voratoon.com") || host.endsWith(".voratoon.com");
    }
    private void showSectionDrawer() {
        int theme = new Prefs(this, null).theme();
        Palette palette = Palette.of(theme);
        SectionDrawer.show(this, SectionDrawer.Section.MANGA, palette, new SectionDrawer.Listener() {
            @Override
            public void onNovel() {
                startActivity(new Intent(MangaActivity.this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                finish();
            }

            @Override
            public void onManga() {
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
        Button b = new Button(this); b.setText(text); b.setAllCaps(false);
        b.setTextColor(Color.WHITE); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff303440));
        return b;
    }
    private int dp(int value) { return (int) (getResources().getDisplayMetrics().density * value + .5f); }
    @Override public void onBackPressed() {
        if (fullscreen) setFullscreen(false);
        else if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("manga_url", lastTrusted);
        state.putBoolean("manga_fullscreen", fullscreen);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
