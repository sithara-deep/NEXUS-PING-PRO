package com.nexus.pingultra

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Canvas
import android.view.View
import android.view.Gravity
import android.widget.*
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.graphics.Typeface
import java.net.Socket
import java.net.InetSocketAddress
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var running = false

    private lateinit var pingText: TextView
    private lateinit var jitterText: TextView
    private lateinit var lossText: TextView
    private lateinit var stabilityText: TextView
    private lateinit var networkText: TextView
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var graph: PingGraph

    private val samples = mutableListOf<Long>()
    private var failures = 0
    private var lastPing = -1L

    private var selectedHost = "1.1.1.1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(4, 8, 20))
            setPadding(25, 35, 25, 25)
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun label(textValue: String, size: Float, color: Int): TextView {
            return TextView(this).apply {
                text = textValue
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
                setPadding(8, 15, 8, 15)
            }
        }

        val title = label(
            "⚡ NEXUS PING ULTRA V2 ⚡",
            25f,
            Color.CYAN
        )

        title.typeface = Typeface.DEFAULT_BOLD

        pingText = label("-- ms", 55f, Color.GREEN)
        jitterText = label("JITTER: -- ms", 18f, Color.WHITE)
        lossText = label("FAILED TESTS: 0%", 18f, Color.WHITE)
        stabilityText = label("STABILITY: --", 18f, Color.CYAN)
        networkText = label("NETWORK: --", 18f, Color.YELLOW)
        statusText = label("READY TO SCAN", 17f, Color.GREEN)

        graph = PingGraph(this)

        graph.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            400
        )

        val serverSpinner = Spinner(this)

        val servers = arrayOf(
            "Cloudflare - 1.1.1.1",
            "Google - 8.8.8.8",
            "Cloudflare Secondary - 1.0.0.1",
            "Google Secondary - 8.8.4.4"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            servers
        )

        serverSpinner.adapter = adapter

        serverSpinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    selectedHost = when(position) {
                        0 -> "1.1.1.1"
                        1 -> "8.8.8.8"
                        2 -> "1.0.0.1"
                        else -> "8.8.4.4"
                    }

                    stopScan()
                }

                override fun onNothingSelected(
                    parent: android.widget.AdapterView<*>?
                ) {}
            }

        startButton = Button(this).apply {
            text = "▶ START LIVE MONITOR"
            setBackgroundColor(Color.rgb(0, 255, 140))
            setTextColor(Color.BLACK)

            setOnClickListener {
                if (running) stopScan()
                else startScan()
            }
        }

        content.addView(title)
        content.addView(pingText)
        content.addView(statusText)
        content.addView(graph)
        content.addView(jitterText)
        content.addView(lossText)
        content.addView(stabilityText)
        content.addView(networkText)
        content.addView(serverSpinner)
        content.addView(startButton)

        scroll.addView(content)
        root.addView(scroll)

        setContentView(root)

        networkText.text = "NETWORK: ${getNetworkType()}"
    }

    private fun getNetworkType(): String {
        val manager = getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

        val network = manager.activeNetwork ?: return "OFFLINE"

        val capabilities = manager.getNetworkCapabilities(network)
            ?: return "UNKNOWN"

        return when {
            capabilities.hasTransport(
                NetworkCapabilities.TRANSPORT_WIFI
            ) -> "WIFI"

            capabilities.hasTransport(
                NetworkCapabilities.TRANSPORT_CELLULAR
            ) -> "MOBILE DATA"

            capabilities.hasTransport(
                NetworkCapabilities.TRANSPORT_VPN
            ) -> "VPN"

            else -> "OTHER"
        }
    }

    private fun startScan() {
        if (running) return

        running = true

        samples.clear()
        failures = 0
        lastPing = -1L
        graph.update(emptyList())

        startButton.text = "■ STOP MONITOR"
        statusText.text = "MONITORING NETWORK..."

        val host = selectedHost

        thread {
            while (running) {

                val start = System.nanoTime()

                val ping = try {
                    Socket().use { socket ->
                        socket.connect(
                            InetSocketAddress(host, 443),
                            1500
                        )
                    }

                    (System.nanoTime() - start) / 1_000_000

                } catch (e: Exception) {
                    -1L
                }

                handler.post {
                    if (!running) return@post
                    updateStats(ping)
                }

                try {
                    Thread.sleep(1500)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
    }

    private fun updateStats(ping: Long) {

        samples.add(ping)

        if (samples.size > 60) {
            samples.removeAt(0)
        }

        if (ping < 0) failures++

        val valid = samples.filter { it >= 0 }

        val jitter = if (valid.size > 1) {
            valid.zipWithNext { a, b ->
                abs(a - b).toDouble()
            }.average()
        } else 0.0

        val failedCount = samples.count { it < 0 }

        val failureRate =
            failedCount * 100.0 / samples.size

        val avgPing = if (valid.isNotEmpty()) {
            valid.average()
        } else 0.0

        val stability = (
            100 -
            failureRate * 0.6 -
            jitter * 0.3 -
            avgPing * 0.1
        ).roundToInt().coerceIn(0, 100)

        pingText.text =
            if (ping >= 0) "$ping ms" else "TIMEOUT"

        pingText.setTextColor(
            when {
                ping < 0 -> Color.RED
                ping < 60 -> Color.GREEN
                ping < 120 -> Color.CYAN
                else -> Color.RED
            }
        )

        jitterText.text =
            "JITTER: ${jitter.roundToInt()} ms"

        lossText.text =
            "FAILED TESTS: ${failureRate.roundToInt()}%"

        stabilityText.text =
            "STABILITY SCORE: $stability / 100"

        networkText.text =
            "NETWORK: ${getNetworkType()}"

        statusText.text = when {
            ping < 0 -> "CONNECTION FAILED"
            ping < 60 -> "EXCELLENT CONNECTION"
            ping < 120 -> "GOOD CONNECTION"
            else -> "HIGH LATENCY"
        }

        graph.update(samples.toList())

        lastPing = ping
    }

    private fun stopScan() {
        running = false

        if (::startButton.isInitialized) {
            startButton.text = "▶ START LIVE MONITOR"
        }

        if (::statusText.isInitialized) {
            statusText.text = "MONITOR STOPPED"
        }
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    class PingGraph(context: Context) : View(context) {

        private val values = mutableListOf<Long>()

        private val paint = Paint().apply {
            color = Color.CYAN
            strokeWidth = 5f
            isAntiAlias = true
            style = Paint.Style.STROKE
        }

        fun update(data: List<Long>) {
            values.clear()
            values.addAll(data)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            if (values.size < 2) return

            val valid = values.filter { it >= 0 }

            if (valid.isEmpty()) return

            val maxPing = valid.maxOrNull()!!
                .coerceAtLeast(100L)

            val step = width.toFloat() /
                (values.size - 1)

            for (i in 1 until values.size) {

                if (values[i] < 0 || values[i-1] < 0) {
                    continue
                }

                val x1 = (i - 1) * step
                val x2 = i * step

                val y1 = height -
                    (values[i-1].toFloat() / maxPing * height)

                val y2 = height -
                    (values[i].toFloat() / maxPing * height)

                canvas.drawLine(
                    x1, y1, x2, y2, paint
                )
            }
        }
    }
}
