package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/**
 * Tab Unduhan hanya boleh menampilkan berkas yang benar-benar ada dan valid,
 * bukan status unduhan di memori.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ShinigamiDownloadsTest {

    private Context context;
    private ShinigamiStore store;
    private ShinigamiDownloader downloader;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, 0).edit().clear().commit();
        store = new ShinigamiStore(context);
        downloader = new ShinigamiDownloader(context);
        ShinigamiDownloadStatus.clear("chapter-a");
        ShinigamiDownloadStatus.clear("chapter-b");
        ShinigamiDownloadStatus.clear("chapter-c");
    }

    @After public void tearDown() {
        for (String id : new String[]{"chapter-a", "chapter-b", "chapter-c"}) {
            downloader.deleteChapter(id);
            ShinigamiDownloadStatus.clear(id);
        }
    }

    private void writePage(String chapterId, int index, boolean valid) throws Exception {
        File dir = downloader.chapterDir(chapterId);
        assertTrue(dir.mkdirs() || dir.isDirectory());
        File target = new File(dir, index + ".img");
        if (!valid) {
            try (FileOutputStream out = new FileOutputStream(target)) {
                out.write(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9});
            }
            return;
        }
        Bitmap bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        try (FileOutputStream out = new FileOutputStream(target)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        } finally {
            bitmap.recycle();
        }
    }

    @Test public void groupsValidChaptersByMangaAndCountsOnlyRealPages() throws Exception {
        store.setChapterOwner("chapter-a", "manga-1", "Judul Satu", "https://assets.shngm.id/c.jpg",
                12d, "Chapter 12");
        store.setChapterOwner("chapter-b", "manga-1", "Judul Satu", "https://assets.shngm.id/c.jpg",
                13d, "Chapter 13");
        store.setChapterOwner("chapter-c", "manga-2", "Judul Dua", "", 5d, "Chapter 5");
        store.setChapterPages("chapter-a", java.util.Arrays.asList("https://assets.shngm.id/chapter/0.png", "https://assets.shngm.id/chapter/1.png"));
        store.setChapterPages("chapter-b", java.util.Arrays.asList("https://assets.shngm.id/chapter/0.png", "https://assets.shngm.id/chapter/1.png"));
        store.setChapterPages("chapter-c", java.util.Collections.singletonList("https://assets.shngm.id/chapter/0.png"));
        writePage("chapter-a", 0, true);
        writePage("chapter-a", 1, true);
        writePage("chapter-b", 0, true);
        writePage("chapter-c", 0, false);

        List<ShinigamiDownloads.Manga> mangas = ShinigamiDownloads.scan(context);

        assertEquals(1, mangas.size());
        ShinigamiDownloads.Manga manga = mangas.get(0);
        assertEquals("manga-1", manga.mangaId);
        assertEquals("Judul Satu", manga.title);
        assertEquals(2, manga.chapters.size());
        assertEquals(1, manga.savedChapters());
        ShinigamiDownloads.Chapter complete = manga.chapters.get(0).complete
                ? manga.chapters.get(0) : manga.chapters.get(1);
        assertEquals("chapter-a", complete.chapterId);
        assertEquals(2, complete.pages);
        assertEquals(100, complete.percent());
        ShinigamiDownloads.Chapter partial = manga.chapters.get(0) == complete
                ? manga.chapters.get(1) : manga.chapters.get(0);
        assertFalse(partial.chapterId + " " + partial.pages + "/" + partial.expected
                + " expectedStore=" + store.expectedPages(partial.chapterId), partial.complete);
        assertEquals(50, partial.percent());
        assertTrue(manga.bytes() > 0);
    }

    @Test public void corruptPagesAreNeverCountedAsSaved() throws Exception {
        store.setChapterOwner("chapter-c", "manga-9", "Rusak", "", 1d, "Chapter 1");
        store.setChapterPages("chapter-c", java.util.Collections.singletonList("https://assets.shngm.id/chapter/0.png"));
        writePage("chapter-c", 0, false);
        ShinigamiDownloadStatus.set("chapter-c", ShinigamiDownloadStatus.SAVED, 100, "");

        assertTrue(ShinigamiDownloads.scan(context).isEmpty());
        assertNull(ShinigamiDownloads.forManga(context, "manga-9"));
    }

    @Test public void legacyDownloadWithoutOwnerFallsBackToHistory() throws Exception {
        writePage("chapter-b", 0, true);
        store.setChapterPages("chapter-b", java.util.Collections.singletonList("https://assets.shngm.id/chapter/0.png"));
        // Riwayat ditulis lewat preferensi yang sama seperti reader.
        try {
            org.json.JSONArray array = new org.json.JSONArray();
            org.json.JSONObject item = new org.json.JSONObject();
            item.put("manga_id", "manga-7");
            item.put("title", "Dari Riwayat");
            item.put("cover", "");
            item.put("chapter_id", "chapter-b");
            item.put("chapter_number", 3d);
            item.put("timestamp", System.currentTimeMillis());
            array.put(item);
            context.getSharedPreferences(ShinigamiStore.FILE_NAME, 0).edit()
                    .putString("history", array.toString()).commit();
        } catch (Exception error) {
            throw new AssertionError(error);
        }
        List<ShinigamiDownloads.Manga> mangas = ShinigamiDownloads.scan(context);
        assertEquals(1, mangas.size());
        assertEquals("manga-7", mangas.get(0).mangaId);
        assertEquals("Dari Riwayat", mangas.get(0).title);
    }

    @Test public void totalBytesAndFormattingFollowRealFiles() throws Exception {
        store.setChapterOwner("chapter-a", "manga-1", "Satu", "", 1d, "Chapter 1");
        store.setChapterPages("chapter-a", java.util.Collections.singletonList("https://assets.shngm.id/chapter/0.png"));
        writePage("chapter-a", 0, true);

        List<ShinigamiDownloads.Manga> mangas = ShinigamiDownloads.scan(context);
        long bytes = ShinigamiDownloads.totalBytes(mangas);
        assertTrue(bytes > 0);
        assertEquals(mangas.get(0).bytes(), bytes);
        assertTrue(ShinigamiDownloads.formatBytes(512).endsWith("B"));
        assertTrue(ShinigamiDownloads.formatBytes(5 * 1024L * 1024L).endsWith("MB"));
        assertNotNull(ShinigamiDownloads.forManga(context, "manga-1"));
    }
}
