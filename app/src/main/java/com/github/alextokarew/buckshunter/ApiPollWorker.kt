package com.github.alextokarew.buckshunter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import androidx.work.*
import java.util.concurrent.TimeUnit

class ApiPollWorker(val context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    companion object {
        val TAG = "api.poll.worker"
        val CHANNEL_ID = "new.atm.channel"
        val PREV_RESULT = "prev_result"

        fun scheduleNextExecution(context: Context, prevResult: String?) {
            Log.i("Poll worker", "Trying to enqueue the next execution")
            val workManager = WorkManager.getInstance(context)
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val delay: Long = prefs.getString(context.resources.getString(R.string.pref_period_min), "2")!!.toLong()

            val dataBuilder = Data.Builder()
            if (prevResult != null) {
                dataBuilder.putString(PREV_RESULT, prevResult)
            }

            val taskRequest = OneTimeWorkRequestBuilder<ApiPollWorker>()
                .setInitialDelay(3, TimeUnit.SECONDS)
//                .setInitialDelay(delay, TimeUnit.MINUTES)
                .addTag(TAG)
                .setInputData(dataBuilder.build())
                .build()
            workManager.enqueue(taskRequest)
            Log.i("Poll worker", "The next task was enqueued after $delay minutes")
        }
    }

    override fun doWork(): Result {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val prevResult = inputData.getString(PREV_RESULT)
        doIteration(prefs, prevResult)
        if (prefs.getBoolean(context.resources.getString(R.string.pref_scan_enabled), false)) {
            scheduleNextExecution(context, "Some prev result")
        }
        return Result.success()
    }

    private fun doIteration(prefs: SharedPreferences, prevResult: String?) {
        Log.i("Poll worker", "Prev result: ${prevResult ?: "NO PREV RESULT"}")
        Log.i("Poll worker", "Reading distance from prefs: ${prefs.getInt(context.resources.getString(R.string.pref_max_distance), -1)}")
        //Query coordinartes
        //Push notification

    }

    private fun pushNotification(message: String) {
        createNotificationChannel()
        val nm = NotificationManagerCompat.from(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("New ATM found!")
            .setContentText("ID: id, Address: address")
            .build()
        val notificationId: Int = (System.currentTimeMillis() / 1000).toInt()
        nm.notify(notificationId, notification)
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library

        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, "ATM Alerts", importance).apply {
            description = "ATM alerting channel"
            enableVibration(true)
        }
        // Register the channel with the system
        val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}