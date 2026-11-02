package id.shuyasal.kuromaku;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.FeedEntry;
import id.shuyasal.kuromaku.data.LibraryBuilder;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.net.FeedClient;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.Ui;

/**
 * Library screen: a native list of volumes and chapters built from the label
 * feed. No website is loaded here, so there is no Blogspot chrome or advertising.
 */
public class MainActivity extends AppCompatActivity {

    private Prefs prefs;
    private Palette palette;
    private LinearLayout content;
    private LinearLayout barBox;
    private LinearLayout spinnerRow;
    private TextView status;
    private Catalog catalog;
    private boolean loading;

    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        palette = Palette.of(prefs.theme());
        buildShell();
        loadLibrary();
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);

        barBox = new LinearLayout(this);
        barBox.setOrientation(LinearLayout.HORIZONTAL);
        barBox.setGravity(Gravity.CENTER_VERTICAL);
        barBox.setBackgroundColor(palette.surface);
        int pad = Ui.dp(this, 14);
        barBox.setPadding(pad, Ui.dp(this, 12), pad, Ui.dp(this, 12));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.label(this, "Kuromaku Reader", 19, palette.text);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        titles.addView(title);
        titles.addView(Ui.label(this, "Monogatari no Kuromaku", 12, palette.muted));
        barBox.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button themeButton = Ui.chip(this, palette.label, palette);
        themeButton.setOnClickListener(v -> {
            prefs.setTheme((prefs.theme() + 1) % 3);
            palette = Palette.of(prefs.theme());
            rebuildShell();
        });
        barBox.addView(themeButton);

        root.addView(barBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        spinnerRow = new LinearLayout(this);
        spinnerRow.setGravity(Gravity.CENTER);
        spinnerRow.setPadding(0, Ui.dp(this, 30), 0, Ui.dp(this, 10));
        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        spinnerRow.addView(spinner);
        root.addView(spinnerRow);

        status = Ui.label(this, "Memuat katalog dari Kaori Translation…", 12, palette.muted);
        status.setGravity(Gravity.CENTER);
        status.setPadding(pad, 0, pad, Ui.dp(this, 10));
        root.addView(status);

        ScrollView scroller = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, Ui.dp(this, 4), pad, Ui.dp(this, 32));
        scroller.addView(content);
        root.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        applySystemBars();
    }

    private void rebuildShell() {
        buildShell();
        if (catalog == null) {
            loadLibrary();
        } else {
            render();
        }
    }

    private void applySystemBars() {
        getWindow().setStatusBarColor(palette.surface);
        getWindow().setNavigationBarColor(palette.background);
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(palette.darkIcons ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    private void loadLibrary() {
        if (loading) return;
        loading = true;
        spinnerRow.setVisibility(View.VISIBLE);
        status.setVisibility(View.VISIBLE);
        status.setText("Memuat katalog dari Kaori Translation…");

        new Thread(() -> {
            try {
                List<FeedEntry> entries = FeedClient.fetchEntries();
                Catalog built = LibraryBuilder.build(entries);
                if (built.size() == 0) {
                    throw new IllegalStateException("Tidak ada chapter yang ditemukan pada feed.");
                }
                main.post(() -> {
                    loading = false;
                    catalog = built;
                    Session.setCatalog(built);
                    render();
                });
            } catch (Exception error) {
                main.post(() -> {
                    loading = false;
                    showFailure(error);
                });
            }
        }).start();
    }

    private void showFailure(Exception error) {
        spinnerRow.setVisibility(View.GONE);
        content.removeAllViews();
        status.setVisibility(View.GONE);

        TextView heading = Ui.label(this, "Katalog gagal dimuat", 17, palette.text);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 8));
        content.addView(heading);

        String reason = error.getMessage();
        if (reason == null || reason.isEmpty()) reason = error.getClass().getSimpleName();
        TextView detail = Ui.label(this, reason
                + "\n\nPeriksa koneksi internet, lalu coba lagi.", 13, palette.muted);
        detail.setPadding(0, 0, 0, Ui.dp(this, 16));
        content.addView(detail);

        Button retry = Ui.chip(this, "Coba lagi", palette);
        retry.setOnClickListener(v -> loadLibrary());
        content.addView(retry);

        Button source = Ui.chip(this, "Buka sumber di browser", palette);
        source.setOnClickListener(v -> openExternal(FeedClient.LABEL_URL));
        content.addView(source);
    }

    private void render() {
        spinnerRow.setVisibility(View.GONE);
        status.setVisibility(View.GONE);
        content.removeAllViews();

        if (catalog == null || catalog.size() == 0) {
            showFailure(new IllegalStateException("Katalog kosong."));
            return;
        }

        TextView count = Ui.label(this, catalog.size() + " chapter · "
                + catalog.volumes.size() + " volume", 12, palette.muted);
        count.setPadding(0, 0, 0, Ui.dp(this, 6));
        content.addView(count);

        Chapter resume = catalog.findByUrl(prefs.lastUrl());
        if (resume != null) {
            Button continueButton = Ui.chip(this, "Lanjutkan membaca: " + resume.label, palette);
            continueButton.setTextColor(palette.accent);
            continueButton.setOnClickListener(v -> openChapter(resume));
            content.addView(continueButton, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            addSpacer(Ui.dp(this, 12));
        }

        for (Volume volume : catalog.volumes) {
            addVolumeHeader(volume);
            for (Chapter chapter : volume.chapters) {
                addChapterRow(chapter);
            }
            addSpacer(Ui.dp(this, 16));
        }

        TextView attribution = Ui.label(this,
                "Sumber: Kaori Translation · kaoritranslation.blogspot.com\n"
                        + "Aplikasi ini hanya membuka isi publik dan tidak menyimpan salinan novel.",
                11, palette.muted);
        attribution.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 10));
        content.addView(attribution);

        Button openSource = Ui.chip(this, "Kunjungi sumber asli", palette);
        openSource.setOnClickListener(v -> openExternal(FeedClient.LABEL_URL));
        content.addView(openSource);
    }

    private void addVolumeHeader(Volume volume) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 6));

        TextView label = Ui.label(this, volume.title(), 16, palette.text);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(label, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(Ui.label(this, String.valueOf(volume.chapters.size()), 12, palette.muted));
        content.addView(row);

        View line = new View(this);
        line.setBackgroundColor(palette.divider);
        content.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 1))));
    }

    private void addChapterRow(Chapter chapter) {
        int progress = prefs.progress(chapter.url);
        boolean finished = progress > 95;
        boolean started = progress > 0 && !finished;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 2), Ui.dp(this, 13), Ui.dp(this, 2), Ui.dp(this, 13));

        TextView label = Ui.label(this, chapter.label, 15,
                finished ? palette.muted : palette.text);
        row.addView(label, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (finished) {
            row.addView(Ui.label(this, "selesai", 11, palette.muted));
        } else if (started) {
            row.addView(Ui.label(this, progress + "%", 11, palette.accent));
        }

        row.setOnClickListener(v -> openChapter(chapter));
        content.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View line = new View(this);
        line.setBackgroundColor(palette.divider);
        content.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 1))));
    }

    private void addSpacer(int height) {
        View spacer = new View(this);
        content.addView(spacer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height));
    }

    private void openChapter(Chapter chapter) {
        Session.setCatalog(catalog);
        Intent intent = new Intent(this, ReaderActivity.class);
        intent.putExtra(ReaderActivity.EXTRA_URL, chapter.url);
        startActivity(intent);
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (Exception error) {
            Toast.makeText(this, "Tidak ada browser tersedia", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (catalog != null) {
            palette = Palette.of(prefs.theme());
            rebuildShell();
        }
    }
}
