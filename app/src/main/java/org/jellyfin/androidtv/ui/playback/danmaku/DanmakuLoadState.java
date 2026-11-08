package org.jellyfin.androidtv.ui.playback.danmaku;

/** Bounded, non-sensitive load outcomes. Never expose authenticated URLs or response bodies. */
public enum DanmakuLoadState {
    OFF, WAITING, UNSUPPORTED, LOADING, READY, EMPTY, NOT_FOUND, UNAUTHORIZED, INVALID_DATA, NETWORK_ERROR, HTTP_ERROR;

    public static DanmakuLoadState fromHttpStatus(int code) {
        if (code == 401 || code == 403) return UNAUTHORIZED;
        // 404 can mean either missing XML for this item or an absent plugin route.
        if (code == 404) return NOT_FOUND;
        return HTTP_ERROR;
    }
}
