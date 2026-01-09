package com.bintianqi.owndroid

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * WorkManager Worker that re-applies restrictions after temporary unlock expires.
 * This runs in background even if app is killed.
 */
class RelockWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    
    companion object {
        private const val TAG = "RelockWorker"
    }
    
    override fun doWork(): Result {
        Log.d(TAG, "========== RelockWorker STARTED ==========")
        Log.d(TAG, "Time: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
        return try {
            // Ensure SP is initialized (in case app was killed)
            if (!isSPInitialized()) {
                SP = SharedPrefs(applicationContext)
                Log.d(TAG, "SP initialized in worker")
            }
            
            Log.d(TAG, "SP.isTempUnlockActive = ${SP.isTempUnlockActive}")
            Log.d(TAG, "SP.tempUnlockEndTime = ${SP.tempUnlockEndTime}")
            
            // Ensure Privilege is initialized
            Privilege.initialize(applicationContext)
            Privilege.updateStatus()
            Log.d(TAG, "Privilege initialized. DPM active = ${Privilege.DPM != null}")
            
            // Get block minutes from SharedPrefs (saved when unlock was activated)
            val blockMinutes = SP.lastUsedBlockMinutes
            Log.d(TAG, "Block minutes: $blockMinutes")
            
            // Execute relock with block time
            Log.d(TAG, "Calling deactivateWithBlock($blockMinutes)...")
            TempUnlockManager.deactivateWithBlock(applicationContext, blockMinutes)
            
            Log.d(TAG, "========== RelockWorker COMPLETED ==========")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "========== RelockWorker FAILED ==========")
            Log.e(TAG, "Error: ${e.message}", e)
            e.printStackTrace()
            Result.failure()
        }
    }
}

