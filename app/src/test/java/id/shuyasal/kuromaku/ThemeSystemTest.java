package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;

/**
 * Tema "Mengikuti Sistem" harus memilih palet terang/gelap sesuai uiMode
 * perangkat tanpa mengubah nilai yang tersimpan.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h640dp-mdpi")
public class ThemeSystemTest {

    private Prefs prefs;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        prefs = new Prefs(context);
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-night-mdpi")
    public void systemThemeBecomesDarkAtNight() {
        prefs.setTheme(Prefs.THEME_SYSTEM);
        assertEquals(Prefs.THEME_DARK, prefs.resolvedTheme());
        assertEquals(Palette.of(Prefs.THEME_DARK).background,
                Palette.of(prefs.resolvedTheme()).background);
        assertEquals("pilihan pengguna tetap tersimpan", Prefs.THEME_SYSTEM, prefs.theme());
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-notnight-mdpi")
    public void systemThemeBecomesLightDuringTheDay() {
        prefs.setTheme(Prefs.THEME_SYSTEM);
        assertEquals(Prefs.THEME_LIGHT, prefs.resolvedTheme());
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-night-mdpi")
    public void explicitLightThemeWinsOverSystemAtNight() {
        prefs.setTheme(Prefs.THEME_LIGHT);
        assertEquals(Prefs.THEME_LIGHT, prefs.resolvedTheme());
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-night-mdpi")
    public void sepiaStaysSepiaAtNight() {
        prefs.setTheme(Prefs.THEME_SEPIA);
        assertEquals(Prefs.THEME_SEPIA, prefs.resolvedTheme());
    }

    @Test
    public void oldStoredThemesKeepWorking() {
        prefs.setTheme(Prefs.THEME_DARK);
        assertEquals(Prefs.THEME_DARK, prefs.resolvedTheme());
        prefs.setTheme(Prefs.THEME_LIGHT);
        assertEquals(Prefs.THEME_LIGHT, prefs.resolvedTheme());
    }
}
