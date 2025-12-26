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
        Log.d(TAG, "RelockWorker started - doWork()")
        return try {
            // Ensure SP is initialized (in case app was killed)
            if (!isSPInitialized()) {
                SP = SharedPrefs(applicationContext)
                Log.d(TAG, "SP initialized in worker")
            }
            
            // Ensure Privilege is initialized
            Privilege.initialize(applicationContext)
            Privilege.updateStatus()
            Log.d(TAG, "Privilege initialized in worker")
            
            // Execute relock
            TempUnlockManager.deactivateTempUnlock(applicationContext)
            Log.d(TAG, "RelockWorker completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "RelockWorker failed", e)
            e.printStackTrace()
            Result.failure()
        }
    }
}

