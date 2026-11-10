package id.shuyasal.kuromaku.komiku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;

/**
 * Dua hal yang dilaporkan pengguna:
 *
 * <ul>
 *   <li>Hasil pencarian harus tampil seperti daftar baris "Chapter Terbaru" —
 *       bukan grid kartu besar.</li>
 *   <li>Menu samping di layar lain harus bisa membuka Manga Server 3.</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-mdpi")
public class KomikuSearchLayoutTest {

    private android.content.Context context;

    @Before public void siapkan() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(KomikuStore.FILE_NAME,
                android.content.Context.MODE_PRIVATE).edit().clear().commit();
        new KomikuCatalog(context).clear();
    }

    private void simpanKatalog(int jumlah) throws Exception {
        List<KomikuManga> items = new ArrayList<>();
        for (int i = 1; i <= jumlah; i++) {
            String slug = "solo-leveling-" + i;
            items.add(new KomikuManga(slug, "Solo Leveling " + i,
                    KomikuConfig.BASE + "/manga/" + slug + "/",
                    "https://thumbnail.komiku.org/" + slug + ".jpg",
                    "Manhwa", "Chapter " + i, i + " jam lalu"));
        }
        new KomikuCatalog(context).save(items, 1);
    }

    private static void tunggu(Runnable aksi) {
        aksi.run();
        for (int i = 0; i < 100; i++) {
            // Pencarian punya debounce 350 ms; jam harus dimajukan, bukan hanya
            // antrean utama dikosongkan, kalau tidak pencarian tidak pernah jalan.
            org.robolectric.shadows.ShadowSystemClock.advanceBy(
                    java.time.Duration.ofMillis(50));
            ShadowLooper.idleMainLooper();
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            ShadowLooper.idleMainLooper();
        }
    }

    @Test public void hasilPencarianMemakaiDaftarBaris() throws Exception {
        simpanKatalog(30);
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            KomikuActivity activity = controller.get();
            tunggu(() -> activity.ketikUntukTes("Solo Leveling 12"));

            assertEquals("satu hasil", 1, activity.jumlahItemUntukTes());
            assertTrue("memakai mode baris, bukan grid",
                    activity.modeBarisUntukTes());
        }
    }

    @Test public void hasilPencarianMenampilkanBeberapaJudul() throws Exception {
        simpanKatalog(40);
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            KomikuActivity activity = controller.get();
            tunggu(() -> activity.ketikUntukTes("solo leveling"));

            assertTrue("banyak hasil", activity.jumlahItemUntukTes() >= 10);
            assertTrue("tetap memakai baris", activity.modeBarisUntukTes());
        }
    }

    @Test public void barisHasilPencarianTerikatTanpaError() throws Exception {
        simpanKatalog(25);
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            KomikuActivity activity = controller.get();
            tunggu(() -> activity.ketikUntukTes("Solo"));

            RecyclerView list = activity.cariDaftarUntukTes();
            assertNotNull(list);
            RecyclerView.Adapter<?> adapter = list.getAdapter();
            assertNotNull(adapter);
            android.view.View root = activity.getWindow().getDecorView();
            root.measure(android.view.View.MeasureSpec.makeMeasureSpec(
                            1080, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(
                            2400, android.view.View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 1080, 2400);

            @SuppressWarnings({"rawtypes", "unchecked"})
            RecyclerView.Adapter raw = adapter;
            for (int i = 0; i < raw.getItemCount(); i++) {
                RecyclerView.ViewHolder holder = raw.createViewHolder(list,
                        raw.getItemViewType(i));
                raw.bindViewHolder(holder, i);
            }
            assertTrue("semua baris terikat", raw.getItemCount() > 0);
        }
    }

    @Test public void menuSampingDariLayarNovelMembukaServer3() {
        Activity activity = Robolectric.buildActivity(
                id.shuyasal.kuromaku.MainActivity.class).setup().get();
        assertNotNull(activity);

        // Menu yang dipakai layar Novel, bukan menu milik Server 3.
        android.app.Dialog drawer = id.shuyasal.kuromaku.ui.SectionDrawer.show(activity,
                id.shuyasal.kuromaku.ui.SectionDrawer.Section.NOVEL,
                id.shuyasal.kuromaku.ui.Palette.of(0),
                new id.shuyasal.kuromaku.ui.SectionDrawer.Listener() {
                    @Override public void onNovel() { }
                    @Override public void onMangaVoratoon() { }
                    @Override public void onShinigami() { }
                    @Override public void onBackup() { }
                    @Override public void onRestore() { }
                    @Override public void onKomiku() {
                        activity.startActivity(new Intent(activity, KomikuActivity.class));
                        activity.finish();
                    }
                });
        assertNotNull(drawer);
        android.view.View decor = drawer.getWindow().getDecorView();
        android.view.View item = cariTag(decor, "drawer_manga_komiku");
        assertNotNull("item Manga Server 3 ada di menu", item);

        item.performClick();
        ShadowLooper.idleMainLooper();

        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull("layar Manga Server 3 dibuka", next);
        assertTrue("tujuan KomikuActivity",
                next.getComponent() != null
                        && next.getComponent().getClassName()
                        .equals(KomikuActivity.class.getName()));
    }

    @Test public void menuDariLayarNovelSungguhanMembukaServer3() {
        // Klik menu garis tiga yang benar-benar ada di layar Novel, lalu pilih
        // item Manga Server 3 dari menu yang benar-benar dibuka layar itu.
        // Ini menangkap layar yang lupa mengimplementasikan onKomiku(): item
        // menunya tampil, tetapi tidak membuka apa pun.
        Activity activity = Robolectric.buildActivity(
                id.shuyasal.kuromaku.MainActivity.class).setup().get();
        android.view.View root = activity.getWindow().getDecorView();
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(
                        1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(
                        2400, android.view.View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 2400);

        android.view.View menuButton = cariTag(root, "section_menu");
        assertNotNull("tombol menu ada di layar Novel", menuButton);
        menuButton.performClick();
        ShadowLooper.idleMainLooper();

        android.app.Dialog drawer = null;
        for (android.app.Dialog dialog : org.robolectric.shadows.ShadowDialog
                .getShownDialogs()) {
            if (dialog.isShowing()) drawer = dialog;
        }
        assertNotNull("menu samping terbuka", drawer);
        android.view.View item = cariTag(drawer.getWindow().getDecorView(),
                "drawer_manga_komiku");
        assertNotNull("item Manga Server 3 ada", item);

        item.performClick();
        ShadowLooper.idleMainLooper();

        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull("layar Manga Server 3 dibuka dari menu Novel", next);
        assertTrue("tujuan KomikuActivity",
                next.getComponent() != null
                        && next.getComponent().getClassName()
                        .equals(KomikuActivity.class.getName()));
    }

    private static android.view.View cariTag(android.view.View root, String tag) {
        if (root == null) return null;
        if (tag.equals(root.getTag())) return root;
        if (!(root instanceof android.view.ViewGroup)) return null;
        android.view.ViewGroup group = (android.view.ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            android.view.View found = cariTag(group.getChildAt(i), tag);
            if (found != null) return found;
        }
        return null;
    }
}
