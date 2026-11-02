package id.shuyasal.kuromaku.parser;

/** Raised when the article body cannot be located or contains no readable content. */
public class ParseException extends Exception {

    public ParseException(String message) {
        super(message);
    }

    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
