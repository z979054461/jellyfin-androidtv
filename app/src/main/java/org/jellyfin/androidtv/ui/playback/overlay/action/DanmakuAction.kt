package org.jellyfin.androidtv.ui.playback.overlay.action

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.playback.danmaku.DanmakuSession
import org.jellyfin.androidtv.ui.playback.danmaku.DanmakuSettings
import org.jellyfin.androidtv.ui.playback.overlay.CustomPlaybackTransportControlGlue
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter

class DanmakuAction(context: Context, glue: CustomPlaybackTransportControlGlue) : CustomAction(context, glue) {
	private var popup: PopupMenu? = null
	private var helpDialog: AlertDialog? = null
	private var session: DanmakuSession? = null

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
		session = videoPlayerAdapter.masterOverlayFragment.danmakuSession
		val settings = context.getSharedPreferences(DanmakuSession.PREFERENCES, Context.MODE_PRIVATE)
		popup = PopupMenu(context, view, Gravity.END).apply {
			val status = menu.add(session?.statusText ?: context.getString(R.string.danmaku_status_waiting)).apply {
				isEnabled = false
			}
			session?.statusListener = { status.title = session?.statusText }
			menu.add(0, 0, 0, R.string.danmaku_enabled).apply {
				isCheckable = true
				isChecked = settings.getBoolean("enabled", DanmakuSettings.DEFAULT_ENABLED)
			}
			menu.add(GROUP_RETRY, GROUP_RETRY * GROUP_ID_STRIDE, 1, R.string.danmaku_retry).apply {
				isEnabled = settings.getBoolean("enabled", DanmakuSettings.DEFAULT_ENABLED)
			}
			menu.add(GROUP_HELP, GROUP_HELP * GROUP_ID_STRIDE, 2, R.string.danmaku_help)
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
				R.string.danmaku_font, GROUP_FONT, DanmakuSettings.fontPercentages.map { "$it%" },
				fonts.indexOfFirst { it == settings.getFloat("font", DanmakuSettings.DEFAULT_FONT) }
			)
			options(R.string.danmaku_rows, GROUP_ROWS, rows.map { it.toString() }, rows.indexOf(settings.getInt("rows", DanmakuSettings.DEFAULT_ROWS)))
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
				if (item.groupId == GROUP_RETRY) {
					session?.retry()
					return@setOnMenuItemClickListener true
				}
				if (item.groupId == GROUP_HELP) {
					helpDialog = AlertDialog.Builder(context)
						.setTitle(R.string.danmaku_help)
						.setMessage(R.string.danmaku_help_text)
						.setPositiveButton(android.R.string.ok, null)
						.create().also { dialog ->
							dialog.setOnDismissListener { helpDialog = null }
							dialog.show()
						}
					return@setOnMenuItemClickListener true
				}
				val index = item.itemId % GROUP_ID_STRIDE
				settings.edit {
					when (item.groupId) {
						0 -> putBoolean("enabled", !settings.getBoolean("enabled", DanmakuSettings.DEFAULT_ENABLED))
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
				session?.statusListener = null
				session = null
			}
		}
		popup?.show()
	}

	fun dismissPopup() {
		helpDialog?.dismiss()
		helpDialog = null
		session?.statusListener = null
		session = null
		popup?.dismiss()
		popup = null
	}

	companion object {
		private const val GROUP_FONT = 1
		private const val GROUP_ROWS = 2
		private const val GROUP_OPACITY = 3
		private const val GROUP_DURATION = 4
		private const val GROUP_OFFSET = 5
		private const val GROUP_RETRY = 6
		private const val GROUP_HELP = 7
		private const val GROUP_ID_STRIDE = DanmakuSettings.MENU_GROUP_STRIDE
	}
}
