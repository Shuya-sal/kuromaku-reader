package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Pembatalan unduhan dan pilihan kualitas.
 *
 * <p>Yang dijaga: pembatalan menunjuk satu episode (bukan posisi baris, bukan
 * semua unduhan), berkas lengkap tidak pernah dihapus, dan kualitas hanya
 * ditawarkan bila berkasnya benar-benar ada.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AnimeDownloadCancelTest {

    private Context context;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        AnimeDownloadStatus.reset();
        AnimeDownloadPolicy.unlockForTesting(true);
    }

    @After public void tearDown() {
        AnimeDownloadStatus.reset();
        AnimeDownloadPolicy.unlockForTesting(null);
    }

    // ------------------------------------------------------------ pembatalan

    @Test public void namaKerjaMemuatKualitasSehinggaTidakSalingMenimpa() {
        String a = AnimeDownloadQueue.workName("ep1", "720p");
        String b = AnimeDownloadQueue.workName("ep1", "480p");
        assertNotEquals("kualitas berbeda = pekerjaan berbeda", a, b);
        assertTrue(a.startsWith("anime-download-ep1"));
        assertTrue(a.contains("720p"));
        assertEquals("kualitas kosong tetap punya nama", "anime-download-ep1-auto",
                AnimeDownloadQueue.workName("ep1", ""));
    }

    @Test public void tagEpisodeStabilDanTidakBergantungKualitas() {
        assertEquals(AnimeDownloadQueue.episodeTag("ep1"),
                AnimeDownloadQueue.episodeTag("ep1"));
        assertNotEquals(AnimeDownloadQueue.episodeTag("ep1"),
                AnimeDownloadQueue.episodeTag("ep2"));
        assertFalse("tag tidak memuat kualitas",
                AnimeDownloadQueue.episodeTag("ep1").contains("720p"));
    }

    @Test public void membatalkanTanpaUnduhanBerjalanTidakMengubahApaPun() {
        AnimeDownloadQueue.CancelResult result =
                AnimeDownloadQueue.cancel(context, "fxskc", "ep1");
        assertEquals(AnimeDownloadQueue.CancelResult.NOT_RUNNING, result.outcome);
        assertEquals(AnimeDownloadStatus.NOT_DOWNLOADED, AnimeDownloadStatus.state("ep1"));
    }

    @Test public void membatalkanMembuangBerkasSementaraSaja() throws Exception {
        File part = AnimeDownloads.partFile(context, "fxskc", "ep1");
        part.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(part)) {
            out.write(new byte[4096]);
        }
        AnimeDownloadStatus.set("ep1", AnimeDownloadStatus.DOWNLOADING, 40, "40%", "720p");

        AnimeDownloadQueue.CancelResult result =
                AnimeDownloadQueue.cancel(context, "fxskc", "ep1");

        assertEquals(AnimeDownloadQueue.CancelResult.CANCELLED, result.outcome);
        assertFalse("berkas sementara dibuang", part.exists());
        assertEquals("status kembali ke awal", AnimeDownloadStatus.NOT_DOWNLOADED,
                AnimeDownloadStatus.state("ep1"));
    }

    @Test public void unduhanLainTidakIkutDibatalkan() throws Exception {
        File lain = AnimeDownloads.partFile(context, "fxskc", "ep2");
        lain.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(lain)) {
            out.write(new byte[2048]);
        }
        AnimeDownloadStatus.set("ep2", AnimeDownloadStatus.DOWNLOADING, 20, "20%", "480p");

        AnimeDownloadQueue.cancel(context, "fxskc", "ep1");

        assertTrue("berkas episode lain tetap ada", lain.exists());
        assertEquals("status episode lain tidak tersentuh", AnimeDownloadStatus.DOWNLOADING,
                AnimeDownloadStatus.state("ep2"));
    }

    @Test public void berkasLengkapTidakDihapusSaatPembatalanBerlomba() throws Exception {
        // Unduhan selesai tepat sebelum X ditekan: hasil yang sah dipertahankan.
        File video = AnimeDownloads.videoFile(context, "fxskc", "ep1");
        video.getParentFile().mkdirs();
        // Berkas harus benar-benar berbentuk media (kotak ftyp), bukan byte acak:
        // itulah yang diperiksa isSaved sebelum menyebut episode tersimpan.
        byte[] isi = new byte[8192];
        isi[0] = 0; isi[1] = 0; isi[2] = 0; isi[3] = 0x20;
        isi[4] = 'f'; isi[5] = 't'; isi[6] = 'y'; isi[7] = 'p';
        isi[8] = 'i'; isi[9] = 's'; isi[10] = 'o'; isi[11] = 'm';
        try (FileOutputStream out = new FileOutputStream(video)) {
            out.write(isi);
        }
        AnimeDownloads.writeMeta(context, new AnimeDownload("ep1", "fxskc", "FX", "",
                "Episode 1", "https://otakudesu.blog/episode/ep1/", "720p", "odcdn",
                "https://cdn.contoh.test/ep1.mp4", isi.length, System.currentTimeMillis()));
        assertTrue("berkas dianggap lengkap", AnimeDownloads.isSaved(context, "fxskc", "ep1"));

        AnimeDownloadQueue.CancelResult result =
                AnimeDownloadQueue.cancel(context, "fxskc", "ep1");

        assertEquals(AnimeDownloadQueue.CancelResult.ALREADY_SAVED, result.outcome);
        assertTrue("berkas lengkap tidak dihapus", video.exists());
        assertEquals(isi.length, video.length());
        assertTrue("status tetap tersimpan",
                AnimeDownloadStatus.state("ep1") == AnimeDownloadStatus.SAVED);
    }

    // -------------------------------------------------------------- kualitas

    @Test public void labelKualitasDinormalkan() {
        assertEquals("720p", AnimeDownloadOptions.normalizeQuality("Mp4 720p"));
        assertEquals("720p", AnimeDownloadOptions.normalizeQuality("720P"));
        assertEquals("480p", AnimeDownloadOptions.normalizeQuality("Mp4 480p"));
        assertEquals("1080p", AnimeDownloadOptions.normalizeQuality("1080p"));
        assertEquals("", AnimeDownloadOptions.normalizeQuality("unknown"));
        assertEquals("", AnimeDownloadOptions.normalizeQuality(null));
    }

    @Test public void hanyaBerkasUtuhYangDianggapBisaDisimpan() {
        assertTrue(AnimeDownloadOptions.looksLikeFile("https://cdn/x/ep1_720p.mp4"));
        assertTrue(AnimeDownloadOptions.looksLikeFile("https://cdn/x/ep1.mp4?token=abc"));
        assertTrue(AnimeDownloadOptions.looksLikeFile("https://cdn/x/ep1.mkv"));
        assertFalse("manifest streaming bukan berkas",
                AnimeDownloadOptions.looksLikeFile("https://cdn/x/master.m3u8"));
        assertFalse(AnimeDownloadOptions.looksLikeFile("https://cdn/x/manifest.mpd"));
        assertFalse("halaman embed bukan berkas",
                AnimeDownloadOptions.looksLikeFile("https://otakudesu.blog/embed/abc"));
        assertFalse(AnimeDownloadOptions.looksLikeFile(""));
    }

    @Test public void penyediaBerkasUtuhDiperiksaLebihDulu() {
        List<OtakudesuPlayback.Mirror> group = new ArrayList<>();
        group.add(new OtakudesuPlayback.Mirror("720p", "vidhide", "a"));
        group.add(new OtakudesuPlayback.Mirror("720p", "odcdn", "b"));
        group.add(new OtakudesuPlayback.Mirror("720p", "mega", "c"));

        List<OtakudesuPlayback.Mirror> ordered = AnimeDownloadOptions.probeOrder(group);
        assertEquals("odcdn", ordered.get(0).provider);
        assertEquals("urutan sisanya tetap", "vidhide", ordered.get(1).provider);
        assertEquals(3, ordered.size());
    }

    @Test public void pilihanTidakTersediaMenjelaskanAlasannya() {
        AnimeDownloadOptions.Option option =
                AnimeDownloadOptions.Option.missing("720p", "Server hanya menyediakan pemutar.");
        assertFalse(option.available);
        assertEquals("Server hanya menyediakan pemutar.", option.detail());
        assertEquals(0L, option.sizeBytes);
    }

    @Test public void pilihanTersediaMenyebutServerDanUkuran() {
        AnimeDownloadOptions.Option option = new AnimeDownloadOptions.Option("720p", "odcdn",
                "https://cdn/x/ep1.mp4", "PROGRESSIVE", 142991170L, true, "");
        assertTrue(option.available);
        assertTrue("nama server tampil", option.detail().contains("odcdn"));
        assertTrue("ukuran nyata tampil",
                option.detail().contains(AnimeDownload.size(142991170L)));
    }
}