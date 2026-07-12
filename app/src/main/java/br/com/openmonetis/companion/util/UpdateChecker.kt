package br.com.openmonetis.companion.util

import android.content.Context
import android.util.Log
import br.com.openmonetis.companion.BuildConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private data class LatestVersionInfo(val sha: String?, val buildTime: String?)

data class UpdateCheckResult(
    val shortSha: String
)

/**
 * Checks the public "debug-latest" GitHub release for a newer build than the
 * one currently installed, and downloads it on request. Talks directly to
 * github.com with a plain client (no auth) - the repo must be public for
 * this to work, since private-repo releases require a token to fetch.
 */
@Singleton
class UpdateChecker @Inject constructor() {

    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun checkForUpdate(): UpdateCheckResult? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(VERSION_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val info = gson.fromJson(body, LatestVersionInfo::class.java)
                val remoteSha = info.sha
                if (remoteSha.isNullOrBlank() || remoteSha == BuildConfig.GIT_SHA) {
                    null
                } else {
                    UpdateCheckResult(shortSha = remoteSha.take(7))
                }
            }
        }.onFailure { Log.w(TAG, "Update check failed", it) }.getOrNull()
    }

    suspend fun downloadApk(context: Context): File? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(APK_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val file = File(context.cacheDir, "companion-update.apk")
                response.body?.byteStream()?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                } ?: return@use null
                file
            }
        }.onFailure { Log.w(TAG, "Update download failed", it) }.getOrNull()
    }

    companion object {
        private const val TAG = "UpdateChecker"
        private const val RELEASE_BASE =
            "https://github.com/Tavares-z/openmonetis-companion/releases/download/debug-latest"
        private const val VERSION_URL = "$RELEASE_BASE/version.json"
        private const val APK_URL = "$RELEASE_BASE/app-debug.apk"
    }
}
