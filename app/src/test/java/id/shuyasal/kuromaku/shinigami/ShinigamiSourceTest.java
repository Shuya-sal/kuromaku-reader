package id.shuyasal.kuromaku.shinigami;

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
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ShinigamiSourceTest {
    Context context;
    @Before public void setup() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(ShinigamiConfig.FILE_NAME, 0).edit().clear().commit();
    }
    String fixture(String name) throws IOException { return ShinigamiReaderTest.fixture(name); }
    ShinigamiSource source(String name) {
        return new ShinigamiSource(context, url -> fixture(name));
    }
    @Test public void realCataloguePreservesIdAndPagination() throws Exception {
        ShinigamiSource.Page page = source("live_manga_list.json").catalog(1, ShinigamiSource.Sort.LATEST);
        assertEquals(2, page.items.size());
        assertEquals("14423a1b-8d7e-41e2-af00-ba0ba655d5f9", page.items.get(0).id);
        assertEquals(500, page.totalPages);
        assertEquals(1000L, page.totalRecords);
    }
    @Test public void realSearchReturnsMatchesAndEncodesQuery() throws Exception {
        ShinigamiSource src = new ShinigamiSource(context, url -> {
            assertTrue(url.contains("q=solo%20leveling"));
            return fixture("live_search_solo.json");
        });
        assertEquals("Solo Leveling : Ragnarok", src.search("solo leveling", 1).items.get(0).title);
        assertTrue(source("live_search_empty.json").search("not-present", 1).items.isEmpty());
    }
    @Test public void realDetailAndChaptersAgreeOnStableIdentity() throws Exception {
        String id = "14423a1b-8d7e-41e2-af00-ba0ba655d5f9";
        ShinigamiManga manga = source("live_manga_detail.json").detail(id);
        assertEquals("Regressor Instruction Manual", manga.title);
        assertFalse(manga.description.isEmpty());
        assertFalse(manga.genres.isEmpty());
        ShinigamiSource.ChapterPage chapters = source("live_chapter_list.json").chapters(id, 1);
        assertEquals(3, chapters.items.size());
        assertEquals(210d, chapters.items.get(0).number, 0d);
        assertEquals(80, chapters.totalPages);
        assertEquals(id, chapters.items.get(0).mangaId);
    }
    @Test public void realChapterImagesStayInSourceOrder() throws Exception {
        ShinigamiSource.ChapterImages images = source("live_chapter_detail.json")
                .chapterImages("d6cd3f14-a528-4752-a546-0d14873e9a4a");
        assertEquals(18, images.urls.size());
        assertTrue(images.urls.get(0).endsWith("/0-c24320.webp"));
        assertTrue(images.urls.get(1).endsWith("/1-de0be8.webp"));
        for (String url : images.urls) assertTrue(ShinigamiConfig.isValidResource(url));
    }
    @Test public void decimalAndDuplicateNumbersUseDistinctChapterIds() throws Exception {
        JSONObject root = new JSONObject(fixture("live_chapter_list.json"));
        JSONArray data = root.getJSONArray("data");
        data.getJSONObject(0).put("chapter_number", 0.1);
        data.getJSONObject(1).put("chapter_number", 0.1);
        data.put(new JSONObject(data.getJSONObject(0).toString()));
        ShinigamiSource.ChapterPage page = new ShinigamiSource(context, url -> root.toString())
                .chapters("14423a1b-8d7e-41e2-af00-ba0ba655d5f9", 1);
        assertEquals(3, page.items.size());
        assertEquals("Chapter 0.1", page.items.get(0).label());
        assertNotEquals(page.items.get(0).id, page.items.get(1).id);
    }
    @Test public void cloudflareAndHttpRestrictionsAreExplicitAndNotRetried() throws Exception {
        for (int status : new int[]{401, 403, 429, 302}) {
            try { ShinigamiSource.decodeResponse(status, ""); fail(); }
            catch (ShinigamiSource.SourceException expected) {
                assertFalse(expected.getMessage().isEmpty());
            }
        }
        try { ShinigamiSource.decodeResponse(200, fixture("live_site_cloudflare_403.html")); fail(); }
        catch (ShinigamiSource.SourceException expected) {
            assertTrue(expected.getMessage().contains("tidak melewati"));
        }
        AtomicInteger calls = new AtomicInteger();
        ShinigamiSource src = new ShinigamiSource(context, url -> {
            calls.incrementAndGet();
            throw new ShinigamiSource.SourceException("HTTP 403");
        });
        try { src.catalog(1, ShinigamiSource.Sort.LATEST); fail(); }
        catch (ShinigamiSource.SourceException expected) { assertEquals(1, calls.get()); }
    }
    @Test public void ioRetriesAreBoundedAtTwo() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ShinigamiSource src = new ShinigamiSource(context, url -> {
            calls.incrementAndGet(); throw new IOException("timeout");
        });
        try { src.catalog(1, ShinigamiSource.Sort.LATEST); fail(); }
        catch (ShinigamiSource.SourceException expected) {
            assertEquals(2, calls.get()); assertTrue(expected.getMessage().contains("waktu tunggu"));
        }
    }
    @Test public void changedShapeIsNotReportedAsEmptyCatalogue() throws Exception {
        for (String body : new String[]{"{}", "<html>blocked</html>", "{\"retcode\":0,\"data\":{}}", "{\"retcode\":0,\"data\":[{}],\"meta\":{}}"}) {
            try { new ShinigamiSource(context, url -> body).catalog(1, ShinigamiSource.Sort.LATEST); fail(); }
            catch (ShinigamiSource.SourceException expected) { assertFalse(expected.getMessage().isEmpty()); }
        }
    }
    @Test public void imageHostsAndPathTraversalAreRejected() throws Exception {
        JSONObject root = new JSONObject(fixture("live_chapter_detail.json"));
        root.getJSONObject("data").put("base_url", "https://evil.example");
        try { new ShinigamiSource(context, url -> root.toString())
                .chapterImages("d6cd3f14-a528-4752-a546-0d14873e9a4a"); fail(); }
        catch (ShinigamiSource.SourceException expected) { assertTrue(expected.getMessage().contains("ditolak")); }
        assertFalse(ShinigamiConfig.isValidBase("https://api.shngm.io/v1/../secret"));
        assertFalse(ShinigamiConfig.isValidBase("https://api.shngm.io/%2e%2e/secret"));
        assertFalse(ShinigamiConfig.isValidBase("https://api.shngm.io:8443/v1"));
        assertFalse(ShinigamiConfig.isValidResource("https://assets.shngm.id.evil.example/a.webp"));
    }
    @Test public void invalidSecondDomainDoesNotPartiallySaveFirstDomain() {
        ShinigamiConfig cfg = new ShinigamiConfig(context);
        assertFalse(cfg.setBases("https://11.shinigami.asia/v1", "http://shinigami.asia"));
        assertEquals(ShinigamiConfig.DEFAULT_API_BASE, cfg.apiBase());
        assertEquals(ShinigamiConfig.DEFAULT_SITE_BASE, cfg.siteBase());
    }
    @Test public void offlineDownloadFailsBeforeAnyFileIsCreated() throws Exception {
        ShinigamiDownloader downloader = new ShinigamiDownloader(context);
        try {
            downloader.download("not-authorized", java.util.Collections.singletonList("https://assets.shngm.id/a.webp"), null);
            fail("Tidak boleh mengunduh tanpa izin sumber");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("izin"));
            assertFalse(downloader.chapterDir("not-authorized").exists());
        }
    }
}
