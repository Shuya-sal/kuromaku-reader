package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

/**
 * Pemeriksaan langsung ke sumber Manga Server 2 yang baru. Bukan bagian dari
 * suite rilis (bergantung jaringan), dipakai sekali untuk membuktikan alamat
 * dan bentuk respons benar-benar cocok dengan pembaca di aplikasi.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h640dp-mdpi")
public class ShinigamiLiveSourceCheck {

    @Ignore("bergantung jaringan: jalankan manual saat memeriksa sumber baru")
    @Test public void sumberBaruTerbacaSampaiGambarChapter() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        ShinigamiSource source = new ShinigamiSource(context);

        ShinigamiSource.Page popular = source.feed(ShinigamiSource.Feed.POPULAR, 1, "", null, null);
        assertFalse("katalog harus berisi judul", popular.items.isEmpty());
        ShinigamiManga first = popular.items.get(0);
        assertFalse("judul tidak kosong", first.title.isEmpty());
        assertFalse("cover harus lolos allowlist", first.coverUrl.isEmpty());
        System.out.println("katalog populer: " + popular.items.size() + " judul, pertama "
                + first.title + " / cover " + first.coverUrl);

        ShinigamiSource.Page newestTitles =
                source.feed(ShinigamiSource.Feed.NEW_TITLES, 1, "", null, null);
        assertFalse("tab Manga Terbaru harus berisi judul", newestTitles.items.isEmpty());
        long firstCreated = newestTitles.items.get(0).createdAtMillis;
        long secondCreated = newestTitles.items.get(1).createdAtMillis;
        assertTrue("judul terbaru dulu", firstCreated >= secondCreated);
        System.out.println("tab Manga Terbaru pertama: " + newestTitles.items.get(0).title
                + " (" + newestTitles.items.get(0).createdAtMillis + ")");

        ShinigamiSource.Page search = source.search("nano", 1);
        assertFalse("pencarian harus berisi hasil", search.items.isEmpty());
        System.out.println("pencarian 'nano': " + search.items.get(0).title);

        ShinigamiManga detail = source.detail(first.id);
        assertTrue("detail judul sama", detail.title.equals(first.title));
        System.out.println("detail: " + detail.title + " status=" + detail.statusLabel()
                + " genre=" + detail.genres.size() + " chapter terakhir=" + detail.latestChapterNumber);

        ShinigamiSource.ChapterPage chapters = source.chapters(first.id, 1, "desc");
        assertFalse("daftar chapter harus terisi", chapters.items.isEmpty());
        ShinigamiChapter chapter = chapters.items.get(0);
        System.out.println("chapter terbaru: #" + chapter.number + " " + chapter.title
                + " totalPages=" + chapters.totalPages);
        assertTrue("chapter menunjuk manga yang sama", chapter.mangaId.equals(first.id));

        ShinigamiSource.ChapterImages images = source.chapterImages(chapter.id);
        assertFalse("gambar chapter harus ada", images.urls.isEmpty());
        String url = images.urls.get(0);
        assertTrue("gambar harus lolos allowlist: " + url,
                ShinigamiConfig.isValidResource(url));
        System.out.println("gambar chapter: " + images.urls.size() + " halaman, pertama " + url);

        List<ShinigamiSource.Genre> genres = source.genres();
        assertFalse("genre harus terisi", genres.isEmpty());
        System.out.println("genre: " + genres.size() + " pertama " + genres.get(0).slug);
        assertNotNull(chapter.id);
    }
}
