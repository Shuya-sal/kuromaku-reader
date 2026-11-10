package id.shuyasal.kuromaku.komiku;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
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

/**
 * Menu garis tiga harus terbuka saat benar-benar disentuh di layar.
 *
 * <p>{@code performClick()} tidak cukup: ia memanggil pendengar secara langsung
 * dan melewati pencarian view penerima sentuhan. Kalau ada view lain yang
 * menutupi tombol, {@code performClick()} tetap "berhasil" sementara sentuhan
 * sungguhan tidak pernah sampai ke tombol. Tes ini mengirim sentuhan pada
 * koordinat tombol lewat akar tampilan, persis seperti jari pengguna.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-mdpi")
public class KomikuMenuTouchTest {

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

    /** Titik tengah view dalam koordinat akar tampilan. */
    private static float[] centerOf(View root, View target) {
        int[] rootPos = new int[2];
        int[] targetPos = new int[2];
        root.getLocationOnScreen(rootPos);
        target.getLocationOnScreen(targetPos);
        float x = targetPos[0] - rootPos[0] + target.getWidth() / 2f;
        float y = targetPos[1] - rootPos[1] + target.getHeight() / 2f;
        return new float[]{x, y};
    }

    private static void tap(View root, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0);
        root.dispatchTouchEvent(down);
        root.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
        // Klik yang datang dari sentuhan diproses di antrean utama, bukan
        // langsung di dalam dispatchTouchEvent.
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
    }

    @Test public void sentuhanPadaTombolMenuMembukaSamping() {
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            Activity activity = controller.get();
            View root = activity.getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 1080, 2400);

            View menu = byTag(root, "section_menu");
            assertNotNull("tombol menu ada", menu);
            assertTrue("tombol menu punya ukuran", menu.getWidth() > 0 && menu.getHeight() > 0);

            float[] point = centerOf(root, menu);
            int sebelum = ShadowDialog.getShownDialogs().size();
            tap(root, point[0], point[1]);

            List<android.app.Dialog> shown = new ArrayList<>(ShadowDialog.getShownDialogs());
            assertTrue("sentuhan pada tombol menu membuka menu samping",
                    shown.size() > sebelum);
        }
    }

    @Test public void tidakAdaViewLainYangMenutupiTombolMenu() {
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            Activity activity = controller.get();
            View root = activity.getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 1080, 2400);

            View menu = byTag(root, "section_menu");
            assertNotNull(menu);
            float[] point = centerOf(root, menu);
            View hit = findDeepestAt(root, point[0], point[1]);
            assertNotNull("ada view pada titik tombol", hit);
            assertTrue("view penerima sentuhan adalah tombol menu atau isinya, bukan "
                            + describe(hit),
                    isSameOrDescendant(menu, hit) || isSameOrDescendant(hit, menu));
        }
    }

    /** View terdalam yang menerima sentuhan pada satu titik. */
    private static View findDeepestAt(View view, float x, float y) {
        if (view.getVisibility() != View.VISIBLE) return null;
        float localX = x - view.getLeft() + view.getScrollX();
        float localY = y - view.getTop() + view.getScrollY();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View child = group.getChildAt(i);
                View found = findDeepestAt(child, localX, localY);
                if (found != null) return found;
            }
        }
        // Koordinat yang sampai di sini sudah relatif terhadap induk view,
        // jadi batas view dihitung dari localX/localY — bukan x/y mentah.
        if (localX >= 0 && localY >= 0
                && localX < view.getWidth() && localY < view.getHeight()) return view;
        return null;
    }

    private static boolean isSameOrDescendant(View ancestor, View view) {
        if (ancestor == view) return true;
        View current = view;
        while (current != null) {
            if (current.getParent() == ancestor) return true;
            if (!(current.getParent() instanceof View)) return false;
            current = (View) current.getParent();
        }
        return false;
    }

    private static String describe(View view) {
        StringBuilder chain = new StringBuilder();
        View current = view;
        while (current != null) {
            chain.append(current.getClass().getSimpleName());
            if (current.getTag() != null) chain.append('[').append(current.getTag()).append(']');
            chain.append('(').append(current.getLeft()).append(',').append(current.getTop())
                    .append(' ').append(current.getWidth()).append('x')
                    .append(current.getHeight()).append(") < ");
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return chain.toString();
    }

    @Test public void cetakHierarkiUntukMenelusuriPenutup() {
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            Activity activity = controller.get();
            View root = activity.getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 1080, 2400);
            View menu = byTag(root, "section_menu");
            float[] point = centerOf(root, menu);
            System.out.println("MENU at " + point[0] + "," + point[1]
                    + " size=" + menu.getWidth() + "x" + menu.getHeight());
            dump(root, 0, root);
        }
    }

    private static void dump(View view, int depth, View root) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) sb.append("  ");
        sb.append(view.getClass().getSimpleName())
          .append(" tag=").append(view.getTag())
          .append(" vis=").append(view.getVisibility())
          .append(" l=").append(view.getLeft()).append(" t=").append(view.getTop())
          .append(" w=").append(view.getWidth()).append(" h=").append(view.getHeight());
        System.out.println("HIER " + sb);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                dump(group.getChildAt(i), depth + 1, root);
            }
        }
    }
}
