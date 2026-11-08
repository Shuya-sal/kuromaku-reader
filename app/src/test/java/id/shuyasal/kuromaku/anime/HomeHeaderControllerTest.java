package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Keputusan tampil/sembunyi header beranda Anime Streaming.
 *
 * <p>Yang dijaga di sini adalah sifat yang membuat scroll terasa tenang:
 * gerakan kecil tidak membalik keadaan, fling bolak-balik tidak membuat header
 * berkedip, dan di posisi paling atas header selalu tampil.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class HomeHeaderControllerTest {

    private final HomeHeaderController controller = new HomeHeaderController();
    private final List<Boolean> changes = new ArrayList<>();

    private void scroll(int dy, boolean atTop) {
        controller.onScrolled(dy, atTop, changes::add);
    }

    @Test public void gerakanKecilTidakMengubahKeadaan() {
        scroll(5, false);
        scroll(5, false);
        assertFalse(controller.isHidden());
        assertTrue("tidak ada perubahan keadaan", changes.isEmpty());
    }

    @Test public void gulirTurunMelewatiAmbangMenyembunyikan() {
        scroll(HomeHeaderController.HIDE_THRESHOLD, false);
        assertTrue(controller.isHidden());
        assertEquals(1, changes.size());
        assertTrue(changes.get(0));
    }

    @Test public void gulirNaikMemunculkanSetelahAmbangTampil() {
        scroll(HomeHeaderController.HIDE_THRESHOLD, false);
        scroll(-HomeHeaderController.SHOW_THRESHOLD + 1, false);
        assertTrue("belum cukup untuk tampil", controller.isHidden());
        scroll(-1, false);
        assertFalse(controller.isHidden());
        assertEquals(2, changes.size());
        assertFalse(changes.get(1));
    }

    @Test public void akumulasiBerbalikArahMulaiDariNol() {
        scroll(HomeHeaderController.HIDE_THRESHOLD - 1, false);
        scroll(-3, false);          // arah berbalik, akumulasi turun dibuang
        scroll(3, false);           // belum mencapai ambang dari awal lagi
        assertFalse(controller.isHidden());
    }

    @Test public void flingBolakBalikTidakMembuatHeaderBerkedip() {
        for (int i = 0; i < 40; i++) {
            scroll(i % 2 == 0 ? 9 : -9, false);
        }
        // Amplitudo 9 px, di bawah kedua ambang: keadaan harus tetap.
        assertFalse(controller.isHidden());
        assertTrue(changes.isEmpty());
    }

    @Test public void diPuncakDaftarHeaderSelaluTampil() {
        scroll(HomeHeaderController.HIDE_THRESHOLD, false);
        assertTrue(controller.isHidden());
        scroll(-1, true);           // sampai di paling atas
        assertFalse(controller.isHidden());
    }

    @Test public void keadaanTidakDiumumkanDuaKali() {
        scroll(HomeHeaderController.HIDE_THRESHOLD, false);
        scroll(HomeHeaderController.HIDE_THRESHOLD, false);
        scroll(HomeHeaderController.HIDE_THRESHOLD * 3, false);
        assertEquals("hanya satu perubahan", 1, changes.size());
    }

    @Test public void dyNolDiabaikan() {
        scroll(0, true);
        assertFalse(controller.isHidden());
        assertTrue(changes.isEmpty());
    }

    @Test public void resetMembuangAkumulasi() {
        scroll(HomeHeaderController.HIDE_THRESHOLD - 1, false);
        controller.reset();
        scroll(1, false);
        assertFalse("akumulasi lama tidak boleh terbawa", controller.isHidden());
    }
}