package com.bintianqi.owndroid

import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import android.content.pm.ApplicationInfo
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
    private val TEMP_UNLOCK_DURATION_MINUTES = AppConfig.TEMP_UNLOCK_DURATION_MINUTES
    private val DAY_UNLOCK_DURATION_MINUTES = AppConfig.DAY_UNLOCK_DURATION_MINUTES
    private const val WORK_NAME = "temp_unlock_relock"
    
    // Night mode hours from config
    private val NIGHT_START_HOUR = AppConfig.NIGHT_MODE_START_HOUR
    private val NIGHT_END_HOUR = AppConfig.NIGHT_MODE_END_HOUR
    
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

    // ============= APP QUERY HELPERS =============

    /**
     * Get all user installed apps (excluding system apps and self)
     */
    fun getAllUserInstalledApps(context: Context): List<String> {
        val pm = context.packageManager
        val myPackage = context.packageName
        
        return pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            .filter { appInfo ->
                // Filter out system apps unless they are updated system apps
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                
                // Keep if it's NOT a system app OR if it IS an updated system app
                // Also exclude our own app
                (!isSystem || isUpdatedSystem) && appInfo.packageName != myPackage
            }
            .map { it.packageName }
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
        return activateUnlock(context, TEMP_UNLOCK_DURATION_MINUTES)
    }
    
    /**
     * Activate day unlock for 24 hours.
     * - Unlocks all SOFTLOCK apps (unsuspend)
     * - HARDLOCK apps remain locked
     * - Clears restrictions
     * - Schedules auto-relock via WorkManager after 24 hours
     */
    fun activateDayUnlock(context: Context): Int {
        return activateUnlock(context, DAY_UNLOCK_DURATION_MINUTES)
    }
    
    /**
     * Internal function to activate unlock for specified duration.
     * - Unlocks all SOFTLOCK apps (unsuspend)
     * - HARDLOCK apps remain locked
     * - Clears restrictions
     * - Schedules auto-relock via WorkManager
     */
    private fun activateUnlock(context: Context, durationMinutes: Long): Int {
        var unlockedCount = 0
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Determine which apps to process
                val hardlockApps = getHardlockApps()
                // In ANY mode (Day or Night), unlocking means "Access to everything except Hardlock"
                // This covers the edge case where Night Mode blocked apps, and we unlock in Day Mode
                
                Log.d(TAG, "Unlock: Unlocking ALL user apps except Hardlock")
                val allUserApps = getAllUserInstalledApps(context)
                
                val appsToUnlock = allUserApps.filter { !hardlockApps.contains(it) }
                
                // Batch unsuspend
                if (appsToUnlock.isNotEmpty()) {
                    val result = Privilege.DPM.setPackagesSuspended(Privilege.DAR, appsToUnlock.toTypedArray(), false)
                    if (result.isNotEmpty()) {
                        Log.w(TAG, "Failed to unsuspend some apps: ${result.contentToString()}")
                    }
                }
                
                // Unhide individually
                for (pkg in appsToUnlock) {
                    unhideApp(pkg)
                    unlockedCount++
                }
                
                // Enforce Hardlock safety
                if (hardlockApps.isNotEmpty()) {
                    Privilege.DPM.setPackagesSuspended(Privilege.DAR, hardlockApps.toTypedArray(), true)
                }

                // 2. Clear install apps restriction
                Privilege.DPM.clearUserRestriction(Privilege.DAR, UserManager.DISALLOW_INSTALL_APPS)
                
                // 3. Clear always-on VPN (User requested: Unlock = No VPN)
                Privilege.DPM.setAlwaysOnVpnPackage(Privilege.DAR, null, false)
                
                // 4. Clear VPN config restriction  
                Privilege.DPM.clearUserRestriction(Privilege.DAR, UserManager.DISALLOW_CONFIG_VPN)
                
                // 5. Clear private DNS config restriction
                Privilege.DPM.clearUserRestriction(Privilege.DAR, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
            }
            
            // Save unlock end time
            SP.tempUnlockEndTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(durationMinutes)
            SP.isTempUnlockActive = true
            
            // Schedule relock worker
            scheduleRelockWorker(context, durationMinutes)
            
            Log.d(TAG, "Unlock activated for $durationMinutes min, unlocked $unlockedCount apps")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error activating unlock", e)
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
                val hardlockApps = getHardlockApps()
                val isNight = isNightMode()
                val vpnPackage = AppConfig.VPN_PACKAGE
                
                if (isNight) {
                    // NIGHT MODE LOCK: Block ALL user apps except Whitelist
                    Log.d(TAG, "Mode: NIGHT. Blocking ALL user apps except Whitelist.")
                    
                    val allUserApps = getAllUserInstalledApps(context)
                    val whitelist = AppConfig.getNightModeWhitelist()
                    
                    val appsToSuspend = ArrayList<String>()
                    val appsToUnsuspend = ArrayList<String>()
                    
                    for (pkg in allUserApps) {
                        if (hardlockApps.contains(pkg)) {
                            // Rule 1: Hardlock is always blocked
                            appsToSuspend.add(pkg)
                        } else if (whitelist.contains(pkg)) {
                            // Rule 2: Whitelist (and not hardlock) is allowed
                            appsToUnsuspend.add(pkg)
                        } else {
                            // Rule 3: Everything else is blocked in Night Mode
                            appsToSuspend.add(pkg)
                        }
                    }
                    
                    // Apply Suspend
                    if (appsToSuspend.isNotEmpty()) {
                        Log.d(TAG, "Suspending ${appsToSuspend.size} apps (Hard + Non-Whitelist)")
                        Privilege.DPM.setPackagesSuspended(Privilege.DAR, appsToSuspend.toTypedArray(), true)
                    }
                    
                    // Apply Unsuspend (for whitelist apps that might have been suspended)
                    if (appsToUnsuspend.isNotEmpty()) {
                        Log.d(TAG, "Unsuspending ${appsToUnsuspend.size} whitelist apps")
                        Privilege.DPM.setPackagesSuspended(Privilege.DAR, appsToUnsuspend.toTypedArray(), false)
                    }
                    
                    // Night Mode VPN Setup: Lockdown DISABLED as requested
                    Log.d(TAG, "Setting Night Mode VPN (Lockdown=FALSE)...")
                    val allowlist: MutableSet<String?> = HashSet(whitelist)
                    // Ensure vpn package itself isn't blocked by VPN config logic (though it's the provider)
                    allowlist.add(vpnPackage) 
                    Privilege.DPM.setAlwaysOnVpnPackage(Privilege.DAR, vpnPackage, false, allowlist)
                    
                } else {
                    // DAY MODE LOCK: Block Softlock + Hardlock, Unblock others
                    Log.d(TAG, "Mode: DAY. Blocking Softlock + Hardlock. Unblocking others.")
                    
                    val allUserApps = getAllUserInstalledApps(context)
                    val softlockApps = getSoftlockApps()
                    
                    val appsToSuspend = ArrayList<String>()
                    val appsToUnsuspend = ArrayList<String>()
                    
                    for (pkg in allUserApps) {
                        if (hardlockApps.contains(pkg)) {
                            appsToSuspend.add(pkg)
                        } else if (softlockApps.contains(pkg)) {
                            appsToSuspend.add(pkg)
                        } else {
                            // Normal apps should be open in Day Mode
                            // Important to UNSUSPEND them in case they were blocked by Night Mode
                            appsToUnsuspend.add(pkg)
                        }
                    }
                    
                    if (appsToSuspend.isNotEmpty()) {
                        Log.d(TAG, "Suspending ${appsToSuspend.size} apps (Soft + Hard)")
                        Privilege.DPM.setPackagesSuspended(Privilege.DAR, appsToSuspend.toTypedArray(), true)
                    }
                    
                    if (appsToUnsuspend.isNotEmpty()) {
                         Log.d(TAG, "Unsuspending ${appsToUnsuspend.size} normal apps")
                        Privilege.DPM.setPackagesSuspended(Privilege.DAR, appsToUnsuspend.toTypedArray(), false)
                    }
                    
                    // Day Mode VPN Setup: Standard Allowlist, Lockdown TRUE
                    Log.d(TAG, "Setting Day Mode VPN (Lockdown=TRUE)...")
                    val allowlist: MutableSet<String?> = HashSet(AppConfig.VPN_ALLOWLIST)
                    Privilege.DPM.setAlwaysOnVpnPackage(Privilege.DAR, vpnPackage, true, allowlist)
                }

                // COMMON ACTIONS FOR BOTH MODES
                
                // 2. Re-add install apps restriction
                Log.d(TAG, "Adding DISALLOW_INSTALL_APPS restriction...")
                Privilege.DPM.addUserRestriction(Privilege.DAR, UserManager.DISALLOW_INSTALL_APPS)
                
                // 3. Re-add VPN config restriction
                Privilege.DPM.addUserRestriction(Privilege.DAR, UserManager.DISALLOW_CONFIG_VPN)
                
                // 4. Re-add private DNS config restriction
                Privilege.DPM.addUserRestriction(Privilege.DAR, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
                
                // 5. Block date/time config to prevent bypassing night mode
                Privilege.DPM.addUserRestriction(Privilege.DAR, UserManager.DISALLOW_CONFIG_DATE_TIME)

                // 6. Block uninstall for VPN app
                Privilege.DPM.setUninstallBlocked(Privilege.DAR, vpnPackage, true)
                
                // 7. Disable user control for VPN app
                val current = Privilege.DPM.getUserControlDisabledPackages(Privilege.DAR)
                if (!current.contains(vpnPackage)) {
                    Privilege.DPM.setUserControlDisabledPackages(Privilege.DAR, current.plus(vpnPackage))
                }
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
    
    private fun scheduleRelockWorker(context: Context, durationMinutes: Long = TEMP_UNLOCK_DURATION_MINUTES) {
        // NOTE: Cannot use setExpedited() with setInitialDelay() - they are mutually exclusive
        val relockRequest = OneTimeWorkRequestBuilder<RelockWorker>()
            .setInitialDelay(durationMinutes, TimeUnit.MINUTES)
            .build()
        
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, relockRequest)
        
        Log.d(TAG, "Scheduled RelockWorker to run in $durationMinutes minutes")
    }
    
    /**
     * Cancel any pending relock work.
     */
    fun cancelRelock(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
    
    // ============= BLOCK TIME =============
    
    /**
     * Check if currently in block period
     */
    fun isBlocked(): Boolean {
        val blockEnd = SP.blockEndTime
        return blockEnd > 0 && System.currentTimeMillis() < blockEnd
    }
    
    /**
     * Get remaining block time in milliseconds
     */
    fun getRemainingBlockTimeMillis(): Long {
        if (!isBlocked()) return 0L
        return SP.blockEndTime - System.currentTimeMillis()
    }
    
    /**
     * Set block period
     */
    fun setBlockPeriod(blockMinutes: Long) {
        if (blockMinutes > 0) {
            SP.blockEndTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(blockMinutes)
            SP.lastUsedBlockMinutes = blockMinutes
            Log.d(TAG, "Block period set for $blockMinutes minutes")
        }
    }
    
    /**
     * Clear block period
     */
    fun clearBlockPeriod() {
        SP.blockEndTime = 0L
    }
    
    // ============= TIER-BASED UNLOCK =============
    
    /**
     * Activate unlock with specified tier
     */
    fun activateWithTier(context: Context, tier: AppConfig.UnlockTier): Int {
        // Clear any block period
        clearBlockPeriod()
        
        // Save block minutes for when unlock expires
        SP.lastUsedBlockMinutes = tier.blockMinutes
        
        // Calculate duration (-1 means until morning)
        val durationMinutes = if (tier.unlockMinutes == -1L) {
            getMinutesUntilNightModeEnds().toLong()
        } else {
            tier.unlockMinutes
        }
        
        return activateUnlock(context, durationMinutes)
    }
    
    /**
     * Deactivate unlock with optional block time
     */
    fun deactivateWithBlock(context: Context, blockMinutes: Long = 0L) {
        deactivateTempUnlock(context)
        if (blockMinutes > 0) {
            setBlockPeriod(blockMinutes)
        }
    }
}

