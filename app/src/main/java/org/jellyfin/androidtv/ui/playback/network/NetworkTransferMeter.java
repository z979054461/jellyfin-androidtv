package org.jellyfin.androidtv.ui.playback.network;

import java.util.Arrays;

/** Payload throughput, not encoded media bitrate or estimated available bandwidth. */
public final class NetworkTransferMeter {
    public interface Clock {
        long elapsedRealtime();
    }

    public enum State { INACTIVE, UNOBSERVED, LOCAL, NETWORK }

    private static final int BUCKET_MS = 50;
    private static final int BUCKET_COUNT = 40;
    private static final long WINDOW_MS = BUCKET_MS * BUCKET_COUNT;
    private final Clock clock;
    private final boolean localSource;
    private final long[] ticks = new long[BUCKET_COUNT];
    private final long[] bytes = new long[BUCKET_COUNT];
    private boolean active = true;
    private boolean networkObserved;
    private boolean localObserved;
    private long firstNetworkTime = -1;
    private int activeNetworkTransfers;

    public NetworkTransferMeter(Clock clock) {
        this(clock, true);
    }

    public NetworkTransferMeter(Clock clock, boolean localSource) {
        this.clock = clock;
        this.localSource = localSource;
        Arrays.fill(ticks, Long.MIN_VALUE);
    }

    public synchronized void onTransferStart(boolean network) {
        if (!active) return;
        observe(network);
        if (network) activeNetworkTransfers++;
    }

    public synchronized void onBytesTransferred(boolean network, int count) {
        if (!active || count <= 0) return;
        observe(network);
        if (!network) return;
        long tick = clock.elapsedRealtime() / BUCKET_MS;
        int slot = (int) (tick % BUCKET_COUNT);
        if (ticks[slot] != tick) {
            ticks[slot] = tick;
            bytes[slot] = 0;
        }
        bytes[slot] += count;
    }

    public synchronized void onTransferEnd(boolean network) {
        if (active && network && activeNetworkTransfers > 0) activeNetworkTransfers--;
    }

    private void observe(boolean network) {
        if (network) {
            if (!networkObserved) firstNetworkTime = clock.elapsedRealtime();
            networkObserved = true;
        } else {
            localObserved = true;
        }
    }

    /** Permanently closes this media session; late callbacks cannot revive it. */
    public synchronized void close() {
        active = false;
        networkObserved = false;
        localObserved = false;
        firstNetworkTime = -1;
        activeNetworkTransfers = 0;
        Arrays.fill(bytes, 0);
        Arrays.fill(ticks, Long.MIN_VALUE);
    }

    public synchronized Snapshot snapshot() {
        long now = clock.elapsedRealtime();
        long tick = now / BUCKET_MS;
        long total = 0;
        for (int i = 0; i < BUCKET_COUNT; i++) {
            if (ticks[i] <= tick && ticks[i] > tick - BUCKET_COUNT) total += bytes[i];
        }
        long elapsed = firstNetworkTime < 0 ? 0 : Math.max(0, now - firstNetworkTime);
        // Warm up for one bucket, avoiding a fictitious rate from a zero-duration sample.
        boolean ready = active && networkObserved && elapsed >= BUCKET_MS;
        long rate = ready ? Math.round(total * 1000.0 / Math.min(WINDOW_MS, elapsed)) : 0;
        State state = !active ? State.INACTIVE : networkObserved ? State.NETWORK
                : localSource && localObserved ? State.LOCAL : State.UNOBSERVED;
        return new Snapshot(state, rate, localObserved, ready, activeNetworkTransfers);
    }

    /** Immutable result; local reads (including future cache reads) never inflate throughput. */
    public static final class Snapshot {
        private final State state;
        private final long bytesPerSecond;
        private final boolean localObserved;
        private final boolean measurementReady;
        private final int activeNetworkTransfers;

        private Snapshot(State state, long bytesPerSecond, boolean localObserved,
                         boolean measurementReady, int activeNetworkTransfers) {
            this.state = state;
            this.bytesPerSecond = bytesPerSecond;
            this.localObserved = localObserved;
            this.measurementReady = measurementReady;
            this.activeNetworkTransfers = activeNetworkTransfers;
        }

        public State getState() { return state; }
        public long getBytesPerSecond() { return bytesPerSecond; }
        public boolean isNetworkObserved() { return state == State.NETWORK; }
        public boolean isLocalObserved() { return localObserved; }
        public boolean isTransferObserved() { return isNetworkObserved() || localObserved; }
        public boolean isMeasurementReady() { return measurementReady; }
        public boolean isSupported() { return state != State.INACTIVE; }
        public int getActiveNetworkTransfers() { return activeNetworkTransfers; }
    }
}
