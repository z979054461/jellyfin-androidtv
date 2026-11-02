package org.jellyfin.androidtv.ui.playback.danmaku;

/** A bounded, plain-text Bilibili XML comment. Times are absolute media milliseconds. */
public final class DanmakuComment {
    public final long timeMs;
    public final int mode;
    public final int color;
    public final String text;

    public DanmakuComment(long timeMs, int mode, int color, String text) {
        this.timeMs = timeMs;
        this.mode = mode;
        this.color = color;
        this.text = text;
    }
}
