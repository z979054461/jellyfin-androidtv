package org.jellyfin.androidtv.ui.playback.overlay.action;

import java.util.Locale;

/** Small, Android-independent formatters. Unknown metadata is never presented as zero. */
public final class PlaybackInfoFormatter {
    private PlaybackInfoFormatter() { }

    public static String bitrate(Number value) {
        if (value == null || value.doubleValue() <= 0 || !finite(value.doubleValue())) return null;
        double bits = value.doubleValue();
        return bits >= 1_000_000
                ? String.format(Locale.ROOT, "%.2f Mbps", bits / 1_000_000)
                : String.format(Locale.ROOT, "%.0f kbps", bits / 1_000);
    }

    public static String dimensions(Integer width, Integer height) {
        return width != null && height != null && width > 0 && height > 0 ? width + " × " + height : null;
    }

    public static String frameRate(Number value) {
        return value != null && finite(value.doubleValue()) && value.doubleValue() > 0
                ? String.format(Locale.ROOT, "%.3f fps", value.doubleValue()) : null;
    }

    public static String sampleRate(Integer value) {
        return value != null && value > 0 ? String.format(Locale.ROOT, "%.1f kHz", value / 1000.0) : null;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    /** Only codec/container identifiers, never arbitrary titles, paths or format.toString(). */
    public static String identifier(String value) {
        return value != null && value.length() <= 80 && value.matches("[a-zA-Z0-9][a-zA-Z0-9.,_+ /-]*")
                && !value.contains(":") && !value.contains("//") ? value : null;
    }
}
