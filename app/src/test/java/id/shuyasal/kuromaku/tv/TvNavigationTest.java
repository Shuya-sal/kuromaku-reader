package id.shuyasal.kuromaku.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.FocusFinder;
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
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;

import id.shuyasal.kuromaku.SourceHubActivity;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.TvFocus;
import id.shuyasal.kuromaku.ui.TvMode;

/**
 * Rantai fokus untuk tombol arah remote televisi.
 *
 * <p><b>Batas pengujian ini, dinyatakan terbuka:</b> Robolectric tidak
 * menjalankan navigasi tombol arah seperti perangkat sungguhan, sehingga
 * "tekan tombol bawah lalu sorotan pindah" tidak bisa dibuktikan di sini.
 *
 * <p>Yang bisa dan memang dibuktikan: {@link FocusFinder} — kelas yang dipakai
 * Android sendiri untuk menentukan ke mana sorotan harus pergi saat tombol arah
 * ditekan. Bila FocusFinder menemukan kartu berikutnya pada arah bawah, maka di
 * perangkat sungguhan tombol bawah juga akan sampai ke kartu itu. Sebaliknya,
 * bila FocusFinder tidak menemukan tujuan, tombol arah pasti tidak akan
 * berbuat apa-apa — dan itulah yang membuat aplikasi tidak bisa dipakai di
 * televisi. Jadi tes ini memeriksa bagian yang menentukan, bukan menebaknya.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w960dp-h540dp-land-mdpi")
public class TvNavigationTest {

    private Context context;

    @Before public void siapkan() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        new Prefs(context).setInt(TvMode.PREF_FORCE, 1);
    }

    private static List<View> kartu(View root) {
        List<View> hasil = new ArrayList<>();
        kumpulkan(root, hasil);
        return hasil;
    }

    private static void kumpulkan(View view, List<View> hasil) {
        if (view == null) return;
        if (view instanceof id.shuyasal.kuromaku.ui.GlassCard) hasil.add(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                kumpulkan(group.getChildAt(index), hasil);
            }
        }
    }

    private static SourceHubActivity siapkanLayar(ActivityController<SourceHubActivity> c) {
        SourceHubActivity activity = c.get();
        View root = activity.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 960, 540);
        ShadowLooper.idleMainLooper();
        return activity;
    }

    /** Ke mana sorotan pergi bila tombol arah ditekan dari view ini. */
    private static View berikutnya(View root, View dari, int arah) {
        return FocusFinder.getInstance().findNextFocus((ViewGroup) root, dari, arah);
    }

    @Test public void tombolBawahPunyaTujuanKartuBerikutnya() {
        try (ActivityController<SourceHubActivity> controller =
                     Robolectric.buildActivity(SourceHubActivity.class).setup()) {
            SourceHubActivity activity = siapkanLayar(controller);
            View root = activity.getWindow().getDecorView();
            List<View> semua = kartu(root);
            assertEquals("lima kartu sumber", 5, semua.size());

            for (int index = 0; index < semua.size() - 1; index++) {
                View tujuan = berikutnya(root, semua.get(index), View.FOCUS_DOWN);
                assertNotNull("kartu " + index + " punya tujuan di bawahnya", tujuan);
                assertEquals("tombol bawah dari kartu " + index + " menuju kartu "
                        + (index + 1), semua.get(index + 1), tujuan);
            }
        }
    }

    @Test public void kartuTerakhirTidakPunyaTujuanKeBawah() {
        // Kartu terakhir adalah ujung daftar: tombol bawah di situ boleh
        // dipakai untuk menggulir, bukan berpindah sorotan.
        try (ActivityController<SourceHubActivity> controller =
                     Robolectric.buildActivity(SourceHubActivity.class).setup()) {
            SourceHubActivity activity = siapkanLayar(controller);
            View root = activity.getWindow().getDecorView();
            List<View> semua = kartu(root);
            View tujuan = berikutnya(root, semua.get(semua.size() - 1), View.FOCUS_DOWN);
            assertNull("tidak ada kartu setelah yang terakhir", tujuan);
        }
    }

    @Test public void tombolAtasPunyaTujuanKartuSebelumnya() {
        try (ActivityController<SourceHubActivity> controller =
                     Robolectric.buildActivity(SourceHubActivity.class).setup()) {
            SourceHubActivity activity = siapkanLayar(controller);
            View root = activity.getWindow().getDecorView();
            List<View> semua = kartu(root);

            for (int index = 1; index < semua.size(); index++) {
                View tujuan = berikutnya(root, semua.get(index), View.FOCUS_UP);
                assertNotNull("kartu " + index + " punya tujuan di atasnya", tujuan);
                assertEquals("tombol atas dari kartu " + index + " kembali ke kartu "
                        + (index - 1), semua.get(index - 1), tujuan);
            }
        }
    }

    @Test public void kartuPertamaTidakPunyaTujuanKeAtas() {
        try (ActivityController<SourceHubActivity> controller =
                     Robolectric.buildActivity(SourceHubActivity.class).setup()) {
            SourceHubActivity activity = siapkanLayar(controller);
            View root = activity.getWindow().getDecorView();
            assertNull("tidak ada apa pun di atas kartu pertama",
                    berikutnya(root, kartu(root).get(0), View.FOCUS_UP));
        }
    }

    @Test public void setiapKartuBisaMenerimaFokus() {
        try (ActivityController<SourceHubActivity> controller =
                     Robolectric.buildActivity(SourceHubActivity.class).setup()) {
            SourceHubActivity activity = siapkanLayar(controller);
            for (View kartu : kartu(activity.getWindow().getDecorView())) {
                assertTrue("kartu " + kartu.getTag() + " menerima fokus",
                        kartu.isFocusable());
                assertFalse("kartu " + kartu.getTag() + " tidak butuh mode sentuh",
                        kartu.isFocusableInTouchMode());
                assertEquals("kartu " + kartu.getTag() + " menahan fokus pada dirinya",
                        ViewGroup.FOCUS_BLOCK_DESCENDANTS,
                        ((ViewGroup) kartu).getDescendantFocusability());
            }
        }
    }

    @Test public void tombolTengahMembukaSumberYangTersorot() {
        try (ActivityController<SourceHubActivity> controller =
                     Robolectric.buildActivity(SourceHubActivity.class).setup()) {
            SourceHubActivity activity = siapkanLayar(controller);
            View kartuKomiku = kartu(activity.getWindow().getDecorView()).get(3);
            // Tombol tengah pada view terfokus dijalankan lewat performClick,
            // jalur yang sama dengan yang dipakai Android untuk tombol OK.
            kartuKomiku.performClick();
            ShadowLooper.idleMainLooper();
            android.content.Intent next =
                    org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull("tombol tengah membuka layar sumber", next);
            assertEquals("kartu keempat = Manga Server 3",
                    "id.shuyasal.kuromaku.komiku.KomikuActivity",
                    next.getComponent().getClassName());
        }
    }

    @Test public void sorotanTidakHilangSaatLayarDigambarUlang() {
        try (ActivityController<SourceHubActivity> controller =
                     Robolectric.buildActivity(SourceHubActivity.class).setup()) {
            SourceHubActivity activity = siapkanLayar(controller);
            View root = activity.getWindow().getDecorView();
            List<View> semua = kartu(root);

            root.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 960, 540);
            ShadowLooper.idleMainLooper();

            for (View kartu : semua) {
                assertNotNull("kartu " + kartu.getTag() + " punya latar sorotan",
                        kartu.getBackground());
                assertTrue("latar kartu " + kartu.getTag() + " mengikuti state fokus",
                        kartu.getBackground().isStateful());
            }
        }
    }

    @Test public void padaPonselPemasanganFokusTidakBerjalan() {
        // Perilaku sentuh tidak boleh berubah sedikit pun. Di ponsel, TvFocus
        // harus benar-benar diam: view yang hanya bisa diketuk tetap tidak
        // dipaksa bisa difokus, dan latarnya tidak dibungkus sorotan.
        new Prefs(context).setInt(TvMode.PREF_FORCE, 0);

        android.widget.LinearLayout kartu = new android.widget.LinearLayout(context);
        kartu.setOnClickListener(v -> { });
        // Diperiksa SEBELUM: setOnClickListener sendiri sudah membuat view bisa
        // difokus, jadi yang penting adalah TvFocus tidak mengubah apa pun.
        boolean fokusAwal = kartu.isFocusable();
        int fokusAnakAwal = kartu.getDescendantFocusability();
        android.graphics.drawable.Drawable asli = kartu.getBackground();

        TvFocus.focusable(kartu, Palette.of(Prefs.THEME_DARK));
        TvFocus.keepRing(kartu, Palette.of(Prefs.THEME_DARK), 14);

        assertEquals("sifat fokus kartu ponsel tidak diubah",
                fokusAwal, kartu.isFocusable());
        assertEquals("latar kartu ponsel tidak diubah", asli, kartu.getBackground());
        assertNotEquals("fokus anak tidak diblokir di ponsel",
                ViewGroup.FOCUS_BLOCK_DESCENDANTS, kartu.getDescendantFocusability());
        assertEquals("fokus anak tetap seperti semula",
                fokusAnakAwal, kartu.getDescendantFocusability());
    }
}
