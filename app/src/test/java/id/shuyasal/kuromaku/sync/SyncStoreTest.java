package id.shuyasal.kuromaku.sync;

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
 * Inti sinkronisasi: pasangan judul bertahan, progres mengalir dua arah,
 * status selesai tidak pernah dicabut, dan melepas tautan tidak menghapus apa pun.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SyncStoreTest {

    private SyncStore sync;

    @Before public void siapkan() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(SyncStore.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        sync = new SyncStore(context);
    }

    private SyncStore.Series tautkan() {
        return sync.link(SyncStore.SOURCE_SHINIGAMI, "shini-1", "Black Clover",
                "https://shinigami/x/black-clover",
                SyncStore.SOURCE_KOMIKU, "black-clover-indonesia", "Black Clover",
                "https://komiku.org/manga/black-clover-indonesia/", "manual");
    }

    @Test public void pasanganJudulTersimpanUntukKeduaSumber() {
        SyncStore.Series series = tautkan();
        assertNotNull(series);
        assertTrue(series.linked(SyncStore.SOURCE_SHINIGAMI));
        assertTrue(series.linked(SyncStore.SOURCE_KOMIKU));
        assertEquals("black-clover-indonesia", series.mangaIdFor(SyncStore.SOURCE_KOMIKU));

        assertNotNull("ditemukan dari sisi Server 2",
                sync.forSource(SyncStore.SOURCE_SHINIGAMI, "shini-1"));
        assertNotNull("ditemukan dari sisi Server 3",
                sync.forSource(SyncStore.SOURCE_KOMIKU, "black-clover-indonesia"));
    }

    @Test public void menautkanUlangTidakMembuatDuaIdentitas() {
        tautkan();
        sync.link(SyncStore.SOURCE_KOMIKU, "black-clover-indonesia", "Black Clover", "",
                SyncStore.SOURCE_SHINIGAMI, "shini-1", "Black Clover", "", "manual");

        assertEquals("tetap satu karya kanonis", 1, sync.series().size());
    }

    @Test public void progresMengalirDariServer2KeServer3() {
        SyncStore.Series series = tautkan();
        sync.recordOpened(series.id, ChapterKey.of("Chapter 10"), SyncStore.SOURCE_SHINIGAMI,
                true, 1000);

        SyncStore.Progress seen = sync.progress(series.id, ChapterKey.of("Chapter 10"));
        assertNotNull("chapter 10 terlihat dari sisi mana pun", seen);
        assertTrue("ditandai selesai", seen.completed);
        assertEquals(SyncStore.SOURCE_SHINIGAMI, seen.lastSource);
    }

    @Test public void progresMengalirBalikDariServer3KeServer2() {
        SyncStore.Series series = tautkan();
        sync.recordOpened(series.id, ChapterKey.of("Chapter 11"), SyncStore.SOURCE_KOMIKU,
                true, 2000);

        SyncStore.Progress seen = sync.progress(series.id, ChapterKey.of("Chapter 11"));
        assertNotNull(seen);
        assertTrue(seen.completed);
        assertEquals(SyncStore.SOURCE_KOMIKU, seen.lastSource);
    }

    @Test public void statusSelesaiTidakDicabutSaatMembukaChapterLama() {
        SyncStore.Series series = tautkan();
        sync.recordOpened(series.id, ChapterKey.of("Chapter 10"), SyncStore.SOURCE_SHINIGAMI,
                true, 1000);
        // Pengguna membuka kembali chapter 3 tanpa menandainya selesai.
        sync.recordOpened(series.id, ChapterKey.of("Chapter 3"), SyncStore.SOURCE_KOMIKU,
                false, 2000);

        assertTrue("chapter 10 tetap selesai",
                sync.progress(series.id, ChapterKey.of("Chapter 10")).completed);
        assertFalse("chapter 3 memang belum selesai",
                sync.progress(series.id, ChapterKey.of("Chapter 3")).completed);
    }

    @Test public void kejadianLebihTuaDiabaikanSehinggaTidakBerputar() {
        SyncStore.Series series = tautkan();
        sync.recordOpened(series.id, ChapterKey.of("Chapter 5"), SyncStore.SOURCE_SHINIGAMI,
                true, 5000);
        // Kejadian "lebih tua" yang datang terlambat (mis. dari proyeksi lain).
        sync.recordOpened(series.id, ChapterKey.of("Chapter 5"), SyncStore.SOURCE_KOMIKU,
                false, 1000);

        SyncStore.Progress progress = sync.progress(series.id, ChapterKey.of("Chapter 5"));
        assertTrue("status selesai dipertahankan", progress.completed);
        assertEquals("sumber terakhir tidak ditimpa kejadian lama",
                SyncStore.SOURCE_SHINIGAMI, progress.lastSource);
    }

    @Test public void catatanSamaDuaKaliTidakMenggandakan() {
        SyncStore.Series series = tautkan();
        sync.recordOpened(series.id, ChapterKey.of("Chapter 7"), SyncStore.SOURCE_KOMIKU,
                true, 3000);
        sync.recordOpened(series.id, ChapterKey.of("Chapter 7"), SyncStore.SOURCE_KOMIKU,
                true, 3000);

        assertEquals("satu baris per chapter", 1, sync.progressFor(series.id).size());
    }

    @Test public void chapterTertinggiSelesaiDikenali() {
        SyncStore.Series series = tautkan();
        sync.recordOpened(series.id, ChapterKey.of("Chapter 2"), SyncStore.SOURCE_SHINIGAMI,
                true, 1000);
        sync.recordOpened(series.id, ChapterKey.of("Chapter 10"), SyncStore.SOURCE_SHINIGAMI,
                true, 2000);
        sync.recordOpened(series.id, ChapterKey.of("Chapter 5"), SyncStore.SOURCE_SHINIGAMI,
                true, 3000);

        assertEquals(ChapterKey.of("Chapter 10"), sync.lastCompleted(series.id));
    }

    @Test public void melepasTautanTidakMenghapusProgres() {
        SyncStore.Series series = tautkan();
        sync.recordOpened(series.id, ChapterKey.of("Chapter 10"), SyncStore.SOURCE_SHINIGAMI,
                true, 1000);

        sync.unlink(SyncStore.SOURCE_KOMIKU, "black-clover-indonesia");

        assertNull("tautan Server 3 hilang",
                sync.forSource(SyncStore.SOURCE_KOMIKU, "black-clover-indonesia"));
        assertNotNull("tautan Server 2 tetap",
                sync.forSource(SyncStore.SOURCE_SHINIGAMI, "shini-1"));
        assertTrue("progres kanonis tidak dihapus",
                sync.progress(series.id, ChapterKey.of("Chapter 10")).completed);
    }

    @Test public void tautkanUlangSetelahLepasMemakaiKaryaYangSama() {
        SyncStore.Series first = tautkan();
        sync.recordOpened(first.id, ChapterKey.of("Chapter 4"), SyncStore.SOURCE_SHINIGAMI,
                true, 1000);
        sync.unlink(SyncStore.SOURCE_KOMIKU, "black-clover-indonesia");

        SyncStore.Series again = tautkan();
        assertEquals("identitas karya dipertahankan", first.id, again.id);
        assertTrue("progres lama masih terlihat",
                sync.progress(again.id, ChapterKey.of("Chapter 4")).completed);
    }

    @Test public void judulTanpaPasanganTidakPunyaProgres() {
        assertNull(sync.forSource(SyncStore.SOURCE_KOMIKU, "tidak-tertaut"));
        assertEquals(0, sync.progressFor("cs_tidak_ada").size());
    }

    @Test public void sumberLainTerbacaDariSatuSisi() {
        SyncStore.Series series = tautkan();
        List<String> others = series.otherSources(SyncStore.SOURCE_SHINIGAMI);
        assertEquals(1, others.size());
        assertEquals(SyncStore.SOURCE_KOMIKU, others.get(0));
    }
}