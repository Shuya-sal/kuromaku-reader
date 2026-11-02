package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.FeedEntry;
import id.shuyasal.kuromaku.data.LibraryBuilder;
import id.shuyasal.kuromaku.net.FeedClient;
import id.shuyasal.kuromaku.net.FeedException;

/**
 * Regression tests for BUG 1.
 *
 * <p>Blogger never guarantees the requested page size: the label feed answers
 * {@code max-results=25} with pages of 25, 22, 25, 20, 12 and 13 items. The 2.0
 * client stopped as soon as a page came back shorter than requested, so the
 * crawl ended after 47 posts and the library appeared to start at Volume 5.
 */
public class FeedPaginationTest {

    private static final Pattern START_INDEX = Pattern.compile("start-index=(\\d+)");

    private static final String[] TEMPLATE = {
            "Prolog", "Chapter 1", "Chapter 2", "Chapter 10", "Epilog", "Illustrasi"};

    // ---------------------------------------------------------------- helpers

    private static String page(List<FeedEntry> entries, int total, boolean includeTotal) {
        try {
            JSONObject feed = new JSONObject();
            if (includeTotal) {
                JSONObject totalObject = new JSONObject();
                totalObject.put("$t", String.valueOf(total));
                feed.put("openSearch$totalResults", totalObject);
            }
            JSONArray items = new JSONArray();
            for (FeedEntry entry : entries) {
                JSONObject item = new JSONObject();
                JSONObject title = new JSONObject();
                title.put("$t", entry.title);
                item.put("title", title);
                JSONObject published = new JSONObject();
                published.put("$t", entry.published + "T08:00:00.000+07:00");
                item.put("published", published);
                JSONArray links = new JSONArray();
                JSONObject alternate = new JSONObject();
                alternate.put("rel", "alternate");
                alternate.put("href", entry.url);
                links.put(alternate);
                item.put("link", links);
                items.put(item);
            }
            feed.put("entry", items);
            JSONObject root = new JSONObject();
            root.put("feed", feed);
            return root.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    /** Serves pages of exactly the given sizes, keyed by start-index. */
    private static final class PagedBlog implements FeedClient.Fetcher {
        private final List<FeedEntry> all;
        private final List<int[]> ranges = new ArrayList<>();
        final List<Integer> requestedStarts = new ArrayList<>();

        PagedBlog(List<FeedEntry> all, int... pageSizes) {
            this.all = all;
            int start = 1;
            int from = 0;
            for (int size : pageSizes) {
                ranges.add(new int[]{start, from, size});
                start += size;
                from += size;
            }
        }

        @Override
        public String get(String url) throws IOException {
            Matcher matcher = START_INDEX.matcher(url);
            if (!matcher.find()) throw new IOException("no start-index in " + url);
            int start = Integer.parseInt(matcher.group(1));
            requestedStarts.add(start);

            for (int[] range : ranges) {
                if (range[0] != start) continue;
                if (range[1] >= all.size()) {
                    return page(new ArrayList<FeedEntry>(), all.size(), true);
                }
                int to = Math.min(range[1] + range[2], all.size());
                return page(all.subList(range[1], to), all.size(), true);
            }
            return page(new ArrayList<FeedEntry>(), all.size(), true);
        }
    }

    /** Seven volumes, six named parts each, newest volume first like Blogger. */
    private static List<FeedEntry> templateCorpus() {
        List<FeedEntry> entries = new ArrayList<>();
        for (int volume = 7; volume >= 1; volume--) {
            for (String part : TEMPLATE) {
                String slug = part.toLowerCase().replace(' ', '-');
                entries.add(new FeedEntry(
                        "Monogatari no Kuromaku Volume " + volume + " " + part,
                        "https://kaoritranslation.blogspot.com/" + volume + "/" + slug,
                        "2026-01-0" + volume));
            }
        }
        return entries;
    }

    /** 117 real-sized posts spread across the seven volumes. */
    private static List<FeedEntry> fullCorpus() {
        int[] perVolume = {14, 18, 17, 17, 16, 18, 17};
        List<FeedEntry> entries = new ArrayList<>();
        for (int volume = 7; volume >= 1; volume--) {
            int count = perVolume[volume - 1];
            for (int index = 1; index <= count; index++) {
                entries.add(new FeedEntry(
                        "Monogatari no Kuromaku Volume " + volume + " Chapter " + index,
                        "https://kaoritranslation.blogspot.com/v" + volume + "/c" + index,
                        "2026-01-01"));
            }
        }
        return entries;
    }

    // ------------------------------------------------------------------ tests

    @Test
    public void shortPageDoesNotEndTheCrawl() throws Exception {
        List<FeedEntry> all = templateCorpus();
        // Page two holds only 10 items although 25 were requested.
        PagedBlog blog = new PagedBlog(all, 25, 10, 7);

        FeedClient.Result result = FeedClient.fetchEntries(blog);

        assertEquals("semua postingan harus terkumpul", all.size(), result.entries.size());
        assertEquals(3, result.pagesRead);
        assertTrue("kursor harus maju sebesar item yang benar-benar diterima",
                blog.requestedStarts.contains(26) && blog.requestedStarts.contains(36));
        assertTrue(result.isComplete());
    }

    @Test
    public void volumesOneToFourSurviveIrregularPagination() throws Exception {
        List<FeedEntry> all = fullCorpus();
        // Exactly the sizes served for this label, summing to 117.
        PagedBlog blog = new PagedBlog(all, new int[]{25, 22, 25, 20, 12, 13});

        FeedClient.Result result = FeedClient.fetchEntries(blog);
        Catalog catalog = LibraryBuilder.build(result.entries);

        assertEquals(117, result.entries.size());

        Set<Integer> volumes = new LinkedHashSet<>();
        for (Chapter chapter : catalog.flat()) volumes.add(chapter.volume);
        for (int volume = 1; volume <= 7; volume++) {
            assertTrue("Volume " + volume + " hilang setelah paginasi", volumes.contains(volume));
        }
        assertEquals(7, catalog.volumes.size());
        assertEquals("volume harus urut menaik", 1, catalog.volumes.get(0).number);
        assertEquals(7, catalog.volumes.get(6).number);
    }

    @Test
    public void oldBehaviourWouldHaveLostVolumes() {
        // Guard against a regression that stops at the first short page: with
        // pages 25 + 22 the crawl would end at 47 posts and start at Volume 5.
        List<FeedEntry> all = fullCorpus();
        PagedBlog blog = new PagedBlog(all, new int[]{25, 22, 25, 20, 12, 13});
        int firstTwoPages = 25 + 22;
        assertTrue("dua halaman pertama tidak boleh menutup seluruh katalog",
                firstTwoPages < all.size());
    }

    @Test
    public void duplicateUrlsAreCollectedOnce() throws Exception {
        List<FeedEntry> all = new ArrayList<>(templateCorpus());
        all.add(5, all.get(2)); // Blogger repeats an entry edited mid-crawl

        PagedBlog blog = new PagedBlog(all, 25, 12, 6);
        FeedClient.Result result = FeedClient.fetchEntries(blog);

        Set<String> urls = new LinkedHashSet<>();
        for (FeedEntry entry : result.entries) urls.add(entry.url);
        assertEquals(urls.size(), result.entries.size());
    }

    @Test
    public void incompleteCrawlReportsAWarning() throws Exception {
        List<FeedEntry> all = fullCorpus();
        PagedBlog blog = new PagedBlog(all, new int[]{25, 22, 25, 20, 0});

        FeedClient.Result result = FeedClient.fetchEntries(blog);

        assertFalse(result.isComplete());
        assertNotNull(result.warning);
        assertTrue(result.warning.contains("dari"));
    }

    @Test
    public void pageFailureNamesThePage() {
        FeedClient.Fetcher broken = url -> {
            throw new IOException("timeout");
        };
        try {
            FeedClient.fetchEntries(broken);
            fail("expected FeedException");
        } catch (Exception error) {
            assertTrue(error instanceof FeedException);
            assertTrue(error.getMessage().contains("start-index"));
            assertEquals(1, ((FeedException) error).page);
        }
    }

    @Test
    public void malformedJsonIsReportedClearly() {
        FeedClient.Fetcher garbage = url -> "<html>bukan json</html>";
        try {
            FeedClient.fetchEntries(garbage);
            fail("expected FeedException");
        } catch (Exception error) {
            assertTrue(error instanceof FeedException);
            assertTrue(error.getMessage().toLowerCase().contains("tidak dapat dibaca"));
        }
    }

    @Test
    public void emptyFeedIsReportedNotSilentlyAccepted() throws Exception {
        FeedClient.Fetcher empty = url -> page(new ArrayList<FeedEntry>(), 0, false);
        FeedClient.Result result = FeedClient.fetchEntries(empty);
        assertEquals(0, result.entries.size());
        assertNotNull(result.warning);
        assertFalse(result.isComplete());
    }

    @Test
    public void totalResultsDrivesEarlyStop() throws Exception {
        List<FeedEntry> three = templateCorpus().subList(0, 3);
        final int[] requests = {0};
        FeedClient.Fetcher fetcher = url -> {
            requests[0]++;
            return page(three, 3, true);
        };

        FeedClient.Result result = FeedClient.fetchEntries(fetcher);

        assertEquals(3, result.entries.size());
        assertEquals(1, requests[0]);
        assertTrue(result.isComplete());
    }

    @Test
    public void everyVolumeKeepsReadingOrderAndLabels() throws Exception {
        PagedBlog blog = new PagedBlog(templateCorpus(), 25, 10, 7);
        Catalog catalog = LibraryBuilder.build(FeedClient.fetchEntries(blog).entries);

        assertEquals(7, catalog.volumes.size());
        for (Chapter chapter : catalog.flat()) {
            String label = chapter.label;
            assertTrue("label tidak boleh kosong untuk " + chapter.title, label.length() > 0);
        }

        List<String> expected = new ArrayList<>();
        for (String part : TEMPLATE) {
            expected.add(part.equals("Illustrasi") ? "Illustrasi" : part);
        }
        for (int index = 0; index < catalog.volumes.size(); index++) {
            List<Chapter> chapters = catalog.volumes.get(index).chapters;
            List<String> labels = new ArrayList<>();
            for (Chapter chapter : chapters) labels.add(chapter.label);
            assertEquals("urutan baca Volume " + catalog.volumes.get(index).number,
                    expected, labels);
        }
    }

    @Test
    public void chaptersSortNumericallyNotAlphabetically() throws Exception {
        List<FeedEntry> entries = new ArrayList<>();
        for (String part : new String[]{"Chapter 10", "Chapter 9", "Chapter 2", "Chapter 11"}) {
            entries.add(new FeedEntry("Monogatari no Kuromaku Volume 3 " + part,
                    "https://kaoritranslation.blogspot.com/x/" + part.replace(' ', '-'),
                    "2026-01-01"));
        }
        Catalog catalog = LibraryBuilder.build(entries);
        List<Chapter> chapters = catalog.volumes.get(0).chapters;

        assertEquals("Chapter 2", chapters.get(0).label);
        assertEquals("Chapter 9", chapters.get(1).label);
        assertEquals("Chapter 10", chapters.get(2).label);
        assertEquals("Chapter 11", chapters.get(3).label);
    }
}
