package com.github.alextokarew.buckshunter

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import androidx.work.*
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.LocationServices
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

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
                .setInitialDelay(10, TimeUnit.SECONDS)
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
        val currentResult = doIteration(prefs, prevResult)
        if (prefs.getBoolean(context.resources.getString(R.string.pref_scan_enabled), false)) {
            scheduleNextExecution(context, currentResult)
        }
        return Result.success()
    }

    private fun doIteration(prefs: SharedPreferences, prevResult: String?): String {
        Log.i("Poll worker", "Prev result: ${prevResult ?: "NO PREV RESULT"}")
        val maxDistance = prefs.getInt(context.resources.getString(R.string.pref_max_distance), -1)
        val prevAtmPoints = prevResult?.split('\n')?.map { AtmPoint.fromString(it) } ?: emptyList()
        val atmPoints = retrieveAtmData()
        val currentResult = atmPoints.map { it.toString() }.joinToString(separator = "\n")
        val location = getCurrentLocation()

        val closestPoints = atmPoints.sortedBy { calculateDistance(location, it) }
        for(point in closestPoints) {
            Log.i("CLOSEST POINT", point.toString())
        }

        if (prevAtmPoints.isNotEmpty()) {
            val diff = calculateDifference(atmPoints, prevAtmPoints)
            for(point in diff) {
                val distance = calculateDistance(location, point)
                if (distance <= maxDistance) {
                    pushNotification(point.address)
                }
            }
        }

        //Push notification
//        pushNotification("Location: ${location.latitude} ${location.longitude}")
        return currentResult
    }

    private fun calculateDifference(
        atmPoints: List<AtmPoint>,
        prevAtmPoints: List<AtmPoint>
    ): List<AtmPoint> {
        val prevIds = prevAtmPoints.map { it.id }.toSet()
        return atmPoints.filter { !prevIds.contains(it.id) }
    }

    private fun calculateDistance(location: Location, point: AtmPoint): Double {
        val resArray = FloatArray(1, { 0.0f })
        Location.distanceBetween(location.latitude, location.longitude, point.lat, point.lon, resArray)
        return resArray[0].toDouble() / 1000
    }


    @SuppressLint("MissingPermission")
    private fun getCurrentLocation(): Location {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val countDownLatch = CountDownLatch(1)
        val locationHolder = AtomicReference<Location>(null)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            locationHolder.set(location)
            countDownLatch.countDown()
        }
        countDownLatch.await()

        Log.i("LOCATION", locationHolder.get().toString())
        return locationHolder.get()
    }

    private fun retrieveAtmData(): List<AtmPoint> {
        val queue = Volley.newRequestQueue(context)
        val url = "https://api.tinkoff.ru/geo/withdraw/clusters"
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val latch = CountDownLatch(1)
        val resultHolder = AtomicReference<List<AtmPoint>>(emptyList())

        val currency = prefs.getString(context.resources.getString(R.string.pref_currency), "USD")
        val minAmount = prefs.getString(context.resources.getString(R.string.pref_min_amount), "4000")!!.toInt()

        val responseListener = Response.Listener<JSONObject> { response ->
            if (response.getString("status") == "Ok") {
                val out = ArrayList<AtmPoint>()
                val clusters = response.getJSONObject("payload").getJSONArray("clusters")
                for(i in 0 until clusters.length()) {
                    val cluster = clusters.getJSONObject(i)
                    val points = cluster.getJSONArray("points")
                    for(j in 0 until points.length()) {
                        val point = points.getJSONObject(j)

                        val id = point.getString("id")
                        val address = point.getString("address")
                        val location = point.getJSONObject("location")
                        val lat = location.getDouble("lat")
                        val lng = location.getDouble("lng")
                        val limits = point.getJSONArray("limits")
                        val amountIdx = (0 until limits.length()).find { limits.getJSONObject(it).getString("currency") == currency }
                        val amount = limits.getJSONObject(amountIdx!!).getInt("amount")

                        if (amount >= minAmount) {
                            out.add(AtmPoint(id, address, amount, lat, lng))
                        }

                    }
                }
                out.sortBy { it.id }
                resultHolder.set(out)
                latch.countDown()
            }
        }

        val errorListener = Response.ErrorListener { error ->
            Log.e("Network error", error.networkResponse.statusCode.toString())
            latch.countDown()
        }


        val bottomLeftLat = prefs.getString(context.resources.getString(R.string.pref_bottom_left_lat), "55.0")
        val bottomLeftLng = prefs.getString(context.resources.getString(R.string.pref_bottom_left_lng), "37.2")
        val topRightLat = prefs.getString(context.resources.getString(R.string.pref_top_right_lat), "56.0")
        val topRightLng = prefs.getString(context.resources.getString(R.string.pref_top_right_lng), "38.3")

        val req = """{"bounds":{
            "bottomLeft":{"lat":$bottomLeftLat,"lng":$bottomLeftLng},
            "topRight":{"lat":$topRightLat,"lng":$topRightLng}},
            "filters":{"banks":["tcs"],"showUnavailable":False,"currencies":["$currency"]},"zoom":10
        }"""

        val requestBody = JSONObject(req)
        val request = JsonObjectRequest(Request.Method.POST, url, requestBody, responseListener, errorListener)

        queue.add(request)
        latch.await()
        return resultHolder.get()
    }

    private fun pushNotification(message: String) {
        createNotificationChannel()
        val nm = NotificationManagerCompat.from(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("New ATM found!")
            .setContentText(message)
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