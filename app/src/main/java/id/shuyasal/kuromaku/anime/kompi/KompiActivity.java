package id.shuyasal.kuromaku.anime.kompi;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
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
 * Beranda Animekompi: daftar per bagian dengan pencarian.
 *
 * <p>Data datang dari API JSON sumbernya, jadi tidak ada halaman HTML yang
 * diurai. Bila sumber tidak bisa dihubungi, layar menampilkan alasannya dan
 * tombol coba lagi — tidak pernah daftar kosong yang tampak seperti hasil
 * pencarian sah.
 */
public class KompiActivity extends id.shuyasal.kuromaku.ui.TvActivity {

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private Palette palette;
    private KompiSource source;
    private LinearLayout content;
    private ProgressBar spinner;
    private TextView errorText;
    private Button retry;
    private EditText search;
    private ScrollView scroll;
    private String pendingQuery = "";

    /** Sumber dibuat ulang per layar supaya pengujian bisa menggantinya. */
    protected KompiSource createSource() {
        return new KompiSource();
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeManager.applyTheme(this);
        palette = Palette.of(new Prefs(this).resolvedTheme());
        source = createSource();
        buildUi();
        // Riwayat ditaruh lebih dulu: dibaca dari berkas lokal, jadi sudah ada
        // sebelum jawaban jaringan datang — dan tetap ada bila sumber gagal.
        tambahkanRiwayat();
        load("");
    }

    @Override protected void onResume() {
        super.onResume();
        // Kembali dari pemutar: tontonan yang baru dicatat harus langsung
        // tampil tanpa perlu menutup layar.
        segarkanRiwayat();
    }

    /**
     * Perbarui rail riwayat di tempat.
     *
     * <p>Daftar anime yang sedang tampil tidak disentuh: hanya rail riwayat
     * yang diganti, jadi posisi gulir dan hasil pencarian tetap.
     */
    private void segarkanRiwayat() {
        if (content == null) return;
        View lama = content.findViewWithTag("rail_riwayat_kompi");
        View baru = riwayatRail();
        if (lama != null) {
            int posisi = content.indexOfChild(lama);
            content.removeViewAt(posisi);
            if (baru != null) content.addView(baru, posisi);
            return;
        }
        if (baru != null) content.addView(baru, 0);
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------- UI

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), 0);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        // Tombol kembali pindah ke kiri: sebelumnya di kanan, dan yang menggantikan
        // posisinya di kiri justru menu samping. Halaman ini adalah pintu masuk
        // cabang Anime Server 2, jadi kembali menuju halaman awal sumber.
        Button back = Ui.backButton(this, palette);
        back.setTag("tombol_kembali");
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, Ui.TOUCH_DP)));

        TextView title = Ui.label(this, "Anime Server 2", 20, palette.text);
        title.setTypeface(null, Typeface.BOLD);
        title.setTag("judul_anime_server2");
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMargins(Ui.dp(this, 12), 0, 0, 0);
        header.addView(title, titleParams);

        // Menu titik tiga di kanan: Tema tampilan, Genre, Muat ulang — sama
        // seperti Anime Server 1. Menu samping tetap ada lewat tombol ini juga.
        android.widget.ImageButton menu = Ui.iconButton(this,
                id.shuyasal.kuromaku.R.drawable.ic_menu, "Buka menu sumber", palette);
        menu.setTag("section_menu");
        // Hamburger tetap membuka menu sumber (drawer); titik tiga yang membuka
        // menu pengaturan. Dua menu ini berbeda dan tidak boleh ditukar.
        menu.setOnClickListener(view -> KompiNavigation.showDrawer(this, palette));

        // Hasil unduhan: di kiri titik tiga, sesuai permintaan.
        android.widget.ImageButton unduhan = Ui.iconButton(this,
                id.shuyasal.kuromaku.R.drawable.ic_download, "Hasil unduhan", palette);
        unduhan.setTag("kompi_downloads");
        unduhan.setOnClickListener(view -> startActivity(
                new android.content.Intent(this,
                        id.shuyasal.kuromaku.anime.AnimeDownloadsActivity.class)));

        android.widget.ImageButton overflow = Ui.iconButton(this,
                id.shuyasal.kuromaku.R.drawable.ic_more, "Menu lain", palette);
        overflow.setTag("kompi_overflow");
        overflow.setOnClickListener(view -> showOverflow());

        header.addView(menu, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));
        header.addView(unduhan, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));
        header.addView(overflow, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));
        root.addView(header);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams searchRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchRowParams.topMargin = Ui.dp(this, 12);

        search = new EditText(this);
        search.setHint("Cari anime");
        search.setSingleLine(true);
        search.setTextColor(palette.text);
        search.setHintTextColor(palette.muted);
        search.setTextSize(15);
        search.setBackground(Ui.ripple(this, palette.surface, palette.divider, 12, palette.ripple));
        search.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        searchRow.addView(search, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button cari = Ui.chip(this, "Cari", palette);
        cari.setOnClickListener(view -> runSearch());
        LinearLayout.LayoutParams cariParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, Ui.TOUCH_DP));
        cariParams.setMargins(Ui.dp(this, 10), 0, 0, 0);
        searchRow.addView(cari, cariParams);
        root.addView(searchRow, searchRowParams);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable text) {
                String value = text.toString().trim();
                if (value.isEmpty() && !pendingQuery.isEmpty()) {
                    pendingQuery = "";
                    load("");
                }
            }
        });
        search.setOnEditorActionListener((view, action, event) -> {
            runSearch();
            return true;
        });

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
        retry.setOnClickListener(view -> load(pendingQuery));
        root.addView(retry, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, Ui.TOUCH_DP)));

        scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 28));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Status bar transparan: tanpa padding sistem, judul dan tombol kembali
        // naik menutupi jam dan ikon notifikasi. Layar lain (Novel, Manga Server
        // 1-3, Anime Server 1) sudah memakai padding ini; layar ini belum.
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

    private void runSearch() {
        String query = search == null ? "" : search.getText().toString().trim();
        if (query.length() < 2) {
            pendingQuery = "";
            load("");
            return;
        }
        pendingQuery = query;
        load(query);
    }

    // ----------------------------------------------------------------- data

    /** Menu titik tiga: tema, genre, muat ulang, dan menu sumber lain. */
    private void showOverflow() {
        final String[] items = {"Tema tampilan", "Genre", "Muat ulang", "Hasil unduhan",
                "Menu sumber"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Anime Server 2")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) showThemeDialog();
                    else if (which == 1) showGenreSheet();
                    else if (which == 2) reloadHome();
                    else if (which == 3) startActivity(new android.content.Intent(this,
                            id.shuyasal.kuromaku.anime.AnimeDownloadsActivity.class));
                    else KompiNavigation.showDrawer(this, palette);
                })
                .show();
    }

    private void showThemeDialog() {
        final String[] options = {"Terang (Light)", "Gelap (Dark)", "Ikuti sistem (System)"};
        int checked = new Prefs(this).theme();
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Tema Anime Server 2")
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    dialog.dismiss();
                    new Prefs(this).setTheme(which);
                    applyThemeInPlace();
                    android.widget.Toast.makeText(this,
                            "Tema disimpan: " + options[which],
                            android.widget.Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    /**
     * Ganti tema di tempat: layar digambar ulang tanpa memuat ulang data, jadi
     * daftar yang sudah tampil dan kata kunci pencarian tetap ada.
     */
    private void applyThemeInPlace() {
        palette = Palette.of(new Prefs(this).resolvedTheme());
        final int keepScroll = scroll == null ? 0 : scroll.getScrollY();
        buildUi();
        if (keepScroll > 0 && scroll != null) {
            final ScrollView target = scroll;
            target.post(() -> target.scrollTo(0, keepScroll));
        }
        if (searchFieldText != null && search != null) search.setText(searchFieldText);
        load(pendingQuery);
        android.view.View decor = getWindow().getDecorView();
        androidx.core.view.WindowCompat.getInsetsController(getWindow(), decor)
                .setAppearanceLightStatusBars(!palette.darkIcons);
    }

    private String searchFieldText;

    /** Muat ulang beranda tanpa menutup layar. */
    private void reloadHome() {
        pendingQuery = "";
        searchFieldText = "";
        if (search != null) search.setText("");
        content.removeAllViews();
        load("");
    }

    // --------------------------------------------------------------- genre

    private void showGenreSheet() {
        showOverflowGenre(null);
    }

    /**
     * Daftar genre dari sumber. Dimuat sekali per pembukaan menu; bila gagal,
     * kegagalannya ditampilkan apa adanya — tidak ada daftar genre karangan.
     */
    private void showOverflowGenre(android.app.ProgressDialog unused) {
        final android.app.ProgressDialog progress =
                android.app.ProgressDialog.show(this, null, "Memuat genre…", true, false);
        worker.execute(() -> {
            List<KompiSource.Genre> list = null;
            String error = null;
            try {
                list = source.genres();
            } catch (KompiSource.SourceException failure) {
                error = failure.getMessage();
            }
            final List<KompiSource.Genre> genres = list;
            final String message = error;
            main.post(() -> {
                progress.dismiss();
                if (isFinishing()) return;
                if (message != null) {
                    errorText.setText(message);
                    errorText.setVisibility(View.VISIBLE);
                    return;
                }
                if (genres == null || genres.isEmpty()) {
                    errorText.setText("Sumber tidak menyediakan daftar genre.");
                    errorText.setVisibility(View.VISIBLE);
                    return;
                }
                String[] names = new String[genres.size()];
                for (int i = 0; i < genres.size(); i++) names[i] = genres.get(i).name;
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Genre")
                        .setItems(names, (dialog, which) -> loadGenre(genres.get(which)))
                        .show();
            });
        });
    }

    /** Tampilkan anime satu genre; halaman berikutnya dimuat saat dugulir. */
    private void loadGenre(KompiSource.Genre genre) {
        spinner.setVisibility(View.VISIBLE);
        errorText.setVisibility(View.GONE);
        retry.setVisibility(View.GONE);
        content.removeAllViews();
        worker.execute(() -> {
            List<KompiSource.Item> items = null;
            String error = null;
            try {
                items = source.byGenre(genre.slug, 1);
            } catch (KompiSource.SourceException failure) {
                error = failure.getMessage();
            }
            final List<KompiSource.Item> loaded = items;
            final String message = error;
            main.post(() -> {
                if (isFinishing()) return;
                spinner.setVisibility(View.GONE);
                if (message != null) {
                    showError(message);
                    return;
                }
                if (loaded == null || loaded.isEmpty()) {
                    errorText.setText("Belum ada anime untuk genre " + genre.name + ".");
                    errorText.setVisibility(View.VISIBLE);
                    return;
                }
                content.addView(sectionTitle("Genre: " + genre.name));
                content.addView(grid(loaded));
                TvFocus.prepare(content, palette);
            });
        });
    }

    private void load(String query) {
        spinner.setVisibility(View.VISIBLE);
        errorText.setVisibility(View.GONE);
        retry.setVisibility(View.GONE);
        if (content.getChildCount() == 0) content.removeAllViews();

        worker.execute(() -> {
            try {
                if (query.isEmpty()) {
                    List<KompiSource.Section> sections = source.home();
                    // Riwayat dibaca di thread utama: jaringan tidak terlibat, jadi rail
                    // tetap tampil walaupun sumber sedang tidak bisa dihubungi.
                    main.post(() -> showSections(sections));
                } else {
                    List<KompiSource.Item> items = source.search(query);
                    main.post(() -> showSearch(query, items));
                }
            } catch (KompiSource.SourceException error) {
                String message = error.getMessage() == null
                        ? "Sumber tidak dapat dihubungi." : error.getMessage();
                main.post(() -> showError(message));
            }
        });
    }

    private void showSections(List<KompiSource.Section> sections) {
        if (isFinishing()) return;
        spinner.setVisibility(View.GONE);
        content.removeAllViews();
        tambahkanRiwayat();
        for (KompiSource.Section section : sections) {
            content.addView(sectionTitle(section.name));
            content.addView(rail(section.items));
        }
        TvFocus.prepare(content, palette);
        if (TvMode.isTelevision(this)) {
            View first = TvFocus.firstFocusable(content);
            if (first != null) first.requestFocus();
        }
    }

    private void showSearch(String query, List<KompiSource.Item> items) {
        if (isFinishing()) return;
        spinner.setVisibility(View.GONE);
        content.removeAllViews();
        tambahkanRiwayat();
        if (items.isEmpty()) {
            errorText.setText("Tidak ada anime yang cocok dengan \"" + query + "\".");
            errorText.setVisibility(View.VISIBLE);
            return;
        }
        content.addView(sectionTitle("Hasil pencarian: " + query));
        content.addView(grid(items));
        TvFocus.prepare(content, palette);
        if (TvMode.isTelevision(this)) {
            View first = TvFocus.firstFocusable(content);
            if (first != null) first.requestFocus();
        }
    }

    private void showError(String message) {
        if (isFinishing()) return;
        spinner.setVisibility(View.GONE);
        // Riwayat tetap ditampilkan walau sumber sedang tidak bisa dihubungi:
        // riwayat dibaca dari berkas lokal, bukan dari jaringan.
        content.removeAllViews();
        tambahkanRiwayat();
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
        retry.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------- tampilan

    private TextView sectionTitle(String text) {
        TextView view = Ui.label(this, text, 17, palette.text);
        view.setTypeface(null, Typeface.BOLD);
        view.setPadding(Ui.dp(this, 4), Ui.dp(this, 18), Ui.dp(this, 4), Ui.dp(this, 10));
        return view;
    }

    /** Satu baris mendatar — nyaman digulir dengan remote maupun sentuhan. */
    private View rail(List<KompiSource.Item> items) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        int width = Ui.dp(this, TvMode.isTelevision(this) ? 150 : 128);
        for (KompiSource.Item item : items) {
            row.addView(card(item, width));
        }
        scroll.addView(row);
        return scroll;
    }

    /**
     * "Lanjutkan Menonton" dari riwayat Anime Server 2.
     *
     * <p>Rail ini tidak ada sebelumnya: halaman ini tidak mencatat apa pun saat
     * episode ditonton, jadi tidak ada riwayat yang bisa ditampilkan. Sekarang
     * pemutar mencatatnya, dan rail muncul hanya bila riwayatnya berisi.
     */
    /**
     * Sisipkan rail riwayat ke konten yang baru digambar.
     *
     * <p>Dipanggil di setiap keadaan — beranda, hasil pencarian, dan galat —
     * karena riwayat berasal dari berkas lokal; sumber yang sedang tidak bisa
     * dihubungi tidak boleh menyembunyikan apa yang sudah pernah ditonton.
     */
    private void tambahkanRiwayat() {
        View rail = riwayatRail();
        if (rail != null) content.addView(rail);
    }

    private View riwayatRail() {
        List<KompiHistory.Entry> entries = KompiHistory.all(this);
        if (entries.isEmpty()) return null;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setTag("rail_riwayat_kompi");

        TextView label = Ui.label(this, "Lanjutkan Menonton", 17, palette.text);
        label.setTypeface(null, Typeface.BOLD);
        label.setPadding(Ui.dp(this, 4), Ui.dp(this, 18), Ui.dp(this, 4), Ui.dp(this, 10));
        box.addView(label);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        int width = Ui.dp(this, TvMode.isTelevision(this) ? 150 : 128);
        for (KompiHistory.Entry entry : entries) {
            row.addView(kartuRiwayat(entry, width));
        }
        scroll.addView(row);
        box.addView(scroll);
        return box;
    }

    private View kartuRiwayat(KompiHistory.Entry entry, int width) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 10));
        card.setFocusable(true);
        card.setClickable(true);
        card.setContentDescription(entry.title);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(Ui.ripple(this, palette.surface, 0, 12, palette.ripple));
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(poster, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        card.addView(frame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 178)));
        if (!entry.poster.isEmpty()) {
            Glide.with(this).load(entry.poster).centerCrop().into(poster);
        }

        TextView title = Ui.label(this, entry.title, 13, palette.text);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 2), 0);
        card.addView(title);

        TextView ep = Ui.label(this, entry.episodeText(), 11, palette.muted);
        ep.setMaxLines(1);
        ep.setPadding(Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2), 0);
        card.addView(ep);

        card.setOnClickListener(view -> {
            // Episode terakhir yang sudah ditonton dibuka lagi, bukan beranda.
            Intent intent = new Intent(this, KompiDetailActivity.class);
            intent.putExtra(KompiDetailActivity.EXTRA_SLUG, entry.slug);
            intent.putExtra(KompiDetailActivity.EXTRA_TITLE, entry.title);
            intent.putExtra(KompiDetailActivity.EXTRA_POSTER, entry.poster);
            startActivity(intent);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                width, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, Ui.dp(this, 8), 0);
        card.setLayoutParams(params);
        TvFocus.focusable(card, palette);
        return card;
    }

    /** Hasil pencarian memakai kisi, bukan baris mendatar. */
    private View grid(List<KompiSource.Item> items) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int kolom = TvMode.isTelevision(this) ? 5 : 3;
        int lebar = Ui.dp(this, 118);
        LinearLayout baris = null;
        for (int i = 0; i < items.size(); i++) {
            if (i % kolom == 0) {
                baris = new LinearLayout(this);
                baris.setOrientation(LinearLayout.HORIZONTAL);
                box.addView(baris);
            }
            baris.addView(card(items.get(i), lebar));
        }
        return box;
    }

    private View card(KompiSource.Item item, int width) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 10));
        card.setFocusable(true);
        card.setClickable(true);
        card.setContentDescription(item.title);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(Ui.ripple(this, palette.surface, 0, 12, palette.ripple));
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(poster, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        card.addView(frame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 178)));
        if (!item.cover.isEmpty()) {
            Glide.with(this).load(item.cover).centerCrop().into(poster);
        }

        TextView title = Ui.label(this, item.title, 13, palette.text);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 2), 0);
        card.addView(title);

        if (!item.note.isEmpty()) {
            TextView note = Ui.label(this, item.note, 11, palette.muted);
            note.setMaxLines(1);
            note.setPadding(Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2), 0);
            card.addView(note);
        }

        card.setOnClickListener(view -> open(item));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                width, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, Ui.dp(this, 8), 0);
        card.setLayoutParams(params);
        TvFocus.focusable(card, palette);
        return card;
    }

    private void open(KompiSource.Item item) {
        Intent intent = new Intent(this, KompiDetailActivity.class);
        intent.putExtra(KompiDetailActivity.EXTRA_SLUG, item.slug);
        intent.putExtra(KompiDetailActivity.EXTRA_TITLE, item.title);
        intent.putExtra(KompiDetailActivity.EXTRA_COVER, item.cover);
        startActivity(intent);
    }
}
