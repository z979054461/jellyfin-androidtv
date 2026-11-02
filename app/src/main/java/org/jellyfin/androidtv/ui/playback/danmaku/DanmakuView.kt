package org.jellyfin.androidtv.ui.playback.danmaku

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** A non-interactive overlay. Every frame is derived from the player's media clock, not wall time. */
class DanmakuView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
	private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
	private var comments: List<DanmakuComment> = emptyList()
	private var timeline: DanmakuTimeline? = null
	var positionMs: Long = 0
		set(value) {
			if (field == value) return
			field = value
			invalidate()
		}
	var fontScale = DanmakuSettings.DEFAULT_FONT
		set(value) {
			if (field == value) return
			field = value
			rebuild()
		}
	var opacity = DanmakuSettings.DEFAULT_OPACITY
	var durationMs = DanmakuSettings.DEFAULT_DURATION_MS
		set(value) {
			if (field == value) return
			field = value
			rebuild()
		}
	var rows = DanmakuSettings.DEFAULT_ROWS
		set(value) {
			if (field == value) return
			field = value
			rebuild()
		}

	init {
		isClickable = false
		isFocusable = false
		importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
	}

	fun hasComments() = comments.isNotEmpty()

	fun setComments(value: List<DanmakuComment>) {
		comments = value
		rebuild()
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = rebuild()

	private fun rebuild() {
		if (width <= 0 || height <= 0) return
		paint.textSize = DEFAULT_FONT_SP * resources.displayMetrics.scaledDensity * fontScale
		val laneHeight = max(1, (paint.fontSpacing * LINE_SPACING).toInt())
		// Keep the lower 30% free for normal subtitles and transport controls.
		val lanes = min(rows, max(1, (height * AVAILABLE_HEIGHT / laneHeight).toInt()))
		timeline = DanmakuTimeline(comments, width, laneHeight, lanes, durationMs) { paint.measureText(it) }
		invalidate()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		val current = timeline ?: return
		paint.alpha = (opacity * MAX_ALPHA).toInt()
		for (placement in current.frame(positionMs)) {
			paint.style = Paint.Style.STROKE
			paint.strokeWidth = resources.displayMetrics.density * 2
			paint.color = Color.BLACK
			paint.alpha = (opacity * MAX_ALPHA).toInt()
			canvas.drawText(placement.comment.text, placement.x, placement.y, paint)
			paint.style = Paint.Style.FILL
			paint.color = placement.comment.color
			paint.alpha = (opacity * MAX_ALPHA).toInt()
			canvas.drawText(placement.comment.text, placement.x, placement.y, paint)
		}
	}

	companion object {
		private const val DEFAULT_FONT_SP = 22f
		private const val LINE_SPACING = 1.3f
		private const val AVAILABLE_HEIGHT = 0.7f
		private const val MAX_ALPHA = 255
	}
}
