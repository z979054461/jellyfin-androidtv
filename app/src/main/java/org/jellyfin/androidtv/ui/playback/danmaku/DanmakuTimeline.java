package org.jellyfin.androidtv.ui.playback.danmaku;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Stateless media-clock rendering: pause/buffering freeze naturally, and seeks reconstruct the same
 * frame without replaying events. Lane admission is precomputed, not dependent on frame rate.
 * Supply only lanes above the subtitle-safe area. Create a new timeline when geometry/settings change.
 */
public final class DanmakuTimeline {
    public interface TextMeasurer {
        float measure(String text);
    }

    public static final class Placement {
        public final DanmakuComment comment;
        public final float x;
        public final float y;
        public final int lane;

        private Placement(DanmakuComment comment, float x, float y, int lane) {
            this.comment = comment;
            this.x = x;
            this.y = y;
            this.lane = lane;
        }
    }

    private static final class Scheduled {
        final DanmakuComment comment;
        final float width;
        final int lane;

        Scheduled(DanmakuComment comment, float width, int lane) {
            this.comment = comment;
            this.width = width;
            this.lane = lane;
        }
    }

    private final int width;
    private final int laneHeight;
    private final long durationMs;
    private final List<Scheduled> scheduled = new ArrayList<>();

    public DanmakuTimeline(List<DanmakuComment> comments, int width, int laneHeight, int lanes,
                          long durationMs, TextMeasurer measurer) {
        if (width <= 0 || laneHeight <= 0 || lanes <= 0 || lanes > 100 || durationMs < 100 || durationMs > 60000) {
            throw new IllegalArgumentException("Invalid danmaku geometry or duration");
        }
        this.width = width;
        this.laneHeight = laneHeight;
        this.durationMs = durationMs;
        List<DanmakuComment> sorted = new ArrayList<>(comments);
        Collections.sort(sorted, Comparator.comparingLong(comment -> comment.timeMs));
        Scheduled[] previous = new Scheduled[lanes];
        for (DanmakuComment comment : sorted) {
            if (comment.timeMs < 0 || (comment.mode != 1 && comment.mode != 4 && comment.mode != 5)) continue;
            float textWidth = measurer.measure(comment.text);
            if (Float.isNaN(textWidth) || Float.isInfinite(textWidth) || textWidth <= 0) continue;
            // Fixed comments wider than the screen cannot fit; omit instead of clipping over controls.
            if (comment.mode != 1 && textWidth > width) continue;
            for (int index = 0; index < lanes; index++) {
                int lane = comment.mode == 4 ? lanes - 1 - index : index;
                if (fits(previous[lane], comment, textWidth)) {
                    Scheduled item = new Scheduled(comment, textWidth, lane);
                    previous[lane] = item;
                    scheduled.add(item);
                    break;
                }
            }
        }
    }

    private boolean fits(Scheduled previous, DanmakuComment next, float nextWidth) {
        if (previous == null) return true;
        long elapsed = next.timeMs - previous.comment.timeMs;
        if (elapsed >= durationMs) return true;
        if (next.mode != 1 || previous.comment.mode != 1) return false;
        double previousSpeed = (width + previous.width) / durationMs;
        double nextSpeed = (width + nextWidth) / durationMs;
        double gap = previousSpeed * elapsed - previous.width;
        // Faster following comments must not catch the predecessor before it fully leaves the screen.
        double closingDistance = Math.max(0, nextSpeed - previousSpeed) * (durationMs - elapsed);
        return gap >= closingDistance + 8;
    }

    public List<Placement> frame(long mediaPositionMs) {
        if (mediaPositionMs < 0 || scheduled.isEmpty()) return Collections.emptyList();
        int low = 0;
        int high = scheduled.size();
        long earliest = mediaPositionMs - durationMs;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (scheduled.get(middle).comment.timeMs <= earliest) low = middle + 1;
            else high = middle;
        }
        List<Placement> frame = new ArrayList<>();
        for (int index = low; index < scheduled.size(); index++) {
            Scheduled item = scheduled.get(index);
            if (item.comment.timeMs > mediaPositionMs) break;
            float x = item.comment.mode == 1
                    ? width - (width + item.width) * ((float) (mediaPositionMs - item.comment.timeMs) / durationMs)
                    : (width - item.width) / 2f;
            frame.add(new Placement(item.comment, x, (item.lane + 0.8f) * laneHeight, item.lane));
        }
        return frame;
    }
}
