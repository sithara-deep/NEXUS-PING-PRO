package com.nexus.pingultra

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var pingText: TextView
    private lateinit var statusText: TextView
    private lateinit var btn: Button
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#030814"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(30, 80, 30, 30)
        }

        // Title
        val title = TextView(this).apply {
            text = "⚡ NEXUS ULTRA ⚡"
            textSize = 32f
            setTextColor(Color.CYAN)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        // Live Ping
        pingText = TextView(this).apply {
            text = "0 ms"
            textSize = 72f
            setTextColor(Color.parseColor("#00FF88"))
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 60, 0, 10)
        }

        // Status
        statusText = TextView(this).apply {
            text = "READY • TAP TO SCAN"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        // Button
        btn = Button(this).apply {
            text = "▶ START PING SCAN"
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#00FF88"))
            textSize = 16f
            setOnClickListener { startScan() }
            setPadding(20, 20, 20, 20)
        }

        root.addView(title)
        root.addView(pingText)
        root.addView(statusText)
        root.addView(btn)
        setContentView(root)
    }

    private fun startScan() {
        if (running) return
        running = true
        btn.text = "⏹ SCANNING..."
        statusText.text = "CONNECTING TO 1.1.1.1..."

        thread {
            try {
                val start = System.currentTimeMillis()
                Socket().use { s ->
                    s.connect(InetSocketAddress("1.1.1.1", 443), 2000)
                }
                val result = System.currentTimeMillis() - start

                handler.post {
                    pingText.text = "${result} ms"
                    statusText.text = when {
                        result < 60 -> "✅ EXCELLENT • LOW PING"
                        result < 120 -> "🟡 GOOD"
                        else -> "🔴 HIGH LATENCY"
                    }
                    pingText.setTextColor(
                        if (result < 60) Color.parseColor("#00FF88")
                        else if (result < 120) Color.YELLOW
                        else Color.RED
                    )
                    btn.text = "▶ SCAN AGAIN"
                    running = false
                }
            } catch (e: Exception) {
                handler.post {
                    pingText.text = "-- ms"
                    statusText.text = "❌ CONNECTION FAILED"
                    pingText.setTextColor(Color.RED)
                    btn.text = "▶ RETRY"
                    running = false
                }
            }
        }
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }
}
