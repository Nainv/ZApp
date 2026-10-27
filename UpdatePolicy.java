package com.zikra.tv;

import java.net.URI;
import java.util.Locale;

/** Limits the updater to this project's releases and GitHub's HTTPS asset hosts. */
public final class UpdatePolicy {
    public static final String REPOSITORY = "Nainv/ZApp";
    public static final long MAX_APK_BYTES = 200L * 1024 * 1024;
    public static final long NOTICE_CHECK_INTERVAL_MS = 60000L;
    private UpdatePolicy() {}
    public static boolean shouldShowNotice(long installed, long published) { return installed > 0 && published > installed; }
    public static boolean shouldCheckNotice(long now, long lastAttempt, boolean busy) {
        return !busy && (lastAttempt < 0 || now - lastAttempt >= NOTICE_CHECK_INTERVAL_MS);
    }

    public static boolean isReleaseAsset(String value) {
        try {
            URI uri = new URI(value);
            return "https".equals(uri.getScheme()) && "github.com".equals(uri.getHost()) && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getPath().startsWith("/" + REPOSITORY + "/releases/download/")
                    && !uri.getPath().contains("/../") && !uri.getPath().contains("/./") && uri.getPath().indexOf('\\') < 0;
        } catch (Exception ignored) { return false; }
    }

    public static boolean isDownloadUrl(String value) {
        try {
            URI uri = new URI(value);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            return "https".equals(uri.getScheme()) && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)
                    && (host.equals("github.com") || host.equals("api.github.com") || host.equals("githubusercontent.com") || host.endsWith(".githubusercontent.com"));
        } catch (Exception ignored) { return false; }
    }

    public static boolean digestMatches(String expected, String actual) {
        return expected != null && expected.matches("(?i)[0-9a-f]{64}") && expected.equalsIgnoreCase(actual);
    }
}
