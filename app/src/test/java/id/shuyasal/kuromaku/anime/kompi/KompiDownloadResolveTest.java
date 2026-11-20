package id.shuyasal.kuromaku.anime.kompi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

import id.shuyasal.kuromaku.anime.AnimeDownloadTask;

/**
 * Sumber unduhan Anime Server 2.
 *
 * <p>Keluhan yang dijaga: menekan "Unduh" tidak menghasilkan berkas. Penyebabnya
 * dua — daftar unduhan resmi sumber ({@code downloads}) tidak pernah dibaca, dan
 * alamat berkas tanpa ekstensi ditolak oleh pemeriksa tugas unduhan.
 */
public class KompiDownloadResolveTest {

    @Test public void pixeldrainMenjadiAlamatBerkas() {
        assertEquals("https://pixeldrain.com/api/file/HcnyW6U9?download",
                KompiDetailActivity.resolveUnduhan("https://pixeldrain.com/u/HcnyW6U9"));
    }

    @Test public void alamatBerkasPixeldrainTetapDipakai() {
        assertEquals("https://pixeldrain.com/api/file/aPqdjXy6?download",
                KompiDetailActivity.resolveUnduhan(
                        "https://pixeldrain.com/api/file/aPqdjXy6?download"));
    }

    /**
     * Tautan sumber lain menjawab dengan halaman HTML, bukan video. Menyimpannya
     * akan menghasilkan berkas rusak, jadi harus ditolak — bukan diantrekan.
     */
    @Test public void halamanPembungkusDitolak() {
        assertNull(KompiDetailActivity.resolveUnduhan(
                "https://acefile.co/f/112412094/hyouken_s2-04-720p-animekompi-link-mkv"));
        assertNull(KompiDetailActivity.resolveUnduhan("https://gofile.io/d/UZelHRrR"));
        assertNull(KompiDetailActivity.resolveUnduhan(
                "https://www.mirrored.to/files/A2B41BBF/x"));
        assertNull(KompiDetailActivity.resolveUnduhan("https://buzzheavier.com/u0cpk3zqs2g3"));
    }

    @Test public void alamatKosongAtauNullAman() {
        assertNull(KompiDetailActivity.resolveUnduhan(null));
        assertNull(KompiDetailActivity.resolveUnduhan(""));
    }

    // ------------------------------------------------------------ tugas unduh

    @Test public void berkasMatroskaBolehDiunduh() {
        // Sumber menerbitkan .mkv untuk unduhan; sebelumnya hanya .mp4/.webm
        // yang diterima sehingga semua tautan resmi ditolak.
        AnimeDownloadTask task = new AnimeDownloadTask("anime-1", "Judul", "",
                "Episode 4", "ep-4", "720p", "PixelDrain",
                "https://pixeldrain.com/api/file/aPqdjXy6?download", "PROGRESSIVE");
        assertTrue(task.downloadable());
    }

    @Test public void alamatPixeldrainTanpaEkstensiBolehDiunduh() {
        AnimeDownloadTask task = new AnimeDownloadTask("anime-1", "Judul", "",
                "Episode 4", "ep-4", "480p", "PixelDrain",
                "https://pixeldrain.com/api/file/HcnyW6U9?download", "PROGRESSIVE");
        assertTrue("berkas pixeldrain tidak berakhiran .mp4 tetapi isinya video",
                task.downloadable());
    }

    @Test public void halamanEmbedTetapDitolak() {
        AnimeDownloadTask task = new AnimeDownloadTask("anime-1", "Judul", "",
                "Episode 4", "ep-4", "", "Server VidHide",
                "https://morencius.com/embed/tibwd0g8h6si", "PROGRESSIVE");
        assertFalse(task.downloadable());
        assertFalse("alasan harus jelas", task.reasonNotDownloadable().isEmpty());
    }

    @Test public void halamanEmbedMp4uploadDitolak() {
        AnimeDownloadTask task = new AnimeDownloadTask("anime-1", "Judul", "",
                "Episode 4", "ep-4", "", "Server Mp4upload",
                "https://www.mp4upload.com/embed-4f9en7w6gixh.html", "PROGRESSIVE");
        assertFalse(task.downloadable());
    }
}
