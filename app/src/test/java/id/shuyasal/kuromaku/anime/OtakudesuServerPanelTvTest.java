package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.TvMode;

/**
 * Panel "Pilih Server" harus bisa dipakai dengan remote televisi.
 *
 * <p>Panel ini adalah popup berjendela sendiri, jadi rantai fokus layar induk
 * tidak menjangkaunya. Sebelumnya baris server hanya punya
 * {@code setOnClickListener} pada tombol kecil "Putar" di dalamnya — di televisi
 * itu berarti tidak ada satu pun server yang bisa dipilih, persis seperti yang
 * dilaporkan.
 *
 * <p>Ditempatkan di paket {@code anime} karena panelnya memang tidak publik;
 * visibilitas kode produksi tidak diubah hanya demi pengujian.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w960dp-h540dp-land-mdpi")
public class OtakudesuServerPanelTvTest {

    private Context context;

    @Before public void siapkan() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        Shadows.shadowOf(context.getPackageManager())
                .setSystemFeature(PackageManager.FEATURE_LEANBACK, true);
        new Prefs(context).setInt(TvMode.PREF_FORCE, 1);
    }

    private static OtakudesuPlayback.Mirror mirror(int index, String quality, String provider) {
        return new OtakudesuPlayback.Mirror(quality, provider, provider, index);
    }

    private static OtakudesuServerPanel.Palette palet() {
        return new OtakudesuServerPanel.Palette(
                0xFF15171C, 0xFFE8EAF0, 0xFF9AA0AC, 0xFF6EA8FF, 0x33FFFFFF);
    }

    private static final class Diam implements OtakudesuServerPanel.Listener {
        int dipilih;
        boolean mintaUlang;

        @Override public void onPick(OtakudesuPlayback.Mirror mirror) {
            if (mirror != null) dipilih++;
        }

        @Override public void onRetryFailed() {
            mintaUlang = true;
        }

        @Override public void onWatchSource() { }
    }

    private static void kumpulkan(View view, List<View> hasil) {
        if (view == null) return;
        hasil.add(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                kumpulkan(group.getChildAt(index), hasil);
            }
        }
    }

    private static View denganTag(View root, String tag) {
        List<View> semua = new ArrayList<>();
        kumpulkan(root, semua);
        for (View view : semua) {
            if (tag.equals(view.getTag())) return view;
        }
        return null;
    }

    private View bangun(List<OtakudesuPlayback.Mirror> mirrors,
                        Set<String> diperiksa,
                        OtakudesuServerPanel.Listener listener) {
        OtakudesuServerPanel panel = new OtakudesuServerPanel(context, palet());
        LinkedHashMap<String, OtakudesuPlayback.Source> siap = new LinkedHashMap<>();
        LinkedHashMap<String, OtakudesuServerPicker.Probed> gagal = new LinkedHashMap<>();
        View root = panel.build(mirrors, siap, gagal, diperiksa, listener);
        root.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 960, 600);
        ShadowLooper.idleMainLooper();
        return root;
    }

    @Test public void setiapBarisServerBisaDipilihDenganRemote() {
        List<OtakudesuPlayback.Mirror> mirrors = new ArrayList<>();
        mirrors.add(mirror(0, "720p", "odstream"));
        mirrors.add(mirror(1, "480p", "odcdn"));
        mirrors.add(mirror(2, "360p", "kfiles"));

        View root = bangun(mirrors, new LinkedHashSet<>(), new Diam());

        for (int index = 0; index < mirrors.size(); index++) {
            View baris = denganTag(root, "anime_server_" + index);
            assertNotNull("baris server " + index + " ada", baris);
            assertTrue("baris server " + index + " bisa dipilih dengan remote",
                    baris.isFocusable());
            assertNotNull("baris server " + index + " punya sorotan",
                    baris.getBackground());
        }
    }

    @Test public void menekanBarisMemilihServerItu() {
        // Inti keluhan: memilih server harus mungkin dengan tombol OK, bukan
        // dengan menyentuh tombol Putar kecil di dalam baris.
        List<OtakudesuPlayback.Mirror> mirrors = new ArrayList<>();
        mirrors.add(mirror(0, "720p", "odstream"));
        mirrors.add(mirror(1, "480p", "odcdn"));
        Diam listener = new Diam();

        View root = bangun(mirrors, new LinkedHashSet<>(), listener);
        View baris = denganTag(root, "anime_server_1");
        assertNotNull(baris);
        baris.performClick();
        ShadowLooper.idleMainLooper();
        assertEquals("baris yang ditekan memilih servernya", 1, listener.dipilih);
    }

    @Test public void barisYangSedangDiperiksaTetapBisaDipilih() {
        // Menunggu pemeriksaan selesai bukan alasan mengunci pilihan: tekan OK
        // berarti "pakai server ini begitu siap".
        List<OtakudesuPlayback.Mirror> mirrors = new ArrayList<>();
        mirrors.add(mirror(0, "720p", "odstream"));
        Set<String> diperiksa = new LinkedHashSet<>();
        diperiksa.add(mirrors.get(0).key());

        View root = bangun(mirrors, diperiksa, new Diam());
        View baris = denganTag(root, "anime_server_0");
        assertNotNull(baris);
        assertTrue("baris yang diperiksa tetap bisa dipilih", baris.isFocusable());
    }

    @Test public void barisServerTidakButuhModeSentuh() {
        List<OtakudesuPlayback.Mirror> mirrors = new ArrayList<>();
        mirrors.add(mirror(0, "720p", "odstream"));
        View root = bangun(mirrors, new LinkedHashSet<>(), new Diam());
        View baris = denganTag(root, "anime_server_0");
        assertNotNull(baris);
        assertFalse("fokus remote tidak bergantung pada sentuhan",
                baris.isFocusableInTouchMode());
    }

    @Test public void padaPonselBarisServerTidakDipaksaDifokus() {
        // Tampilan sentuh tidak boleh berubah sedikit pun.
        new Prefs(context).setInt(TvMode.PREF_FORCE, 0);
        Shadows.shadowOf(context.getPackageManager())
                .setSystemFeature(PackageManager.FEATURE_LEANBACK, false);

        List<OtakudesuPlayback.Mirror> mirrors = new ArrayList<>();
        mirrors.add(mirror(0, "720p", "odstream"));
        View root = bangun(mirrors, new LinkedHashSet<>(), new Diam());
        View baris = denganTag(root, "anime_server_0");
        assertNotNull(baris);
        assertEquals("fokus anak tidak diblokir di ponsel",
                ViewGroup.FOCUS_BEFORE_DESCENDANTS,
                ((ViewGroup) baris).getDescendantFocusability());
    }

    @Test public void panelPunyaTombolYangBisaDipilih() {
        // Panel juga punya tombol lain (mis. Periksa ulang). Kalau tidak bisa
        // dipilih, server yang gagal tidak bisa dicoba lagi dari televisi.
        List<OtakudesuPlayback.Mirror> mirrors = new ArrayList<>();
        mirrors.add(mirror(0, "720p", "odstream"));
        View root = bangun(mirrors, new LinkedHashSet<>(), new Diam());
        List<View> semua = new ArrayList<>();
        kumpulkan(root, semua);
        int bisaDipilih = 0;
        for (View view : semua) {
            if (view.isFocusable()) bisaDipilih++;
        }
        assertTrue("ada elemen panel yang bisa dipilih", bisaDipilih >= 1);
    }
}
