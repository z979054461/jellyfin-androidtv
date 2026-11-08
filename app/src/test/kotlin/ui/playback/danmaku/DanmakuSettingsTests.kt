package org.jellyfin.androidtv.ui.playback.danmaku

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DanmakuSettingsTests : FunSpec({
	test("smaller default remains one of the selectable sizes") {
		DanmakuSettings.DEFAULT_FONT shouldBe 0.8f
		DanmakuSettings.fonts.any { it == DanmakuSettings.DEFAULT_FONT } shouldBe true
		DanmakuSettings.fontPercentages shouldBe (50..150 step 10).toList()
	}

	test("all row counts from one to twenty are selectable") {
		DanmakuSettings.rows.toList() shouldBe (1..20).toList()
		DanmakuSettings.rows.contains(DanmakuSettings.DEFAULT_ROWS) shouldBe true
	}

	test("expanded menu indices do not wrap or collide with other groups") {
		val ids = mutableSetOf<Int>()
		for (group in 1..7) {
			for (index in DanmakuSettings.rows.indices) {
				val id = group * DanmakuSettings.MENU_GROUP_STRIDE + index
				ids.add(id) shouldBe true
				(id % DanmakuSettings.MENU_GROUP_STRIDE) shouldBe index
			}
		}
	}

	test("previous user font and row preferences remain supported") {
		listOf(0.8f, 1f, 1.2f).all { saved -> DanmakuSettings.fonts.any { it == saved } } shouldBe true
		listOf(3, 6, 9).all { it in DanmakuSettings.rows } shouldBe true
	}
})
