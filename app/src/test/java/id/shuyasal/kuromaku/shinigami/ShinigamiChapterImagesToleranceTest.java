package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Toleransi satu gambar bermasalah pada chapter Manga Server 2.
 *
 * <p>Dulu satu entri yang alamatnya tidak bisa dipakai membatalkan SELURUH
 * chapter: pembaca melihat pesan galat dan tidak satu pun halaman muncul,
 * padahal hanya satu halaman yang bermasalah. Tes ini mengunci perilaku yang
 * benar: halaman yang rusak dilewati, sisanya tetap ditampilkan, dan urutannya
 * tidak berubah.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ShinigamiChapterImagesToleranceTest {

    private static final String HOST = "https://cdn.shngm.id";
    private static final String CHAPTER = "8b01b9ec-876c-4908-b153-97e86bc9e12f";

    private Context context;

    @Before public void siapkan() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(ShinigamiConfig.FILE_NAME, 0).edit().clear().commit();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, 0).edit().clear().commit();
    }

    /** Payload detail chapter seperti yang dikirim sumber. */
    private static String payload(String... names) throws Exception {
        JSONArray files = new JSONArray();
        for (String name : names) {
            if (name == null) files.put(JSONObject.NULL);
            else files.put(name);
        }
        JSONObject inner = new JSONObject()
                .put("chapter_id", CHAPTER)
                .put("path", "chapter/2023")
                .put("data", files);
        JSONObject data = new JSONObject()
                .put("chapter_id", CHAPTER)
                .put("base_url", HOST)
                .put("chapter", inner);
        return new JSONObject().put("retcode", 0).put("data", data).toString();
    }

    private ShinigamiSource sourceFor(String json) {
        return new ShinigamiSource(context, url -> json);
    }

    // --------------------------------------------------- satu gambar bermasalah

    @Test public void satuAlamatRusakTidakMembatalkanSeluruhChapter() throws Exception {
        // Entri ketiga bukan nama berkas yang sah (mengandung ":").
        String json = payload("1-aaa.jpg", "2-bbb.jpg", "http://jahat.example/x.jpg",
                "4-ddd.jpg", "5-eee.jpg");
        ShinigamiSource.ChapterImages images = sourceFor(json).chapterImages(CHAPTER);

        assertEquals("empat halaman tetap tampil", 4, images.urls.size());
        assertEquals("satu halaman dilewati", 1, images.skipped);
        assertTrue(images.urls.get(0).endsWith("1-aaa.jpg"));
        assertTrue(images.urls.get(1).endsWith("2-bbb.jpg"));
        assertTrue("halaman keempat tetap ikut", images.urls.get(2).endsWith("4-ddd.jpg"));
        assertTrue(images.urls.get(3).endsWith("5-eee.jpg"));
    }

    @Test public void entriBukanTeksDilewati() throws Exception {
        String json = payload("1-aaa.jpg", null, "3-ccc.jpg");
        ShinigamiSource.ChapterImages images = sourceFor(json).chapterImages(CHAPTER);
        assertEquals(2, images.urls.size());
        assertEquals(1, images.skipped);
    }

    @Test public void urutanTetapAsliSetelahAdaYangDilewati() throws Exception {
        String json = payload("1-a.jpg", "2-b.jpg", "3-c.jpg", "rusak:ini", "5-e.jpg",
                "6-f.jpg");
        List<String> urls = sourceFor(json).chapterImages(CHAPTER).urls;
        List<String> nomor = new ArrayList<>();
        for (String url : urls) {
            String name = url.substring(url.lastIndexOf('/') + 1);
            nomor.add(name.substring(0, name.indexOf('-')));
        }
        assertEquals(java.util.Arrays.asList("1", "2", "3", "5", "6"), nomor);
    }

    @Test public void semuaAlamatRusakTetapDilaporkanSebagaiGagal() throws Exception {
        // Bukan diam-diam menampilkan chapter kosong: bila tidak ada satu pun
        // halaman yang bisa dipakai, pembaca harus tahu alasannya.
        String json = payload("http://a/x.jpg", "https://host-lain/x.jpg");
        try {
            sourceFor(json).chapterImages(CHAPTER);
            fail("harus melaporkan kegagalan");
        } catch (ShinigamiSource.SourceException expected) {
            assertNotNull(expected.getMessage());
            assertFalse(expected.getMessage().isEmpty());
        }
    }

    // --------------------------------------------------------------- urutan

    @Test public void urutanTerbalikDikembalikanKeUrutanHalaman() {
        List<String> names = new ArrayList<>(java.util.Arrays.asList(
                "3-c.jpg", "1-a.jpg", "2-b.jpg"));
        List<String> urls = new ArrayList<>(java.util.Arrays.asList(
                HOST + "/3-c.jpg", HOST + "/1-a.jpg", HOST + "/2-b.jpg"));
        int changed = ShinigamiSource.reorderByPageNumber(names, urls);
        assertEquals(3, changed);
        assertEquals(java.util.Arrays.asList(
                HOST + "/1-a.jpg", HOST + "/2-b.jpg", HOST + "/3-c.jpg"), urls);
    }

    @Test public void penomoranTidakLengkapTidakDitebak() {
        // Nomor 1,2,5 bukan deret lengkap: urutan sumber dipertahankan apa
        // adanya, karena menebak urutan lebih buruk daripada mengikuti sumber.
        List<String> names = new ArrayList<>(java.util.Arrays.asList(
                "5-e.jpg", "1-a.jpg", "2-b.jpg"));
        List<String> urls = new ArrayList<>(java.util.Arrays.asList(
                HOST + "/5-e.jpg", HOST + "/1-a.jpg", HOST + "/2-b.jpg"));
        assertEquals(0, ShinigamiSource.reorderByPageNumber(names, urls));
        assertTrue(urls.get(0).endsWith("5-e.jpg"));
    }

    @Test public void nomorGandaTidakDitebak() {
        List<String> names = new ArrayList<>(java.util.Arrays.asList(
                "2-b.jpg", "2-c.jpg", "1-a.jpg"));
        List<String> urls = new ArrayList<>(java.util.Arrays.asList(
                HOST + "/2-b.jpg", HOST + "/2-c.jpg", HOST + "/1-a.jpg"));
        assertEquals(0, ShinigamiSource.reorderByPageNumber(names, urls));
        assertTrue(urls.get(0).endsWith("2-b.jpg"));
    }

    @Test public void urutanSumberYangSudahBenarTidakDiubah() {
        List<String> names = new ArrayList<>(java.util.Arrays.asList(
                "1-a.jpg", "2-b.jpg", "3-c.jpg", "4-d.jpg"));
        List<String> urls = new ArrayList<>();
        for (String name : names) urls.add(HOST + "/" + name);
        List<String> before = new ArrayList<>(urls);
        assertEquals(0, ShinigamiSource.reorderByPageNumber(names, urls));
        assertEquals(before, urls);
    }

    @Test public void nomorHalamanDibacaDariAwalNamaBerkas() {
        assertEquals(12, ShinigamiSource.leadingPageNumber("12-abc.jpg"));
        assertEquals(1, ShinigamiSource.leadingPageNumber("1-4b02d22465c4.jpg"));
        assertEquals(0, ShinigamiSource.leadingPageNumber("cover.jpg"));
        assertEquals(0, ShinigamiSource.leadingPageNumber(null));
    }

    /**
     * Urutan asli dari sumber tidak diubah.
     *
     * <p>Payload asli diambil dari sumber: array sudah urut terhadap nomor
     * halaman. Jaring pengaman tidak boleh menyentuhnya.
     */
    @Test public void payloadAsliDibiarkanApaAdanya() throws Exception {
        String json = ShinigamiReaderTest.fixture("live_chapter_detail.json");
        List<String> urls = sourceFor(json)
                .chapterImages("d6cd3f14-a528-4752-a546-0d14873e9a4a").urls;
        assertFalse("ada halaman", urls.isEmpty());

        List<String> names = new ArrayList<>();
        for (String url : urls) names.add(url.substring(url.lastIndexOf('/') + 1));
        List<String> salinan = new ArrayList<>(urls);
        assertEquals("tidak ada yang perlu diurutkan", 0,
                ShinigamiSource.reorderByPageNumber(names, urls));
        assertEquals(salinan, urls);
    }
}
