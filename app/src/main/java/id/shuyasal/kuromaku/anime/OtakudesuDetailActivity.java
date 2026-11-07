package id.shuyasal.kuromaku.anime;

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
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

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
        source = new OtakudesuSource();
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
        worker.execute(() -> {
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

    private View episodeRow(OtakudesuAnime.Episode episode) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        row.setBackground(Ui.ripple(this, palette.surface, palette.divider, 12, palette.ripple));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 6);
        row.setLayoutParams(params);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        boolean watched = store.isWatched(animeId, episode.url);
        TextView label = Ui.label(this, (watched ? "✓ " : "") + episode.label, 13,
                watched ? palette.muted : palette.text);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        text.addView(label);
        if (!episode.date.isEmpty()) {
            TextView date = Ui.label(this, episode.date, 11, palette.muted);
            text.addView(date);
        }
        row.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button watch = Ui.chip(this, "Tonton", palette);
        watch.setTag("otakudesu_watch_button");
        watch.setContentDescription("Tonton " + episode.label + " di pemutar resmi sumber");
        watch.setOnClickListener(v -> watch(episode));
        row.addView(watch);
        return row;
    }

    private void watch(OtakudesuAnime.Episode episode) {
        if (episode == null) return;
        Intent intent = new Intent(this, OtakudesuPlayerActivity.class)
                .putExtra(OtakudesuPlayerActivity.EXTRA_ANIME_ID, animeId)
                .putExtra(OtakudesuPlayerActivity.EXTRA_ANIME_TITLE, animeTitle)
                .putExtra(OtakudesuPlayerActivity.EXTRA_ANIME_POSTER, animePoster)
                .putExtra(OtakudesuPlayerActivity.EXTRA_EPISODE_URL, episode.url)
                .putExtra(OtakudesuPlayerActivity.EXTRA_EPISODE_LABEL, episode.label)
                .putExtra(OtakudesuPlayerActivity.EXTRA_EPISODE_NUMBER,
                        (int) Math.round(Math.max(0, episode.number)))
                .putExtra(OtakudesuPlayerActivity.EXTRA_EPISODE_TOTAL, parseTotal())
                .putExtra(OtakudesuPlayerActivity.EXTRA_EPISODES,
                        OtakudesuEpisodes.toJson(storyOrder()));
        startActivity(intent);
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

    @Override protected void onResume() {
        super.onResume();
        // Tanda "sudah dibuka" bisa berubah setelah menonton di pemutar.
        if (detail != null && episodeSearch != null) renderEpisodes();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}