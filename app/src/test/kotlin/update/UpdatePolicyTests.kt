package org.jellyfin.androidtv.update

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class UpdatePolicyTests : FunSpec({
	test("only this fork's single release APK naming is accepted") {
		UpdatePolicy.versionCode("jellyfin-danmaku-191004.apk") shouldBe 191004L
		listOf("jellyfin-danmaku-0.apk", "jellyfin-danmaku-01.apk", "jellyfin-danmaku-191004-debug.apk", "app-release.apk",
			"jellyfin-danmaku-191004.apks", "jellyfin-danmaku-999999999999999999999.apk").forEach {
			UpdatePolicy.versionCode(it) shouldBe null
		}
	}
	test("version comparison excludes equal versions downgrades and Android overflow") {
		UpdatePolicy.isNewer(191004, 191003) shouldBe true
		UpdatePolicy.isNewer(191003, 191003) shouldBe false
		UpdatePolicy.isNewer(191002, 191003) shouldBe false
		UpdatePolicy.isNewer(Int.MAX_VALUE.toLong() + 1, 191003) shouldBe false
	}
	test("GitHub SHA256 metadata is mandatory and normalized") {
		UpdatePolicy.sha256("sha256:" + "AB".repeat(32)) shouldBe "ab".repeat(32)
		listOf("", "null", "sha256:1234", "sha512:" + "a".repeat(64), "sha256:" + "g".repeat(64)).forEach {
			UpdatePolicy.sha256(it) shouldBe null
		}
	}
	test("original downloads must be from the fixed repository") {
		UpdatePolicy.trustedDownloadUrl("https://github.com/z979054461/jellyfin-androidtv/releases/download/v1/jellyfin-danmaku-191004.apk", true) shouldBe true
		UpdatePolicy.trustedDownloadUrl("https://release-assets.githubusercontent.com/example?token=value", true) shouldBe false
		UpdatePolicy.trustedDownloadUrl("https://github.com/other/repository/releases/download/v1/app.apk", true) shouldBe false
	}
	test("redirect destinations use exact HTTPS host allowlist") {
		UpdatePolicy.trustedDownloadUrl("https://release-assets.githubusercontent.com/github-production-release-asset/file?signature=value") shouldBe true
		UpdatePolicy.trustedDownloadUrl("https://objects.githubusercontent.com/github-production-release-asset/file") shouldBe true
		listOf("http://release-assets.githubusercontent.com/a", "https://release-assets.githubusercontent.com.evil.example/a",
			"https://github.com@evil.example/a", "https://user@release-assets.githubusercontent.com/a",
			"https://release-assets.githubusercontent.com:444/a", "file:///cache/update.apk", "https://127.0.0.1/a",
			"https://github.com/z979054461/jellyfin-androidtv/releases/download/../a",
			"https://github.com/z979054461/jellyfin-androidtv/releases/download/%2e%2e/a",
			"https://release-assets.githubusercontent.com/a#fragment", "not a URL").forEach {
			UpdatePolicy.trustedDownloadUrl(it) shouldBe false
		}
	}
	test("download size formatting is stable") {
		UpdatePolicy.sizeLabel(0) shouldBe "0.0 MiB"
		UpdatePolicy.sizeLabel(1572864) shouldBe "1.5 MiB"
		UpdatePolicy.sizeLabel(UpdatePolicy.MAX_APK_BYTES) shouldBe "200.0 MiB"
	}
})
