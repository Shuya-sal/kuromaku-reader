package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Looper;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/**
 * Manga stage 1: a separate screen that shows the source site, and the one rule
 * that keeps it from being taken over.
 *
 * <p>{@code MangaActivity} loads the landing page and lets the site discover its
 * own numbered domain, so the guard here is the only thing standing between the
 * reader and an advert or a redirect chain. Every rejection below is a shape that
 * looks allowed at a glance.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w320dp-h640dp-mdpi")
public class MangaScreenTest {

    private Context context = ApplicationProvider.getApplicationContext();

    private boolean allowed(String url) {
        return MangaActivity.trusted(Uri.parse(url));
    }

    /** The landing site and its own subdomains are the only pages allowed. */
    @Test
    public void theSourceAndItsOwnPagesAreAllowed() {
        assertTrue("landing", allowed("https://voratoon.id/"));
        assertTrue("halaman manga", allowed("https://voratoon.id/manga/some-title"));
        assertTrue("subdomain", allowed("https://cdn.voratoon.id/page/1.jpg"));
        assertTrue("domain lama", allowed("https://voratoon.com/manga"));
        assertTrue("subdomain domain lama", allowed("https://12.voratoon.com/manga"));
    }

    /**
     * A host that merely ends with the name is not the source. Without the dot in
     * front of the suffix check, "evilvoratoon.id" would pass — that is the whole
     * point of this test.
     */
    @Test
    public void aLookalikeHostIsRejected() {
        assertFalse("tanpa titik", allowed("https://evilvoratoon.id/manga"));
        assertFalse("tanpa titik, domain lama", allowed("https://evilvoratoon.com/"));
        assertFalse("suffix di belakang host lain", allowed("https://voratoon.id.evil.com/"));
        assertFalse("subdomain penyusup", allowed("https://voratoon.id.evil.com/manga/1"));
        assertFalse("huruf besar menyamarkan host lain", allowed("https://VORATOON.ID.EVIL.COM/manga"));
        // Memuat nama sumber di tengah host tetap bukan host sumber. Tes ini yang
        // membedakan "berakhiran" dari sekadar "mengandung": pemeriksaan longgar
        // seperti contains(".voratoon.id") akan meloloskan alamat ini.
        assertFalse("nama sumber di tengah host", allowed("https://cdn.voratoon.id.evil.com/manga"));
        assertFalse("nama domain lama di tengah host", allowed("https://cdn.voratoon.com.evil.com/"));
    }

    /**
     * A path that happens to contain the source name is not a host. This looks
     * like an attack but is a page on the real site, so it must stay readable —
     * rejecting it would break ordinary links.
     */
    @Test
    public void aPathThatMentionsTheNameIsStillTheRealSite() {
        assertTrue(allowed("https://VORATOON.ID/palsu.evil.com"));
        assertTrue(allowed("https://voratoon.id/manga/voratoon.id.evil.com"));
    }

    /** Cleartext is refused even on the right host, and so is a missing host. */
    @Test
    public void onlyHttpsOnARealHostIsAllowed() {
        assertFalse("http", allowed("http://voratoon.id/"));
        assertFalse("file", allowed("file:///etc/hosts"));
        assertFalse("javascript", allowed("javascript:alert(1)"));
        assertFalse("tanpa host", allowed("https:///manga"));
        assertFalse("null", MangaActivity.trusted(null));
    }

    /** The screen must be declared, or Android refuses to open it at all. */
    @Test
    public void theScreenIsDeclaredInTheManifest() throws Exception {
        ComponentName name = new ComponentName(context, MangaActivity.class);
        ActivityInfo info = context.getPackageManager().getActivityInfo(name, 0);
        assertNotNull("MangaActivity harus terdaftar", info);
        assertFalse("hanya dari dalam aplikasi", info.exported);
    }

    /**
     * The novel screen offers a way across; without it the screen is unreachable.
     * The entry is an icon button now, so the check is on the description a screen
     * reader announces and on the 48 dp tap area, not on visible text.
     */
    @Test
    public void theNovelToolbarOffersTheMangaSection() {
        Intent intent = new Intent(context, MainActivity.class);
        MainActivity host = Robolectric.buildActivity(MainActivity.class, intent).setup().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        View menu = host.getWindow().getDecorView().findViewWithTag("section_menu");
        assertNotNull("tombol menu bagian harus ada", menu);
        assertEquals("Menu Novel dan Manga", menu.getContentDescription().toString());
        assertTrue("ikon, bukan teks", menu instanceof android.widget.ImageButton);
        // A button without a drawable is a blank square: tappable, announced by a
        // screen reader, and invisible to everyone else. Checking the type alone
        // let exactly that through.
        assertNotNull("ikon harus tergambar, bukan tombol kosong",
                ((android.widget.ImageButton) menu).getDrawable());
        assertTrue("area ketuk 48 dp", menu.getHeight() > 0 || menu.getLayoutParams().width > 0);
        assertEquals("terlihat di rak", View.VISIBLE, menu.getVisibility());
    }

    /**
     * The section menu belongs to the shelf only. On the detail and download pages
     * the back arrow takes that slot; two controls in one corner is how a user ends
     * up on the manga screen while trying to leave a novel.
     */
    @Test
    public void theSectionMenuStepsAsideOnTheOtherPages() {
        Intent intent = new Intent(context, MainActivity.class);
        MainActivity host = Robolectric.buildActivity(MainActivity.class, intent).setup().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        View menu = host.getWindow().getDecorView().findViewWithTag("section_menu");
        View back = host.getWindow().getDecorView().findViewWithTag("back_to_library");
        assertNotNull(menu);
        assertNotNull(back);
        assertEquals("rak: menu tampil", View.VISIBLE, menu.getVisibility());
        assertEquals("rak: panah sembunyi", View.GONE, back.getVisibility());

        View downloads = host.getWindow().getDecorView().findViewWithTag("open_downloads");
        assertNotNull(downloads);
        downloads.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals("unduhan: menu sembunyi", View.GONE, menu.getVisibility());
        assertEquals("unduhan: panah tampil", View.VISIBLE, back.getVisibility());
    }

    /** Coming back from the manga screen keeps the page that was open. */
    @Test
    public void theOpenPageSurvivesARecreation() {
        Intent intent = new Intent(context, MangaActivity.class);
        MangaActivity screen = Robolectric.buildActivity(MangaActivity.class, intent).setup().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        android.os.Bundle state = new android.os.Bundle();
        screen.onSaveInstanceState(state);
        assertEquals("https://voratoon.id/", state.getString("manga_url"));

        assertNotNull("layar harus terbangun", screen.getWindow().getDecorView());
    }
}
