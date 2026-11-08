package org.jellyfin.androidtv.ui.playback;

/** Dependency-free regression suite, also invoked by the Android project's Kotest runner. */
public final class HoldToDoubleSpeedTestCases {
    private static final int PLAY = 126;
    private static final int TOGGLE = 85;
    private static final class Fake implements HoldToDoubleSpeed.Target {
        boolean eligible = true;
        boolean indicator;
        float speed;
        int clicks;
        int writes;
        Runnable onShortPress;
        Fake(float speed) { this.speed = speed; }
        public boolean isEligible() { return eligible; }
        public float getSpeed() { return speed; }
        public void setTemporarySpeed(float value) { speed = value; writes++; }
        public void showIndicator(boolean visible) { indicator = visible; }
        public void shortPress(int keyCode) {
            clicks++;
            if (onShortPress != null) onShortPress.run();
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void runAll() {
        Fake f = new Fake(1.25f);
        HoldToDoubleSpeed h = new HoldToDoubleSpeed(f, 500);
        check(h.down(PLAY, 100, 100, false), "eligible down owned");
        h.tick(599);
        check(f.speed == 1.25f && !f.indicator, "short hold does not change speed");
        check(h.up(PLAY, 100, false), "release owned");
        check(f.clicks == 1 && f.writes == 0, "short press exactly once");
        check(h.up(PLAY, 100, false), "duplicate release swallowed");
        check(f.clicks == 1, "duplicate release never toggles");
        check(h.down(PLAY, 100, 650, false), "duplicate completed DOWN swallowed");
        h.up(PLAY, 100, false);
        check(f.clicks == 1 && !h.isTracking(), "duplicate event pair never toggles");

        f = new Fake(1.25f); h = new HoldToDoubleSpeed(f, 500);
        final HoldToDoubleSpeed reentrantGesture = h;
        f.onShortPress = reentrantGesture::cancel; // PlaybackController.pause callback.
        h.down(TOGGLE, 0, 0, false); h.up(TOGGLE, 0, false);
        check(f.clicks == 1 && f.writes == 0 && !h.isTracking(), "short pause reentrancy is safe");
        h.up(TOGGLE, 0, false);
        check(f.clicks == 1, "reentrant cancellation preserves duplicate-release ownership");
        h.down(TOGGLE, 1000, 1000, false);
        check(h.hasFinished(TOGGLE, 0), "adapter recognizes completed events before changing session");
        h.down(TOGGLE, 0, 1200, false); h.up(TOGGLE, 0, false);
        check(h.isTracking() && h.owns(TOGGLE, 1000), "old event pair leaves new hold intact");
        h.tick(1500); h.up(TOGGLE, 1000, false);
        check(f.speed == 1.25f && f.clicks == 1, "new hold works after reentrant cancellation");

        for (float speed : new float[] {1, 1.25f, 1.5f, 2}) {
            f = new Fake(speed);
            h = new HoldToDoubleSpeed(f, 500);
            h.down(TOGGLE, 100, 100, false);
            h.tick(600); // No repeated DOWN is needed.
            check(f.speed == 2 && f.indicator, "timer activates 2x");
            h.down(TOGGLE, 100, 700, true);
            h.down(TOGGLE, 100, 800, true);
            check(f.writes == 1, "repeat does not replace original speed");
            h.up(TOGGLE, 100, false);
            check(f.speed == speed && !f.indicator && f.clicks == 0, "restore exact prior speed");
        }

        f = new Fake(1.5f); h = new HoldToDoubleSpeed(f, 500);
        h.down(PLAY, 100, 100, false); h.up(PLAY, 100, true);
        check(f.clicks == 0 && f.writes == 0, "canceled short press does nothing");
        h.down(PLAY, 200, 200, false); h.tick(700); h.up(PLAY, 200, true);
        check(f.speed == 1.5f && !f.indicator, "canceled long release restores");

        for (String reason : new String[] {"focus", "pause", "stop", "episode", "view teardown", "alternate key"}) {
            f = new Fake(1.25f); h = new HoldToDoubleSpeed(f, 500);
            h.down(PLAY, 0, 0, false); h.tick(500); h.cancel(); h.cancel();
            check(f.speed == 1.25f && f.writes == 2, reason + " restores once");
            h.down(PLAY, 0, 900, true); h.up(PLAY, 0, false);
            check(f.clicks == 0 && f.speed == 1.25f, reason + " release cannot resume");
        }
        f = new Fake(1.5f); h = new HoldToDoubleSpeed(f, 500);
        h.down(PLAY, 0, 0, false); f.eligible = false; h.tick(500); h.up(PLAY, 0, false);
        check(f.writes == 0 && f.clicks == 0, "invalidated pending gesture never activates");
        check(!h.down(PLAY, 600, 600, false), "paused/live/nonseekable not intercepted");
        f.eligible = true;
        check(!h.down(PLAY, 600, 700, true), "orphan repeat ignored");
        h.down(PLAY, 800, 800, false); h.tick(1300); f.eligible = false; h.tick(1400);
        check(f.speed == 1.5f && !f.indicator, "poll detects invalid session");
        h.up(PLAY, 800, false);
        check(f.clicks == 0, "invalid session release does not toggle");

        f = new Fake(1.25f); h = new HoldToDoubleSpeed(f, 500);
        h.down(PLAY, 0, 0, false); h.tick(500); h.tick(HoldToDoubleSpeed.MAX_HOLD_MILLIS);
        check(f.speed == 1.25f && !f.indicator, "missing UP bounded by failsafe");
        check(!h.isTracking() && h.owns(PLAY, 0), "failsafe stops timer but retains release ownership");
        h.down(PLAY, 0, 31000, true);
        check(f.speed == 1.25f && !h.isTracking(), "post-failsafe repeat cannot rearm 2x");
        h.up(PLAY, 0, false);
        check(f.clicks == 0, "late UP after failsafe suppressed");
        h.down(PLAY, 40000, 40000, false); h.tick(40500);
        h.down(TOGGLE, 41000, 41000, false);
        check(f.speed == 1.25f, "replacement gesture restores old gesture first");
        h.up(TOGGLE, 41000, false);
        check(f.clicks == 1, "replacement short press works");
    }
    public static void main(String[] args) {
        runAll();
        System.out.println("HoldToDoubleSpeed regression cases passed");
    }
}
