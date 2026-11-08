package org.jellyfin.androidtv.update;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Bounded cache with current-screen pins and persistent installer read leases. */
public final class UpdateCache {
    public static final int MAX_FILES = 3;
    public static final long EXPIRY_MS = TimeUnit.DAYS.toMillis(7);
    private static final long RECENT_MS = TimeUnit.HOURS.toMillis(1);
    private static final Map<String, Integer> PINS = new HashMap<>();
    private UpdateCache() { }

    public static synchronized void pin(File file) {
        String path = file.getAbsolutePath();
        PINS.put(path, PINS.getOrDefault(path, 0) + 1);
    }

    public static synchronized void unpin(File file) {
        String path = file.getAbsolutePath();
        int count = PINS.getOrDefault(path, 0);
        if (count <= 1) PINS.remove(path); else PINS.put(path, count - 1);
    }

    /** Persist before handing a content URI to an installer, which may open it asynchronously. */
    public static synchronized void leaseForInstaller(File file, long now) throws IOException {
        File lease = new File(file.getPath() + ".lease");
        if (!lease.exists() && !lease.createNewFile()) throw new IOException("Cannot protect installer APK");
        if (!lease.setLastModified(now)) throw new IOException("Cannot timestamp installer APK lease");
    }

    public static synchronized File createPartial(File directory, String currentName, long now) throws IOException {
        File[] leases = directory.listFiles((dir, name) -> name.endsWith(".apk.lease"));
        if (leases != null) for (File lease : leases) {
            String apkPath = lease.getPath().substring(0, lease.getPath().length() - ".lease".length());
            if (!new File(apkPath).exists()) lease.delete();
        }
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".apk") || name.endsWith(".part"));
        if (files == null) throw new IOException("Cannot read update cache");
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        int count = files.length;
        for (File file : files) {
            if (protectedFile(file, currentName, now)) continue;
            if (now - file.lastModified() >= EXPIRY_MS || count >= MAX_FILES) {
                if (file.delete()) {
                    count--;
                    new File(file.getPath() + ".lease").delete();
                }
            }
        }
        // Never evict an active download, current candidate or pending installer to make space.
        if (count >= MAX_FILES) throw new IOException("Update cache is protected or full");
        return File.createTempFile("update-", ".part", directory);
    }

    private static boolean protectedFile(File file, String currentName, long now) {
        if (file.getName().equals(currentName) || PINS.containsKey(file.getAbsolutePath())) return true;
        if (now - file.lastModified() < RECENT_MS) return true;
        File lease = new File(file.getPath() + ".lease");
        return lease.exists() && now - lease.lastModified() < EXPIRY_MS;
    }
}
