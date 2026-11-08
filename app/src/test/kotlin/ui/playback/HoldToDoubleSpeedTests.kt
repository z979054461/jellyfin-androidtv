package org.jellyfin.androidtv.ui.playback

import io.kotest.core.spec.style.FunSpec

class HoldToDoubleSpeedTests : FunSpec({
	test("temporary speed gestures restore speed and preserve short presses") {
		HoldToDoubleSpeedTestCases.runAll()
	}
})
