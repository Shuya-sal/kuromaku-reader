package id.shuyasal.kuromaku.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
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

public final class MegumiParser implements NovelSourceParser {

    private static final Pattern VOL_PATTERN = Pattern.compile("(?i)(?:vol(?:ume)?|jilid)\\s*[-.]?\\s*(\\d+)");
    private static final Pattern CH_PATTERN = Pattern.compile("(?i)(?:chapter|ch(?:ap)?\\.?)\\s*[-.]?\\s*(\\d+(?:\\.\\d+)?)");

    @Override
    public NovelUrl.Source source() {
        return NovelUrl.Source.MEGUMI;
    }

    @Override
    public boolean canHandle(String normalizedUrl) {
        return NovelUrl.sourceOf(normalizedUrl) == NovelUrl.Source.MEGUMI;
    }

    @Override
    public NovelDiscovery.Result parseCatalog(String html, String url) throws NovelDiscovery.Unreadable {
        return parse(html, url);
    }

    public static NovelDiscovery.Result parse(String html, String url) throws NovelDiscovery.Unreadable {
        if (html == null || html.trim().isEmpty()) {
            throw new NovelDiscovery.Unreadable("Halaman katalog Megumi Novel kosong.");
        }

        Document doc;
        try {
            doc = Jsoup.parse(html, url == null ? "" : url);
        } catch (Exception error) {
            throw new NovelDiscovery.Unreadable("HTML Megumi Novel tidak dapat dibaca: " + error.getMessage());
        }

        String title = extractTitle(doc);
        if (title.isEmpty()) {
            throw new NovelDiscovery.Unreadable("Judul novel tidak ditemukan di halaman Megumi Novel.");
        }

        String coverUrl = extractCover(doc);
        String titleJp = extractField(doc, "Nama Lain", "Judul Lain", "Alternative");
        String author = extractField(doc, "Penulis", "Author");
        if (author.isEmpty()) author = "Tidak tercantum di halaman sumber";
        String illustrator = "Tidak tercantum di halaman sumber";
        String genres = extractField(doc, "Genre");
        String status = extractField(doc, "Status");
        if (status.isEmpty()) status = "Ongoing";

        String[] synopsis = extractSynopsis(doc);

        List<NovelDiscovery.Candidate> candidates = new ArrayList<>();
        List<RawChapter> rawChapters = extractChapters(doc, url, candidates);

        if (rawChapters.isEmpty()) {
            throw new NovelDiscovery.Unreadable("Tidak ada chapter yang ditemukan pada halaman katalog Megumi Novel.");
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
        Element h1 = doc.selectFirst("h1.entry-title, header.entry-header h1, h1");
        if (h1 != null) {
            String text = clean(h1.text());
            text = text.replaceAll("(?i)\\s*[-|]\\s*Megumi Novel.*$", "").trim();
            if (!text.isEmpty()) return text;
        }
        Element ogTitle = doc.selectFirst("meta[property=og:title]");
        if (ogTitle != null && ogTitle.hasAttr("content")) {
            String text = clean(ogTitle.attr("content"));
            text = text.replaceAll("(?i)\\s*[-|]\\s*Megumi Novel.*$", "").trim();
            if (!text.isEmpty()) return text;
        }
        return "";
    }

    private static String extractCover(Document doc) {
        Element img = doc.selectFirst("img.featured-img, img.wp-post-image, .featured-image img");
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

    private static String extractField(Document doc, String... labels) {
        for (String label : labels) {
            Elements elements = doc.select("h2:contains(" + label + "), strong:contains(" + label + "), b:contains(" + label + "), span:contains(" + label + ")");
            for (Element el : elements) {
                String tag = el.tagName().toLowerCase(Locale.ROOT);
                if (tag.equals("h2") || tag.equals("h3") || tag.equals("h4")) {
                    Element next = el.nextElementSibling();
                    while (next != null && !next.tagName().matches("(?i)h[1-6]")) {
                        String text = clean(next.text());
                        if (!text.isEmpty()) return text;
                        next = next.nextElementSibling();
                    }
                } else if (tag.equals("strong") || tag.equals("b") || tag.equals("span")) {
                    Element p = el.parent();
                    if (p != null && (p.tagName().equalsIgnoreCase("p") || p.tagName().equalsIgnoreCase("li"))) {
                        String full = clean(p.text());
                        int colon = full.indexOf(':');
                        if (colon >= 0 && colon + 1 < full.length()) {
                            String val = clean(full.substring(colon + 1));
                            if (!val.isEmpty()) return val;
                        }
                    }
                }
            }
        }
        return "";
    }

    private static String[] extractSynopsis(Document doc) {
        List<String> list = new ArrayList<>();
        Element heading = doc.selectFirst("h2:contains(Sinopsis)");
        if (heading != null) {
            Element container = heading.parent();
            while (container != null && container.select("p").isEmpty()) {
                container = container.nextElementSibling();
            }
            if (container != null) {
                Elements ps = container.select("p");
                for (Element p : ps) {
                    String text = clean(p.text());
                    if (!text.isEmpty()) list.add(text);
                }
            }
        }
        if (list.isEmpty()) {
            Elements ps = doc.select(".entry-content p");
            int taken = 0;
            for (Element p : ps) {
                String text = clean(p.text());
                if (text.length() > 30 && !text.toLowerCase(Locale.ROOT).contains("chapter")) {
                    list.add(text);
                    if (++taken >= 3) break;
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

        RawChapter(String title, String url, int volume, int order) {
            this.title = title;
            this.url = url;
            this.volume = volume;
            this.order = order;
        }
    }

    private static List<RawChapter> extractChapters(Document doc, String novelUrl, List<NovelDiscovery.Candidate> candidates) {
        Elements links = doc.select("a.p-url, .foxiz-list a, .entry-content a[href*='chapter'], .entry-content a[href*='vol-'], .entry-content a[href*='volume-']");
        Set<String> seen = new LinkedHashSet<>();
        List<RawChapter> raw = new ArrayList<>();
        int index = 0;

        for (Element link : links) {
            String href = link.absUrl("href");
            if (href == null || href.trim().isEmpty()) continue;
            href = normalizeChapterUrl(href);
            if (href.equals(novelUrl) || !href.contains("meguminovel.com/")) continue;
            if (href.contains("/tag/") || href.contains("/category/") || href.contains("/author/")) continue;

            String text = clean(link.text());
            if (text.isEmpty() || "Semua".equalsIgnoreCase(text) || text.contains("Lebih Banyak")) continue;

            index++;
            if (!seen.add(href)) {
                candidates.add(new NovelDiscovery.Candidate(href, text, 0, false, "tautan duplikat", index, false));
                continue;
            }

            int vol = parseVolume(text, href);
            int ord = parseOrder(text);

            candidates.add(new NovelDiscovery.Candidate(href, text, vol, true, "chapter megumi novel", index, true));
            raw.add(new RawChapter(text, href, vol, ord));
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
        if (lower.contains("epilog")) return 990;
        if (lower.contains("ilustrasi")) return 5;
        Matcher m = CH_PATTERN.matcher(text);
        if (m.find()) {
            try {
                float num = Float.parseFloat(m.group(1));
                return (int) (num * 100) + 100;
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
            list.sort(Comparator.comparingInt(a -> a.order));

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