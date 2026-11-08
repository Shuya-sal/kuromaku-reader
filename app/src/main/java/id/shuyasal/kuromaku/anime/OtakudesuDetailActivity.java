package id.shuyasal.kuromaku.anime;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import id.shuyasal.kuromaku.R;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.RatioFrameLayout;
import id.shuyasal.kuromaku.ui.ThemeManager;
import id.shuyasal.kuromaku.ui.Ui;

/**
 * Detail anime: poster, sinopsis, genre, status, studio, jumlah episode, dan
 * daftar episode yang bisa diurutkan serta dicari.
 *
 * <p>Video tidak diputar di dalam aplikasi: halaman episode memuat pemutar
 * pihak ketiga yang tidak menyediakan berkas media resmi untuk aplikasi ini.
 * Tombol menonton membuka halaman episode resmi di browser, dan riwayat
 * menonton dicatat secara lokal.
 */
public class OtakudesuDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ANIME_ID = "anime_id";
    public static final String EXTRA_ANIME_TITLE = "anime_title";
    public static final String EXTRA_ANIME_POSTER = "anime_poster";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    /** Sumber halaman; bisa diganti pengujian dengan data offline. */
    protected OtakudesuSource createSource() {
        return new OtakudesuSource();
    }
    /** Tombol unduh per episode, diperbarui langsung dari status unduhan. */
    private final java.util.Map<String, Button> downloadButtons =
            new java.util.LinkedHashMap<>();
    private final java.util.Map<String, TextView> downloadPercents =
            new java.util.LinkedHashMap<>();
    private final java.util.Map<String, View> downloadProgressLines =
            new java.util.LinkedHashMap<>();
    private final java.util.Map<String, View> downloadCancels =
            new java.util.LinkedHashMap<>();
    private final java.util.Map<String, ProgressBar> downloadBars =
            new java.util.LinkedHashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<OtakudesuAnime.Episode> episodes = new ArrayList<>();
    private final List<OtakudesuAnime.Episode> shown = new ArrayList<>();

    private Palette palette;
    private OtakudesuSource source;
    private OtakudesuStore store;
    private String animeId;
    private String animeTitle;
    private String animePoster;
    private OtakudesuAnime.Detail detail;

    private TextView favoriteButton;
    private TextView episodeCountLabel;
    private LinearLayout episodeList;
    private EditText episodeSearch;
    private ProgressBar spinner;
    private TextView errorText;
    private Button retry;
    private boolean newestFirst = true;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeManager.applyTheme(this);
        palette = Palette.of(new Prefs(this).resolvedTheme());
        source = createSource();
        store = new OtakudesuStore(this);
        animeId = getIntent().getStringExtra(EXTRA_ANIME_ID);
        animeTitle = getIntent().getStringExtra(EXTRA_ANIME_TITLE);
        animePoster = getIntent().getStringExtra(EXTRA_ANIME_POSTER);
        if (animeId == null || animeId.isEmpty()) {
            finish();
            return;
        }
        buildUi();
        load();
    }

    // ------------------------------------------------------------------- UI

    private ScrollView scroll;
    private LinearLayout content;
    private ImageView poster;
    private TextView titleView;
    private TextView metaView;
    private TextView synopsisView;

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);
        root.addView(buildToolbar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout body = new FrameLayout(this);
        scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 24));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);

        RatioFrameLayout box = new RatioFrameLayout(this);
        box.setRatio(0.70f);
        box.setBackground(Ui.rounded(palette.surface, palette.divider, 14, this));
        box.setClipToOutline(true);
        poster = new ImageView(this);
        poster.setTag("otakudesu_detail_poster");
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        box.addView(poster, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        header.addView(box, new LinearLayout.LayoutParams(Ui.dp(this, 120), Ui.dp(this, 172)));

        LinearLayout headerText = new LinearLayout(this);
        headerText.setOrientation(LinearLayout.VERTICAL);
        headerText.setPadding(Ui.dp(this, 12), 0, 0, 0);
        titleView = Ui.label(this, "", 17, palette.text);
        titleView.setTag("otakudesu_detail_title");
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        headerText.addView(titleView);

        metaView = Ui.label(this, "", 12, palette.muted);
        metaView.setTag("otakudesu_detail_meta");
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = Ui.dp(this, 6);
        headerText.addView(metaView, metaParams);

        favoriteButton = Ui.chip(this, "☆ Favorit", palette);
        favoriteButton.setTag("otakudesu_favorite");
        favoriteButton.setOnClickListener(v -> toggleFavorite());
        LinearLayout.LayoutParams favoriteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        favoriteParams.topMargin = Ui.dp(this, 10);
        headerText.addView(favoriteButton, favoriteParams);

        header.addView(headerText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        content.addView(header);

        synopsisView = Ui.label(this, "", 13, palette.text);
        synopsisView.setTag("otakudesu_detail_synopsis");
        synopsisView.setLineSpacing(Ui.dp(this, 2), 1f);
        LinearLayout.LayoutParams synopsisParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        synopsisParams.topMargin = Ui.dp(this, 12);
        content.addView(synopsisView, synopsisParams);

        TextView section = Ui.label(this, "Daftar Episode", 15, palette.text);
        section.setTypeface(section.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sectionParams.topMargin = Ui.dp(this, 18);
        content.addView(section, sectionParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        controlParams.topMargin = Ui.dp(this, 8);
        content.addView(controls, controlParams);

        episodeSearch = new EditText(this);
        episodeSearch.setHint("Cari episode");
        episodeSearch.setTag("otakudesu_episode_search");
        episodeSearch.setSingleLine(true);
        episodeSearch.setTextColor(palette.text);
        episodeSearch.setHintTextColor(palette.muted);
        episodeSearch.setBackground(Ui.rounded(palette.surface, palette.divider, 12, this));
        episodeSearch.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        episodeSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { renderEpisodes(); }
        });
        controls.addView(episodeSearch, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button sort = Ui.chip(this, "Terbaru → Terlama", palette);
        sort.setTag("otakudesu_episode_sort");
        sort.setOnClickListener(v -> {
            newestFirst = !newestFirst;
            sort.setText(newestFirst ? "Terbaru → Terlama" : "Terlama → Terbaru");
            renderEpisodes();
        });
        LinearLayout.LayoutParams sortParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sortParams.leftMargin = Ui.dp(this, 8);
        controls.addView(sort, sortParams);

        episodeCountLabel = Ui.label(this, "", 12, palette.muted);
        episodeCountLabel.setTag("otakudesu_episode_count");
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countParams.topMargin = Ui.dp(this, 8);
        content.addView(episodeCountLabel, countParams);

        episodeList = new LinearLayout(this);
        episodeList.setOrientation(LinearLayout.VERTICAL);
        episodeList.setTag("otakudesu_episode_list");
        content.addView(episodeList);

        scroll.addView(content);
        body.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        spinner = new ProgressBar(this);
        spinner.setVisibility(View.VISIBLE);
        body.addView(spinner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        LinearLayout errorBox = new LinearLayout(this);
        errorBox.setOrientation(LinearLayout.VERTICAL);
        errorBox.setGravity(Gravity.CENTER);
        errorBox.setVisibility(View.GONE);
        errorText = Ui.label(this, "", 14, palette.text);
        errorText.setGravity(Gravity.CENTER);
        errorText.setTag("otakudesu_detail_error");
        errorBox.addView(errorText);
        retry = Ui.chip(this, "Coba lagi", palette);
        retry.setOnClickListener(v -> load());
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        retryParams.topMargin = Ui.dp(this, 12);
        errorBox.addView(retry, retryParams);
        body.addView(errorBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        errorBoxRef = errorBox;

        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        titleView.setText(animeTitle == null ? "" : animeTitle);
        if (animePoster != null && !animePoster.isEmpty()) {
            Glide.with(this).load(animePoster).centerCrop()
                    .placeholder(palette.surface).error(palette.surface).into(poster);
        }
        updateFavoriteLabel();
    }

    private LinearLayout errorBoxRef;

    private LinearLayout buildToolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        bar.setBackgroundColor(palette.surface);
        android.widget.ImageButton back = Ui.iconButton(this,
                R.drawable.ic_arrow_back, "Kembali", palette);
        back.setTag("otakudesu_detail_back");
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));
        TextView title = Ui.label(this, "Detail Anime", 18, palette.text);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = Ui.dp(this, 8);
        bar.addView(title, params);
        return bar;
    }

    // ----------------------------------------------------------------- muat

    private void load() {
        spinner.setVisibility(View.VISIBLE);
        errorBoxRef.setVisibility(View.GONE);
        retry.setVisibility(View.GONE);
        runInBackground(() -> {
            OtakudesuAnime.Detail result = null;
            String failure = null;
            try {
                result = source.detail(animeId);
            } catch (OtakudesuSource.SourceException error) {
                failure = error.getMessage();
            }
            final OtakudesuAnime.Detail payload = result;
            final String message = failure;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                spinner.setVisibility(View.GONE);
                if (message != null || payload == null) {
                    errorText.setText(message == null ? "Data anime tidak tersedia." : message);
                    errorBoxRef.setVisibility(View.VISIBLE);
                    retry.setVisibility(View.VISIBLE);
                    return;
                }
                bind(payload);
            });
        });
    }

    private void bind(OtakudesuAnime.Detail data) {
        detail = data;
        animeTitle = data.summary.title;
        animePoster = data.summary.posterUrl;
        titleView.setText(animeTitle);
        if (!animePoster.isEmpty()) {
            Glide.with(this).load(animePoster).centerCrop()
                    .placeholder(palette.surface).error(palette.surface).into(poster);
        }
        StringBuilder meta = new StringBuilder();
        appendLine(meta, "Status", data.status);
        appendLine(meta, "Total Episode", data.totalEpisodes);
        appendLine(meta, "Studio", data.studio);
        if (!data.summary.genres.isEmpty()) {
            appendLine(meta, "Genre", TextUtils.join(", ", data.summary.genres));
        }
        metaView.setText(meta.toString());
        synopsisView.setText(data.synopsis.isEmpty() ? "Sinopsis belum tersedia." : data.synopsis);
        episodes.clear();
        episodes.addAll(data.episodes);
        renderEpisodes();
        updateFavoriteLabel();
    }

    private void appendLine(StringBuilder builder, String label, String value) {
        if (value == null || value.trim().isEmpty()) return;
        if (builder.length() > 0) builder.append('\n');
        builder.append(label).append(": ").append(value.trim());
    }

    private void renderEpisodes() {
        shown.clear();
        String query = episodeSearch == null ? "" : episodeSearch.getText().toString().trim();
        String needle = query.toLowerCase(java.util.Locale.ROOT);
        for (OtakudesuAnime.Episode episode : episodes) {
            if (!needle.isEmpty()
                    && !episode.label.toLowerCase(java.util.Locale.ROOT).contains(needle)) continue;
            shown.add(episode);
        }
        final boolean descending = newestFirst;
        Collections.sort(shown, (left, right) -> {
            int byNumber;
            if (left.number >= 0 && right.number >= 0) {
                byNumber = Double.compare(left.number, right.number);
            } else {
                byNumber = left.label.compareToIgnoreCase(right.label);
            }
            return descending ? -byNumber : byNumber;
        });
        episodeList.removeAllViews();
        // Baris dibuat ulang: tombol lama tidak dipakai lagi.
        downloadButtons.clear();
        downloadBars.clear();
        downloadPercents.clear();
        downloadProgressLines.clear();
        downloadCancels.clear();
        for (OtakudesuAnime.Episode episode : shown) {
            episodeList.addView(episodeRow(episode));
        }
        String info = shown.size() + " episode";
        if (!episodes.isEmpty() && shown.size() != episodes.size()) {
            info += " dari " + episodes.size();
        }
        episodeCountLabel.setText(info);
        if (shown.isEmpty() && !episodes.isEmpty()) {
            TextView empty = Ui.label(this, "Tidak ada episode yang cocok.", 13, palette.muted);
            empty.setPadding(0, Ui.dp(this, 10), 0, 0);
            episodeList.addView(empty);
        }
    }

    /**
     * Satu baris daftar episode.
     *
     * <p>Susunannya dua tingkat supaya tetap terbaca di layar sempit: baris
     * pertama judul + tanggal + tombol Tonton dan Unduh, baris kedua khusus
     * kemajuan unduhan (persen, bar, dan tombol batal 48dp). Tanggal dan judul
     * tidak dipotong per kata; yang dipotong hanya ekor teks yang kepanjangan.
     */
    private View episodeRow(OtakudesuAnime.Episode episode) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        card.setBackground(Ui.ripple(this, palette.surface, palette.divider, 12, palette.ripple));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 6);
        card.setLayoutParams(params);

        final String key = AnimeDownloads.episodeKey(episode.url, episode.label);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        boolean watched = store.isWatched(animeId, episode.url);
        TextView label = Ui.label(this, (watched ? "\u2713 " : "") + episode.label, 13,
                watched ? palette.muted : palette.text);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        text.addView(label);
        if (!episode.date.isEmpty()) {
            TextView date = Ui.label(this, episode.date, 11, palette.muted);
            date.setMaxLines(1);
            date.setEllipsize(TextUtils.TruncateAt.END);
            text.addView(date);
        }
        top.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button watch = Ui.chip(this, "Tonton", palette);
        watch.setTag("otakudesu_watch_button");
        watch.setContentDescription("Tonton " + episode.label + " di pemutar resmi sumber");
        watch.setMinimumHeight(Ui.dp(this, Ui.TOUCH_DP));
        watch.setOnClickListener(v -> watch(episode));
        top.addView(watch);

        Button download = Ui.chip(this, downloadLabel(key), palette);
        download.setTag("otakudesu_download_button");
        download.setMinimumHeight(Ui.dp(this, Ui.TOUCH_DP));
        download.setOnClickListener(v -> startDownload(episode));
        LinearLayout.LayoutParams downloadParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, Ui.TOUCH_DP));
        downloadParams.leftMargin = Ui.dp(this, 6);
        top.addView(download, downloadParams);
        card.addView(top);

        // Baris kemajuan: persen dari byte nyata, bar, lalu X pembatal enggak
        // pernah menutupi tombol Tonton dan tetap 48dp.
        LinearLayout progressLine = new LinearLayout(this);
        progressLine.setOrientation(LinearLayout.HORIZONTAL);
        progressLine.setGravity(Gravity.CENTER_VERTICAL);
        progressLine.setTag("otakudesu_download_progress");

        TextView percent = Ui.label(this, "", 11, palette.muted);
        percent.setTag("otakudesu_download_percent");
        percent.setMinWidth(Ui.dp(this, 44));
        percent.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        progressLine.addView(percent);

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setTag("otakudesu_download_bar");
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                0, Ui.dp(this, 6), 1);
        barParams.leftMargin = Ui.dp(this, 8);
        barParams.rightMargin = Ui.dp(this, 4);
        progressLine.addView(bar, barParams);

        android.widget.ImageButton cancel = Ui.iconButton(this, R.drawable.ic_close,
                "Batalkan unduhan episode", palette);
        cancel.setTag("otakudesu_download_cancel");
        cancel.setOnClickListener(v -> cancelDownload(episode));
        progressLine.addView(cancel, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));

        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progressParams.topMargin = Ui.dp(this, 2);
        card.addView(progressLine, progressParams);

        if (!key.isEmpty()) {
            downloadButtons.put(key, download);
            downloadBars.put(key, bar);
            downloadPercents.put(key, percent);
            downloadProgressLines.put(key, progressLine);
            downloadCancels.put(key, cancel);
        }
        refreshRow(key);
        return card;
    }

    /**
     * Unduh episode dari daftar ini.
     *
     * <p>Tidak membuka pemutar. Bila izin penyimpanan belum diberikan, pengguna
     * diminta menyetujui lebih dulu; setelah itu lembar pemilihan kualitas
     * dibuka dan unduhan berjalan di latar belakang.
     */
    private void startDownload(final OtakudesuAnime.Episode episode) {
        if (episode == null) return;
        final String key = AnimeDownloads.episodeKey(episode.url, episode.label);
        if (isRunning(key)) {
            Toast.makeText(this, "Episode ini sedang diunduh.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (AnimeDownloads.isSaved(this, animeId, key)) {
            Toast.makeText(this, "Episode ini sudah tersimpan di perangkat. "
                    + "Buka dari menu Unduhan.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!AnimeDownloadPolicy.allowed(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Unduhan offline")
                    .setMessage(AnimeDownloadPolicy.notice())
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Setuju", (dialog, which) -> {
                        AnimeDownloadPolicy.acknowledge(this);
                        showQualitySheet(episode);
                    })
                    .show();
            return;
        }
        showQualitySheet(episode);
    }

    /** Lempar ke lembar pemilihan kualitas; tidak pernah membuka pemutar. */
    private void showQualitySheet(final OtakudesuAnime.Episode episode) {
        final Dialog sheet = new Dialog(this);
        sheet.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 22));
        panel.setBackground(Ui.ripple(this, palette.surface, palette.divider, 18, palette.ripple));
        panel.setTag("anime_quality_sheet");

        TextView title = Ui.label(this, "Pilih Kualitas Unduhan", 17, palette.text);
        panel.addView(title);
        TextView subtitle = Ui.label(this, episode.label, 12, palette.muted);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = Ui.dp(this, 2);
        panel.addView(subtitle, subtitleParams);

        TextView status = Ui.label(this, "Memeriksa kualitas yang benar-benar tersedia\u2026",
                12, palette.muted);
        status.setTag("anime_quality_status");
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = Ui.dp(this, 14);
        panel.addView(status, statusParams);

        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setTag("anime_quality_rows");
        LinearLayout.LayoutParams rowsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowsParams.topMargin = Ui.dp(this, 8);
        panel.addView(rows, rowsParams);

        Button close = Ui.chip(this, "Tutup", palette);
        close.setOnClickListener(v -> sheet.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, Ui.TOUCH_DP));
        closeParams.topMargin = Ui.dp(this, 16);
        panel.addView(close, closeParams);

        sheet.setContentView(panel);
        Window window = sheet.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                    android.graphics.Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }
        sheet.show();
        Window shown = sheet.getWindow();
        if (shown != null) {
            shown.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            shown.setGravity(Gravity.BOTTOM);
        }

        runInBackground(() -> {
            List<AnimeDownloadOptions.Option> options;
            String failure = null;
            try {
                options = AnimeDownloadOptions.resolve(source, null, episode.url);
            } catch (OtakudesuSource.SourceException error) {
                options = null;
                failure = error.getMessage();
            }
            final List<AnimeDownloadOptions.Option> result = options;
            final String message = failure;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (result == null) {
                    status.setText(message == null
                            ? "Kualitas tidak bisa diperiksa sekarang."
                            : message);
                    return;
                }
                status.setText("Hanya kualitas yang benar-benar tersedia yang bisa dipilih.");
                rows.removeAllViews();
                for (AnimeDownloadOptions.Option option : result) {
                    rows.addView(qualityRow(sheet, episode, option));
                }
            });
        });
    }

    /** Satu baris kualitas di lembar pemilihan. */
    private View qualityRow(final Dialog sheet, final OtakudesuAnime.Episode episode,
                            final AnimeDownloadOptions.Option option) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        row.setMinimumHeight(Ui.dp(this, Ui.TOUCH_DP));
        row.setBackground(Ui.ripple(this,
                option.available ? palette.background : palette.surface,
                palette.divider, 12, palette.ripple));
        row.setTag("anime_quality_" + option.quality);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 6);
        row.setLayoutParams(params);

        TextView name = Ui.label(this,
                option.quality + (option.available ? "" : " — tidak tersedia"),
                14, option.available ? palette.text : palette.muted);
        row.addView(name);
        TextView detail = Ui.label(this, option.detail(), 11, palette.muted);
        detail.setMaxLines(2);
        row.addView(detail);

        if (option.available) {
            row.setContentDescription("Unduh " + episode.label + " kualitas " + option.quality);
            row.setOnClickListener(v -> {
                sheet.dismiss();
                download(episode, option);
            });
        } else {
            row.setAlpha(0.55f);
            row.setEnabled(false);
            row.setContentDescription("Kualitas " + option.quality
                    + " tidak tersedia: " + option.reason);
        }
        return row;
    }

    /** Antrekan unduhan untuk kualitas yang dipilih pengguna. */
    private void download(final OtakudesuAnime.Episode episode,
                          final AnimeDownloadOptions.Option option) {
        if (option == null || !option.available) return;
        final String key = AnimeDownloads.episodeKey(episode.url, episode.label);
        if (isRunning(key)) {
            Toast.makeText(this, "Episode ini sedang diunduh.", Toast.LENGTH_SHORT).show();
            return;
        }
        AnimeDownloadTask task = new AnimeDownloadTask(animeId, animeTitle, animePoster,
                episode.label, episode.url, option.quality, option.server, option.url,
                "PROGRESSIVE");
        if (!task.downloadable()) {
            Toast.makeText(this, "Berkas ini tidak bisa disimpan sebagai unduhan offline.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        boolean queued = AnimeDownloadQueue.enqueue(this, task);
        if (queued) {
            AnimeDownloadStatus.set(key, AnimeDownloadStatus.QUEUED, 0, "Menyiapkan\u2026",
                    option.quality);
            Toast.makeText(this, "Unduhan " + episode.label + " " + option.quality
                    + " dimulai di latar belakang.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Unduhan episode ini sudah berjalan.", Toast.LENGTH_SHORT).show();
        }
        refreshDownloadButtons();
    }

    /** Batalkan unduhan episode ini saja, dengan konfirmasi. */
    private void cancelDownload(final OtakudesuAnime.Episode episode) {
        if (episode == null) return;
        final String key = AnimeDownloads.episodeKey(episode.url, episode.label);
        new AlertDialog.Builder(this)
                .setTitle("Batalkan unduhan?")
                .setMessage("Unduhan " + episode.label + " akan dihentikan dan berkas "
                        + "sementaranya dibuang. Unduhan episode lain tidak terpengaruh.")
                .setNegativeButton("Lanjutkan unduh", null)
                .setPositiveButton("Batalkan", (dialog, which) -> {
                    runInBackground(() -> {
                        final AnimeDownloadQueue.CancelResult result =
                                AnimeDownloadQueue.cancel(
                                        getApplicationContext(), animeId, key);
                        main.post(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            Toast.makeText(this, result.message(), Toast.LENGTH_SHORT).show();
                            refreshDownloadButtons();
                        });
                    });
                })
                .show();
    }

    /** Unduhan sedang berjalan (antre, mengunduh, atau memeriksa berkas). */
    private static boolean runningState(int state) {
        return state == AnimeDownloadStatus.QUEUED
                || state == AnimeDownloadStatus.DOWNLOADING
                || state == AnimeDownloadStatus.VERIFYING;
    }

    private boolean isRunning(String key) {
        return runningState(AnimeDownloadStatus.state(key));
    }

    /** Perbarui satu baris dari status yang tersimpan. */
    private void refreshRow(String key) {
        Button button = downloadButtons.get(key);
        ProgressBar bar = downloadBars.get(key);
        TextView percent = downloadPercents.get(key);
        View progressLine = downloadProgressLines.get(key);
        View cancel = downloadCancels.get(key);
        if (button == null) return;

        int state = AnimeDownloadStatus.state(key);
        boolean running = runningState(state);
        boolean saved = AnimeDownloads.isSaved(this, animeId, key);
        boolean partial = !saved && AnimeDownloads.savedBytes(this, animeId, key) > 0;

        String label = downloadLabel(key);
        button.setText(label);
        button.setContentDescription("Unduh episode: " + label);
        // Saat berjalan, tombol diganti baris kemajuan supaya persen dan X
        // menempati tempat yang sama di setiap episode.
        button.setVisibility(running ? View.GONE : View.VISIBLE);

        if (progressLine == null) return;
        boolean showProgress = running || state == AnimeDownloadStatus.PAUSED || partial;
        progressLine.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        if (cancel != null) cancel.setVisibility(showProgress ? View.VISIBLE : View.GONE);

        if (percent != null) {
            String text;
            if (state == AnimeDownloadStatus.QUEUED) {
                text = "Menunggu";
            } else if (state == AnimeDownloadStatus.VERIFYING) {
                text = "Memeriksa";
            } else if (state == AnimeDownloadStatus.PAUSED) {
                text = "Terjeda";
            } else if (AnimeDownloadStatus.total(key) > 0) {
                // Persen hanya dari byte nyata yang sudah tersimpan.
                text = AnimeDownloadStatus.percent(key) + "%";
            } else if (AnimeDownloadStatus.bytes(key) > 0) {
                text = AnimeDownload.size(AnimeDownloadStatus.bytes(key));
            } else {
                text = "";
            }
            percent.setText(text);
            percent.setVisibility(text.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        }
        if (bar != null) {
            bar.setProgress(AnimeDownloadStatus.percent(key));
            bar.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Label tombol unduh untuk satu episode.
     *
     * <p>Status diambil dari berkas dan dari {@link AnimeDownloadStatus}; tidak
     * ada angka yang dikarang. Persen hanya muncul bila jumlah byte total
     * diketahui, dan kualitas yang tersimpan dibaca dari keterangan berkasnya.
     */
    String downloadLabel(String key) {
        if (key == null || key.isEmpty()) return "Unduh";
        AnimeDownload saved = AnimeDownloads.readMeta(this, animeId, key);
        if (AnimeDownloads.isSaved(this, animeId, key)) {
            String quality = saved == null ? AnimeDownloadStatus.quality(key) : saved.quality;
            return quality == null || quality.isEmpty() ? "Tersimpan" : "Tersimpan \u00b7 " + quality;
        }
        int state = AnimeDownloadStatus.state(key);
        switch (state) {
            case AnimeDownloadStatus.SAVED:
                String quality = AnimeDownloadStatus.quality(key);
                return quality.isEmpty() ? "Tersimpan" : "Tersimpan \u00b7 " + quality;
            case AnimeDownloadStatus.QUEUED:
                return "Menyiapkan\u2026";
            case AnimeDownloadStatus.DOWNLOADING:
                return AnimeDownloadStatus.total(key) > 0
                        ? AnimeDownloadStatus.percent(key) + "%"
                        : AnimeDownload.size(AnimeDownloadStatus.bytes(key));
            case AnimeDownloadStatus.VERIFYING:
                return "Memeriksa\u2026";
            case AnimeDownloadStatus.PAUSED:
                return "Lanjutkan";
            case AnimeDownloadStatus.FAILED:
                return "Coba Lagi";
            default:
                // Proses bisa dimulai di sesi sebelumnya lalu aplikasi ditutup:
                // sisa berkas sementara adalah bukti nyata bahwa unduhan pernah
                // berjalan, jadi tombolnya menawarkan melanjutkan.
                return AnimeDownloads.savedBytes(this, animeId, key) > 0
                        ? "Lanjutkan" : "Unduh";
        }
    }

    /** Perbarui semua baris unduhan yang sedang tampil. */
    void refreshDownloadButtons() {
        for (String key : new java.util.ArrayList<>(downloadButtons.keySet())) {
            refreshRow(key);
        }
    }

    private final AnimeDownloadStatus.Listener downloadListener = this::refreshDownloadButtons;

    private void watch(OtakudesuAnime.Episode episode) {
        if (episode == null) return;
        startActivity(intentFor(episode));
    }

    private Intent intentFor(OtakudesuAnime.Episode episode) {
        return new Intent(this, AnimePlayerActivity.class)
                .putExtra(AnimePlayerActivity.EXTRA_ANIME_ID, animeId)
                .putExtra(AnimePlayerActivity.EXTRA_ANIME_TITLE, animeTitle)
                .putExtra(AnimePlayerActivity.EXTRA_ANIME_POSTER, animePoster)
                .putExtra(AnimePlayerActivity.EXTRA_EPISODE_URL, episode.url)
                .putExtra(AnimePlayerActivity.EXTRA_EPISODE_LABEL, episode.label)
                .putExtra(AnimePlayerActivity.EXTRA_EPISODE_NUMBER,
                        (int) Math.round(Math.max(0, episode.number)))
                .putExtra(AnimePlayerActivity.EXTRA_EPISODE_TOTAL, parseTotal())
                .putExtra(AnimePlayerActivity.EXTRA_EPISODES,
                        OtakudesuEpisodes.toJson(storyOrder()));
    }

    /** Urutan cerita (nomor menaik) untuk navigasi episode di pemutar. */
    private List<OtakudesuAnime.Episode> storyOrder() {
        List<OtakudesuAnime.Episode> ordered = new ArrayList<>(episodes);
        Collections.sort(ordered, (left, right) -> {
            if (left.number >= 0 && right.number >= 0) {
                return Double.compare(left.number, right.number);
            }
            return left.label.compareToIgnoreCase(right.label);
        });
        return ordered;
    }

    private int parseTotal() {
        String raw = detail == null ? "" : detail.totalEpisodes;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("([0-9]+)").matcher(raw == null ? "" : raw);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return episodes.size();
            }
        }
        return episodes.size();
    }

    /**
     * Membuka halaman episode di browser/Custom Tab.
     *
     * <p>Aplikasi tidak mengekstrak alamat video dari pemutar pihak ketiga;
     * halaman resmi sumber adalah satu-satunya jalur pemutaran yang tersedia.
     */
    static void openExternally(Context context, String url) {
        if (url == null || url.isEmpty()) return;
        try {
            CustomTabsIntent tabs = new CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build();
            tabs.launchUrl(context, Uri.parse(url));
        } catch (Exception error) {
            try {
                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
                // Tidak ada browser yang bisa menangani tautan.
            }
        }
    }

    static void openEpisode(Context context, OtakudesuStore.HistoryEntry entry) {
        if (entry == null) return;
        openExternally(context, entry.episodeUrl);
    }

    private void toggleFavorite() {
        boolean favorite = store.toggleFavorite(animeId, animeTitle, animePoster);
        updateFavoriteLabel();
        favoriteButton.animate().scaleX(1.08f).scaleY(1.08f).setDuration(90)
                .withEndAction(() -> favoriteButton.animate().scaleX(1f).scaleY(1f)
                        .setDuration(90).start())
                .start();
        if (favorite) {
            android.widget.Toast.makeText(this, "Ditambahkan ke favorit",
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void updateFavoriteLabel() {
        boolean favorite = store.isFavorite(animeId);
        favoriteButton.setText(favorite ? "★ Favorit" : "☆ Favorit");
        favoriteButton.setSelected(favorite);
    }

    @Override protected void onStart() {
        super.onStart();
        AnimeDownloadStatus.addListener(downloadListener);
        refreshDownloadButtons();
    }

    @Override protected void onStop() {
        AnimeDownloadStatus.removeListener(downloadListener);
        super.onStop();
    }

    @Override protected void onResume() {
        super.onResume();
        // Tanda "sudah dibuka" bisa berubah setelah menonton di pemutar.
        if (detail != null && episodeSearch != null) renderEpisodes();
        else refreshDownloadButtons();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    /**
     * Jalankan tugas latar hanya bila layar masih hidup.
     *
     * <p>Callback yang sudah mengantre bisa tiba setelah {@code onDestroy};
     * menyerahkan tugas baru ke executor yang sudah dimatikan akan melempar
     * {@code RejectedExecutionException}. Di sini kasus itu diabaikan dengan
     * sengaja: tidak ada lagi yang perlu dimuat.
     */
    private void runInBackground(Runnable task) {
        if (isFinishing() || isDestroyed()) return;
        try {
            worker.execute(task);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // Executor sudah ditutup: layar memang sudah tidak ada.
        }
    }
}
