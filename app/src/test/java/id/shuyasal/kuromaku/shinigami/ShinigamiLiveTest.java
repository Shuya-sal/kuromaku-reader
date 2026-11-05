package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ShinigamiLiveTest {

    @Test
    public void liveSourceReturnsCatalogueSearchDetailChaptersAndOrderedImages() throws Exception {
        assumeTrue("1".equals(System.getenv("KUROMAKU_LIVE")));
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(ShinigamiConfig.FILE_NAME, 0).edit().clear().commit();
        ShinigamiSource source = new ShinigamiSource(context);

        ShinigamiSource.Page latest = source.catalog(1, ShinigamiSource.Sort.LATEST);
        assertFalse(latest.items.isEmpty());
        assertTrue(latest.totalPages > 1);
        ShinigamiSource.Page second = source.catalog(2, ShinigamiSource.Sort.LATEST);
        assertFalse(second.items.isEmpty());
        assertFalse(latest.items.get(0).id.equals(second.items.get(0).id));

        ShinigamiSource.Page found = source.search("solo", 1);
        assertFalse(found.items.isEmpty());
        assertTrue(found.items.get(0).title.toLowerCase().contains("solo"));

        ShinigamiManga first = latest.items.get(0);
        ShinigamiManga detail = source.detail(first.id);
        assertEquals(first.id, detail.id);
        assertFalse(detail.title.isEmpty());

        ShinigamiSource.ChapterPage chapters = source.chapters(first.id, 1);
        assertFalse(chapters.items.isEmpty());
        ShinigamiChapter chapter = chapters.items.get(0);
        assertEquals(first.id, chapter.mangaId);

        ShinigamiSource.ChapterImages images = source.chapterImages(chapter.id);
        assertFalse(images.urls.isEmpty());
        for (String url : images.urls) assertTrue(ShinigamiConfig.isValidResource(url));
        System.out.println("LIVE manga=" + detail.title + " id=" + detail.id
                + " status=" + detail.statusLabel() + " genres=" + detail.genres
                + " catalogPages=" + latest.totalPages + " chapters=" + chapters.items.size()
                + " chapter=" + chapter.label() + " images=" + images.urls.size()
                + " firstImage=" + images.urls.get(0)
                + " searchFirst=" + found.items.get(0).title);
    }
}
