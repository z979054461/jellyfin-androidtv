import org.jellyfin.androidtv.update.UpdateCache;
import org.jellyfin.androidtv.update.UpdatePolicy;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

public final class UpdateCacheTest {
    private static int assertions;
    private static void check(boolean value) {
        assertions++;
        if (!value) throw new AssertionError("Assertion " + assertions + " failed");
    }
    private static File create(File dir, String name, long timestamp) throws IOException {
        File file = new File(dir, name);
        check(file.createNewFile());
        check(file.setLastModified(timestamp));
        return file;
    }
    public static void main(String[] args) throws Exception {
        // Invalid candidates cannot hide a valid newer candidate, but all-invalid is not "latest".
        UpdatePolicy.requireVerifiableCandidate(true, true);
        UpdatePolicy.requireVerifiableCandidate(true, false);
        UpdatePolicy.requireVerifiableCandidate(false, false);
        boolean failed = false;
        try { UpdatePolicy.requireVerifiableCandidate(false, true); } catch (IllegalStateException expected) { failed = true; }
        check(failed);
        long now = System.currentTimeMillis();
        long old = now - TimeUnit.DAYS.toMillis(8);
        File dir = Files.createTempDirectory("update-cache-tests").toFile();
        try {
            File current = create(dir, "current.apk", old);
            File installer = create(dir, "installer.apk", old);
            File expired = create(dir, "expired.apk", old);
            UpdateCache.pin(current);
            UpdateCache.leaseForInstaller(installer, now);
            File partial = UpdateCache.createPartial(dir, "next.apk", now);
            check(current.exists());
            check(installer.exists());
            check(!expired.exists());
            check(partial.exists());
            check(dir.listFiles((d, n) -> n.endsWith(".apk") || n.endsWith(".part")).length == UpdateCache.MAX_FILES);
            failed = false;
            try { UpdateCache.createPartial(dir, "next.apk", now); } catch (IOException expected) { failed = true; }
            check(failed); // No eviction of current, pending installer or recent active partial.
            partial.delete();
            UpdateCache.unpin(current);
            File next = UpdateCache.createPartial(dir, "next.apk", now);
            check(!current.exists());
            check(installer.exists());
            next.delete();
            File later = UpdateCache.createPartial(dir, "next.apk", now + UpdateCache.EXPIRY_MS + 1);
            check(!installer.exists());
            check(!new File(installer.getPath() + ".lease").exists());
            later.delete();
            // Even a non-pinned old file with the current candidate name is never pruned.
            File selected = create(dir, "selected.apk", old);
            File reserve = UpdateCache.createPartial(dir, selected.getName(), now);
            check(selected.exists());
            reserve.delete();
            selected.delete();
            // At capacity, remove the oldest unprotected APK before its seven-day expiration.
            File oldest = create(dir, "oldest.apk", now - TimeUnit.DAYS.toMillis(3));
            File middle = create(dir, "middle.apk", now - TimeUnit.DAYS.toMillis(2));
            File newest = create(dir, "newest.apk", now - TimeUnit.DAYS.toMillis(1));
            File room = UpdateCache.createPartial(dir, "next.apk", now);
            check(!oldest.exists());
            check(middle.exists() && newest.exists() && room.exists());
        } finally {
            for (File file : dir.listFiles()) file.delete();
            dir.delete();
        }
        System.out.println("UpdateCacheTest: " + assertions + " assertions passed");
    }
}
