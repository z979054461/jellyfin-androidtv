package org.jellyfin.androidtv.update;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deliberately specific to this fork; this is not an arbitrary APK downloader. */
public final class UpdatePolicy {
    public static final String PACKAGE = "io.github.z979054461.jellyfin.danmaku";
    public static final String API = "https://api.github.com/repos/z979054461/jellyfin-androidtv/releases?per_page=20";
    public static final long MAX_APK_BYTES = 200L * 1024 * 1024;
    private static final String RELEASE_PATH = "/z979054461/jellyfin-androidtv/releases/download/";
    private static final Pattern ASSET_NAME = Pattern.compile("jellyfin-danmaku-([1-9][0-9]*)\\.apk");
    private static final Pattern DIGEST = Pattern.compile("sha256:([a-fA-F0-9]{64})");

    private UpdatePolicy() { }

    public static Long versionCode(String name) {
        Matcher match = ASSET_NAME.matcher(name);
        if (!match.matches()) return null;
        try {
            return Long.parseLong(match.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static String sha256(String value) {
        Matcher match = DIGEST.matcher(value);
        return match.matches() ? match.group(1).toLowerCase(Locale.ROOT) : null;
    }

    public static void requireVerifiableCandidate(boolean hasValid, boolean hasInvalidNewer) {
        if (!hasValid && hasInvalidNewer) throw new IllegalStateException("Newer APK metadata could not be verified");
    }

    public static boolean isNewer(long candidate, long installed) {
        return candidate > installed && candidate <= Integer.MAX_VALUE;
    }

    public static String sizeLabel(long bytes) {
        return String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024));
    }

    public static boolean trustedDownloadUrl(String value) {
        return trustedDownloadUrl(value, false);
    }

    public static boolean trustedDownloadUrl(String value, boolean initial) {
        try {
            URI uri = new URI(value);
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443) || uri.getFragment() != null) return false;
            String host = uri.getHost();
            if (host == null) return false;
            switch (host.toLowerCase(Locale.ROOT)) {
                case "github.com":
                    String path = uri.getRawPath();
                    return path != null && path.startsWith(RELEASE_PATH) && !path.contains("..") && !path.contains("%");
                case "release-assets.githubusercontent.com":
                case "objects.githubusercontent.com":
                    return !initial;
                default:
                    return false;
            }
        } catch (URISyntaxException ignored) {
            return false;
        }
    }
}
