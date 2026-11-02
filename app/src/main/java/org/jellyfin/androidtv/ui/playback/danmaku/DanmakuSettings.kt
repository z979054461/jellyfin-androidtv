package org.jellyfin.androidtv.ui.playback.danmaku

/** Named, bounded user-selectable presets shared by the menu and renderer. */
object DanmakuSettings {
	const val DEFAULT_FONT = 1f
	const val DEFAULT_ROWS = 6
	const val DEFAULT_OPACITY = 0.8f
	const val DEFAULT_DURATION_MS = 8000L

	// Numeric values are the visible menu presets, not hidden calculations.
	@Suppress("MagicNumber")
	val fonts = floatArrayOf(0.8f, 1f, 1.2f)
	@Suppress("MagicNumber")
	val rows = intArrayOf(3, 6, 9)
	@Suppress("MagicNumber")
	val opacity = floatArrayOf(0.5f, 0.8f, 1f)
	@Suppress("MagicNumber")
	val durations = longArrayOf(6000L, 8000L, 10000L)
	@Suppress("MagicNumber")
	val offsets = intArrayOf(-5000, -1000, 0, 1000, 5000)
}
