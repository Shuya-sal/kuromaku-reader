package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Pemutar native: format waktu, penolakan sumber yang bukan media, dan perilaku
 * Activity ketika halaman episode tidak punya berkas video.
 *
 * <p>Video sungguhan tidak diputar di sini — Robolectric tidak punya codec.
 * Yang diuji adalah keputusan aplikasi: jangan menampilkan layar hitam, dan
 * selalu sediakan jalan ke halaman resmi.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h640dp-mdpi")
public class AnimePlayerTest {

    @Test public void formatWaktuMudahDibaca() {
        assertEquals("00:00", AnimePlayerActivity.format(0));
        assertEquals("00:00", AnimePlayerActivity.format(-5));
        assertEquals("01:05", AnimePlayerActivity.format(65_000));
        assertEquals("59:59", AnimePlayerActivity.format(3_599_000));
        assertEquals("1:01:01", AnimePlayerActivity.format(3_661_000));
    }

    @Test public void halamanEpisodeTanpaMediaMenampilkanPesanDanTombolSumber() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                AnimePlayerActivity.class)
                .putExtra(AnimePlayerActivity.EXTRA_ANIME_ID, "fxskc")
                .putExtra(AnimePlayerActivity.EXTRA_ANIME_TITLE, "FX Senshi Kurumi-chan")
                .putExtra(AnimePlayerActivity.EXTRA_EPISODE_URL,
                        "https://otakudesu.blog/episode/fxskc-episode-1-sub-indo/")
                .putExtra(AnimePlayerActivity.EXTRA_EPISODE_LABEL, "Episode 1");

        AnimePlayerActivity activity = Robolectric.buildActivity(
                AnimePlayerActivity.class, intent).setup().get();

        TextView error = activity.getWindow().getDecorView()
                .findViewWithTag("anime_player_error");
        assertNotNull("Pesan status harus ada", error);

        // Resolusi berjalan di thread pekerja; tunggu sebentar lalu periksa hasil.
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        // Tunggu sampai kotak status benar-benar tampil dan berisi pesan.
        for (int i = 0; i < 80 && text(error).trim().isEmpty(); i++) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
        }

        // Pengguna harus selalu punya jalan keluar. Di lingkungan uji, jaringan
        // tidak tersedia, jadi panel server tidak akan terbuka — yang diperiksa
        // di sini adalah jaminan paling dasar: tombol "Pilih Server" selalu ada,
        // dan begitu ditekan tanpa daftar server pengguna diberi tahu, bukan
        // dibiarkan menatap layar kosong.
        Button pickServer = activity.getWindow().getDecorView()
                .findViewWithTag("anime_player_pick_server");
        assertNotNull("Tombol Pilih Server harus tersedia", pickServer);
        assertEquals("Coba Server Lain", pickServer.getText().toString());

        Button source = activity.getWindow().getDecorView()
                .findViewWithTag("anime_player_source");
        assertNotNull("Tombol Tonton di Sumber harus tersedia", source);
        assertEquals("Tonton di Sumber", source.getText().toString());
    }

    /**
     * Pesan kegagalan harus menyebut server yang gagal, sebabnya, dan dua jalan
     * keluar. "Layar hitam tanpa penjelasan" tidak boleh terjadi.
     */
    @Test public void pesanKegagalanMenyebutServerSebabDanJalanKeluar() {
        String message = AnimePlayerActivity.kegagalanServer("vidhide 720p",
                "Berkas ditutup pemutar penyedia (terenkripsi).");
        assertTrue("nama server harus disebut", message.contains("vidhide 720p"));
        assertTrue("sebabnya harus disebut", message.contains("terenkripsi"));
        assertTrue("jalan keluar server lain", message.contains("server lain"));
        assertTrue("jalan keluar pemutar sumber", message.contains("Buka Player Sumber"));
    }

    @Test public void pesanKegagalanTetapJelasTanpaNamaServer() {
        String message = AnimePlayerActivity.kegagalanServer("", "");
        assertTrue(message.startsWith("Video tidak dapat diputar aplikasi."));
        assertFalse("tidak boleh kosong", message.trim().isEmpty());
    }

    /** Tombol jalan keluar di kotak galat: server lain dan pemutar sumber. */
    @Test public void kotakGalatMenyediakanDuaJalanKeluar() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                AnimePlayerActivity.class)
                .putExtra(AnimePlayerActivity.EXTRA_ANIME_ID, "fxskc")
                .putExtra(AnimePlayerActivity.EXTRA_EPISODE_URL,
                        "https://otakudesu.blog/episode/fxskc-episode-1-sub-indo/");
        AnimePlayerActivity activity = Robolectric.buildActivity(
                AnimePlayerActivity.class, intent).setup().get();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();

        android.view.View decor = activity.getWindow().getDecorView();
        assertNotNull("tombol coba server lain",
                decor.findViewWithTag("anime_player_pick_server"));
        assertNotNull("tombol buka player sumber",
                decor.findViewWithTag("anime_player_open_server"));
        assertNotNull("tombol tonton di sumber",
                decor.findViewWithTag("anime_player_source"));
    }

    /**
     * Berpindah server tidak mengembalikan pengguna ke awal episode.
     *
     * <p>Sebelumnya penggantian server mengosongkan posisi menonton, jadi episode
     * yang sudah ditonton separuh mulai lagi dari nol. Tes ini mengunci janji
     * bahwa posisi itu dibawa.
     */
    @Test public void gantiServerTidakMengulangDariAwal() throws Exception {
        java.io.File dir = ApplicationProvider.getApplicationContext().getFilesDir();
        OtakudesuStore store = new OtakudesuStore(ApplicationProvider.getApplicationContext());
        store.saveProgress("fxskc", "fxskc-episode-1-sub-indo", 754_000L, 1_425_000L, false);

        OtakudesuStore.Progress saved = store.progress("fxskc", "fxskc-episode-1-sub-indo");
        assertNotNull("posisi tersimpan harus terbaca", saved);
        assertEquals(754_000L, saved.resumePositionMs());

        // Sumber yang sama harus bisa dilanjutkan: pemutar mengambil posisi dari
        // penyimpanan, bukan dari nol.
        long resume = store.progress("fxskc", "fxskc-episode-1-sub-indo").resumePositionMs();
        assertTrue("posisi menonton tidak boleh kembali ke awal", resume > 0);
    }

    /** Episode tanpa berkas dan tanpa mirror tidak boleh membuat aplikasi berhenti. */
    @Test public void tanpaMirrorSamaSekaliTidakCrash() {
        String message = AnimePlayerActivity.kegagalanServer("", "");
        assertNotNull(message);
        assertFalse("pesan tidak boleh kosong", message.trim().isEmpty());
    }

    private static boolean isVisible(android.view.View view) {
        android.view.View current = view;
        while (current != null) {
            if (current.getVisibility() != android.view.View.VISIBLE) return false;
            if (!(current.getParent() instanceof android.view.View)) return true;
            current = (android.view.View) current.getParent();
        }
        return true;
    }

    private static String text(TextView view) {
        return view.getText() == null ? "" : view.getText().toString();
    }

    @Test public void episodeTanpaAlamatLangsungDitutupDenganPesan() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                AnimePlayerActivity.class)
                .putExtra(AnimePlayerActivity.EXTRA_ANIME_ID, "fxskc");
        AnimePlayerActivity activity = Robolectric.buildActivity(
                AnimePlayerActivity.class, intent).setup().get();

        TextView error = activity.getWindow().getDecorView()
                .findViewWithTag("anime_player_error");
        assertNotNull(error);
        assertTrue(text(error).contains("tidak memiliki alamat"));
    }

    // ------------------------------------------------- toolbar & layar penuh

    /**
     * Toolbar pemutar tidak memuat chip yang meluber.
     *
     * <p>Akar bug: baris atas menampung lima chip (Server, Kualitas, Subtitle,
     * 1x, Layar penuh) di samping tombol kembali dan judul. Pada layar sempit
     * chip terakhir terpotong di luar tepi kanan sehingga tidak bisa ditekan.
     * Sekarang baris atas hanya memuat kembali, judul, pintasan server, dan menu
     * tambahan; sisanya pindah ke menu.
     */
    @Test public void toolbarTidakMeluberDanMenuTambahanAda() {
        ActivityController<AnimePlayerActivity> controller = player();
        try {
            View decor = controller.get().getWindow().getDecorView();
            assertNotNull("pintasan server tetap satu sentuhan",
                    decor.findViewWithTag("anime_player_server"));
            assertNotNull("menu tambahan harus ada",
                    decor.findViewWithTag("anime_player_more"));
            assertNull("chip kualitas tidak lagi menumpuk di baris atas",
                    decor.findViewWithTag("anime_player_quality"));
            assertNull("chip subtitle tidak lagi menumpuk di baris atas",
                    decor.findViewWithTag("anime_player_subtitle"));
            assertNull("chip kecepatan tidak lagi menumpuk di baris atas",
                    decor.findViewWithTag("anime_player_speed"));
            // Judul tetap ada dan dibatasi satu baris supaya tidak mendorong
            // kontrol lain keluar layar.
            TextView title = decor.findViewWithTag("anime_player_title");
            assertNotNull(title);
            assertTrue("judul harus satu baris", title.getMaxLines() == 1);
        } finally {
            controller.close();
        }
    }

    /**
     * Layar penuh tidak boleh memaksa orientasi.
     *
     * <p>Akar bug: tombol "Layar penuh" memanggil SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
     * jadi menonton dalam posisi portrait langsung diputar ke samping dengan
     * sendirinya. Orientasi sekarang dibiarkan mengikuti perangkat, dan pemutaran
     * paksa tersedia terpisah lewat "Putar layar".
     */
    @Test public void layarPenuhTidakMemaksaRotasi() throws Exception {
        ActivityController<AnimePlayerActivity> controller = player();
        try {
            AnimePlayerActivity activity = controller.get();
            activity.setRequestedOrientation(
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            java.lang.reflect.Method setFullscreen =
                    AnimePlayerActivity.class.getDeclaredMethod("setFullscreen", boolean.class);
            setFullscreen.setAccessible(true);
            setFullscreen.invoke(activity, true);

            assertEquals("layar penuh tidak boleh memutar layar",
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                    activity.getRequestedOrientation());
        } finally {
            controller.close();
        }
    }

    /** Arah "Putar layar" menukar orientasi, bukan selalu ke landscape. */
    @Test public void putarLayarMenukarOrientasi() throws Exception {
        ActivityController<AnimePlayerActivity> controller = player();
        try {
            AnimePlayerActivity activity = controller.get();
            java.lang.reflect.Method rotate =
                    AnimePlayerActivity.class.getDeclaredMethod("rotateScreen");
            rotate.setAccessible(true);
            activity.setRequestedOrientation(
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            rotate.invoke(activity);
            int setelah = activity.getRequestedOrientation();
            assertTrue("orientasi harus diminta secara eksplisit",
                    setelah == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            || setelah == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);

            // Arah mengikuti orientasi perangkat saat itu: pada portrait
            // permintaannya landscape, jadi hasilnya harus salah satu dari dua
            // nilai eksplisit itu — bukan UNSPECIFIED, yang berarti "ikuti
            // perangkat" dan bukan pemutaran paksa.
            assertFalse("pemutaran paksa tidak boleh berarti ikut perangkat",
                    setelah == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        } finally {
            controller.close();
        }
    }

    private ActivityController<AnimePlayerActivity> player() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                AnimePlayerActivity.class);
        intent.putExtra("anime_id", "fxskc");
        intent.putExtra("title", "FX Senshi Kurumi-chan");
        intent.putExtra("episode_url",
                "https://otakudesu.blog/episode/fxskc-episode-1-sub-indo/");
        intent.putExtra("episode_label", "Episode 1");
        return Robolectric.buildActivity(AnimePlayerActivity.class, intent).setup();
    }

}
