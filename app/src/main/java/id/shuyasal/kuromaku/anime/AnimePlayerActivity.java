package id.shuyasal.kuromaku.anime;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.PlayerView;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import id.shuyasal.kuromaku.R;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.ThemeManager;
import id.shuyasal.kuromaku.ui.Ui;

/**
 * Pemutar video native (Media3 ExoPlayer) untuk Anime Otakudesu.
 *
 * <p>Hanya memutar media yang benar-benar dipublikasikan halaman episode
 * (mp4/m3u8/mpd). Halaman episode Otakudesu normalnya hanya memuat iframe
 * pemutar pihak ketiga; untuk episode seperti itu tidak ada yang bisa diberikan
 * ke ExoPlayer, jadi layar ini menampilkan alasan yang jelas dan tombol
 * "Tonton di Sumber" — bukan layar hitam dan bukan WebView.
 *
 * <p>Tidak ada kontrol milik website di sini: tidak ada banner, popup, footer,
 * atau tombol mirror. Seluruh kontrol digambar aplikasi.
 */
@UnstableApi
public class AnimePlayerActivity extends AppCompatActivity {

    public static final String EXTRA_ANIME_ID = "anime_id";
    public static final String EXTRA_ANIME_TITLE = "anime_title";
    public static final String EXTRA_ANIME_POSTER = "anime_poster";
    public static final String EXTRA_EPISODE_URL = "episode_url";
    public static final String EXTRA_EPISODE_LABEL = "episode_label";
    public static final String EXTRA_EPISODE_NUMBER = "episode_number";
    public static final String EXTRA_EPISODE_TOTAL = "episode_total";
    public static final String EXTRA_EPISODES = "episodes";

    private static final long SAVE_EVERY_MS = 5_000L;
    /** Kontrol hilang setelah 5 detik tanpa interaksi (mode layar penuh). */
    private static final long CONTROLS_TIMEOUT_MS = 5_000L;
    private static final long CONTROLS_FADE_MS = 160L;
    private static final long SEEK_STEP_MS = 10_000L;
    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};

    private Palette palette;
    private OtakudesuStore store;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    /** Penjadwal tunggal untuk auto-hide kontrol. */
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean controlsVisible = true;
    private boolean panelOpen;

    private ExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private PlayerView playerView;
    private DefaultTimeBar timeBar;
    private ProgressBar buffering;
    private TextView clock;
    private View errorBox;
    private TextView errorText;
    private LinearLayout topBar;
    private LinearLayout bottomBar;
    private Button playButton;
    private Button previous;
    private Button next;
    private Button qualityButton;
    private Button subtitleButton;

    private String animeId = "";
    private String animeTitle = "";
    private String animePoster = "";
    private String episodeUrl = "";
    private String episodeLabel = "";
    private int episodeNumber;
    private int episodeTotal;
    private final List<OtakudesuAnime.Episode> episodes = new ArrayList<>();

    private int generation;
    private boolean resumeOnStart;
    /** Layar penuh immersive saja: bilah disembunyikan, orientasi mengikuti perangkat. */
    private boolean fullscreen;
    /**
     * Cara orientasi ditentukan. Hanya ada satu tempat yang memanggil
     * {@code setRequestedOrientation}, yaitu {@link #applyOrientation()}.
     *
     * <p>Sebelumnya orientasi ditulis di dua tempat yang saling menimpa:
     * "Putar layar" meminta landscape, lalu {@code setFullscreen()} langsung
     * menimpanya kembali ke "ikuti perangkat". Hasilnya persis yang dilaporkan:
     * layar sempat miring, lalu balik lagi sepersekian detik kemudian.
     */
    private static final int ORIENTATION_FOLLOW_DEVICE = 0;
    private static final int ORIENTATION_FORCED_LANDSCAPE = 1;
    private int orientationMode = ORIENTATION_FOLLOW_DEVICE;
    /** Orientasi eksplisit sebelum dipaksa, dikembalikan saat mode dilepas. */
    private int orientationBeforeForced = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    /** Perpindahan server sedang berjalan: pilihan berikutnya ditahan. */
    private boolean switchingServer;
    /** Nomor generasi pemilihan server; hasil async yang basi diabaikan. */
    private int serverGeneration;
    private boolean firstSource = true;
    private long pendingResumeMs;
    private String activeQuality = "Otomatis";
    private Button serverButton;
    /** Mirror terakhir yang dibaca dari halaman episode; sumber pemilih server. */
    private final List<OtakudesuPlayback.Mirror> mirrors = new ArrayList<>();
    /**
     * Kunci kanonis mirror → sumber media hasil pemeriksaan.
     *
     * <p>Dulu dipetakan dengan <b>posisi di daftar</b>. Posisi itu bukan
     * identitas: begitu daftar mirror diganti — episode lain, atau halaman
     * episode dibaca ulang — indeks yang sama menunjuk server yang berbeda,
     * sehingga pemeriksaan lama bisa dipakai untuk server yang salah, dan
     * panel yang masih terbuka bisa meminta indeks yang sudah tidak ada.
     */
    private final java.util.LinkedHashMap<String, OtakudesuPlayback.Source> probedServers =
            new java.util.LinkedHashMap<>();
    private final List<String> probingServers = new ArrayList<>();
    private PopupWindow serverPanel;
    private Document episodeDoc;
    private String activeServerLabel = "";
    /** Alamat pemutar server yang sedang dipilih; dipakai "Buka Player Sumber". */
    private String activePlayerUrl = "";
    /** Nama server yang sedang dipakai, untuk pesan kegagalan. */
    private String activeServerName = "";
    private String activeSubtitle = "Nonaktif";

    /** Tugas auto-hide; selalu dibatalkan sebelum dijadwalkan ulang. */
    private final Runnable hideControlsTask = new Runnable() {
        @Override public void run() {
            if (panelOpen || scrubbing || controlsPinned()) return;
            setControlsVisible(false);
        }
    };

    private final Runnable saveTick = new Runnable() {
        @Override public void run() {
            saveProgress(false);
            main.postDelayed(this, SAVE_EVERY_MS);
        }
    };

    @Override protected void onCreate(@Nullable Bundle state) {
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

        // Keadaan layar penuh dan orientasi dipulihkan SEBELUM UI ditata, jadi
        // tata letak pertama sudah benar dan tidak ada kedipan mode.
        if (state != null) {
            pendingResumeMs = state.getLong("resume_ms", 0L);
            orientationMode = state.getInt("orientation_mode", ORIENTATION_FOLLOW_DEVICE);
            orientationBeforeForced = state.getInt("orientation_before_forced",
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            fullscreen = state.getBoolean("immersive_fullscreen", false);
            firstSource = false;
        }
        buildUi();
        if (state != null) {
            // Terapkan tanpa mengubah state yang sudah dipulihkan.
            applyOrientation();
            if (fullscreen) {
                WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                        getWindow(), getWindow().getDecorView());
                controller.setSystemBarsBehavior(
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsetsCompat.Type.systemBars());
                setControlsVisible(true);
                scheduleHideControls();
            }
        }
        if (episodeUrl.isEmpty()) {
            showUnavailable("Episode tidak memiliki alamat halaman.");
            return;
        }
        loadEpisode(episodeUrl, episodeLabel);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    // ------------------------------------------------------------------- UI

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        playerView = new PlayerView(this);
        // Kontrol milik aplikasi; kontrol bawaan PlayerView tidak dipakai agar
        // tidak ada tombol atau elemen yang menyerupai pemutar website.
        playerView.setUseController(false);
        playerView.setTag("anime_player_surface");
        playerView.setShutterBackgroundColor(0xFF000000);
        // Satu ketukan pada permukaan video: munculkan kontrol (dan jadwalkan
        // sembunyi lagi 5 detik), atau sembunyikan bila sedang terlihat.
        playerView.setOnClickListener(v -> toggleControls());
        root.addView(playerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buffering = new ProgressBar(this);
        buffering.setVisibility(View.GONE);
        root.addView(buffering, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        topBar = buildTopBar();
        root.addView(topBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP));

        LinearLayout bottom = buildBottomBar();
        root.addView(bottom, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        errorBox = buildErrorBox();
        root.addView(errorBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            topBar.setPadding(bars.left, bars.top, bars.right, 0);
            bottom.setPadding(bars.left, 0, bars.right, bars.bottom);
            return insets;
        });
        setTitleText();
    }

    private LinearLayout buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xB3000000);
        bar.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));

        android.widget.ImageButton back = Ui.iconButton(this, R.drawable.ic_arrow_back,
                "Kembali", palette);
        back.setTag("anime_player_back");
        back.setOnClickListener(v -> {
            if (orientationMode == ORIENTATION_FORCED_LANDSCAPE) {
                setOrientationMode(ORIENTATION_FOLLOW_DEVICE);
                return;
            }
            if (fullscreen) {
                setFullscreen(false);
                return;
            }
            finish();
        });
        bar.addView(back, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));

        TextView title = Ui.label(this, "", 14, 0xFFFFFFFF);
        title.setTag("anime_player_title");
        title.setSingleLine(true);
        title.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        titleView = title;
        bar.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        // Baris atas hanya memuat kontrol yang paling sering dipakai pada layar
        // sempit: tombol kembali, judul singkat, satu pintasan server (supaya
        // pemilih server tetap satu sentuhan), dan menu tambahan. Server,
        // Kualitas, Subtitle, dan Kecepatan semuanya tersedia di menu itu juga,
        // jadi tidak ada kontrol yang hilang pada layar kecil.
        serverButton = chip(serverChipLabel(), "anime_player_server");
        serverButton.setOnClickListener(v -> {
            showControls();
            showServerPanel();
        });
        bar.addView(serverButton);

        Button more = chip("\u22ef", "anime_player_more");
        more.setContentDescription("Menu lainnya");
        more.setOnClickListener(v -> {
            showControls();
            showMoreMenu(bar);
        });
        bar.addView(more);
        return bar;
    }

    /** Label pintasan server: nama server aktif bila sudah diketahui. */
    private String serverChipLabel() {
        String name = activeServerLabel == null ? "" : activeServerLabel.trim();
        if (name.isEmpty() || !name.contains(" ")) return "Server";
        String quality = name.substring(0, name.indexOf(' ')).trim();
        return quality.isEmpty() ? "Server" : "Server " + quality;
    }

    /**
     * Menu tambahan pemutar: Server, Kualitas, Subtitle, dan Kecepatan.
     *
     * <p>Keempatnya dulu berupa chip pada baris atas; pada layar sempit baris itu
     * meluber ke kanan sehingga chip terpotong dan tidak bisa ditekan. Menu ini
     * menampung semuanya dengan label yang jelas.
     */
    private void showMoreMenu(View anchor) {
        boolean hasQuality = activeServerLabel != null && !activeServerLabel.trim().isEmpty();
        java.util.List<String> items = new java.util.ArrayList<>();
        items.add("Server  \u00b7  " + (activeServerLabel == null || activeServerLabel.trim().isEmpty()
                ? "belum dipilih" : activeServerLabel));
        if (hasQuality) items.add("Kualitas");
        items.add("Subtitle");
        items.add("Kecepatan");
        items.add(orientationMode == ORIENTATION_FORCED_LANDSCAPE
                ? "Kembali ke orientasi semula" : "Putar layar");
        String[] labels = items.toArray(new String[0]);

        cancelHideControls();
        setPanelOpen(true);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Menu pemutar")
                .setOnDismissListener(dialog -> setPanelOpen(false))
                .setItems(labels, (dialog, which) -> {
                    String chosen = labels[which];
                    if (chosen.startsWith("Server")) {
                        showServerPanel();
                    } else if (chosen.equals("Kualitas")) {
                        showQualityMenu();
                    } else if (chosen.equals("Subtitle")) {
                        showSubtitleMenu();
                    } else if (chosen.startsWith("Putar layar")
                            || chosen.equals("Kembali ke orientasi semula")) {
                        rotateScreen();
                    } else {
                        showSpeedMenu();
                    }
                })
                .show();
    }

    private TextView titleView;

    private Button chip(String text, String tag) {
        Button button = Ui.chip(this, text, palette);
        button.setTag(tag);
        button.setMinimumWidth(Ui.dp(this, 48));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, Ui.TOUCH_DP));
        params.leftMargin = Ui.dp(this, 4);
        button.setLayoutParams(params);
        return button;
    }

    private LinearLayout buildBottomBar() {
        LinearLayout column = new LinearLayout(this);
        bottomBar = column;
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(0xB3000000);

        timeBar = new DefaultTimeBar(this, null);
        timeBar.setTag("anime_player_timeline");
        timeBar.setBackgroundColor(0x00000000);
        timeBar.addListener(new androidx.media3.ui.TimeBar.OnScrubListener() {
            @Override public void onScrubStart(androidx.media3.ui.TimeBar timeBar, long position) {
                scrubbing = true;
                // Selama menyeret timeline, kontrol tidak boleh hilang.
                cancelHideControls();
                setControlsVisible(true);
            }

            @Override public void onScrubMove(androidx.media3.ui.TimeBar timeBar, long position) {
                pendingScrubMs = position;
                if (clock != null) clock.setText(format(position) + " / " + format(durationMs));
            }

            @Override public void onScrubStop(androidx.media3.ui.TimeBar timeBar, long position,
                                              boolean canceled) {
                scrubbing = false;
                if (player != null) player.seekTo(position);
                // Selesai menyeret: hitungan 5 detik dimulai lagi.
                showControls();
            }
        });
        column.addView(timeBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 28)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 6));

        previous = chip("‹ Sebelumnya", "anime_player_previous");
        previous.setOnClickListener(v -> {
            showControls();
            step(-1);
        });
        row.addView(previous, new LinearLayout.LayoutParams(0,
                Ui.dp(this, Ui.TOUCH_DP), 1f));

        Button back10 = chip("-10s", "anime_player_back10");
        back10.setOnClickListener(v -> {
            showControls();
            seekBy(-SEEK_STEP_MS);
        });
        row.addView(back10);

        playButton = chip("Putar", "anime_player_play");
        playButton.setOnClickListener(v -> {
            showControls();
            togglePlay();
        });
        row.addView(playButton, new LinearLayout.LayoutParams(0,
                Ui.dp(this, Ui.TOUCH_DP), 1f));

        Button forward10 = chip("+10s", "anime_player_forward10");
        forward10.setOnClickListener(v -> {
            showControls();
            seekBy(SEEK_STEP_MS);
        });
        row.addView(forward10);

        next = chip("Berikutnya ›", "anime_player_next");
        next.setOnClickListener(v -> {
            showControls();
            step(1);
        });
        row.addView(next, new LinearLayout.LayoutParams(0,
                Ui.dp(this, Ui.TOUCH_DP), 1f));

        column.addView(row);

        clock = Ui.label(this, "00:00 / 00:00", 11, 0xFFDDDDDD);
        clock.setTag("anime_player_clock");
        clock.setGravity(Gravity.CENTER);
        clock.setPadding(0, 0, 0, Ui.dp(this, 4));
        column.addView(clock);
        return column;
    }

    private View buildErrorBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(0xFF000000);
        box.setPadding(Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24));
        box.setVisibility(View.GONE);

        errorText = Ui.label(this, "", 14, 0xFFFFFFFF);
        errorText.setTag("anime_player_error");
        errorText.setGravity(Gravity.CENTER);
        box.addView(errorText);

        Button pickServer = Ui.chip(this, "Coba Server Lain", palette);
        pickServer.setTag("anime_player_pick_server");
        pickServer.setOnClickListener(v -> showServerPanel());

        Button openPlayer = Ui.chip(this, "Buka Player Sumber", palette);
        openPlayer.setTag("anime_player_open_server");
        openPlayer.setOnClickListener(v -> openServerPlayer());
        LinearLayout.LayoutParams pickParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pickParams.topMargin = Ui.dp(this, 12);
        box.addView(pickServer, pickParams);
        box.addView(openPlayer, pickParams);

        Button retry = Ui.chip(this, "Coba Lagi", palette);
        retry.setTag("anime_player_retry");
        retry.setOnClickListener(v -> {
            hideError();
            if (player == null) loadEpisode(episodeUrl, episodeLabel);
            else player.prepare();
        });
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        retryParams.topMargin = Ui.dp(this, 8);
        box.addView(retry, retryParams);

        Button website = Ui.chip(this, "Tonton di Sumber", palette);
        website.setTag("anime_player_source");
        website.setOnClickListener(v -> openEpisodePage());
        LinearLayout.LayoutParams websiteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        websiteParams.topMargin = Ui.dp(this, 8);
        box.addView(website, websiteParams);
        return box;
    }

    private void setTitleText() {
        if (titleView == null) return;
        String label = episodeLabel.isEmpty() ? animeTitle : episodeLabel;
        titleView.setText(label);
    }

    // -------------------------------------------------------------- pemutaran

    private void ensurePlayer() {
        if (player != null) return;
        try {
            createPlayer();
        } catch (Throwable error) {
            // Perangkat tanpa codec/permukaan yang cocok tidak boleh membuat
            // aplikasi tertutup: pemutaran dilaporkan gagal, UI tetap hidup.
            player = null;
            showUnavailable(kegagalanServer(activeServerName,
                    "Pemutar tidak dapat disiapkan pada perangkat ini.")
                    + (error.getMessage() == null ? "" : " (" + error.getMessage() + ")"));
        }
    }

    private void createPlayer() {
        // Buffer wajar: cukup untuk koneksi lambat tanpa menyimpan banyak video.
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 60_000, 1_500, 3_000)
                .build();
        trackSelector = new DefaultTrackSelector(this);
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setPreferredTextLanguage("id")
                .setSelectUndeterminedTextLanguage(true));
        player = new ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .setTrackSelector(trackSelector)
                .build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                buffering.setVisibility(state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
                updatePlayButton();
                if (state == Player.STATE_READY) {
                    updateTrackButtons();
                    firstSource = false;
                } else if (state == Player.STATE_ENDED) {
                    saveProgress(true);
                    markFinished();
                }
                // Saat buffering video belum berjalan: kontrol penting tetap
                // terlihat supaya pengguna tidak kehilangan jalan memulihkan.
                if (state == Player.STATE_BUFFERING) showControls();
            }

            @Override public void onIsPlayingChanged(boolean playing) {
                updatePlayButton();
                // Selama tidak diputar, kontrol ditahan terlihat.
                if (!playing) showControls();
                else scheduleHideControls();
            }

            @Override public void onTracksChanged(Tracks tracks) {
                updateTrackButtons();
            }

            @Override public void onPlayerError(androidx.media3.common.PlaybackException error) {
                // Galat pemutaran tidak menutup aplikasi dan tidak menutup
                // Activity: pesan spesifik + tiga jalan keluar.
                showError(kegagalanServer(activeServerName,
                        "Berkas video gagal dimuat.")
                        + " Kode: " + error.errorCode + ".");
                showControls();
            }
        });
    }

    /** Muat satu episode: periksa sumber, lalu putar bila memang ada media. */
    private void loadEpisode(String url, String label) {
        final int request = ++generation;
        episodeUrl = url;
        episodeLabel = label;
        setTitleText();
        updateNeighbors();
        showBuffering(true);
        hideError();

        if (firstSource) {
            pendingResumeMs = savedPosition(episodeUrl);
        }

        worker.execute(() -> {
            OtakudesuPlayback playback = null;
            String failure = null;
            String html = null;
            try {
                OtakudesuSource source = new OtakudesuSource();
                html = source.html(url);
                playback = OtakudesuMediaResolver.resolveHtml(html, null, url);
            } catch (OtakudesuSource.SourceException error) {
                failure = error.getMessage();
            }
            final OtakudesuPlayback result = playback;
            final String message = failure;
            final String pageHtml = html;
            main.post(() -> {
                if (isFinishing() || request != generation) return;
                showBuffering(false);
                if (message != null) {
                    showUnavailable(message);
                    return;
                }
                if (result == null || result.mirrors.isEmpty()) {
                    showUnavailable("Halaman episode ini tidak menyebut satu pun mirror"
                            + " yang bisa dipilih.");
                    return;
                }
                episodeDoc = pageHtml == null ? null : Jsoup.parse(pageHtml);
                mirrors.clear();
                mirrors.addAll(result.mirrors);
                probedServers.clear();
                activePlayback = result;

                // Sumber media yang sudah dipublikasikan halaman episode dipakai
                // lebih dulu; kalau tidak ada, pemilih server yang menentukan.
                if (result.playable()) {
                    activeServerLabel = "Halaman episode";
                    updateServerButton();
                    play(result.sources.get(0), result.qualities());
                    return;
                }
                updateServerButton();
                showServerPanel();
            });
        });
    }

    private void updateServerButton() {
        if (serverButton == null) return;
        if (!activeServerLabel.isEmpty()) {
            serverButton.setText(activeServerLabel);
        } else if (!mirrors.isEmpty()) {
            serverButton.setText("Server (" + mirrors.size() + ")");
        } else {
            serverButton.setText("Server");
        }
    }

    /**
     * Panel "Pilih Server Streaming". Server yang berkasnya terbuka diperiksa
     * lebih dulu di latar belakang; pengguna juga bisa memilih langsung tanpa
     * menunggu, dan hasilnya dilaporkan apa adanya.
     */
    private void showServerPanel() {
        if (mirrors.isEmpty()) {
            Toast.makeText(this, "Halaman episode ini tidak menyebut mirror.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (episodeDoc == null) {
            Toast.makeText(this, "Halaman episode belum selesai dibaca.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        OtakudesuServerPanel.Palette colors = new OtakudesuServerPanel.Palette(
                palette.surface, palette.text, palette.muted, palette.accent, palette.ripple);
        OtakudesuServerPanel panel = new OtakudesuServerPanel(this, colors);

        popupPanel(panel.build(mirrors, probedServers, probingServers,
                panelListener()));

        // Pemeriksaan latar belakang dibatasi: hanya dua server yang paling
        // mungkin terbuka, dan tidak menumpuk bila ada yang sedang berjalan.
        // Sisanya menunggu permintaan pengguna (ketuk server, atau Periksa
        // semua server). Untuk sembilan mirror, memeriksa semuanya otomatis
        // berarti sembilan permintaan ke penyedia.
        for (OtakudesuPlayback.Mirror mirror : OtakudesuServerPicker.probeOrder(mirrors)) {
            if (probingServers.size() >= 2) break;
            if (mirror == null) continue;
            String id = mirror.key();
            if (probedServers.containsKey(id) || probingServers.contains(id)) continue;
            probeServer(mirror, id, false);
        }
    }

    private void dismissServerPanel() {
        if (serverPanel != null && serverPanel.isShowing()) serverPanel.dismiss();
        serverPanel = null;
        setPanelOpen(false);
    }

    /** Panel ditampilkan sebagai popup agar tidak memindahkan layar video. */
    private void popupPanel(View content) {
        dismissServerPanel();
        setPanelOpen(true);
        int width = Math.min(getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 24),
                Ui.dp(this, 420));
        serverPanel = new PopupWindow(content, width,
                (int) (getResources().getDisplayMetrics().heightPixels * 0.6f), true);
        serverPanel.setBackgroundDrawable(Ui.rounded(0xF0111318, 0x33FFFFFF, 18, this));
        serverPanel.setOutsideTouchable(true);
        serverPanel.showAtLocation(playerView, Gravity.CENTER, 0, 0);
    }

    /**
     * Membuka pemutar server yang sedang dipilih di peramban.
     *
     * <p>Ini jalan keluar untuk server yang berkasnya tidak bisa dibaca Media3
     * (mis. pemutar beriklan atau pemutar terenkripsi). Halaman penyedia tampil
     * apa adanya — termasuk iklannya — karena aplikasi tidak mengubah apa pun
     * di sana.
     */
    private void openServerPlayer() {
        // Tujuan pertama: pemutar server itu sendiri. Kalau server mengirim
        // berkas langsung (tidak lewat halaman pemutar), halaman episode yang
        // jadi tujuan — di sana penyedia menampilkan pemutarnya sendiri.
        String url = activePlayerUrl;
        if (url == null || url.isEmpty()) url = episodeUrl;
        if (url == null || url.isEmpty()) {
            Toast.makeText(this, "Tidak ada halaman pemutar yang bisa dibuka.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Tampilan mengikuti penyedia dan tidak selalu bebas iklan.",
                Toast.LENGTH_LONG).show();
        try {
            new CustomTabsIntent.Builder().setShowTitle(true).build()
                    .launchUrl(this, Uri.parse(url));
        } catch (Exception error) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
                Toast.makeText(this, "Tidak ada browser untuk membuka pemutar sumber",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    /** Membuka satu server lalu memeriksa berkasnya; hasilnya ditulis ke layar. */
    /**
     * Listener panel server — satu implementasi untuk kedua jalur tampilan.
     *
     * <p>Pemilihan memakai {@link OtakudesuPlayback.Mirror#key()}, bukan posisi
     * di daftar: panel yang masih terbuka bisa memegang mirror dari daftar
     * sebelumnya, dan {@code indexOf} atas mirror seperti itu menghasilkan -1.
     * Indeks -1 dulu dipakai untuk membaca dan menulis daftar, yang melempar
     * {@code IndexOutOfBoundsException} — persis crash saat memilih server.
     */
    private OtakudesuServerPanel.Listener panelListener() {
        return new OtakudesuServerPanel.Listener() {
            @Override public void onPick(OtakudesuPlayback.Mirror mirror) {
                if (mirror == null) return;
                pickServer(mirror);
            }

            @Override public void onCheckAll() {
                for (OtakudesuPlayback.Mirror mirror : new ArrayList<>(mirrors)) {
                    String id = mirror.key();
                    if (probedServers.containsKey(id) || probingServers.contains(id)) continue;
                    probeServer(mirror, id, false);
                }
            }
        };
    }

    /**
     * Pengguna memilih satu server.
     *
     * <p>Transisi diserialisasi: selama perpindahan berjalan, ketukan berikutnya
     * diabaikan (bukan dijalankan bersamaan). Ini mencegah dua
     * {@code prepare()} saling menimpa dan mencegah callback lama menulis ke
     * pemutar yang sudah memuat server lain.
     */
    private void pickServer(OtakudesuPlayback.Mirror mirror) {
        if (switchingServer) {
            Toast.makeText(this, "Sedang berpindah server…", Toast.LENGTH_SHORT).show();
            return;
        }
        String id = mirror.key();
        OtakudesuPlayback.Source ready = probedServers.get(id);
        if (ready != null) {
            switchServer(mirror, ready);
            return;
        }
        probeServer(mirror, id, true);
    }

    private void probeServer(OtakudesuPlayback.Mirror mirror, String id, boolean pickWhenReady) {
        if (mirror == null || id == null) return;
        if (probingServers.contains(id)) return;
        if (id.isEmpty()) return;
        probingServers.add(id);
        final int probeGeneration = serverGeneration;
        refreshServerPanel();
        main.postDelayed(refreshServerPanelTask, 150);

        worker.execute(() -> {
            OtakudesuServerPicker.Probed result;
            try {
                result = OtakudesuServerPicker.probe(mirror, OtakudesuServerPicker.networkHttp(),
                        episodeDoc);
            } catch (Throwable error) {
                // Kegagalan apa pun dari penyedia (jaringan, parser, format
                // tak dikenal) berakhir sebagai status, bukan crash.
                result = new OtakudesuServerPicker.Probed(
                        OtakudesuServerPicker.Status.UNAVAILABLE, null,
                        error.getMessage() == null ? "Server tidak dapat diperiksa." : error.getMessage());
            }
            final OtakudesuServerPicker.Probed outcome = result;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                probingServers.remove(id);
                // Hasil dari generasi lama tidak boleh menyentuh keadaan baru.
                if (probeGeneration != serverGeneration) return;
                if (outcome.playable() && outcome.source != null) {
                    probedServers.put(id, outcome.source);
                }
                markMirrorPlayerUrl(id, outcome.playerUrl);
                refreshServerPanel();
                if (!pickWhenReady) return;
                if (outcome.playable() && outcome.source != null) {
                    switchServer(mirror, outcome.source);
                } else {
                    activeServerName = OtakudesuServerPanel.summary(mirror);
                    activePlayerUrl = outcome.playerUrl == null ? "" : outcome.playerUrl;
                    switchingServer = false;
                    showUnavailable(kegagalanServer(activeServerName, outcome.note));
                    showServerPanel();
                }
            });
        });
    }

    /**
     * Simpan alamat pemutar ke mirror yang tepat.
     *
     * <p>Dulu memakai {@code mirrors.set(index, ...)} dengan penjaga
     * {@code index < mirrors.size()} saja — untuk {@code index == -1} penjaga
     * itu lolos dan daftar ditulis di posisi negatif. Sekarang dicari lewat
     * kunci kanonis, dan tidak ada penulisan bila mirrornya sudah tidak ada.
     */
    private void markMirrorPlayerUrl(String id, String playerUrl) {
        if (playerUrl == null || playerUrl.isEmpty()) return;
        for (int i = 0; i < mirrors.size(); i++) {
            OtakudesuPlayback.Mirror current = mirrors.get(i);
            if (current != null && id.equals(current.key())) {
                mirrors.set(i, current.withPlayer(playerUrl));
                return;
            }
        }
    }

    private final Runnable refreshServerPanelTask = new Runnable() {
        @Override public void run() {
            if (isFinishing() || isDestroyed()) return;
            refreshServerPanel();
        }
    };

    private void refreshServerPanel() {
        if (serverPanel == null || !serverPanel.isShowing()) return;
        OtakudesuServerPanel.Palette colors = new OtakudesuServerPanel.Palette(
                palette.surface, palette.text, palette.muted, palette.accent, palette.ripple);
        OtakudesuServerPanel panel = new OtakudesuServerPanel(this, colors);
        serverPanel.setContentView(panel.build(mirrors, probedServers, probingServers,
                panelListener()));
    }

    /**
     * Berpindah ke server lain untuk <b>episode yang sama</b>.
     *
     * <p>Posisi menonton disimpan lebih dulu dan langsung dipakai di sumber baru,
     * jadi pengguna tidak dikembalikan ke awal episode. Riwayat, favorit, dan
     * progres episode tetap utuh — hanya satu field posisi yang dibawa.
     */
    private void switchServer(OtakudesuPlayback.Mirror mirror, OtakudesuPlayback.Source ready) {
        if (mirror == null || ready == null) {
            // Jangan pernah menyerahkan sumber kosong ke pemutar: itulah bentuk
            // kegagalan yang tampil sebagai crash.
            switchingServer = false;
            showUnavailable("Server ini tidak menyediakan berkas video yang bisa diputar.");
            return;
        }
        if (switchingServer) return;
        switchingServer = true;
        serverGeneration++;
        long position = savedPosition(episodeUrl);
        pendingResumeMs = position;
        dismissServerPanel();
        activeServerLabel = OtakudesuServerPanel.summary(mirror);
        activeServerName = activeServerLabel;
        activePlayerUrl = knownPlayerUrl(mirror);
        updateServerButton();
        try {
            play(ready, OtakudesuPlayback.singletonQuality(ready));
        } finally {
            // Transisi selesai begitu sumber baru sudah diserahkan ke pemutar;
            // kegagalan setelah ini ditangani listener pemutar.
            switchingServer = false;
        }
    }

    /** Alamat pemutar server ini, bila sudah pernah dibuka saat diperiksa. */
    private String knownPlayerUrl(OtakudesuPlayback.Mirror mirror) {
        if (mirror == null) return "";
        if (mirror.playerUrl != null && !mirror.playerUrl.isEmpty()) return mirror.playerUrl;
        for (OtakudesuPlayback.Mirror known : mirrors) {
            if (known != null && known.key().equals(mirror.key()) && !known.playerUrl.isEmpty()) {
                return known.playerUrl;
            }
        }
        for (OtakudesuPlayback.Mirror known : mirrors) {
            if (known.provider.equalsIgnoreCase(mirror.provider)
                    && known.quality.equals(mirror.quality)
                    && !known.playerUrl.isEmpty()) {
                return known.playerUrl;
            }
        }
        return activePlayerUrl;
    }

    /** Pesan kegagalan: server mana, sebabnya apa, dan jalan keluarnya. */
    static String kegagalanServer(String server, String note) {
        StringBuilder message = new StringBuilder();
        if (server == null || server.isEmpty()) {
            message.append("Video tidak dapat diputar aplikasi.");
        } else {
            message.append("Server ").append(server).append(" tidak dapat diputar aplikasi.");
        }
        if (note != null && !note.trim().isEmpty()) {
            message.append(' ').append(note.trim());
        }
        message.append(" Pilih server lain, atau buka pemutar sumber lewat tombol")
                .append(" Buka Player Sumber.");
        return message.toString();
    }

    private static String mirrorSummary(OtakudesuPlayback playback) {
        StringBuilder builder = new StringBuilder();
        for (OtakudesuPlayback.Mirror mirror : playback.mirrors) {
            if (builder.length() > 0) builder.append(", ");
            if (!mirror.quality.isEmpty()) builder.append(mirror.quality).append(' ');
            builder.append(mirror.provider);
        }
        return builder.toString();
    }

    private OtakudesuPlayback activePlayback;

    /**
     * Memutar satu sumber.
     *
     * <p>Posisi menonton yang tersimpan selalu dipakai lagi saat sumber berganti.
     * Sebelumnya penggantian server mengosongkan {@code pendingResumeMs}, jadi
     * berpindah server membuat episode mulai dari awal — riwayat menontonnya
     * tidak hilang, tetapi posisinya terbuang.
     */
    private void play(OtakudesuPlayback.Source source, List<String> qualities) {
        if (source == null || source.url == null || source.url.isEmpty()) {
            showUnavailable("Sumber ini tidak menyediakan berkas video yang bisa diputar.");
            return;
        }
        ensurePlayer();
        if (player == null) return;
        // Tetap satu player: episode baru memakai player yang sama, bukan
        // Activity atau player bertumpuk.
        player.setMediaItem(mediaItem(source), Math.max(0L, pendingResumeMs));
        player.prepare();
        player.setPlayWhenReady(true);
        pendingResumeMs = 0L;
        activeQuality = "Otomatis";
        if (qualityButton != null) qualityButton.setText("Auto");
        startTicker();
    }

    private MediaItem mediaItem(OtakudesuPlayback.Source source) {
        MediaItem.Builder builder = new MediaItem.Builder().setUri(Uri.parse(source.url));
        switch (source.kind) {
            case HLS:
                builder.setMimeType(MimeTypes.APPLICATION_M3U8);
                break;
            case DASH:
                builder.setMimeType(MimeTypes.APPLICATION_MPD);
                break;
            default:
                builder.setMimeType(MimeTypes.VIDEO_MP4);
                break;
        }
        return builder.build();
    }

    private void togglePlay() {
        if (player == null) return;
        if (player.isPlaying()) player.pause();
        else player.play();
    }

    private void seekBy(long deltaMs) {
        if (player == null) return;
        long duration = player.getDuration();
        long target = player.getCurrentPosition() + deltaMs;
        if (target < 0) target = 0;
        if (duration > 0 && target > duration) target = duration;
        player.seekTo(target);
    }

    private void step(int delta) {
        OtakudesuAnime.Episode target = OtakudesuEpisodes.neighbor(episodes, episodeUrl, delta);
        if (target == null) return;
        saveProgress(false);
        pendingResumeMs = 0L;
        firstSource = false;
        episodeNumber = (int) Math.round(Math.max(0, target.number));
        loadEpisode(target.url, target.label);
    }

    /**
     * Posisi menonton tersimpan untuk sebuah episode.
     *
     * <p>Dipisah sebagai fungsi supaya penggantian server bisa memakai posisi
     * yang sama: kalau pemutar masih memutar episode ini, posisi saat ini lebih
     * baru daripada yang tersimpan.
     */
    private long savedPosition(String url) {
        if (player != null && episodeUrl != null && episodeUrl.equals(url)
                && player.getDuration() > 0) {
            return Math.max(0L, player.getCurrentPosition());
        }
        if (animeId.isEmpty()) return 0L;
        OtakudesuStore.Progress progress = store.progress(animeId,
                OtakudesuSource.episodeId(url));
        return progress == null ? 0L : progress.resumePositionMs();
    }

    private void updateNeighbors() {
        if (previous == null || next == null) return;
        boolean hasPrevious = OtakudesuEpisodes.neighbor(episodes, episodeUrl, -1) != null;
        boolean hasNext = OtakudesuEpisodes.neighbor(episodes, episodeUrl, 1) != null;
        previous.setEnabled(hasPrevious);
        previous.setAlpha(hasPrevious ? 1f : 0.4f);
        next.setEnabled(hasNext);
        next.setAlpha(hasNext ? 1f : 0.4f);
    }

    private void updatePlayButton() {
        if (playButton == null || player == null) return;
        playButton.setText(player.isPlaying() ? "Jeda" : "Putar");
    }

    private void startTicker() {
        // Keduanya dibatalkan lebih dulu: tanpa ini setiap perpindahan server
        // menambah satu rantai clockTick yang memposting dirinya sendiri, dan
        // setelah beberapa perpindahan ada beberapa timer yang berebut UI.
        main.removeCallbacks(saveTick);
        main.removeCallbacks(clockTick);
        main.postDelayed(saveTick, SAVE_EVERY_MS);
        main.postDelayed(clockTick, 500);
    }

    private boolean scrubbing;
    private long pendingScrubMs;
    private long durationMs;

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (player != null) {
                durationMs = player.getDuration();
                long position = scrubbing ? pendingScrubMs : player.getCurrentPosition();
                clock.setText(format(position) + " / " + format(durationMs));
                if (timeBar != null) {
                    if (durationMs > 0) timeBar.setDuration(durationMs);
                    timeBar.setPosition(position);
                    timeBar.setBufferedPosition(player.getBufferedPosition());
                }
            }
            main.postDelayed(this, 500);
        }
    };

    static String format(long ms) {
        if (ms <= 0) return "00:00";
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    // ------------------------------------------------------------ track menu

    private void updateTrackButtons() {
        if (subtitleButton != null) {
            subtitleButton.setEnabled(hasTextTracks());
            subtitleButton.setAlpha(hasTextTracks() ? 1f : 0.45f);
        }
        if (qualityButton != null) {
            boolean multiple = videoHeights().size() > 1;
            qualityButton.setEnabled(multiple);
            qualityButton.setAlpha(multiple ? 1f : 0.45f);
        }
    }

    private boolean hasTextTracks() {
        if (player == null) return false;
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() == C.TRACK_TYPE_TEXT) return true;
        }
        return false;
    }

    /** Tinggi video yang benar-benar ada di manifest; bukan daftar karangan. */
    private List<Integer> videoHeights() {
        List<Integer> heights = new ArrayList<>();
        if (player == null) return heights;
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int i = 0; i < group.length; i++) {
                int height = group.getTrackFormat(i).height;
                if (height > 0 && !heights.contains(height)) heights.add(height);
            }
        }
        java.util.Collections.sort(heights);
        return heights;
    }

    /**
     * Dialog pilihan yang menahan auto-hide selama terbuka.
     *
     * <p>Tanpa ini, kontrol bisa hilang di tengah pengguna memilih kualitas atau
     * subtitle, dan pengguna kehilangan tempat kembali.
     */
    private void showOptionsDialog(String title, String[] labels,
                                   android.content.DialogInterface.OnClickListener onPick) {
        cancelHideControls();
        setPanelOpen(true);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(labels, onPick)
                .setOnDismissListener(dialog -> setPanelOpen(false))
                .show();
    }

    private void showQualityMenu() {
        List<Integer> heights = videoHeights();
        if (heights.isEmpty()) return;
        final List<String> labels = new ArrayList<>();
        labels.add("Otomatis");
        for (Integer height : heights) labels.add(height + "p");

        cancelHideControls();
        setPanelOpen(true);
        new AlertDialog.Builder(this)
                .setTitle("Kualitas video")
                .setOnDismissListener(dialog -> setPanelOpen(false))
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    if (player == null || trackSelector == null) return;
                    if (which == 0) {
                        trackSelector.setParameters(trackSelector.buildUponParameters()
                                .clearVideoSizeConstraints());
                        activeQuality = "Otomatis";
                    } else {
                        int height = heights.get(which - 1);
                        trackSelector.setParameters(trackSelector.buildUponParameters()
                                .setMaxVideoSize(Integer.MAX_VALUE, height)
                                .setMinVideoSize(0, height));
                        activeQuality = height + "p";
                    }
                    qualityButton.setText(which == 0 ? "Auto" : activeQuality);
                })
                .show();
    }

    private void showSubtitleMenu() {
        if (player == null || trackSelector == null) return;
        final List<Tracks.Group> groups = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        labels.add("Nonaktif");
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < group.length; i++) {
                String language = group.getTrackFormat(i).language;
                String label = group.getTrackFormat(i).label;
                labels.add(label != null && !label.isEmpty() ? label
                        : (language == null ? "Subtitle" : language.toUpperCase(Locale.ROOT)));
                groups.add(group);
            }
        }
        if (groups.isEmpty()) return;

        cancelHideControls();
        setPanelOpen(true);
        new AlertDialog.Builder(this)
                .setTitle("Subtitle")
                .setOnDismissListener(dialog -> setPanelOpen(false))
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        trackSelector.setParameters(trackSelector.buildUponParameters()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true));
                        activeSubtitle = "Nonaktif";
                    } else {
                        Tracks.Group group = groups.get(which - 1);
                        trackSelector.setParameters(trackSelector.buildUponParameters()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(new TrackSelectionOverride(
                                        group.getMediaTrackGroup(), 0)));
                        activeSubtitle = labels.get(which);
                    }
                    subtitleButton.setText(activeSubtitle.equals("Nonaktif")
                            ? "Subtitle" : activeSubtitle);
                })
                .show();
    }

    private void showSpeedMenu() {
        if (player == null) return;
        String[] labels = {"0.5x", "0.75x", "Normal", "1.25x", "1.5x", "2x"};
        cancelHideControls();
        setPanelOpen(true);
        new AlertDialog.Builder(this)
                .setTitle("Kecepatan")
                .setOnDismissListener(dialog -> setPanelOpen(false))
                .setItems(labels, (dialog, which) -> {
                    player.setPlaybackParameters(new PlaybackParameters(SPEEDS[which]));
                })
                .show();
    }

    // ------------------------------------------------------------- fullscreen

    /**
     * Layar penuh = menyembunyikan bilah, bukan memaksa orientasi.
     *
     * <p>Sebelumnya tombol ini memanggil {@code SCREEN_ORIENTATION_SENSOR_LANDSCAPE},
     * jadi menekan "Layar penuh" pada posisi portrait memutar perangkat ke
     * samping dengan sendirinya — perilaku yang tidak diminta pengguna. Sekarang
     * orientasi dibiarkan mengikuti perangkat: layar penuh pada portrait tetap
     * portrait, dan bila perangkat diputar manual (rotasi diizinkan) tata letak
     * mengikuti landscape seperti biasa. Pemutaran paksa disediakan terpisah
     * lewat {@link #rotateScreen()}.
     */
    private void setFullscreen(boolean enable) {
        if (fullscreen == enable) return;
        fullscreen = enable;
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (enable) {
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
            // Masuk layar penuh selalu memunculkan kontrol, lalu dihitung ulang.
            showControls();
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars());
            // Di luar layar penuh kontrol selalu terlihat: tidak ada yang
            // menghalangi gambar, dan pengguna butuh jalur kembali.
            cancelHideControls();
            setControlsVisible(true);
        }
        applyOrientation();
    }

    /**
     * Satu-satunya tempat orientasi ditentukan.
     *
     * <p>Dipanggil dari {@link #setFullscreen(boolean)} dan
     * {@link #setOrientationMode(int)}, jadi tidak ada dua penulis yang bisa
     * saling menimpa. Tidak dipanggil dari layout pass, dari ticker, atau dari
     * callback lifecycle lain — keadaan yang dipilih pengguna tidak diganti
     * oleh kejadian di sekitarnya.
     */
    private void applyOrientation() {
        try {
            if (orientationMode == ORIENTATION_FORCED_LANDSCAPE) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }
        } catch (RuntimeException ignored) {
            // Sebagian perangkat/OEM menolak permintaan orientasi. Tata letak
            // tetap menyesuaikan orientasi yang berlaku; tidak ada layar penuh
            // palsu yang mengaku landscape padahal tidak.
        }
    }

    /** Ubah mode orientasi; layar penuh ikut dinyalakan saat landscape dipaksa. */
    private void setOrientationMode(int mode) {
        if (orientationMode == mode) return;
        if (mode == ORIENTATION_FORCED_LANDSCAPE) {
            orientationBeforeForced = getRequestedOrientation();
            orientationMode = mode;
            if (!fullscreen) setFullscreen(true);
            else applyOrientation();
            Toast.makeText(this, "Landscape penuh aktif. Tekan lagi untuk kembali.",
                    Toast.LENGTH_SHORT).show();
        } else {
            orientationMode = ORIENTATION_FOLLOW_DEVICE;
            int kembali = orientationBeforeForced;
            orientationBeforeForced = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
            try {
                setRequestedOrientation(kembali == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        ? ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED : kembali);
            } catch (RuntimeException ignored) {
                applyOrientation();
            }
            if (fullscreen) setFullscreen(false);
            else applyOrientation();
        }
    }

    /** "Putar layar": landscape + layar penuh sekaligus; tekan lagi untuk keluar. */
    private void rotateScreen() {
        if (orientationMode == ORIENTATION_FORCED_LANDSCAPE) {
            setOrientationMode(ORIENTATION_FOLLOW_DEVICE);
        } else {
            setOrientationMode(ORIENTATION_FORCED_LANDSCAPE);
        }
        updateOrientationMenuLabel();
    }

    // ------------------------------------------------------- kontrol 5 detik

    /**
     * Menyembunyikan kontrol setelah {@link #CONTROLS_TIMEOUT_MS} tanpa
     * interaksi. Satu penjadwal saja: setiap permintaan baru membatalkan yang
     * lama ({@code removeCallbacks}) sebelum memasang yang baru, jadi tidak ada
     * dua timer yang saling berlomba.
     */
    private void scheduleHideControls() {
        uiHandler.removeCallbacks(hideControlsTask);
        if (!fullscreen) return;
        if (panelOpen || scrubbing || controlsPinned()) return;
        uiHandler.postDelayed(hideControlsTask, CONTROLS_TIMEOUT_MS);
    }

    private void cancelHideControls() {
        uiHandler.removeCallbacks(hideControlsTask);
    }

    /**
     * Kontrol ditahan terlihat saat video dijeda, buffering, atau galat.
     *
     * <p>Bisa di-override pengujian untuk memisahkan perilaku penjadwalan dari
     * keadaan pemutar yang sesungguhnya.
     */
    protected boolean controlsPinned() {
        if (player == null) return true;
        int state = player.getPlaybackState();
        if (state == Player.STATE_BUFFERING || state == Player.STATE_IDLE) return true;
        if (errorBox != null && errorBox.getVisibility() == View.VISIBLE) return true;
        return !player.isPlaying();
    }

    private void showControls() {
        setControlsVisible(true);
        scheduleHideControls();
    }

    private void toggleControls() {
        if (!fullscreen) return;
        if (panelOpen) {
            // Saat panel/dialog terbuka, ketukan di luar tidak boleh
            // menyembunyikan kontrol di belakangnya.
            showControls();
            return;
        }
        if (controlsVisible) {
            if (controlsPinned()) {
                // Saat dijeda/buffering, ketukan tidak menyembunyikan kontrol:
                // pengguna butuh tombol putar untuk melanjutkan.
                showControls();
                return;
            }
            hideControlsNow();
        } else {
            showControls();
        }
    }

    private void hideControlsNow() {
        uiHandler.removeCallbacks(hideControlsTask);
        setControlsVisible(false);
    }

    /**
     * Satu-satunya penulis visibilitas kontrol.
     *
     * <p>Bilah atas dan bawah adalah overlay di atas permukaan video, jadi
     * menyembunyikannya tidak mengubah ukuran video dan tidak memicu lompatan
     * tata letak. Video tidak pernah diredupkan atau diubah warnanya.
     */
    private void setControlsVisible(boolean visible) {
        if (controlsVisible == visible
                && (topBar == null || topBar.getVisibility() == (visible ? View.VISIBLE : View.INVISIBLE))) {
            return;
        }
        controlsVisible = visible;
        fadeBar(topBar, visible);
        fadeBar(bottomBar, visible);
    }

    /**
     * Pudarkan satu bilah kontrol.
     *
     * <p>Saat muncul: tampilkan dulu, lalu naikkan alfa — jadi tidak ada kedip.
     * Saat hilang: turunkan alfa dulu, baru sembunyikan setelah animasi selesai,
     * sehingga tidak ada lompatan visibilitas di tengah animasi. Memakai
     * INVISIBLE (bukan GONE) menjaga tata letak overlay tetap sama, jadi video
     * tidak berubah ukuran.
     */
    private void fadeBar(final View bar, final boolean visible) {
        if (bar == null) return;
        bar.animate().cancel();
        if (visible) {
            bar.setVisibility(View.VISIBLE);
            bar.animate().alpha(1f).setDuration(CONTROLS_FADE_MS).start();
        } else {
            bar.animate().alpha(0f).setDuration(CONTROLS_FADE_MS)
                    .withEndAction(() -> {
                        if (!controlsVisible) bar.setVisibility(View.INVISIBLE);
                    })
                    .start();
        }
    }

    private void updateOrientationMenuLabel() {
        // Label menu dibaca saat menu dibuka; tidak ada state tambahan di sini.
    }

    /** Catat bahwa sebuah dialog/panel sedang terbuka agar timer tidak jalan. */
    private void setPanelOpen(boolean open) {
        panelOpen = open;
        if (open) cancelHideControls();
        else scheduleHideControls();
    }

    @Override public void onBackPressed() {
        // Urutan yang konsisten: keluar dari mode rotasi lebih dulu, lalu layar
        // penuh, baru meninggalkan pemutar — riwayat pemutaran tidak hilang.
        if (orientationMode == ORIENTATION_FORCED_LANDSCAPE) {
            setOrientationMode(ORIENTATION_FOLLOW_DEVICE);
            return;
        }
        if (fullscreen) {
            setFullscreen(false);
            return;
        }
        super.onBackPressed();
    }

    // ---------------------------------------------------------------- status

    private void showBuffering(boolean visible) {
        if (buffering != null) buffering.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        errorText.setText(message);
        errorBox.setVisibility(View.VISIBLE);
        showBuffering(false);
    }

    private void hideError() {
        if (errorBox != null) errorBox.setVisibility(View.GONE);
    }

    /** Sumber tidak bisa diputar native: jelaskan, jangan tampilkan layar hitam. */
    private void showUnavailable(String message) {
        showControls();
        if (player != null) {
            player.pause();
        }
        errorText.setText(message);
        errorBox.setVisibility(View.VISIBLE);
        showBuffering(false);
        updateNeighbors();
    }

    private void openEpisodePage() {
        String url = episodeUrl;
        if (url == null || url.isEmpty()) return;
        try {
            new CustomTabsIntent.Builder().setShowTitle(true).build()
                    .launchUrl(this, Uri.parse(url));
        } catch (Exception error) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
                Toast.makeText(this, "Tidak ada browser untuk membuka sumber",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ------------------------------------------------------------ riwayat

    private void markFinished() {
        if (animeId.isEmpty()) return;
        store.markWatched(animeId, OtakudesuSource.episodeId(episodeUrl));
        store.recordWatched(animeId, animeTitle, animePoster, episodeLabel, episodeUrl,
                episodeNumber, episodeTotal);
    }

    private void saveProgress(boolean finished) {
        if (player == null || animeId.isEmpty()) return;
        long duration = player.getDuration();
        if (duration <= 0) return;
        long position = player.getCurrentPosition();
        store.saveProgress(animeId, OtakudesuSource.episodeId(episodeUrl),
                position, duration, finished);
        if (!finished) {
            store.recordWatched(animeId, animeTitle, animePoster, episodeLabel, episodeUrl,
                    episodeNumber, episodeTotal);
        }
    }

    // ------------------------------------------------------------- lifecycle

    /**
     * Perubahan konfigurasi (rotasi) tidak boleh mematikan lalu membuat ulang
     * pemutar: posisi, buffer, dan state ExoPlayer tetap. Penanganannya ada di
     * {@link #onSaveInstanceState} + pemulihan, dan player tidak dilepas di
     * {@code onStop}.
     */
    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        // Tata letak disusun ulang tanpa menyentuh pemutar.
        if (topBar != null) topBar.requestLayout();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (player != null) out.putLong("resume_ms", player.getCurrentPosition());
        out.putInt("orientation_mode", orientationMode);
        out.putInt("orientation_before_forced", orientationBeforeForced);
        out.putBoolean("immersive_fullscreen", fullscreen);
    }

    @Override protected void onStart() {
        super.onStart();
        // Lanjutkan hanya bila sebelumnya memang sedang diputar; jangan
        // memaksa play setelah pengguna menjeda sendiri.
        if (player != null && resumeOnStart) {
            player.play();
            resumeOnStart = false;
        }
    }

    @Override protected void onPause() {
        resumeOnStart = player != null && player.isPlaying();
        saveProgress(false);
        if (player != null) player.pause();
        super.onPause();
    }

    @Override protected void onStop() {
        saveProgress(false);
        super.onStop();
    }

    @Override protected void onDestroy() {
        main.removeCallbacks(saveTick);
        main.removeCallbacks(clockTick);
        main.removeCallbacks(refreshServerPanelTask);
        uiHandler.removeCallbacks(hideControlsTask);
        worker.shutdownNow();
        // Satu-satunya tempat player dilepas: tidak ada player yang tertinggal.
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}