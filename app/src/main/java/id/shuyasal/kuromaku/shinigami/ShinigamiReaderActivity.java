package id.shuyasal.kuromaku.shinigami;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.ThemeManager;
import id.shuyasal.kuromaku.ui.Ui;

public class ShinigamiReaderActivity extends AppCompatActivity {

    public static final String EXTRA_MANGA_ID = "manga_id";
    public static final String EXTRA_MANGA_TITLE = "manga_title";
    public static final String EXTRA_MANGA_COVER = "manga_cover";
    public static final String EXTRA_CHAPTER_ID = "chapter_id";
    public static final String EXTRA_CHAPTER_NUMBER = "chapter_number";
    public static final String EXTRA_CHAPTER_TITLE = "chapter_title";

    private static final String STATE_NOTICE_SHOWN = "reader_notice_shown";
    private static final int MENU_QUALITY = 1;
    private static final int MENU_FULLSCREEN = 2;
    private static final int MENU_RELOAD = 3;
    private static final int MENU_MAIN = 4;
    /** Lama notifikasi offline terlihat (maksimal 3 detik). */
    private static final long NOTICE_MS = 3000L;
    private int activeResolvedTheme = -1;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    /** Satu thread khusus menyiapkan gambar berikutnya; tidak menahan halaman aktif. */
    private final ExecutorService prefetchPool = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<String> pages = new ArrayList<>();

    private Palette palette;
    private ShinigamiSource source;
    private ShinigamiStore store;
    private ShinigamiDownloader downloader;

    private String mangaId;
    private String mangaTitle;
    private String mangaCover;
    private String chapterId;
    private double chapterNumber;
    private String chapterTitle;

    private RecyclerView list;
    private PageAdapter adapter;
    private LinearLayout bar;
    private TextView statusText;
    private TextView errorText;
    private Button retryButton;
    private TextView offlineNotice;
    private TextView titleView;
    /** Navigasi chapter sebelumnya/berikutnya menurut urutan cerita. */
    private LinearLayout chapterNav;
    private android.widget.ImageButton prevChapterButton;
    private android.widget.ImageButton nextChapterButton;
    private TextView navLabel;
    private View fullscreenTapStrip;
    private final List<ShinigamiChapter> storyOrder = new ArrayList<>();
    private ShinigamiChapter currentChapter;
    private ShinigamiChapter previousChapter;
    private ShinigamiChapter nextChapter;
    /** Menahan ketukan berulang saat perpindahan chapter sedang berjalan. */
    private boolean switching;
    private int indexGeneration;
    private ShinigamiPageCache cache;
    private ShinigamiQuality quality = ShinigamiQuality.AUTO;
    private boolean fullscreen;
    private boolean offlineChapter;
    private boolean progressReliable = true;
    private boolean positionReady;
    /** Notifikasi offline hanya sekali per pembukaan chapter. */
    private boolean noticeShown;
    private final Runnable hideNotice = this::fadeOutNotice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.applyTheme(this);
        activeResolvedTheme = new Prefs(this).resolvedTheme();
        palette = Palette.of(activeResolvedTheme);
        source = createSource();
        store = new ShinigamiStore(this);
        downloader = new ShinigamiDownloader(this);
        cache = new ShinigamiPageCache(this);
        quality = ShinigamiQuality.stored(this);
        ShinigamiImageRequest.register(this);

        noticeShown = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_NOTICE_SHOWN, false);

        mangaId = getIntent().getStringExtra(EXTRA_MANGA_ID);
        mangaTitle = getIntent().getStringExtra(EXTRA_MANGA_TITLE);
        mangaCover = getIntent().getStringExtra(EXTRA_MANGA_COVER);
        chapterId = getIntent().getStringExtra(EXTRA_CHAPTER_ID);
        chapterNumber = getIntent().getDoubleExtra(EXTRA_CHAPTER_NUMBER, 0d);
        chapterTitle = getIntent().getStringExtra(EXTRA_CHAPTER_TITLE);

        currentChapter = new ShinigamiChapter(chapterId == null ? "" : chapterId, mangaId,
                chapterNumber, chapterTitle, "", "");

        if (chapterId == null || chapterId.isEmpty()) {
            Toast.makeText(this, "Chapter tidak ditemukan.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        buildUi();
        loadPages();
        loadChapterIndex();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);

        bar = buildBar();
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout body = new FrameLayout(this);
        list = new RecyclerView(this);
        list.setTag("shinigami_reader_list");
        list.setBackgroundColor(palette.background);
        adapter = new PageAdapter();
        list.setAdapter(adapter);
        LinearLayoutManager manager = new LinearLayoutManager(this);
        list.setLayoutManager(manager);
        // Tanpa animator item: halaman tidak pernah "muncul" dengan animasi yang
        // menggeser posisi, dan tidak ada invalidate tambahan saat scroll.
        list.setItemAnimator(null);
        list.setHasFixedSize(true);
        list.setItemViewCacheSize(3);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView view, int dx, int dy) {
                saveProgress();
            }
        });
        body.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        statusText = Ui.label(this, "Memuat halaman...", 13, 0xFFCCCCCC);
        statusText.setGravity(Gravity.CENTER);
        statusText.setTag("shinigami_reader_status");
        statusText.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        body.addView(statusText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        errorText = Ui.label(this, "", 13, 0xFFFF8080);
        errorText.setGravity(Gravity.CENTER);
        errorText.setTag("shinigami_reader_error");
        errorText.setVisibility(View.GONE);
        errorText.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        body.addView(errorText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        retryButton = Ui.chip(this, "Coba lagi", palette);
        retryButton.setTag("shinigami_reader_retry");
        retryButton.setVisibility(View.GONE);
        retryButton.setOnClickListener(v -> loadPages());
        body.addView(retryButton, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        // Overlay kecil di bawah: tidak menyisakan ruang baca dan tidak
        // menutupi bagian atas gambar.
        offlineNotice = Ui.label(this, "", 12, 0xFFFFE082);
        offlineNotice.setTag("shinigami_reader_offline_notice");
        offlineNotice.setVisibility(View.GONE);
        offlineNotice.setMaxLines(3);
        offlineNotice.setPadding(Ui.dp(this, 14), Ui.dp(this, 9), Ui.dp(this, 14), Ui.dp(this, 9));
        offlineNotice.setBackground(Ui.rounded(0xE6202020, 0x33FFFFFF, 16, this));
        FrameLayout.LayoutParams noticeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        noticeParams.setMargins(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 28));
        body.addView(offlineNotice, noticeParams);

        // Strip tipis di dasar layar: hanya aktif saat layar penuh, supaya
        // ketukan pada gambar tetap membuka zoom seperti sebelumnya.
        fullscreenTapStrip = new View(this);
        fullscreenTapStrip.setTag("shinigami_reader_tap_strip");
        fullscreenTapStrip.setVisibility(View.GONE);
        fullscreenTapStrip.setOnClickListener(v -> toggleFullscreen());
        body.addView(fullscreenTapStrip, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 56), Gravity.BOTTOM));

        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        chapterNav = buildChapterNav();
        root.addView(chapterNav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                            | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    /**
     * Toolbar responsif: empat aksi 48 dp + judul yang menyusut, tanpa satu pun
     * tombol yang perlu digeser. Pengaturan kualitas pindah ke menu overflow.
     */
    private LinearLayout buildBar() {
        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        bar.setTag("shinigami_reader_bar");
        // Warna toolbar mengikuti tema; mode terang tetap kontras untuk ikon.
        bar.setBackgroundColor(palette.surface);

        android.widget.ImageButton back = Ui.iconButton(this,
                id.shuyasal.kuromaku.R.drawable.ic_arrow_back, "Kembali", palette);
        back.setTag("shinigami_reader_back");
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));

        titleView = Ui.label(this, shortTitle(), 15, palette.text);
        titleView.setTag("shinigami_reader_title");
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 2), 0);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleParams.weight = 1f;
        bar.addView(titleView, titleParams);

        android.widget.ImageButton chapters = Ui.iconButton(this,
                id.shuyasal.kuromaku.R.drawable.ic_list, "Daftar chapter", palette);
        chapters.setTag("shinigami_reader_chapters");
        chapters.setOnClickListener(this::showChapterList);
        bar.addView(chapters, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));

        android.widget.ImageButton zoom = Ui.iconButton(this,
                id.shuyasal.kuromaku.R.drawable.ic_fullscreen, "Layar penuh", palette);
        zoom.setTag("shinigami_reader_fullscreen");
        zoom.setOnClickListener(v -> toggleFullscreen());
        bar.addView(zoom, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));

        android.widget.ImageButton more = Ui.iconButton(this,
                id.shuyasal.kuromaku.R.drawable.ic_more, "Menu lainnya", palette);
        more.setTag("shinigami_reader_more");
        more.setOnClickListener(this::showOverflow);
        bar.addView(more, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));
        return bar;
    }

    /** "Ch. 399" — sengaja pendek supaya tidak mendorong tombol keluar layar. */
    private String shortTitle() {
        if (chapterNumber > 0) {
            return "Ch. " + ShinigamiManga.formatNumber(chapterNumber);
        }
        return chapterTitle == null || chapterTitle.isEmpty() ? "Chapter" : chapterTitle;
    }

    private void showOverflow(View anchor) {
        android.widget.PopupMenu menu = new android.widget.PopupMenu(this, anchor);
        menu.getMenu().add(0, MENU_QUALITY, 0, "Kualitas: " + quality.label);
        menu.getMenu().add(0, MENU_FULLSCREEN, 1,
                fullscreen ? "Keluar layar penuh" : "Layar penuh");
        menu.getMenu().add(0, MENU_RELOAD, 2, "Muat ulang chapter");
        menu.getMenu().add(0, MENU_MAIN, 3, "Buka menu utama");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case MENU_QUALITY:
                    cycleQuality(null);
                    return true;
                case MENU_FULLSCREEN:
                    toggleFullscreen();
                    return true;
                case MENU_RELOAD:
                    pages.clear();
                    adapter.notifyDataSetChanged();
                    loadPages();
                    return true;
                case MENU_MAIN:
                    ShinigamiNavigation.showDrawer(this, palette, false);
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    /** Daftar chapter dari sumber; kegagalan jaringan dilaporkan apa adanya. */
    private void showChapterList(View anchor) {
        Toast.makeText(this, "Memuat daftar chapter...", Toast.LENGTH_SHORT).show();
        worker.execute(() -> {
            List<ShinigamiChapter> found = new ArrayList<>();
            String failure = null;
            try {
                ShinigamiSource.ChapterPage page = source.chapters(mangaId, 1);
                if (page != null && page.items != null) found.addAll(page.items);
            } catch (ShinigamiSource.SourceException error) {
                failure = error.getMessage();
            }
            final List<ShinigamiChapter> items = found;
            final String message = failure;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (items.isEmpty()) {
                    Toast.makeText(this, message == null || message.isEmpty()
                            ? "Daftar chapter tidak tersedia saat offline."
                            : message, Toast.LENGTH_LONG).show();
                    return;
                }
                String[] labels = new String[items.size()];
                for (int index = 0; index < items.size(); index++) {
                    labels[index] = ShinigamiManga.chapterLabel(
                            items.get(index).number, items.get(index).title);
                }
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Daftar chapter")
                        .setItems(labels, (dialog, which) -> openChapter(items.get(which)))
                        .setNegativeButton("Tutup", null)
                        .show();
            });
        });
    }

    /** Pindah chapter tanpa menumpuk activity: pakai ulang activity ini. */
    private void openChapter(ShinigamiChapter chapter) {
        Intent next = new Intent(this, getClass());
        next.putExtra(EXTRA_MANGA_ID, mangaId);
        next.putExtra(EXTRA_MANGA_TITLE, mangaTitle);
        next.putExtra(EXTRA_MANGA_COVER, mangaCover);
        next.putExtra(EXTRA_CHAPTER_ID, chapter.id);
        next.putExtra(EXTRA_CHAPTER_NUMBER, chapter.number);
        next.putExtra(EXTRA_CHAPTER_TITLE, chapter.title);
        next.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(next);
        overridePendingTransition(0, 0);
        finish();
    }

    private void cycleQuality(View anchor) {
        ShinigamiQuality[] modes = ShinigamiQuality.values();
        String[] items = new String[modes.length];
        int checked = 0;
        for (int index = 0; index < modes.length; index++) {
            items[index] = modes[index].label;
            if (modes[index] == quality) checked = index;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Kualitas gambar")
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    quality = modes[which];
                    quality.save(this);
                    Toast.makeText(this, quality.summary(pageSourceWidth()), Toast.LENGTH_LONG).show();
                    if (adapter != null) adapter.notifyDataSetChanged();
                    dialog.dismiss();
                })
                .setNegativeButton("Tutup", null)
                .show();
    }

    /** Lebar sumber yang diketahui, untuk teks pengaturan kualitas. */
    private int pageSourceWidth() {
        if (pages.isEmpty() || offlineChapter) return 800;
        File local = downloader.pageFile(chapterId, 0);
        int[] size = ShinigamiPageTiles.bounds(local);
        return size == null ? 800 : size[0];
    }

    private void toggleFullscreen() {
        fullscreen = !fullscreen;
        bar.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        if (chapterNav != null) chapterNav.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        if (fullscreenTapStrip != null) {
            fullscreenTapStrip.setVisibility(fullscreen ? View.VISIBLE : View.GONE);
        }
        if (fullscreen) {
            Toast.makeText(this, "Layar penuh. Ketuk bagian bawah layar untuk menampilkan kontrol.",
                    Toast.LENGTH_SHORT).show();
        }
        if (fullscreen) {
            androidx.core.view.WindowInsetsControllerCompat controller =
                    androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            androidx.core.view.WindowInsetsControllerCompat controller =
                    androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars());
        }
    }

    /**
     * Bar navigasi bawah: [←] judul chapter aktif [→].
     *
     * <p>Toolbar atas sudah memuat lima aksi 48 dp; menambah dua tombol lagi di
     * sana menyisakan ruang judul hampir nol pada layar 360 dp. Bar bawah tidak
     * menutupi gambar karena mengambil ruang tata letak sendiri, dan ikut
     * tersembunyi saat layar penuh.
     */
    private LinearLayout buildChapterNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setTag("shinigami_reader_nav");
        nav.setBackgroundColor(palette.surface);
        nav.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));

        prevChapterButton = Ui.iconButton(this,
                id.shuyasal.kuromaku.R.drawable.ic_chevron_left, "Chapter sebelumnya", palette);
        prevChapterButton.setTag("shinigami_reader_prev");
        prevChapterButton.setOnClickListener(v -> navigateTo(previousChapter));
        nav.addView(prevChapterButton, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));

        navLabel = Ui.label(this, shortTitle(), 12, palette.muted);
        navLabel.setTag("shinigami_reader_nav_label");
        navLabel.setGravity(Gravity.CENTER);
        navLabel.setSingleLine(true);
        navLabel.setEllipsize(TextUtils.TruncateAt.END);
        nav.addView(navLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        nextChapterButton = Ui.iconButton(this,
                id.shuyasal.kuromaku.R.drawable.ic_chevron_right, "Chapter berikutnya", palette);
        nextChapterButton.setTag("shinigami_reader_next");
        nextChapterButton.setOnClickListener(v -> navigateTo(nextChapter));
        nav.addView(nextChapterButton, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));

        updateNavButtons();
        return nav;
    }

    /**
     * Indeks chapter untuk panah kiri/kanan.
     *
     * <p>Diambil dari seluruh chapter manga pada sumber (bukan halaman yang
     * sedang dimuat), lalu diurutkan menurut alur cerita. Saat offline, urutan
     * disusun dari chapter yang tersimpan di perangkat.
     */
    private void loadChapterIndex() {
        final int generation = ++indexGeneration;
        final boolean online = hasInternet();
        worker.execute(() -> {
            List<ShinigamiChapter> ordered = new ArrayList<>();
            if (online) {
                try {
                    ordered.addAll(source.chaptersAll(mangaId));
                } catch (ShinigamiSource.SourceException ignored) {
                    // Jaringan gagal di tengah jalan: pakai chapter offline.
                }
            }
            if (ordered.isEmpty()) ordered.addAll(offlineChapters());
            final List<ShinigamiChapter> result = ordered;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (generation != indexGeneration) return;
                storyOrder.clear();
                storyOrder.addAll(result);
                previousChapter = ShinigamiChapterOrder.previous(storyOrder, currentChapter);
                nextChapter = ShinigamiChapterOrder.next(storyOrder, currentChapter);
                updateNavButtons();
            });
        });
    }

    /** Chapter yang benar-benar ada di penyimpanan, urut cerita. */
    private List<ShinigamiChapter> offlineChapters() {
        List<ShinigamiChapter> local = new ArrayList<>();
        ShinigamiDownloads.Manga stored = ShinigamiDownloads.forManga(this, mangaId);
        if (stored == null) return local;
        for (ShinigamiDownloads.Chapter chapter : stored.chapters) {
            local.add(new ShinigamiChapter(chapter.chapterId, mangaId, chapter.number,
                    chapter.label, "", ""));
        }
        java.util.Collections.sort(local, ShinigamiChapterOrder.ASC);
        return local;
    }

    private void updateNavButtons() {
        if (prevChapterButton == null || nextChapterButton == null) return;
        boolean canPrevious = previousChapter != null && !switching;
        boolean canNext = nextChapter != null && !switching;
        prevChapterButton.setEnabled(canPrevious);
        prevChapterButton.setAlpha(canPrevious ? 1f : 0.35f);
        prevChapterButton.setContentDescription(previousChapter == null
                ? "Tidak ada chapter sebelumnya" : previousChapter.label());
        nextChapterButton.setEnabled(canNext);
        nextChapterButton.setAlpha(canNext ? 1f : 0.35f);
        nextChapterButton.setContentDescription(nextChapter == null
                ? "Tidak ada chapter berikutnya" : nextChapter.label());
        if (navLabel != null) {
            int index = ShinigamiChapterOrder.indexOf(storyOrder, chapterId);
            String position = index < 0 ? "" : " · " + (index + 1) + "/" + storyOrder.size();
            String title = currentChapter == null ? shortTitle() : currentChapter.label();
            navLabel.setText(title + position);
        }
    }

    /**
     * Pindah ke chapter tujuan bila bisa dibuka; kalau tidak, halaman yang
     * sedang dibaca tidak disentuh sama sekali.
     */
    private void navigateTo(ShinigamiChapter target) {
        if (target == null || switching) return;
        switching = true;
        updateNavButtons();
        // Progres chapter lama disimpan sebelum activity ini selesai.
        saveProgress();
        worker.execute(() -> {
            final boolean ready = isChapterAvailable(target);
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (!ready) {
                    switching = false;
                    updateNavButtons();
                    Toast.makeText(this, "Chapter " + ShinigamiManga.formatNumber(target.number)
                            + " belum tersimpan offline dan tidak ada koneksi internet. "
                            + "Halaman yang sedang dibaca tidak diubah.", Toast.LENGTH_LONG).show();
                    return;
                }
                openChapter(target);
            });
        });
    }

    /** Chapter bisa dibuka bila tersimpan offline atau perangkat online. */
    private boolean isChapterAvailable(ShinigamiChapter chapter) {
        int expected = store.expectedPages(chapter.id);
        if (expected > 0 && downloader.isDownloaded(chapter.id, expected)) return true;
        return hasInternet();
    }

    /** Bisa dioverride di tes agar jalur online/offline bisa diuji tanpa jaringan. */
    protected boolean hasInternet() {
        try {
            android.net.ConnectivityManager manager = (android.net.ConnectivityManager)
                    getSystemService(CONNECTIVITY_SERVICE);
            if (manager == null) return true;
            android.net.Network network = manager.getActiveNetwork();
            if (network == null) return false;
            android.net.NetworkCapabilities caps = manager.getNetworkCapabilities(network);
            return caps != null && caps.hasCapability(
                    android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private void loadPages() {
        statusText.setVisibility(View.VISIBLE);
        errorText.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);

        int expected = expectedPages();
        if (expected > 0 && downloader.isDownloaded(chapterId, expected)) {
            showOfflineChapter(expected);
            return;
        }
        offlineChapter = false;
        worker.execute(() -> {
            List<String> urls = new ArrayList<>();
            String failure = null;
            boolean usedCache = false;
            try {
                urls.addAll(source.chapterImages(chapterId).urls);
            } catch (ShinigamiSource.SourceException error) {
                failure = error.getCause() instanceof java.io.IOException
                        ? null
                        : error.getMessage();
                if (failure == null) {
                    // Only connectivity failures may reuse the prior, validated page order.
                    // Access-denied/CAPTCHA responses are never worked around.
                    urls.addAll(store.chapterUrls(chapterId));
                    usedCache = !urls.isEmpty();
                }
            }
            final List<String> result = urls;
            final String message = failure;
            final boolean cached = usedCache;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (message != null) {
                    statusText.setVisibility(View.GONE);
                    errorText.setText(message);
                    errorText.setVisibility(View.VISIBLE);
                    retryButton.setVisibility(View.VISIBLE);
                    return;
                }
                if (result.isEmpty()) {
                    statusText.setVisibility(View.GONE);
                    errorText.setText(offlineMessage());
                    errorText.setVisibility(View.VISIBLE);
                    retryButton.setVisibility(View.VISIBLE);
                    return;
                }
                pages.clear();
                pages.addAll(result);
                adapter.notifyDataSetChanged();
                statusText.setVisibility(View.GONE);
                progressReliable = !cached;
                if (cached) {
                    showNoticeOnce("Koneksi bermasalah. Chapter ini belum tersimpan lengkap, "
                            + "jadi sebagian gambar mungkin belum bisa dibuka offline.");
                }
                recordOpen();
                restorePosition();
            });
        });
    }

    private int expectedPages() {
        int expected = store.expectedPages(chapterId);
        if (expected <= 0) expected = store.chapterUrls(chapterId).size();
        return expected;
    }

    private void showOfflineChapter(int expected) {
        pages.clear();
        for (int index = 0; index < expected; index++) pages.add("");
        offlineChapter = true;
        statusText.setVisibility(View.GONE);
        errorText.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        showNoticeOnce("Dibaca dari penyimpanan offline. Tidak perlu koneksi internet.");
        progressReliable = true;
        adapter.notifyDataSetChanged();
        recordOpen();
        restorePosition();
    }

    private static String offlineMessage() {
        return "Chapter ini belum tersimpan lengkap dan koneksi internet tidak tersedia.\n\n"
                + "Unduh chapter saat online untuk membacanya offline. Progres membaca tetap "
                + "tersimpan, jadi posisi terakhir tidak hilang.";
    }

    private void recordOpen() {
        store.recordRead(mangaId, mangaTitle, mangaCover, chapterId, chapterNumber);
        store.setLastChapter(mangaId, chapterId, chapterNumber);
    }

    private void restorePosition() {
        int page = store.page(chapterId);
        if (page <= 0) {
            positionReady = true;
            return;
        }
        if (page >= pages.size()) return;
        list.scrollToPosition(page);
        positionReady = true;
    }

    private void saveProgress() {
        if (pages.isEmpty() || !positionReady) return;
        LinearLayoutManager manager = (LinearLayoutManager) list.getLayoutManager();
        if (manager == null) return;
        int first = manager.findFirstVisibleItemPosition();
        if (first < 0) return;
        store.setPage(chapterId, first);
        if (!progressReliable) return;
        int percent = (int) Math.round(((first + 1) * 100.0) / pages.size());
        store.setProgress(chapterId, percent);
    }

    /** Tampilkan sekali per pembukaan chapter, lalu hilang sendiri. */
    private void showNoticeOnce(String text) {
        if (offlineNotice == null) return;
        if (noticeShown) {
            if (offlineNotice.getVisibility() != View.VISIBLE) offlineNotice.setVisibility(View.GONE);
            return;
        }
        noticeShown = true;
        offlineNotice.setText(text);
        offlineNotice.animate().cancel();
        offlineNotice.setAlpha(1f);
        offlineNotice.setVisibility(View.VISIBLE);
        // Satu timer saja: timer lama dibatalkan supaya tidak ada fade ganda.
        main.removeCallbacks(hideNotice);
        main.postDelayed(hideNotice, NOTICE_MS);
    }

    private void fadeOutNotice() {
        if (offlineNotice == null || offlineNotice.getVisibility() != View.VISIBLE) return;
        offlineNotice.animate().alpha(0f).setDuration(220)
                .withEndAction(() -> {
                    if (offlineNotice != null) offlineNotice.setVisibility(View.GONE);
                })
                .start();
    }

    /** Timer & animasi dibatalkan saat activity berhenti agar tidak bocor. */
    private void cancelNotice() {
        main.removeCallbacks(hideNotice);
        if (offlineNotice != null) offlineNotice.animate().cancel();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_NOTICE_SHOWN, noticeShown);
    }

    protected ShinigamiSource createSource() {
        return new ShinigamiSource(this);
    }

    @Override
    public void onBackPressed() {
        if (fullscreen) {
            toggleFullscreen();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        int currentResolved = new Prefs(this).resolvedTheme();
        if (activeResolvedTheme != -1 && currentResolved != activeResolvedTheme) {
            recreate();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveProgress();
        // Timer/animasi notifikasi dibatalkan saat activity tidak terlihat.
        cancelNotice();
    }

    @Override
    protected void onDestroy() {
        cancelNotice();
        worker.shutdownNow();
        prefetchPool.shutdownNow();
        super.onDestroy();
    }

    private final class PageAdapter extends RecyclerView.Adapter<PageAdapter.Holder> {

        PageAdapter() {
            setHasStableIds(true);
        }

        @Override public long getItemId(int position) {
            return ((long) chapterId.hashCode() << 32) ^ position;
        }
        class Holder extends RecyclerView.ViewHolder {
            final ShinigamiPageView cell;
            int bound = -1;
            int generation;
            Holder(ShinigamiPageView item) { super(item); cell = item; }
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            ShinigamiPageView cell = new ShinigamiPageView(ShinigamiReaderActivity.this);
            cell.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new Holder(cell);
        }

        @Override public void onBindViewHolder(Holder holder, int position) {
            holder.bound = position;
            holder.cell.setRetryAction(() -> {
                if (holder.bound >= 0 && holder.bound < pages.size()) load(holder);
            });
            holder.cell.setZoomRequest(new ShinigamiPageView.ZoomRequest() {
                @Override public void onZoom(int factor, float focusY) {
                    if (holder.bound >= 0 && holder.bound < pages.size()) {
                        openZoomed(holder.bound, factor, focusY);
                    }
                }

                @Override public void onSingleTap(float focusY) {
                    if (holder.bound >= 0 && holder.bound < pages.size()) {
                        openZoomed(holder.bound, 1f, focusY);
                    }
                }
            });
            // Tinggi placeholder memakai rasio yang pernah terukur untuk halaman
            // ini, jadi daftar tidak melompat saat gambar selesai dimuat.
            double known = store.pageRatio(chapterId, position);
            if (known > 0) holder.cell.setPlaceholderRatio(known);
            load(holder);
        }

        /**
         * Halaman dibaca dari berkas: unduhan offline bila ada, kalau tidak
         * dari cache halaman (diunduh sekali, bukan per tampilan). Tidak ada
         * decode seluruh halaman, jadi lebar asli sumber tidak dikorbankan.
         */
        private void load(Holder holder) {
            holder.cell.showLoading();
            final int position = holder.bound;
            if (position < 0 || position >= pages.size()) return;
            final int generation = ++holder.generation;

            File local = downloader.pageFile(chapterId, position);
            if (local != null) {
                holder.cell.showFile(local, quality);
                rememberRatio(position, local);
                prefetch(position + 1);
                return;
            }
            if (offlineChapter) {
                holder.cell.showFailure("Halaman " + (position + 1)
                        + " tidak tersedia lagi di penyimpanan. Unduh ulang chapter ini.");
                return;
            }
            String url = pages.get(position);
            if (url == null || url.isEmpty()) {
                holder.cell.showFailure("Alamat gambar halaman " + (position + 1) + " tidak dikenal.");
                return;
            }
            // Perbaikan gambar hitam: cache halaman (online) dicek sebelum jaringan,
            // jadi menggulir balik tidak pernah memicu unduhan ulang.
            File cached = cache.file(url);
            if (cached != null) {
                holder.cell.showFile(cached, quality);
                rememberRatio(position, cached);
                prefetch(position + 1);
                return;
            }
            final String referer = source.config().siteBase() + "/";
            worker.execute(() -> {
                File file = null;
                String failure = null;
                try {
                    file = cache.fetch(url, referer);
                } catch (java.io.IOException error) {
                    failure = error.getMessage() == null ? "Gambar gagal dimuat." : error.getMessage();
                }
                final File ready = file;
                final String reason = failure;
                main.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (holder.bound != position || holder.generation != generation) return;
                    if (ready != null) {
                        holder.cell.showFile(ready, quality);
                        rememberRatio(position, ready);
                        prefetch(position + 1);
                        int[] size = ShinigamiPageTiles.bounds(ready);
                        android.util.Log.i("ShinigamiReader", "page " + (position + 1) + "/" + pages.size()
                                + " source=" + (size == null ? "?" : size[0] + "x" + size[1])
                                + " quality=" + quality.name());
                    } else {
                        holder.cell.showFailure("Halaman " + (position + 1) + ": " + reason);
                    }
                });
            });
        }

        /**
         * Simpan rasio halaman supaya kunjungan berikutnya memakai placeholder
         * setinggi gambar aslinya (tanpa pergeseran layout).
         */
        private void rememberRatio(int position, File file) {
            int[] size = ShinigamiPageTiles.bounds(file);
            if (size == null || size[0] <= 0) return;
            double ratio = (double) size[1] / size[0];
            if (store.pageRatio(chapterId, position) <= 0) {
                store.setPageRatio(chapterId, position, ratio);
            }
        }

        /**
         * Siapkan gambar berikutnya di thread terpisah.
         *
         * <p>Hanya satu halaman ke depan: cukup untuk membuat scroll terasa
         * mulus, tanpa menumpuk unduhan atau memakan RAM berlebihan.
         */
        private void prefetch(int position) {
            if (offlineChapter || position < 0 || position >= pages.size()) return;
            final String url = pages.get(position);
            if (url == null || url.isEmpty()) return;
            // Pemeriksaan cache dilakukan di dalam pool: jangan menyentuh disk
            // dari main thread hanya untuk memutuskan prefetch.
            final String referer = source.config().siteBase() + "/";
            prefetchPool.execute(() -> {
                try {
                    cache.fetch(url, referer);
                } catch (java.io.IOException | RuntimeException ignored) {
                    // Prefetch bersifat peluang; kegagalan tidak mengubah tampilan.
                }
            });
        }

        @Override public void onViewRecycled(Holder holder) {
            // Perbaikan gambar hitam: state dibersihkan agar rebinding selalu memuat
            // ulang dari berkas (offline/cache), bukan mewarisi tampilan holder lama.
            holder.generation++;
            holder.bound = -1;
            // Bitmap dan decoder di TiledSurface sengaja TIDAK dibersihkan di sini;
            // showLoading() menutup permukaan, dan load() berikutnya membuka ulang
            // decoder dari berkas yang sama (murah, file-based).
            super.onViewRecycled(holder);
        }
        @Override public int getItemCount() { return pages.size(); }
    }

    private void openZoomed(int position) {
        openZoomed(position, 1f, 0f);
    }

    private void openZoomed(int position, float factor, float focusY) {
        if (position < 0 || position >= pages.size()) return;
        Intent intent = new Intent(this, ShinigamiImageZoomActivity.class);
        intent.putExtra("url", pages.get(position));
        intent.putExtra("chapter_id", chapterId);
        intent.putExtra("page", position);
        intent.putExtra("zoom_factor", factor);
        intent.putExtra("focus_y", focusY);
        intent.putExtra("focus_x", 0.5f);
        startActivity(intent);
    }
}
