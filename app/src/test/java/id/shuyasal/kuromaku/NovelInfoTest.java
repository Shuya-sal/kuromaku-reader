package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import id.shuyasal.kuromaku.data.Novel;
import id.shuyasal.kuromaku.data.NovelInfo;
import id.shuyasal.kuromaku.data.Novels;

/**
 * The static copy of Monogatari's metadata must not drift away from the live novel.
 *
 * <p>{@link NovelInfo} holds the values that were read off the source landing page
 * once, as plain constants. {@link Novels} holds the same values inside the object the
 * app actually renders. Nothing tied the two together: before this class existed the
 * name {@code NovelInfo} appeared nowhere outside its own file, so every field could
 * be edited — a wrong author, an invented status — and all 353 tests still passed.
 *
 * <p>These are claims about a real page, so they are not free to drift. The source
 * page states no publication status at all, which is exactly why the app says so in
 * words instead of guessing "Ongoing" or "Completed"; a test that only checked "not
 * empty" would happily accept a guess.
 */
public class NovelInfoTest {

    private static Novel live() {
        return Novels.byId(Novels.MONOGATARI);
    }

    /**
     * Every text field the app shows agrees with the constants.
     *
     * <p>Kept as one test on purpose: the point is that this is a single set of facts,
     * and a failure should point at the file that drifted rather than at eight tests.
     */
    @Test
    public void theTextMetadataMatchesTheLiveNovel() {
        Novel novel = live();

        assertEquals("judul", NovelInfo.TITLE, novel.title);
        assertEquals("judul Jepang", NovelInfo.TITLE_JP, novel.titleJp);
        assertEquals("judul lengkap", NovelInfo.FULL_TITLE, novel.fullTitle);
        assertEquals("penulis", NovelInfo.AUTHOR, novel.author);
        assertEquals("ilustrator", NovelInfo.ILLUSTRATOR, novel.illustrator);
        assertEquals("genre", NovelInfo.GENRES, novel.genres);
        assertEquals("status", NovelInfo.STATUS, novel.status);
        assertEquals("penerjemah", NovelInfo.TRANSLATION, novel.translation);
        assertEquals("halaman label", NovelInfo.SOURCE_LABEL_URL, novel.sourceLabelUrl);
    }

    /**
     * The status is the sentence the source never printed, not a guess.
     *
     * <p>Asserted separately from the equality above so that the rule survives even if
     * both copies are edited in the same wrong way: the app may not claim a publication
     * status the page does not state.
     */
    @Test
    public void theStatusIsTheAbsenceOfOneNotAGuess() {
        assertEquals("Tidak dicantumkan di halaman sumber", NovelInfo.STATUS);
        assertFalse("status tidak boleh kosong", NovelInfo.STATUS.trim().isEmpty());
        assertFalse("status tidak boleh dikarang jadi Ongoing",
                NovelInfo.STATUS.toLowerCase().contains("ongoing"));
        assertFalse("status tidak boleh dikarang jadi Completed",
                NovelInfo.STATUS.toLowerCase().contains("completed"));
    }

    /**
     * The synopsis paragraphs and the preview count agree with what the reader shows.
     *
     * <p>Paragraph boundaries matter: they are what the "Baca selengkapnya" cut is
     * measured in. A merged paragraph would silently change how much text is shown
     * before the tap, and the cover ratio decides how much space is reserved.
     */
    @Test
    public void theSynopsisAndCoverAgreeWithTheLiveNovel() {
        Novel novel = live();

        assertEquals("jumlah paragraf sinopsis", novel.synopsis.length, NovelInfo.SYNOPSIS.length);
        for (int i = 0; i < NovelInfo.SYNOPSIS.length; i++) {
            assertEquals("paragraf sinopsis " + (i + 1), novel.synopsis[i], NovelInfo.SYNOPSIS[i]);
        }
        assertEquals("paragraf sebelum 'Baca selengkapnya'",
                novel.synopsisPreview, NovelInfo.SYNOPSIS_PREVIEW);
        assertTrue("pratinjau harus lebih pendek dari seluruh sinopsis: " + NovelInfo.SYNOPSIS_PREVIEW,
                NovelInfo.SYNOPSIS_PREVIEW < NovelInfo.SYNOPSIS.length);

        // The constant is written as a fraction; the live value is the computed float.
        assertEquals("rasio cover", novel.coverRatio, NovelInfo.COVER_RATIO, 1e-5);
        assertEquals("tinggi cover untuk lebar 720",
                Math.round(720 / novel.coverRatio), NovelInfo.coverHeight(720));
        assertEquals("lebar 0 tidak boleh menghasilkan tinggi", 0, NovelInfo.coverHeight(0));
    }
}
