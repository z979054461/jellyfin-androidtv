package org.jellyfin.androidtv.ui.settings.screen.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListMessage
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.update.UpdateFocusPolicy
import org.jellyfin.androidtv.update.UpdatePolicy
import org.jellyfin.design.Tokens

@Composable
fun SettingsAppUpdateScreen() {
	val context = LocalContext.current
	val lifecycle = LocalLifecycleOwner.current.lifecycle
	val router = LocalRouter.current
	val scope = rememberCoroutineScope()
	val controller = remember(context, lifecycle, router, scope) {
		AppUpdateController(context, scope) {
			lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && router.backStack.lastOrNull()?.route == Routes.APP_UPDATE
		}
	}
	val active = router.backStack.lastOrNull()?.route == Routes.APP_UPDATE
	var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
	DisposableEffect(controller, lifecycle, active) {
		val observer = LifecycleEventObserver { _, event ->
			resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
			when (event) {
				Lifecycle.Event.ON_PAUSE -> controller.leave()
				Lifecycle.Event.ON_STOP -> controller.stop()
				else -> Unit
			}
		}
		lifecycle.addObserver(observer)
		if (!active) controller.stop()
		onDispose {
			lifecycle.removeObserver(observer)
			controller.stop()
		}
	}
	DisposableEffect(controller) { onDispose { controller.dispose() } }
	val target = UpdateFocusPolicy.preferred(controller.busy, controller.apk != null, controller.candidate != null)
	val requesters = remember {
		listOf(UpdateFocusPolicy.CHECK, UpdateFocusPolicy.DOWNLOAD, UpdateFocusPolicy.INSTALL,
			UpdateFocusPolicy.PERMISSION, UpdateFocusPolicy.CANCEL).associateWith { FocusRequester() }
	}
	LaunchedEffect(target, resumed, active) {
		if (resumed && active && controller.supported) {
			// Wait for the newly enabled action to attach, and never reclaim focus while away.
			withFrameNanos { }
			if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && router.backStack.lastOrNull()?.route == Routes.APP_UPDATE) {
				requesters.getValue(target).requestFocus()
			}
		}
	}

	Column(
		modifier = Modifier.verticalScroll(rememberScrollState()).padding(Tokens.Space.spaceSm),
		verticalArrangement = Arrangement.spacedBy(Tokens.Space.spaceXs),
	) {
		ListSection(
			overlineContent = { Text(stringResource(R.string.pref_about_title).uppercase()) },
			headingContent = { Text(stringResource(R.string.update_title)) },
		)
		ListMessage { Text("${stringResource(R.string.update_current)}: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") }
		ListMessage {
			Text(if (!controller.supported) stringResource(R.string.update_debug_unsupported)
			else controller.status ?: stringResource(R.string.update_intro))
		}
		UpdateAction(UpdateFocusPolicy.CHECK, R.string.update_check, requesters,
			enabled = controller.supported && !controller.busy, onClick = controller::checkForUpdate)
		controller.candidate?.let { release ->
			UpdateAction(UpdateFocusPolicy.DOWNLOAD, R.string.update_download, requesters,
				enabled = !controller.busy && controller.apk == null,
				caption = stringResource(R.string.update_candidate, release.versionCode.toString(), UpdatePolicy.sizeLabel(release.size)),
				onClick = { controller.download(release) })
		}
		if (controller.apk != null) {
			UpdateAction(UpdateFocusPolicy.INSTALL, R.string.update_install, requesters,
				enabled = !controller.busy, caption = stringResource(R.string.update_install_explanation), onClick = controller::install)
			if (!controller.canInstall()) {
				UpdateAction(UpdateFocusPolicy.PERMISSION, R.string.update_allow_install, requesters,
					enabled = !controller.busy, caption = stringResource(R.string.update_permission_explanation), onClick = controller::allowInstall)
			}
		}
		if (controller.busy) {
			UpdateAction(UpdateFocusPolicy.CANCEL, R.string.update_cancel, requesters, onClick = controller::cancel)
		}
	}
}

@Composable
private fun UpdateAction(
	key: String,
	title: Int,
	requesters: Map<String, FocusRequester>,
	enabled: Boolean = true,
	caption: String? = null,
	onClick: () -> Unit,
) {
	ListButton(
		headingContent = { Text(stringResource(title)) },
		captionContent = caption?.let { { Text(it) } },
		enabled = enabled,
		onClick = onClick,
		modifier = Modifier.focusKey(key, requesters.getValue(key)),
	)
}
