package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import id.shuyasal.kuromaku.SourceHubActivity;

/**
 * Beranda Anime Otakudesu: katalog native, tab default Ongoing, pencarian yang
 * tidak memakai alamat paginasi yang ditolak sumber, dan Favorit yang memakai
 * metadata asli. Sumber diuji lewat fixture HTML, bukan jaringan.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h640dp-mdpi")
public class OtakudesuScreenTest {

    private static String fixture(String name) {
        try (InputStream in = OtakudesuScreenTest.class.getResourceAsStream("/otakudesu/" + name)) {
            if (in == null) throw new IllegalStateException("Fixture hilang: " + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private final List<String> requests = new ArrayList<>();

    private OtakudesuSource source() {
        return new OtakudesuSource(url -> {
            requests.add(url);
            if (url.contains("?s=")) return fixture("search.html");
            if (url.contains("/jadwal-rilis/")) return fixture("schedule.html");
            if (url.contains("/anime/")) return fixture("detail.html");
            return fixture("ongoing.html");
        });
    }

    /** Activity asli dengan sumber offline; Robolectric butuh konstruktor tanpa argumen. */
    private static OtakudesuSource nextSource;

    public static final class Host extends OtakudesuActivity {
        @Override protected OtakudesuSource createSource() {
            return nextSource;
        }
    }

    private Host launch() {
        nextSource = source();
        ActivityController<Host> controller = Robolectric.buildActivity(Host.class).setup();
        return controller.get();
    }

    private void await(OtakudesuActivity host) throws Exception {
        Field field = OtakudesuActivity.class.getDeclaredField("worker");
        field.setAccessible(true);
        ((ExecutorService) field.get(host)).submit(() -> {}).get(5, TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private View tagged(OtakudesuActivity host, String tag) {
        View found = host.getWindow().getDecorView().findViewWithTag(tag);
        assertNotNull(tag, found);
        return found;
    }

    @Test public void berandaLangsungMenampilkanOngoing() throws Exception {
        OtakudesuActivity host = launch();
        await(host);

        RecyclerView grid = (RecyclerView) tagged(host, "otakudesu_grid");
        assertTrue("Kartu anime harus terisi", grid.getAdapter().getItemCount() >= 10);

        TextView status = (TextView) tagged(host, "otakudesu_status");
        assertTrue(status.getText().toString().contains("anime"));

        Button ongoing = (Button) tagged(host, "otakudesu_chip_Ongoing");
        assertTrue("Tab default harus aktif", ongoing.isSelected());

        assertTrue("Permintaan pertama ke halaman ongoing",
                requests.get(0).equals("https://otakudesu.blog/ongoing-anime/"));
        TextView error = (TextView) tagged(host, "otakudesu_error");
        assertEquals("Tidak ada galat saat katalog berhasil dimuat",
                "", error.getText().toString().trim());
    }

    @Test public void kartuMenampilkanJudulDanEpisodeTerbaru() throws Exception {
        OtakudesuActivity host = launch();
        await(host);

        RecyclerView grid = (RecyclerView) tagged(host, "otakudesu_grid");
        grid.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        grid.layout(0, 0, 1080, 1920);

        View card = grid.getChildAt(0);
        assertNotNull("Kartu pertama harus terpasang", card);
        TextView title = card.findViewWithTag("otakudesu_title");
        TextView meta = card.findViewWithTag("otakudesu_meta");
        assertNotNull(title);
        assertNotNull(meta);
        assertFalse(title.getText().toString().isEmpty());
        assertTrue("Kartu memuat episode terbaru",
                meta.getText().toString().contains("Episode"));
    }

    @Test public void pindahTabMemakaiKatalogTerpisah() throws Exception {
        OtakudesuActivity host = launch();
        await(host);
        requests.clear();

        tagged(host, "otakudesu_chip_Complete").performClick();
        await(host);

        assertTrue("Complete memakai halaman complete-anime",
                requests.get(0).startsWith("https://otakudesu.blog/complete-anime/"));
        Button complete = (Button) tagged(host, "otakudesu_chip_Complete");
        assertTrue(complete.isSelected());
    }

    @Test public void jadwalRilisDitampilkanPerHari() throws Exception {
        OtakudesuActivity host = launch();
        await(host);

        tagged(host, "otakudesu_chip_Jadwal Rilis").performClick();
        await(host);

        RecyclerView grid = (RecyclerView) tagged(host, "otakudesu_grid");
        assertTrue("Jadwal berisi anime", grid.getAdapter().getItemCount() >= 20);
        TextView status = (TextView) tagged(host, "otakudesu_status");
        assertTrue(status.getText().toString().contains("jadwal"));
    }

    @Test public void pencarianTidakMemakaiHalamanPaginasiYangDitolak() throws Exception {
        OtakudesuActivity host = launch();
        await(host);
        requests.clear();

        EditText search = (EditText) tagged(host, "otakudesu_search");
        search.setText("naruto");
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        await(host);

        boolean searched = false;
        for (String request : requests) {
            assertFalse("Sumber menjawab 403 untuk /page/N/?s=", request.contains("/page/")
                    && request.contains("?s="));
            if (request.contains("?s=naruto")) searched = true;
        }
        assertTrue("Pencarian harus memakai alamat yang diterima sumber", searched);

        RecyclerView grid = (RecyclerView) tagged(host, "otakudesu_grid");
        assertTrue(grid.getAdapter().getItemCount() >= 1);
    }

    @Test public void favoritMenampilkanJudulAsliBukanSlug() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(
                OtakudesuStore.FILE_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        new OtakudesuStore(context).toggleFavorite("uchi-otouto-sumimasen-sub-indo",
                "Uchi no Otouto-domo ga Sumimasen", "https://gambar/poster.jpg");

        OtakudesuActivity host = launch();
        await(host);
        tagged(host, "otakudesu_chip_Favorit").performClick();
        await(host);

        RecyclerView grid = (RecyclerView) tagged(host, "otakudesu_grid");
        assertEquals(1, grid.getAdapter().getItemCount());

        grid.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        grid.layout(0, 0, 1080, 1920);
        TextView title = grid.getChildAt(0).findViewWithTag("otakudesu_title");
        assertEquals("Uchi no Otouto-domo ga Sumimasen", title.getText().toString());
    }

    @Test public void gantiTemaTidakMemuatUlangKatalog() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(id.shuyasal.kuromaku.ui.Prefs.FILE_NAME,
                Context.MODE_PRIVATE).edit()
                .putInt("theme", id.shuyasal.kuromaku.ui.Prefs.THEME_LIGHT).commit();

        OtakudesuActivity host = launch();
        await(host);
        RecyclerView before = (RecyclerView) tagged(host, "otakudesu_grid");
        int count = before.getAdapter().getItemCount();
        assertTrue(count >= 10);
        requests.clear();

        // Pengguna mengganti tema, lalu kembali ke halaman ini.
        context.getSharedPreferences(id.shuyasal.kuromaku.ui.Prefs.FILE_NAME,
                Context.MODE_PRIVATE).edit()
                .putInt("theme", id.shuyasal.kuromaku.ui.Prefs.THEME_DARK).commit();
        host.onResume();
        await(host);

        assertTrue("Perubahan tema tidak boleh memicu permintaan katalog baru",
                requests.isEmpty());
        RecyclerView after = (RecyclerView) tagged(host, "otakudesu_grid");
        assertEquals("Katalog tetap berisi data yang sama", count,
                after.getAdapter().getItemCount());
    }

    @Test public void galatSumberTampilDenganPesanDanTombolCobaLagi() throws Exception {
        nextSource = new OtakudesuSource(url -> {
            throw new java.io.IOException("mati");
        });
        Host host = Robolectric.buildActivity(Host.class).setup().get();

        Field field = OtakudesuActivity.class.getDeclaredField("worker");
        field.setAccessible(true);
        ((ExecutorService) field.get(host)).submit(() -> {}).get(5, TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        TextView error = (TextView) tagged(host, "otakudesu_error");
        assertEquals(View.VISIBLE, error.getVisibility());
        assertTrue(error.getText().toString().contains("tidak dapat diakses"));
        assertEquals(View.VISIBLE, tagged(host, "otakudesu_retry").getVisibility());
    }

    @Test public void drawerMemilikiEmpatSumber() {
        SectionDrawerSections sections = new SectionDrawerSections();
        assertTrue(sections.names().contains("ANIME_OTAKUDESU"));
        assertEquals(4, sections.names().size());
    }

    private static final class SectionDrawerSections {
        List<String> names() {
            List<String> names = new ArrayList<>();
            for (id.shuyasal.kuromaku.ui.SectionDrawer.Section section
                    : id.shuyasal.kuromaku.ui.SectionDrawer.Section.values()) {
                names.add(section.name());
            }
            return names;
        }
    }

    @Test public void sourceHubMenampilkanKartuOtakudesu() {
        SourceHubActivity hub = Robolectric.buildActivity(SourceHubActivity.class).setup().get();
        assertNotNull("Kartu Anime Otakudesu harus ada",
                hub.getWindow().getDecorView().findViewWithTag("source_hub_card_otakudesu"));
        assertNotNull(hub.getWindow().getDecorView().findViewWithTag("source_hub_card_shinigami"));
    }

    @Test public void tigaActivityAnimeTerdaftarDiManifest() {
        Context context = ApplicationProvider.getApplicationContext();
        for (Class<?> screen : new Class<?>[]{OtakudesuActivity.class,
                OtakudesuDetailActivity.class, AnimePlayerActivity.class}) {
            Intent intent = new Intent(context, screen);
            assertNotNull(screen.getSimpleName() + " harus terdaftar",
                    context.getPackageManager().resolveActivity(intent, 0));
        }
    }
}