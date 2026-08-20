package com.bintianqi.owndroid

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * WorkManager Worker that lifts the strict lock (khoá chặt chẽ) automatically
 * after its configured number of days has elapsed.
 */
class StrictLockWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val TAG = "StrictLockWorker"
    }

    override fun doWork(): Result {
        Log.d(TAG, "========== StrictLockWorker STARTED ==========")
        return try {
            // Ensure SP is initialized (in case app was killed)
            if (!isSPInitialized()) {
                SP = SharedPrefs(applicationContext)
                Log.d(TAG, "SP initialized in worker")
            }

            // Ensure Privilege is initialized
            Privilege.initialize(applicationContext)
            Privilege.updateStatus()

            Log.d(TAG, "SP.strictLockEndTime = ${SP.strictLockEndTime}, now = ${System.currentTimeMillis()}")

            // Only lift if the strict lock has really expired
            if (!TempUnlockManager.isStrictLockActive()) {
                TempUnlockManager.deactivateStrictLock(applicationContext)
                Log.d(TAG, "========== StrictLockWorker COMPLETED ==========")
            } else {
                Log.d(TAG, "Strict lock still active, skipping")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "========== StrictLockWorker FAILED ==========")
            Log.e(TAG, "Error: ${e.message}", e)
            e.printStackTrace()
            Result.failure()
        }
    }
}
