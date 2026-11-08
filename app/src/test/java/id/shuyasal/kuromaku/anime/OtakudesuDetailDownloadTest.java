package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.Ui;

/**
 * Tombol Unduh pada daftar episode.
 *
 * <p>Aturan yang dijaga: menekan Unduh <b>tidak</b> membuka pemutar, dan tombol
 * pada baris itu sendiri melaporkan kemajuannya (menyiapkan, persen,
 * tersimpan, gagal).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class OtakudesuDetailDownloadTest {

    private static OtakudesuSource nextSource;

    public static final class Host extends OtakudesuDetailActivity {
        @Override protected OtakudesuSource createSource() {
            return nextSource;
        }
    }

    @Before public void setUp() {
        AnimeDownloadStatus.reset();
        AnimeDownloadPolicy.unlockForTesting(true);
        nextSource = new OtakudesuSource(url -> fixture("detail.html"));
    }

    @After public void tearDown() {
        AnimeDownloadStatus.reset();
        AnimeDownloadPolicy.unlockForTesting(null);
    }

    private static String fixture(String name) {
        try (InputStream in = OtakudesuDetailDownloadTest.class.getResourceAsStream(
                "/otakudesu/" + name)) {
            if (in == null) throw new IllegalStateException("fixture: " + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private ActivityController<Host> open() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), Host.class)
                .putExtra(OtakudesuDetailActivity.EXTRA_ANIME_ID, "fxskc-sub-indo")
                .putExtra(OtakudesuDetailActivity.EXTRA_ANIME_TITLE, "FX Senshi Kurumi-chan")
                .putExtra(OtakudesuDetailActivity.EXTRA_ANIME_POSTER, "");
        return Robolectric.buildActivity(Host.class, intent).setup();
    }

    private static void await(OtakudesuDetailActivity activity) throws Exception {
        java.util.concurrent.ExecutorService worker = (java.util.concurrent.ExecutorService)
                field(activity, "worker");
        worker.submit(() -> { }).get(10, java.util.concurrent.TimeUnit.SECONDS);
        ShadowLooper.idleMainLooper();
    }

    private static Button downloadButton(OtakudesuDetailActivity activity) {
        View found = activity.getWindow().getDecorView()
                .findViewWithTag("otakudesu_download_button");
        return found instanceof Button ? (Button) found : null;
    }

    private static View downloadBar(OtakudesuDetailActivity activity) {
        return activity.getWindow().getDecorView().findViewWithTag("otakudesu_download_bar");
    }

    private static View cancelButton(OtakudesuDetailActivity activity) {
        return activity.getWindow().getDecorView().findViewWithTag("otakudesu_download_cancel");
    }

    private static TextView percentLabel(OtakudesuDetailActivity activity) {
        return (TextView) activity.getWindow().getDecorView()
                .findViewWithTag("otakudesu_download_percent");
    }

    private static View progressLine(OtakudesuDetailActivity activity) {
        return activity.getWindow().getDecorView()
                .findViewWithTag("otakudesu_download_progress");
    }

    /** Episode pertama pada fixture detail, dipakai sebagai sasaran unduhan. */
    private static OtakudesuAnime.Episode firstEpisode(OtakudesuDetailActivity activity)
            throws Exception {
        OtakudesuAnime.Detail detail = (OtakudesuAnime.Detail) field(activity, "detail");
        assertNotNull("detail anime harus termuat", detail);
        assertFalse("harus ada episode", detail.episodes.isEmpty());
        return detail.episodes.get(0);
    }

    // -------------------------------------------------------------- perilaku

    @Test public void menekanUnduhTidakMembukaPemutar() throws Exception {
        try (ActivityController<Host> controller = open()) {
            Host activity = controller.get();
            await(activity);
            Button button = downloadButton(activity);
            assertNotNull("tombol Unduh harus ada pada baris episode", button);
            assertEquals("Unduh", button.getText().toString());

            button.performClick();
            ShadowLooper.idleMainLooper();

            assertNull("tidak boleh ada Activity pemutar yang dibuka",
                    Shadows.shadowOf(activity).getNextStartedActivity());
            assertFalse("Activity tetap hidup", activity.isFinishing());
        }
    }

    @Test public void saatMengunduhMunculPersenBarDanTombolBatal() throws Exception {
        try (ActivityController<Host> controller = open()) {
            Host activity = controller.get();
            await(activity);
            OtakudesuAnime.Episode episode = firstEpisode(activity);
            String key = AnimeDownloads.episodeKey(episode.url, episode.label);
            Button button = downloadButton(activity);
            assertNotNull(button);

            AnimeDownloadStatus.set(key, AnimeDownloadStatus.QUEUED, 0, "Menyiapkan…", "720p");
            ShadowLooper.idleMainLooper();
            assertEquals("Menyiapkan…", button.getText().toString());
            assertEquals("tombol batal tampil saat antre", View.VISIBLE,
                    cancelButton(activity).getVisibility());

            // 12 dari 100 byte: persen berasal dari byte nyata, bukan timer.
            AnimeDownloadStatus.progress(key, 12, 100);
            ShadowLooper.idleMainLooper();
            assertEquals("12%", percentLabel(activity).getText().toString());
            assertEquals("bar progres tampil", View.VISIBLE,
                    downloadBar(activity).getVisibility());
            assertEquals(12, ((ProgressBar) downloadBar(activity)).getProgress());
            assertEquals("tombol Unduh diganti baris kemajuan", View.GONE,
                    button.getVisibility());
            assertEquals("X tetap 48dp", Ui.dp(activity, Ui.TOUCH_DP),
                    cancelButton(activity).getLayoutParams().width);
            assertEquals("Batalkan unduhan episode",
                    cancelButton(activity).getContentDescription().toString());

            AnimeDownloadStatus.set(key, AnimeDownloadStatus.SAVED, 100,
                    "Tersimpan · 720p", "720p");
            ShadowLooper.idleMainLooper();
            assertEquals("Tersimpan · 720p", button.getText().toString());
            assertEquals("tombol Unduh kembali tampil", View.VISIBLE, button.getVisibility());
            assertEquals("baris kemajuan disembunyikan", View.GONE,
                    progressLine(activity).getVisibility());

            AnimeDownloadStatus.set(key, AnimeDownloadStatus.FAILED, 0,
                    "Server menolak unduhan (kode 403).");
            ShadowLooper.idleMainLooper();
            assertEquals("Coba Lagi", button.getText().toString());
        }
    }

    @Test public void menekanUnduhMembukaLembarKualitasTanpaPemutar() throws Exception {
        try (ActivityController<Host> controller = open()) {
            Host activity = controller.get();
            await(activity);
            Button button = downloadButton(activity);
            assertNotNull(button);
            button.performClick();
            await(activity);

            android.app.Dialog sheet = org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertNotNull("lembar pemilihan kualitas harus terbuka", sheet);
            assertTrue("lembar benar-benar ditampilkan", sheet.isShowing());
            View panel = sheet.findViewById(android.R.id.content)
                    .findViewWithTag("anime_quality_sheet");
            assertNotNull("panel kualitas ada", panel);
            assertNull("pemutar tidak boleh terbuka",
                    Shadows.shadowOf(activity).getNextStartedActivity());
        }
    }

    @Test public void kualitasTidakTersediaDijelaskanBukanDitawarkan() throws Exception {
        try (ActivityController<Host> controller = open()) {
            Host activity = controller.get();
            await(activity);
            downloadButton(activity).performClick();
            await(activity);

            android.app.Dialog sheet = org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertNotNull(sheet);
            View rows = sheet.findViewById(android.R.id.content)
                    .findViewWithTag("anime_quality_rows");
            assertNotNull(rows);
            assertEquals("dua kualitas diperiksa", 2, ((android.view.ViewGroup) rows).getChildCount());
            for (int i = 0; i < ((android.view.ViewGroup) rows).getChildCount(); i++) {
                View row = ((android.view.ViewGroup) rows).getChildAt(i);
                assertFalse("baris tidak bisa ditekan bila tidak tersedia", row.isEnabled());
            }
        }
    }

    @Test public void unduhanTanpaPersetujuanTidakMenyimpanApaPun() throws Exception {
        AnimeDownloadPolicy.unlockForTesting(false);
        try (ActivityController<Host> controller = open()) {
            Host activity = controller.get();
            await(activity);
            Button button = downloadButton(activity);
            assertNotNull(button);
            button.performClick();
            ShadowLooper.idleMainLooper();

            // Dialog persetujuan muncul; tidak ada Activity yang dibuka dan tidak
            // ada unduhan yang dimulai.
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
            assertTrue("status belum tersimpan",
                    AnimeDownloadStatus.state(
                            AnimeDownloads.episodeKey(firstEpisode(activity).url,
                                    firstEpisode(activity).label))
                            != AnimeDownloadStatus.SAVED);
        }
    }

    @Test public void statusAwalTanpaUnduhanAdalahUnduh() throws Exception {
        try (ActivityController<Host> controller = open()) {
            Host activity = controller.get();
            await(activity);
            Button button = downloadButton(activity);
            assertNotNull(button);
            assertEquals("Unduh", button.getText().toString());
            assertEquals(View.GONE, downloadBar(activity).getVisibility());
        }
    }

    // ------------------------------------------------------------ perkakas

    private static Object field(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}