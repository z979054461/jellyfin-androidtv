package org.jellyfin.androidtv.ui.playback.network;

import java.util.Locale;

/** Presentation-only classification. A zero transfer rate does not imply a slow connection. */
public final class NetworkSpeedLabel {
    public enum State { TRANSFERRING, WAITING, BUFFERED, PAUSED, IDLE, LOCAL, LOCAL_PAUSED }
    private NetworkSpeedLabel() { }

    public static State classify(boolean active, boolean local, long bytesPerSecond,
            boolean observed, boolean paused, boolean loading, long bufferedMs) {
        if (!active) return State.IDLE;
        if (local) return paused ? State.LOCAL_PAUSED : State.LOCAL;
        if (bytesPerSecond > 0) return paused ? State.PAUSED : State.TRANSFERRING;
        if (paused) return State.PAUSED;
        if (!observed || loading) return State.WAITING;
        if (bufferedMs > 0) return State.BUFFERED;
        return State.IDLE;
    }

    /** Decimal bytes per second, deliberately not bits/s, encoded bitrate or estimated bandwidth. */
    public static String formatRate(long bytesPerSecond, boolean observed) {
        if (!observed && bytesPerSecond <= 0) return "—";
        long value = Math.max(0, bytesPerSecond);
        if (value < 1_000) return value + " B/s";
        if (value < 1_000_000) return String.format(Locale.ROOT, "%.1f kB/s", value / 1_000.0);
        if (value < 1_000_000_000) return String.format(Locale.ROOT, "%.1f MB/s", value / 1_000_000.0);
        return String.format(Locale.ROOT, "%.1f GB/s", value / 1_000_000_000.0);
    }
}
