package id.shuyasal.kuromaku.shinigami;

import org.junit.Test;
import static org.junit.Assert.*;

public class ShinigamiImageRequestTest {
    @Test public void rejectsHtmlDespiteSuccessfulHttp() throws Exception {
        try {
            ShinigamiImageRequest.validate(200, "text/html", "<html>challenge</html>".getBytes());
            fail("HTML bukan gambar");
        } catch (java.io.IOException error) { assertTrue(error.getMessage().contains("bukan gambar")); }
    }
    @Test public void reportsAccessDeniedWithoutBodyOrToken() throws Exception {
        try {
            ShinigamiImageRequest.validate(403, "text/html", "secret-cookie".getBytes());
            fail("403 harus ditolak");
        } catch (java.io.IOException error) {
            assertTrue(error.getMessage().contains("403"));
            assertFalse(error.getMessage().contains("secret"));
        }
    }
    @Test public void acceptsActualWebpSignature() throws Exception {
        ShinigamiImageRequest.validate(200, "image/webp", "RIFF1234WEBPVP8 ".getBytes());
    }
    @Test public void rejectsInvalidSignatureEvenWithImageMime() throws Exception {
        try {
            ShinigamiImageRequest.validate(200, "image/png", "<html>not png</html>".getBytes());
            fail("signature harus diperiksa");
        } catch (java.io.IOException error) { assertTrue(error.getMessage().contains("format")); }
    }
}
