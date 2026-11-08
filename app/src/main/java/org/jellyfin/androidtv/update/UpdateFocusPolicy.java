package org.jellyfin.androidtv.update;

/** Focus a reachable action after the updater replaces its preference views. */
public final class UpdateFocusPolicy {
    private UpdateFocusPolicy() {}
    public static final String CHECK = "update_check";
    public static final String DOWNLOAD = "update_download";
    public static final String INSTALL = "update_install";
    public static final String PERMISSION = "update_permission";
    public static final String CANCEL = "update_cancel";

    public static String preferred(boolean busy, boolean verifiedApk, boolean candidate) {
        if (busy) return CANCEL;
        if (verifiedApk) return INSTALL;
        if (candidate) return DOWNLOAD;
        return CHECK;
    }
}
