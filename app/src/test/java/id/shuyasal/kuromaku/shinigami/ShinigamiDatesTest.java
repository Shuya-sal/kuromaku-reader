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

    // ------------------------------------------------------------ label kartu

    private static final long MENIT = 60_000L;
    private static final long JAM = 60L * MENIT;
    private static final long HARI = 24L * JAM;
    private static final long SEKARANG = 1_766_000_000_000L;

    @Test public void labelKartuMemakaiJamBukanSingkatanBahasaInggris() {
        // Sumber mengirim "2026-11-17T00:19:23Z"; kartu harus berbunyi
        // "3 jam", bukan "3 hr" (singkatan bahasa Inggris).
        assertEquals("5 mnt", ShinigamiDates.relative(SEKARANG - 5 * MENIT, SEKARANG));
        String tigaJam = ShinigamiDates.relative(SEKARANG - 3 * JAM, SEKARANG);
        assertEquals("3 jam", tigaJam);
        assertFalse("harus \"jam\", bukan \"hr\": " + tigaJam, tigaJam.endsWith(" hr"));
    }

    @Test public void labelKartuHariMingguBulanTahun() {
        assertEquals("5 hr", ShinigamiDates.relative(SEKARANG - 5 * HARI, SEKARANG));
        assertEquals("2 mgg", ShinigamiDates.relative(SEKARANG - 16 * HARI, SEKARANG));
        assertEquals("3 bln", ShinigamiDates.relative(SEKARANG - 95 * HARI, SEKARANG));
        assertTrue(ShinigamiDates.relative(SEKARANG - 400 * HARI, SEKARANG).endsWith(" thn"));
    }

    @Test public void waktuMasaDepanDiperlakukanSebagaiBaru() {
        assertEquals("baru", ShinigamiDates.relative(SEKARANG + 5 * JAM, SEKARANG));
        assertEquals("", ShinigamiDates.relative(0L, SEKARANG));
    }

    @Test public void updatedLabelMemakaiTanggalUntukJudulLama() {
        assertEquals("3 jam", ShinigamiDates.updatedLabel(SEKARANG - 3 * JAM, SEKARANG));
        String tua = ShinigamiDates.updatedLabel(SEKARANG - 400 * HARI, SEKARANG);
        assertFalse("judul lama harus bertanggal: " + tua, tua.endsWith(" thn"));
        assertTrue("judul lama harus bertanggal ringkas: " + tua, tua.matches("\\d{1,2} \\S+ \\d{4}"));
    }

    @Test public void labelAngkaTidakBergantungLocalePerangkat() {
        // Angka tetap digit Latin walau perangkat berbahasa Arab.
        java.util.Locale asli = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(new java.util.Locale("ar", "EG"));
            assertEquals("3 jam", ShinigamiDates.relative(SEKARANG - 3 * JAM, SEKARANG));
            assertEquals("12 mnt", ShinigamiDates.relative(SEKARANG - 12 * MENIT, SEKARANG));
        } finally {
            java.util.Locale.setDefault(asli);
        }
    }
}
