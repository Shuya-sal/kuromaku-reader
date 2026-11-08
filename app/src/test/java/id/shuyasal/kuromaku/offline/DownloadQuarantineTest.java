package id.shuyasal.kuromaku.offline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Karantina pekerjaan unduhan yang gagal berulang.
 *
 * <p>Yang dijaga: pekerjaan yang sudah selesai tidak pernah dikarantina, batas
 * percobaan dihormati, dan kunci episode bisa dibaca dari tag pekerjaan.
 */
public class DownloadQuarantineTest {

    @Test public void pekerjaanSelesaiTidakDikarantina() {
        assertFalse(DownloadQuarantine.judge(true, 99).quarantine);
    }

    @Test public void pekerjaanBaruTidakDikarantina() {
        assertFalse(DownloadQuarantine.judge(false, 0).quarantine);
        assertFalse(DownloadQuarantine.judge(false, 1).quarantine);
        assertFalse(DownloadQuarantine.judge(false, 2).quarantine);
    }

    @Test public void pekerjaanYangHabisPercobaanDikarantina() {
        DownloadQuarantine.Verdict verdict = DownloadQuarantine.judge(false, 3);
        assertTrue(verdict.quarantine);
        assertTrue("alasan harus bisa dibaca pengguna",
                verdict.reason.contains("coba lagi"));
    }

    @Test public void kunciEpisodeDibacaDariTag() {
        assertEquals("ep1", DownloadQuarantine.episodeKeyFrom(new HashSet<>(
                Arrays.asList("anime-download", "anime-download-tag-ep1"))));
        assertEquals("", DownloadQuarantine.episodeKeyFrom(new HashSet<>(
                Arrays.asList("anime-download"))));
        assertEquals("", DownloadQuarantine.episodeKeyFrom(null));
    }

    @Test public void tagKarantinaSempitTidakMenyentuhModulLain() {
        assertEquals("anime-download", DownloadQuarantine.ANIME_TAG);
        assertFalse("tag novel tidak boleh ikut",
                DownloadQuarantine.ANIME_TAG.contains("novel"));
        assertFalse("tag manga tidak boleh ikut",
                DownloadQuarantine.ANIME_TAG.contains("shinigami"));
    }
}
