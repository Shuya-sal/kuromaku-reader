package id.shuyasal.kuromaku;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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

/** Manga entry point. The landing page discovers the current Voratoon domain;
 * never pin a numbered subdomain such as 12.voratoon.com. */
public class MangaActivity extends AppCompatActivity {
    private static final String LANDING = "https://voratoon.id/";
    private WebView web;
    private ProgressBar progress;
    private TextView address;
    private String lastTrusted = LANDING;

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.rgb(19, 21, 28));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(8), dp(8), dp(8));
        Button menu = button("☰");
        menu.setContentDescription("Pilih Novel atau Manga");
        menu.setOnClickListener(v -> showMenu(menu));
        header.addView(menu);
        TextView heading = new TextView(this);
        heading.setText("Manga"); heading.setTextSize(20); heading.setTextColor(Color.WHITE);
        heading.setPadding(dp(12), 0, 0, 0);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Button home = button("Sumber");
        home.setOnClickListener(v -> web.loadUrl(LANDING));
        header.addView(home);
        layout.addView(header);
        address = new TextView(this);
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
        web.loadUrl(state == null ? LANDING : state.getString("manga_url", LANDING));
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
    private void showMenu(View anchor) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, anchor);
        popup.getMenu().add("Novel").setOnMenuItemClickListener(item -> {
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish(); return true;
        });
        popup.getMenu().add("Manga ✓");
        popup.show();
    }
    private Button button(String text) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false);
        b.setTextColor(Color.WHITE); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff303440));
        return b;
    }
    private int dp(int value) { return (int) (getResources().getDisplayMetrics().density * value + .5f); }
    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("manga_url", lastTrusted);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
