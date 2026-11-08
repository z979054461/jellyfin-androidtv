package org.jellyfin.androidtv.ui.playback.network;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

/** One listener per media session, attached only to that session's data-source factory. */
@OptIn(markerClass = UnstableApi.class)
public final class PlayerTransferListener implements TransferListener {
    private final NetworkTransferMeter meter;

    public PlayerTransferListener(NetworkTransferMeter meter) {
        this.meter = meter;
    }

    @Override
    public void onTransferInitializing(@NonNull DataSource source, @NonNull DataSpec dataSpec, boolean isNetwork) {
        // An attempted connection is not an observed transfer.
    }

    @Override
    public void onTransferStart(@NonNull DataSource source, @NonNull DataSpec dataSpec, boolean isNetwork) {
        meter.onTransferStart(isNetwork);
    }

    @Override
    public void onBytesTransferred(@NonNull DataSource source, @NonNull DataSpec dataSpec, boolean isNetwork, int bytesTransferred) {
        meter.onBytesTransferred(isNetwork, bytesTransferred);
    }

    @Override
    public void onTransferEnd(@NonNull DataSource source, @NonNull DataSpec dataSpec, boolean isNetwork) {
        meter.onTransferEnd(isNetwork);
    }
}
