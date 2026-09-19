package com.bintianqi.owndroid

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetch + lưu cấu hình online từ web block_site.
 * Chạy được ngay ở màn Login (chưa login vẫn Sync được), không yêu cầu auth local.
 * URL base cố định, chỉ cho đổi trong Settings (sau login) để tránh bị trỏ sang server lạ pre-login.
 */
object RemoteConfigManager {

    private const val TAG = "RemoteConfigManager"
    const val DEFAULT_BASE_URL = "https://naqblocksite.pages.dev"
    private const val TIMEOUT_MS = 15000

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Volatile
    private var memory: OwndroidRemoteConfig? = null

    fun baseUrl(): String {
        val raw = try { SP.remoteConfigUrl } catch (_: Exception) { null }
        val b = if (raw.isNullOrBlank()) DEFAULT_BASE_URL else raw.trim()
        return b.trimEnd('/')
    }

    fun configUrl(): String = baseUrl() + "/api/owndroid-config"

    fun setBaseUrl(url: String?) {
        SP.remoteConfigUrl = url?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Config local đã lưu (null nếu chưa sync lần nào). */
    fun getLocal(): OwndroidRemoteConfig? {
        if (!isSPInitialized()) return memory
        val raw = SP.remoteConfigJson ?: return memory
        if (raw.isBlank()) return memory
        return try {
            memory ?: json.decodeFromString<OwndroidRemoteConfig>(raw).also { memory = it }
        } catch (e: Exception) {
            Log.e(TAG, "Parse local remote config failed", e)
            memory
        }
    }

    fun effective(): OwndroidRemoteConfig? = getLocal()

    fun lastSyncMillis(): Long = try { SP.remoteConfigUpdatedAt } catch (_: Exception) { 0L }

    private val pkgRegex = Regex("^[a-zA-Z0-9_.]{1,200}$")

    private fun cleanPkgs(input: List<String>, max: Int = 500): List<String> {
        val out = LinkedHashSet<String>()
        for (raw in input) {
            val s = raw.trim()
            if (s.isNotEmpty() && pkgRegex.matches(s)) out.add(s)
            if (out.size >= max) break
        }
        return out.toList()
    }

    private fun clampLong(v: Long, def: Long, min: Long, max: Long): Long =
        if (v in min..max) v else def

    private fun clampInt(v: Int, def: Int, min: Int, max: Int): Int =
        if (v in min..max) v else def

    /** Chuẩn hoá config từ server: chặn số âm/khủng, lọc package lạ, hard thắng soft. */
    fun sanitize(c: OwndroidRemoteConfig): OwndroidRemoteConfig {
        val hard = cleanPkgs(c.hardlockApps).toSet()
        val soft = cleanPkgs(c.softlockApps).filter { !hard.contains(it) }
        val strict = c.strictLock
        val night = c.nightMode
        val login = c.login
        // unlock = -1 nghĩa là "tới sáng", giữ nguyên
        fun tier(t: RemoteTier) = RemoteTier(
            req = clampInt(t.req, 1, 1, 999),
            unlock = if (t.unlock == -1L) -1L else clampLong(t.unlock, 5, 1, 1440),
            block = clampLong(t.block, 15, 0, 1440)
        )
        return c.copy(
            strictLock = RemoteStrict(
                unlockMinutes = clampLong(strict.unlockMinutes, 10, 1, 1440),
                blockMinutes = clampLong(strict.blockMinutes, 15, 0, 1440),
                dailyUnlocks = clampInt(strict.dailyUnlocks, 30, 1, 1000)
            ),
            dayTiers = c.dayTiers.take(5).map(::tier).ifEmpty { OwndroidRemoteConfig().dayTiers },
            nightTiers = c.nightTiers.take(5).map(::tier).ifEmpty { OwndroidRemoteConfig().nightTiers },
            nightMode = RemoteNightMode(
                startHour = clampInt(night.startHour, 22, 0, 23),
                endHour = clampInt(night.endHour, 7, 0, 23)
            ),
            login = RemoteLogin(
                attemptsForSettings = clampInt(login.attemptsForSettings, 20, 1, 999),
                reloginMinutes = clampLong(login.reloginMinutes, 10, 1, 1440),
                challengeLength = clampInt(login.challengeLength, 10, 4, 32)
            ),
            softlockApps = soft,
            hardlockApps = hard.toList(),
            nightWhitelistExtra = cleanPkgs(c.nightWhitelistExtra),
            vpn = RemoteVpn(
                `package` = c.vpn.`package`.trim().takeIf { pkgRegex.matches(it) } ?: "naq.dns",
                allowlist = cleanPkgs(c.vpn.allowlist)
            )
        )
    }

    /**
     * Sync từ web về, lưu local + áp danh sách soft/hard.
     * An toàn khi gọi pre-login: mọi lỗi DPM đều try/catch, offline trả failure và giữ config cũ.
     */
    suspend fun sync(context: Context): Result<OwndroidRemoteConfig> = withContext(Dispatchers.IO) {
        try {
            if (!isSPInitialized()) SP = SharedPrefs(context.applicationContext)
            val url = URL(configUrl())
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
            }
            try {
                val code = conn.responseCode
                if (code != 200) return@withContext Result.failure(Exception("Máy chủ trả $code"))
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val wrapper = json.decodeFromString<RemoteConfigWrapper>(body)
                val clean = sanitize(wrapper.config.copy(updatedAt = System.currentTimeMillis()))
                SP.remoteConfigJson = json.encodeToString(clean)
                SP.remoteConfigUpdatedAt = clean.updatedAt
                memory = clean
                applyLists(context.applicationContext, clean)
                Log.d(TAG, "Sync OK: ${clean.softlockApps.size} soft, ${clean.hardlockApps.size} hard")
                Result.success(clean)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.failure(Exception("Sync thất bại: ${e.message ?: e.javaClass.simpleName}"))
        }
    }

    /**
     * Thay thế toàn bộ soft/hard bằng remote (server là source-of-truth),
     * rồi suspend lại các app trong danh sách để khóa ngay.
     */
    fun applyLists(context: Context, c: OwndroidRemoteConfig) {
        try {
            if (!isSPInitialized()) SP = SharedPrefs(context.applicationContext)
            SP.softlockApps = json.encodeToString(c.softlockApps)
            SP.hardlockApps = json.encodeToString(c.hardlockApps)
        } catch (e: Exception) {
            Log.e(TAG, "Save lists failed", e)
            return
        }
        // Khóa ngay các app mới (best-effort, pre-login vẫn chạy nếu DPM đã active)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Privilege.initialize(context.applicationContext)
                val all = (c.softlockApps + c.hardlockApps).distinct().toTypedArray()
                if (all.isNotEmpty()) {
                    try {
                        Privilege.DPM.setPackagesSuspended(Privilege.DAR, all, true)
                    } catch (e: Exception) {
                        Log.w(TAG, "Suspend after sync skipped: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Apply suspend skipped: ${e.message}")
        }
    }
}
