package id.shuyasal.kuromaku.offline;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Dialog;
import android.content.Context;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

import id.shuyasal.kuromaku.SourceHubActivity;

/**
 * Kanal laporan crash sampai ke pengguna.
 *
 * <p>Kalau crash tidak pernah terbaca, tidak ada yang bisa diperbaiki. Tes ini
 * memastikan laporan yang tertulis saat crash benar-benar muncul sebagai dialog
 * di Source Hub saat aplikasi dibuka lagi.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class CrashReportSurfaceTest {

    @After public void tearDown() {
        Context context = ApplicationProvider.getApplicationContext();
        CrashLog.clear(context);
        CrashLog.resetForTests();
    }

    @Test public void laporanMunculSaatSourceHubDibuka() {
        Context context = ApplicationProvider.getApplicationContext();
        CrashLog.install(context);
        Thread.getDefaultUncaughtExceptionHandler().uncaughtException(
                Thread.currentThread(), new IllegalStateException("unduhan meledak"));

        assertTrue("laporan tertulis", CrashLog.exists(context));

        SourceHubActivity activity = Robolectric.buildActivity(SourceHubActivity.class)
                .setup().get();
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull("dialog laporan muncul", dialog);
        assertTrue("dialog tampil", dialog.isShowing());

        String teks = teksDialog(dialog);
        assertTrue("memuat tipe exception", teks.contains("IllegalStateException"));
        assertTrue("memuat pesan", teks.contains("unduhan meledak"));
        activity.finish();
    }

    @Test public void tanpaCrashTidakAdaDialog() {
        CrashLog.clear(ApplicationProvider.getApplicationContext());
        SourceHubActivity activity = Robolectric.buildActivity(SourceHubActivity.class)
                .setup().get();
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertTrue("tidak ada dialog", dialog == null || !dialog.isShowing());
        activity.finish();
    }

    private static String teksDialog(Dialog dialog) {
        StringBuilder hasil = new StringBuilder();
        android.view.View root = dialog.getWindow() == null
                ? null : dialog.getWindow().getDecorView();
        kumpulkan(root, hasil);
        return hasil.toString();
    }

    private static void kumpulkan(android.view.View view, StringBuilder hasil) {
        if (view == null) return;
        if (view instanceof TextView) {
            CharSequence isi = ((TextView) view).getText();
            if (isi != null) hasil.append(isi).append('\n');
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup grup = (android.view.ViewGroup) view;
            for (int i = 0; i < grup.getChildCount(); i++) {
                kumpulkan(grup.getChildAt(i), hasil);
            }
        }
    }
}