package org.jellyfin.androidtv.ui.playback;

/** Android-independent, single-gesture state machine. Times are monotonic milliseconds. */
public final class HoldToDoubleSpeed {
    public interface Target {
        boolean isEligible();
        float getSpeed();
        void setTemporarySpeed(float speed);
        void showIndicator(boolean visible);
        void shortPress(int keyCode);
    }

    // A lost release must never leave the player accelerated indefinitely.
    public static final long MAX_HOLD_MILLIS = 30_000;
    private final Target target;
    private final long timeoutMillis;
    private int key = -1;
    private long downTime;
    private long finishedDownTime = -1;
    private int finishedKey = -1;
    private boolean active;
    private boolean canceled;
    private float previousSpeed;

    public HoldToDoubleSpeed(Target target, long timeoutMillis) {
        this.target = target;
        this.timeoutMillis = timeoutMillis;
    }

    public boolean isTracking() { return key != -1 && !canceled; }

    public boolean owns(int keyCode, long gestureDownTime) {
        return key == keyCode && downTime == gestureDownTime;
    }

    public boolean hasFinished(int keyCode, long gestureDownTime) {
        return finishedKey == keyCode && finishedDownTime == gestureDownTime;
    }

    public boolean down(int keyCode, long gestureDownTime, long now, boolean repeat) {
        // Some dispatch paths replay an entire event pair. Never begin it twice,
        // and never let a stale event cancel a newer gesture.
        if (hasFinished(keyCode, gestureDownTime)) return true;
        if (key != -1) {
            if (key == keyCode && downTime == gestureDownTime) {
                tick(now);
                return true;
            }
            cancel();
            finish();
        }
        if (repeat || !target.isEligible()) return false;
        key = keyCode;
        downTime = gestureDownTime;
        canceled = false;
        tick(now);
        return true;
    }

    public boolean up(int keyCode, long gestureDownTime, boolean eventCanceled) {
        if (key != keyCode || downTime != gestureDownTime) {
            return finishedKey == keyCode && finishedDownTime == gestureDownTime;
        }
        boolean click = !active && !canceled && !eventCanceled && target.isEligible();
        restore();
        finish();
        if (click) target.shortPress(keyCode);
        return true;
    }

    public void tick(long now) {
        if (key == -1 || canceled) return;
        if (!target.isEligible() || now - downTime >= MAX_HOLD_MILLIS) {
            cancel();
        } else if (!active && now - downTime >= timeoutMillis) {
            previousSpeed = target.getSpeed();
            active = true;
            target.setTemporarySpeed(2.0f);
            target.showIndicator(true);
        }
    }

    /** Keep ownership of this release so cancellation cannot accidentally pause/resume. */
    public void cancel() {
        canceled = true;
        restore();
    }

    private void restore() {
        if (!active) return;
        active = false;
        target.setTemporarySpeed(previousSpeed);
        target.showIndicator(false);
    }

    private void finish() {
        finishedKey = key;
        finishedDownTime = downTime;
        key = -1;
        canceled = false;
    }
}
