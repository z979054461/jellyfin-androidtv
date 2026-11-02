package org.jellyfin.androidtv.ui.playback.overlay.action

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import androidx.core.content.edit
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.playback.danmaku.DanmakuSession
import org.jellyfin.androidtv.ui.playback.danmaku.DanmakuSettings
import org.jellyfin.androidtv.ui.playback.overlay.CustomPlaybackTransportControlGlue
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter

class DanmakuAction(context: Context, glue: CustomPlaybackTransportControlGlue) : CustomAction(context, glue) {
	private var popup: PopupMenu? = null

	init {
		initializeWithIcon(R.drawable.ic_danmaku)
	}

	override fun handleClickAction(
		playbackController: PlaybackController,
		videoPlayerAdapter: VideoPlayerAdapter,
		context: Context,
		view: View,
	) {
		dismissPopup()
		videoPlayerAdapter.leanbackOverlayFragment.setFading(false)
		val settings = context.getSharedPreferences(DanmakuSession.PREFERENCES, Context.MODE_PRIVATE)
		popup = PopupMenu(context, view, Gravity.END).apply {
			menu.add(0, 0, 0, R.string.danmaku_enabled).apply {
				isCheckable = true
				isChecked = settings.getBoolean("enabled", false)
			}
			val fonts = DanmakuSettings.fonts
			val rows = DanmakuSettings.rows
			val opacity = DanmakuSettings.opacity
			val duration = DanmakuSettings.durations
			val offset = DanmakuSettings.offsets
			fun options(title: Int, group: Int, labels: List<String>, selected: Int) {
				val submenu = menu.addSubMenu(title)
				labels.forEachIndexed { index, label ->
					submenu.add(group, group * GROUP_ID_STRIDE + index, index, label).apply {
						isCheckable = true
						isChecked = index == selected
					}
				}
				submenu.setGroupCheckable(group, true, true)
			}
			options(
				R.string.danmaku_font, GROUP_FONT, listOf("80%", "100%", "120%"),
				fonts.indexOfFirst { it == settings.getFloat("font", 1f) }
			)
			options(R.string.danmaku_rows, GROUP_ROWS, listOf("3", "6", "9"), rows.indexOf(settings.getInt("rows", DanmakuSettings.DEFAULT_ROWS)))
			options(
				R.string.danmaku_opacity, GROUP_OPACITY, listOf("50%", "80%", "100%"),
				opacity.indexOfFirst { it == settings.getFloat("opacity", DanmakuSettings.DEFAULT_OPACITY) }
			)
			options(
				R.string.danmaku_duration, GROUP_DURATION, listOf("6 s", "8 s", "10 s"),
				duration.indexOf(settings.getLong("duration", DanmakuSettings.DEFAULT_DURATION_MS))
			)
			options(
				R.string.danmaku_offset, GROUP_OFFSET, listOf("-5 s", "-1 s", "0 s", "+1 s", "+5 s"),
				offset.indexOf(settings.getInt("offset", 0))
			)
			setOnMenuItemClickListener { item ->
				if (item.hasSubMenu()) return@setOnMenuItemClickListener false
				val index = item.itemId % GROUP_ID_STRIDE
				settings.edit {
					when (item.groupId) {
						0 -> putBoolean("enabled", !settings.getBoolean("enabled", false))
						GROUP_FONT -> putFloat("font", fonts[index])
						GROUP_ROWS -> putInt("rows", rows[index])
						GROUP_OPACITY -> putFloat("opacity", opacity[index])
						GROUP_DURATION -> putLong("duration", duration[index])
						GROUP_OFFSET -> putInt("offset", offset[index])
					}
				}
				true
			}
			setOnDismissListener {
				videoPlayerAdapter.leanbackOverlayFragment.setFading(true)
				popup = null
			}
		}
		popup?.show()
	}

	fun dismissPopup() {
		popup?.dismiss()
		popup = null
	}

	companion object {
		private const val GROUP_FONT = 1
		private const val GROUP_ROWS = 2
		private const val GROUP_OPACITY = 3
		private const val GROUP_DURATION = 4
		private const val GROUP_OFFSET = 5
		private const val GROUP_ID_STRIDE = 10
	}
}
