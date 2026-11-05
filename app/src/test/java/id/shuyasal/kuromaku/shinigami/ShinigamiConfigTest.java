package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ShinigamiConfigTest {

    private Context context;
    private ShinigamiConfig config;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        config = new ShinigamiConfig(context);
        context.getSharedPreferences(ShinigamiConfig.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        config = new ShinigamiConfig(context);
    }

    @Test
    public void withoutConfigurationTheKnownAddressesAreUsed() {
        assertEquals(ShinigamiConfig.DEFAULT_API_BASE, config.apiBase());
        assertEquals(ShinigamiConfig.DEFAULT_SITE_BASE, config.siteBase());
    }

    @Test
    public void aValidAddressIsStoredAndReadBack() {
        assertTrue(config.setApiBase("https://api.shngm.io/v1/"));
        assertEquals("https://api.shngm.io/v1", config.apiBase());
        assertTrue(config.setSiteBase("https://11.shinigami.asia"));
        assertEquals("https://11.shinigami.asia", config.siteBase());
    }

    @Test
    public void anotherMirrorOfTheSourceIsAllowed() {
        assertTrue(ShinigamiConfig.isValidBase("https://12.shinigami.asia"));
        assertTrue(ShinigamiConfig.isValidBase("https://shinigami.to"));
        assertTrue(ShinigamiConfig.isValidBase("https://api.shngm.io/v1"));
    }

    @Test
    public void plainHttpIsRefused() {
        assertFalse(ShinigamiConfig.isValidBase("http://api.shngm.io/v1"));
        assertFalse(ShinigamiConfig.isValidBase("http://11.shinigami.asia"));
    }

    @Test
    public void aHostThatMerelyContainsTheTrustedNameIsRefused() {
        assertFalse(ShinigamiConfig.isValidBase("https://shinigami.asia.evil.example"));
        assertFalse(ShinigamiConfig.isValidBase("https://notshinigami.asia"));
        assertFalse(ShinigamiConfig.isValidBase("https://evil.example/shinigami.asia"));
    }

    @Test
    public void aHostOutsideTheSourceIsRefused() {
        assertFalse(ShinigamiConfig.isValidBase("https://example.com"));
        assertFalse(ShinigamiConfig.isValidBase("https://shinigami.asia.com"));
    }

    @Test
    public void malformedValuesAreRefused() {
        assertFalse(ShinigamiConfig.isValidBase(""));
        assertFalse(ShinigamiConfig.isValidBase(null));
        assertFalse(ShinigamiConfig.isValidBase("api.shngm.io"));
        assertFalse(ShinigamiConfig.isValidBase("https://"));
        assertFalse(ShinigamiConfig.isValidBase("https://api.shngm.io:notaport"));
        assertFalse(ShinigamiConfig.isValidBase("https://user@api.shngm.io"));
        assertFalse(ShinigamiConfig.isValidBase("https://api_shngm.io"));
    }

    @Test
    public void aRefusedValueIsNotStored() {
        assertFalse(config.setApiBase("http://api.shngm.io/v1"));
        assertEquals(ShinigamiConfig.DEFAULT_API_BASE, config.apiBase());
        assertFalse(config.setSiteBase("https://contoh.example"));
        assertEquals(ShinigamiConfig.DEFAULT_SITE_BASE, config.siteBase());
    }
}
