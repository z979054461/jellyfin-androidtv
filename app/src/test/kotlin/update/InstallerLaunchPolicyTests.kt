package org.jellyfin.androidtv.update

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class InstallerLaunchPolicyTests : FunSpec({
	test("leaving invalidates a handoff and old completions cannot consume an explicit retry") {
		val gate = InstallerHandoffGate()
		val departed = gate.begin()
		gate.invalidate()
		gate.claim(departed) shouldBe false
		val retry = gate.begin()
		gate.claim(departed) shouldBe false
		gate.claim(retry) shouldBe true
		gate.claim(retry) shouldBe false
		val cancelled = gate.begin()
		gate.invalidate()
		val next = gate.begin()
		gate.claim(cancelled) shouldBe false
		gate.claim(next) shouldBe true
		gate.claim(next) shouldBe false
	}
	test("only a missing standard handler permits one fallback including a terminal fallback denial") {
		InstallerLaunchPolicy.Result.values().forEach { primary ->
			InstallerLaunchPolicy.Result.values().forEach { fallback ->
				val actions = mutableListOf<InstallerLaunchPolicy.Action>()
				val result = InstallerLaunchPolicy.launch { action ->
					actions += action
					if (action == InstallerLaunchPolicy.Action.VIEW) primary else fallback
				}
				val missing = primary == InstallerLaunchPolicy.Result.NO_HANDLER
				result shouldBe if (missing) fallback else primary
				actions shouldBe if (missing) listOf(InstallerLaunchPolicy.Action.VIEW, InstallerLaunchPolicy.Action.INSTALL_PACKAGE)
				else listOf(InstallerLaunchPolicy.Action.VIEW)
			}
		}
	}
	test("download completion and launch retry focus installation instead of the disabled download") {
		UpdateFocusPolicy.preferred(true, false, true) shouldBe UpdateFocusPolicy.CANCEL
		UpdateFocusPolicy.preferred(false, true, true) shouldBe UpdateFocusPolicy.INSTALL
		UpdateFocusPolicy.preferred(false, false, true) shouldBe UpdateFocusPolicy.DOWNLOAD
		UpdateFocusPolicy.preferred(false, false, false) shouldBe UpdateFocusPolicy.CHECK
	}
})
