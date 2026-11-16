import com.android.apksig.ApkVerifier;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Check APK cryptographic identity through apksig, not its changing CLI labels. */
class VerifyReleaseApk {
    public static void main(String[] args) {
        try {
            if (args.length != 2) fail("expected APK and public fingerprint paths");
            String expected = Files.readString(Path.of(args[1])).trim();
            if (!expected.matches("[a-f0-9]{64}")) fail("invalid public certificate pin");
            var result = new ApkVerifier.Builder(new File(args[0])).build().verify();
            if (!result.isVerified()) fail("cryptographic signature verification failed");
            var certificates = result.getSignerCertificates();
            if (certificates.size() != 1) fail("expected exactly one signing certificate");
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(certificates.getFirst().getEncoded()));
            if (!actual.equals(expected)) fail("certificate mismatch; public SHA-256=" + actual);
            System.out.println("APK signature: verified one signer; public SHA-256=" + actual);
        } catch (Exception error) {
            // Exceptions can contain paths or certificate subject data. Do not dump them.
            fail("unable to inspect APK signature");
        }
    }

    private static void fail(String message) {
        System.err.println("APK signature: " + message);
        System.exit(1);
    }
}
