package id.shuyasal.kuromaku.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

/**
 * Alur yang diminta instruksi, diuji ujung ke ujung pada lapisan data:
 * baca Chapter 10 di Server 2 → chapter itu terlihat selesai dari Server 3,
 * dan sebaliknya. Juga dijaga: judul yang belum dipasangkan tidak ikut campur.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SyncBridgeTest {

    private Context context;
    private SyncBridge bridge;

    @Before public void siapkan() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(SyncStore.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        bridge = new SyncBridge(context);
    }

    private void tautkan() {
        bridge.link(SyncBridge.SOURCE_SHINIGAMI, "shini-1", "Black Clover", "",
                SyncBridge.SOURCE_KOMIKU, "black-clover-indonesia", "Black Clover", "", "manual");
    }

    @Test public void bacaDiServer2TerlihatDiServer3() {
        tautkan();
        bridge.onChapterOpened(SyncBridge.SOURCE_SHINIGAMI, "shini-1", "Chapter 10", true);

        assertTrue("terlihat selesai dari Server 3",
                bridge.isCompletedAnywhere(SyncBridge.SOURCE_KOMIKU, "black-clover-indonesia",
                        "Chapter 10"));
    }

    @Test public void bacaDiServer3TerlihatDiServer2() {
        tautkan();
        bridge.onChapterOpened(SyncBridge.SOURCE_KOMIKU, "black-clover-indonesia",
                "Chapter 11", true);

        assertTrue("terlihat selesai dari Server 2",
                bridge.isCompletedAnywhere(SyncBridge.SOURCE_SHINIGAMI, "shini-1", "Chapter 11"));
    }

    @Test public void labelBerbedaBentukTetapCocok() {
        tautkan();
        // Sumber 2 menulis "Ch. 12", sumber 3 menulis "Chapter 12".
        bridge.onChapterOpened(SyncBridge.SOURCE_SHINIGAMI, "shini-1", "Ch. 12", true);

        assertTrue(bridge.isCompletedAnywhere(SyncBridge.SOURCE_KOMIKU,
                "black-clover-indonesia", "Chapter 12"));
    }

    @Test public void judulBelumDipasangkanTidakIkutSinkron() {
        bridge.onChapterOpened(SyncBridge.SOURCE_SHINIGAMI, "shini-1", "Chapter 10", true);

        assertFalse("tanpa pasangan, tidak ada klaim sinkron",
                bridge.isCompletedAnywhere(SyncBridge.SOURCE_KOMIKU, "black-clover-indonesia",
                        "Chapter 10"));
    }

    @Test public void judulBerbedaTidakSalingMencemari() {
        tautkan();
        bridge.link(SyncBridge.SOURCE_SHINIGAMI, "shini-2", "Demonic Emperor", "",
                SyncBridge.SOURCE_KOMIKU, "demonic-emperor", "Demonic Emperor", "", "manual");

        bridge.onChapterOpened(SyncBridge.SOURCE_SHINIGAMI, "shini-1", "Chapter 10", true);

        assertFalse("Demonic Emperor tidak ikut tercentang",
                bridge.isCompletedAnywhere(SyncBridge.SOURCE_KOMIKU, "demonic-emperor",
                        "Chapter 10"));
    }

    @Test public void chapterTidakSelesaiTidakDicapSelesai() {
        tautkan();
        bridge.onChapterOpened(SyncBridge.SOURCE_KOMIKU, "black-clover-indonesia",
                "Chapter 9", false);

        assertFalse(bridge.isCompletedAnywhere(SyncBridge.SOURCE_SHINIGAMI, "shini-1",
                "Chapter 9"));
        assertNotNull("namun tetap tercatat dibuka",
                bridge.canonical(SyncBridge.SOURCE_SHINIGAMI, "shini-1", "Chapter 9"));
    }

    @Test public void keadaanTautanUntukBadge() {
        assertFalse(bridge.stateFor(SyncBridge.SOURCE_KOMIKU, "black-clover-indonesia").linked);

        tautkan();
        SyncBridge.LinkState state = bridge.stateFor(SyncBridge.SOURCE_KOMIKU,
                "black-clover-indonesia");
        assertTrue(state.linked);
        assertEquals("Manga Server 2", state.otherSourceLabel());
    }

    @Test public void progresBertahanSetelahStoreDibuatUlang() {
        tautkan();
        bridge.onChapterOpened(SyncBridge.SOURCE_SHINIGAMI, "shini-1", "Chapter 20", true);

        // Simulasi restart aplikasi: instance baru membaca berkas yang sama.
        SyncBridge afterRestart = new SyncBridge(context);
        assertTrue(afterRestart.isCompletedAnywhere(SyncBridge.SOURCE_KOMIKU,
                "black-clover-indonesia", "Chapter 20"));
        assertTrue(afterRestart.stateFor(SyncBridge.SOURCE_SHINIGAMI, "shini-1").linked);
    }

    @Test public void chapterTerakhirSelesaiDipakaiUntukLanjutMembaca() {
        tautkan();
        bridge.onChapterOpened(SyncBridge.SOURCE_SHINIGAMI, "shini-1", "Chapter 10", true);
        bridge.onChapterOpened(SyncBridge.SOURCE_SHINIGAMI, "shini-1", "Chapter 12", true);

        List<String> labels = Arrays.asList("Chapter 10", "Chapter 11", "Chapter 12", "Chapter 13");
        assertEquals("Chapter 12", bridge.lastCompletedLabel(SyncBridge.SOURCE_KOMIKU,
                "black-clover-indonesia", labels));
        assertEquals("Chapter 13", bridge.nextAfterLastCompleted(SyncBridge.SOURCE_KOMIKU,
                "black-clover-indonesia", labels));
    }

    @Test public void daftarSumberSinkronBerisiDuaServer() {
        List<String> sources = SyncBridge.sources();
        assertEquals(2, sources.size());
        assertTrue(sources.contains(SyncStore.SOURCE_SHINIGAMI));
        assertTrue(sources.contains(SyncStore.SOURCE_KOMIKU));
    }
}