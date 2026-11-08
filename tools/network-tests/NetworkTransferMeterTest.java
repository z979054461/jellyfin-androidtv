import org.jellyfin.androidtv.ui.playback.network.NetworkTransferMeter;
import org.jellyfin.androidtv.ui.playback.network.NetworkTransferMeter.State;

public final class NetworkTransferMeterTest {
    private static final class Clock implements NetworkTransferMeter.Clock {
        long now;
        public long elapsedRealtime() { return now; }
    }

    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) throws Exception {
        Clock clock = new Clock();
        NetworkTransferMeter meter = new NetworkTransferMeter(clock);
        equal(State.UNOBSERVED, meter.snapshot().getState());
        equal(false, meter.snapshot().isMeasurementReady());
        meter.onTransferStart(true);
        meter.onBytesTransferred(true, 1000);
        equal(false, meter.snapshot().isMeasurementReady());
        equal(0L, meter.snapshot().getBytesPerSecond());
        clock.now = 1000;
        equal(1000L, meter.snapshot().getBytesPerSecond());
        meter.onTransferStart(true);
        meter.onBytesTransferred(true, 2000);
        equal(2, meter.snapshot().getActiveNetworkTransfers());
        equal(3000L, meter.snapshot().getBytesPerSecond());
        meter.onTransferStart(false);
        meter.onBytesTransferred(false, Integer.MAX_VALUE);
        equal(3000L, meter.snapshot().getBytesPerSecond());
        equal(State.NETWORK, meter.snapshot().getState());
        equal(true, meter.snapshot().isLocalObserved());
        meter.onTransferEnd(true);
        meter.onTransferEnd(true);
        meter.onTransferEnd(false);
        equal(0, meter.snapshot().getActiveNetworkTransfers());
        clock.now = 2000;
        equal(1000L, meter.snapshot().getBytesPerSecond());
        clock.now = 3000;
        equal(0L, meter.snapshot().getBytesPerSecond());
        equal(State.NETWORK, meter.snapshot().getState());
        equal(true, meter.snapshot().isMeasurementReady());

        // Replacement uses a new immutable binding, so callbacks of all kinds on old
        // sources cannot start a transfer, add bytes, or decrement the new session.
        meter.close();
        NetworkTransferMeter next = new NetworkTransferMeter(clock, false);
        meter.onTransferStart(true);
        meter.onBytesTransferred(true, Integer.MAX_VALUE);
        meter.onTransferEnd(true);
        equal(State.INACTIVE, meter.snapshot().getState());
        equal(0L, meter.snapshot().getBytesPerSecond());
        equal(false, meter.snapshot().isSupported());
        equal(State.UNOBSERVED, next.snapshot().getState());
        next.onTransferStart(false); // Local subtitle before HTTP video must not label video local.
        next.onBytesTransferred(false, 10000);
        equal(State.UNOBSERVED, next.snapshot().getState());
        equal(0L, next.snapshot().getBytesPerSecond());

        NetworkTransferMeter local = new NetworkTransferMeter(clock, true);
        local.onTransferStart(false);
        local.onBytesTransferred(false, 10000);
        equal(State.LOCAL, local.snapshot().getState());
        equal(false, local.snapshot().isNetworkObserved());
        equal(false, local.snapshot().isMeasurementReady());
        equal(0L, local.snapshot().getBytesPerSecond());

        // Many events over a long runtime use only the same forty buckets.
        next.onTransferStart(true);
        for (int i = 0; i < 100000; i++) {
            clock.now += 50;
            next.onBytesTransferred(true, 100);
        }
        equal(2000L, next.snapshot().getBytesPerSecond());
        clock.now += 10L * 365 * 24 * 60 * 60 * 1000;
        equal(0L, next.snapshot().getBytesPerSecond());
        next.onBytesTransferred(true, 2000);
        equal(1000L, next.snapshot().getBytesPerSecond());
        next.onBytesTransferred(true, -1);
        next.onBytesTransferred(true, 0);
        equal(1000L, next.snapshot().getBytesPerSecond());

        // Concurrent audio/video loaders must not lose updates.
        Clock concurrentClock = new Clock();
        NetworkTransferMeter concurrent = new NetworkTransferMeter(concurrentClock);
        concurrent.onTransferStart(true);
        Thread[] writers = new Thread[4];
        for (int i = 0; i < writers.length; i++) {
            writers[i] = new Thread(() -> {
                for (int n = 0; n < 10000; n++) concurrent.onBytesTransferred(true, 10);
            });
            writers[i].start();
        }
        for (Thread writer : writers) writer.join();
        concurrentClock.now = 1000;
        equal(400000L, concurrent.snapshot().getBytesPerSecond());
        System.out.println("NetworkTransferMeterTest passed");
    }
}
