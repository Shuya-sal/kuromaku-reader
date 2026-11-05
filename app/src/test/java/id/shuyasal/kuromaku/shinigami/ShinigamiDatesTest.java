package id.shuyasal.kuromaku.shinigami;

import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;

public class ShinigamiDatesTest {
    @Test public void mangaKeepsReleaseAndUpdateAsSeparateUtcInstants() throws Exception {
        ShinigamiManga manga = ShinigamiSource.parseManga(new JSONObject()
                .put("manga_id", "manga-1").put("title", "Title")
                .put("latest_chapter_time", "2026-11-05T03:24:41Z")
                .put("updated_at", "2026-11-05T03:39:16Z"));
        assertEquals(1793849081000L, dateField(manga, "latestChapterTimeMillis"));
        assertEquals(1793849956000L, dateField(manga, "updatedAtMillis"));
    }
    private long dateField(ShinigamiManga manga, String name) throws Exception {
        try { return ShinigamiManga.class.getField(name).getLong(manga); }
        catch (NoSuchFieldException missing) { fail("Missing UTC metadata: " + name); return 0L; }
    }
    @Test public void chapterDatePriorityDoesNotRelabelUpdatesAsReleases() {
        ShinigamiChapter chapter = new ShinigamiChapter("a", "m", 1, "", "2026-11-05T03:24:41Z", "", "2026-11-05T03:39:16Z");
        assertTrue(chapter.dateLabel(), chapter.dateLabel().startsWith("Rilis: "));
        ShinigamiChapter updateOnly = new ShinigamiChapter("a", "m", 1, "", "invalid", "", "2026-11-05T03:39:16Z");
        assertTrue(updateOnly.dateLabel(), updateOnly.dateLabel().startsWith("Diperbarui: "));
        assertEquals("Tanggal belum tersedia", new ShinigamiChapter("a", "m", 1, "", "", "").dateLabel());
    }

    @Test public void timestampsWithOffsetsAreTheSameInstant() {
        assertEquals(1793849081000L, ShinigamiDates.parse("2026-11-05T10:24:41+07:00"));
        assertEquals(1793849081123L, ShinigamiDates.parse("2026-11-05T03:24:41.123Z"));
    }
    @Test public void missingOrInvalidDatesAreNotInvented() {
        for (String value : new String[]{null, "", "null", "not a date", "2026-02-30T00:00:00Z"}) {
            assertEquals(0L, ShinigamiDates.parse(value));
            assertEquals("Tanggal belum tersedia", ShinigamiDates.format(ShinigamiDates.parse(value)));
        }
    }
}
