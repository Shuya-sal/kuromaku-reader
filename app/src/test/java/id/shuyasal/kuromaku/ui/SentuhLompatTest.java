package id.shuyasal.kuromaku.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Ketukan ganda kiri/kanan = lompat 10 detik.
 *
 * <p>Yang dijaga di sini adalah perhitungannya, bukan pemutaran videonya:
 * Robolectric tidak punya codec, jadi yang bisa dan perlu diuji adalah sisi
 * mana yang menghasilkan arah mana, dan bahwa lompatan tidak pernah keluar dari
 * rentang video.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SentuhLompatTest {

    private final SentuhLompat lompat = new SentuhLompat();

    @Test public void sisiKananMajuSisiKiriMundur() {
        assertEquals("ketukan di sisi kiri = mundur", -1, lompat.sisi(10f, 1000));
        assertEquals("ketukan di sisi kanan = maju", 1, lompat.sisi(900f, 1000));
        assertEquals("tepat di tengah dianggap maju", 1, lompat.sisi(500f, 1000));
    }

    @Test public void lebarNolTidakMembuatPembagianGagal() {
        assertEquals(1, lompat.sisi(0f, 0));
    }

    @Test public void lompatSepuluhDetikBolakBalik() {
        assertEquals(40_000L, lompat.posisiBaru(30_000L, 600_000L, 1));
        assertEquals(20_000L, lompat.posisiBaru(30_000L, 600_000L, -1));
    }

    @Test public void tidakPernahMundurSebelumNol() {
        assertEquals(0L, lompat.posisiBaru(3_000L, 600_000L, -1));
        assertEquals(0L, lompat.posisiBaru(0L, 600_000L, -1));
    }

    @Test public void tidakPernahMenyentuhAkhirBerkas() {
        // 595.000 + 10.000 melewati durasi 600.000: dijepit ke durasi - 300 ms
        // supaya pemutaran tidak dianggap selesai lalu berhenti.
        assertEquals(599_700L, lompat.posisiBaru(595_000L, 600_000L, 1));
    }

    @Test public void durasiBelumDiketahuiTetapBisaMaju() {
        // Sebelum metadata masuk, durasi bisa 0 atau negatif. Lompatan tetap
        // dijalankan; pemutar sendiri yang akan menjepitnya.
        assertEquals(40_000L, lompat.posisiBaru(30_000L, 0L, 1));
        assertEquals(40_000L, lompat.posisiBaru(30_000L, -1L, 1));
    }

    @Test public void labelMemberiTahuArahDanWaktuTujuan() {
        assertEquals("+10 detik · 1:05", lompat.label(1, 65_000L));
        assertEquals("−10 detik · 0:05", lompat.label(-1, 5_000L));
    }

    @Test public void formatJamDuaDigit() {
        assertEquals("0:00", SentuhLompat.jam(0));
        assertEquals("0:07", SentuhLompat.jam(7_400L));
        assertEquals("2:05", SentuhLompat.jam(125_000L));
        assertEquals("0:00", SentuhLompat.jam(-500L));
    }
}
