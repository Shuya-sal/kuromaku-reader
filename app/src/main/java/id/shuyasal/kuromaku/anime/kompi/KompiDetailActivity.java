package id.shuyasal.kuromaku.anime.kompi;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.ThemeManager;
import id.shuyasal.kuromaku.ui.TvFocus;
import id.shuyasal.kuromaku.ui.TvMode;
import id.shuyasal.kuromaku.ui.Ui;

/**
 * Detail anime dari Animekompi: poster, keterangan, sinopsis, dan daftar
 * episode. Menekan satu episode membuka pemutar aplikasi, bukan peramban.
 */
public class KompiDetailActivity extends id.shuyasal.kuromaku.ui.TvActivity {

    public static final String EXTRA_SLUG = "slug";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_COVER = "cover";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private Palette palette;
    private KompiSource source;
    private String slug;
    private String judul;
    private String cover;
    private LinearLayout body;
    private ProgressBar spinner;
    private TextView errorText;
    private Button retry;

    protected KompiSource createSource() {
        return new KompiSource();
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeManager.applyTheme(this);
        palette = Palette.of(new Prefs(this).resolvedTheme());
        source = createSource();
        Intent intent = getIntent();
        slug = intent.getStringExtra(EXTRA_SLUG);
        judul = intent.getStringExtra(EXTRA_TITLE);
        cover = intent.getStringExtra(EXTRA_COVER);
        if (slug == null || slug.isEmpty()) {
            finish();
            return;
        }
        buildUi();
        load();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), 0);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        Button back = Ui.backButton(this, palette);
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, Ui.TOUCH_DP)));

        TextView title = Ui.label(this, judul == null ? "Detail" : judul, 17, palette.text);
        title.setTypeface(null, Typeface.BOLD);
        title.setMaxLines(2);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMargins(Ui.dp(this, 12), 0, 0, 0);
        header.addView(title, titleParams);
        root.addView(header);

        spinner = new ProgressBar(this);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        spinnerParams.gravity = Gravity.CENTER_HORIZONTAL;
        spinnerParams.topMargin = Ui.dp(this, 24);
        root.addView(spinner, spinnerParams);

        errorText = Ui.label(this, "", 14, palette.muted);
        errorText.setVisibility(View.GONE);
        errorText.setPadding(Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 4), 0);
        root.addView(errorText);

        retry = Ui.chip(this, "Coba lagi", palette);
        retry.setVisibility(View.GONE);
        retry.setOnClickListener(view -> load());
        root.addView(retry);

        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 28));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private void load() {
        spinner.setVisibility(View.VISIBLE);
        errorText.setVisibility(View.GONE);
        retry.setVisibility(View.GONE);
        worker.execute(() -> {
            try {
                KompiSource.Detail detail = source.detail(slug);
                main.post(() -> show(detail));
            } catch (KompiSource.SourceException error) {
                String message = error.getMessage() == null
                        ? "Detail tidak dapat diambil." : error.getMessage();
                main.post(() -> {
                    spinner.setVisibility(View.GONE);
                    errorText.setText(message);
                    errorText.setVisibility(View.VISIBLE);
                    retry.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void show(KompiSource.Detail detail) {
        if (isFinishing()) return;
        spinner.setVisibility(View.GONE);
        body.removeAllViews();

        LinearLayout atas = new LinearLayout(this);
        atas.setOrientation(LinearLayout.HORIZONTAL);

        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(Ui.ripple(this, palette.surface, 0, 12, palette.ripple));
        String gambar = detail.cover.isEmpty() ? cover : detail.cover;
        if (gambar != null && !gambar.isEmpty()) {
            Glide.with(this).load(gambar).centerCrop().into(poster);
        }
        atas.addView(poster, new LinearLayout.LayoutParams(
                Ui.dp(this, 128), Ui.dp(this, 182)));

        LinearLayout kanan = new LinearLayout(this);
        kanan.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams kananParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        kananParams.setMargins(Ui.dp(this, 14), 0, 0, 0);

        TextView judulView = Ui.label(this, detail.title, 18, palette.text);
        judulView.setTypeface(null, Typeface.BOLD);
        kanan.addView(judulView);

        if (!detail.info.isEmpty()) {
            TextView info = Ui.label(this, detail.info, 13, palette.muted);
            info.setPadding(0, Ui.dp(this, 6), 0, 0);
            kanan.addView(info);
        }
        atas.addView(kanan, kananParams);
        body.addView(atas);

        if (!detail.synopsis.isEmpty()) {
            TextView sinopsis = Ui.label(this, detail.synopsis, 13, palette.muted);
            sinopsis.setPadding(0, Ui.dp(this, 14), 0, 0);
            sinopsis.setMaxLines(8);
            sinopsis.setEllipsize(android.text.TextUtils.TruncateAt.END);
            body.addView(sinopsis);
        }

        TextView labelEpisode = Ui.label(this,
                "Episode (" + detail.episodes.size() + ")", 16, palette.text);
        labelEpisode.setTypeface(null, Typeface.BOLD);
        labelEpisode.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 10));
        body.addView(labelEpisode);

        if (detail.episodes.isEmpty()) {
            body.addView(Ui.label(this, "Sumber ini belum memuat episode.", 13, palette.muted));
        }
        for (KompiSource.Episode episode : detail.episodes) {
            body.addView(barisEpisod(detail, episode));
        }

        TvFocus.prepare(body, palette);
        if (TvMode.isTelevision(this)) {
            View first = TvFocus.firstFocusable(body);
            if (first != null) first.requestFocus();
        }
    }

    private View barisEpisod(KompiSource.Detail detail, KompiSource.Episode episode) {
        LinearLayout baris = new LinearLayout(this);
        baris.setOrientation(LinearLayout.HORIZONTAL);
        baris.setGravity(Gravity.CENTER_VERTICAL);
        baris.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        baris.setBackground(Ui.ripple(this, palette.surface, palette.divider, 14, palette.ripple));
        baris.setFocusable(true);
        baris.setClickable(true);

        TextView nomor = Ui.label(this,
                episode.number < 0 ? "EP" : String.valueOf(episode.number), 15, palette.accent);
        nomor.setTypeface(null, Typeface.BOLD);
        nomor.setMinWidth(Ui.dp(this, 44));
        baris.addView(nomor);

        TextView nama = Ui.label(this, episode.name, 14, palette.text);
        nama.setMaxLines(1);
        nama.setEllipsize(android.text.TextUtils.TruncateAt.END);
        baris.addView(nama, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        baris.setOnClickListener(view -> bukaPemutar(detail, episode));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(this, 8);
        baris.setLayoutParams(params);
        TvFocus.focusable(baris, palette);
        return baris;
    }

    private void bukaPemutar(KompiSource.Detail detail, KompiSource.Episode episode) {
        Intent intent = new Intent(this, KompiPlayerActivity.class);
        intent.putExtra(KompiPlayerActivity.EXTRA_SLUG, episode.slug);
        intent.putExtra(KompiPlayerActivity.EXTRA_TITLE, detail.title);
        intent.putExtra(KompiPlayerActivity.EXTRA_EPISODE, episode.name);
        startActivity(intent);
    }
}
