package org.jellyfin.androidtv.ui.settings.screen.about

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.update.ApkUpdater
import org.jellyfin.androidtv.update.InstallerHandoffGate
import org.jellyfin.androidtv.update.InstallerLaunchPolicy
import org.jellyfin.androidtv.update.ReleaseApk
import org.jellyfin.androidtv.update.UpdatePolicy
import java.io.File
import kotlin.coroutines.coroutineContext

/** Screen-owned work; no automatic check and no installation without a user-started operation. */
internal class AppUpdateController(
	private val context: Context,
	private val scope: CoroutineScope,
	private val isForeground: () -> Boolean,
) {
	private val updater = ApkUpdater(context.applicationContext)
	private var job: Job? = null
	var busy by mutableStateOf(false)
		private set
	var status by mutableStateOf<String?>(null)
		private set
	var candidate by mutableStateOf<ReleaseApk?>(null)
		private set
	var apk by mutableStateOf<File?>(null)
		private set
	private val handoffGate = InstallerHandoffGate()
	val supported = context.packageName == UpdatePolicy.PACKAGE && !BuildConfig.DEBUG
	private fun getString(id: Int) = context.getString(id)

	fun leave() = handoffGate.invalidate()

	fun cancel() {
		handoffGate.invalidate()
		job?.cancel()
		busy = false
		status = getString(R.string.update_cancelled)
	}

	fun stop() {
		leave()
		if (busy) cancel()
	}

	fun dispose() {
		stop()
		updater.releaseCachedFile()
	}

	fun checkForUpdate() = runOperation(R.string.update_checking) {
		candidate = null
		apk = null
		updater.releaseCachedFile()
		candidate = updater.check()
		status = getString(if (candidate == null) R.string.update_none else R.string.update_available)
	}

	fun download(release: ReleaseApk) = runOperation(R.string.update_downloading) { operation ->
		apk = null
		apk = updater.download(release)
		status = getString(R.string.update_verifying)
		openVerifiedInstaller(operation)
	}

	private fun runOperation(message: Int, operation: suspend (Long) -> Unit) {
		if (busy || !supported || !isForeground()) return
		val generation = handoffGate.begin()
		busy = true
		status = getString(message)
		job = scope.launch {
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
				}
			}
		}
	}

	fun canInstall() = try {
		Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()
	} catch (_: RuntimeException) {
		false // An unavailable permission probe must not hide the standard installer action.
	}

	fun allowInstall() {
		if (busy || !supported || !isForeground()) return
		try {
			context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
		} catch (_: Exception) {
			status = getString(R.string.update_settings_unavailable)
		}
	}

	fun install() = runOperation(R.string.update_verifying) { operation -> openVerifiedInstaller(operation) }

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
		if (!isForeground() || !handoffGate.claim(operation)) {
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
		val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
		val intent = Intent(when (action) {
			InstallerLaunchPolicy.Action.VIEW -> Intent.ACTION_VIEW
			InstallerLaunchPolicy.Action.INSTALL_PACKAGE -> Intent.ACTION_INSTALL_PACKAGE
		}).apply {
			setDataAndType(uri, "application/vnd.android.package-archive")
			clipData = ClipData.newRawUri("APK update", uri)
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}
		context.startActivity(intent)
		InstallerLaunchPolicy.Result.OPENED
	} catch (_: ActivityNotFoundException) {
		InstallerLaunchPolicy.Result.NO_HANDLER
	} catch (_: SecurityException) {
		InstallerLaunchPolicy.Result.BLOCKED
	} catch (_: Exception) {
		InstallerLaunchPolicy.Result.FAILED
	}
}
