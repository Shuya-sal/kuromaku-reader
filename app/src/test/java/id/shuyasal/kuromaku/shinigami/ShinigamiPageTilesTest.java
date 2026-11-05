package id.shuyasal.kuromaku.shinigami;

import androidx.test.core.app.ApplicationProvider;

import android.graphics.Bitmap;
import android.graphics.BitmapRegionDecoder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ShinigamiPageTilesTest {

    private static File png(String name, int width, int height) throws Exception {
        File dir = new File(ApplicationProvider.getApplicationContext().getCacheDir(), "tiles-test");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File file = new File(dir, name);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();
        return file;
    }

    @Test public void slicesCoverWholePageWithoutMissingRow() {
        List<int[]> parts = ShinigamiPageTiles.slices(10000, 1600);
        assertEquals(7, parts.size());
        assertEquals(0, parts.get(0)[0]);
        int covered = 0;
        for (int[] part : parts) covered += part[1];
        assertEquals(10000, covered);
        assertEquals(10000 - 6 * 1600, parts.get(6)[1]);
    }

    @Test public void indexMapsPixelToSlice() {
        assertEquals(0, ShinigamiPageTiles.indexOf(0, 1280));
        assertEquals(0, ShinigamiPageTiles.indexOf(1279, 1280));
        assertEquals(1, ShinigamiPageTiles.indexOf(1280, 1280));
        assertEquals(7, ShinigamiPageTiles.indexOf(9999, 1280));
    }

    @Test public void regionKeepsSourceWidthInsteadOfShrinkingIt() throws Exception {
        File file = png("tall.png", 300, 2500);
        BitmapRegionDecoder decoder = ShinigamiPageTiles.open(file);
        assertNotNull(decoder);
        Bitmap tile = ShinigamiPageTiles.region(decoder,
                new android.graphics.Rect(0, 1200, 300, 2400), false);
        assertNotNull(tile);
        // Lebar tetap 300 px: tidak ada penyusutan seperti AT_MOST ke tinggi 4096.
        assertEquals(300, tile.getWidth());
        assertEquals(1200, tile.getHeight());
        tile.recycle();
        decoder.recycle();
    }

    @Test public void boundsRejectNonImageFile() throws Exception {
        File broken = new File(ApplicationProvider.getApplicationContext().getCacheDir(), "broken.img");
        try (FileOutputStream out = new FileOutputStream(broken)) {
            out.write("bukan gambar".getBytes("UTF-8"));
        }
        assertNull(ShinigamiPageTiles.bounds(broken));
        assertNull(ShinigamiPageTiles.open(broken));
    }

    @Test public void decodeFullKeepsNativePixelsUnderBudget() throws Exception {
        File file = png("page.png", 300, 1000);
        Bitmap bitmap = ShinigamiPageTiles.decodeFull(file, 8_000_000, true);
        assertNotNull(bitmap);
        assertEquals(300, bitmap.getWidth());
        assertEquals(1000, bitmap.getHeight());
        bitmap.recycle();
    }

    @Test public void decodeFullShrinksOnlyWhenBudgetExceeded() throws Exception {
        File file = png("big.png", 300, 1000);
        Bitmap bitmap = ShinigamiPageTiles.decodeFull(file, 100_000, true);
        assertNotNull(bitmap);
        assertTrue(bitmap.getWidth() * bitmap.getHeight() <= 100_000);
        assertTrue(bitmap.getWidth() < 300);
        bitmap.recycle();
    }
}
