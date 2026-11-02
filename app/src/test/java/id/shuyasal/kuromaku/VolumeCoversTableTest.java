package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import id.shuyasal.kuromaku.data.Novel;
import id.shuyasal.kuromaku.data.Novels;
import id.shuyasal.kuromaku.data.VolumeCovers;
import id.shuyasal.kuromaku.parser.VolumeCoverParser;

/**
 * The cover table shipped in the app must equal what the source pages say.
 *
 * <p>{@link VolumeCovers} is a hard-coded table because the app cannot download
 * two landing pages before showing the library. That table is the one thing that
 * could silently drift — the classic failure being every volume showing Volume
 * 1's artwork. This test re-parses the saved source pages and compares every
 * volume against the table, so a wrong or duplicated cover fails the build.
 */
public class VolumeCoversTableTest {

    private static String fixture(String name) throws Exception {
        try (InputStream in = VolumeCoversTableTest.class
                .getClassLoader().getResourceAsStream("catalog/" + name)) {
            assertNotNull("fixture tidak ditemukan: " + name, in);
            try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
                scanner.useDelimiter("\\A");
                return scanner.hasNext() ? scanner.next() : "";
            }
        }
    }

    /** Normalised so a size segment rewrite does not count as a difference. */
    private static String normalise(String url) {
        if (url == null) return "";
        String value = url.trim();
        // Blogger: compare the image identity, not the requested width.
        // Blogger sizes are not limited to four digits: the source really does
        // serve some plates as "=s16000", so the pattern allows five.
        value = value.replaceAll("/s\\d{2,5}(-[a-z0-9-]+)?/", "/SIZE/");
        value = value.replaceAll("=s\\d{2,5}(-[a-z0-9-]+)?$", "=SIZE");
        value = value.replaceAll("=w\\d{2,5}-h\\d{2,5}(-[a-z0-9-]+)?$", "=SIZE");
        value = value.replaceAll("/w\\d{2,5}-h\\d{2,5}(-[a-z0-9-]+)?/", "/SIZE/");
        return value;
    }

    /** Every volume in the Magical★Explorer table matches its own source picture. */
    @Test
    public void magicalTableMatchesTheSourcePage() throws Exception {
        Map<Integer, VolumeCoverParser.Found> parsed =
                VolumeCoverParser.parse(fixture("magical_landing.html"));
        assertFalse(parsed.isEmpty());

        for (Map.Entry<Integer, VolumeCoverParser.Found> entry : parsed.entrySet()) {
            int volume = entry.getKey();
            VolumeCovers.Cover table = VolumeCovers.forVolume(Novels.MAGICAL, volume);
            assertNotNull("tabel tidak punya cover volume " + volume, table);
            assertEquals("cover volume " + volume + " harus sama dengan sumber",
                    normalise(entry.getValue().url), normalise(table.url));
        }
    }

    /** Same check for Monogatari, volumes 1-7. */
    @Test
    public void monogatariTableMatchesTheSourcePage() throws Exception {
        Map<Integer, VolumeCoverParser.Found> parsed =
                VolumeCoverParser.parse(fixture("monogatari_landing.html"));
        assertFalse(parsed.isEmpty());

        for (Map.Entry<Integer, VolumeCoverParser.Found> entry : parsed.entrySet()) {
            int volume = entry.getKey();
            VolumeCovers.Cover table = VolumeCovers.forVolume(Novels.MONOGATARI, volume);
            assertNotNull("tabel tidak punya cover volume " + volume, table);
            assertEquals("cover volume " + volume + " harus sama dengan sumber",
                    normalise(entry.getValue().url), normalise(table.url));
        }
    }

    /** No volume may reuse another volume's artwork. */
    @Test
    public void noCoverIsReusedAcrossVolumes() {
        for (Novel novel : Novels.all()) {
            Set<String> seen = new HashSet<>();
            for (int volume = 1; volume <= 20; volume++) {
                VolumeCovers.Cover cover = VolumeCovers.forVolume(novel.id, volume);
                if (cover == null) continue;
                assertTrue("cover " + novel.id + " volume " + volume + " dipakai dua kali",
                        seen.add(normalise(cover.url)));
            }
        }
    }

    /** Every stored cover has a plausible printed-book ratio. */
    @Test
    public void everyCoverHasAPlausibleBookRatio() {
        for (Novel novel : Novels.all()) {
            for (int volume = 1; volume <= 20; volume++) {
                VolumeCovers.Cover cover = VolumeCovers.forVolume(novel.id, volume);
                if (cover == null) continue;
                assertTrue("cover " + novel.id + " volume " + volume + " tanpa ukuran",
                        cover.width > 0 && cover.height > 0);
                float ratio = cover.ratio();
                assertTrue("rasio cover " + novel.id + " v" + volume + " aneh: " + ratio,
                        ratio > 0.55f && ratio < 0.85f);
            }
        }
    }
}
