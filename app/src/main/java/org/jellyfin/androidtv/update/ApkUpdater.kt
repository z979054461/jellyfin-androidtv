package org.jellyfin.androidtv.update

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipFile
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Anonymous, isolated client: never uses the Jellyfin server's URL, credentials, or HTTP client. */
internal class ApkUpdater(private val context: Context) {
	private var pinnedFile: File? = null

	fun releaseCachedFile() {
		pinnedFile?.let(UpdateCache::unpin)
		pinnedFile = null
	}

	private val client = OkHttpClient.Builder()
		.followRedirects(false).followSslRedirects(false)
		.connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
		.callTimeout(5, TimeUnit.MINUTES).build()

	suspend fun check(): ReleaseApk? {
		require(context.packageName == UpdatePolicy.PACKAGE)
		val installed = installedVersion()
		return fetch(UpdatePolicy.API, api = true) { response ->
			val body = requireNotNull(response.body)
			// Metadata is bounded too. Never read an unbounded JSON response into memory.
			val bytes = body.byteStream().readBytesBounded(2 * 1024 * 1024)
			val releases = JSONArray(bytes.toString(Charsets.UTF_8))
			var invalidNewerCandidate = false
			val candidates = buildList {
				for (index in 0 until releases.length()) {
					val release = releases.getJSONObject(index)
					if (release.optBoolean("draft")) continue
					val assets = release.optJSONArray("assets") ?: continue
					for (assetIndex in 0 until assets.length()) {
						val asset = assets.getJSONObject(assetIndex)
						val name = asset.optString("name")
						val code = UpdatePolicy.versionCode(name) ?: continue
						if (!UpdatePolicy.isNewer(code, installed)) continue
						val url = asset.optString("browser_download_url")
						if (!UpdatePolicy.trustedDownloadUrl(url, true)) {
							invalidNewerCandidate = true
							continue
						}
						val size = asset.optLong("size")
						if (size !in 1..UpdatePolicy.MAX_APK_BYTES) {
							invalidNewerCandidate = true
							continue
						}
						// Missing GitHub asset digest fails closed, never trust a sibling checksum file.
						val sha = UpdatePolicy.sha256(asset.optString("digest"))
						if (sha == null) {
							invalidNewerCandidate = true
							continue
						}
						add(ReleaseApk(name, code, size, url, sha))
					}
				}
			}
			val best = candidates.maxByOrNull { it.versionCode }
			UpdatePolicy.requireVerifiableCandidate(best != null, invalidNewerCandidate)
			best
		}
	}

	suspend fun download(asset: ReleaseApk): File {
		require(UpdatePolicy.trustedDownloadUrl(asset.url, true))
		require(asset.size in 1..UpdatePolicy.MAX_APK_BYTES)
		val completed = fetch(asset.url, api = false) { response ->
			val directory = File(context.cacheDir, "updates").apply { check(isDirectory || mkdirs()) }
			val partial = UpdateCache.createPartial(directory, "${asset.sha256}.apk", System.currentTimeMillis())
			try {
				val body = requireNotNull(response.body)
				check(body.contentLength() == -1L || body.contentLength() == asset.size)
				val hash = MessageDigest.getInstance("SHA-256")
				var total = 0L
				body.byteStream().use { input ->
					partial.outputStream().use { output ->
						val buffer = ByteArray(32 * 1024)
						while (true) {
							val count = input.read(buffer)
							if (count == -1) break
							total += count
							check(total <= asset.size && total <= UpdatePolicy.MAX_APK_BYTES)
							hash.update(buffer, 0, count)
							output.write(buffer, 0, count)
						}
					}
				}
				check(total == asset.size)
				check(hash.digest().hex() == asset.sha256)
				verifyArchive(partial, asset.versionCode)
				val complete = File(directory, "${asset.sha256}.apk")
				check(partial.renameTo(complete))
				complete
			} finally {
				partial.delete()
			}
		}
		releaseCachedFile()
		UpdateCache.pin(completed)
		pinnedFile = completed
		return completed
	}

	suspend fun revalidate(file: File, asset: ReleaseApk) = withContext(Dispatchers.IO) {
		check(file.isFile && file.length() == asset.size)
		val digest = MessageDigest.getInstance("SHA-256")
		file.inputStream().use { input ->
			val buffer = ByteArray(32 * 1024)
			while (true) {
				val count = input.read(buffer)
				if (count == -1) break
				digest.update(buffer, 0, count)
			}
		}
		check(digest.digest().hex() == asset.sha256)
		verifyArchive(file, asset.versionCode)
		UpdateCache.leaseForInstaller(file, System.currentTimeMillis())
	}

	@Suppress("DEPRECATION")
	private fun packageFlags() = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

	@Suppress("DEPRECATION")
	private fun installedPackage() = context.packageManager.getPackageInfo(context.packageName, packageFlags())
	fun installedVersion(): Long = PackageInfoCompat.getLongVersionCode(installedPackage())

	@Suppress("DEPRECATION")
	private fun certificates(info: PackageInfo): Set<String> {
		val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
		return signatures.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).hex() }.toSet()
	}

	@Suppress("DEPRECATION")
	private fun verifyArchive(file: File, expectedCode: Long) {
		check(context.packageName == UpdatePolicy.PACKAGE)
		val archive = requireNotNull(context.packageManager.getPackageArchiveInfo(file.absolutePath, packageFlags()))
		check(archive.packageName == context.packageName)
		check(archive.splitNames.isNullOrEmpty())
		check(requireNotNull(archive.applicationInfo).flags and ApplicationInfo.FLAG_DEBUGGABLE == 0)
		ZipFile(file).use { zip ->
			check(zip.getEntry("AndroidManifest.xml") != null && zip.getEntry("classes.dex") != null && zip.getEntry("resources.arsc") != null)
		}
		val current = installedPackage()
		val version = PackageInfoCompat.getLongVersionCode(archive)
		check(version == expectedCode && UpdatePolicy.isNewer(version, PackageInfoCompat.getLongVersionCode(current)))
		val signers = certificates(current)
		check(signers.isNotEmpty() && certificates(archive) == signers)
	}

	private suspend fun <T> fetch(url: String, api: Boolean, consume: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
		val active = AtomicReference<Call?>()
		val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(if (api) 30 else 300)
		continuation.invokeOnCancellation { active.get()?.cancel() }
		fun request(target: String, redirects: Int) {
			if (!continuation.isActive) return
			try {
				check(if (api) target == UpdatePolicy.API else UpdatePolicy.trustedDownloadUrl(target))
				check(redirects <= 5 && System.nanoTime() < deadline)
				val call = client.newCall(Request.Builder().url(target)
					.header("Accept", if (api) "application/vnd.github+json" else "application/octet-stream")
					.header("User-Agent", "Jellyfin-Danmaku-Updater").build())
				call.timeout().deadlineNanoTime(deadline)
				active.set(call)
				if (!continuation.isActive) { call.cancel(); return }
				call.enqueue(object : Callback {
					override fun onFailure(call: Call, e: IOException) {
						if (continuation.isActive) continuation.resumeWithException(e)
					}
					override fun onResponse(call: Call, response: Response) {
						try {
							response.use {
								if (response.code in listOf(301, 302, 303, 307, 308)) {
									check(!api)
									val next = requireNotNull(response.header("Location"))
									check(UpdatePolicy.trustedDownloadUrl(next))
									request(next, redirects + 1)
								} else {
									check(response.isSuccessful)
									if (!continuation.isActive) return
									val result = consume(response)
									if (continuation.isActive) continuation.resume(result)
								}
							}
						} catch (error: Exception) {
							if (continuation.isActive) continuation.resumeWithException(error)
						}
					}
				})
			} catch (error: Exception) {
				if (continuation.isActive) continuation.resumeWithException(error)
			}
		}
		request(url, 0)
	}

	private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
		val output = java.io.ByteArrayOutputStream()
		val buffer = ByteArray(8192)
		while (true) {
			val count = read(buffer)
			if (count == -1) break
			check(output.size() + count <= limit)
			output.write(buffer, 0, count)
		}
		return output.toByteArray()
	}

	private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}
