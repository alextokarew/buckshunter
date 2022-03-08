package com.github.alextokarew.buckshunter

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.*
import java.util.concurrent.TimeUnit

class ApiPollWorker(val context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    companion object {
        val TAG = "api.poll.worker"

        fun scheduleNextExecution(context: Context) {
            Log.i("Poll worker", "Trying to enqueue the next execution")
            val workManager = WorkManager.getInstance(context)

            val taskRequest = OneTimeWorkRequestBuilder<ApiPollWorker>()
                .setInitialDelay(3, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()
            workManager.enqueue(taskRequest)
            Log.i("Poll worker", "The next task was enqueued")
        }
    }

    override fun doWork(): Result {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        Log.i("Poll worker", "Reading distance from prefs: ${prefs.getInt(context.resources.getString(R.string.pref_max_distance), -1)}")
        if (prefs.getBoolean(context.resources.getString(R.string.pref_scan_enabled), false)) {
            scheduleNextExecution(context)
        }
        return Result.success()
    }
}