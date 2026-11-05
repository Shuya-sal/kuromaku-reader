package id.shuyasal.kuromaku.shinigami;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public class ShinigamiImageZoomTest {
    @Test public void invalidAddressShowsRetryRatherThanBlackScreen() {
        Context context=ApplicationProvider.getApplicationContext();
        Intent intent=new Intent(context,ShinigamiImageZoomActivity.class)
                .putExtra("url","http://untrusted.example/image.png");
        try(ActivityController<ShinigamiImageZoomActivity> controller=Robolectric
                .buildActivity(ShinigamiImageZoomActivity.class,intent).setup()) {
            View root=controller.get().getWindow().getDecorView();
            assertEquals(View.VISIBLE,root.findViewWithTag("zoom_error").getVisibility());
            assertEquals(View.VISIBLE,root.findViewWithTag("zoom_retry").getVisibility());
            assertTrue(root.findViewWithTag("zoom_image") instanceof id.shuyasal.kuromaku.ui.ZoomableImageView);
        }
    }
}
