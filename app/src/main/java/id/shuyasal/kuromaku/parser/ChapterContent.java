package id.shuyasal.kuromaku.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import id.shuyasal.kuromaku.data.Block;

/** Result of extracting one chapter: title, ordered blocks, and the original URL. */
public final class ChapterContent {

    private final String title;
    private final String sourceUrl;
    private final List<Block> blocks;

    public ChapterContent(String title, String sourceUrl, List<Block> blocks) {
        this.title = title == null ? "" : title;
        this.sourceUrl = sourceUrl == null ? "" : sourceUrl;
        this.blocks = Collections.unmodifiableList(new ArrayList<>(blocks));
    }

    public String title() {
        return title;
    }

    public String sourceUrl() {
        return sourceUrl;
    }

    public List<Block> blocks() {
        return blocks;
    }

    public int imageCount() {
        int count = 0;
        for (Block block : blocks) {
            if (block.isImage()) count++;
        }
        return count;
    }

    public int textCount() {
        int count = 0;
        for (Block block : blocks) {
            if (!block.isImage() && block.type != Block.TYPE_DIVIDER) count++;
        }
        return count;
    }
}
