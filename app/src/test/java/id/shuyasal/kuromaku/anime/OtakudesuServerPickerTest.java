package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Pemilih server streaming diuji terhadap halaman episode asli sumber
 * (FX Senshi Kurumi-chan Episode 1) dan terhadap pemutar-pemutar yang bentuknya
 * sudah diperiksa dengan permintaan sungguhan.
 *
 * <p>Yang dijaga tes ini: daftar server selalu berasal dari halaman episode,
 * status tidak pernah disimpulkan dari nama provider, dan alamat berkas yang
 * bentuknya rusak tidak pernah dilaporkan siap diputar.
 */
public class OtakudesuServerPickerTest {

    static String fixture(String name) {
        try (InputStream in = OtakudesuServerPickerTest.class
                .getResourceAsStream("/otakudesu/" + name)) {
            if (in == null) throw new IllegalStateException("Fixture hilang: " + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private static Document episode() {
        return Jsoup.parse(fixture("episode_mirrors.html"));
    }

    /** Http palsu: peta alamat → jawaban; alamat lain dianggap gagal. */
    private static OtakudesuServerPicker.Http http(java.util.Map<String, String> answers,
                                                    List<String> posted) {
        return new OtakudesuServerPicker.Http() {
            @Override public String get(String url) {
                String answer = answers.get(url);
                if (answer == null) throw new IllegalStateException("tidak diminta: " + url);
                return answer;
            }

            @Override public String post(String url, List<String[]> fields) {
                StringBuilder key = new StringBuilder("POST ");
                for (String[] field : fields) {
                    key.append(field[0]).append('=').append(field[1]).append('&');
                }
                posted.add(key.toString());
                String answer = answers.get(key.toString());
                if (answer == null) throw new IllegalStateException("tidak diminta: " + key);
                return answer;
            }
        };
    }

    // -------------------------------------------------------- daftar server

    @Test public void daftarServerSelaluDariHalamanEpisode() {
        List<OtakudesuPlayback.Mirror> mirrors = OtakudesuServerPicker.parseMirrors(episode());

        assertEquals("halaman episode punya dua belas mirror", 12, mirrors.size());
        // Tidak ada satu nama pun yang ditulis tetap di kode; semuanya dari halaman.
        String[] expected = {"odcdn", "odstream", "ondesuhd", "filedon", "vidhide", "mega"};
        for (OtakudesuPlayback.Mirror mirror : mirrors) {
            boolean known = false;
            for (String name : expected) {
                if (name.equalsIgnoreCase(mirror.provider)) known = true;
            }
            assertTrue("provider asing: " + mirror.provider, known);
        }
    }

    @Test public void mirrorDikelompokkanPerResolusiDariTertinggi() {
        List<OtakudesuPlayback.Mirror> mirrors = OtakudesuServerPicker.parseMirrors(episode());
        LinkedHashMap<String, List<OtakudesuPlayback.Mirror>> groups =
                OtakudesuServerPicker.grouped(mirrors);

        List<String> order = new ArrayList<>(groups.keySet());
        assertEquals("720p", order.get(0));
        assertEquals("480p", order.get(1));
        assertEquals("360p", order.get(2));
        assertEquals(5, groups.get("720p").size());
        assertEquals(4, groups.get("480p").size());
        assertEquals(3, groups.get("360p").size());
    }

    @Test public void indeksServerDipakaiSaatMemilih() {
        List<OtakudesuPlayback.Mirror> mirrors = OtakudesuServerPicker.parseMirrors(episode());
        for (OtakudesuPlayback.Mirror mirror : mirrors) {
            assertTrue("id server harus angka: " + mirror.serverId,
                    mirror.serverId.matches("\\d+"));
            assertTrue("kualitas harus terisi: " + mirror.quality, !mirror.quality.isEmpty());
        }
        // Urutan daftar mengikuti halaman: 360p lebih dulu di HTML, sedangkan
        // tampilan panel diurutkan dari kualitas tertinggi.
        OtakudesuPlayback.Mirror first = mirrors.get(0);
        assertEquals("360p", first.quality);
        assertEquals(0, first.index);

        LinkedHashMap<String, List<OtakudesuPlayback.Mirror>> groups =
                OtakudesuServerPicker.grouped(mirrors);
        assertEquals("720p", new ArrayList<>(groups.keySet()).get(0));
    }

    @Test public void urutanPemeriksaanMendahulukanServerTerbuka() {
        List<OtakudesuPlayback.Mirror> mirrors = OtakudesuServerPicker.parseMirrors(episode());
        List<OtakudesuPlayback.Mirror> ordered = OtakudesuServerPicker.probeOrder(mirrors);

        assertEquals(mirrors.size(), ordered.size());
        int firstGated = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (OtakudesuMediaResolver.isAdGated(ordered.get(i).provider)) {
                firstGated = i;
                break;
            }
        }
        assertTrue("harus ada server beriklan di daftar", firstGated > 0);
        for (int i = firstGated; i < ordered.size(); i++) {
            assertTrue("server beriklan harus di belakang",
                    OtakudesuMediaResolver.isAdGated(ordered.get(i).provider));
        }
    }

    // ------------------------------------------------------- aksi halaman

    /**
     * Dua aksi di halaman episode berbeda tugas. Tertukar berarti sumber
     * menjawab HTTP 403 — inilah kesalahan yang membuat pemutar tidak pernah
     * mendapat berkas.
     */
    @Test public void aksiMirrorDanAksiNonceTidakTertukar() {
        Document doc = episode();
        assertEquals("2a3505c93b0035d3f455df82bf976b84", OtakudesuServerPicker.action(doc));
        assertEquals("aa1208d27f29ca340c92c66d1926f13f", OtakudesuServerPicker.nonceAction(doc));
    }

    @Test public void aksiNonceAdalahYangTidakDikirimBersamaNonce() {
        Document doc = episode();
        String mirror = OtakudesuServerPicker.action(doc);
        String nonce = OtakudesuServerPicker.nonceAction(doc);
        assertFalse(mirror.equals(nonce));

        // Bentuk pemakaian di halaman: aksi mirror selalu berdampingan dengan nonce:.
        String html = doc.html();
        int pair = html.indexOf("nonce:window.__x__nonce,action:\"" + mirror + "\"");
        assertTrue("aksi mirror harus muncul bersama nonce di skrip halaman", pair > 0);
    }

    @Test public void nonceNullDiHalamanTidakDipakai() {
        Document doc = Jsoup.parse("<html><script>window.__x__nonce=null,"
                + "$('.mirrorstream a').on('click',function(a){"
                + "data:{...e,nonce:window.__x__nonce,action:\"2a3505c93b0035d3f455df82bf976b84\"}}"
                + ":</script></html>");
        assertEquals("literal null tidak boleh jadi nonce",
                "", OtakudesuServerPicker.nonceIn(doc));
        assertEquals("2a3505c93b0035d3f455df82bf976b84", OtakudesuServerPicker.action(doc));
    }

    @Test public void halamanTanpaAksiTidakMenghasilkanPenebakan() {
        Document doc = Jsoup.parse("<html><body>tidak ada skrip</body></html>");
        assertEquals("", OtakudesuServerPicker.action(doc));
        assertEquals("", OtakudesuServerPicker.nonceAction(doc));
    }

    // ---------------------------------------------------------- pemeriksaan

    @Test public void serverTerbukaDilaporkanSiapDiputar() {
        Document doc = episode();
        OtakudesuPlayback.Mirror mirror = mirrorNamed(doc, "odcdn");
        List<String> posted = new ArrayList<>();
        java.util.Map<String, String> answers = new java.util.HashMap<>();
        answers.put("POST action=aa1208d27f29ca340c92c66d1926f13f&", "{\"data\":\"abc123\"}");
        answers.put("POST id=" + mirror.serverId + "&i=" + mirror.index + "&q=" + mirror.quality
                + "&nonce=abc123&action=2a3505c93b0035d3f455df82bf976b84&",
                "{\"data\":\"" + base64("<iframe src=\"https://desustream.net/dstream/arcg/?id=x\"></iframe>") + "\"}");
        answers.put("https://desustream.net/dstream/arcg/?id=x",
                "<html><script>const videoURL = "
                        + "\"https://cdn.odcloud.net/anime/contoh_720p.mp4\";</script></html>");

        OtakudesuServerPicker.Probed probed =
                OtakudesuServerPicker.probe(mirror, http(answers, posted), doc);

        assertEquals(OtakudesuServerPicker.Status.READY, probed.status);
        assertTrue(probed.playable());
        assertEquals("https://cdn.odcloud.net/anime/contoh_720p.mp4", probed.source.url);
    }

    @Test public void pemutarTerenkripsiDilaporkanTerlindungi() {
        Document doc = episode();
        OtakudesuPlayback.Mirror mirror = mirrorNamed(doc, "vidhide");
        java.util.Map<String, String> answers = new java.util.HashMap<>();
        answers.put("POST action=aa1208d27f29ca340c92c66d1926f13f&", "{\"data\":\"abc123\"}");
        answers.put("POST id=" + mirror.serverId + "&i=" + mirror.index + "&q=" + mirror.quality
                        + "&nonce=abc123&action=2a3505c93b0035d3f455df82bf976b84&",
                "{\"data\":\"" + base64("<iframe src=\"https://odvidhide.com/embed/xyz\"></iframe>") + "\"}");
        answers.put("https://odvidhide.com/embed/xyz",
                "<html><script>var a=\"pkcs7\",b=\"RSAKey\",c=\"decrypt(\";</script></html>");

        OtakudesuServerPicker.Probed probed =
                OtakudesuServerPicker.probe(mirror, http(answers, new ArrayList<>()), doc);

        assertEquals(OtakudesuServerPicker.Status.PROTECTED_SERVER, probed.status);
        assertFalse("server terenkripsi tidak boleh dianggap bisa diputar", probed.playable());
    }

    @Test public void alamatYangBaruTerbentukSaatPemutarJalanTidakDipakai() {
        assertTrue(OtakudesuServerPicker.looksLateBound(
                "<video><source src=\"https://rr5---sn-x.googlevideo.com/videoplayback?ip=1&expire=2\">"));
        assertFalse(OtakudesuServerPicker.looksLateBound(
                "<script>const videoURL=\"https://cdn.odcloud.net/anime/a_720p.mp4\";</script>"));
    }

    @Test public void kegagalanJaringanDilaporkanTidakTersedia() {
        Document doc = episode();
        OtakudesuPlayback.Mirror mirror = mirrorNamed(doc, "odcdn");
        OtakudesuServerPicker.Http broken = new OtakudesuServerPicker.Http() {
            @Override public String get(String url) {
                throw new IllegalStateException("mati");
            }

            @Override public String post(String url, List<String[]> fields) {
                throw new IllegalStateException("mati");
            }
        };
        OtakudesuServerPicker.Probed probed =
                OtakudesuServerPicker.probe(mirror, broken, doc);
        assertEquals(OtakudesuServerPicker.Status.UNAVAILABLE, probed.status);
        assertFalse(probed.playable());
    }

    /**
     * Alamat pemutar ikut dilaporkan bersama hasil pemeriksaan.
     *
     * <p>Ini yang membuat tombol "Buka Player Sumber" punya tujuan ketika
     * berkasnya tidak bisa dibaca Media3: pengguna tetap bisa membuka pemutar
     * penyedia, dengan catatan tampilannya mengikuti penyedia.
     */
    @Test public void hasilPemeriksaanMembawaAlamatPemutar() {
        Document doc = episode();
        OtakudesuPlayback.Mirror mirror = mirrorNamed(doc, "vidhide");
        java.util.Map<String, String> answers = new java.util.HashMap<>();
        answers.put("POST action=aa1208d27f29ca340c92c66d1926f13f&", "{\"data\":\"abc123\"}");
        answers.put("POST id=" + mirror.serverId + "&i=" + mirror.index + "&q=" + mirror.quality
                        + "&nonce=abc123&action=2a3505c93b0035d3f455df82bf976b84&",
                "{\"data\":\"" + base64("<iframe src=\"https://odvidhide.com/embed/xyz\"></iframe>") + "\"}");
        answers.put("https://odvidhide.com/embed/xyz",
                "<html><script>var a=\"pkcs7\";</script></html>");

        OtakudesuServerPicker.Probed probed =
                OtakudesuServerPicker.probe(mirror, http(answers, new ArrayList<>()), doc);

        assertEquals(OtakudesuServerPicker.Status.PROTECTED_SERVER, probed.status);
        assertEquals("https://odvidhide.com/embed/xyz", probed.playerUrl);
    }

    @Test public void berkasLangsungTetapMembawaAlamatPemutarnya() {
        Document doc = episode();
        OtakudesuPlayback.Mirror mirror = mirrorNamed(doc, "odcdn");
        java.util.Map<String, String> answers = new java.util.HashMap<>();
        answers.put("POST action=aa1208d27f29ca340c92c66d1926f13f&", "{\"data\":\"abc123\"}");
        String player = "https://desustream.net/dstream/odcdn/?id=abc";
        answers.put("POST id=" + mirror.serverId + "&i=" + mirror.index + "&q=" + mirror.quality
                        + "&nonce=abc123&action=2a3505c93b0035d3f455df82bf976b84&",
                "{\"data\":\"" + base64("<iframe src=\"" + player + "\"></iframe>") + "\"}");
        answers.put(player, "<html><script>const videoURL = "
                + "\"https://cdn.odcloud.net/anime/contoh_480p.mp4\";</script></html>");

        OtakudesuServerPicker.Probed probed =
                OtakudesuServerPicker.probe(mirror, http(answers, new ArrayList<>()), doc);

        assertEquals(OtakudesuServerPicker.Status.READY, probed.status);
        assertEquals(player, probed.playerUrl);
    }

    /** Mirror bisa menyimpan alamat pemutar untuk tombol "Buka Player Sumber". */
    @Test public void mirrorMenyimpanAlamatPemutar() {
        OtakudesuPlayback.Mirror mirror = new OtakudesuPlayback.Mirror("480p", "odcdn", "207282", 0);
        assertEquals("", mirror.playerUrl);
        OtakudesuPlayback.Mirror withPlayer = mirror.withPlayer("https://desustream.net/x");
        assertEquals("https://desustream.net/x", withPlayer.playerUrl);
        assertEquals("odcdn", withPlayer.provider);
        assertEquals(0, withPlayer.index);
    }

    // ------------------------------------------------------------- alamat

    @Test public void alamatTersusunGandaDitolak() {
        assertTrue(OtakudesuServerPicker.looksDoubled(
                "https://archive.org/download/https://archive.org/download/x/a_720p.mp4.mp4"));
        assertFalse(OtakudesuServerPicker.looksDoubled(
                "https://cdn.odcloud.net/anime/Otakudesu.io_FSKC--01_720p.mp4"));
    }

    @Test public void hostRusakDitolak() {
        assertTrue(OtakudesuServerPicker.looksBroken("https:///a.mp4"));
        assertTrue(OtakudesuServerPicker.looksBroken("http://x.test/a.mp4"));
        assertTrue(OtakudesuServerPicker.looksBroken("https://archive.org/download/"));
        // Tanda hubung di host itu wajar (mis. rr5---sn-x.googlevideo.com).
        assertFalse(OtakudesuServerPicker.looksBroken("https://rr5---sn-x.googlevideo.com/a.mp4"));
        assertFalse(OtakudesuServerPicker.looksBroken("https://cdn.odcloud.net/a.mp4"));
    }

    @Test public void alamatRusakTidakPernahDilaporkanSiapDiputar() {
        String html = "<html><script>const videoURL = "
                + "\"https://archive.org/download/https://archive.org/download/x/a_720p.mp4.mp4\";"
                + "</script></html>";
        assertEquals("alamat tersusun ganda tidak dipakai",
                null, OtakudesuServerPicker.firstSource(html));
    }

    @Test public void jawabanBase64DanJsonDibaca() {
        String iframe = "<iframe src=\"https://desustream.net/dstream/odcdn/?id=abc\"></iframe>";
        assertEquals("https://desustream.net/dstream/odcdn/?id=abc",
                OtakudesuServerPicker.playerUrl("{\"data\":\"" + base64(iframe) + "\"}"));
        assertEquals("", OtakudesuServerPicker.playerUrl("{\"data\":\"\"}"));
        assertEquals("", OtakudesuServerPicker.playerUrl(null));
    }

    @Test public void jawabanBukanJsonTetapDibaca() {
        assertEquals("https://desustream.net/dstream/odcdn/?id=abc",
                OtakudesuServerPicker.playerUrl(
                        "<html><iframe src=\"https://desustream.net/dstream/odcdn/?id=abc\">"));
    }

    // --------------------------------------------------------------- panel

    @Test public void pilihanPanelMenyebutStatusTerpisahDariNama() {
        List<OtakudesuPlayback.Mirror> mirrors = OtakudesuServerPicker.parseMirrors(episode());
        LinkedHashMap<Integer, OtakudesuPlayback.Source> probed = new LinkedHashMap<>();
        probed.put(0, new OtakudesuPlayback.Source(
                "https://cdn.odcloud.net/a_720p.mp4", OtakudesuPlayback.Kind.PROGRESSIVE, "720p"));

        List<OtakudesuPlayback.Choice> choices =
                OtakudesuServerPanel.choices(mirrors, probed);

        assertEquals(mirrors.size(), choices.size());
        assertTrue(choices.get(0).playable());
        assertEquals(OtakudesuServerPicker.Status.READY, choices.get(0).status);
        assertFalse("yang belum diperiksa tidak dianggap bisa diputar", choices.get(1).playable());
        assertEquals(OtakudesuServerPicker.Status.UNTESTED, choices.get(1).status);
    }

    @Test public void ringkasanTombolMenempelNamaDanKualitas() {
        List<OtakudesuPlayback.Mirror> mirrors = OtakudesuServerPicker.parseMirrors(episode());
        OtakudesuPlayback.Mirror odcdn = null;
        for (OtakudesuPlayback.Mirror mirror : mirrors) {
            if ("odcdn".equalsIgnoreCase(mirror.provider)) odcdn = mirror;
        }
        assertEquals("odcdn 480p", OtakudesuServerPanel.summary(odcdn));
        assertEquals("Server", OtakudesuServerPanel.summary(null));
    }

    /** Mirror yang providernya disebut tes; tidak menebak posisi di daftar. */
    private static OtakudesuPlayback.Mirror mirrorNamed(Document doc, String provider) {
        for (OtakudesuPlayback.Mirror mirror : OtakudesuServerPicker.parseMirrors(doc)) {
            if (provider.equalsIgnoreCase(mirror.provider)) return mirror;
        }
        throw new IllegalStateException("provider tidak ada di halaman: " + provider);
    }

    private static String base64(String value) {
        return java.util.Base64.getEncoder().encodeToString(
                value.getBytes(StandardCharsets.UTF_8));
    }
}
