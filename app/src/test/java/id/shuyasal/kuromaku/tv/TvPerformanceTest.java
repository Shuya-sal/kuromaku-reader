package id.shuyasal.kuromaku.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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

import id.shuyasal.kuromaku.komiku.KomikuImages;
import id.shuyasal.kuromaku.ui.GlassCard;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.TvMode;

/**
 * Televisi harus ringan.
 *
 * <p>Kotak televisi 32 inci biasanya berbagi chip dan memori dengan telepon
 * kelas bawah, sedangkan yang digambar jauh lebih besar: kartu selebar layar
 * 720p. Yang paling mahal di sini adalah efek blur pada lapisan kaca dan gambar
 * halaman chapter yang didekode lebih besar daripada layar.
 *
 * <p>Tes ini mengunci penghematan yang benar-benar dilakukan, supaya tidak
 * kembali diam-diam saat tampilan dirapikan di kemudian hari.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w960dp-h540dp-land-mdpi")
public class TvPerformanceTest {

    private Context context;

    @Before public void siapkan() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    private void jadikanTelevisi() {
        Shadows.shadowOf(context.getPackageManager())
                .setSystemFeature(PackageManager.FEATURE_LEANBACK, true);
        new Prefs(context).setInt(TvMode.PREF_FORCE, 1);
    }

    // ------------------------------------------------------------- blur kaca

    @Test public void kacaTelevisiTidakMemakaiBlur() {
        jadikanTelevisi();
        GlassCard kartu = new GlassCard(context, Palette.of(Prefs.THEME_DARK),
                "source_hub_background");
        // Blur per kartu adalah biaya GPU terbesar di layar beranda TV.
        assertNull("tidak ada lapisan gambar yang diburamkan",
                kartu.findViewWithTag("glass_backdrop"));
    }

    @Test public void kacaPonselTetapMemakaiBlur() {
        // Tampilan ponsel tidak boleh berubah: blur tetap ada di sana.
        new Prefs(context).setInt(TvMode.PREF_FORCE, 0);
        GlassCard kartu = new GlassCard(context, Palette.of(Prefs.THEME_DARK),
                "source_hub_background");
        assertNotNull("blur ponsel tetap seperti semula",
                kartu.findViewWithTag("glass_backdrop"));
    }

    // --------------------------------------------------- susut gambar halaman

    @Test public void gambarSepuluhKaliLebihBesarDaripadaLayarIkutDisusut() {
        // 1600 px pada layar 720p: tanpa penyusutan satu halaman saja bisa
        // memakan belasan megabyte, dan satu chapter berisi puluhan halaman.
        int[] hasil = KomikuImages.scaledSize(1600, 2400, 720);
        assertNotNull("perlu disusutkan", hasil);
        assertEquals("lebar menjadi selebar layar", 720, hasil[0]);
        assertEquals("tinggi ikut mengecil dengan perbandingan sama", 1080, hasil[1]);
    }

    @Test public void gambarYangSudahPasTidakDisusut() {
        // Menyusut tanpa perlu hanya memakan waktu dan memori sementara.
        assertNull("gambar seukuran layar dibiarkan",
                KomikuImages.scaledSize(720, 1080, 720));
        assertNull("gambar sedikit lebih lebar dibiarkan",
                KomikuImages.scaledSize(760, 1140, 720));
    }

    @Test public void perbandinganGambarTidakBerubah() {
        int[] hasil = KomikuImages.scaledSize(1200, 1800, 600);
        assertNotNull(hasil);
        float asli = 1200f / 1800f;
        float baru = hasil[0] / (float) hasil[1];
        assertEquals("bentuk gambar tidak gepeng", asli, baru, 0.01f);
    }

    @Test public void gambarSangatTinggiTetapPunyaTinggiMinimalSatu() {
        // Halaman komik bisa sangat tinggi dan sempit; pembulatan tidak boleh
        // menghasilkan tinggi nol yang membuat bitmap gagal dibuat.
        int[] hasil = KomikuImages.scaledSize(4000, 3, 320);
        assertNotNull(hasil);
        assertTrue("tinggi minimal satu piksel", hasil[1] >= 1);
    }

    @Test public void ukuranTidakSahTidakDisusut() {
        assertNull(KomikuImages.scaledSize(0, 0, 720));
        assertNull(KomikuImages.scaledSize(-5, 100, 720));
        assertNull("tanpa batas lebar tidak disusut", KomikuImages.scaledSize(1600, 2400, 0));
    }

    // ------------------------------------------------------- memori perangkat

    @Test public void perangkatBeramKecilDikenaliDariAnggaranMemori() {
        // Ambang 192 MB memisahkan kotak televisi kelas bawah dari telepon
        // modern. Yang diuji di sini hanya bahwa keputusannya konsisten dengan
        // anggaran memori sebenarnya, bukan angka yang dikarang.
        long anggaran = Runtime.getRuntime().maxMemory();
        assertEquals("keputusan mengikuti anggaran memori",
                anggaran <= 192L * 1024 * 1024, KomikuImages.isLowMemory());
    }

    // ------------------------------------------------------ daftar hemat memori

    @Test public void mungkinDibuatBanyakKartuTanpaMemakanMemoriBerlebih() {
        // Lima kartu beranda televisi: tanpa blur, tiap kartu hanya berisi
        // lapisan warna dan border, bukan bitmap hasil render efek.
        jadikanTelevisi();
        Palette palette = Palette.of(Prefs.THEME_DARK);
        for (int index = 0; index < 5; index++) {
            GlassCard kartu = new GlassCard(context, palette, "source_hub_background");
            assertNull("kartu " + index + " tanpa blur",
                    kartu.findViewWithTag("glass_backdrop"));
            assertTrue("kartu " + index + " tetap punya isi kaca",
                    kartu.getChildCount() > 0);
        }
    }

    @Test public void latarKartuTelevisiTidakMembuatGambarSendiri() {
        // Kartu kaca sebelumnya membuat ImageView latar selebar kartu; di TV
        // lapisan itu tidak berguna karena blur dilewati.
        jadikanTelevisi();
        GlassCard kartu = new GlassCard(context, Palette.of(Prefs.THEME_DARK),
                "source_hub_background");
        int gambar = 0;
        for (int index = 0; index < kartu.getChildCount(); index++) {
            View anak = kartu.getChildAt(index);
            if (anak instanceof android.widget.ImageView) gambar++;
        }
        assertEquals("tidak ada ImageView latar yang sia-sia", 0, gambar);
    }

    @Test public void ponselMasihMemakaiLatarKaca() {
        new Prefs(context).setInt(TvMode.PREF_FORCE, 0);
        GlassCard kartu = new GlassCard(context, Palette.of(Prefs.THEME_DARK),
                "source_hub_background");
        boolean adaGambar = false;
        for (int index = 0; index < kartu.getChildCount(); index++) {
            if (kartu.getChildAt(index) instanceof android.widget.ImageView) adaGambar = true;
        }
        assertTrue("ponsel tetap memakai latar kaca", adaGambar);
    }
}
