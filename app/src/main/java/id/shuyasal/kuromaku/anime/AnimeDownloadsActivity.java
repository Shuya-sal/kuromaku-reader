package id.shuyasal.kuromaku.anime;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import id.shuyasal.kuromaku.R;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.ThemeManager;
import id.shuyasal.kuromaku.ui.Ui;

/**
 * Perpustakaan unduhan anime.
 *
 * <p>Isi halaman dibangun dari {@link AnimeDownloads} — berkas media yang
 * benar-benar ada dan lolos pemeriksaan di penyimpanan aplikasi — bukan dari
 * status WorkManager. Karena itu halaman ini tetap benar tanpa internet dan
 * tanpa proses latar hidup: episode yang tampil memang bisa diputar.
 *
 * <p>Data anime sengaja terpisah dari unduhan manga Shinigami: kelas ini hanya
 * membaca folder {@code anime-downloads} dan tidak pernah menyentuh halaman
 * manga. Menghapus unduhan tidak menyentuh riwayat/progres menonton di
 * {@link OtakudesuStore}.
 */
public class AnimeDownloadsActivity extends id.shuyasal.kuromaku.ui.TvActivity {

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<AnimeDownloads.Anime> items = new ArrayList<>();
    private final Set<String> expanded = new HashSet<>();

    private Palette palette;
    private OtakudesuStore store;
    private LinearLayout content;
    private TextView summary;
    private LinearLayout emptyBox;
    /** Bagian "Sedang diunduh" di atas daftar berkas; diperbarui tanpa rebuild. */
    private LinearLayout activeSection;
    /** Pendengar status: memperbarui bagian aktif saat persentase berubah. */
    private final AnimeDownloadStatus.Listener activeListener = this::renderActive;
    private int generation;
    private int activeResolvedTheme = -1;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeManager.applyTheme(this);
        activeResolvedTheme = new Prefs(this).resolvedTheme();
        palette = Palette.of(activeResolvedTheme);
        store = new OtakudesuStore(this);
        buildUi();
        scan();
    }

    @Override protected void onStart() {
        super.onStart();
        AnimeDownloadStatus.addListener(activeListener);
    }

    @Override protected void onStop() {
        AnimeDownloadStatus.removeListener(activeListener);
        super.onStop();
    }

    @Override protected void onResume() {
        super.onResume();
        int current = new Prefs(this).resolvedTheme();
        if (activeResolvedTheme != -1 && current != activeResolvedTheme) {
            recreate();
            return;
        }
        scan();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        header.setBackgroundColor(palette.surface);

        ImageButton back = Ui.iconButton(this, R.drawable.ic_arrow_back,
                "Kembali", palette);
        back.setTag("anime_downloads_back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));

        TextView title = Ui.label(this, "Unduhan Anime", 18, palette.text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setTag("anime_downloads_title");
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleParams.leftMargin = Ui.dp(this, 8);
        header.addView(title, titleParams);
        root.addView(header);

        summary = Ui.label(this, "", 12, palette.muted);
        summary.setTag("anime_downloads_summary");
        summary.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 4));
        root.addView(summary);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 24));

        emptyBox = new LinearLayout(this);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER);
        emptyBox.setPadding(Ui.dp(this, 28), Ui.dp(this, 56), Ui.dp(this, 28), Ui.dp(this, 28));
        emptyBox.setTag("anime_downloads_empty");
        TextView emptyTitle = Ui.label(this, "Belum ada episode yang diunduh", 17, palette.text);
        emptyTitle.setGravity(Gravity.CENTER);
        emptyBox.addView(emptyTitle);
        TextView emptyBody = Ui.label(this,
                "Unduh episode dari halaman anime atau dari pemutar. Episode yang tersimpan "
                        + "bisa ditonton tanpa internet.", 13, palette.muted);
        emptyBody.setGravity(Gravity.CENTER);
        emptyBody.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 12));
        emptyBox.addView(emptyBody);
        TextView backToCatalog = Ui.chip(this, "Kembali ke katalog", palette);
        backToCatalog.setTag("anime_downloads_empty_action");
        backToCatalog.setOnClickListener(v -> finish());
        emptyBox.addView(backToCatalog);

        content.addView(emptyBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Bagian unduhan berjalan dibuat sekarang juga: ia tidak bergantung pada
        // pemindaian berkas, jadi unduhan yang sedang berjalan langsung terlihat.
        activeSection = new LinearLayout(this);
        activeSection.setOrientation(LinearLayout.VERTICAL);
        activeSection.setTag("anime_downloads_active");
        content.addView(activeSection, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        renderActive();
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        setContentView(root);
        WindowCompat.getInsetsController(getWindow(), root)
                .setAppearanceLightStatusBars(!palette.darkIcons);
    }

    private void scan() {
        final int current = ++generation;
        summary.setText("Memeriksa berkas…");
        worker.execute(() -> {
            List<AnimeDownloads.Anime> found = AnimeDownloads.list(this);
            main.post(() -> {
                if (isFinishing() || isDestroyed() || current != generation) return;
                items.clear();
                items.addAll(found);
                render();
            });
        });
    }

    private void render() {
        // Dua anak pertama tetap: kotak kosong dan bagian unduhan berjalan.
        while (content.getChildCount() > 2) content.removeViewAt(2);
        renderActive();

        emptyBox.setVisibility(items.isEmpty() && AnimeDownloadStatus.active().isEmpty()
                ? View.VISIBLE : View.GONE);

        int episodes = 0;
        long bytes = 0L;
        for (AnimeDownloads.Anime anime : items) {
            episodes += anime.count();
            bytes += anime.bytes();
        }
        int sedang = AnimeDownloadStatus.active().size();
        if (items.isEmpty() && sedang == 0) {
            summary.setText("Belum ada episode yang diunduh");
        } else if (items.isEmpty()) {
            summary.setText(sedang + " unduhan sedang berjalan");
        } else {
            summary.setText(items.size() + " anime · " + episodes + " episode · "
                    + AnimeDownload.size(bytes) + (sedang == 0 ? ""
                    : " · " + sedang + " berjalan"));
        }

        for (AnimeDownloads.Anime anime : items) content.addView(animeCard(anime));
    }

    /**
     * Susun ulang bagian "Sedang diunduh".
     *
     * <p>Hanya bagian ini yang dibangun ulang saat status berubah; kartu anime
     * yang sudah tersimpan tidak disentuh, jadi daftar tidak berkedip dan posisi
     * gulir tidak melompat.
     */
    private void renderActive() {
        if (activeSection == null) return;
        activeSection.removeAllViews();
        java.util.List<AnimeDownloadStatus.Active> aktif = AnimeDownloadStatus.active();
        activeSection.setVisibility(aktif.isEmpty() ? View.GONE : View.VISIBLE);
        if (aktif.isEmpty()) return;

        TextView judul = Ui.label(this, "Sedang diunduh", 13, palette.muted);
        judul.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 6));
        activeSection.addView(judul);

        for (AnimeDownloadStatus.Active item : aktif) {
            activeSection.addView(activeRow(item));
        }
    }

    /** Satu baris unduhan yang sedang berjalan, dengan persentase dan tombol Batal. */
    private View activeRow(final AnimeDownloadStatus.Active item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        card.setBackground(Ui.rounded(palette.surface, palette.divider, 14, this));
        card.setTag("anime_downloads_active_" + item.episodeKey);

        LinearLayout baris = new LinearLayout(this);
        baris.setOrientation(LinearLayout.HORIZONTAL);
        baris.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout teks = new LinearLayout(this);
        teks.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams teksParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);

        String nama = item.title.isEmpty() ? "Anime" : item.title;
        TextView judul = Ui.label(this, nama, 14, palette.text);
        judul.setTypeface(Typeface.DEFAULT_BOLD);
        judul.setMaxLines(1);
        judul.setEllipsize(TextUtils.TruncateAt.END);
        teks.addView(judul);

        String ket = item.label();
        TextView sub = Ui.label(this, ket, 12, palette.muted);
        sub.setMaxLines(1);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        sub.setPadding(0, Ui.dp(this, 2), 0, 0);
        teks.addView(sub);

        baris.addView(teks, teksParams);

        TextView persen = Ui.label(this, statusTeks(item), 12, palette.text);
        persen.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        baris.addView(persen);

        ImageButton batal = Ui.iconButton(this, R.drawable.ic_close,
                "Batalkan unduhan episode", palette);
        batal.setTag("anime_downloads_cancel_" + item.episodeKey);
        batal.setOnClickListener(v -> {
            AnimeDownloadQueue.cancel(this, item.owner, item.episodeKey);
            render();
        });
        baris.addView(batal, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));

        card.addView(baris);

        android.widget.ProgressBar progress = new android.widget.ProgressBar(
                this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        boolean pasti = item.total > 0 || item.percent > 0;
        progress.setIndeterminate(!pasti);
        if (pasti) progress.setProgress(Math.max(0, Math.min(100, item.percent)));
        progress.setTag("anime_downloads_progress_" + item.episodeKey);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 6));
        progressParams.topMargin = Ui.dp(this, 8);
        card.addView(progress, progressParams);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = Ui.dp(this, 8);
        card.setLayoutParams(cardParams);
        return card;
    }

    /** Persentase bila total diketahui; kalau tidak, pesan yang jujur. */
    private String statusTeks(AnimeDownloadStatus.Active item) {
        if (item.state == AnimeDownloadStatus.FAILED) return "Gagal";
        if (item.state == AnimeDownloadStatus.PAUSED) return "Dijeda";
        if (item.state == AnimeDownloadStatus.QUEUED) return "Menunggu";
        if (item.total <= 0 && item.percent <= 0) return "Menyiapkan…";
        return Math.max(0, Math.min(100, item.percent)) + "%";
    }

    private View animeCard(final AnimeDownloads.Anime anime) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        card.setBackground(Ui.rounded(palette.surface, palette.divider, 16, this));
        card.setTag("anime_downloads_item_" + anime.animeId);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);

        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackgroundColor(palette.background);
        if (!anime.poster.isEmpty()) {
            Glide.with(this).load(anime.poster).centerCrop().into(poster);
        }
        head.addView(poster, new LinearLayout.LayoutParams(Ui.dp(this, 62), Ui.dp(this, 88)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        textParams.leftMargin = Ui.dp(this, 12);

        TextView name = Ui.label(this, anime.title, 15, palette.text);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setTag("anime_downloads_open_" + anime.animeId);
        name.setOnClickListener(v -> toggle(anime.animeId));
        text.addView(name);

        TextView meta = Ui.label(this, anime.count() + " episode · "
                + AnimeDownload.size(anime.bytes()), 12, palette.muted);
        meta.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 6));
        text.addView(meta);

        AnimeDownload next = anime.continueEpisode(store);
        if (next != null) {
            TextView watchText = Ui.label(this, "Lanjutkan menonton", 12, palette.accent);
            watchText.setTag("anime_downloads_continue_" + anime.animeId);
            watchText.setPadding(0, 0, 0, Ui.dp(this, 6));
            watchText.setOnClickListener(v -> play(next));
            text.addView(watchText);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView open = Ui.chip(this, "Episode", palette);
        open.setTag("anime_downloads_episodes_" + anime.animeId);
        open.setMinimumHeight(Ui.dp(this, Ui.TOUCH_DP));
        open.setOnClickListener(v -> toggle(anime.animeId));
        actions.addView(open);

        TextView removeAll = Ui.chip(this, "Hapus semua", palette);
        removeAll.setTag("anime_downloads_remove_all_" + anime.animeId);
        removeAll.setMinimumHeight(Ui.dp(this, Ui.TOUCH_DP));
        removeAll.setOnClickListener(v -> confirmRemoveAnime(anime));
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        removeParams.leftMargin = Ui.dp(this, 8);
        actions.addView(removeAll, removeParams);

        text.addView(actions);
        head.addView(text, textParams);
        card.addView(head);

        if (expanded.contains(anime.animeId)) {
            for (AnimeDownload episode : anime.episodes) card.addView(episodeRow(anime, episode));
        }
        return card;
    }

    private View episodeRow(final AnimeDownloads.Anime anime, final AnimeDownload episode) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 10), 0, 0);
        row.setTag("anime_downloads_episode_" + episode.episodeKey);

        TextView label = Ui.label(this,
                episode.episodeLabel.isEmpty() ? "Episode" : episode.episodeLabel,
                14, palette.text);
        label.setTag("anime_downloads_play_" + episode.episodeKey);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        label.setOnClickListener(v -> play(episode));

        String detail = AnimeDownload.size(episode.bytes);
        if (!episode.quality.isEmpty()) detail = episode.quality + " · " + detail;
        if (!episode.server.isEmpty()) detail = detail + " · " + episode.server;

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(label);
        TextView sub = Ui.label(this, detail, 11, palette.muted);
        column.addView(sub);
        row.addView(column, labelParams);

        TextView delete = Ui.chip(this, "Hapus", palette);
        delete.setTag("anime_downloads_delete_" + episode.episodeKey);
        delete.setMinimumHeight(Ui.dp(this, Ui.TOUCH_DP));
        delete.setOnClickListener(v -> confirmRemoveEpisode(anime, episode));
        row.addView(delete);
        return row;
    }

    private void toggle(String animeId) {
        if (expanded.contains(animeId)) {
            expanded.remove(animeId);
        } else {
            expanded.add(animeId);
        }
        render();
    }

    private void play(AnimeDownload episode) {
        Intent intent = new Intent(this, AnimePlayerActivity.class)
                .putExtra(AnimePlayerActivity.EXTRA_ANIME_ID, episode.animeId)
                .putExtra(AnimePlayerActivity.EXTRA_ANIME_TITLE, episode.animeTitle)
                .putExtra(AnimePlayerActivity.EXTRA_ANIME_POSTER, episode.poster)
                .putExtra(AnimePlayerActivity.EXTRA_EPISODE_URL, episode.episodeUrl)
                .putExtra(AnimePlayerActivity.EXTRA_EPISODE_LABEL, episode.episodeLabel)
                .putExtra(AnimePlayerActivity.EXTRA_OFFLINE_ONLY, true);
        startActivity(intent);
    }

    private void confirmRemoveEpisode(final AnimeDownloads.Anime anime,
                                      final AnimeDownload episode) {
        new AlertDialog.Builder(this)
                .setTitle("Hapus unduhan")
                .setMessage((episode.episodeLabel.isEmpty() ? "Episode ini" : episode.episodeLabel)
                        + " akan dihapus dari perangkat. Riwayat dan progres menonton tetap "
                        + "tersimpan.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus", (dialog, which) -> {
                    boolean removed = AnimeDownloads.delete(this, anime.animeId,
                            episode.episodeKey);
                    AnimeDownloadStatus.clear(episode.episodeKey);
                    Toast.makeText(this, removed
                            ? "Unduhan dihapus. Progres menonton tetap tersimpan."
                            : "Berkas tidak ditemukan; daftar diperbarui.", Toast.LENGTH_SHORT)
                            .show();
                    scan();
                })
                .show();
    }

    private void confirmRemoveAnime(final AnimeDownloads.Anime anime) {
        new AlertDialog.Builder(this)
                .setTitle("Hapus semua unduhan")
                .setMessage("Seluruh episode " + anime.title + " yang tersimpan akan dihapus. "
                        + "Riwayat dan progres menonton tetap tersimpan.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus semua", (dialog, which) -> {
                    AnimeDownloads.deleteAnime(this, anime.animeId);
                    for (AnimeDownload episode : anime.episodes) {
                        AnimeDownloadStatus.clear(episode.episodeKey);
                    }
                    Toast.makeText(this, "Unduhan dihapus. Progres menonton tetap tersimpan.",
                            Toast.LENGTH_SHORT).show();
                    scan();
                })
                .show();
    }
}