package id.shuyasal.kuromaku.shinigami;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
 * Daftar unduhan Manga Shinigami.
 *
 * <p>Isi halaman berasal dari {@link ShinigamiDownloads} yang memindai berkas
 * nyata dan memvalidasi setiap halaman — bukan status WorkManager. Menghapus
 * unduhan hanya menghapus berkas halaman; progres membaca, riwayat, dan favorit
 * tidak disentuh.
 */
public class ShinigamiDownloadsActivity extends AppCompatActivity {

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<ShinigamiDownloads.Manga> items = new ArrayList<>();

    private Palette palette;
    private ShinigamiStore store;
    private LinearLayout content;
    private TextView summary;
    private TextView emptyTitle;
    private TextView emptyBody;
    private LinearLayout emptyBox;
    private int generation;
    private int activeResolvedTheme = -1;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeManager.applyTheme(this);
        activeResolvedTheme = new Prefs(this).resolvedTheme();
        palette = Palette.of(activeResolvedTheme);
        store = new ShinigamiStore(this);
        buildUi();
        scan();
    }

    @Override protected void onResume() {
        super.onResume();
        int currentResolved = new Prefs(this).resolvedTheme();
        if (activeResolvedTheme != -1 && currentResolved != activeResolvedTheme) {
            recreate();
            return;
        }
        scan();
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

        android.widget.ImageButton back = Ui.iconButton(this, R.drawable.ic_arrow_back,
                getString(R.string.shinigami_downloads_cancel), palette);
        back.setTag("downloads_back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.TOUCH_DP), Ui.dp(this, Ui.TOUCH_DP)));

        TextView title = Ui.label(this, getString(R.string.shinigami_downloads_title), 18, palette.text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleParams.leftMargin = Ui.dp(this, 8);
        header.addView(title, titleParams);
        root.addView(header);

        summary = Ui.label(this, "", 12, palette.muted);
        summary.setTag("downloads_summary");
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
        emptyBox.setVisibility(View.GONE);
        emptyBox.setTag("downloads_empty");
        emptyTitle = Ui.label(this, getString(R.string.shinigami_downloads_empty_title), 17, palette.text);
        emptyTitle.setGravity(Gravity.CENTER);
        emptyBox.addView(emptyTitle);
        emptyBody = Ui.label(this, getString(R.string.shinigami_downloads_empty_body), 13, palette.muted);
        emptyBody.setGravity(Gravity.CENTER);
        emptyBody.setPadding(0, Ui.dp(this, 8), 0, 0);
        emptyBox.addView(emptyBody);

        content.addView(emptyBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
        // Setelah setContentView: decor view sudah ada.
        WindowCompat.getInsetsController(getWindow(), root)
                .setAppearanceLightStatusBars(!palette.darkIcons);
    }

    private void scan() {
        final int current = ++generation;
        summary.setText(R.string.shinigami_downloads_scanning);
        worker.execute(() -> {
            List<ShinigamiDownloads.Manga> found = ShinigamiDownloads.scan(this);
            long bytes = ShinigamiDownloads.totalBytes(found);
            main.post(() -> {
                if (isFinishing() || isDestroyed() || current != generation) return;
                items.clear();
                items.addAll(found);
                render(bytes);
            });
        });
    }

    private void render(long bytes) {
        while (content.getChildCount() > 1) content.removeViewAt(1);
        emptyBox.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);

        int chapters = 0;
        for (ShinigamiDownloads.Manga manga : items) chapters += manga.chapters.size();
        if (items.isEmpty()) {
            summary.setText(R.string.shinigami_downloads_empty_title);
        } else {
            summary.setText(getString(R.string.shinigami_downloads_summary,
                    items.size(), chapters, ShinigamiDownloads.formatBytes(bytes)));
        }

        for (ShinigamiDownloads.Manga manga : items) {
            content.addView(mangaCard(manga));
        }
    }

    private View mangaCard(ShinigamiDownloads.Manga manga) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        card.setBackground(Ui.rounded(palette.surface, palette.divider, 16, this));
        card.setTag("downloads_manga_" + manga.mangaId);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);

        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackgroundColor(palette.background);
        cover.setTag("downloads_cover_" + manga.mangaId);
        if (!manga.coverUrl.isEmpty()) {
            Glide.with(this).load(manga.coverUrl).centerCrop().into(cover);
        }
        head.addView(cover, new LinearLayout.LayoutParams(Ui.dp(this, 58), Ui.dp(this, 82)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(Ui.dp(this, 12), 0, 0, 0);

        TextView title = Ui.label(this, manga.title.isEmpty()
                ? getString(R.string.app_name) : manga.title, 15, palette.text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setTag("downloads_title_" + manga.mangaId);
        text.addView(title);

        TextView count = Ui.label(this, getString(R.string.shinigami_downloads_count,
                manga.chapters.size()) + " - " + ShinigamiDownloads.formatBytes(manga.bytes()),
                12, palette.muted);
        count.setPadding(0, Ui.dp(this, 4), 0, 0);
        count.setTag("downloads_count_" + manga.mangaId);
        text.addView(count);

        head.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(head);

        android.widget.Button resume = Ui.chip(this, getString(R.string.shinigami_downloads_continue), palette);
        resume.setTag("downloads_continue_" + manga.mangaId);
        resume.setOnClickListener(v -> {
            ShinigamiDownloads.Chapter target = manga.continueChapter(store);
            if (target != null) openReader(manga, target);
        });
        LinearLayout.LayoutParams resumeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 40));
        resumeParams.topMargin = Ui.dp(this, 12);
        card.addView(resume, resumeParams);

        TextView listLabel = Ui.label(this, getString(R.string.shinigami_downloads_chapters),
                11, palette.muted);
        listLabel.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 4));
        card.addView(listLabel);

        for (ShinigamiDownloads.Chapter chapter : manga.chapters) {
            card.addView(chapterRow(manga, chapter));
        }
        return card;
    }

    private View chapterRow(ShinigamiDownloads.Manga manga, ShinigamiDownloads.Chapter chapter) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(this, 52));
        row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        row.setTag("downloads_chapter_" + chapter.chapterId);

        TextView label = Ui.label(this, chapter.label, 13, palette.text);
        label.setMaxLines(1);
        label.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView size = Ui.label(this, ShinigamiDownloads.formatBytes(chapter.bytes)
                + (chapter.complete ? "" : " (" + chapter.percent() + "%)"), 11,
                chapter.complete ? palette.muted : palette.accent);
        size.setTag("downloads_chapter_size_" + chapter.chapterId);
        row.addView(size);

        android.widget.Button delete = Ui.chip(this,
                getString(R.string.shinigami_downloads_delete_chapter), palette);
        delete.setTextSize(11f);
        delete.setMinWidth(0);
        delete.setMinimumWidth(0);
        delete.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        delete.setTag("downloads_delete_" + chapter.chapterId);
        delete.setContentDescription(getString(R.string.shinigami_downloads_delete_chapter)
                + ": " + chapter.label);
        delete.setOnClickListener(v -> confirmDelete(manga, chapter));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 36));
        deleteParams.leftMargin = Ui.dp(this, 8);
        row.addView(delete, deleteParams);

        row.setOnClickListener(v -> openReader(manga, chapter));
        return row;
    }

    private void confirmDelete(ShinigamiDownloads.Manga manga, ShinigamiDownloads.Chapter chapter) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.shinigami_downloads_delete_title)
                .setMessage(getString(R.string.shinigami_downloads_delete_body, chapter.label))
                .setNegativeButton(R.string.shinigami_downloads_cancel, null)
                .setPositiveButton(R.string.shinigami_downloads_delete_confirm, (dialog, which) -> {
                    // Hanya berkas halaman yang dihapus; progres baca tidak disentuh.
                    ShinigamiDownloadQueue.deleteChapter(this, chapter.chapterId);
                    ShinigamiDownloadStatus.clear(chapter.chapterId);
                    Toast.makeText(this, R.string.shinigami_downloads_deleted, Toast.LENGTH_SHORT).show();
                    scan();
                })
                .show();
    }

    private void openReader(ShinigamiDownloads.Manga manga, ShinigamiDownloads.Chapter chapter) {
        Intent intent = new Intent(this, ShinigamiReaderActivity.class);
        intent.putExtra(ShinigamiReaderActivity.EXTRA_MANGA_ID, manga.mangaId);
        intent.putExtra(ShinigamiReaderActivity.EXTRA_MANGA_TITLE, manga.title);
        intent.putExtra(ShinigamiReaderActivity.EXTRA_MANGA_COVER, manga.coverUrl);
        intent.putExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_ID, chapter.chapterId);
        intent.putExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_NUMBER, chapter.number);
        intent.putExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_TITLE, chapter.label);
        startActivity(intent);
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
