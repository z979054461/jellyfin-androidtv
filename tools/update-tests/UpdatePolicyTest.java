import org.jellyfin.androidtv.update.UpdatePolicy;

public final class UpdatePolicyTest {
    private static int assertions;
    private static void check(boolean value) {
        assertions++;
        if (!value) throw new AssertionError("Assertion " + assertions + " failed");
    }
    public static void main(String[] args) {
        check(UpdatePolicy.versionCode("jellyfin-danmaku-191004.apk") == 191004L);
        for (String name : new String[] {"app-release.apk", "jellyfin-danmaku-0.apk", "jellyfin-danmaku-01.apk",
                "jellyfin-danmaku-191004-debug.apk", "jellyfin-danmaku-191004.apks", "jellyfin-danmaku-999999999999999999999.apk"}) {
            check(UpdatePolicy.versionCode(name) == null);
        }
        check(UpdatePolicy.isNewer(191004, 191003));
        check(!UpdatePolicy.isNewer(191003, 191003));
        check(!UpdatePolicy.isNewer(191002, 191003));
        check(!UpdatePolicy.isNewer((long) Integer.MAX_VALUE + 1, 191003));
        check(UpdatePolicy.sha256("sha256:" + "AB".repeat(32)).equals("ab".repeat(32)));
        for (String digest : new String[] {"", "null", "sha256:1234", "sha512:" + "a".repeat(64), "sha256:" + "g".repeat(64)}) {
            check(UpdatePolicy.sha256(digest) == null);
        }
        check(UpdatePolicy.trustedDownloadUrl("https://github.com/z979054461/jellyfin-androidtv/releases/download/v1/jellyfin-danmaku-191004.apk", true));
        check(!UpdatePolicy.trustedDownloadUrl("https://release-assets.githubusercontent.com/file", true));
        check(UpdatePolicy.trustedDownloadUrl("https://release-assets.githubusercontent.com/file?signature=value"));
        check(UpdatePolicy.trustedDownloadUrl("https://objects.githubusercontent.com/file"));
        for (String url : new String[] {"https://github.com/other/repo/releases/download/v1/app.apk", "http://release-assets.githubusercontent.com/a",
                "https://release-assets.githubusercontent.com.evil.example/a", "https://github.com@evil.example/a",
                "https://user@release-assets.githubusercontent.com/a", "https://release-assets.githubusercontent.com:444/a",
                "file:///cache/update.apk", "https://127.0.0.1/a", "https://github.com/z979054461/jellyfin-androidtv/releases/download/../a",
                "https://github.com/z979054461/jellyfin-androidtv/releases/download/%2e%2e/a",
                "https://release-assets.githubusercontent.com/a#fragment", "not a URL"}) {
            check(!UpdatePolicy.trustedDownloadUrl(url));
        }
        check(UpdatePolicy.sizeLabel(1572864).equals("1.5 MiB"));
        check(UpdatePolicy.sizeLabel(UpdatePolicy.MAX_APK_BYTES).equals("200.0 MiB"));
        System.out.println("UpdatePolicyTest: " + assertions + " assertions passed");
    }
}
