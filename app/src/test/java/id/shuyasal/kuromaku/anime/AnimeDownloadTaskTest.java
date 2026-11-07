package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Aturan mana yang boleh diunduh.
 *
 * <p>Ini penjaga yang mencegah aplikasi menjanjikan unduhan untuk server yang
 * sebenarnya hanya menyediakan pemutar bertaut. Kesalahan di sini akan terlihat
 * sebagai tombol Unduh yang tidak pernah selesai, jadi aturannya diuji langsung.
 */
public class AnimeDownloadTaskTest {

    private static AnimeDownloadTask task(OtakudesuPlayback.Source source) {
        return AnimeDownloadTask.of("fxskc", "FX Senshi Kurumi-chan", "",
                "Episode 1", "https://otakudesu.blog/episode/fxskc-episode-1-sub-indo/",
                source, "odcdn");
    }

    @Test public void berkasMp4LangsungBolehDiunduh() {
        AnimeDownloadTask task = task(new OtakudesuPlayback.Source(
                "https://cdn.odcloud.net/anime/Otakudesu.io_FSKC--01_720p.mp4",
                OtakudesuPlayback.Kind.PROGRESSIVE, "720p"));
        assertTrue(task.downloadable());
        assertEquals("", task.reasonNotDownloadable());
    }

    @Test public void berkasWebmBolehDiunduh() {
        AnimeDownloadTask task = task(new OtakudesuPlayback.Source(
                "https://cdn.invalid/clip.webm", OtakudesuPlayback.Kind.PROGRESSIVE, "480p"));
        assertTrue(task.downloadable());
    }

    @Test public void hlsTidakDitawarkanSebagaiUnduhan() {
        AnimeDownloadTask task = task(new OtakudesuPlayback.Source(
                "https://cdn.invalid/master.m3u8", OtakudesuPlayback.Kind.HLS, "360p"));
        assertFalse("manifest HLS sumber ini terenkripsi, bukan berkas utuh",
                task.downloadable());
        assertTrue(task.reasonNotDownloadable().contains("HLS"));
    }

    @Test public void halamanEmbedBukanBerkasMedia() {
        AnimeDownloadTask task = task(new OtakudesuPlayback.Source(
                "https://otakudesu.blog/embed/vidhide/abc", OtakudesuPlayback.Kind.PROGRESSIVE,
                "720p"));
        assertFalse("halaman embed tidak boleh dianggap berkas", task.downloadable());
        assertTrue(task.reasonNotDownloadable().contains("bukan berkas video langsung"));
    }

    @Test public void alamatKosongTidakBisaDiunduh() {
        AnimeDownloadTask task = task(new OtakudesuPlayback.Source("",
                OtakudesuPlayback.Kind.PROGRESSIVE, "720p"));
        assertFalse(task.downloadable());
        assertTrue(task.reasonNotDownloadable().contains("alamat berkas"));
    }

    @Test public void sumberNullTidakMembuatTugas() {
        assertNull(AnimeDownloadTask.of("fxskc", "FX", "", "Episode 1", "https://x/y",
                null, "odcdn"));
    }

    @Test public void alamatDataTidakDianggapBerkas() {
        AnimeDownloadTask task = task(new OtakudesuPlayback.Source(
                "data:video/mp4;base64,AAAA", OtakudesuPlayback.Kind.PROGRESSIVE, "720p"));
        assertFalse(task.downloadable());
    }
}