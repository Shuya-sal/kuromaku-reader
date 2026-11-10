package id.shuyasal.kuromaku.komiku;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

import java.util.ArrayList;
import java.util.List;

/** Menu garis tiga di beranda Manga Server 3 harus bisa dibuka. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-mdpi")
public class KomikuDrawerTest {

    private Context context;

    @Before public void siapkan() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(KomikuStore.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        new KomikuCatalog(context).clear();
    }

    private static View byTag(View root, String tag) {
        if (root == null) return null;
        if (tag.equals(root.getTag())) return root;
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = byTag(group.getChildAt(i), tag);
            if (found != null) return found;
        }
        return null;
    }

    @Test public void tombolMenuAdaDanBisaDiklik() {
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            Activity activity = controller.get();
            View root = activity.getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 1080, 2400);

            View menu = byTag(root, "section_menu");
            assertNotNull("tombol menu ada", menu);
            assertTrue("tombol menu terlihat", menu.getVisibility() == View.VISIBLE);
            assertTrue("tombol menu punya ukuran", menu.getWidth() > 0);

            int sebelum = ShadowDialog.getShownDialogs().size();
            menu.performClick();
            List<android.app.Dialog> shown = new ArrayList<>(ShadowDialog.getShownDialogs());
            assertTrue("menu samping terbuka setelah diklik",
                    shown.size() > sebelum);
        }
    }
}
