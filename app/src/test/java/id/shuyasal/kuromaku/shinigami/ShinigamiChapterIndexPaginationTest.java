package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/**
 * Membuktikan indeks chapter diambil lengkap untuk judul yang lebih panjang dari
 * satu halaman sumber.
 *
 * Fixture JSON adalah respons asli endpoint
 * {@code /v1/chapter/{id}/list?page_size=500} untuk manga 579 chapter
 * ("My Disciples Are All Big Villains"), diambil langsung dari api.shngm.io lalu
 * dipadatkan ke berkas .json.gz. Bukti perilaku sumber yang mendasari perbaikan:
 *
 * <pre>
 * desc halaman 1 → 500 chapter, nomor 575 … 80
 * desc halaman 2 →  79 chapter, nomor  79 …  1
 * asc  halaman 1 → 500 chapter, nomor   1 … 496
 * asc  halaman 2 →  79 chapter, nomor 497 … 575
 * </pre>
 *
 * Artinya permintaan satu halaman (perilaku rilis 2.29.0) tidak pernah memuat
 * chapter 1..79 pada urutan Terbaru, sehingga "Terlama" berhenti di tengah daftar.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w320dp-h640dp-mdpi")
public class ShinigamiChapterIndexPaginationTest {

    static final String MANGA = "db3daa3b-3799-40c4-b4b7-fc06b0cbba8d";
    static final int TOTAL = 579;

    static final List<String> CHAPTER_REQUESTS = new ArrayList<>();

    public static class Detail extends ShinigamiDetailActivity {
        @Override protected ShinigamiSource createSource() {
            return new ShinigamiSource(this, ShinigamiChapterIndexPaginationTest::route);
        }
    }

    Context context;

    @Before public void setup() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("kuromaku_prefs", 0).edit().clear().commit();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, 0).edit().clear().commit();
        CHAPTER_REQUESTS.clear();
    }

    static String route(String url) throws IOException {
        if (url.contains("/manga/detail/")) return mangaDetail();
        if (url.contains("/chapter/") && url.contains("/list")) {
            CHAPTER_REQUESTS.add(url);
            String order = url.contains("sort_order=asc") ? "asc" : "desc";
            String page = url.contains("page=2") ? "2" : "1";
            return fixture("shinigami_" + order + "_p" + page + ".json.gz");
        }
        throw new IOException("unexpected " + url);
    }

    static String mangaDetail() {
        return "{\"retcode\":0,\"data\":{\"manga_id\":\"" + MANGA + "\",\"title\":\"Uji 579 Chapter\","
                + "\"description\":\"Uji\",\"status\":\"ongoing\",\"cover_image_url\":\"\"}}";
    }

    static String fixture(String name) throws IOException {
        try (InputStream raw = ShinigamiChapterIndexPaginationTest.class
                .getResourceAsStream("/shinigami/" + name)) {
            if (raw == null) throw new IOException("fixture tidak ditemukan: " + name);
            try (GZIPInputStream in = new GZIPInputStream(raw)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
                return out.toString("UTF-8");
            }
        }
    }

    Intent intent() {
        return new Intent(context, Detail.class)
                .putExtra(ShinigamiDetailActivity.EXTRA_MANGA_ID, MANGA)
                .putExtra(ShinigamiDetailActivity.EXTRA_MANGA_TITLE, "Uji 579 Chapter");
    }

    static void await(ShinigamiDetailActivity activity) throws Exception {
        java.lang.reflect.Field field = ShinigamiDetailActivity.class.getDeclaredField("worker");
        field.setAccessible(true);
        ExecutorService worker = (ExecutorService) field.get(activity);
        for (int i = 0; i < 10; i++) {
            worker.submit(() -> { }).get(10, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));
        }
    }

    static List<String> labels(ShinigamiDetailActivity activity) {
        LinearLayout list = (LinearLayout) activity.getWindow().getDecorView()
                .findViewWithTag("shinigami_chapter_list");
        assertNotNull(list);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < list.getChildCount(); i++) {
            TextView label = list.getChildAt(i).findViewWithTag("shinigami_chapter_label");
            if (label != null) out.add(label.getText().toString());
        }
        return out;
    }

    @Test public void terbaruMemuatSeluruhChapterTermasukYangTertua() throws Exception {
        try (ActivityController<Detail> controller =
                     Robolectric.buildActivity(Detail.class, intent()).setup()) {
            Detail detail = controller.get();
            await(detail);

            List<String> shown = labels(detail);
            assertEquals("seluruh chapter harus terindeks: " + shown.size(), TOTAL, shown.size());
            assertEquals("Chapter 575", shown.get(0));
            assertEquals("Chapter 1", shown.get(shown.size() - 1));
            assertEquals("tidak boleh ada duplikat", TOTAL, new HashSet<>(shown).size());

            boolean secondPage = false;
            for (String url : CHAPTER_REQUESTS) {
                if (url.contains("page=2") && url.contains("sort_order=desc")) secondPage = true;
            }
            assertTrue("halaman kedua sumber harus diambil: " + CHAPTER_REQUESTS, secondPage);
        }
    }

    @Test public void terlamaMemuatChapterPalingAwalDariSeluruhHalaman() throws Exception {
        try (ActivityController<Detail> controller =
                     Robolectric.buildActivity(Detail.class, intent()).setup()) {
            Detail detail = controller.get();
            await(detail);

            detail.getWindow().getDecorView()
                    .findViewWithTag("shinigami_sort_button").performClick();
            await(detail);

            List<String> shown = labels(detail);
            assertEquals("seluruh chapter harus terindeks: " + shown.size(), TOTAL, shown.size());
            assertEquals("Chapter 1", shown.get(0));
            assertEquals("Chapter 575", shown.get(shown.size() - 1));
            assertEquals("tidak boleh ada duplikat", TOTAL, new HashSet<>(shown).size());
        }
    }

    @Test public void pencarianMenemukanChapterAwalPadaIndeksLengkap() throws Exception {
        try (ActivityController<Detail> controller =
                     Robolectric.buildActivity(Detail.class, intent()).setup()) {
            Detail detail = controller.get();
            await(detail);

            android.widget.EditText search = (android.widget.EditText) detail.getWindow().getDecorView()
                    .findViewWithTag("shinigami_search_view");
            search.setText("1");
            await(detail);

            List<String> shown = labels(detail);
            assertTrue("chapter 1 harus ketemu: " + shown, shown.contains("Chapter 1"));
            assertTrue("chapter 10 harus ketemu: " + shown, shown.contains("Chapter 10"));

            search.setText("");
            await(detail);
            assertEquals("daftar penuh kembali setelah pencarian dihapus",
                    TOTAL, labels(detail).size());
        }
    }
}
