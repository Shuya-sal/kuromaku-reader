package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.View;
import android.widget.ImageView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import id.shuyasal.kuromaku.shinigami.ShinigamiActivity;

/** Source Hub: launcher tunggal, tiga kartu, latar lokal dengan centerCrop. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h740dp")
public class SourceHubActivityTest {

    private SourceHubActivity launch() {
        return Robolectric.buildActivity(SourceHubActivity.class).setup().get();
    }

    private View card(SourceHubActivity hub, String tag) {
        View found = hub.getWindow().getDecorView().findViewWithTag(tag);
        assertNotNull("kartu " + tag, found);
        return found;
    }

    @Test public void sourceHubIsTheOnlyLauncher() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(context.getPackageName());
        java.util.List<ResolveInfo> resolved = context.getPackageManager()
                .queryIntentActivities(intent, 0);
        assertNotNull("launcher harus terdaftar", resolved);
        assertEquals("tidak boleh ada dua launcher", 1, resolved.size());
        assertEquals(SourceHubActivity.class.getName(), resolved.get(0).activityInfo.name);
    }

    @Test public void threeCardsOpenExistingScreens() {
        SourceHubActivity hub = launch();
        assertNotNull(card(hub, "source_hub_card_novel"));
        assertNotNull(card(hub, "source_hub_card_voratoon"));
        assertNotNull(card(hub, "source_hub_card_shinigami"));

        card(hub, "source_hub_card_novel").performClick();
        Intent novel = Shadows.shadowOf(hub).getNextStartedActivity();
        assertEquals(MainActivity.class.getName(), novel.getComponent().getClassName());

        card(hub, "source_hub_card_voratoon").performClick();
        Intent manga = Shadows.shadowOf(hub).getNextStartedActivity();
        assertEquals(MangaActivity.class.getName(), manga.getComponent().getClassName());

        card(hub, "source_hub_card_shinigami").performClick();
        Intent shinigami = Shadows.shadowOf(hub).getNextStartedActivity();
        assertEquals(ShinigamiActivity.class.getName(), shinigami.getComponent().getClassName());
    }

    @Test public void cardsDoNotStackActivities() {
        SourceHubActivity hub = launch();
        card(hub, "source_hub_card_shinigami").performClick();
        Intent started = Shadows.shadowOf(hub).getNextStartedActivity();
        int flags = started.getFlags();
        assertTrue("CLEAR_TOP", (flags & Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0);
        assertTrue("SINGLE_TOP", (flags & Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0);
    }

    @Test public void backgroundUsesLocalImageWithCenterCropAndScrim() {
        SourceHubActivity hub = launch();
        ImageView background = hub.getWindow().getDecorView()
                .findViewWithTag("source_hub_background");
        assertNotNull(background);
        assertEquals(ImageView.ScaleType.CENTER_CROP, background.getScaleType());
        assertNotNull("gambar lokal harus terpasang", background.getDrawable());
        assertNotNull(hub.getWindow().getDecorView().findViewWithTag("source_hub_scrim"));
    }

    @Test public void drawerOffersHomeEntry() {
        SourceHubActivity hub = launch();
        final boolean[] home = {false};
        android.app.Dialog drawer = id.shuyasal.kuromaku.ui.SectionDrawer.show(hub,
                id.shuyasal.kuromaku.ui.SectionDrawer.Section.NOVEL,
                id.shuyasal.kuromaku.ui.Palette.of(id.shuyasal.kuromaku.ui.Prefs.THEME_DARK),
                new id.shuyasal.kuromaku.ui.SectionDrawer.Listener() {
                    @Override public void onNovel() { }
                    @Override public void onMangaVoratoon() { }
                    @Override public void onShinigami() { }
                    @Override public void onBackup() { }
                    @Override public void onRestore() { }
                    @Override public void onHome() { home[0] = true; }
                });
        View item = drawer.findViewById(android.R.id.content).findViewWithTag("drawer_home");
        assertNotNull("menu Beranda harus ada", item);
        item.performClick();
        assertTrue(home[0]);
        drawer.dismiss();
    }
}
