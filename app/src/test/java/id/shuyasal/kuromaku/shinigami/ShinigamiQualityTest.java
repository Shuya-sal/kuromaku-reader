package id.shuyasal.kuromaku.shinigami;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ShinigamiQualityTest {

    @Test public void hdNeverExceedsSourceWidth() {
        // Sumber hanya 800 px: HD berarti native, bukan upscaling ke lebar layar.
        assertEquals(800, ShinigamiQuality.HD.targetWidth(1440, 800));
        assertEquals(800, ShinigamiQuality.HD.targetWidth(1080, 800));
    }

    @Test public void autoFollowsScreenButStopsAtSource() {
        assertEquals(720, ShinigamiQuality.AUTO.targetWidth(720, 800));
        assertEquals(800, ShinigamiQuality.AUTO.targetWidth(1080, 800));
    }

    @Test public void saverStaysUnderBudget() {
        assertEquals(720, ShinigamiQuality.SAVER.targetWidth(1440, 1600));
        assertEquals(600, ShinigamiQuality.SAVER.targetWidth(600, 1600));
    }

    @Test public void pixelFormatFollowsMode() {
        // Halaman 800x10000 = 8 MP: otomatis memakai 16-bit supaya hemat, HD tetap 32-bit.
        assertTrue(ShinigamiQuality.AUTO.useRgb565(800, 10000));
        assertTrue(ShinigamiQuality.SAVER.useRgb565(800, 1200));
        assertEquals(false, ShinigamiQuality.HD.useRgb565(800, 10000));
        assertEquals(false, ShinigamiQuality.AUTO.useRgb565(800, 1200));
    }

    @Test public void cyclingCoversEveryModeAndPersists() {
        Context context = ApplicationProvider.getApplicationContext();
        ShinigamiQuality stored = ShinigamiQuality.stored(context);
        assertEquals(ShinigamiQuality.AUTO, stored);
        ShinigamiQuality next = stored.next();
        next.save(context);
        assertEquals(next, ShinigamiQuality.stored(context));
        assertEquals(stored, next.next().next());
    }

    @Test public void labelsMatchUserFacingNames() {
        assertEquals("Otomatis", ShinigamiQuality.AUTO.label);
        assertEquals("Hemat Data", ShinigamiQuality.SAVER.label);
        assertEquals("HD / Kualitas Asli", ShinigamiQuality.HD.label);
        assertTrue(ShinigamiQuality.HD.summary(800).contains("800"));
    }
}
