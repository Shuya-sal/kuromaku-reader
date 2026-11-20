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
 * Detail anime dari Animekompi: poster, keterangan, sinopsis, dan daftar
 * episode. Menekan satu episode membuka pemutar aplikasi, bukan peramban.
 */
public class KompiDetailActivity extends id.shuyasal.kuromaku.ui.TvActivity {

    public static final String EXTRA_SLUG = "slug";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_COVER = "cover";
    /** Alias untuk penelepon yang memakai nama poster. */
    public static final String EXTRA_POSTER = "cover";

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

        // Status bar transparan: tanpa padding sistem, tombol kembali dan judul
        // anime naik ke area jam. Halaman ini juga menampilkan poster di atas,
        // jadi padding samping tetap dipertahankan di dalam listener.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                            | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            v.setPadding(Ui.dp(this, 16) + bars.left, Ui.dp(this, 14) + bars.top,
                    Ui.dp(this, 16) + bars.right, bars.bottom);
            return insets;
        });
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
        KompiHistory.Entry riwayat = KompiHistory.forSlug(this, slug);
        for (KompiSource.Episode episode : detail.episodes) {
            body.addView(barisEpisod(detail, episode, riwayat));
        }

        TvFocus.prepare(body, palette);
        if (TvMode.isTelevision(this)) {
            View first = TvFocus.firstFocusable(body);
            if (first != null) first.requestFocus();
        }
    }

    /**
     * Baris episode dengan penanda episode terakhir yang ditonton.
     *
     * <p>Tanda ini datang dari riwayat yang nyata, bukan tanda yang ditebak dari
     * nomor episode terbesar.
     */
    private View barisEpisod(KompiSource.Detail detail, KompiSource.Episode episode,
                             KompiHistory.Entry riwayat) {
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

        if (riwayat != null && episode.slug.equals(riwayat.episodeSlug)) {
            TextView tanda = Ui.label(this, "\u2713 Ditonton", 12, palette.accent);
            tanda.setPadding(Ui.dp(this, 8), 0, 0, 0);
            baris.addView(tanda);
        }

        // Unduhan per episode: tombolnya membuka pemutar episode itu lebih dulu,
        // karena berkas media baru diketahui setelah server episode diperiksa.
        Button unduh = Ui.chip(this, "Unduh", palette);
        unduh.setTag("unduh_" + episode.slug);
        unduh.setContentDescription("Unduh " + episode.name);
        unduh.setOnClickListener(view -> unduhEpisode(detail, episode));
        LinearLayout.LayoutParams unduhParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, Ui.TOUCH_DP));
        unduhParams.setMargins(Ui.dp(this, 8), 0, 0, 0);
        baris.addView(unduh, unduhParams);

        // Episode yang berkasnya sudah tersimpan ditandai, bukan tombolnya
        // dibiarkan seolah belum pernah diunduh.
        if (sudahTersimpan(episode)) {
            TextView tersimpan = Ui.label(this, "\u2713 Tersimpan", 12, palette.accent);
            tersimpan.setPadding(Ui.dp(this, 8), 0, 0, 0);
            baris.addView(tersimpan);
        }

        baris.setOnClickListener(view -> bukaPemutar(detail, episode));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(this, 8);
        baris.setLayoutParams(params);
        TvFocus.focusable(baris, palette);
        return baris;
    }

    /**
     * Episode ini sudah ada di perangkat?
     *
     * <p>Kunci unduhan memakai alamat episode ({@code episode.slug}) dan label,
     * sama dengan yang dipakai antrean unduhan, sehingga tanda "Tersimpan"
     * hanya muncul untuk episode yang benar-benar ada — bukan tebakan.
     */
    private boolean sudahTersimpan(KompiSource.Episode episode) {
        String key = id.shuyasal.kuromaku.anime.AnimeDownloads.episodeKey(
                episode.slug, episode.name);
        return id.shuyasal.kuromaku.anime.AnimeDownloads.isSaved(this, slug, key);
    }

    /**
     * Unduh satu episode langsung dari daftar episode.
     *
     * <p>Alurnya tidak membuka pemutar: server episode diperiksa di latar
     * belakang, berkas media pertama yang benar-benar tersedia dipilih, lalu
     * unduhan diantrekan. Bila episode ini hanya punya halaman pemutar,
     * pengguna diberi alasannya — tidak ada unduhan palsu.
     */
    private void unduhEpisode(KompiSource.Detail detail, KompiSource.Episode episode) {
        // Persetujuan unduhan diminta di sini, seperti di Anime Server 1.
        // Tanpa ini antrean menolak setiap permintaan dan tombol Unduh tidak
        // pernah menghasilkan berkas — persis keluhan "belum bisa download".
        if (!id.shuyasal.kuromaku.anime.AnimeDownloadPolicy.allowed(this)) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Unduhan offline")
                    .setMessage(id.shuyasal.kuromaku.anime.AnimeDownloadPolicy.notice())
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Setuju", (dialog, which) -> {
                        id.shuyasal.kuromaku.anime.AnimeDownloadPolicy.acknowledge(this);
                        unduhEpisode(detail, episode);
                    })
                    .show();
            return;
        }
        final String labelEp = episode.name;
        final String kunci = id.shuyasal.kuromaku.anime.AnimeDownloads.episodeKey(
                episode.slug, episode.name);
        if (id.shuyasal.kuromaku.anime.AnimeDownloads.isSaved(this, slug, kunci)) {
            android.widget.Toast.makeText(this,
                    "Episode ini sudah tersimpan di perangkat.",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        android.widget.Toast.makeText(this, "Menyiapkan unduhan " + labelEp + "…",
                android.widget.Toast.LENGTH_SHORT).show();
        worker.execute(() -> {
            List<KompiSource.Download> tautan = null;
            String alasan = null;
            try {
                // Tautan unduhan resmi sumber dipakai lebih dulu: inilah yang
                // benar-benar berupa berkas. Server pemutar di sumber ini
                // kebanyakan halaman embed, jadi mengandalkannya saja membuat
                // tombol Unduh selalu gagal.
                tautan = source.episode(episode.slug).downloads;
            } catch (KompiSource.SourceException error) {
                alasan = error.getMessage();
            }
            final List<KompiSource.Download> resmi = tautan;
            final String pesanGalat = alasan;
            main.post(() -> {
                if (isFinishing()) return;
                if (resmi != null && !resmi.isEmpty()) {
                    pilihKualitas(detail, episode, resmi);
                    return;
                }
                // Sumber tidak menerbitkan tautan unduhan: coba server pemutar.
                cariBerkasDariServer(detail, episode,
                        pesanGalat == null ? "" : pesanGalat);
            });
        });
    }

    /**
     * Pilih kualitas unduhan.
     *
     * <p>Hanya kualitas yang benar-benar diterbitkan sumber yang ditawarkan —
     * tidak ada 1080p karangan bila sumbernya hanya menyediakan 480p.
     */
    private void pilihKualitas(KompiSource.Detail detail, KompiSource.Episode episode,
                               List<KompiSource.Download> tautan) {
        // Satu tautan: langsung jalan, tidak perlu dialog.
        if (tautan.size() == 1) {
            antre(detail, episode, tautan.get(0));
            return;
        }
        String[] label = new String[tautan.size()];
        for (int i = 0; i < tautan.size(); i++) label[i] = tautan.get(i).describe();
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Pilih kualitas · " + episode.name)
                .setItems(label, (dialog, which) -> antre(detail, episode, tautan.get(which)))
                .setNegativeButton("Batal", null)
                .show();
    }

    /** Antrekan satu tautan unduhan resmi. */
    private void antre(KompiSource.Detail detail, KompiSource.Episode episode,
                       KompiSource.Download tautan) {
        String berkas = resolveUnduhan(tautan.url);
        if (berkas == null || berkas.isEmpty()) {
            android.widget.Toast.makeText(this,
                    "Tautan " + tautan.describe() + " bukan berkas yang bisa disimpan.",
                    android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        id.shuyasal.kuromaku.anime.AnimeDownloadTask task =
                new id.shuyasal.kuromaku.anime.AnimeDownloadTask(
                        slug, detail.title, detail.cover, episode.name, episode.slug,
                        tautan.resolution, tautan.label, berkas, "PROGRESSIVE");
        boolean queued = id.shuyasal.kuromaku.anime.AnimeDownloadQueue.enqueue(this, task);
        android.widget.Toast.makeText(this,
                queued ? "Unduhan dimasukkan ke antrean: " + episode.name
                        + (tautan.resolution.isEmpty() ? "" : " · " + tautan.resolution)
                        : "Unduhan tidak dapat dijadwalkan.",
                android.widget.Toast.LENGTH_SHORT).show();
    }

    /**
     * Ubah tautan unduhan sumber menjadi alamat berkas.
     *
     * <p>Hanya Pixeldrain yang memberi berkas sungguhan dari daftar unduhan
     * sumber ini. Penyedia lain (acefile, gofile, mirrored, buzzheavier)
     * menjawab dengan halaman HTML, bukan video — menyimpannya akan menghasilkan
     * berkas rusak yang diklaim sebagai episode. Karena itu tautan seperti itu
     * ditolak, dan layar menawarkan "Tonton di sumber" alih-alih unduhan palsu.
     */
    static String resolveUnduhan(String url) {
        if (url == null || url.isEmpty()) return null;
        String low = url.toLowerCase(java.util.Locale.US);
        if (!low.contains("pixeldrain.")) return null;
        java.util.regex.Matcher pixeldrain =
                java.util.regex.Pattern.compile("/(?:u|api/file)/([A-Za-z0-9]+)").matcher(url);
        return pixeldrain.find()
                ? "https://pixeldrain.com/api/file/" + pixeldrain.group(1) + "?download"
                : null;
    }

    /**
     * Tidak ada berkas yang bisa disimpan: tawarkan menonton di sumber.
     *
     * <p>Jujur soal batas sumbernya — sebagian episode hanya tersedia sebagai
     * pemutar di peramban. Yang ditawarkan adalah jalan yang benar-benar
     * bekerja, bukan tombol unduh yang diam.
     */
    private void cariBerkasDariServer(KompiSource.Detail detail, KompiSource.Episode episode,
                                      String catatan) {
        String alasan = catatan == null || catatan.isEmpty()
                ? "Sumber tidak menerbitkan berkas unduhan untuk episode ini."
                : catatan;
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Unduh tidak tersedia")
                .setMessage(alasan + "\n\n" + episode.name
                        + " masih bisa ditonton lewat server pemutar sumbernya.")
                .setPositiveButton("Tonton di sumber", (dialog, which) ->
                        tontonDiSumber(episode))
                .setNegativeButton("Batal", null)
                .show();
    }

    /** Buka server pemutar pertama episode ini di peramban. */
    private void tontonDiSumber(KompiSource.Episode episode) {
        worker.execute(() -> {
            String page = "";
            try {
                KompiSource.EpisodeInfo info = source.episode(episode.slug);
                if (!info.servers.isEmpty()) page = info.servers.get(0).url;
            } catch (KompiSource.SourceException ignored) {
                page = "";
            }
            final String alamat = page;
            main.post(() -> {
                if (isFinishing() || alamat == null || alamat.isEmpty()) {
                    android.widget.Toast.makeText(this, "Server episode tidak dapat dibuka.",
                            android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                new androidx.browser.customtabs.CustomTabsIntent.Builder()
                        .build()
                        .launchUrl(this, android.net.Uri.parse(alamat));
            });
        });
    }

    private void bukaPemutar(KompiSource.Detail detail, KompiSource.Episode episode) {
        Intent intent = new Intent(this, KompiPlayerActivity.class);
        intent.putExtra(KompiPlayerActivity.EXTRA_SLUG, episode.slug);
        intent.putExtra(KompiPlayerActivity.EXTRA_TITLE, detail.title);
        intent.putExtra(KompiPlayerActivity.EXTRA_EPISODE, episode.name);
        // Riwayat disimpan per anime, bukan per episode: tanpa slug induk,
        // menonton episode 2 akan tampil sebagai judul kedua di riwayat.
        intent.putExtra(KompiPlayerActivity.EXTRA_ANIME_SLUG, slug);
        intent.putExtra(KompiPlayerActivity.EXTRA_POSTER, detail.cover);
        intent.putExtra(KompiPlayerActivity.EXTRA_EPISODE_NUMBER, episode.number);
        startActivity(intent);
    }
}
