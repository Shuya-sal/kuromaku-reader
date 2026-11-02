package id.shuyasal.kuromaku.net;

import java.io.IOException;

/** Raised when part of the feed could not be collected. */
public class FeedException extends IOException {

    public final int page;
    public final int startIndex;

    public FeedException(String message, int page, int startIndex, Throwable cause) {
        super(message, cause);
        this.page = page;
        this.startIndex = startIndex;
    }
}
