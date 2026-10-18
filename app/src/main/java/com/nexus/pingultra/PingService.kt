package com.nexus.pingultra

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.IBinder
import android.os.Build
import java.net.Socket
import java.net.InetSocketAddress
import kotlin.concurrent.thread
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PingService : Service() {

    companion object {

        @Volatile
        var running = false

        @Volatile
        var lastPing = -1L

        @Volatile
        var sent = 0

        @Volatile
        var failed = 0

        @Volatile
        var averagePing = 0L

        @Volatile
        var logs = ""

        private const val CHANNEL = "nexus_monitor"
    }

    private var worker: Thread? = null
    private var generation = 0

    override fun onCreate() {
        super.onCreate()

        val manager = getSystemService(
            NOTIFICATION_SERVICE
        ) as NotificationManager

        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL,
                "Nexus Network Monitor",
                NotificationManager.IMPORTANCE_LOW
            )

            manager.createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification {

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("NEXUS ULTRA X")
            .setContentText("Network monitoring active")
            .setSmallIcon(
                android.R.drawable.stat_notify_sync
            )
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (running) return START_STICKY

        val host = intent?.getStringExtra("host")
            ?: "1.1.1.1"

        val interval = intent?.getIntExtra(
            "interval", 15
        )?.coerceIn(5, 60) ?: 15

        startForeground(1, notification())

        running = true
        sent = 0
        failed = 0
        averagePing = 0
        lastPing = -1
        logs = ""

        val token = ++generation

        worker = thread {

            var totalLatency = 0L
            var successful = 0L

            while (running && token == generation) {

                val start = System.nanoTime()

                val result = try {

                    Socket().use { socket ->
                        socket.connect(
                            InetSocketAddress(host, 443),
                            2000
                        )
                    }

                    (System.nanoTime() - start) / 1_000_000

                } catch (e: Exception) {
                    -1L
                }

                if (!running || token != generation) break

                sent++

                lastPing = result

                if (result < 0) {
                    failed++
                } else {
                    successful++
                    totalLatency += result
                    averagePing = totalLatency / successful
                }

                val time = SimpleDateFormat(
                    "HH:mm:ss",
                    Locale.US
                ).format(Date())

                val message = if (result >= 0) {
                    "[$time] TCP: ${result}ms"
                } else {
                    "[$time] CONNECTION FAILED"
                }

                logs = "$message\n$logs"
                    .take(3000)

                try {
                    Thread.sleep(interval * 1000L)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        generation++
        worker?.interrupt()
        worker = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
