package id.shuyasal.kuromaku.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.net.NovelUrl;

public final class SakuraParser implements NovelSourceParser {

    private static final Pattern VOL_PATTERN = Pattern.compile("(?i)(?:vol(?:ume)?|jilid|book)\\s*[-.]?\\s*(\\d+)");
    private static final Pattern CH_PATTERN = Pattern.compile("(?i)(?:chapter|ch(?:ap)?\\.?|episode|ep\\.?)\\s*[-.]?\\s*(\\d+(?:\\.\\d+)?)");

    @Override
    public NovelUrl.Source source() {
        return NovelUrl.Source.SAKURA;
    }

    @Override
    public boolean canHandle(String normalizedUrl) {
        return NovelUrl.sourceOf(normalizedUrl) == NovelUrl.Source.SAKURA;
    }

    @Override
    public NovelDiscovery.Result parseCatalog(String html, String url) throws NovelDiscovery.Unreadable {
        return parse(html, url);
    }

    public static NovelDiscovery.Result parse(String html, String url) throws NovelDiscovery.Unreadable {
        if (html == null || html.trim().isEmpty()) {
            throw new NovelDiscovery.Unreadable("Halaman katalog Sakura Novel kosong.");
        }

        Document doc;
        try {
            doc = Jsoup.parse(html, url == null ? "" : url);
        } catch (Exception error) {
            throw new NovelDiscovery.Unreadable("HTML Sakura Novel tidak dapat dibaca: " + error.getMessage());
        }

        String title = extractTitle(doc);
        if (title.isEmpty()) {
            throw new NovelDiscovery.Unreadable("Judul novel tidak ditemukan di halaman Sakura Novel.");
        }

        String coverUrl = extractCover(doc);
        String titleJp = extractAlternativeTitle(doc);
        String author = extractAuthor(doc);
        String illustrator = "Tidak tercantum di halaman sumber";
        String genres = extractGenres(doc);
        String status = extractStatus(doc);
        String[] synopsis = extractSynopsis(doc);

        List<NovelDiscovery.Candidate> candidates = new ArrayList<>();
        List<RawChapter> rawChapters = extractChapters(doc, url, candidates);

        if (rawChapters.isEmpty()) {
            throw new NovelDiscovery.Unreadable("Tidak ada chapter yang ditemukan pada halaman katalog Sakura Novel.");
        }

        List<Volume> volumes = groupVolumes(rawChapters);
        int acceptedCount = 0;
        for (Volume v : volumes) acceptedCount += v.chapterCount();

        List<String> warnings = new ArrayList<>();
        return new NovelDiscovery.Result(
                url,
                title,
                titleJp,
                title,
                author,
                illustrator,
                genres,
                status,
                coverUrl,
                0.7f,
                synopsis,
                volumes,
                warnings,
                candidates.size(),
                acceptedCount,
                candidates,
                false
        );
    }

    private static String extractTitle(Document doc) {
        Element h1 = doc.selectFirst("h1.entry-title, h1[itemprop=name], .post-title h1, h1");
        if (h1 != null) {
            String text = clean(h1.text());
            text = text.replaceAll("(?i)\\s*[-|]\\s*Sakura Novel.*$", "").trim();
            if (!text.isEmpty()) return text;
        }
        Element ogTitle = doc.selectFirst("meta[property=og:title]");
        if (ogTitle != null && ogTitle.hasAttr("content")) {
            String text = clean(ogTitle.attr("content"));
            text = text.replaceAll("(?i)\\s*[-|]\\s*Sakura Novel.*$", "").trim();
            if (!text.isEmpty()) return text;
        }
        return "";
    }

    private static String extractCover(Document doc) {
        Element img = doc.selectFirst(".thumb img, .thumbook img, .featured-image img, img[itemprop=image]");
        if (img != null) {
            String src = img.hasAttr("src") ? img.attr("src") : img.attr("data-src");
            if (src != null && !src.trim().isEmpty() && !src.startsWith("data:")) {
                return src.trim();
            }
        }
        Element og = doc.selectFirst("meta[property=og:image]");
        if (og != null && og.hasAttr("content")) {
            return og.attr("content").trim();
        }
        return null;
    }

    private static String extractAuthor(Document doc) {
        Elements spans = doc.select(".spe span, .infox .spe span");
        for (Element span : spans) {
            String text = span.text();
            if (text.toLowerCase(Locale.ROOT).contains("author") || text.toLowerCase(Locale.ROOT).contains("penulis")) {
                int colon = text.indexOf(':');
                if (colon >= 0 && colon + 1 < text.length()) {
                    String val = clean(text.substring(colon + 1));
                    if (!val.isEmpty()) return val;
                }
            }
        }
        return "Tidak tercantum di halaman sumber";
    }

    private static String extractStatus(Document doc) {
        Elements spans = doc.select(".spe span, .infox .spe span");
        for (Element span : spans) {
            String text = span.text();
            if (text.toLowerCase(Locale.ROOT).contains("status")) {
                int colon = text.indexOf(':');
                if (colon >= 0 && colon + 1 < text.length()) {
                    String val = clean(text.substring(colon + 1));
                    if (!val.isEmpty()) return val;
                }
            }
        }
        return "Ongoing";
    }

    private static String extractGenres(Document doc) {
        Elements genreLinks = doc.select(".spe a[href*='/genre/'], .genredata a, .sertag a");
        if (!genreLinks.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Element g : genreLinks) {
                String text = clean(g.text());
                if (!text.isEmpty()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(text);
                }
            }
            if (sb.length() > 0) return sb.toString();
        }
        return "";
    }

    private static String extractAlternativeTitle(Document doc) {
        Elements spans = doc.select(".spe span, .infox .spe span");
        for (Element span : spans) {
            String text = span.text();
            if (text.toLowerCase(Locale.ROOT).contains("alternative") || text.toLowerCase(Locale.ROOT).contains("judul lain")) {
                int colon = text.indexOf(':');
                if (colon >= 0 && colon + 1 < text.length()) {
                    String val = clean(text.substring(colon + 1));
                    if (!val.isEmpty()) return val;
                }
            }
        }
        return "";
    }

    private static String[] extractSynopsis(Document doc) {
        List<String> list = new ArrayList<>();
        Elements ps = doc.select(".entry-content[itemprop=description] p, .entry-content-single p, .synp .entry-content p");
        for (Element p : ps) {
            String text = clean(p.text());
            if (!text.isEmpty()) list.add(text);
        }
        if (list.isEmpty()) {
            ps = doc.select(".entry-content p");
            for (Element p : ps) {
                String text = clean(p.text());
                if (text.length() > 20 && !text.toLowerCase(Locale.ROOT).contains("chapter")) {
                    list.add(text);
                }
            }
        }
        return list.toArray(new String[0]);
    }

    private static final class RawChapter {
        final String title;
        final String url;
        final int volume;
        final int order;
        final int rawIndex;

        RawChapter(String title, String url, int volume, int order, int rawIndex) {
            this.title = title;
            this.url = url;
            this.volume = volume;
            this.order = order;
            this.rawIndex = rawIndex;
        }
    }

    private static List<RawChapter> extractChapters(Document doc, String seriesUrl, List<NovelDiscovery.Candidate> candidates) {
        Elements links = doc.select("#chapterlist ul li a, .eplister ul li a, .clstyle li a, .bxcl ul li a");
        if (links.isEmpty()) {
            links = doc.select("a[href*='chapter'], a[href*='episode']");
        }

        Set<String> seen = new LinkedHashSet<>();
        List<RawChapter> raw = new ArrayList<>();
        int index = 0;

        for (Element link : links) {
            String href = link.absUrl("href");
            if (href == null || href.trim().isEmpty()) continue;
            href = normalizeChapterUrl(href);
            if (href.equals(seriesUrl) || !href.contains("sakuranovel.id/")) continue;
            if (href.contains("/series/") || href.contains("/tag/") || href.contains("/genre/")) continue;

            String text = clean(link.text());
            Element chapternum = link.selectFirst(".chapternum");
            if (chapternum != null && !clean(chapternum.text()).isEmpty()) {
                text = clean(chapternum.text());
            }
            if (text.isEmpty()) continue;

            index++;
            if (!seen.add(href)) {
                candidates.add(new NovelDiscovery.Candidate(href, text, 0, false, "tautan duplikat", index, false));
                continue;
            }

            int vol = parseVolume(text, href);
            int ord = parseOrder(text);

            candidates.add(new NovelDiscovery.Candidate(href, text, vol, true, "chapter sakura novel", index, true));
            raw.add(new RawChapter(text, href, vol, ord, index));
        }

        return raw;
    }

    private static int parseVolume(String text, String url) {
        Matcher m = VOL_PATTERN.matcher(text);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        m = VOL_PATTERN.matcher(url);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return 1;
    }

    private static int parseOrder(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("prolog")) return 10;
        if (lower.contains("epilog")) return 99990;
        if (lower.contains("ilustrasi")) return 5;
        Matcher m = CH_PATTERN.matcher(text);
        if (m.find()) {
            try {
                float num = Float.parseFloat(m.group(1));
                return (int) (num * 100);
            } catch (NumberFormatException ignored) {}
        }
        return 500;
    }

    private static List<Volume> groupVolumes(List<RawChapter> raw) {
        Map<Integer, List<RawChapter>> byVol = new LinkedHashMap<>();
        for (RawChapter ch : raw) {
            List<RawChapter> list = byVol.get(ch.volume);
            if (list == null) {
                list = new ArrayList<>();
                byVol.put(ch.volume, list);
            }
            list.add(ch);
        }

        List<Integer> sortedVolKeys = new ArrayList<>(byVol.keySet());
        Collections.sort(sortedVolKeys);

        List<Volume> volumes = new ArrayList<>();
        for (int volNum : sortedVolKeys) {
            List<RawChapter> list = byVol.get(volNum);
            // Sakura Novel lists chapters newest-to-oldest in DOM.
            // If orders are distinct, sort by order ascending. Otherwise reverse DOM order.
            boolean hasOrder = false;
            for (RawChapter rc : list) {
                if (rc.order != 500) {
                    hasOrder = true;
                    break;
                }
            }
            if (hasOrder) {
                list.sort(Comparator.comparingInt(a -> a.order));
            } else {
                Collections.reverse(list);
            }

            List<Chapter> chapters = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                RawChapter item = list.get(i);
                chapters.add(new Chapter(item.title, item.title, item.url, "", i + 1, volNum));
            }
            volumes.add(new Volume(volNum, chapters));
        }

        return volumes;
    }

    private static String normalizeChapterUrl(String url) {
        if (url == null) return "";
        int q = url.indexOf('?');
        if (q >= 0) url = url.substring(0, q);
        int h = url.indexOf('#');
        if (h >= 0) url = url.substring(0, h);
        url = url.trim();
        if (!url.endsWith("/")) url += "/";
        return url;
    }

    private static String clean(String raw) {
        if (raw == null) return "";
        return raw.replace('\u00a0', ' ').trim().replaceAll("\\s{2,}", " ");
    }
}