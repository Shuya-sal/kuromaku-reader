package id.shuyasal.kuromaku.parser;

import id.shuyasal.kuromaku.net.NovelUrl;

public interface NovelSourceParser {
    NovelUrl.Source source();
    boolean canHandle(String normalizedUrl);
    NovelDiscovery.Result parseCatalog(String html, String url) throws NovelDiscovery.Unreadable;
}