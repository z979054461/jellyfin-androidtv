package org.jellyfin.androidtv.ui.playback.overlay

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.ui.playback.overlay.action.PlaybackInfoFormatter

class PlaybackInfoFormatterTests : FunSpec({
	test("unknown and invalid measurements are omitted rather than shown as zero") {
		PlaybackInfoFormatter.bitrate(null) shouldBe null
		PlaybackInfoFormatter.bitrate(-1) shouldBe null
		PlaybackInfoFormatter.bitrate(Double.POSITIVE_INFINITY) shouldBe null
		PlaybackInfoFormatter.frameRate(Float.NaN) shouldBe null
		PlaybackInfoFormatter.frameRate(0) shouldBe null
		PlaybackInfoFormatter.sampleRate(-1) shouldBe null
		PlaybackInfoFormatter.dimensions(1920, null) shouldBe null
	}
	test("media units preserve useful precision") {
		PlaybackInfoFormatter.bitrate(192000) shouldBe "192 kbps"
		PlaybackInfoFormatter.bitrate(40500000L) shouldBe "40.50 Mbps"
		PlaybackInfoFormatter.frameRate(23.976f) shouldBe "23.976 fps"
		PlaybackInfoFormatter.sampleRate(44100) shouldBe "44.1 kHz"
		PlaybackInfoFormatter.dimensions(3840, 2160) shouldBe "3840 × 2160"
	}
	test("only metadata identifiers are shown, not paths or authenticated URLs") {
		PlaybackInfoFormatter.identifier("video/hevc") shouldBe "video/hevc"
		PlaybackInfoFormatter.identifier("hvc1.2.4.L153.B0") shouldBe "hvc1.2.4.L153.B0"
		PlaybackInfoFormatter.identifier("https://server/video?api_key=secret") shouldBe null
		PlaybackInfoFormatter.identifier("/media/private/file.mkv") shouldBe null
		PlaybackInfoFormatter.identifier("codec\nAuthorization: secret") shouldBe null
		PlaybackInfoFormatter.identifier("a".repeat(81)) shouldBe null
	}
})
