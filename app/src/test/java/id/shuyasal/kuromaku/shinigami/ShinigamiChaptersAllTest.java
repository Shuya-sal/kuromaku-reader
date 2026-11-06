package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Indeks chapter untuk panah reader harus lengkap, bukan satu halaman.
 *
 * <p>Fixture adalah respons asli {@code api.shngm.io} untuk manga 579 chapter:
 * asc halaman 1 = chapter 1…496 (500 item), asc halaman 2 = 497…575 (79 item).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h640dp-mdpi")
public class ShinigamiChaptersAllTest {

    static final String MANGA = "db3daa3b-3799-40c4-b4b7-fc06b0cbba8d";
    static final List<String> REQUESTS = new ArrayList<>();

    private ShinigamiSource source;

    @Before public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, 0).edit().clear().commit();
        ShinigamiSource.clearChapterIndex(null);
        REQUESTS.clear();
        source = new ShinigamiSource(context, url -> {
            REQUESTS.add(url);
            String page = url.contains("page=2") ? "2" : "1";
            String order = url.contains("sort_order=desc") ? "desc" : "asc";
            return fixture("shinigami_" + order + "_p" + page + ".json.gz");
        });
    }

    static String fixture(String name) throws IOException {
        try (InputStream raw = ShinigamiChaptersAllTest.class.getResourceAsStream(
                "/shinigami/" + name)) {
            if (raw == null) throw new IOException("fixture tidak ada: " + name);
            try (GZIPInputStream in = new GZIPInputStream(raw)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
                return out.toString("UTF-8");
            }
        }
    }

    @Test public void indeksMengambilSeluruhHalamanSumber() throws Exception {
        List<ShinigamiChapter> all = source.chaptersAll(MANGA);

        assertEquals("seluruh chapter harus terkumpul", 579, all.size());
        Set<String> ids = new HashSet<>();
        for (ShinigamiChapter chapter : all) assertTrue("id unik", ids.add(chapter.id));
        assertEquals("chapter paling awal", 1d, all.get(0).number, 0.0001);
        assertEquals("chapter paling akhir", 575d, all.get(all.size() - 1).number, 0.0001);

        boolean askedSecondPage = false;
        for (String url : REQUESTS) if (url.contains("page=2")) askedSecondPage = true;
        assertTrue("halaman kedua harus diambil: " + REQUESTS, askedSecondPage);
    }

    @Test public void indeksDiurutkanMenurutNomorChapter() throws Exception {
        List<ShinigamiChapter> all = source.chaptersAll(MANGA);
        double previous = Double.NEGATIVE_INFINITY;
        for (ShinigamiChapter chapter : all) {
            assertTrue("urutan naik: " + previous + " lalu " + chapter.number,
                    chapter.number >= previous);
            previous = chapter.number;
        }
    }

    @Test public void indeksDiCacheTanpaPermintaanBaru() throws Exception {
        source.chaptersAll(MANGA);
        int afterFirst = REQUESTS.size();
        assertTrue(afterFirst > 0);
        List<ShinigamiChapter> again = source.chaptersAll(MANGA);
        assertEquals("hasil sama dari cache", 579, again.size());
        assertEquals("tidak ada permintaan ulang", afterFirst, REQUESTS.size());
    }

    @Test public void tetanggaChapterMelintasiHalamanPagination() throws Exception {
        List<ShinigamiChapter> all = source.chaptersAll(MANGA);
        // Chapter 500 ada di halaman 1 (1…496?) bukan — pilih chapter 496 yang
        // berikutnya berada di halaman pagination kedua pada fixture asli.
        ShinigamiChapter near = null;
        for (ShinigamiChapter chapter : all) {
            if (Math.abs(chapter.number - 496d) < 0.0001) near = chapter;
        }
        assertNotNull("chapter 496 harus ada", near);
        ShinigamiChapter following = ShinigamiChapterOrder.next(all, near);
        assertNotNull("chapter setelah 496 harus ada", following);
        assertEquals(497d, following.number, 0.0001);
    }
}