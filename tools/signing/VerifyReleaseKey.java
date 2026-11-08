import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;

/** Verify the one expected release signer without printing or exporting private material. */
class VerifyReleaseKey {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Expected store, public fingerprint and alias-output paths");
        char[] storePassword = requiredEnv("KEYSTORE_PASSWORD").toCharArray();
        char[] keyPassword = requiredEnv("SIGNING_KEY_PASSWORD").toCharArray();
        try {
            KeyStore store = KeyStore.getInstance("PKCS12");
            try (var input = Files.newInputStream(Path.of(args[0]))) { store.load(input, storePassword); }
            String expected = Files.readString(Path.of(args[1])).trim();
            if (!expected.matches("[a-f0-9]{64}")) throw new IllegalStateException("Invalid public certificate fingerprint");
            ArrayList<String> matches = new ArrayList<>();
            var aliases = store.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (!store.isKeyEntry(alias) || !(store.getCertificate(alias) instanceof X509Certificate certificate)) continue;
                String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
                if (!actual.equals(expected)) continue;
                certificate.checkValidity();
                var key = store.getKey(alias, keyPassword);
                if (!(key instanceof PrivateKey)) throw new IllegalStateException("Expected private signing entry");
                byte[] challenge = "Jellyfin Danmaku release signing verification".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                Signature signer = Signature.getInstance("SHA256withRSA");
                signer.initSign((PrivateKey) key);
                signer.update(challenge);
                byte[] signed = signer.sign();
                signer.initVerify(certificate.getPublicKey());
                signer.update(challenge);
                if (!signer.verify(signed)) throw new IllegalStateException("Certificate and signing key do not match");
                matches.add(alias);
            }
            if (matches.size() != 1) throw new IllegalStateException("Expected exactly one matching release signing key; found " + matches.size());
            String alias = matches.getFirst();
            if (alias.contains("\n") || alias.contains("\r")) throw new IllegalStateException("Invalid key alias");
            // PKCS12 friendlyName mapping differs between Windows/OpenSSL and Java. Use Java's verified alias.
            Files.writeString(Path.of(args[2]), alias);
            System.out.println("Verified release private-key entry and pinned public certificate: " + expected);
        } finally {
            Arrays.fill(storePassword, '\0');
            Arrays.fill(keyPassword, '\0');
        }
    }
    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) throw new IllegalStateException("Missing signing configuration: " + name);
        return value;
    }
}
