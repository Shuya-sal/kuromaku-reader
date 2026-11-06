package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Menguji perilaku daftar chapter pada layar detail:
 * - "Terlama" benar-benar memuat chapter paling awal (urutan server asc).
 * - Pencarian menjangkau halaman yang belum dimuat.
 * - Pembuatan ulang Activity (pergantian tema) tidak memicu fetch ulang.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w320dp-h640dp-mdpi")
public class ShinigamiDetailIndexTest {
    static final String MANGA = "11111111-2222-3333-4444-555555555555";
    static final int TOTAL = 60;

    static final AtomicInteger DETAIL_CALLS = new AtomicInteger();
    static final List<String> CHAPTER_REQUESTS = new ArrayList<>();

    public static class Detail extends ShinigamiDetailActivity {
        @Override protected ShinigamiSource createSource() {
            return new ShinigamiSource(this, ShinigamiDetailIndexTest::route);
        }
    }

    Context context;

    @Before public void setup() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("kuromaku_prefs", 0).edit().clear().commit();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, 0).edit().clear().commit();
        DETAIL_CALLS.set(0);
        CHAPTER_REQUESTS.clear();
    }

    static String route(String url) throws java.io.IOException {
        if (url.contains("/manga/detail/")) {
            DETAIL_CALLS.incrementAndGet();
            return mangaDetail();
        }
        if (url.contains("/chapter/") && url.contains("/list")) {
            CHAPTER_REQUESTS.add(url);
            boolean asc = url.contains("sort_order=asc");
            boolean secondPage = url.contains("page=2");
            return chapterPage(asc, secondPage);
        }
        throw new java.io.IOException("unexpected " + url);
    }

    static String mangaDetail() {
        return "{\"retcode\":0,\"data\":{\"manga_id\":\"" + MANGA + "\",\"title\":\"Uji Indeks\","
                + "\"description\":\"Uji\",\"status\":\"ongoing\",\"cover_image_url\":\"\"}}";
    }

    /** Halaman 1 berisi 30 chapter, halaman 2 berisi 30 chapter berikutnya. */
    static String chapterPage(boolean asc, boolean secondPage) throws java.io.IOException {
        int first;
        int last;
        if (asc) {
            first = secondPage ? 31 : 1;
            last = secondPage ? TOTAL : 30;
        } else {
            first = secondPage ? 30 : TOTAL;
            last = secondPage ? 1 : TOTAL - 29;
        }
        try {
            JSONArray data = new JSONArray();
            int step = asc ? 1 : -1;
            for (int n = first; asc ? n <= last : n >= last; n += step) {
                JSONObject item = new JSONObject();
                item.put("chapter_id", "ch-" + n);
                item.put("manga_id", MANGA);
                item.put("chapter_number", n);
                item.put("chapter_title", "");
                item.put("release_date", "2026-01-01T00:00:00Z");
                data.put(item);
            }
            JSONObject meta = new JSONObject();
            meta.put("total_record", TOTAL);
            meta.put("total_page", 2);
            JSONObject root = new JSONObject();
            root.put("retcode", 0);
            root.put("data", data);
            root.put("meta", meta);
            return root.toString();
        } catch (org.json.JSONException error) {
            throw new java.io.IOException(error);
        }
    }

    Intent intent() {
        return new Intent(context, Detail.class)
                .putExtra(ShinigamiDetailActivity.EXTRA_MANGA_ID, MANGA)
                .putExtra(ShinigamiDetailActivity.EXTRA_MANGA_TITLE, "Uji Indeks");
    }

    static void await(ShinigamiDetailActivity activity) throws Exception {
        java.lang.reflect.Field field = ShinigamiDetailActivity.class.getDeclaredField("worker");
        field.setAccessible(true);
        ExecutorService worker = (ExecutorService) field.get(activity);
        for (int i = 0; i < 8; i++) {
            worker.submit(() -> { }).get(5, TimeUnit.SECONDS);
            // Pencarian menjangkau halaman lain lewat tugas tertunda, jadi jam
            // looper harus dimajukan sebelum hasilnya diperiksa.
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));
        }
    }

    static LinearLayout list(ShinigamiDetailActivity activity) {
        return (LinearLayout) activity.getWindow().getDecorView()
                .findViewWithTag("shinigami_chapter_list");
    }

    static List<String> labels(ShinigamiDetailActivity activity) {
        LinearLayout list = list(activity);
        assertNotNull(list);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < list.getChildCount(); i++) {
            TextView label = list.getChildAt(i).findViewWithTag("shinigami_chapter_label");
            if (label != null) out.add(label.getText().toString());
        }
        return out;
    }

    @Test public void terlamaMemuatChapterPalingAwalDariSumber() throws Exception {
        try (ActivityController<Detail> controller =
                     Robolectric.buildActivity(Detail.class, intent()).setup()) {
            Detail detail = controller.get();
            await(detail);
            assertTrue("default harus Terbaru", labels(detail).get(0).contains("60"));

            detail.getWindow().getDecorView()
                    .findViewWithTag("shinigami_sort_button").performClick();
            await(detail);

            List<String> shown = labels(detail);
            assertEquals("Chapter 1", shown.get(0));
            assertEquals("Chapter 60", shown.get(shown.size() - 1));
            boolean askedAsc = false;
            for (String url : CHAPTER_REQUESTS) {
                if (url.contains("page=1") && url.contains("sort_order=asc")) askedAsc = true;
            }
            assertTrue("permintaan asc harus dikirim ke sumber: " + CHAPTER_REQUESTS, askedAsc);
        }
    }

    @Test public void pencarianMenjangkauHalamanYangBelumDimuat() throws Exception {
        try (ActivityController<Detail> controller =
                     Robolectric.buildActivity(Detail.class, intent()).setup()) {
            Detail detail = controller.get();
            await(detail);
            assertEquals("indeks penuh dimuat sejak awal", 60, labels(detail).size());

            EditText search = (EditText) detail.getWindow().getDecorView()
                    .findViewWithTag("shinigami_search_view");
            search.setText("1");
            await(detail);

            assertTrue("chapter 1 harus ketemu walau di halaman 2: " + labels(detail),
                    labels(detail).contains("Chapter 1"));
            boolean fetchedSecondPage = false;
            for (String url : CHAPTER_REQUESTS) {
                if (url.contains("page=2")) fetchedSecondPage = true;
            }
            assertTrue("pencarian harus memuat halaman lanjutan: " + CHAPTER_REQUESTS,
                    fetchedSecondPage);
        }
    }

    @Test public void pembuatanUlangActivityTidakMemuatUlangData() throws Exception {
        try (ActivityController<Detail> controller =
                     Robolectric.buildActivity(Detail.class, intent()).setup()) {
            Detail detail = controller.get();
            await(detail);
            int detailCalls = DETAIL_CALLS.get();
            int chapterCalls = CHAPTER_REQUESTS.size();
            List<String> before = labels(detail);

            controller.recreate();
            await(controller.get());

            assertEquals("detail tidak boleh di-fetch ulang saat recreate",
                    detailCalls, DETAIL_CALLS.get());
            assertEquals("chapter tidak boleh di-fetch ulang saat recreate",
                    chapterCalls, CHAPTER_REQUESTS.size());
            assertEquals(before, labels(controller.get()));
        }
    }
}