package org.jellyfin.androidtv.ui.playback.danmaku

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.lifecycle.LifecycleCoroutineScope
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.koin.java.KoinJavaComponent.get
import timber.log.Timber

/** View-lifecycle owned. Missing/unsupported plugins never interrupt video playback. */
class DanmakuSession(
	private val view: DanmakuView,
	private val controller: PlaybackController,
	private val api: ApiClient,
	private val scope: LifecycleCoroutineScope,
) {
	private val preferences = view.context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
	private val handler = Handler(Looper.getMainLooper())
	private val client = get<OkHttpFactory>(OkHttpFactory::class.java)
		.createClient(get<HttpClientOptions>(HttpClientOptions::class.java))
		.newBuilder()
		.callTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
		.followRedirects(false)
		.followSslRedirects(false)
		.build()
	private var itemId: UUID? = null
	private var loading: Job? = null
	private var generation = 0
	private var closed = false
	private var enabled = false
	private var state = DanmakuLoadState.OFF
	private var commentCount = 0
	var statusListener: (() -> Unit)? = null
	val statusText: String
		get() = view.context.getString(when (state) {
			DanmakuLoadState.OFF -> R.string.danmaku_status_off
			DanmakuLoadState.WAITING -> R.string.danmaku_status_waiting
			DanmakuLoadState.UNSUPPORTED -> R.string.danmaku_status_unsupported
			DanmakuLoadState.LOADING -> R.string.danmaku_status_loading
			DanmakuLoadState.READY -> R.string.danmaku_status_ready
			DanmakuLoadState.EMPTY -> R.string.danmaku_status_empty
			DanmakuLoadState.NOT_FOUND -> R.string.danmaku_status_not_found
			DanmakuLoadState.UNAUTHORIZED -> R.string.danmaku_status_unauthorized
			DanmakuLoadState.INVALID_DATA -> R.string.danmaku_status_invalid
			DanmakuLoadState.NETWORK_ERROR -> R.string.danmaku_status_network
			DanmakuLoadState.HTTP_ERROR -> R.string.danmaku_status_http
		}, commentCount)

	private fun updateStatus(value: DanmakuLoadState) {
		state = value
		statusListener?.invoke()
	}

	private fun idleStatus() = when {
		!enabled -> DanmakuLoadState.OFF
		controller.currentlyPlayingItem == null -> DanmakuLoadState.WAITING
		else -> DanmakuLoadState.UNSUPPORTED
	}

	fun retry() {
		if (!closed) load()
	}
	private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> applySettings() }
	private val frame = object : Runnable {
		override fun run() {
			if (closed) return
			val id = if (controller.isDanmakuSupported) controller.currentlyPlayingItem?.id else null
			if (id != itemId) {
				itemId = id
				load()
			} else if (id == null && state != idleStatus()) {
				updateStatus(idleStatus())
			}
			if (enabled) view.positionMs = controller.danmakuPositionMs + preferences.getInt("offset", 0)
			handler.postDelayed(this, if (enabled && view.hasComments() && controller.isPlaying) FRAME_DELAY_MS else IDLE_DELAY_MS)
		}
	}

	init {
		preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
		applySettings()
		handler.post(frame)
	}

	private fun applySettings() {
		val previouslyEnabled = enabled
		enabled = preferences.getBoolean("enabled", DanmakuSettings.DEFAULT_ENABLED)
		view.visibility = if (enabled) View.VISIBLE else View.GONE
		view.fontScale = preferences.getFloat("font", DanmakuSettings.DEFAULT_FONT)
			.coerceIn(DanmakuSettings.fonts.first(), DanmakuSettings.fonts.last())
		view.rows = preferences.getInt("rows", DanmakuSettings.DEFAULT_ROWS)
			.coerceIn(DanmakuSettings.rows.first(), DanmakuSettings.rows.last())
		view.opacity = preferences.getFloat("opacity", DanmakuSettings.DEFAULT_OPACITY)
			.coerceIn(DanmakuSettings.opacity.first(), DanmakuSettings.opacity.last())
		view.durationMs = preferences.getLong("duration", DanmakuSettings.DEFAULT_DURATION_MS)
			.coerceIn(DanmakuSettings.durations.first(), DanmakuSettings.durations.last())
		if (enabled != previouslyEnabled) load()
	}

	private fun load() {
		loading?.cancel()
		val requestGeneration = ++generation
		view.setComments(emptyList())
		commentCount = 0
		val id = itemId
		if (!enabled || id == null) {
			updateStatus(idleStatus())
			return
		}
		updateStatus(DanmakuLoadState.LOADING)
		loading = scope.launch {
			try {
				val bytes = fetch(id)
				val comments = withContext(Dispatchers.Default) {
					try {
						DanmakuParser.parse(bytes)
					} catch (_: IOException) {
						throw LoadException(DanmakuLoadState.INVALID_DATA)
					}
				}
				if (!closed && generation == requestGeneration && itemId == id) {
					view.setComments(comments)
					commentCount = comments.size
					updateStatus(if (comments.isEmpty()) DanmakuLoadState.EMPTY else DanmakuLoadState.READY)
				}
			} catch (cancelled: CancellationException) {
				throw cancelled
			} catch (error: Exception) {
				if (!closed && generation == requestGeneration && itemId == id) {
					updateStatus((error as? LoadException)?.state ?: DanmakuLoadState.NETWORK_ERROR)
				}
				// Do not log authenticated URLs, XML, or comment text.
				Timber.i("Danmaku unavailable; continuing normal video playback")
			}
		}
	}

	/** Bounded streaming read; cancellation closes the call, including on episode changes. */
	private suspend fun fetch(id: UUID): ByteArray = suspendCancellableCoroutine { continuation ->
		val request = Request.Builder()
			.url(api.createUrl("/api/danmu/$id/raw"))
			.header("Authorization", AuthorizationHeaderBuilder.buildHeader(
				api.clientInfo.name, api.clientInfo.version, api.deviceInfo.id, api.deviceInfo.name, api.accessToken
			))
			.get()
			.build()
		val call = client.newCall(request)
		continuation.invokeOnCancellation { call.cancel() }
		call.enqueue(object : Callback {
			override fun onFailure(call: Call, e: IOException) {
				if (continuation.isActive) continuation.resumeWithException(e)
			}

			override fun onResponse(call: Call, response: Response) {
				try {
					val bytes = response.use {
						if (!it.isSuccessful) throw LoadException(DanmakuLoadState.fromHttpStatus(it.code))
						val body = it.body ?: throw LoadException(DanmakuLoadState.INVALID_DATA)
						if (body.contentLength() > DanmakuParser.MAX_BYTES) throw LoadException(DanmakuLoadState.INVALID_DATA)
						body.byteStream().use { input ->
							val output = ByteArrayOutputStream()
							val buffer = ByteArray(READ_BUFFER_BYTES)
							while (true) {
								if (!continuation.isActive) throw IOException("Cancelled")
								val count = input.read(buffer)
								if (count < 0) break
								if (output.size() + count > DanmakuParser.MAX_BYTES) throw LoadException(DanmakuLoadState.INVALID_DATA)
								output.write(buffer, 0, count)
							}
							output.toByteArray()
						}
					}
					if (continuation.isActive) continuation.resume(bytes)
				} catch (e: IOException) {
					if (continuation.isActive) continuation.resumeWithException(e)
				}
			}
		})
	}

	fun close() {
		closed = true
		statusListener = null
		generation++
		loading?.cancel()
		handler.removeCallbacks(frame)
		preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
		view.setComments(emptyList())
	}

	private class LoadException(val state: DanmakuLoadState) : IOException("Danmaku unavailable")

	companion object {
		const val PREFERENCES = "native_danmaku"
		private const val HTTP_TIMEOUT_SECONDS = 15L
		private const val FRAME_DELAY_MS = 16L
		private const val IDLE_DELAY_MS = 250L
		private const val READ_BUFFER_BYTES = 8192
	}
}
