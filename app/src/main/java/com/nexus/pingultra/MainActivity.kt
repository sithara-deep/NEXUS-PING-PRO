package com.nexus.pingultra

import android.app.Activity
import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread
import java.util.Locale

class MainActivity : Activity() {

    private val bg = Color.rgb(3, 5, 14)
    private val neon = Color.rgb(0, 255, 170)
    private val cyan = Color.rgb(0, 220, 255)

    private lateinit var status: TextView
    private lateinit var ping: TextView
    private lateinit var stats: TextView
    private lateinit var logs: TextView
    private lateinit var power: Button

    private var selectedHost = "1.1.1.1"
    private var interval = 15

    private val handler = Handler(Looper.getMainLooper())

    private val updater = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }

        val scroll = ScrollView(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 35, 30, 45)
            setBackgroundColor(bg)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        fun label(
            value: String,
            size: Float,
            color: Int
        ): TextView {
            return TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
                setPadding(8, 18, 8, 18)
            }
        }

        fun button(
            title: String,
            action: () -> Unit
        ): Button {
            return Button(this).apply {
                text = title
                textSize = 16f
                setTextColor(Color.BLACK)
                setBackgroundColor(neon)
                setOnClickListener { action() }
            }
        }

        val title = label(
            "⚡ NEXUS ULTRA X ⚡",
            27f,
            cyan
        ).apply {
            typeface = Typeface.DEFAULT_BOLD
        }

        status = label(
            "STATUS: DISCONNECTED",
            20f,
            Color.RED
        )

        ping = label(
            "-- ms",
            65f,
            neon
        ).apply {
            typeface = Typeface.DEFAULT_BOLD
        }

        power = button("▶ START FIX") {
            if (PingService.running) {
                stopService(
                    Intent(this, PingService::class.java)
                )
            } else {
                val intent = Intent(
                    this,
                    PingService::class.java
                )

                intent.putExtra("host", selectedHost)
                intent.putExtra("interval", interval)

                try {
                    if (Build.VERSION.SDK_INT >= 26) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        "Cannot start service: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            handler.postDelayed({ refresh() }, 500)
        }

        val servers = arrayOf(
            "Cloudflare",
            "Google",
            "Cloudflare Secondary",
            "Google Secondary",
            "Custom Server"
        )

        val spinner = Spinner(this)

        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            servers
        )

        spinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    selectedHost = when (position) {
                        0 -> "1.1.1.1"
                        1 -> "8.8.8.8"
                        2 -> "1.0.0.1"
                        3 -> "8.8.4.4"
                        else -> selectedHost
                    }
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {}
            }

        val custom = EditText(this).apply {
            hint = "Custom Host / IP"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }

        val customButton = button("SET CUSTOM SERVER") {
            val value = custom.text.toString().trim()

            if (value.isNotEmpty()) {
                selectedHost = value
                Toast.makeText(
                    this,
                    "Server: $value",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val intervals = arrayOf(
            "Every 5 Seconds",
            "Every 15 Seconds",
            "Every 30 Seconds"
        )

        val intervalSpinner = Spinner(this)

        intervalSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            intervals
        )

        intervalSpinner.setSelection(1)

        intervalSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    interval = when (position) {
                        0 -> 5
                        1 -> 15
                        else -> 30
                    }
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {}
            }

        stats = label(
            "REQUESTS: 0\nFAILED: 0\nAVG: --",
            18f,
            Color.WHITE
        )

        logs = label(
            "No logs yet",
            14f,
            neon
        ).apply {
            gravity = Gravity.START
            typeface = Typeface.MONOSPACE
        }

        root.addView(title)
        root.addView(status)
        root.addView(ping)
        root.addView(power)

        root.addView(
            label("TARGET NETWORK SERVER", 17f, cyan)
        )

        root.addView(spinner)
        root.addView(custom)
        root.addView(customButton)

        root.addView(
            label("PING INTERVAL", 17f, cyan)
        )

        root.addView(intervalSpinner)

        root.addView(
            label("NETWORK STATISTICS", 22f, neon)
        )

        root.addView(stats)

        root.addView(
            button("🚀 DOWNLOAD SPEED TEST") {
                speedTest()
            }
        )

        root.addView(
            button("📶 NETWORK SETTINGS") {
                startActivity(
                    Intent(Settings.ACTION_WIRELESS_SETTINGS)
                )
            }
        )

        root.addView(
            label("LIVE LOG CONSOLE", 20f, cyan)
        )

        root.addView(logs)

        root.addView(
            label(
                "NEXUS ULTRA X\nAdvanced Network Monitor",
                13f,
                Color.GRAY
            )
        )

        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(updater)
        handler.post(updater)
    }

    override fun onPause() {
        handler.removeCallbacks(updater)
        super.onPause()
    }

    private fun refresh() {

        val active = PingService.running

        status.text = if (active) {
            "STATUS: MONITORING"
        } else {
            "STATUS: DISCONNECTED"
        }

        status.setTextColor(
            if (active) neon else Color.RED
        )

        power.text = if (active) {
            "■ STOP FIX"
        } else {
            "▶ START FIX"
        }

        val value = PingService.lastPing

        ping.text = if (value >= 0) {
            "$value ms"
        } else {
            "-- ms"
        }

        val sent = PingService.sent
        val failed = PingService.failed

        val rate = if (sent > 0) {
            failed * 100 / sent
        } else {
            0
        }

        stats.text =
            "REQUESTS: $sent\n" +
            "FAILED: $failed\n" +
            "FAILURE RATE: $rate%\n" +
            "AVG LATENCY: ${PingService.averagePing} ms"

        logs.text = PingService.logs
    }

    private fun speedTest() {

        Toast.makeText(
            this,
            "Testing Download Speed...",
            Toast.LENGTH_SHORT
        ).show()

        thread {

            var connection: HttpsURLConnection? = null

            try {
                val url = URL(
                    "https://speed.cloudflare.com/__down?bytes=1000000"
                )

                connection =
                    url.openConnection() as HttpsURLConnection

                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.useCaches = false

                val start = System.nanoTime()
                var total = 0L

                connection.inputStream.use { input ->

                    val buffer = ByteArray(8192)

                    while (true) {
                        val count = input.read(buffer)

                        if (count == -1) break

                        total += count
                    }
                }

                val seconds =
                    (System.nanoTime() - start) / 1e9

                val mbps =
                    total * 8.0 / seconds / 1_000_000

                handler.post {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("DOWNLOAD SPEED")
                        .setMessage(
                            String.format(
                                Locale.US,
                                "Speed: %.2f Mbps\nDownloaded: %d KB",
                                mbps,
                                total / 1024
                            )
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }

            } catch (e: Exception) {
                handler.post {
                    Toast.makeText(
                        this,
                        "Speed Test Failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                connection?.disconnect()
            }
        }
    }
}
