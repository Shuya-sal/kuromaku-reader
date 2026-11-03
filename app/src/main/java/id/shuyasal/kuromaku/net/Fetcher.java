package id.shuyasal.kuromaku.net;

/**
 * Where the app gets its bytes.
 *
 * <p>Production always uses {@link Http}. Tests replace the instance with a
 * fixture source so the real download path — validation, ordering, atomic write,
 * index — runs end to end without a network, and so an "offline" scenario can be
 * simulated deterministically instead of hoping a connection fails.
 */
public final class Fetcher {

    /** Two things the reader ever needs: an article page and an illustration. */
    public interface Source {
        String page(String url) throws Exception;

        byte[] image(String url) throws Exception;

        /**
         * Same download, reporting how many bytes have arrived.
         *
         * <p>A default so every existing source — including the fixture sources
         * tests install — keeps working untouched; the real network overrides it.
         * A source that cannot report progress simply returns the bytes, and the
         * UI then shows a spinner instead of a percentage.
         */
        default byte[] image(String url, Http.Progress progress) throws Exception {
            return image(url);
        }
    }

    private static final Source NETWORK = new Source() {
        @Override
        public String page(String url) throws Exception {
            return Http.get(url);
        }

        @Override
        public byte[] image(String url) throws Exception {
            return Http.getBytes(url);
        }

        @Override
        public byte[] image(String url, Http.Progress progress) throws Exception {
            return Http.getBytes(url, progress);
        }
    };

    private static volatile Source source = NETWORK;

    private Fetcher() {
    }

    public static Source get() {
        return source;
    }

    /** Replaces the source; {@code null} restores the real HTTP client. */
    public static void use(Source replacement) {
        source = replacement == null ? NETWORK : replacement;
    }

    public static void reset() {
        source = NETWORK;
    }
}
