package com.bintianqi.owndroid

import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * Manager for temporary unlock feature with Hardlock/Softlock support.
 * 
 * - Hardlock: Apps permanently locked, NEVER unlocked during temp unlock
 * - Softlock: Apps that CAN be unlocked during temp unlock period
 */
object TempUnlockManager {
    
    private const val TAG = "TempUnlockManager"
    private const val TEMP_UNLOCK_DURATION_MINUTES = 1L // TEST: 1 minute
    private const val WORK_NAME = "temp_unlock_relock"
    
    // Night mode hours (22:00 - 07:00)
    private const val NIGHT_START_HOUR = 22
    private const val NIGHT_END_HOUR = 7
    
    private val json = Json { ignoreUnknownKeys = true }
    
    // ============= SOFTLOCK LIST MANAGEMENT =============
    
    /**
     * Get list of softlock apps (can be unlocked during temp unlock)
     */
    fun getSoftlockApps(): List<String> {
        val jsonStr = SP.softlockApps
        Log.d(TAG, "getSoftlockApps: raw SP.softlockApps = $jsonStr")
        if (jsonStr == null) return emptyList()
        return try {
            val result: List<String> = json.decodeFromString(jsonStr)
            Log.d(TAG, "getSoftlockApps: parsed = $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing softlock apps", e)
            emptyList()
        }
    }
    
    /**
     * Add app to softlock list
     */
    fun addToSoftlock(packageName: String) {
        val current = getSoftlockApps().toMutableList()
        if (!current.contains(packageName)) {
            current.add(packageName)
            SP.softlockApps = json.encodeToString(current)
            Log.d(TAG, "Added to softlock: $packageName")
        }
    }
    
    /**
     * Remove app from softlock list
     */
    fun removeFromSoftlock(packageName: String) {
        val current = getSoftlockApps().toMutableList()
        if (current.remove(packageName)) {
            SP.softlockApps = json.encodeToString(current)
            Log.d(TAG, "Removed from softlock: $packageName")
        }
    }
    
    // ============= HARDLOCK LIST MANAGEMENT =============
    
    /**
     * Get list of hardlock apps (permanently locked)
     */
    fun getHardlockApps(): List<String> {
        val jsonStr = SP.hardlockApps ?: return emptyList()
        return try {
            json.decodeFromString(jsonStr)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing hardlock apps", e)
            emptyList()
        }
    }
    
    /**
     * Add app to hardlock list
     */
    fun addToHardlock(packageName: String) {
        val current = getHardlockApps().toMutableList()
        if (!current.contains(packageName)) {
            current.add(packageName)
            SP.hardlockApps = json.encodeToString(current)
            Log.d(TAG, "Added to hardlock: $packageName")
        }
        // Also remove from softlock if exists
        removeFromSoftlock(packageName)
    }
    
    /**
     * Remove app from hardlock list
     */
    fun removeFromHardlock(packageName: String) {
        val current = getHardlockApps().toMutableList()
        if (current.remove(packageName)) {
            SP.hardlockApps = json.encodeToString(current)
            Log.d(TAG, "Removed from hardlock: $packageName")
        }
    }
    
    /**
     * Move app from softlock to hardlock
     */
    fun moveToHardlock(packageName: String) {
        removeFromSoftlock(packageName)
        addToHardlock(packageName)
    }
    
    /**
     * Move app from hardlock to softlock
     */
    fun moveToSoftlock(packageName: String) {
        removeFromHardlock(packageName)
        addToSoftlock(packageName)
    }
    
    // ============= LOCK/UNLOCK OPERATIONS =============
    
    /**
     * Suspend a single app immediately
     */
    fun suspendApp(packageName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            val result = Privilege.DPM.setPackagesSuspended(Privilege.DAR, arrayOf(packageName), true)
            val success = result.isEmpty()
            Log.d(TAG, "Suspend $packageName: ${if (success) "OK" else "FAILED"}")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error suspending $packageName", e)
            false
        }
    }
    
    /**
     * Unsuspend a single app
     */
    fun unsuspendApp(packageName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            val result = Privilege.DPM.setPackagesSuspended(Privilege.DAR, arrayOf(packageName), false)
            val success = result.isEmpty()
            Log.d(TAG, "Unsuspend $packageName: ${if (success) "OK" else "FAILED"}")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error unsuspending $packageName", e)
            false
        }
    }
    
    /**
     * Unhide a single app (make it visible in launcher again)
     */
    fun unhideApp(packageName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            val success = Privilege.DPM.setApplicationHidden(Privilege.DAR, packageName, false)
            Log.d(TAG, "Unhide $packageName: ${if (success) "OK" else "FAILED"}")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error unhiding $packageName", e)
            false
        }
    }
    
    /**
     * Lock all apps in softlock list (suspend them)
     */
    fun lockAllSoftlockApps() {
        val apps = getSoftlockApps()
        Log.d(TAG, "Locking ${apps.size} softlock apps")
        for (pkg in apps) {
            suspendApp(pkg)
        }
    }
    
    /**
     * Unlock all apps in softlock list (unsuspend them)
     */
    fun unlockAllSoftlockApps() {
        val apps = getSoftlockApps()
        Log.d(TAG, "Unlocking ${apps.size} softlock apps")
        for (pkg in apps) {
            unsuspendApp(pkg)
        }
    }
    
    // ============= NIGHT MODE =============
    
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
    
    // ============= TEMP UNLOCK =============
    
    /**
     * Activate temporary unlock for 10 minutes.
     * - Unlocks all SOFTLOCK apps (unsuspend)
     * - HARDLOCK apps remain locked
     * - Clears restrictions
     * - Schedules auto-relock via WorkManager
     */
    fun activateTempUnlock(context: Context): Int {
        var unlockedCount = 0
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // 1. Unlock all SOFTLOCK apps only
                val softlockApps = getSoftlockApps()
                Log.d(TAG, "Temp unlock: softlockApps = $softlockApps")
                Log.d(TAG, "Temp unlock: unlocking ${softlockApps.size} softlock apps")
                for (pkg in softlockApps) {
                    unsuspendApp(pkg)
                    unhideApp(pkg)
                    unlockedCount++
                }
                
                // 2. Clear install apps restriction
                Privilege.DPM.clearUserRestriction(Privilege.DAR, UserManager.DISALLOW_INSTALL_APPS)
                
                // 3. Clear always-on VPN
                Privilege.DPM.setAlwaysOnVpnPackage(Privilege.DAR, null, false)
                
                // 4. Clear VPN config restriction  
                Privilege.DPM.clearUserRestriction(Privilege.DAR, UserManager.DISALLOW_CONFIG_VPN)
                
                // 5. Clear private DNS config restriction
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
     * Deactivate temporary unlock - re-lock all SOFTLOCK apps and restore restrictions.
     * HARDLOCK apps are not affected (they're always locked)
     */
    fun deactivateTempUnlock(context: Context) {
        Log.d(TAG, "========== deactivateTempUnlock START ==========")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // 1. Re-lock all SOFTLOCK apps
                Log.d(TAG, "Step 1: Locking all softlock apps...")
                lockAllSoftlockApps()
                Log.d(TAG, "Step 1: DONE")
                
                val naqdns = "naq.dns"
                
                // 2. Re-add install apps restriction
                Log.d(TAG, "Step 2: Adding DISALLOW_INSTALL_APPS restriction...")
                Privilege.DPM.addUserRestriction(Privilege.DAR, UserManager.DISALLOW_INSTALL_APPS)
                Log.d(TAG, "Step 2: DONE")
                
                // 3. Re-set always-on VPN
                Log.d(TAG, "Step 3: Setting VPN to $naqdns...")
                val allowlist: MutableSet<String?> = HashSet()
                allowlist.add("com.facebook.adsmanager")
                allowlist.add("com.facebook.orca")
                allowlist.add("com.facebook.pages.app")
                Privilege.DPM.setAlwaysOnVpnPackage(Privilege.DAR, naqdns, false, allowlist)
                Log.d(TAG, "Step 3: VPN SET DONE")
                
                // 4. Re-add VPN config restriction
                Log.d(TAG, "Step 4: Adding DISALLOW_CONFIG_VPN...")
                Privilege.DPM.addUserRestriction(Privilege.DAR, UserManager.DISALLOW_CONFIG_VPN)
                Log.d(TAG, "Step 4: DONE")
                
                // 5. Re-add private DNS config restriction
                Log.d(TAG, "Step 5: Adding DISALLOW_CONFIG_PRIVATE_DNS...")
                Privilege.DPM.addUserRestriction(Privilege.DAR, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
                Log.d(TAG, "Step 5: DONE")
                
                // 6. Block uninstall for VPN app
                Log.d(TAG, "Step 6: Block uninstall for $naqdns...")
                Privilege.DPM.setUninstallBlocked(Privilege.DAR, naqdns, true)
                Log.d(TAG, "Step 6: DONE")
                
                // 7. Disable user control for VPN app
                Log.d(TAG, "Step 7: Disable user control...")
                val current = Privilege.DPM.getUserControlDisabledPackages(Privilege.DAR)
                if (!current.contains(naqdns)) {
                    Privilege.DPM.setUserControlDisabledPackages(Privilege.DAR, current.plus(naqdns))
                }
                Log.d(TAG, "Step 7: DONE")
            }
            
            // Clear unlock state
            Log.d(TAG, "Clearing unlock state...")
            SP.tempUnlockEndTime = 0L
            SP.isTempUnlockActive = false
            
            Log.d(TAG, "========== deactivateTempUnlock COMPLETED ==========")
            
        } catch (e: Exception) {
            Log.e(TAG, "========== deactivateTempUnlock FAILED ==========")
            Log.e(TAG, "Error: ${e.message}", e)
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
        // NOTE: Cannot use setExpedited() with setInitialDelay() - they are mutually exclusive
        val relockRequest = OneTimeWorkRequestBuilder<RelockWorker>()
            .setInitialDelay(TEMP_UNLOCK_DURATION_MINUTES, TimeUnit.MINUTES)
            .build()
        
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, relockRequest)
        
        Log.d(TAG, "Scheduled RelockWorker to run in $TEMP_UNLOCK_DURATION_MINUTES minutes")
    }
    
    /**
     * Cancel any pending relock work.
     */
    fun cancelRelock(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

