package tv.iptvibe.mobile;

/** Version ordering uses Android's integer code, never a lexical version string. */
public final class UpdatePolicy {
    public static final long MAX_APK = 200L * 1024 * 1024;
    public static final long CHECK_INTERVAL_MS = 5L * 60 * 1000;
    private UpdatePolicy() {}
    public static boolean newer(long installed, long offered) { return offered > installed; }
    public static boolean checkDue(long now, long last, boolean manual) {
        return manual || last == 0 || now < last || now - last >= CHECK_INTERVAL_MS;
    }
    public static boolean valid(long code, long minimum, long size, String hash, String url) {
        if (code < 1 || code > Integer.MAX_VALUE || minimum < 0 || minimum > code || size < 1 || size > MAX_APK
                || hash == null || !hash.matches("[a-fA-F0-9]{64}")) return false;
        try {
            java.net.URI u = new java.net.URI(url);
            return "https".equals(u.getScheme()) && "github.com".equals(u.getHost())
                    && u.getPort() == -1 && u.getRawQuery() == null && u.getRawFragment() == null
                    && u.getRawUserInfo() == null
                    && u.getRawPath().matches("/frittcat/IPTVibe-Runtime/releases/download/[A-Za-z0-9._-]+/IPTVibe-Mobile\\.apk");
        } catch (Exception e) { return false; }
    }
}
