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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Panel pemilihan server: tidak boleh menawarkan tombol putar untuk mirror
 * yang belum atau tidak bisa diputar.
 *
 * <p>Keluhan pengguna: daftar server penuh tombol, padahal yang benar-benar
 * berjalan hanya satu. Karena itu panel sekarang memisahkan server
 * terverifikasi dari sisanya, dan tombol "Putar" hanya muncul pada yang
 * terverifikasi.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class OtakudesuServerPanelTest {

    private static String key(int index) {
        return MIRRORS.get(index).key();
    }

    private static final List<OtakudesuPlayback.Mirror> MIRRORS = Arrays.asList(
            new OtakudesuPlayback.Mirror("720p", "odstream", "a", 0),
            new OtakudesuPlayback.Mirror("480p", "odcdn", "b", 1),
            new OtakudesuPlayback.Mirror("720p", "ondesuhd", "c", 2),
            new OtakudesuPlayback.Mirror("720p", "mega", "d", 3));

    private final OtakudesuServerPanel.Listener listener = new OtakudesuServerPanel.Listener() {
        @Override public void onPick(OtakudesuPlayback.Mirror mirror) { }
        @Override public void onCheckAll() { }
    };

    private OtakudesuServerPanel.Palette palette() {
        return new OtakudesuServerPanel.Palette(0xFF191B22, 0xFFF2F4F8, 0xFF9AA0AC,
                0xFF7FA6FF, 0x33FFFFFF);
    }

    private View build(LinkedHashMap<String, OtakudesuPlayback.Source> probed,
                       LinkedHashMap<String, OtakudesuServerPicker.Probed> failed) {
        OtakudesuServerPanel panel = new OtakudesuServerPanel(
                ApplicationProvider.getApplicationContext(), palette());
        return panel.build(new java.util.ArrayList<>(MIRRORS), probed, failed,
                new java.util.ArrayList<>(), listener);
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

    private static java.util.List<View> findAll(View root, String tag) {
        java.util.List<View> found = new java.util.ArrayList<>();
        if (tag.equals(root.getTag())) found.add(root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                found.addAll(findAll(group.getChildAt(i), tag));
            }
        }
        return found;
    }

    @Test public void hanyaServerTerverifikasiYangTampilDiDaftarUtama() {
        LinkedHashMap<String, OtakudesuPlayback.Source> probed = new LinkedHashMap<>();
        probed.put(key(0),
                new OtakudesuPlayback.Source("https://cdn/x.mp4",
                        OtakudesuPlayback.Kind.PROGRESSIVE, "720p"));

        View panel = build(probed, new LinkedHashMap<>());

        // Tiga mirror lain disembunyikan di dalam wadah "server lain".
        View others = findViewById(panel, "anime_server_others");
        assertNotNull(others);
        assertEquals("mirror belum terverifikasi disembunyikan", View.GONE,
                others.getVisibility());
        assertEquals(3, ((ViewGroup) others).getChildCount());

        View toggle = findViewById(panel, "anime_server_toggle");
        assertNotNull("harus ada jalan melihat semua server", toggle);
        assertTrue(((Button) toggle).getText().toString().contains("3"));
    }

    @Test public void tombolPutarHanyaUntukServerTerverifikasi() {
        LinkedHashMap<String, OtakudesuPlayback.Source> probed = new LinkedHashMap<>();
        probed.put(key(0),
                new OtakudesuPlayback.Source("https://cdn/x.mp4",
                        OtakudesuPlayback.Kind.PROGRESSIVE, "720p"));

        View panel = build(probed, new LinkedHashMap<>());

        int play = 0;
        int periksa = 0;
        for (View action : findAll(panel, "anime_server_action")) {
            String text = ((Button) action).getText().toString();
            if (text.equals("Putar")) play++;
            if (text.equals("Periksa")) periksa++;
            assertFalse("tidak boleh ada tombol lain yang mengklaim siap",
                    !text.equals("Putar") && !text.equals("Periksa"));
        }
        assertEquals("hanya satu tombol Putar", 1, play);
        assertEquals("sisanya ditawari Periksa", 3, periksa);
    }

    @Test public void statusServerGagalTidakDisamarkanSebagaiBelumDiperiksa() {
        LinkedHashMap<String, OtakudesuServerPicker.Probed> failed = new LinkedHashMap<>();
        failed.put(key(2), new OtakudesuServerPicker.Probed(
                OtakudesuServerPicker.Status.PROTECTED_SERVER, null,
                "menunggu pemutar penyedia", "https://ondesuhd/x"));
        failed.put(key(3), new OtakudesuServerPicker.Probed(
                OtakudesuServerPicker.Status.UNAVAILABLE, null, "timeout"));

        View panel = build(new LinkedHashMap<>(), failed);

        StringBuilder semua = new StringBuilder();
        for (View status : findAll(panel, "anime_server_status")) {
            semua.append(((TextView) status).getText().toString()).append('\n');
        }
        String teks = semua.toString();
        assertTrue("server embed disebut tidak kompatibel", teks.contains("Tidak kompatibel"));
        assertTrue("server gagal disebut tidak tersedia", teks.contains("Tidak tersedia"));
        assertTrue("alasan ikut ditampilkan", teks.contains("timeout"));
        assertFalse("tidak ada klaim siap untuk server gagal",
                teks.contains("Terverifikasi dapat diputar"));
    }

    @Test public void tanpaServerTerverifikasiDaftarLainLangsungTerbuka() {
        View panel = build(new LinkedHashMap<>(), new LinkedHashMap<>());

        View others = findViewById(panel, "anime_server_others");
        assertNotNull(others);
        assertEquals("jangan sembunyikan semua pilihan saat belum ada yang siap",
                View.VISIBLE, others.getVisibility());
        assertNull("tidak perlu tombol buka-tutup bila sudah terbuka semua",
                findViewById(panel, "anime_server_toggle"));
        assertEquals("tidak ada tombol Putar sama sekali", 0,
                findAll(panel, "anime_server_action").stream()
                        .filter(v -> ((Button) v).getText().toString().equals("Putar"))
                        .count());
    }

    @Test public void statusTerverifikasiMenyebutKualitasBerkas() {
        LinkedHashMap<String, OtakudesuPlayback.Source> probed = new LinkedHashMap<>();
        probed.put(key(1), new OtakudesuPlayback.Source("https://cdn/y.m3u8",
                OtakudesuPlayback.Kind.HLS, "480p"));

        View panel = build(probed, new LinkedHashMap<>());
        TextView status = (TextView) findViewById(panel, "anime_server_status");
        assertNotNull(status);
        assertEquals("Terverifikasi dapat diputar · HLS", status.getText().toString());
    }

    @Test public void barisTidakMemasangDuaKlikSekaligus() {
        LinkedHashMap<String, OtakudesuPlayback.Source> probed = new LinkedHashMap<>();
        probed.put(key(0),
                new OtakudesuPlayback.Source("https://cdn/x.mp4",
                        OtakudesuPlayback.Kind.PROGRESSIVE, "720p"));
        View panel = build(probed, new LinkedHashMap<>());
        // Baris hanya bisa dipilih lewat tombolnya; baris itu sendiri tidak
        // lagi punya klik kedua yang membuat satu ketukan berjalan dua kali.
        View row = findViewById(panel, "anime_server_0");
        assertNotNull(row);
        assertFalse(row.hasOnClickListeners());
    }
}