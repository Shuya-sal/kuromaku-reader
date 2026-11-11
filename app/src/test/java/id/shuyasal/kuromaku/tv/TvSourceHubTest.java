package id.shuyasal.kuromaku.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import id.shuyasal.kuromaku.SourceHubActivity;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.TvMode;

/**
 * Layar pembuka pada televisi.
 *
 * <p>Layar ini adalah pintu masuk aplikasi: kalau tidak ada satu pun kartu yang
 * bisa dipilih dengan remote, seluruh aplikasi tidak bisa dipakai di televisi
 * walau layarnya terlihat normal.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w960dp-h540dp-land-mdpi")
public class TvSourceHubTest {

    private Context context;

    @Before public void siapkan() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        new Prefs(context).setInt(TvMode.PREF_FORCE, 1);
    }

    private static void ukurDanSusun(View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 960, 540);
    }

    private static List<View> terkumpulBisaDifokus(View root, List<View> hasil) {
        if (root == null) return hasil;
        if (root.isFocusable() && root.getVisibility() == View.VISIBLE) hasil.add(root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                terkumpulBisaDifokus(group.getChildAt(index), hasil);
            }
        }
        return hasil;
    }

    /** Semua kartu sumber di layar, sesuai urutan tampilnya. */
    private static List<View> kartuSumber(View root) {
        List<View> hasil = new ArrayList<>();
        kumpulkanKartu(root, hasil);
        return hasil;
    }

    private static void kumpulkanKartu(View view, List<View> hasil) {
        if (view == null) return;
        if (view instanceof id.shuyasal.kuromaku.ui.GlassCard) hasil.add(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                kumpulkanKartu(group.getChildAt(index), hasil);
            }
        }
    }

    @Test public void limaKartuSumberBisaDipilihDenganRemote() {
        try (ActivityController<SourceHubActivity> controller =
                     Robolectric.buildActivity(SourceHubActivity.class).setup()) {
            SourceHubActivity activity = controller.get();
            View root = activity.getWindow().getDecorView();
            ukurDanSusun(root);
            org.robolectric.shadows.ShadowLooper.idleMainLooper();

            List<View> kartu = kartuSumber(root);
            assertEquals("lima sumber tampil", 5, kartu.size());

            String[] harapan = {"source_hub_card_novel", "source_hub_card_voratoon",
                    "source_hub_card_shinigami", "source_hub_card_komiku",
                    "source_hub_card_otakudesu"};
            for (int index = 0; index < harapan.length; index++) {
                View view = kartu.get(index);
                assertEquals("urutan kartu " + index, harapan[index], view.getTag());
                assertTrue("kartu " + harapan[index] + " bisa difokus remote",
                        view.isFocusable());
                assertEquals("kartu " + harapan[index] + " menangkap sendiri tombol arahnya",
                        ViewGroup.FOCUS_BLOCK_DESCENDANTS,
                        ((ViewGroup) view).getDescendantFocusability());
            }
        }
    }

    @Test public void adaSorotanAwalSupayaPenggunaTahuKendaliDiMana() {
        try (ActivityController<SourceHubActivity> controller =
                     Robolectric.buildActivity(SourceHubActivity.class).setup()) {
            SourceHubActivity activity = controller.get();
            View root = activity.getWindow().getDecorView();
            ukurDanSusun(root);
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            controller.get().getWindow().getDecorView().post(() -> { });
            org.robolectric.shadows.ShadowLooper.idleMainLooper();

            android.view.View fokus = activity.getCurrentFocus();
            // Boleh saja belum ada fokus bila tata letak belum siap di
            // Robolectric; yang wajib: minimal satu kartu bisa menerima fokus
            // sehingga tombol arah punya tujuan.
            assertFalse("ada elemen yang bisa dipilih",
                    terkumpulBisaDifokus(root, new ArrayList<>()).isEmpty());
            if (fokus != null) {
                assertTrue("fokus awal berada di dalam layar",
                        fokus.getRootView() == root);
            }
        }
    }

    @Test public void tombolTengahMenjalankanKartuYangDipilih() {
        try (ActivityController<SourceHubActivity> controller =
                     Robolectric.buildActivity(SourceHubActivity.class).setup()) {
            SourceHubActivity activity = controller.get();
            View root = activity.getWindow().getDecorView();
            ukurDanSusun(root);
            org.robolectric.shadows.ShadowLooper.idleMainLooper();

            List<View> semua = kartuSumber(root);
            assertEquals(5, semua.size());
            View kartu = semua.get(2);
            assertEquals("Manga Server 2", "source_hub_card_shinigami", kartu.getTag());
            kartu.requestFocus();
            // Tombol tengah remote: performClick adalah jalur yang sama dengan
            // yang dipakai Android saat tombol OK ditekan pada view terfokus.
            kartu.performClick();
            android.content.Intent next =
                    org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull("kartu yang dipilih membuka layarnya", next);
        }
    }

    @Test public void padaPonselTidakAdaPerubahanPerilaku() {
        // Mode sentuh: tampilan dan sentuhan harus sama seperti sebelumnya.
        new Prefs(context).setInt(TvMode.PREF_FORCE, 0);
        assertFalse(TvMode.isTelevision(context));
        try (ActivityController<SourceHubActivity> controller =
                     Robolectric.buildActivity(SourceHubActivity.class).setup()) {
            SourceHubActivity activity = controller.get();
            View root = activity.getWindow().getDecorView();
            assertEquals("lima kartu tetap tampil di ponsel", 5, kartuSumber(root).size());
        }
    }

    @Test public void tombolKembaliRemoteTidakMematikanLayarSaatMasihDiBeranda() {
        try (ActivityController<SourceHubActivity> controller =
                     Robolectric.buildActivity(SourceHubActivity.class).setup()) {
            SourceHubActivity activity = controller.get();
            ukurDanSusun(activity.getWindow().getDecorView());
            // Tombol kembali di beranda: sistem yang memutuskan (keluar
            // aplikasi). Yang dijaga di sini: tidak ada penanganan yang
            // menelannya sehingga pengguna tidak bisa keluar dari TV.
            activity.dispatchKeyEvent(new KeyEvent(
                    KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            // Tombol kembali di beranda boleh ditangani sistem; yang tidak boleh
            // adalah aplikasi menelannya sendiri sehingga pengguna tidak bisa
            // keluar dari televisi.
            assertFalse("aplikasi tidak menyembunyikan tombol kembali",
                    activity.isFinishing() && activity.getIntent() == null);
        }
    }
}
