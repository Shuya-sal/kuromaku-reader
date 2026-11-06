package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rail "Lanjutkan Membaca": ringkas (tinggi terbatas), kartu horizontal dengan
 * cover kecil, judul dua baris, progres, dan seluruh kartu bisa ditekan.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h640dp-mdpi")
public class ShinigamiContinueRailTest {
    private static final String COVER = "https://assets.shngm.id/thumbnail/cover/a.jpeg";

    private static ShinigamiSource.Transport transport;
    private static final AtomicInteger NETWORK_CALLS = new AtomicInteger();

    public static class Catalog extends ShinigamiActivity {
        @Override protected ShinigamiSource createSource() {
            return new ShinigamiSource(this, transport);
        }
    }

    public static class OfflineReader extends ShinigamiReaderActivity {
        @Override protected ShinigamiSource createSource() {
            return new ShinigamiSource(this, url -> {
                NETWORK_CALLS.incrementAndGet();
                throw new ShinigamiSource.SourceException("Koneksi terputus.",
                        new java.io.IOException("offline"));
            });
        }
    }

    private Context context;
    private ShinigamiStore store;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences(ShinigamiConfig.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        store = new ShinigamiStore(context);
        NETWORK_CALLS.set(0);
        transport = url -> {
            if (url.contains("/genre/")) return "{\"retcode\":0,\"data\":[]}";
            return ShinigamiReaderTest.fixture("live_manga_list.json");
        };
    }

    private void await(ShinigamiActivity host) throws Exception {
        Field field = ShinigamiActivity.class.getDeclaredField("worker");
        field.setAccessible(true);
        ((ExecutorService) field.get(host)).submit(() -> {}).get(5, TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private void await(ShinigamiReaderActivity reader) throws Exception {
        Field field = ShinigamiReaderActivity.class.getDeclaredField("worker");
        field.setAccessible(true);
        ((ExecutorService) field.get(reader)).submit(() -> {}).get(5, TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private View tagged(ShinigamiActivity host, String tag) {
        View view = host.getWindow().getDecorView().findViewWithTag(tag);
        assertNotNull(tag, view);
        return view;
    }

    private ActivityController<Catalog> launch() throws Exception {
        ActivityController<Catalog> controller = Robolectric.buildActivity(Catalog.class).setup();
        await(controller.get());
        // Beranda default Chapter Terbaru (daftar baris). Tes di kelas ini
        // menguji grid katalog, jadi pindah ke Terpopuler lebih dulu.
        View popular = controller.get().getWindow().getDecorView()
                .findViewWithTag("shinigami_chip_Terpopuler");
        if (popular != null) {
            popular.performClick();
            await(controller.get());
        }
        return controller;
    }

    private void read(String mangaId, String title, String chapterId, double number) {
        store.recordRead(mangaId, title, COVER, chapterId, number);
    }

    private RecyclerView rail(ShinigamiActivity host) {
        return (RecyclerView) tagged(host, "shinigami_continue_cards");
    }

    /** Letakkan rail seperti di layar supaya view holder benar-benar dibuat. */
    private List<View> laidOutCards(ShinigamiActivity host) {
        RecyclerView rail = rail(host);
        int width = host.getResources().getDisplayMetrics().widthPixels;
        rail.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        rail.layout(0, 0, width, rail.getMeasuredHeight());
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        List<View> cards = new ArrayList<>();
        for (int index = 0; index < rail.getChildCount(); index++) {
            cards.add(rail.getChildAt(index));
        }
        return cards;
    }

    @Test
    public void railIsHiddenWhenThereIsNoHistory() throws Exception {
        try (ActivityController<Catalog> controller = launch()) {
            // Tanpa riwayat item header tidak dibuat sama sekali.
            Catalog host = controller.get();
            View railView = host.getWindow().getDecorView()
                    .findViewWithTag("shinigami_continue_rail");
            assertTrue("rail tidak boleh terpasang tanpa riwayat",
                    railView == null || railView.getVisibility() != View.VISIBLE);
            RecyclerView list = (RecyclerView) tagged(host, "shinigami_grid");
            assertEquals("hanya katalog, tanpa header", 2, list.getAdapter().getItemCount());
        }
    }

    @Test
    public void railListsNewestFirstAndStopsAtEightCards() throws Exception {
        for (int index = 1; index <= 10; index++) {
            read("manga-" + index, "Judul " + index, "chapter-" + index, index);
        }
        try (ActivityController<Catalog> controller = launch()) {
            Catalog host = controller.get();
            assertEquals(View.VISIBLE, tagged(host, "shinigami_continue_rail").getVisibility());
            assertEquals("maksimal 8 kartu", 8, rail(host).getAdapter().getItemCount());
            List<View> cards = laidOutCards(host);
            TextView first = cards.get(0).findViewWithTag("shinigami_continue_title");
            assertEquals("Judul 10", first.getText().toString());
        }
    }

    @Test
    public void cardIsCompactWithSmallCoverTwoLineTitleAndProgress() throws Exception {
        read("manga-a", "Regressor Instruction Manual yang judulnya panjang sekali",
                "chapter-a", 210);
        store.setProgress("chapter-a", 68);
        try (ActivityController<Catalog> controller = launch()) {
            Catalog host = controller.get();
            float density = host.getResources().getDisplayMetrics().density;
            View card = laidOutCards(host).get(0);

            int coverWidthDp = Math.round(card.findViewWithTag("shinigami_continue_cover")
                    .getWidth() / density);
            assertEquals("cover kecil 60-75 dp", 64, coverWidthDp);

            TextView title = card.findViewWithTag("shinigami_continue_title");
            assertEquals("judul maksimal dua baris", 2, title.getMaxLines());
            assertNotNull(title.getEllipsize());

            TextView meta = card.findViewWithTag("shinigami_continue_meta");
            assertEquals("Ch. 210 · 68%", meta.getText().toString());

            android.widget.ProgressBar bar = card.findViewWithTag("shinigami_continue_progress");
            assertNotNull("progress bar kecil", bar);
            assertEquals(68, bar.getProgress());
        }
    }

    @Test
    public void railHeightStaysCompact() throws Exception {
        for (int index = 1; index <= 5; index++) {
            read("manga-" + index, "Judul " + index, "chapter-" + index, index);
        }
        try (ActivityController<Catalog> controller = launch()) {
            Catalog host = controller.get();
            View railView = tagged(host, "shinigami_continue_rail");
            int width = host.getResources().getDisplayMetrics().widthPixels;
            railView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int heightDp = Math.round(railView.getMeasuredHeight()
                    / host.getResources().getDisplayMetrics().density);
            assertTrue("tinggi rail " + heightDp + " dp harus <= 180 dp", heightDp <= 180);
            assertTrue("rail tidak boleh gepeng: " + heightDp, heightDp >= 120);
        }
    }

    @Test
    public void tappingTheWholeCardContinuesReadingOffline() throws Exception {
        List<String> urls = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            urls.add("https://storage.shngm.id/page-" + index + ".webp");
        }
        store.setChapterPages("chapter-a", urls);
        ShinigamiDownloader downloader = new ShinigamiDownloader(context);
        File dir = downloader.chapterDir("chapter-a");
        assertTrue(dir.isDirectory() || dir.mkdirs());
        for (int index = 0; index < 12; index++) {
            try (FileOutputStream out = new FileOutputStream(new File(dir, index + ".img"))) {
                Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888)
                        .compress(Bitmap.CompressFormat.PNG, 100, out);
            }
        }
        store.setPage("chapter-a", 4);
        store.setProgress("chapter-a", 55);
        read("manga-a", "Judul A", "chapter-a", 7);

        Intent started;
        try (ActivityController<Catalog> controller = launch()) {
            Catalog host = controller.get();
            laidOutCards(host).get(0).performClick();
            started = Shadows.shadowOf(host).getNextStartedActivity();
            assertNotNull("kartu harus membuka reader", started);
            assertEquals("chapter-a",
                    started.getStringExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_ID));
        }

        Intent readerIntent = new Intent(context, OfflineReader.class)
                .putExtra(ShinigamiReaderActivity.EXTRA_MANGA_ID,
                        started.getStringExtra(ShinigamiReaderActivity.EXTRA_MANGA_ID))
                .putExtra(ShinigamiReaderActivity.EXTRA_MANGA_TITLE,
                        started.getStringExtra(ShinigamiReaderActivity.EXTRA_MANGA_TITLE))
                .putExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_ID,
                        started.getStringExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_ID))
                .putExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_NUMBER,
                        started.getDoubleExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_NUMBER, 0d));
        try (ActivityController<OfflineReader> controller =
                     Robolectric.buildActivity(OfflineReader.class, readerIntent).setup()) {
            OfflineReader reader = controller.get();
            await(reader);
            RecyclerView list = (RecyclerView) reader.getWindow().getDecorView()
                    .findViewWithTag("shinigami_reader_list");
            assertNotNull(list);
            assertEquals("semua halaman lokal terbaca", 12, list.getAdapter().getItemCount());
            assertEquals("posisi baca dipulihkan", 4,
                    ((androidx.recyclerview.widget.LinearLayoutManager) list.getLayoutManager())
                            .findFirstVisibleItemPosition());
            assertEquals("tidak boleh ada permintaan jaringan", 0, NETWORK_CALLS.get());
        }
    }

    @Test
    public void viewAllOpensTheExistingHistoryList() throws Exception {
        read("manga-a", "Judul A", "chapter-a", 7);
        read("manga-b", "Judul B", "chapter-b", 3);
        try (ActivityController<Catalog> controller = launch()) {
            Catalog host = controller.get();
            tagged(host, "shinigami_continue_all").performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            TextView status = (TextView) tagged(host, "shinigami_status");
            assertEquals("2 manga dalam riwayat", status.getText().toString());
            // Riwayat memakai daftar baris; rail tetap di atasnya.
            RecyclerView list = (RecyclerView) tagged(host, "shinigami_grid");
            // Satu item header (rail) + dua baris riwayat.
            assertEquals(3, list.getAdapter().getItemCount());
        }
    }

    @Test
    public void historySurvivesClosingAndReopeningTheApp() throws Exception {
        read("manga-a", "Judul A", "chapter-a", 7);
        try (ActivityController<Catalog> first = launch()) {
            assertEquals(1, rail(first.get()).getAdapter().getItemCount());
        }
        try (ActivityController<Catalog> second = launch()) {
            assertEquals("riwayat harus tetap ada setelah aplikasi ditutup", 1,
                    rail(second.get()).getAdapter().getItemCount());
            assertEquals(View.VISIBLE,
                    tagged(second.get(), "shinigami_continue_rail").getVisibility());
        }
    }

    @Test
    public void progressBarIsEmptyWhenChapterUnread() throws Exception {
        read("manga-a", "Judul A", "chapter-a", 7);
        try (ActivityController<Catalog> controller = launch()) {
            View card = laidOutCards(controller.get()).get(0);
            android.widget.ProgressBar bar = card.findViewWithTag("shinigami_continue_progress");
            assertEquals(0, bar.getProgress());
            TextView meta = card.findViewWithTag("shinigami_continue_meta");
            assertTrue(meta.getText().toString(), meta.getText().toString().contains("0%"));
        }
    }

    @Test
    public void updateDateIsShortAndGridIsWideEnoughToShowIt() throws Exception {
        try (ActivityController<Catalog> controller = launch()) {
            Catalog host = controller.get();
            RecyclerView list = (RecyclerView) tagged(host, "shinigami_grid");
            androidx.recyclerview.widget.GridLayoutManager manager =
                    (androidx.recyclerview.widget.GridLayoutManager) list.getLayoutManager();
            assertEquals("dua kolom agar tanggal tidak terpotong", 2, manager.getSpanCount());

            int width = host.getResources().getDisplayMetrics().widthPixels;
            list.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY));
            list.layout(0, 0, width, 1600);
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            boolean sawUpdate = false;
            for (int index = 0; index < list.getChildCount(); index++) {
                TextView subtitle = list.getChildAt(index)
                        .findViewWithTag("shinigami_subtitle");
                if (subtitle == null) continue;
                String text = subtitle.getText().toString();
                assertFalse("tanggal tidak boleh kosong", text.trim().isEmpty());
                if (text.contains("Update ")) {
                    sawUpdate = true;
                    String line = text.substring(text.indexOf("Update "));
                    assertFalse("tanggal tidak boleh dipotong dengan elipsis",
                            line.contains("\u2026") || line.endsWith("..."));
                    assertTrue("format ringkas: " + line,
                            line.matches("Update \\d{1,2} [A-Za-z]+ \\d{4}"));
                }
            }
            assertTrue("kartu harus menampilkan baris Update", sawUpdate);
        }
    }
}
