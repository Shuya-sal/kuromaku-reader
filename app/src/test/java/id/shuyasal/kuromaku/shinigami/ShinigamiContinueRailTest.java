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
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
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
 * Uji rail "Lanjutkan Membaca" di beranda Manga Shinigami: sumbernya hanya
 * ShinigamiStore (riwayat + progres), urut terbaru, dibatasi, bisa dibuka
 * offline, dan tidak memperpanjang katalog.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h640dp-mdpi")
public class ShinigamiContinueRailTest {
    private static final String COVER = "https://assets.shngm.id/thumbnail/cover/a.jpeg";

    private static ShinigamiSource.Transport transport;
    private static final AtomicInteger NETWORK_CALLS = new AtomicInteger();

    /** Beranda dengan sumber palsu supaya tes tidak menyentuh jaringan. */
    public static class Catalog extends ShinigamiActivity {
        @Override protected ShinigamiSource createSource() {
            return new ShinigamiSource(this, transport);
        }
    }

    /** Reader dengan sumber yang selalu gagal: membuktikan jalur offline. */
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
        return controller;
    }

    private void read(String mangaId, String title, String chapterId, double number) {
        store.recordRead(mangaId, title, COVER, chapterId, number);
    }

    private List<View> continueCards(ShinigamiActivity host) {
        ViewGroup container = (ViewGroup) tagged(host, "shinigami_continue_cards");
        List<View> cards = new ArrayList<>();
        for (int index = 0; index < container.getChildCount(); index++) {
            cards.add(container.getChildAt(index));
        }
        return cards;
    }

    @Test
    public void railIsHiddenWhenThereIsNoHistory() throws Exception {
        try (ActivityController<Catalog> controller = launch()) {
            assertEquals(View.GONE, tagged(controller.get(), "shinigami_continue_rail").getVisibility());
            assertEquals(0, continueCards(controller.get()).size());
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
            List<View> cards = continueCards(host);
            assertEquals("maksimal 8 kartu", 8, cards.size());
            assertEquals("shinigami_continue_manga-10", cards.get(0).getTag());
            assertEquals("shinigami_continue_manga-3", cards.get(7).getTag());
            TextView title = (TextView) cards.get(0).findViewWithTag("shinigami_continue_title");
            assertEquals("Judul 10", title.getText().toString());
        }
    }

    @Test
    public void cardShowsChapterNumberPercentAndContinueButton() throws Exception {
        read("manga-a", "Judul A", "chapter-a", 7);
        store.setProgress("chapter-a", 58);
        try (ActivityController<Catalog> controller = launch()) {
            Catalog host = controller.get();
            View card = continueCards(host).get(0);
            TextView title = (TextView) card.findViewWithTag("shinigami_continue_title");
            TextView meta = (TextView) card.findViewWithTag("shinigami_continue_meta");
            TextView action = (TextView) card.findViewWithTag("shinigami_continue_action");
            View cover = card.findViewWithTag("shinigami_continue_cover");
            assertEquals("Judul A", title.getText().toString());
            assertEquals("Ch. 7 · 58%", meta.getText().toString());
            assertEquals("Lanjutkan", action.getText().toString());
            assertNotNull("cover asli harus dipakai", cover);
        }
    }

    @Test
    public void continueOpensChapterOfflineAndRestoresReadingPosition() throws Exception {
        // Chapter benar-benar tersimpan di disk + posisi baca terakhir halaman 4.
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
            View action = continueCards(controller.get()).get(0)
                    .findViewWithTag("shinigami_continue_action");
            action.performClick();
            started = Shadows.shadowOf(controller.get()).getNextStartedActivity();
            assertNotNull("tombol Lanjutkan harus membuka reader", started);
            assertEquals("chapter-a", started.getStringExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_ID));
            assertEquals("manga-a", started.getStringExtra(ShinigamiReaderActivity.EXTRA_MANGA_ID));
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
        assertEquals("posisi baca tersimpan tetap halaman yang sama", 4, store.page("chapter-a"));
        assertTrue("progres baca tetap wajar", store.progress("chapter-a") > 0);
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
            RecyclerView list = (RecyclerView) tagged(host, "shinigami_grid");
            assertEquals(2, list.getAdapter().getItemCount());
        }
    }

    @Test
    public void historySurvivesClosingAndReopeningTheApp() throws Exception {
        read("manga-a", "Judul A", "chapter-a", 7);
        try (ActivityController<Catalog> first = launch()) {
            assertEquals(1, continueCards(first.get()).size());
        }
        try (ActivityController<Catalog> second = launch()) {
            assertEquals("riwayat harus tetap ada setelah aplikasi ditutup", 1,
                    continueCards(second.get()).size());
            assertEquals(View.VISIBLE,
                    tagged(second.get(), "shinigami_continue_rail").getVisibility());
        }
    }

    @Test
    public void updateDateIsShortAndGridIsWideEnoughToShowIt() throws Exception {
        try (ActivityController<Catalog> controller = launch()) {
            Catalog host = controller.get();
            RecyclerView list = (RecyclerView) tagged(host, "shinigami_grid");
            GridLayoutManager manager = (GridLayoutManager) list.getLayoutManager();
            assertEquals("dua kolom agar tanggal tidak terpotong", 2, manager.getSpanCount());

            list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY));
            list.layout(0, 0, 1080, 1600);
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            boolean sawUpdate = false;
            for (int index = 0; index < list.getChildCount(); index++) {
                View child = list.getChildAt(index);
                TextView subtitle = (TextView) child.findViewWithTag("shinigami_subtitle");
                if (subtitle == null) continue;
                String text = subtitle.getText().toString();
                assertFalse("tanggal tidak boleh kosong", text.trim().isEmpty());
                assertTrue("baris tanggal harus utuh", subtitle.getMaxLines() >= 3);
                if (text.contains("Update ")) {
                    sawUpdate = true;
                    String line = text.substring(text.indexOf("Update "));
                    assertFalse("tanggal tidak boleh dipotong dengan elipsis",
                            line.contains("\u2026") || line.endsWith("..."));
                    assertTrue("tanggal harus memakai format ringkas: " + line,
                            line.matches("Update \\d{1,2} [A-Za-z]+ \\d{4}"));
                }
            }
            assertTrue("kartu harus menampilkan baris Update", sawUpdate);
        }
    }

    @Test
    public void railFollowsLightAndDarkTheme() throws Exception {
        read("manga-a", "Judul A", "chapter-a", 7);
        for (int theme : new int[]{id.shuyasal.kuromaku.ui.Prefs.THEME_LIGHT,
                id.shuyasal.kuromaku.ui.Prefs.THEME_DARK}) {
            new id.shuyasal.kuromaku.ui.Prefs(context).setTheme(theme);
            id.shuyasal.kuromaku.ui.Palette palette =
                    id.shuyasal.kuromaku.ui.Palette.of(new id.shuyasal.kuromaku.ui.Prefs(context).theme());
            try (ActivityController<Catalog> controller = launch()) {
                Catalog host = controller.get();
                TextView label = (TextView) tagged(host, "shinigami_continue_label");
                TextView meta = (TextView) tagged(host, "shinigami_continue_meta");
                assertEquals("judul rail mengikuti warna tema", palette.text,
                        label.getCurrentTextColor());
                assertEquals("meta rail mengikuti warna tema", palette.muted,
                        meta.getCurrentTextColor());
            }
        }
    }

    @Test
    public void cardLabelsAreNotClippedAfterLayout() throws Exception {
        read("manga-a", "Regressor Instruction Manual", "chapter-a", 210);
        store.setProgress("chapter-a", 68);
        read("manga-b", "Nano Machine", "chapter-b", 212);
        store.setProgress("chapter-b", 24);
        try (ActivityController<Catalog> controller = launch()) {
            Catalog host = controller.get();
            int width = (int) (112 * host.getResources().getDisplayMetrics().density);
            for (View card : continueCards(host)) {
                card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                card.layout(0, 0, card.getMeasuredWidth(), card.getMeasuredHeight());
                assertNotClipped((TextView) card.findViewWithTag("shinigami_continue_meta"));
                assertNotClipped((TextView) card.findViewWithTag("shinigami_continue_action"));
            }
        }
    }

    private void assertNotClipped(TextView view) {
        assertNotNull(view);
        assertNotNull("layout harus terbentuk", view.getLayout());
        assertEquals("teks terpotong: " + view.getText(), 0,
                view.getLayout().getEllipsisCount(view.getLayout().getLineCount() - 1));
        assertFalse("teks tidak boleh pecah baris: " + view.getText(),
                view.getLayout().getLineCount() > 1);
    }
}
