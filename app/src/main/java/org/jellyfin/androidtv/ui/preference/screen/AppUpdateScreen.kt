package org.jellyfin.androidtv.ui.preference.screen

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.preference.dsl.OptionsFragment
import org.jellyfin.androidtv.ui.preference.dsl.action
import org.jellyfin.androidtv.ui.preference.dsl.link
import org.jellyfin.androidtv.ui.preference.dsl.optionsScreen
import org.jellyfin.androidtv.update.ApkUpdater
import org.jellyfin.androidtv.update.ReleaseApk
import org.jellyfin.androidtv.update.UpdatePolicy
import java.io.File

/** No work starts on entry. Each network/download/installation step requires a remote click. */
class AppUpdateScreen : OptionsFragment() {
	private val updater by lazy { ApkUpdater(requireContext().applicationContext) }
	private var job: Job? = null
	private var busy = false
	private var status: String? = null
	private var candidate: ReleaseApk? = null
	private var apk: File? = null
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
				setTitle(R.string.update_check)
				enabled = supported && !busy
				onActivate = { checkForUpdate() }
			}
			candidate?.let { release ->
				action {
					setTitle(R.string.update_download)
					content = getString(R.string.update_candidate, release.versionCode.toString(), UpdatePolicy.sizeLabel(release.size))
					enabled = !busy && apk == null
					onActivate = { download(release) }
				}
			}
			if (apk != null) {
				action {
					setTitle(if (canInstall()) R.string.update_install else R.string.update_allow_install)
					setContent(R.string.update_install_explanation)
					enabled = !busy
					onActivate = { if (canInstall()) install() else allowInstall() }
				}
			}
			if (busy) {
				action {
					setTitle(R.string.update_cancel)
					onActivate = {
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

	private fun download(release: ReleaseApk) = runOperation(R.string.update_downloading) {
		apk = null
		apk = updater.download(release)
		status = getString(R.string.update_ready)
	}

	private fun runOperation(message: Int, operation: suspend () -> Unit) {
		if (busy || !supported) return
		busy = true
		status = getString(message)
		rebuild()
		job = viewLifecycleOwner.lifecycleScope.launch {
			try {
				operation()
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

	private fun canInstall() = Build.VERSION.SDK_INT < 26 || requireContext().packageManager.canRequestPackageInstalls()

	private fun allowInstall() {
		try {
			startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${requireContext().packageName}")))
		} catch (_: Exception) {
			status = getString(R.string.update_settings_unavailable)
			rebuild()
		}
	}

	private fun install() = runOperation(R.string.update_verifying) {
		val file = requireNotNull(apk)
		updater.revalidate(file, requireNotNull(candidate))
		val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.updates", file)
		val intent = Intent(Intent.ACTION_VIEW).apply {
			setDataAndType(uri, "application/vnd.android.package-archive")
			clipData = ClipData.newRawUri("APK update", uri)
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}
		status = getString(R.string.update_installer_opened)
		startActivity(intent)
	}

	override fun onDestroy() {
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
