import org.jellyfin.androidtv.ui.playback.network.NetworkSpeedLabel;
import org.jellyfin.androidtv.ui.playback.network.NetworkSpeedLabel.State;

class NetworkSpeedLabelTest {
    private static int checks;
    private static void expect(Object actual, Object expected) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    public static void main(String[] args) {
        expect(NetworkSpeedLabel.formatRate(0, false), "—");
        expect(NetworkSpeedLabel.formatRate(0, true), "0 B/s");
        expect(NetworkSpeedLabel.formatRate(-1, true), "0 B/s");
        expect(NetworkSpeedLabel.formatRate(999, true), "999 B/s");
        expect(NetworkSpeedLabel.formatRate(1_000, true), "1.0 kB/s");
        expect(NetworkSpeedLabel.formatRate(1_500_000, true), "1.5 MB/s");
        expect(NetworkSpeedLabel.formatRate(2_500_000_000L, true), "2.5 GB/s");
        expect(NetworkSpeedLabel.classify(true, false, 1000, true, false, true, 0), State.TRANSFERRING);
        expect(NetworkSpeedLabel.classify(true, false, 1000, true, true, true, 0), State.PAUSED);
        expect(NetworkSpeedLabel.classify(true, false, 0, true, false, false, 5000), State.BUFFERED);
        expect(NetworkSpeedLabel.classify(true, false, 0, true, false, true, 5000), State.WAITING);
        expect(NetworkSpeedLabel.classify(true, false, 0, false, false, false, 0), State.WAITING);
        expect(NetworkSpeedLabel.classify(true, false, 0, true, true, false, 5000), State.PAUSED);
        expect(NetworkSpeedLabel.classify(true, false, 0, true, false, false, 0), State.IDLE);
        expect(NetworkSpeedLabel.classify(false, false, 1000, true, false, true, 0), State.IDLE);
        expect(NetworkSpeedLabel.classify(true, true, 0, true, false, false, 5000), State.LOCAL);
        expect(NetworkSpeedLabel.classify(true, true, 0, true, true, false, 5000), State.LOCAL_PAUSED);
        System.out.println("Network speed labels: " + checks + " checks passed");
    }
}
