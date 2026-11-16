package org.jellyfin.androidtv.ui.playback.overlay.action

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.media3.common.Format
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.playback.overlay.CustomPlaybackTransportControlGlue
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod

/** Read-only local snapshot, refreshed only while visible. Does not pause playback. */
class PlaybackInfoAction(context: Context, glue: CustomPlaybackTransportControlGlue) : CustomAction(context, glue) {
	private var dialog: AlertDialog? = null
	private val handler = Handler(Looper.getMainLooper())
	private var refresh: Runnable? = null

	init {
		initializeWithIcon(R.drawable.ic_playback_info)
	}

	override fun handleClickAction(
		playbackController: PlaybackController,
		videoPlayerAdapter: VideoPlayerAdapter,
		context: Context,
		view: View,
	) {
		dismissPopup()
		if (!view.isAttachedToWindow || !playbackController.hasInitializedVideoManager()) return
		val itemId = playbackController.currentStreamInfo?.itemId
		val text = TextView(context).apply {
			textSize = 16f
			val padding = (24 * resources.displayMetrics.density).toInt()
			setPadding(padding, padding / 2, padding, padding / 2)
			setTextIsSelectable(false)
		}
		val scroll = ScrollView(context).apply { addView(text) }
		val opened = AlertDialog.Builder(context)
			.setTitle(R.string.custom_playback_info_title)
			.setView(scroll)
			.setPositiveButton(R.string.custom_playback_info_close, null)
			.create()
		dialog = opened
		videoPlayerAdapter.leanbackOverlayFragment.setFading(false)
		opened.setOnDismissListener {
			refresh?.let(handler::removeCallbacks)
			refresh = null
			dialog = null
			videoPlayerAdapter.leanbackOverlayFragment.setFading(true)
		}
		text.text = snapshot(context, playbackController)
		opened.show()
		// Keep the panel compact on TV and allow D-pad scrolling on smaller displays.
		opened.window?.setLayout(
			minOf((600 * context.resources.displayMetrics.density).toInt(), (context.resources.displayMetrics.widthPixels * .85f).toInt()),
			(context.resources.displayMetrics.heightPixels * .8f).toInt(),
		)
		opened.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus()
		refresh = object : Runnable {
			override fun run() {
				if (dialog !== opened || !opened.isShowing) return
				if (!view.isAttachedToWindow || !playbackController.hasInitializedVideoManager() ||
					playbackController.currentStreamInfo?.itemId != itemId
				) {
					dismissPopup()
					return
				}
				val updated = snapshot(context, playbackController)
				if (text.text.toString() != updated) text.text = updated
				handler.postDelayed(this, 1000)
			}
		}.also { handler.postDelayed(it, 1000) }
	}

	fun dismissPopup() {
		refresh?.let(handler::removeCallbacks)
		refresh = null
		dialog?.dismiss()
		dialog = null
	}

	private fun snapshot(context: Context, controller: PlaybackController): String {
		val info = controller.currentStreamInfo
		// Do not fall back to a previous item's or guessed media source during a transition.
		val source = info?.mediaSource
		val streams = source?.mediaStreams.orEmpty()
		val video = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
		// SDK stream indices are identifiers, not list positions. Never guess the first audio track.
		val audio = streams.firstOrNull { it.type == MediaStreamType.AUDIO && it.index == controller.playbackInfoAudioStreamIndex }
		val unknown = context.getString(R.string.custom_playback_info_unavailable)
		fun row(label: Int, value: String?) = context.getString(label) + ": " + (value ?: unknown)
		val method = when (info?.playMethod) {
			PlayMethod.DIRECT_PLAY -> R.string.custom_playback_info_direct_play
			PlayMethod.DIRECT_STREAM -> R.string.custom_playback_info_direct_stream
			PlayMethod.TRANSCODE -> R.string.custom_playback_info_transcode
			else -> R.string.custom_playback_info_unavailable
		}
		return listOf(
			row(R.string.custom_playback_info_method, context.getString(method)),
			row(R.string.custom_playback_info_container, PlaybackInfoFormatter.identifier(info?.container)),
			"",
			context.getString(R.string.custom_playback_info_source),
			row(R.string.custom_playback_info_container, PlaybackInfoFormatter.identifier(source?.container)),
			row(R.string.custom_playback_info_video, sourceVideo(video)),
			row(R.string.custom_playback_info_audio, sourceAudio(context, audio)),
			"",
			context.getString(R.string.custom_playback_info_player),
			row(R.string.custom_playback_info_video, playerFormat(context, controller.playbackInfoVideoFormat, true)),
			row(R.string.custom_playback_info_audio, playerFormat(context, controller.playbackInfoAudioFormat, false)),
			"",
			context.getString(R.string.custom_playback_info_note),
		).joinToString("\n")
	}

	private fun sourceVideo(stream: MediaStream?): String? = join(
		PlaybackInfoFormatter.identifier(stream?.codec),
		PlaybackInfoFormatter.dimensions(stream?.width, stream?.height),
		PlaybackInfoFormatter.frameRate(stream?.realFrameRate ?: stream?.averageFrameRate),
		PlaybackInfoFormatter.bitrate(stream?.bitRate),
	)

	private fun sourceAudio(context: Context, stream: MediaStream?): String? = join(
		PlaybackInfoFormatter.identifier(stream?.codec),
		channels(context, stream?.channels),
		PlaybackInfoFormatter.sampleRate(stream?.sampleRate),
		PlaybackInfoFormatter.bitrate(stream?.bitRate),
	)

	private fun playerFormat(context: Context, format: Format?, video: Boolean): String? {
		if (format == null) return null
		return join(
			PlaybackInfoFormatter.identifier(format.sampleMimeType),
			PlaybackInfoFormatter.identifier(format.codecs),
			if (video) PlaybackInfoFormatter.dimensions(format.width, format.height) else channels(context, format.channelCount),
			if (video) PlaybackInfoFormatter.frameRate(format.frameRate) else PlaybackInfoFormatter.sampleRate(format.sampleRate),
			PlaybackInfoFormatter.bitrate(format.bitrate),
		)
	}

	private fun channels(context: Context, count: Int?): String? =
		count?.takeIf { it > 0 }?.let { context.getString(R.string.custom_playback_info_channels, it) }

	private fun join(vararg values: String?): String? = values.filterNotNull().joinToString(" · ").ifEmpty { null }
}
