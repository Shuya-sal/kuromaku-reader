package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Panel pemilihan server: hasil pemeriksaan otomatis ditampilkan bertahap,
 * tanpa tombol periksa manual dan tanpa tombol putar palsu.
 *
 * <p>Keluhan pengguna sebelumnya: daftar penuh tulisan "Belum diperiksa" dan
 * tombol Periksa, padahal seharusnya pemeriksaan berjalan sendiri.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class OtakudesuServerPanelTest {

    private static final List<OtakudesuPlayback.Mirror> MIRRORS = Arrays.asList(
            new OtakudesuPlayback.Mirror("720p", "odstream", "a", 0),
            new OtakudesuPlayback.Mirror("480p", "odcdn", "b", 1),
            new OtakudesuPlayback.Mirror("720p", "ondesuhd", "c", 2),
            new OtakudesuPlayback.Mirror("720p", "mega", "d", 3));

    private int retries;
    private int sources;

    private final OtakudesuServerPanel.Listener listener = new OtakudesuServerPanel.Listener() {
        @Override public void onPick(OtakudesuPlayback.Mirror mirror) { }
        @Override public void onRetryFailed() { retries++; }
        @Override public void onWatchSource() { sources++; }
    };

    private static String key(int index) {
        return MIRRORS.get(index).key();
    }

    private OtakudesuServerPanel.Palette palette() {
        return new OtakudesuServerPanel.Palette(0xFF191B22, 0xFFF2F4F8, 0xFF9AA0AC,
                0xFF7FA6FF, 0x33FFFFFF);
    }

    private View build(LinkedHashMap<String, OtakudesuPlayback.Source> probed,
                       LinkedHashMap<String, OtakudesuServerPicker.Probed> failed,
                       Set<String> checking) {
        OtakudesuServerPanel panel = new OtakudesuServerPanel(
                ApplicationProvider.getApplicationContext(), palette());
        return panel.build(new ArrayList<>(MIRRORS), probed, failed, checking, listener);
    }

    private static View findViewById(View root, String tag) {
        if (tag.equals(root.getTag())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findViewById(group.getChildAt(i), tag);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static List<View> findAll(View root, String tag) {
        List<View> found = new ArrayList<>();
        if (tag.equals(root.getTag())) found.add(root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                found.addAll(findAll(group.getChildAt(i), tag));
            }
        }
        return found;
    }

    private static List<String> texts(View root, String tag) {
        List<String> out = new ArrayList<>();
        for (View view : findAll(root, tag)) {
            if (view instanceof TextView) out.add(((TextView) view).getText().toString());
        }
        return out;
    }

    private static LinkedHashMap<String, OtakudesuPlayback.Source> ready(int index, String url,
                                                                       OtakudesuPlayback.Kind kind) {
        LinkedHashMap<String, OtakudesuPlayback.Source> probed = new LinkedHashMap<>();
        probed.put(key(index), new OtakudesuPlayback.Source(url, kind, "720p"));
        return probed;
    }

    @Test public void serverSiapTampilDuluDenganTombolPutar() {
        View panel = build(ready(0, "https://cdn/x.mp4", OtakudesuPlayback.Kind.PROGRESSIVE),
                new LinkedHashMap<>(), new LinkedHashSet<>());

        assertTrue("bagian siap diputar ada",
                texts(panel, "anime_server_section").contains("Siap diputar"));
        assertEquals("hanya satu tombol aksi", 1, toStrings(panel).size());
        assertEquals("Putar", toStrings(panel).get(0));
        assertTrue("status menyebut siap",
                texts(panel, "anime_server_status").contains("Siap diputar · MP4"));
    }

    private List<String> toStrings(View panel) {
        List<String> out = new ArrayList<>();
        for (View view : findAll(panel, "anime_server_action")) {
            out.add(((Button) view).getText().toString());
        }
        return out;
    }

    @Test public void serverYangSedangDiperiksaTidakPunyaTombol() {
        Set<String> checking = new LinkedHashSet<>(Arrays.asList(key(2), key(3)));
        View panel = build(new LinkedHashMap<>(), new LinkedHashMap<>(), checking);

        assertTrue("bagian pemeriksaan ada",
                texts(panel, "anime_server_section").contains("Sedang diperiksa"));
        assertEquals("dua indikator berjalan", 2,
                findAll(panel, "anime_server_spinner").size());
        assertTrue("tidak ada tombol aksi untuk yang sedang diperiksa",
                findAll(panel, "anime_server_action").isEmpty());
        assertTrue("status menyebut memeriksa",
                texts(panel, "anime_server_status").contains("Memeriksa…"));
    }

    @Test public void serverBermasalahDilipatDiBagianTersendiri() {
        LinkedHashMap<String, OtakudesuServerPicker.Probed> failed = new LinkedHashMap<>();
        failed.put(key(2), new OtakudesuServerPicker.Probed(
                OtakudesuServerPicker.Status.PROTECTED_SERVER, null, "pemutar penyedia"));
        failed.put(key(3), new OtakudesuServerPicker.Probed(
                OtakudesuServerPicker.Status.UNAVAILABLE, null, "timeout"));

        View panel = build(ready(0, "https://cdn/x.mp4", OtakudesuPlayback.Kind.PROGRESSIVE),
                failed, new LinkedHashSet<>());

        View others = findViewById(panel, "anime_server_others");
        assertNotNull(others);
        assertEquals("bagian lain dilipat", View.GONE, others.getVisibility());
        Button toggle = (Button) findViewById(panel, "anime_server_toggle");
        assertNotNull(toggle);
        assertEquals("Server lainnya (3)", toggle.getText().toString());

        String semua = String.join("\n", texts(panel, "anime_server_status"));
        assertTrue("embed disebut tidak kompatibel", semua.contains("Tidak kompatibel"));
        assertTrue("gagal disebut tidak tersedia", semua.contains("Tidak tersedia"));
        assertTrue("alasan ikut tampil", semua.contains("timeout"));
    }

    @Test public void tidakAdaTombolPeriksaManualDanPeriksaSemua() {
        View panel = build(ready(0, "https://cdn/x.mp4", OtakudesuPlayback.Kind.PROGRESSIVE),
                new LinkedHashMap<>(), new LinkedHashSet<>());

        assertNull("tombol periksa semua sudah tidak ada",
                findViewById(panel, "anime_server_check_all"));
        for (String text : toStrings(panel)) {
            assertFalse("tidak ada tombol Periksa manual", text.equals("Periksa"));
        }
    }

    @Test public void tanpaServerSiapMunculDuaJalanKeluar() {
        View panel = build(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashSet<>());

        assertNotNull("pesan kosong tampil", findViewById(panel, "anime_server_empty"));
        Button retry = (Button) findViewById(panel, "anime_server_retry");
        Button source = (Button) findViewById(panel, "anime_server_source");
        assertNotNull(retry);
        assertNotNull(source);
        assertEquals("Coba lagi", retry.getText().toString());
        assertEquals("Tonton di Sumber", source.getText().toString());

        retry.performClick();
        source.performClick();
        assertEquals("retry memanggil pemeriksaan ulang", 1, retries);
        assertEquals("sumber dibuka lewat listener", 1, sources);
    }

    @Test public void kualitasDikelompokkanSesuaiSumber() {
        LinkedHashMap<String, OtakudesuPlayback.Source> probed = new LinkedHashMap<>();
        probed.put(key(0), new OtakudesuPlayback.Source("https://cdn/x.mp4",
                OtakudesuPlayback.Kind.PROGRESSIVE, "720p"));
        probed.put(key(1), new OtakudesuPlayback.Source("https://cdn/y.mp4",
                OtakudesuPlayback.Kind.PROGRESSIVE, "480p"));

        View panel = build(probed, new LinkedHashMap<>(), new LinkedHashSet<>());
        assertEquals(Arrays.asList("720p", "480p"), texts(panel, "anime_server_group"));
    }

    @Test public void statusHlsDisebutTerpisah() {
        View panel = build(ready(1, "https://cdn/y.m3u8", OtakudesuPlayback.Kind.HLS),
                new LinkedHashMap<>(), new LinkedHashSet<>());
        assertTrue(texts(panel, "anime_server_status").contains("Siap diputar · HLS"));
    }

    @Test public void barisTidakMemasangDuaKlikSekaligus() {
        View panel = build(ready(0, "https://cdn/x.mp4", OtakudesuPlayback.Kind.PROGRESSIVE),
                new LinkedHashMap<>(), new LinkedHashSet<>());
        View row = findViewById(panel, "anime_server_0");
        assertNotNull(row);
        assertFalse("baris tidak menangkap klik kedua", row.hasOnClickListeners());
    }

    /**
     * Inti perbaikan: panel yang <b>sama</b> berubah isi saat hasil masuk.
     *
     * <p>Bug sebelumnya: popup hanya diisi sekali, karena
     * {@code PopupWindow.setContentView()} diabaikan selama popup tampil.
     * Pengujian ini memastikan render ulang benar-benar mengganti isi view
     * yang sudah ada, bukan membuat panel baru.
     */
    @Test public void renderMemperbaruiIsiPanelYangSama() {
        OtakudesuServerPanel panel = new OtakudesuServerPanel(
                ApplicationProvider.getApplicationContext(), palette());
        Set<String> checking = new LinkedHashSet<>(Arrays.asList(key(0), key(1)));
        View view = panel.build(new ArrayList<>(MIRRORS), new LinkedHashMap<>(),
                new LinkedHashMap<>(), checking, listener);

        assertEquals("awal: dua baris diperiksa", 2,
                findAll(view, "anime_server_spinner").size());
        assertFalse("belum ada yang siap",
                texts(view, "anime_server_section").contains("Siap diputar"));

        // Hasil pertama masuk selagi panel masih terbuka.
        LinkedHashMap<String, OtakudesuPlayback.Source> probed = new LinkedHashMap<>();
        probed.put(key(0), new OtakudesuPlayback.Source("https://cdn/x.mp4",
                OtakudesuPlayback.Kind.PROGRESSIVE, "720p"));
        Set<String> sisa = new LinkedHashSet<>(Arrays.asList(key(1)));
        panel.render(new ArrayList<>(MIRRORS), probed, new LinkedHashMap<>(), sisa, listener);

        assertTrue("grup siap diputar muncul tanpa membuka ulang",
                texts(view, "anime_server_section").contains("Siap diputar"));
        assertEquals("satu tombol Putar", 1, toStrings(view).size());
        assertEquals("satu baris masih diperiksa", 1,
                findAll(view, "anime_server_spinner").size());
        assertTrue("progres ikut berubah",
                texts(view, "anime_server_progress").get(0).contains("1 siap"));
    }

    @Test public void progresAgregatJujur() {
        assertEquals("7 dari 9 diperiksa · 2 siap",
                OtakudesuServerPanel.progressText(7, 9, 2, false));
        assertEquals("Selesai · 2 siap dari 9",
                OtakudesuServerPanel.progressText(9, 9, 2, true));
        assertEquals("", OtakudesuServerPanel.progressText(0, 0, 0, true));
    }

    @Test public void serverWaktuHabisTidakBerputarSelamanya() {
        LinkedHashMap<String, OtakudesuServerPicker.Probed> failed = new LinkedHashMap<>();
        failed.put(key(2), new OtakudesuServerPicker.Probed(
                OtakudesuServerPicker.Status.TIMED_OUT, null, "tidak ada jawaban"));

        View panel = build(new LinkedHashMap<>(), failed, new LinkedHashSet<>());
        String semua = String.join("\n", texts(panel, "anime_server_status"));
        assertTrue("status waktu habis dijelaskan", semua.contains("waktu habis"));
        assertTrue("tidak ada spinner tersisa",
                findAll(panel, "anime_server_spinner").isEmpty());
    }

    @Test public void labelTombolServerMenyebutJumlahSiap() {
        assertEquals("2 siap · 9 mirror", OtakudesuServerPanel.counter(2, 9));
        assertEquals("9 mirror", OtakudesuServerPanel.counter(0, 9));
        assertEquals("Server", OtakudesuServerPanel.counter(0, 0));
    }
}