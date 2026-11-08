package org.jellyfin.androidtv.ui.preference.screen

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.view.doOnNextLayout
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.ViewHolderTask
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.preference.dsl.OptionsFragment
import org.jellyfin.androidtv.ui.preference.dsl.action
import org.jellyfin.androidtv.ui.preference.dsl.link
import org.jellyfin.androidtv.ui.preference.dsl.optionsScreen
import org.jellyfin.androidtv.update.ApkUpdater
import org.jellyfin.androidtv.update.InstallerHandoffGate
import org.jellyfin.androidtv.update.InstallerLaunchPolicy
import org.jellyfin.androidtv.update.ReleaseApk
import org.jellyfin.androidtv.update.UpdateFocusPolicy
import org.jellyfin.androidtv.update.UpdatePolicy
import java.io.File
import kotlin.coroutines.coroutineContext

/** No work starts on entry. Download hands a verified APK to Android for user confirmation. */
class AppUpdateScreen : OptionsFragment() {
	private val updater by lazy { ApkUpdater(requireContext().applicationContext) }
	private var job: Job? = null
	private var busy = false
	private var status: String? = null
	private var candidate: ReleaseApk? = null
	private var apk: File? = null
	private var focusEpoch = 0
	private val handoffGate = InstallerHandoffGate()
	private val supported get() = requireContext().packageName == UpdatePolicy.PACKAGE && !BuildConfig.DEBUG
	override val rebuildOnResume = true

	override val screen by optionsScreen {
		setTitle(R.string.update_title)
		category {
			link {
				setTitle(R.string.update_current)
				content = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
			}
			link {
				setTitle(R.string.update_status)
				content = if (!supported) getString(R.string.update_debug_unsupported)
				else status ?: getString(R.string.update_intro)
			}
			action {
				key = UpdateFocusPolicy.CHECK
				setTitle(R.string.update_check)
				enabled = supported && !busy
				onActivate = { checkForUpdate() }
			}
			candidate?.let { release ->
				action {
					key = UpdateFocusPolicy.DOWNLOAD
					setTitle(R.string.update_download)
					content = getString(R.string.update_candidate, release.versionCode.toString(), UpdatePolicy.sizeLabel(release.size))
					enabled = !busy && apk == null
					onActivate = { download(release) }
				}
			}
			if (apk != null) {
				action {
					key = UpdateFocusPolicy.INSTALL
					setTitle(R.string.update_install)
					setContent(R.string.update_install_explanation)
					enabled = !busy
					onActivate = { install() }
				}
				// The permission probe is advisory. Some TV firmware has no app-specific settings UI.
				if (Build.VERSION.SDK_INT >= 26 && !canInstall()) {
					action {
						key = UpdateFocusPolicy.PERMISSION
						setTitle(R.string.update_allow_install)
						setContent(R.string.update_permission_explanation)
						enabled = !busy
						onActivate = { allowInstall() }
					}
				}
			}
			if (busy) {
				action {
					key = UpdateFocusPolicy.CANCEL
					setTitle(R.string.update_cancel)
					onActivate = {
						handoffGate.invalidate()
						job?.cancel()
						busy = false
						status = getString(R.string.update_cancelled)
						rebuild()
					}
				}
			}
		}
	}

	private fun checkForUpdate() = runOperation(R.string.update_checking) {
		candidate = null
		apk = null
		updater.releaseCachedFile()
		candidate = updater.check()
		status = getString(if (candidate == null) R.string.update_none else R.string.update_available)
	}

	private fun download(release: ReleaseApk) = runOperation(R.string.update_downloading) { operation ->
		apk = null
		apk = updater.download(release)
		status = getString(R.string.update_verifying)
		rebuild()
		openVerifiedInstaller(operation)
	}

	private fun runOperation(message: Int, operation: suspend (Long) -> Unit) {
		if (busy || !supported) return
		val generation = handoffGate.begin()
		busy = true
		status = getString(message)
		rebuild()
		job = viewLifecycleOwner.lifecycleScope.launch {
			try {
				operation(generation)
			} catch (cancelled: CancellationException) {
				throw cancelled
			} catch (_: Exception) {
				apk = null
				status = getString(R.string.update_failed)
			} finally {
				// A canceled operation must not overwrite a newer operation's screen state.
				if (kotlin.coroutines.coroutineContext[Job]?.isActive == true) {
					busy = false
					if (view != null) rebuild()
				}
			}
		}
	}

	private fun canInstall() = try {
		Build.VERSION.SDK_INT < 26 || requireContext().packageManager.canRequestPackageInstalls()
	} catch (_: RuntimeException) {
		false // An unavailable permission probe must not hide the standard installer action.
	}

	private fun allowInstall() {
		try {
			startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${requireContext().packageName}")))
		} catch (_: Exception) {
			status = getString(R.string.update_settings_unavailable)
			rebuild()
		}
	}

	private fun install() = runOperation(R.string.update_verifying) { operation -> openVerifiedInstaller(operation) }

	private suspend fun openVerifiedInstaller(operation: Long) {
		val file = requireNotNull(apk)
		try {
			updater.revalidate(file, requireNotNull(candidate))
		} catch (cancelled: CancellationException) {
			throw cancelled
		} catch (_: Exception) {
			apk = null
			updater.releaseCachedFile()
			status = getString(R.string.update_validation_failed)
			return
		}

		coroutineContext.ensureActive()
		if (!isResumed || view == null || !handoffGate.claim(operation)) {
			status = getString(R.string.update_ready)
			return // Leaving during download/verification must never open another activity.
		}
		// Validation is complete. Leaving for the installer must not cancel this handoff in onStop.
		// There are no suspension points between here and launch; each retry revalidates the APK.
		busy = false
		val result = InstallerLaunchPolicy.launch { action -> launchInstaller(file, action) }
		status = getString(when (result) {
			InstallerLaunchPolicy.Result.OPENED -> R.string.update_installer_opened
			InstallerLaunchPolicy.Result.NO_HANDLER -> R.string.update_installer_unavailable
			InstallerLaunchPolicy.Result.BLOCKED -> R.string.update_installer_blocked
			else -> R.string.update_installer_failed
		})
		// Launch-only failures retain the verified file and candidate for an explicit retry.
	}

	@Suppress("DEPRECATION")
	private fun launchInstaller(file: File, action: InstallerLaunchPolicy.Action): InstallerLaunchPolicy.Result = try {
		val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.updates", file)
		val intent = Intent(when (action) {
			InstallerLaunchPolicy.Action.VIEW -> Intent.ACTION_VIEW
			InstallerLaunchPolicy.Action.INSTALL_PACKAGE -> Intent.ACTION_INSTALL_PACKAGE
		}).apply {
			setDataAndType(uri, "application/vnd.android.package-archive")
			clipData = ClipData.newRawUri("APK update", uri)
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}
		startActivity(intent)
		InstallerLaunchPolicy.Result.OPENED
	} catch (_: ActivityNotFoundException) {
		InstallerLaunchPolicy.Result.NO_HANDLER
	} catch (_: SecurityException) {
		InstallerLaunchPolicy.Result.BLOCKED
	} catch (_: Exception) {
		InstallerLaunchPolicy.Result.FAILED
	}

	// Rebuilding the preference screen removes its focused view. Only this screen opts in to
	// workflow focus restoration; stable action keys leave every other options screen unchanged.
	override fun rebuild() {
		super.rebuild()
		restoreActionFocus()
	}

	override fun onResume() {
		super.onResume()
		restoreActionFocus()
	}

	private fun restoreActionFocus() {
		val root = view ?: return
		val epoch = ++focusEpoch
		val target = UpdateFocusPolicy.preferred(busy, apk != null, candidate != null)
		val list = listView
		// PreferenceGroupAdapter posts its own synchronization after removeAll/add. Wait for it
		// before locating the stable key, then wait for a layout before touching the new holder.
		list.post {
			if (epoch != focusEpoch || view !== root || !isResumed) return@post
			val adapter = list.adapter as? PreferenceGroupAdapter ?: return@post
			val position = adapter.getPreferenceAdapterPosition(target)
			fun stillValid() = epoch == focusEpoch && view === root && isResumed &&
				list.adapter === adapter && position >= 0 && adapter.getItem(position)?.let {
					it.key == target && it.isEnabled
				} == true
			if (!stillValid()) return@post
			if (list is BaseGridView) {
				list.setSelectedPosition(position, object : ViewHolderTask() {
					override fun run(holder: RecyclerView.ViewHolder) {
						if (stillValid()) holder.itemView.requestFocus()
					}
				})
			} else {
				// Non-Leanback fallback: layout first, then a bounded retry if the holder is late.
				fun focusHolder(attemptsLeft: Int) {
					if (!stillValid()) return
					val holder = list.findViewHolderForAdapterPosition(position)
					if (holder != null) holder.itemView.requestFocus()
					else if (attemptsLeft > 0) list.post { focusHolder(attemptsLeft - 1) }
				}
				list.doOnNextLayout { focusHolder(2) }
				list.scrollToPosition(position)
				list.requestLayout()
			}
		}
	}

	override fun onPause() {
		handoffGate.invalidate() // Even a brief leave/return cannot revive an automatic handoff.
		++focusEpoch // Invalidate posted focus work when opening settings/installer or leaving.
		super.onPause()
	}

	override fun onDestroy() {
		handoffGate.invalidate()
		updater.releaseCachedFile()
		super.onDestroy()
	}

	override fun onStop() {
		if (busy) {
			job?.cancel()
			busy = false
			status = getString(R.string.update_cancelled)
		}
		super.onStop()
	}
}
