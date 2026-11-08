package org.jellyfin.androidtv.update;

/** Main-thread, one-shot authorization for a user-started operation, invalidated on leaving. */
public final class InstallerHandoffGate {
    private long generation;
    private boolean consumed = true;

    public long begin() {
        consumed = false;
        return ++generation;
    }

    public void invalidate() {
        ++generation;
        consumed = true;
    }

    public boolean claim(long operation) {
        if (operation != generation || consumed) return false;
        consumed = true;
        return true;
    }
}
