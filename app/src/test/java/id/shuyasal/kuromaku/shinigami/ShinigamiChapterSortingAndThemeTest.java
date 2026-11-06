package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.ThemeManager;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ShinigamiChapterSortingAndThemeTest {

    private Context context;

    @Before
    public void setup() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(Prefs.FILE_NAME, 0).edit().clear().commit();
    }

    @Test
    public void themeManagerMapsThemesToAppCompatModes() {
        ThemeManager.apply(Prefs.THEME_LIGHT);
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode());

        ThemeManager.apply(Prefs.THEME_DARK);
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode());

        ThemeManager.apply(Prefs.THEME_SYSTEM);
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.getDefaultNightMode());
    }

    @Test
    public void prefsSetThemeTriggersThemeManager() {
        Prefs prefs = new Prefs(context);
        prefs.setTheme(Prefs.THEME_DARK);
        assertEquals(Prefs.THEME_DARK, prefs.theme());
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode());

        prefs.setTheme(Prefs.THEME_LIGHT);
        assertEquals(Prefs.THEME_LIGHT, prefs.theme());
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode());
    }

    @Test
    public void canonicalChapterComparatorHandlesPrologZeroDecimalAndEpilogue() {
        ShinigamiChapter prolog = new ShinigamiChapter("p", "m", 0.0, "Prolog", "", "");
        ShinigamiChapter ch0 = new ShinigamiChapter("0", "m", 0.0, "", "", "");
        ShinigamiChapter ch1 = new ShinigamiChapter("1", "m", 1.0, "", "", "");
        ShinigamiChapter ch1_5 = new ShinigamiChapter("1.5", "m", 1.5, "Chapter 1.5 - Side Story", "", "");
        ShinigamiChapter ch2 = new ShinigamiChapter("2", "m", 2.0, "", "", "");
        ShinigamiChapter epilogue = new ShinigamiChapter("e", "m", 100.0, "Epilog", "", "");

        List<ShinigamiChapter> list = new ArrayList<>();
        list.add(ch2);
        list.add(epilogue);
        list.add(ch1_5);
        list.add(ch0);
        list.add(prolog);
        list.add(ch1);

        Collections.sort(list, ShinigamiDetailActivity.COMPARATOR_ASC);
        assertEquals("p", list.get(0).id);
        assertEquals("0", list.get(1).id);
        assertEquals("1", list.get(2).id);
        assertEquals("1.5", list.get(3).id);
        assertEquals("2", list.get(4).id);
        assertEquals("e", list.get(5).id);

        Collections.sort(list, ShinigamiDetailActivity.COMPARATOR_DESC);
        assertEquals("e", list.get(0).id);
        assertEquals("2", list.get(1).id);
        assertEquals("1.5", list.get(2).id);
        assertEquals("1", list.get(3).id);
        assertEquals("0", list.get(4).id);
        assertEquals("p", list.get(5).id);
    }

    @Test
    public void retainedStateStoresAllModesAndItems() {
        List<ShinigamiManga> mangas = new ArrayList<>();
        mangas.add(new ShinigamiManga("m1", "Manga 1", "", "cover1", "", 1, "2024", 9.0, 1000L, 1.0, "1d", Collections.emptyList(), Collections.emptyList(), "manga"));
        List<ShinigamiActivity.Row> rows = new ArrayList<>();
        rows.add(new ShinigamiActivity.Row("m1", "Manga 1", "cover1", "ch1", 1.0, "today"));
        List<ShinigamiDownloads.Manga> downloads = new ArrayList<>();

        ShinigamiActivity.RetainedState state = new ShinigamiActivity.RetainedState(
                mangas, rows, downloads, 1, 10, "query", ShinigamiSource.Sort.LATEST,
                "", "", ShinigamiActivity.Mode.HISTORY, 5, 20);

        assertEquals(1, state.items.size());
        assertEquals(1, state.rows.size());
        assertEquals("query", state.query);
        assertEquals(ShinigamiActivity.Mode.HISTORY, state.mode);
        assertEquals(5, state.scrollPosition);
        assertEquals(20, state.scrollOffset);
    }
}
