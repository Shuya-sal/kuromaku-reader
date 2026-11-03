package id.shuyasal.kuromaku.offline;

import java.util.Locale;

/**
 * Decides whether downloaded bytes really are a usable illustration.
 *
 * <p>A failed or truncated download is the normal case on a flaky connection,
 * and Blogger happily serves an HTML error page with status 200. Trusting the
 * content type is not enough: the first bytes of the file are checked instead,
 * so a chapter can never be marked as saved while one of its pictures is
 * actually a web page or half a JPEG.
 */
public final class ImageValidator {

    /** Below this a picture cannot be a real illustration, only a fragment. */
    static final int MIN_BYTES = 512;

    private ImageValidator() {
    }

    /**
     * Sniffs the real format from the file header.
     *
     * @return a file extension such as {@code jpg}, {@code png}, {@code gif} or
     *         {@code webp}, or {@code null} when the bytes are not a picture.
     */
    public static String extensionOf(byte[] data) {
        if (data == null || data.length < MIN_BYTES) return null;

        // JPEG: FF D8 FF
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G'
                && (data[4] & 0xFF) == 0x0D && (data[5] & 0xFF) == 0x0A) {
            return "png";
        }
        // GIF: "GIF87a" / "GIF89a"
        if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8') {
            return "gif";
        }
        // WEBP: "RIFF" .... "WEBP"
        if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data.length > 12
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "webp";
        }
        // BMP: "BM"
        if (data[0] == 'B' && data[1] == 'M') {
            return "bmp";
        }
        return null;
    }

    public static boolean isImage(byte[] data) {
        return extensionOf(data) != null;
    }

    /** Human readable reason for a rejected download, used in status messages. */
    public static String rejectionReason(byte[] data, String url) {
        if (data == null || data.length == 0) {
            return "gambar kosong: " + shorten(url);
        }
        if (data.length < MIN_BYTES) {
            return "gambar terlalu kecil (" + data.length + " byte): " + shorten(url);
        }
        String head = new String(data, 0, Math.min(48, data.length), java.nio.charset.StandardCharsets.ISO_8859_1)
                .toLowerCase(Locale.ROOT);
        if (head.contains("<html") || head.contains("<!doctype") || head.contains("<body")) {
            return "server mengirim halaman HTML, bukan gambar: " + shorten(url);
        }
        return "format gambar tidak dikenali: " + shorten(url);
    }

    private static String shorten(String url) {
        if (url == null) return "";
        return url.length() <= 60 ? url : url.substring(0, 57) + "…";
    }
}
