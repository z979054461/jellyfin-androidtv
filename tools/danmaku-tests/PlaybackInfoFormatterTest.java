import java.util.Locale;
import java.util.Objects;
import org.jellyfin.androidtv.ui.playback.overlay.action.PlaybackInfoFormatter;

public final class PlaybackInfoFormatterTest {
    public static void main(String[] args) {
        Locale.setDefault(Locale.GERMANY);
        equal(null, PlaybackInfoFormatter.bitrate(null));
        equal(null, PlaybackInfoFormatter.bitrate(-1));
        equal(null, PlaybackInfoFormatter.bitrate(Double.NaN));
        equal(null, PlaybackInfoFormatter.bitrate(Double.POSITIVE_INFINITY));
        equal("192 kbps", PlaybackInfoFormatter.bitrate(192000));
        equal("1.00 Mbps", PlaybackInfoFormatter.bitrate(1000000));
        equal("40.50 Mbps", PlaybackInfoFormatter.bitrate(40500000L));
        equal(null, PlaybackInfoFormatter.dimensions(1920, null));
        equal(null, PlaybackInfoFormatter.dimensions(-1, 1080));
        equal("3840 × 2160", PlaybackInfoFormatter.dimensions(3840, 2160));
        equal("23.976 fps", PlaybackInfoFormatter.frameRate(23.976f));
        equal(null, PlaybackInfoFormatter.frameRate(Float.NaN));
        equal(null, PlaybackInfoFormatter.frameRate(0));
        equal(null, PlaybackInfoFormatter.sampleRate(-1));
        equal("44.1 kHz", PlaybackInfoFormatter.sampleRate(44100));
        equal("48.0 kHz", PlaybackInfoFormatter.sampleRate(48000));
        equal("video/hevc", PlaybackInfoFormatter.identifier("video/hevc"));
        equal("hvc1.2.4.L153.B0", PlaybackInfoFormatter.identifier("hvc1.2.4.L153.B0"));
        equal("matroska,webm", PlaybackInfoFormatter.identifier("matroska,webm"));
        equal(null, PlaybackInfoFormatter.identifier("https://server/video?api_key=secret"));
        equal(null, PlaybackInfoFormatter.identifier("/media/private/file.mkv"));
        equal(null, PlaybackInfoFormatter.identifier("codec\nAuthorization: secret"));
        equal(null, PlaybackInfoFormatter.identifier(""));
        equal(null, PlaybackInfoFormatter.identifier(null));
        equal(null, PlaybackInfoFormatter.identifier("a".repeat(81)));
        System.out.println("Playback information formatting: 25 checks passed");
    }

    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
