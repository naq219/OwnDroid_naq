package com.bintianqi.owndroid

import android.content.Context
import android.os.Build
import android.os.UserManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Manager for temporary unlock feature.
 * When activated, removes restrictions for 10 minutes, then auto-relocks.
 */
object TempUnlockManager {
    
    private const val TEMP_UNLOCK_DURATION_MINUTES = 10L
    private const val WORK_NAME = "temp_unlock_relock"
    
    /**
     * Activate temporary unlock for 10 minutes.
     * - Clears DISALLOW_INSTALL_APPS restriction
     * - Clears always-on VPN
     * - Schedules auto-relock via WorkManager
     */
    fun activateTempUnlock(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // 1. Clear install apps restriction
                Privilege.DPM.clearUserRestriction(Privilege.DAR, UserManager.DISALLOW_INSTALL_APPS)
                
                // 2. Clear always-on VPN
                Privilege.DPM.setAlwaysOnVpnPackage(Privilege.DAR, null, false)
                
                // 3. Clear VPN config restriction  
                Privilege.DPM.clearUserRestriction(Privilege.DAR, UserManager.DISALLOW_CONFIG_VPN)
                
                // 4. Clear private DNS config restriction
                Privilege.DPM.clearUserRestriction(Privilege.DAR, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
            }
            
            // Save unlock end time
            SP.tempUnlockEndTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(TEMP_UNLOCK_DURATION_MINUTES)
            SP.isTempUnlockActive = true
            
            // Schedule relock worker
            scheduleRelockWorker(context)
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Deactivate temporary unlock - re-apply all restrictions.
     */
    fun deactivateTempUnlock(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val naqdns = "naq.dns"
                
                // 1. Re-add install apps restriction
                Privilege.DPM.addUserRestriction(Privilege.DAR, UserManager.DISALLOW_INSTALL_APPS)
                
                // 2. Re-set always-on VPN
                val allowlist: MutableSet<String?> = HashSet()
                allowlist.add("com.facebook.adsmanager")
                allowlist.add("com.facebook.orca")
                allowlist.add("com.facebook.pages.app")
                Privilege.DPM.setAlwaysOnVpnPackage(Privilege.DAR, naqdns, false, allowlist)
                
                // 3. Re-add VPN config restriction
                Privilege.DPM.addUserRestriction(Privilege.DAR, UserManager.DISALLOW_CONFIG_VPN)
                
                // 4. Re-add private DNS config restriction
                Privilege.DPM.addUserRestriction(Privilege.DAR, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
                
                // 5. Block uninstall for VPN app
                Privilege.DPM.setUninstallBlocked(Privilege.DAR, naqdns, true)
                
                // 6. Disable user control for VPN app
                val current = Privilege.DPM.getUserControlDisabledPackages(Privilege.DAR)
                if (!current.contains(naqdns)) {
                    Privilege.DPM.setUserControlDisabledPackages(Privilege.DAR, current.plus(naqdns))
                }
            }
            
            // Clear unlock state
            SP.tempUnlockEndTime = 0L
            SP.isTempUnlockActive = false
            
        } catch (e: Exception) {
            e.printStackTrace()
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
