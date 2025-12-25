package com.bintianqi.owndroid

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * Data class to store blocked app state
 */
@Serializable
data class BlockedAppState(
    val packageName: String,
    val isSuspended: Boolean,
    val isHidden: Boolean
)

/**
 * Manager for temporary unlock feature.
 * When activated, saves blocked apps state, removes restrictions for 10 minutes, then auto-relocks.
 */
object TempUnlockManager {
    
    private const val TAG = "TempUnlockManager"
    private const val TEMP_UNLOCK_DURATION_MINUTES = 10L
    private const val WORK_NAME = "temp_unlock_relock"
    
    // Night mode hours (22:00 - 07:00)
    private const val NIGHT_START_HOUR = 22
    private const val NIGHT_END_HOUR = 7
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Check if current time is in night mode (22:00 - 07:00)
     * During night mode, temp unlock is disabled
     */
    fun isNightMode(): Boolean {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR
    }
    
    /**
     * Get minutes until night mode ends (returns 0 if not in night mode)
     */
    fun getMinutesUntilNightModeEnds(): Int {
        if (!isNightMode()) return 0
        
        val cal = java.util.Calendar.getInstance()
        val currentHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = cal.get(java.util.Calendar.MINUTE)
        
        val minutesRemaining = if (currentHour >= NIGHT_START_HOUR) {
            // After 22:00 - calculate to midnight + hours until 7:00
            ((24 - currentHour - 1) * 60) + (60 - currentMinute) + (NIGHT_END_HOUR * 60)
        } else {
            // Before 7:00 - calculate to 7:00
            ((NIGHT_END_HOUR - currentHour - 1) * 60) + (60 - currentMinute)
        }
        return minutesRemaining
    }
    
    /**
     * Get list of currently blocked apps (suspended or hidden)
     * Uses DPM APIs to properly detect suspended/hidden state
     */
    fun getBlockedApps(context: Context): List<BlockedAppState> {
        val blockedApps = mutableListOf<BlockedAppState>()
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return blockedApps
        
        try {
            val pm = context.packageManager
            // Get all installed packages including disabled/hidden ones
            val installedPackages = pm.getInstalledPackages(
                PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES
            )
            
            for (pkg in installedPackages) {
                val packageName = pkg.packageName
                try {
                    // Use DPM to check hidden state
                    val isHidden = try {
                        Privilege.DPM.isApplicationHidden(Privilege.DAR, packageName)
                    } catch (e: Exception) {
                        false
                    }
                    
                    // Check if suspended using ApplicationInfo flags
                    val isSuspended = try {
                        val appInfo = pm.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            (appInfo.flags and ApplicationInfo.FLAG_SUSPENDED) != 0
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        false
                    }
                    
                    if (isHidden || isSuspended) {
                        blockedApps.add(BlockedAppState(packageName, isSuspended, isHidden))
                        Log.d(TAG, "Found blocked app: $packageName (suspended=$isSuspended, hidden=$isHidden)")
                    }
                } catch (e: Exception) {
                    // Ignore individual package errors
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting blocked apps", e)
        }
        
        Log.d(TAG, "Total blocked apps found: ${blockedApps.size}")
        return blockedApps
    }
    
    /**
     * Activate temporary unlock for 10 minutes.
     * - Saves current blocked apps state
     * - Unblocks all suspended/hidden apps
     * - Clears restrictions
     * - Schedules auto-relock via WorkManager
     */
    fun activateTempUnlock(context: Context): Int {
        var unlockedCount = 0
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // 1. Save current blocked apps state
                val blockedApps = getBlockedApps(context)
                SP.blockedAppsJson = json.encodeToString(blockedApps)
                Log.d(TAG, "Saved ${blockedApps.size} blocked apps: ${blockedApps.map { it.packageName }}")
                
                // 2. Unblock all apps - ALWAYS try both unsuspend AND unhide
                for (app in blockedApps) {
                    try {
                        // Always try to unsuspend (regardless of isSuspended flag detection)
                        val unsuspendResult = Privilege.DPM.setPackagesSuspended(Privilege.DAR, arrayOf(app.packageName), false)
                        if (unsuspendResult.isEmpty()) {
                            Log.d(TAG, "Unsuspended: ${app.packageName}")
                        }
                        
                        // Always try to unhide (regardless of isHidden flag detection)
                        val unhideResult = Privilege.DPM.setApplicationHidden(Privilege.DAR, app.packageName, false)
                        if (unhideResult) {
                            Log.d(TAG, "Unhidden: ${app.packageName}")
                        }
                        
                        unlockedCount++
                    } catch (e: Exception) {
                        Log.e(TAG, "Error unblocking ${app.packageName}", e)
                    }
                }
                
                // 3. Clear install apps restriction
                Privilege.DPM.clearUserRestriction(Privilege.DAR, UserManager.DISALLOW_INSTALL_APPS)
                
                // 4. Clear always-on VPN
                Privilege.DPM.setAlwaysOnVpnPackage(Privilege.DAR, null, false)
                
                // 5. Clear VPN config restriction  
                Privilege.DPM.clearUserRestriction(Privilege.DAR, UserManager.DISALLOW_CONFIG_VPN)
                
                // 6. Clear private DNS config restriction
                Privilege.DPM.clearUserRestriction(Privilege.DAR, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
            }
            
            // Save unlock end time
            SP.tempUnlockEndTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(TEMP_UNLOCK_DURATION_MINUTES)
            SP.isTempUnlockActive = true
            
            // Schedule relock worker
            scheduleRelockWorker(context)
            
            Log.d(TAG, "Temp unlock activated, unlocked $unlockedCount apps")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error activating temp unlock", e)
        }
        return unlockedCount
    }
    
    /**
     * Deactivate temporary unlock - restore all previous blocked apps and restrictions.
     */
    fun deactivateTempUnlock(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // 1. Restore blocked apps from saved state
                val blockedAppsJsonStr = SP.blockedAppsJson
                if (!blockedAppsJsonStr.isNullOrEmpty()) {
                    try {
                        val blockedApps = json.decodeFromString<List<BlockedAppState>>(blockedAppsJsonStr)
                        Log.d(TAG, "Restoring ${blockedApps.size} blocked apps")
                        
                        for (app in blockedApps) {
                            try {
                                // Re-suspend if it was suspended
                                if (app.isSuspended) {
                                    Privilege.DPM.setPackagesSuspended(Privilege.DAR, arrayOf(app.packageName), true)
                                    Log.d(TAG, "Re-suspended: ${app.packageName}")
                                }
                                // Re-hide if it was hidden
                                if (app.isHidden) {
                                    Privilege.DPM.setApplicationHidden(Privilege.DAR, app.packageName, true)
                                    Log.d(TAG, "Re-hidden: ${app.packageName}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error restoring block for ${app.packageName}", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing blocked apps JSON", e)
                    }
                }
                
                val naqdns = "naq.dns"
                
                // 2. Re-add install apps restriction
                Privilege.DPM.addUserRestriction(Privilege.DAR, UserManager.DISALLOW_INSTALL_APPS)
                
                // 3. Re-set always-on VPN
                val allowlist: MutableSet<String?> = HashSet()
                allowlist.add("com.facebook.adsmanager")
                allowlist.add("com.facebook.orca")
                allowlist.add("com.facebook.pages.app")
                Privilege.DPM.setAlwaysOnVpnPackage(Privilege.DAR, naqdns, false, allowlist)
                
                // 4. Re-add VPN config restriction
                Privilege.DPM.addUserRestriction(Privilege.DAR, UserManager.DISALLOW_CONFIG_VPN)
                
                // 5. Re-add private DNS config restriction
                Privilege.DPM.addUserRestriction(Privilege.DAR, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
                
                // 6. Block uninstall for VPN app
                Privilege.DPM.setUninstallBlocked(Privilege.DAR, naqdns, true)
                
                // 7. Disable user control for VPN app
                val current = Privilege.DPM.getUserControlDisabledPackages(Privilege.DAR)
                if (!current.contains(naqdns)) {
                    Privilege.DPM.setUserControlDisabledPackages(Privilege.DAR, current.plus(naqdns))
                }
            }
            
            // Clear unlock state
            SP.tempUnlockEndTime = 0L
            SP.isTempUnlockActive = false
            SP.blockedAppsJson = null
            
            Log.d(TAG, "Temp unlock deactivated")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deactivating temp unlock", e)
        }
    }
    
    /**
     * Get saved blocked apps list (for display in UI)
     */
    fun getSavedBlockedApps(): List<BlockedAppState> {
        val jsonStr = SP.blockedAppsJson ?: return emptyList()
        return try {
            json.decodeFromString(jsonStr)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Check if temporary unlock is currently active.
     */
    fun isUnlockActive(): Boolean {
        if (!SP.isTempUnlockActive) return false
        return System.currentTimeMillis() < SP.tempUnlockEndTime
    }
    
    /**
     * Get remaining unlock time in milliseconds.
     */
    fun getRemainingTimeMillis(): Long {
        if (!isUnlockActive()) return 0L
        return SP.tempUnlockEndTime - System.currentTimeMillis()
    }
    
    private fun scheduleRelockWorker(context: Context) {
        val relockRequest = OneTimeWorkRequestBuilder<RelockWorker>()
            .setInitialDelay(TEMP_UNLOCK_DURATION_MINUTES, TimeUnit.MINUTES)
            .build()
        
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, relockRequest)
    }
    
    /**
     * Cancel any pending relock work.
     */
    fun cancelRelock(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
