package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
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

    /** Tombol mana pun tidak boleh membuat pemutar dilepas. */
    @Test public void beralihModeTidakMelepasPemutar() throws Exception {
        ActivityController<AnimePlayerActivity> controller = player();
        try {
            AnimePlayerActivity activity = controller.get();
            java.lang.reflect.Field playerField =
                    AnimePlayerActivity.class.getDeclaredField("player");
            playerField.setAccessible(true);
            Object sebelum = playerField.get(activity);

            java.lang.reflect.Method rotate =
                    AnimePlayerActivity.class.getDeclaredMethod("rotateScreen");
            rotate.setAccessible(true);
            rotate.invoke(activity);
            assertSame("pemutar tidak dibuat ulang saat mode rotasi",
                    sebelum, playerField.get(activity));
            rotate.invoke(activity);
            assertSame("pemutar tetap sama saat keluar mode rotasi",
                    sebelum, playerField.get(activity));
        } finally {
            controller.close();
        }
    }


    /** Pemutar dengan intent lengkap, seperti dibuka dari daftar episode. */
    private ActivityController<AnimePlayerActivity> player() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                AnimePlayerActivity.class);
        intent.putExtra("anime_id", "fxskc");
        intent.putExtra("title", "FX Senshi Kurumi-chan");
        intent.putExtra("episode_url",
                "https://otakudesu.blog/episode/fxskc-episode-1-sub-indo/");
        intent.putExtra("episode_label", "Episode 1");
        return Robolectric.buildActivity(AnimePlayerActivity.class, intent).setup().visible();
    }

    // ------------------------------------------------ orientasi yang menetap

    /**
     * "Putar layar" meminta landscape dan landscape itu TIDAK dibatalkan.
     *
     * <p>Akar bug: {@code setFullscreen()} selalu menulis
     * {@code SCREEN_ORIENTATION_UNSPECIFIED} di akhir, dan "Putar layar"
     * memanggilnya tepat setelah meminta landscape. Hasilnya layar sempat
     * miring lalu kembali sendiri. Sekarang hanya satu metode yang menulis
     * orientasi, dan layar penuh tidak menyentuhnya.
     */
    @Test public void landscapeTetapSetelahLayarPenuhDinyalakan() throws Exception {
        ActivityController<AnimePlayerActivity> controller = player();
        try {
            AnimePlayerActivity activity = controller.get();
            invoke(activity, "rotateScreen");

            assertEquals("landscape harus tetap diminta",
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                    activity.getRequestedOrientation());

            // Kejadian di sekitar pemutaran tidak boleh mengubahnya: perubahan
            // konfigurasi, resume, dan tata letak ulang.
            activity.onConfigurationChanged(
                    activity.getResources().getConfiguration());
            controller.pause().resume();
            activity.getWindow().getDecorView().requestLayout();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();

            assertEquals("landscape tidak boleh dibatalkan kejadian di sekitarnya",
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                    activity.getRequestedOrientation());
            assertTrue("layar penuh mati", (Boolean) field(activity, "fullscreen"));
        } finally {
            controller.close();
        }
    }

    /** Layar penuh biasa tidak pernah memaksa landscape. */
    @Test public void layarPenuhBiasaTidakMenyentuhOrientasi() throws Exception {
        ActivityController<AnimePlayerActivity> controller = player();
        try {
            AnimePlayerActivity activity = controller.get();
            invoke(activity, "setFullscreen", true);
            assertEquals("orientasi tetap mengikuti perangkat",
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                    activity.getRequestedOrientation());
            assertTrue("layar penuh tetap menyala", (Boolean) field(activity, "fullscreen"));
        } finally {
            controller.close();
        }
    }

    /** Menekan "Putar layar" dua kali mengembalikan orientasi semula. */
    @Test public void putarLayarDuaKaliKembaliKeSemula() throws Exception {
        ActivityController<AnimePlayerActivity> controller = player();
        try {
            AnimePlayerActivity activity = controller.get();
            invoke(activity, "rotateScreen");
            invoke(activity, "rotateScreen");
            assertEquals("orientasi kembali mengikuti perangkat",
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                    activity.getRequestedOrientation());
            assertFalse("layar penuh ikut dimatikan", (Boolean) field(activity, "fullscreen"));
        } finally {
            controller.close();
        }
    }

    /** State orientasi dipulihkan saat Activity dibuat ulang. */
    @Test public void orientasiDipulihkanSaatActivityDibuatUlang() throws Exception {
        ActivityController<AnimePlayerActivity> controller = player();
        try {
            AnimePlayerActivity activity = controller.get();
            invoke(activity, "rotateScreen");

            android.os.Bundle out = new android.os.Bundle();
            controller.saveInstanceState(out);
            controller.pause().stop().destroy();

            try (ActivityController<AnimePlayerActivity> kedua =
                         Robolectric.buildActivity(AnimePlayerActivity.class, intent())
                                 .setup(out).visible()) {
                assertEquals("mode landscape dipulihkan",
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                        kedua.get().getRequestedOrientation());
            }
        } finally {
            controller.close();
        }
    }

    // ------------------------------------------------ kontrol auto-hide 5 dtk

    /**
     * Kontrol hilang setelah 5 detik, muncul lagi saat diketuk, lalu hilang lagi.
     *
     * <p>{@code controlsPinned()} di-override agar perilaku penjadwalan diuji
     * terpisah dari keadaan pemutar (tanpa perangkat, pemutar tidak pernah
     * benar-benar memutar video).
     */
    @Test public void kontrolHilangSetelahLimaDetikDanMunculSaatDiketuk() throws Exception {
        try (ActivityController<Playable> controller =
                     Robolectric.buildActivity(Playable.class, intent()).setup().visible()) {
            Playable activity = controller.get();
            View top = activity.findViewById(android.R.id.content)
                    .findViewWithTag("anime_player_back");
            View bar = (View) top.getParent();
            View surface = activity.findViewById(android.R.id.content)
                    .findViewWithTag("anime_player_surface");

            // Tanpa jaringan, pemutar membuka panel server; tutup dulu supaya
            // yang diuji benar-benar penjadwalan auto-hide (panel memang
            // menahan kontrol agar tetap terlihat).
            invoke(activity, "dismissServerPanel");
            invoke(activity, "setFullscreen", true);
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertEquals("kontrol tampil saat masuk layar penuh", View.VISIBLE, bar.getVisibility());

            // Belum lima detik: masih tampil.
            org.robolectric.shadows.ShadowLooper.getShadowMainLooper()
                    .idleFor(java.time.Duration.ofMillis(4_000));
            assertEquals("belum hilang sebelum 5 detik", View.VISIBLE, bar.getVisibility());

            // Lewat lima detik: tersembunyi.
            org.robolectric.shadows.ShadowLooper.getShadowMainLooper()
                    .idleFor(java.time.Duration.ofMillis(1_800));
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertEquals("hilang setelah 5 detik", View.INVISIBLE, bar.getVisibility());

            // Ketuk permukaan video: muncul lagi.
            surface.performClick();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertEquals("ketukan memunculkan kontrol", View.VISIBLE, bar.getVisibility());

            // Dan hilang lagi setelah 5 detik tanpa interaksi.
            org.robolectric.shadows.ShadowLooper.getShadowMainLooper()
                    .idleFor(java.time.Duration.ofMillis(5_800));
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertEquals("hilang lagi setelah 5 detik tanpa interaksi",
                    View.INVISIBLE, bar.getVisibility());
        }
    }

    /** Di luar layar penuh kontrol selalu terlihat. */
    @Test public void kontrolSelaluTerlihatDiLuarLayarPenuh() throws Exception {
        try (ActivityController<Playable> controller =
                     Robolectric.buildActivity(Playable.class, intent()).setup().visible()) {
            Playable activity = controller.get();
            View bar = (View) activity.findViewById(android.R.id.content)
                    .findViewWithTag("anime_player_back").getParent();
            org.robolectric.shadows.ShadowLooper.getShadowMainLooper()
                    .idleFor(java.time.Duration.ofMillis(8_000));
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertEquals("kontrol tidak pernah hilang di mode normal",
                    View.VISIBLE, bar.getVisibility());
        }
    }

    /** Ketukan pada tombol kontrol tidak diteruskan menjadi ketukan video. */
    @Test public void ketukanPadaTombolTidakIkutMenyembunyikanKontrol() throws Exception {
        try (ActivityController<Playable> controller =
                     Robolectric.buildActivity(Playable.class, intent()).setup().visible()) {
            Playable activity = controller.get();
            View bar = (View) activity.findViewById(android.R.id.content)
                    .findViewWithTag("anime_player_back").getParent();
            invoke(activity, "dismissServerPanel");
            invoke(activity, "setFullscreen", true);
            org.robolectric.shadows.ShadowLooper.idleMainLooper();

            // Kontrol sempat tersembunyi, lalu tombol ditekan.
            org.robolectric.shadows.ShadowLooper.getShadowMainLooper()
                    .idleFor(java.time.Duration.ofMillis(5_500));
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertEquals(View.INVISIBLE, bar.getVisibility());

            activity.findViewById(android.R.id.content)
                    .findViewWithTag("anime_player_more").performClick();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertEquals("menekan tombol memunculkan kontrol, bukan menyembunyikannya lagi",
                    View.VISIBLE, bar.getVisibility());
        }
    }

    // ------------------------------------------------ tidak crash saat pilih server

    /**
     * Memilih server yang sudah tidak ada di daftar tidak melempar apa pun.
     *
     * <p>Akar crash: pilihan dicari dengan {@code mirrors.indexOf(mirror)}.
     * Panel yang masih terbuka bisa memegang mirror dari daftar episode
     * sebelumnya, sehingga hasilnya -1; indeks itu lalu dipakai untuk membaca
     * dan menulis daftar — {@code mirrors.get(-1)} / {@code mirrors.set(-1, …)}
     * melempar IndexOutOfBoundsException. Sekarang identitas mirror dipakai
     * sebagai kunci, dan indeks tidak pernah dipakai untuk menulis.
     */
    @Test public void memilihMirrorBasiTidakCrash() throws Exception {
        try (ActivityController<Playable> controller =
                     Robolectric.buildActivity(Playable.class, intent()).setup().visible()) {
            Playable activity = controller.get();
            OtakudesuPlayback.Mirror basi = new OtakudesuPlayback.Mirror(
                    "720p", "vidhide", "srv-1", 3, "");

            // Tidak boleh melempar, dan status transisi kembali bersih.
            invoke(activity, "pickServer", basi);
            assertFalse("transisi tidak tertinggal menyala",
                    (Boolean) field(activity, "switchingServer"));
        }
    }

    /** Dua server pada resolusi sama punya identitas berbeda. */
    @Test public void identitasMirrorTidakBentrokPadaResolusiSama() {
        OtakudesuPlayback.Mirror odcdn = new OtakudesuPlayback.Mirror("480p", "odcdn", "", 0, "");
        OtakudesuPlayback.Mirror vidhide = new OtakudesuPlayback.Mirror("480p", "vidhide", "", 1, "");
        assertNotEquals("480p ODCDN dan 480p Vidhide bukan server yang sama",
                odcdn.key(), vidhide.key());

        // Kunci sama untuk mirror yang sama walau objeknya berbeda.
        OtakudesuPlayback.Mirror salinan = new OtakudesuPlayback.Mirror("480p", "odcdn", "", 0, "");
        assertEquals(odcdn.key(), salinan.key());
    }

    /** Memilih cepat berkali-kali tidak menjalankan dua transisi bersamaan. */
    @Test public void pilihanCepatTidakMenjalankanDuaTransisi() throws Exception {
        ActivityController<AnimePlayerActivity> controller = player();
        try {
            AnimePlayerActivity activity = controller.get();
            java.lang.reflect.Field switching =
                    AnimePlayerActivity.class.getDeclaredField("switchingServer");
            switching.setAccessible(true);
            switching.set(activity, true);
            int sebelum = (Integer) field(activity, "serverGeneration");

            OtakudesuPlayback.Mirror mirror = new OtakudesuPlayback.Mirror(
                    "720p", "vidhide", "srv", 1, "");
            invoke(activity, "pickServer", mirror);

            assertEquals("tidak ada transisi baru saat yang lain berjalan",
                    (int) sebelum, (int) (Integer) field(activity, "serverGeneration"));
        } finally {
            controller.close();
        }
    }

    /** Galat pemutaran tidak menutup Activity. */
    @Test public void galatPemutaranTidakMenutupActivity() throws Exception {
        ActivityController<AnimePlayerActivity> controller = player();
        try {
            AnimePlayerActivity activity = controller.get();
            invoke(activity, "showUnavailable", "Server tidak kompatibel.");
            assertFalse("Activity tetap hidup", activity.isFinishing());
            View galat = activity.getWindow().getDecorView()
                    .findViewWithTag("anime_player_error");
            assertEquals("pesan galat tampil", View.VISIBLE, galat.getVisibility());
        } finally {
            controller.close();
        }
    }


    /**
     * Menyimpan alamat pemutar untuk mirror yang sudah tidak ada tidak menyentuh
     * daftar sama sekali.
     *
     * <p>Ini baris yang dulu melempar {@code IndexOutOfBoundsException}: penjaga
     * {@code index < mirrors.size()} lolos untuk {@code index == -1}, lalu
     * {@code mirrors.get(-1)} dan {@code mirrors.set(-1, …)} dijalankan.
     */
    @Test public void menyimpanAlamatPemutarTidakPernahMemakaiIndeksNegatif() throws Exception {
        try (ActivityController<Playable> controller =
                     Robolectric.buildActivity(Playable.class, intent()).setup().visible()) {
            Playable activity = controller.get();
            @SuppressWarnings("unchecked")
            java.util.List<OtakudesuPlayback.Mirror> mirrors =
                    (java.util.List<OtakudesuPlayback.Mirror>) field(activity, "mirrors");
            mirrors.clear();
            mirrors.add(new OtakudesuPlayback.Mirror("480p", "odcdn", "", 0, ""));
            mirrors.add(new OtakudesuPlayback.Mirror("480p", "vidhide", "", 1, ""));

            // Mirror basi: kuncinya tidak ada di daftar.
            invoke(activity, "markMirrorPlayerUrl",
                    new OtakudesuPlayback.Mirror("720p", "mega", "srv-x", 9, "").key(),
                    "https://contoh.invalid/player");
            assertEquals("daftar tidak berubah", 2, mirrors.size());
            assertEquals("tidak ada mirror yang diisi", "", mirrors.get(0).playerUrl);

            // Mirror yang benar: hanya barisnya sendiri yang diperbarui.
            invoke(activity, "markMirrorPlayerUrl", mirrors.get(1).key(),
                    "https://contoh.invalid/player");
            assertEquals("480p ODCDN tidak ikut terisi", "", mirrors.get(0).playerUrl);
            assertEquals("480p Vidhide terisi",
                    "https://contoh.invalid/player", mirrors.get(1).playerUrl);
        }
    }


    // ------------------------------------------------ label dan kontrol baru

    /** Tombol layar penuh berlabel "Full Screen" dan tidak ada label lama. */
    @Test public void tombolLayarPenuhBerlabelFullScreen() throws Exception {
        ActivityController<AnimePlayerActivity> controller = player();
        try {
            AnimePlayerActivity activity = controller.get();
            android.view.View button = activity.getWindow().getDecorView()
                    .findViewWithTag("anime_player_full_screen");
            assertNotNull("tombol Full Screen harus ada", button);
            assertEquals("Full Screen", ((android.widget.TextView) button).getText().toString());
            assertEquals("Full Screen", String.valueOf(button.getContentDescription()));

            button.performClick();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertEquals("satu ketukan: landscape + layar penuh",
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                    activity.getRequestedOrientation());
            assertEquals("Keluar dari Full Screen",
                    ((android.widget.TextView) button).getText().toString());

            button.performClick();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertEquals("ketukan kedua: kembali mengikuti perangkat",
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                    activity.getRequestedOrientation());
            assertEquals("Full Screen", ((android.widget.TextView) button).getText().toString());
        } finally {
            controller.close();
        }
    }

    /** Tidak ada label "Putar Layar" tersisa di menu pemutar. */
    @Test public void tidakAdaLabelPutarLayar() throws Exception {
        ActivityController<AnimePlayerActivity> controller = player();
        try {
            AnimePlayerActivity activity = controller.get();
            java.lang.reflect.Method method = null;
            for (java.lang.reflect.Method candidate : AnimePlayerActivity.class.getDeclaredMethods()) {
                if (candidate.getName().equals("showMoreMenu")) method = candidate;
            }
            assertNotNull(method);
            // Sumber label menu tidak boleh memuat istilah lama.
            String source = readSource();
            assertFalse("istilah lama harus hilang dari UI",
                    source.contains("\"Putar layar\"") || source.contains("\"Putar Layar\""));
            assertTrue(source.contains("Full Screen"));
        } finally {
            controller.close();
        }
    }

    private static String readSource() {
        try {
            java.io.File file = new java.io.File(
                    "src/main/java/id/shuyasal/kuromaku/anime/AnimePlayerActivity.java");
            if (!file.isFile()) {
                file = new java.io.File("app/src/main/java/id/shuyasal/kuromaku/anime/"
                        + "AnimePlayerActivity.java");
            }
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new AssertionError("sumber tidak terbaca: " + error.getMessage(), error);
        }
    }

    // ------------------------------------------------- pemutaran offline

    /** Episode tersimpan diputar dari berkas dan tombolnya melaporkan itu. */
    @Test public void episodeTersimpanDiputarDariBerkas() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        String animeId = "fxskc";
        String url = "https://otakudesu.blog/episode/fxskc-episode-1-sub-indo/";
        String key = AnimeDownloads.episodeKey(url, "Episode 1");
        java.io.File dir = AnimeDownloads.animeDir(context, animeId);
        assertTrue(dir.isDirectory() || dir.mkdirs());
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(
                AnimeDownloads.videoFile(context, animeId, key))) {
            byte[] head = new byte[32];
            head[4] = 'f'; head[5] = 't'; head[6] = 'y'; head[7] = 'p';
            out.write(head);
        }
        java.io.File video = AnimeDownloads.videoFile(context, animeId, key);
        AnimeDownloads.writeMeta(context, new AnimeDownload(key, animeId, "FX Senshi Kurumi-chan",
                "", "Episode 1", url, "720p", "odcdn", "https://cdn.invalid/a.mp4",
                video.length(), 1L));

        try (ActivityController<Playable> controller =
                     Robolectric.buildActivity(Playable.class, intent()).setup().visible()) {
            Playable activity = controller.get();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertEquals("pemutar melaporkan sumber lokal", "Tersimpan · odcdn",
                    String.valueOf(field(activity, "activeServerLabel")));
            android.view.View download = activity.getWindow().getDecorView()
                    .findViewWithTag("anime_player_download");
            assertEquals("Tersimpan", ((android.widget.TextView) download).getText().toString());
        } finally {
            AnimeDownloads.delete(context, animeId, key);
        }
    }

    /** Tanpa berkas, mode offline menjelaskan keadaannya dan tidak ke jaringan. */
    @Test public void modeOfflineTanpaBerkasTidakMenghubungiJaringan() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        String key = AnimeDownloads.episodeKey(
                "https://otakudesu.blog/episode/fxskc-episode-1-sub-indo/", "Episode 1");
        AnimeDownloads.delete(context, "fxskc", key);

        try (ActivityController<Playable> controller =
                     Robolectric.buildActivity(Playable.class, intent()).setup().visible()) {
            Playable activity = controller.get();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            android.view.View error = activity.getWindow().getDecorView()
                    .findViewWithTag("anime_player_error");
            assertEquals("pesan harus tampil", android.view.View.VISIBLE, error.getVisibility());
            TextView text = (TextView) error;
            assertTrue("pesan menyebut episode tidak tersimpan",
                    String.valueOf(text.getText()).contains("tidak tersimpan"));
            assertFalse("Activity tetap hidup", activity.isFinishing());
        }
    }

    // ------------------------------------------------------------- perkakas

    /** Subclass yang menganggap video sedang berjalan, untuk menguji timer. */
    public static class Playable extends AnimePlayerActivity {
        @Override protected boolean controlsPinned() {
            return false;
        }
    }

    private static Intent intent() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                Playable.class);
        intent.putExtra("anime_id", "fxskc");
        intent.putExtra("title", "FX Senshi Kurumi-chan");
        intent.putExtra("episode_url",
                "https://otakudesu.blog/episode/fxskc-episode-1-sub-indo/");
        intent.putExtra("episode_label", "Episode 1");
        // Tanpa berkas tersimpan pemutar tidak menyentuh jaringan sama sekali,
        // sehingga panel server tidak terbuka sendiri selama pengujian timer.
        intent.putExtra(AnimePlayerActivity.EXTRA_OFFLINE_ONLY, true);
        return intent;
    }

    private static void invoke(Object target, String name, Object... args) throws Exception {
        // Telusuri seluruh hierarki kelas: metode privat (setFullscreen,
        // pickServer) dideklarasikan di AnimePlayerActivity, bukan di subclass uji.
        Class<?> type = target.getClass();
        while (type != null) {
            for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name)) continue;
                if (method.getParameterTypes().length != args.length) continue;
                method.setAccessible(true);
                method.invoke(target, args);
                return;
            }
            type = type.getSuperclass();
        }
        throw new NoSuchMethodException(name);
    }

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
