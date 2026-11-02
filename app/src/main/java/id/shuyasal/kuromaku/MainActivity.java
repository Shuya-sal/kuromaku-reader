package id.shuyasal.kuromaku;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;
import android.content.Intent;

public class MainActivity extends Activity {
    private static final String HOST = "kaoritranslation.blogspot.com";
    private static final String INDEX = "https://kaoritranslation.blogspot.com/search/label/Monogatari%20no%20Kuromaku?m=1";
    private WebView web;
    private SharedPreferences prefs;
    private boolean dark;
    private int fontSize;
    private TextView title;
    private Button modeButton;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("reader", MODE_PRIVATE);
        dark = prefs.getBoolean("dark", false);
        fontSize = prefs.getInt("font", 18);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(22, 25, 32));
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        title = new TextView(this);
        title.setText("Kuromaku Reader");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setPadding(12, 15, 6, 12);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button home = button("Daftar", () -> web.loadUrl(INDEX));
        Button back = button("‹", () -> { if (web.canGoBack()) web.goBack(); });
        modeButton = button(dark ? "☀" : "☾", () -> { dark = !dark; prefs.edit().putBoolean("dark", dark).apply(); modeButton.setText(dark ? "☀" : "☾"); applyReadingStyle(); });
        Button smaller = button("A−", () -> { fontSize = Math.max(12, fontSize - 2); saveFont(); });
        Button larger = button("A+", () -> { fontSize = Math.min(32, fontSize + 2); saveFont(); });
        toolbar.addView(home); toolbar.addView(back); toolbar.addView(modeButton); toolbar.addView(smaller); toolbar.addView(larger);
        root.addView(toolbar);
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.getSettings().setBuiltInZoomControls(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("https".equals(u.getScheme()) && HOST.equalsIgnoreCase(u.getHost())) return false;
                if ("https".equals(u.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                }
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (url.startsWith("https://" + HOST + "/")) {
                    prefs.edit().putString("last", url).apply();
                    applyReadingStyle();
                }
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        String last = prefs.getString("last", INDEX);
        web.loadUrl(last.startsWith("https://" + HOST + "/") ? last : INDEX);
    }

    private Button button(String label, Runnable click) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(3, 0, 3, 0);
        b.setOnClickListener(v -> click.run());
        return b;
    }

    private void saveFont() { prefs.edit().putInt("font", fontSize).apply(); applyReadingStyle(); }

    private void applyReadingStyle() {
        if (web == null) return;
        String background = dark ? "#17191e" : "#fffdf8";
        String foreground = dark ? "#e6e4e0" : "#292929";
        // This is a reading-only presentation layer. It does not remove source content or fetch a copy.
        String js = "(function(){" +
            "var id='kuromaku-reader-style';var old=document.getElementById(id);if(old)old.remove();" +
            "var s=document.createElement('style');s.id=id;s.textContent=" +
            "'html,body{background:" + background + "!important;color:" + foreground + "!important;}" +
            "body{font-size:" + fontSize + "px!important;line-height:1.85!important;}" +
            ".post-body,.entry-content,.post-content{font-size:" + fontSize + "px!important;line-height:1.85!important;color:" + foreground + "!important;max-width:850px!important;margin:auto!important;padding:12px!important;}" +
            ".post-body *, .entry-content *{color:inherit!important;line-height:inherit!important;}" +
            ".post-body img,.entry-content img{max-width:100%!important;height:auto!important;}" +
            "header,.header-outer,nav,.sidebar,.sidebar-container,footer,.footer-outer,.adsbygoogle,ins.adsbygoogle,iframe[src*=\\\"doubleclick\\\"],iframe[src*=\\\"googlesyndication\\\"]{display:none!important;}" +
            "a{color:" + (dark ? "#a8c7fa" : "#2457a7") + "!important;}';document.head.appendChild(s);" +
            "})();";
        web.evaluateJavascript(js, null);
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
    @Override protected void onDestroy() { if (web != null) web.destroy(); super.onDestroy(); }
}
