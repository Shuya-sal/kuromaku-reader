package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Sumber Manga Server 2 yang baru tidak menerima nomor halaman: daftar chapter
 * diambil dengan {@code cursor} berantai dan penanda {@code has_next}.
 *
 * <p>Tes ini memastikan rantai cursor benar-benar diikuti: halaman 3 hanya bisa
 * diambil setelah cursor halaman 2 diketahui, dan halaman yang sudah pernah
 * diambil tidak diminta ulang.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h640dp-mdpi")
public class ShinigamiCursorPagingTest {

    static final String MANGA = "c0f1d049-ff7f-474d-8c6a-3a55e4c44147";
    static final int PER_PAGE = 500;
    static final int PAGES = 3;
    static final List<String> REQUESTS = new ArrayList<>();

    private ShinigamiSource source;

    @Before public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, 0).edit().clear().commit();
        ShinigamiSource.clearChapterIndex(null);
        REQUESTS.clear();
        source = new ShinigamiSource(context, url -> {
            REQUESTS.add(url);
            // Sumber baru mengabaikan page/page_size; hanya cursor yang dihitung.
            String cursor = param(url, "cursor");
            int page = "c2".equals(cursor) ? 2 : "c3".equals(cursor) ? 3 : 1;
            return page(page);
        });
    }

    static String param(String url, String name) {
        String marker = "&" + name + "=";
        int found = url.indexOf(marker);
        if (found < 0) return "";
        int end = url.indexOf('&', found + 1);
        int start = found + marker.length();
        return end < 0 ? url.substring(start) : url.substring(start, end);
    }

    static String page(int page) throws IOException {
        try {
            JSONArray data = new JSONArray();
            for (int index = 0; index < PER_PAGE; index++) {
                int number = (page - 1) * PER_PAGE + index + 1;
                JSONObject item = new JSONObject();
                item.put("chapter_id", "ch-" + number);
                item.put("manga_id", MANGA);
                item.put("chapter_number", number);
                item.put("chapter_title", "");
                item.put("release_date", "2026-01-01T00:00:00Z");
                data.put(item);
            }
            JSONObject meta = new JSONObject();
            meta.put("limit", PER_PAGE);
            meta.put("has_next", page < PAGES);
            if (page < PAGES) meta.put("next_cursor", "c" + (page + 1));
            JSONObject root = new JSONObject();
            root.put("retcode", 0);
            root.put("data", data);
            root.put("meta", meta);
            return root.toString();
        } catch (org.json.JSONException error) {
            throw new IOException(error);
        }
    }

    @Test public void halamanPertamaMemberiTahuMasihAdaHalamanBerikutnya() throws Exception {
        ShinigamiSource.ChapterPage first = source.chapters(MANGA, 1, "asc");

        assertEquals("jumlah chapter halaman pertama", PER_PAGE, first.items.size());
        assertEquals("nomor chapter pertama", 1d, first.items.get(0).number, 0.0001);
        assertTrue("halaman kedua masih boleh diminta", first.totalPages > 1);
        assertTrue("tidak boleh meminta halaman lain untuk halaman 1: " + REQUESTS,
                REQUESTS.size() == 1);
    }

    @Test public void halamanKetigaDiambilDenganMengikutiRantaiCursor() throws Exception {
        ShinigamiSource.ChapterPage third = source.chapters(MANGA, 3, "asc");

        assertEquals("jumlah chapter halaman ketiga", PER_PAGE, third.items.size());
        assertEquals("chapter pertama halaman ketiga", (double) (2 * PER_PAGE + 1),
                third.items.get(0).number, 0.0001);
        assertEquals("halaman 3 hanya bisa dicapai lewat cursor halaman 2", 3,
                REQUESTS.size());
        assertTrue("permintaan pertama tanpa cursor: " + REQUESTS,
                !REQUESTS.get(0).contains("cursor="));
        assertTrue("permintaan kedua memakai cursor halaman 2: " + REQUESTS,
                REQUESTS.get(1).contains("cursor=c2"));
        assertTrue("permintaan ketiga memakai cursor halaman 3: " + REQUESTS,
                REQUESTS.get(2).contains("cursor=c3"));
    }

    @Test public void halamanYangSudahDiambilTidakDimintaUlang() throws Exception {
        source.chapters(MANGA, 1, "asc");
        source.chapters(MANGA, 2, "asc");
        int afterTwo = REQUESTS.size();

        ShinigamiSource.ChapterPage again = source.chapters(MANGA, 2, "asc");

        assertEquals("halaman 2 tetap 500 chapter", PER_PAGE, again.items.size());
        assertEquals("halaman 2 tidak boleh diambil ulang", afterTwo + 1, REQUESTS.size());
        assertTrue(REQUESTS.get(REQUESTS.size() - 1).contains("cursor=c2"));
    }

    @Test public void indeksLengkapMengikutiSeluruhRantaiCursor() throws Exception {
        List<ShinigamiChapter> all = source.chaptersAll(MANGA);

        assertEquals("seluruh chapter dari tiga halaman", PER_PAGE * PAGES, all.size());
        assertEquals(1d, all.get(0).number, 0.0001);
        assertEquals((double) (PER_PAGE * PAGES), all.get(all.size() - 1).number, 0.0001);
        assertTrue("tidak ada cursor yang terlewat: " + REQUESTS,
                REQUESTS.get(REQUESTS.size() - 1).contains("cursor=c3")
                        || REQUESTS.size() >= PAGES);
        assertEquals("hanya " + PAGES + " permintaan halaman: " + REQUESTS, PAGES,
                REQUESTS.size());
    }
}
