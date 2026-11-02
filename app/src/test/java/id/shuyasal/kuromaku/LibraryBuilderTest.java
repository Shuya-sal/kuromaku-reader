package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.FeedEntry;
import id.shuyasal.kuromaku.data.LibraryBuilder;
import id.shuyasal.kuromaku.data.Volume;

/** Grouping and ordering rules for the library screen. */
public class LibraryBuilderTest {

    private static FeedEntry entry(String title, String url) {
        return new FeedEntry(title, url, "2026-03-25");
    }

    @Test
    public void groupsEntriesIntoVolumes() {
        List<FeedEntry> entries = Arrays.asList(
                entry("[LN] Monogatari no Kuromaku volume 1 chapter 1", "https://x/1"),
                entry("[LN] Monogatari no Kuromaku volume 1 chapter 2", "https://x/2"),
                entry("Monogatari no Kuromaku Volume 2 Chapter 1", "https://x/3"));

        Catalog catalog = LibraryBuilder.build(entries);
        assertEquals(2, catalog.volumes.size());
        assertEquals(2, catalog.volumes.get(0).chapters.size());
        assertEquals(1, catalog.volumes.get(1).chapters.size());
        assertEquals(2, catalog.volumes.get(1).number);
        assertEquals(3, catalog.size());
    }

    @Test
    public void sortsChaptersNaturallyNotAlphabetically() {
        List<FeedEntry> entries = new ArrayList<>();
        entries.add(entry("Monogatari no Kuromaku Volume 7 Chapter 10", "https://x/10"));
        entries.add(entry("Monogatari no Kuromaku Volume 7 Chapter 2", "https://x/2"));
        entries.add(entry("Monogatari no Kuromaku Volume 7 Prolog", "https://x/pro"));
        entries.add(entry("Monogatari no Kuromaku Volume 7 Epilog", "https://x/epi"));
        entries.add(entry("Monogatari no Kuromaku Volume 7 Afterword", "https://x/af"));

        Catalog catalog = LibraryBuilder.build(entries);
        List<Chapter> chapters = catalog.volumes.get(0).chapters;

        assertEquals("https://x/pro", chapters.get(0).url);
        assertEquals("https://x/2", chapters.get(1).url);
        assertEquals("https://x/10", chapters.get(2).url);
        assertEquals("https://x/epi", chapters.get(3).url);
        assertEquals("https://x/af", chapters.get(4).url);
    }

    @Test
    public void labelsAreShort() {
        List<FeedEntry> entries = Arrays.asList(
                entry("Monogatari no Kuromaku Volume  7 Chapter 5", "https://x/a"),
                entry("Monogatari no Kuromaku Volume 3 Prolog", "https://x/b"),
                entry("[LN] Monogatari no Kuromaku volume 1 Epilog", "https://x/c"));

        Catalog catalog = LibraryBuilder.build(entries);
        String first = catalog.volumes.get(0).chapters.get(0).label;
        assertTrue(first.contains("Epilog"));
        assertTrue(first.length() < 30);
    }

    @Test
    public void navigationLookupWorks() {
        List<FeedEntry> entries = Arrays.asList(
                entry("Monogatari no Kuromaku Volume 1 Chapter 1", "https://x/1"),
                entry("Monogatari no Kuromaku Volume 1 Chapter 2", "https://x/2"),
                entry("Monogatari no Kuromaku Volume 1 Chapter 3", "https://x/3"));

        Catalog catalog = LibraryBuilder.build(entries);
        assertEquals(1, catalog.indexOf("https://x/2"));
        assertEquals("https://x/3", catalog.chapterAt(2).url);
        assertNotNull(catalog.findByUrl("https://x/1"));
        assertEquals(-1, catalog.indexOf("https://x/missing"));
    }

    @Test
    public void volumesTitleIsReadable() {
        Volume volume = new Volume(4, new ArrayList<Chapter>());
        assertEquals("Volume 4", volume.title());
    }
}
