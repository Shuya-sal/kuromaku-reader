package id.shuyasal.kuromaku.anime.kompi;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.browser.customtabs.CustomTabsIntent;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.ThemeManager;
import id.shuyasal.kuromaku.ui.TvFocus;
import id.shuyasal.kuromaku.ui.TvMode;
import id.shuyasal.kuromaku.ui.Ui;

/**
 * Pemutar Animekompi.
 *
 * <p>Hanya server yang benar-benar memberi berkas video yang diputar. Server
 * yang cuma menyediakan halaman pemutar ditolak dengan alasan yang jelas dan
 * tombol menuju halaman resminya — layar hitam tidak pernah ditampilkan
 * sebagai "berhasil".
 *
 * <p>Di televisi: layar penuh sejak awal, kontrol hilang sendiri saat video
 * berjalan dan muncul lagi setiap tombol ditekan; kiri/kanan menggeser 10
 * detik saat kontrol tersembunyi, dan berpindah tombol saat kontrol tampil.
 * Tombol kembali menyembunyikan kontrol dulu, kembali lagi keluar.
 */
public class KompiPlayerActivity extends id.shuyasal.kuromaku.ui.TvActivity {

    public static final String EXTRA_SLUG = "slug";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_EPISODE = "episode";

    private static final long SEMBUNYI_MS = 3200;
    private static final long GESER_MS = 10_000;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private Palette palette;
    private KompiSource source;
    private String slug;
    private String judul;
    private String episode;

    private ExoPlayer player;
    private PlayerView playerView;
    private FrameLayout root;
    private LinearLayout overlay;
    private LinearLayout panelServer;
    private TextView status;
    private ProgressBar spinner;
    private Button tombolPutar;
    private final List<KompiSource.Server> servers = new ArrayList<>();
    private String serverAktif = "";

    private final Runnable sembunyikan = () -> setKontrolTampil(false);

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
        episode = intent.getStringExtra(EXTRA_EPISODE);
        if (slug == null || slug.isEmpty()) {
            finish();
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();
        loadServers();
    }

    @Override protected void onStop() {
        super.onStop();
        if (player != null) player.pause();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        ui.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------------- UI

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(android.graphics.Color.BLACK);

        playerView = new PlayerView(this);
        playerView.setUseController(false);
        root.addView(playerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        spinner = new ProgressBar(this);
        FrameLayout.LayoutParams spinnerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        spinnerParams.gravity = Gravity.CENTER;
        root.addView(spinner, spinnerParams);

        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setBackgroundColor(0xB3000000);
        overlay.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 14));
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        overlayParams.gravity = Gravity.BOTTOM;
        root.addView(overlay, overlayParams);

        TextView judulView = Ui.label(this,
                (judul == null ? "" : judul) + (episode == null ? "" : " · " + episode),
                15, android.graphics.Color.WHITE);
        judulView.setTypeface(null, Typeface.BOLD);
        judulView.setMaxLines(2);
        overlay.addView(judulView);

        status = Ui.label(this, "Menyiapkan…", 13, 0xFFCCCCCC);
        status.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        overlay.addView(status);

        LinearLayout barisTombol = new LinearLayout(this);
        barisTombol.setOrientation(LinearLayout.HORIZONTAL);
        barisTombol.setGravity(Gravity.CENTER_VERTICAL);

        Button kembali = tombol("-10 dtk", () -> geser(-GESER_MS));
        Button putar = tombol("Putar", this::putarAtauJeda);
        tombolPutar = putar;
        Button maju = tombol("+10 dtk", () -> geser(GESER_MS));
        Button daftarServer = tombol("Server", this::tampilkanServer);
        Button keluar = tombol("Keluar", this::finish);
        for (Button tombol : new Button[] {kembali, putar, maju, daftarServer, keluar}) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, Ui.dp(this, Ui.TOUCH_DP), 1f);
            params.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            barisTombol.addView(tombol, params);
        }
        overlay.addView(barisTombol);

        panelServer = new LinearLayout(this);
        panelServer.setOrientation(LinearLayout.VERTICAL);
        panelServer.setVisibility(View.GONE);
        overlay.addView(panelServer);

        root.setOnClickListener(view -> {
            if (overlay.getVisibility() == View.VISIBLE) setKontrolTampil(false);
            else setKontrolTampil(true);
        });

        setContentView(root);
        TvFocus.prepare(root, palette);
    }

    private Button tombol(String teks, Runnable aksi) {
        Button button = Ui.chip(this, teks, palette);
        button.setTextColor(android.graphics.Color.WHITE);
        button.setBackground(Ui.ripple(this, 0x33FFFFFF, 0x66FFFFFF, 12, 0x33FFFFFF));
        button.setOnClickListener(view -> {
            jadwalkanSembunyi();
            aksi.run();
        });
        return button;
    }

    // ---------------------------------------------------------------- sumber

    private void loadServers() {
        worker.execute(() -> {
            try {
                KompiSource.EpisodeInfo info = source.episode(slug);
                main(() -> {
                    servers.clear();
                    servers.addAll(info.servers);
                    spinner.setVisibility(View.GONE);
                    if (!info.title.isEmpty()) judul = info.title;
                    if (servers.isEmpty()) {
                        status.setText("Episode ini tidak menyebut satu pun server.");
                        setKontrolTampil(true);
                        return;
                    }
                    tampilkanServer();
                    // Server berkas pertama dipilih otomatis; halaman pemutar
                    // tidak pernah diputar diam-diam.
                    for (KompiSource.Server server : servers) {
                        if (server.file) {
                            putarServer(server);
                            return;
                        }
                    }
                    status.setText("Semua server di episode ini hanya halaman pemutar. "
                            + "Pilih salah satu untuk membukanya di peramban.");
                    setKontrolTampil(true);
                });
            } catch (KompiSource.SourceException error) {
                String pesan = error.getMessage() == null
                        ? "Daftar server tidak dapat diambil." : error.getMessage();
                main(() -> {
                    spinner.setVisibility(View.GONE);
                    status.setText(pesan);
                    setKontrolTampil(true);
                });
            }
        });
    }

    private void tampilkanServer() {
        panelServer.removeAllViews();
        panelServer.setVisibility(View.VISIBLE);
        setKontrolTampil(true);
        for (KompiSource.Server server : servers) {
            Button button = Ui.chip(this,
                    server.label + (server.file ? " · berkas" : " · halaman pemutar"), palette);
            button.setTextColor(android.graphics.Color.WHITE);
            button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            button.setBackground(Ui.ripple(this, 0x33FFFFFF, 0x66FFFFFF, 12, 0x33FFFFFF));
            boolean aktif = server.url.equals(serverAktif);
            button.setText((aktif ? "● " : "") + button.getText());
            button.setOnClickListener(view -> {
                jadwalkanSembunyi();
                putarServer(server);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, Ui.TOUCH_DP));
            params.topMargin = Ui.dp(this, 6);
            panelServer.addView(button, params);
        }
        TvFocus.prepare(panelServer, palette);
        if (TvMode.isTelevision(this)) {
            View first = TvFocus.firstFocusable(panelServer);
            if (first != null) first.requestFocus();
        }
    }

    private void putarServer(KompiSource.Server server) {
        serverAktif = server.url;
        panelServer.setVisibility(View.GONE);
        spinner.setVisibility(View.VISIBLE);
        status.setText("Memeriksa " + server.label + "…");
        worker.execute(() -> {
            String media = null;
            String alasan = null;
            try {
                media = source.resolve(server.url);
            } catch (KompiSource.SourceException error) {
                alasan = error.getMessage();
            }
            final String alamat = media;
            final String galat = alasan;
            main(() -> {
                spinner.setVisibility(View.GONE);
                if (alamat != null && !alamat.isEmpty()) {
                    play(alamat, KompiSource.refererFor(server.url));
                } else {
                    status.setText(galat != null ? galat
                            : "Server ini hanya menyediakan halaman pemutar, bukan berkas video.");
                    tambahBukaSumber(server);
                    setKontrolTampil(true);
                }
            });
        });
    }

    private void tambahBukaSumber(KompiSource.Server server) {
        // Satu tombol saja, walau beberapa server gagal berturut-turut.
        View lama = overlay.findViewWithTag("buka_sumber");
        if (lama != null) overlay.removeView(lama);
        Button buka = tombol("Buka player sumber", () -> {
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            builder.setShowTitle(true);
            builder.build().launchUrl(this, Uri.parse(server.url));
        });
        buka.setTag("buka_sumber");
        overlay.addView(buka, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, Ui.TOUCH_DP)));
    }

    private void play(String media, String referer) {
        if (player == null) {
            DefaultHttpDataSource.Factory factory = new DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Linux; Android 13) Kuromaku/1.0")
                    .setAllowCrossProtocolRedirects(true)
                    .setDefaultRequestProperties(
                            java.util.Collections.singletonMap("Referer", referer));
            player = new ExoPlayer.Builder(this)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(factory))
                    .build();
            player.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY) {
                        spinner.setVisibility(View.GONE);
                        status.setText("Siap diputar");
                        tombolPutar.setText("Jeda");
                        jadwalkanSembunyi();
                    } else if (state == Player.STATE_BUFFERING) {
                        spinner.setVisibility(View.VISIBLE);
                        status.setText("Memuat video…");
                    } else if (state == Player.STATE_ENDED) {
                        status.setText("Selesai");
                        setKontrolTampil(true);
                    }
                }

                @Override public void onPlayerError(androidx.media3.common.PlaybackException error) {
                    spinner.setVisibility(View.GONE);
                    status.setText("Video gagal diputar (" + error.getErrorCodeName() + "). "
                            + "Coba server lain.");
                    setKontrolTampil(true);
                }

                @Override public void onIsPlayingChanged(boolean playing) {
                    tombolPutar.setText(playing ? "Jeda" : "Putar");
                    if (playing) jadwalkanSembunyi();
                    else ui.removeCallbacks(sembunyikan);
                }
            });
            playerView.setPlayer(player);
        }
        status.setText("Menyiapkan video…");
        player.setMediaItem(MediaItem.fromUri(media));
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private void putarAtauJeda() {
        if (player == null) return;
        if (player.isPlaying()) player.pause();
        else player.play();
    }

    private void geser(long delta) {
        if (player == null) return;
        long target = Math.max(0, Math.min(player.getDuration(), player.getCurrentPosition() + delta));
        player.seekTo(target);
        jadwalkanSembunyi();
    }

    // --------------------------------------------------------------- kontrol

    private void setKontrolTampil(boolean tampil) {
        ui.removeCallbacks(sembunyikan);
        overlay.setVisibility(tampil ? View.VISIBLE : View.GONE);
        if (!tampil && root != null) root.requestFocus();
        if (tampil && TvMode.isTelevision(this) && tombolPutar != null) {
            tombolPutar.requestFocus();
        }
        if (tampil && player != null && player.isPlaying()) jadwalkanSembunyi();
    }

    private void jadwalkanSembunyi() {
        ui.removeCallbacks(sembunyikan);
        if (player != null && player.isPlaying()) {
            ui.postDelayed(sembunyikan, SEMBUNYI_MS);
        }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (overlay.getVisibility() == View.VISIBLE && player != null && player.isPlaying()) {
                setKontrolTampil(false);
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }

        boolean tersembunyi = overlay.getVisibility() != View.VISIBLE;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (tersembunyi) {
                    setKontrolTampil(true);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (tersembunyi) {
                    geser(-GESER_MS);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (tersembunyi) {
                    geser(GESER_MS);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                putarAtauJeda();
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                geser(GESER_MS);
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                geser(-GESER_MS);
                return true;
            default:
                break;
        }
        if (overlay.getVisibility() == View.VISIBLE) jadwalkanSembunyi();
        return super.onKeyDown(keyCode, event);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    private void main(Runnable aksi) {
        if (isFinishing() || isDestroyed()) return;
        ui.post(aksi);
    }
}
