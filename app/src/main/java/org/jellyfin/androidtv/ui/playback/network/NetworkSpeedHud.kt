package org.jellyfin.androidtv.ui.playback.network

import android.os.Handler
import android.os.Looper
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.ClockUserView
import org.jellyfin.androidtv.ui.playback.PlaybackController

/** One UI timer while controls are visible; observes the player without making requests. */
class NetworkSpeedHud(
    private val view: ClockUserView,
    private val controller: PlaybackController,
) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private var visible = false
    private var closed = false
    private val update = object : Runnable {
        override fun run() {
            if (closed || !visible) return
            render()
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    fun setVisible(value: Boolean) {
        handler.removeCallbacks(update)
        visible = value && !closed
        if (visible) update.run()
    }

    private fun render() {
        val snapshot = controller.networkTransferSnapshot
        val rate = snapshot?.bytesPerSecond ?: 0L
        val observed = snapshot?.isNetworkObserved == true && snapshot.isMeasurementReady
        val state = NetworkSpeedLabel.classify(
            controller.isNetworkPlaybackActive && snapshot?.isSupported == true,
            snapshot?.state == NetworkTransferMeter.State.LOCAL,
            rate,
            observed,
            controller.isNetworkPlaybackPaused,
            controller.isNetworkLoading,
            controller.networkBufferedDurationMs,
        )
        val stateResource = when (state) {
            NetworkSpeedLabel.State.WAITING -> R.string.network_rate_waiting
            NetworkSpeedLabel.State.BUFFERED -> R.string.network_rate_buffered
            NetworkSpeedLabel.State.PAUSED -> R.string.network_rate_paused
            NetworkSpeedLabel.State.IDLE -> R.string.network_rate_idle
            NetworkSpeedLabel.State.LOCAL -> R.string.network_rate_local
            NetworkSpeedLabel.State.LOCAL_PAUSED -> R.string.network_rate_local_paused
            else -> null
        }
        val context = view.context
        val text = if (state == NetworkSpeedLabel.State.LOCAL || state == NetworkSpeedLabel.State.LOCAL_PAUSED) {
            context.getString(requireNotNull(stateResource))
        } else {
            val formatted = NetworkSpeedLabel.formatRate(rate, observed)
            if (stateResource == null) context.getString(R.string.network_rate_value, formatted)
            else context.getString(R.string.network_rate_with_state, formatted, context.getString(stateResource))
        }
        view.setPlaybackNetworkStatus(text)
    }

    override fun close() {
        closed = true
        setVisible(false)
        view.setPlaybackNetworkStatus(null)
    }

    private companion object {
        const val REFRESH_MS = 1_000L
    }
}
