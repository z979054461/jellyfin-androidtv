package org.jellyfin.androidtv.ui.playback.danmaku

/** Named, bounded user-selectable presets shared by the menu and renderer. */
object DanmakuSettings {
	const val DEFAULT_ENABLED = true
	const val DEFAULT_FONT = 0.8f
	const val DEFAULT_ROWS = 6
	const val DEFAULT_OPACITY = 0.8f
	const val DEFAULT_DURATION_MS = 8000L

	// Room for every selectable row/font option, without cross-group ID collisions.
	const val MENU_GROUP_STRIDE = 100

	// Numeric values are the visible menu presets, not hidden calculations.
	@Suppress("MagicNumber")
	val fontPercentages = (50..150 step 10).toList()
	val fonts = fontPercentages.map { it / 100f }.toFloatArray()
	@Suppress("MagicNumber")
	val rows = (1..20).toList().toIntArray()
	@Suppress("MagicNumber")
	val opacity = floatArrayOf(0.5f, 0.8f, 1f)
	@Suppress("MagicNumber")
	val durations = longArrayOf(6000L, 8000L, 10000L)
	@Suppress("MagicNumber")
	val offsets = intArrayOf(-5000, -1000, 0, 1000, 5000)
}
