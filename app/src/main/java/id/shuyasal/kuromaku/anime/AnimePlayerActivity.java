package id.shuyasal.kuromaku.anime;

import android.app.AlertDialog;
import android.content.Intent;
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
    private static final long SEEK_STEP_MS = 10_000L;
    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};

    private Palette palette;
    private OtakudesuStore store;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

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
    private boolean fullscreen;
    private boolean firstSource = true;
    private long pendingResumeMs;
    private String activeQuality = "Otomatis";
    private String activeSubtitle = "Nonaktif";

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

        buildUi();
        if (state != null) {
            pendingResumeMs = state.getLong("resume_ms", 0L);
            firstSource = false;
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

        qualityButton = chip("Kualitas", "anime_player_quality");
        qualityButton.setOnClickListener(v -> showQualityMenu());
        bar.addView(qualityButton);

        subtitleButton = chip("Subtitle", "anime_player_subtitle");
        subtitleButton.setOnClickListener(v -> showSubtitleMenu());
        bar.addView(subtitleButton);

        Button speed = chip("1x", "anime_player_speed");
        speed.setOnClickListener(v -> showSpeedMenu());
        bar.addView(speed);

        Button full = chip("Layar penuh", "anime_player_fullscreen");
        full.setOnClickListener(v -> setFullscreen(!fullscreen));
        bar.addView(full);
        return bar;
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
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(0xB3000000);

        timeBar = new DefaultTimeBar(this, null);
        timeBar.setTag("anime_player_timeline");
        timeBar.setBackgroundColor(0x00000000);
        timeBar.addListener(new androidx.media3.ui.TimeBar.OnScrubListener() {
            @Override public void onScrubStart(androidx.media3.ui.TimeBar timeBar, long position) {
                scrubbing = true;
            }

            @Override public void onScrubMove(androidx.media3.ui.TimeBar timeBar, long position) {
                pendingScrubMs = position;
                if (clock != null) clock.setText(format(position) + " / " + format(durationMs));
            }

            @Override public void onScrubStop(androidx.media3.ui.TimeBar timeBar, long position,
                                              boolean canceled) {
                scrubbing = false;
                if (player != null) player.seekTo(position);
            }
        });
        column.addView(timeBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 28)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 6));

        previous = chip("‹ Sebelumnya", "anime_player_previous");
        previous.setOnClickListener(v -> step(-1));
        row.addView(previous, new LinearLayout.LayoutParams(0,
                Ui.dp(this, Ui.TOUCH_DP), 1f));

        Button back10 = chip("-10s", "anime_player_back10");
        back10.setOnClickListener(v -> seekBy(-SEEK_STEP_MS));
        row.addView(back10);

        playButton = chip("Putar", "anime_player_play");
        playButton.setOnClickListener(v -> togglePlay());
        row.addView(playButton, new LinearLayout.LayoutParams(0,
                Ui.dp(this, Ui.TOUCH_DP), 1f));

        Button forward10 = chip("+10s", "anime_player_forward10");
        forward10.setOnClickListener(v -> seekBy(SEEK_STEP_MS));
        row.addView(forward10);

        next = chip("Berikutnya ›", "anime_player_next");
        next.setOnClickListener(v -> step(1));
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

        Button retry = Ui.chip(this, "Coba Lagi", palette);
        retry.setTag("anime_player_retry");
        retry.setOnClickListener(v -> {
            hideError();
            if (player == null) loadEpisode(episodeUrl, episodeLabel);
            else player.prepare();
        });
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        retryParams.topMargin = Ui.dp(this, 12);
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
            }

            @Override public void onIsPlayingChanged(boolean playing) {
                updatePlayButton();
            }

            @Override public void onTracksChanged(Tracks tracks) {
                updateTrackButtons();
            }

            @Override public void onPlayerError(androidx.media3.common.PlaybackException error) {
                showError("Video gagal dimuat. Periksa koneksi lalu coba lagi.");
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
            OtakudesuStore.Progress progress = store.progress(animeId,
                    OtakudesuSource.episodeId(url));
            pendingResumeMs = progress == null ? 0L : progress.resumePositionMs();
        }

        worker.execute(() -> {
            OtakudesuPlayback playback = null;
            String failure = null;
            try {
                OtakudesuSource source = new OtakudesuSource();
                playback = OtakudesuMediaResolver.resolve(source::html, url);
            } catch (OtakudesuSource.SourceException error) {
                failure = error.getMessage();
            }
            final OtakudesuPlayback result = playback;
            final String message = failure;
            main.post(() -> {
                if (isFinishing() || request != generation) return;
                showBuffering(false);
                if (message != null) {
                    showUnavailable(message);
                    return;
                }
                if (result == null || !result.playable()) {
                    showUnavailable("Tidak ada berkas video yang bisa diputar aplikasi"
                            + " untuk episode ini."
                            + (result != null && !result.mirrors.isEmpty()
                            ? " Mirror yang tersedia: " + mirrorSummary(result) + "." : "")
                            + " Buka halaman sumber untuk menonton lewat pemutar resminya.");
                    return;
                }
                activePlayback = result;
                play(result.sources.get(0), result.qualities());
            });
        });
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

    private void play(OtakudesuPlayback.Source source, List<String> qualities) {
        ensurePlayer();
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
        main.removeCallbacks(saveTick);
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

    private void showQualityMenu() {
        List<Integer> heights = videoHeights();
        if (heights.isEmpty()) return;
        final List<String> labels = new ArrayList<>();
        labels.add("Otomatis");
        for (Integer height : heights) labels.add(height + "p");

        new AlertDialog.Builder(this)
                .setTitle("Kualitas video")
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

        new AlertDialog.Builder(this)
                .setTitle("Subtitle")
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
        new AlertDialog.Builder(this)
                .setTitle("Kecepatan")
                .setItems(labels, (dialog, which) -> {
                    player.setPlaybackParameters(new PlaybackParameters(SPEEDS[which]));
                })
                .show();
    }

    // ------------------------------------------------------------- fullscreen

    private void setFullscreen(boolean enable) {
        fullscreen = enable;
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (enable) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            topBar.setVisibility(View.GONE);
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            topBar.setVisibility(View.VISIBLE);
            controller.show(WindowInsetsCompat.Type.systemBars());
        }
    }

    @Override public void onBackPressed() {
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

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (player != null) out.putLong("resume_ms", player.getCurrentPosition());
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