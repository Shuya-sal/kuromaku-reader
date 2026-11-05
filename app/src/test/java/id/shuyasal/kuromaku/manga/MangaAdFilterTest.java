package id.shuyasal.kuromaku.manga;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class MangaAdFilterTest {

    @Test
    public void detectsGoogleAuthNavigation() {
        assertTrue(MangaAdFilter.isGoogleAuth(Uri.parse("https://accounts.google.com/o/oauth2/v2/auth?client_id=123")));
        assertTrue(MangaAdFilter.isGoogleAuth(Uri.parse("https://accounts.google.com/signin/oauth/v2/identifier")));
        assertTrue(MangaAdFilter.isGoogleAuth(Uri.parse("https://oauth2.googleapis.com/token")));
        assertTrue(MangaAdFilter.isGoogleAuth(Uri.parse("https://accounts.google.com/ServiceLogin")));

        assertFalse(MangaAdFilter.isGoogleAuth(Uri.parse("https://voratoon.id/")));
        assertFalse(MangaAdFilter.isGoogleAuth(Uri.parse("http://accounts.google.com/")));
        assertFalse(MangaAdFilter.isGoogleAuth(Uri.parse("https://googleads.g.doubleclick.net/")));
        assertFalse(MangaAdFilter.isGoogleAuth(null));
    }

    @Test
    public void whitelistsEssentialAuthAndSecurityServices() {
        assertTrue(MangaAdFilter.isEssentialOrWhitelisted(Uri.parse("https://accounts.google.com/o/oauth2/auth")));
        assertTrue(MangaAdFilter.isEssentialOrWhitelisted(Uri.parse("https://oauth2.googleapis.com/token")));
        assertTrue(MangaAdFilter.isEssentialOrWhitelisted(Uri.parse("https://apis.google.com/js/api.js")));
        assertTrue(MangaAdFilter.isEssentialOrWhitelisted(Uri.parse("https://ssl.gstatic.com/accounts/ui/avatar_2x.png")));
        assertTrue(MangaAdFilter.isEssentialOrWhitelisted(Uri.parse("https://www.gstatic.com/identity/boq/auth/")));

        assertTrue(MangaAdFilter.isEssentialOrWhitelisted(Uri.parse("https://www.googleapis.com/oauth2/v3/userinfo")));
        assertTrue(MangaAdFilter.isEssentialOrWhitelisted(Uri.parse("https://www.googleapis.com/auth/userinfo.profile")));

        assertFalse(MangaAdFilter.isEssentialOrWhitelisted(Uri.parse("https://www.googleapis.com/ads/reporting")));

        assertTrue(MangaAdFilter.isEssentialOrWhitelisted(Uri.parse("https://voratoon.id/manga/title")));
        assertTrue(MangaAdFilter.isEssentialOrWhitelisted(Uri.parse("https://13.voratoon.com/chapter-1")));
        assertTrue(MangaAdFilter.isEssentialOrWhitelisted(Uri.parse("https://api.voratoon.com/v1/auth/user")));
        assertTrue(MangaAdFilter.isEssentialOrWhitelisted(Uri.parse("https://voratoon.co/")));

        assertTrue(MangaAdFilter.isEssentialOrWhitelisted(Uri.parse("https://challenges.cloudflare.com/turnstile/v0/api.js")));
        assertTrue(MangaAdFilter.isEssentialOrWhitelisted(Uri.parse("https://static.cloudflareinsights.com/beacon.min.js")));

        assertTrue(MangaAdFilter.isEssentialOrWhitelisted(Uri.parse("https://fonts.googleapis.com/css2?family=Roboto")));
        assertTrue(MangaAdFilter.isEssentialOrWhitelisted(Uri.parse("https://i.imgur.com/cover123.jpg")));
        assertTrue(MangaAdFilter.isEssentialOrWhitelisted(Uri.parse("https://blogger.googleusercontent.com/img/a/sample")));
    }

    @Test
    public void blocksIdentifiedAdAndTrackerNetworks() {
        assertTrue(MangaAdFilter.isBlockedAd(Uri.parse("https://googleads.g.doubleclick.net/pagead/ads?client=ca-pub")));
        assertTrue(MangaAdFilter.isBlockedAd(Uri.parse("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js")));
        assertTrue(MangaAdFilter.isBlockedAd(Uri.parse("https://adservice.google.com/adsid/integrator.js")));
        assertTrue(MangaAdFilter.isBlockedAd(Uri.parse("https://adsterra.com/script/opts.js")));
        assertTrue(MangaAdFilter.isBlockedAd(Uri.parse("https://monetag.com/sdk.js")));
        assertTrue(MangaAdFilter.isBlockedAd(Uri.parse("https://highperformancegate.com/banner.js")));
        assertTrue(MangaAdFilter.isBlockedAd(Uri.parse("https://popads.net/serve.js")));
        assertTrue(MangaAdFilter.isBlockedAd(Uri.parse("https://propellerads.com/zone.js")));
        assertTrue(MangaAdFilter.isBlockedAd(Uri.parse("https://histats.com/js15.js")));
        assertTrue(MangaAdFilter.isBlockedAd(Uri.parse("https://mgid.com/ghits.js")));
        assertTrue(MangaAdFilter.isBlockedAd(Uri.parse("https://adnxs.com/ib.js")));

        assertTrue(MangaAdFilter.isBlockedAd(Uri.parse("https://random-cdn.org/popunder.js")));
        assertTrue(MangaAdFilter.isBlockedAd(Uri.parse("https://example.com/adbanner/view.png")));
        assertTrue(MangaAdFilter.isBlockedAd(Uri.parse("https://tracker.net/ads/display")));
    }

    @Test
    public void neverBlocksEssentialAuthOrImagesAsAds() {
        assertFalse(MangaAdFilter.isBlockedAd(Uri.parse("https://accounts.google.com/o/oauth2/v2/auth")));
        assertFalse(MangaAdFilter.isBlockedAd(Uri.parse("https://oauth2.googleapis.com/token")));
        assertFalse(MangaAdFilter.isBlockedAd(Uri.parse("https://api.voratoon.com/v1/auth/login")));
        assertFalse(MangaAdFilter.isBlockedAd(Uri.parse("https://challenges.cloudflare.com/turnstile/v0/api.js")));
        assertFalse(MangaAdFilter.isBlockedAd(Uri.parse("https://i.imgur.com/chapter-image-01.jpg")));
        assertFalse(MangaAdFilter.isBlockedAd(Uri.parse("https://13.voratoon.com/manga/solo-leveling/chapter-1")));
    }

    @Test
    public void formatsShortDisplayHostForToolbar() {
        assertEquals("Sumber: voratoon.id", MangaAdFilter.formatDisplayHost(null));
        assertEquals("Sumber: voratoon.id", MangaAdFilter.formatDisplayHost(""));
        assertEquals("Sumber: 13.voratoon.com", MangaAdFilter.formatDisplayHost("https://13.voratoon.com/manga/solo-leveling-1"));
        assertEquals("Sumber: voratoon.id", MangaAdFilter.formatDisplayHost("https://voratoon.id/home"));
        assertEquals("Login Google (accounts.google.com)", MangaAdFilter.formatDisplayHost("https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=very-long-id-123456789"));
    }
}
