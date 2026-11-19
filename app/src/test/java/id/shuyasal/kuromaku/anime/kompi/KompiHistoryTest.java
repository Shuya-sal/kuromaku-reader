package id.shuyasal.kuromaku.anime.kompi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

/**
 * Riwayat tontonan Anime Server 2.
 *
 * <p>Halaman ini sebelumnya tidak mencatat apa pun, jadi tidak punya riwayat.
 * Yang diuji: entri tercatat per anime (bukan per episode), urutan terbaru di
 * atas, sumber lain tidak tersentuh, dan berkas rusak tidak membuat aplikasi
 * berhenti.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class KompiHistoryTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    @Before public void bersihkan() {
        context.getSharedPreferences(KompiHistory.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test public void riwayatKosongDiAwal() {
        assertTrue(KompiHistory.all(context).isEmpty());
        assertNull(KompiHistory.forSlug(context, "apa-saja"));
    }

    @Test public void menontonTercatatDanTerbacaLagi() {
        KompiHistory.record(context, "one-piece", "One Piece",
                "https://contoh/cover.jpg", "Episode 3", "one-piece-episode-3", 3);

        KompiHistory.Entry entry = KompiHistory.forSlug(context, "one-piece");
        assertNotNull(entry);
        assertEquals("One Piece", entry.title);
        assertEquals("Episode 3", entry.episodeText());
        assertEquals(3, entry.episodeNumber);
        assertEquals("https://contoh/cover.jpg", entry.poster);
    }

    @Test public void episodeBaruMenggantikanEntriAnimeYangSama() {
        KompiHistory.record(context, "one-piece", "One Piece", "", "Episode 3", "ep-3", 3);
        KompiHistory.record(context, "one-piece", "One Piece", "", "Episode 4", "ep-4", 4);

        List<KompiHistory.Entry> entries = KompiHistory.all(context);
        assertEquals("satu anime satu entri", 1, entries.size());
        assertEquals("Episode 4", entries.get(0).episodeText());
    }

    @Test public void animeBerbedaTercatatTerpisahDanTerbaruDiatas() {
        KompiHistory.record(context, "anime-a", "Anime A", "", "Episode 1", "a-1", 1);
        // Waktu sistem bisa sama dalam satu tes; entri baru tetap didahulukan
        // karena selalu disisipkan di depan.
        KompiHistory.record(context, "anime-b", "Anime B", "", "Episode 2", "b-2", 2);

        List<KompiHistory.Entry> entries = KompiHistory.all(context);
        assertEquals(2, entries.size());
        assertEquals("anime-b", entries.get(0).slug);
    }

    @Test public void slugKosongTidakDicatat() {
        KompiHistory.record(context, "", "Tanpa Slug", "", "Episode 1", "x", 1);
        KompiHistory.record(context, null, "Tanpa Slug", "", "Episode 1", "x", 1);
        assertTrue(KompiHistory.all(context).isEmpty());
    }

    @Test public void menghapusSatuEntriTidakMenyentuhYangLain() {
        KompiHistory.record(context, "anime-a", "Anime A", "", "Episode 1", "a-1", 1);
        KompiHistory.record(context, "anime-b", "Anime B", "", "Episode 2", "b-2", 2);

        KompiHistory.remove(context, "anime-a");

        assertNull(KompiHistory.forSlug(context, "anime-a"));
        assertNotNull("entri lain tetap ada", KompiHistory.forSlug(context, "anime-b"));
    }

    @Test public void berkasRusakDianggapKosongBukanCrash() {
        context.getSharedPreferences(KompiHistory.FILE_NAME, Context.MODE_PRIVATE)
                .edit().putString("entries", "{bukan json").apply();
        assertTrue(KompiHistory.all(context).isEmpty());
    }

    @Test public void jumlahEntriDibatasi() {
        for (int i = 0; i < KompiHistory.MAX_ENTRIES + 12; i++) {
            KompiHistory.record(context, "anime-" + i, "Anime " + i, "", "Episode 1", "e-" + i, 1);
        }
        assertEquals(KompiHistory.MAX_ENTRIES, KompiHistory.all(context).size());
    }

    @Test public void labelEpisodeMemakaiNomorBilaLabelKosong() {
        KompiHistory.Entry entry = new KompiHistory.Entry("s", "T", "", "", "e", 7, 0);
        assertEquals("Episode 7", entry.episodeText());

        KompiHistory.Entry tanpaNomor = new KompiHistory.Entry("s", "T", "", "", "e", 0, 0);
        assertEquals("Episode", tanpaEpisode(tanpaNomor));
    }

    private String tanpaEpisode(KompiHistory.Entry entry) {
        assertFalse("tidak boleh kosong", entry.episodeText().isEmpty());
        return entry.episodeText();
    }
}
