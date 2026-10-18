package com.nexus.pingultra

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.content.Intent
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Socket
import java.net.InetSocketAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread
import kotlin.math.abs
import java.util.Locale

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var result: TextView
    private lateinit var details: TextView
    private lateinit var network: TextView

    private val background = Color.rgb(5, 9, 23)
    private val neon = Color.rgb(0, 255, 180)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 45, 30, 45)
            setBackgroundColor(Color.rgb(5, 9, 23))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        fun text(value: String, size: Float, color: Int): TextView {
            return TextView(this).apply {
                this.text = value
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
                setPadding(10, 20, 10, 20)
            }
        }

        fun button(
            title: String,
            action: () -> Unit
        ): Button {

            return Button(this).apply {
                text = title
                textSize = 15f
                setTextColor(Color.BLACK)
                setBackgroundColor(neon)
                setOnClickListener { action() }
            }
        }

        val title = text(
            "⚡ NEXUS PING ULTRA V3 ⚡",
            25f,
            Color.CYAN
        )

        title.typeface = Typeface.DEFAULT_BOLD

        result = text(
            "READY",
            38f,
            neon
        )

        details = text(
            "Advanced Network Diagnostics",
            17f,
            Color.WHITE
        )

        network = text(
            "NETWORK: ${networkType()}",
            17f,
            Color.YELLOW
        )

        root.addView(title)
        root.addView(result)
        root.addView(details)
        root.addView(network)

        root.addView(button("⚡ TEST NETWORK PING") {
            testPing()
        })

        root.addView(button("🚀 TEST DOWNLOAD SPEED") {
            testDownload()
        })

        root.addView(button("🌐 COMPARE SERVERS") {
            compareServers()
        })

        root.addView(button("📶 REFRESH NETWORK INFO") {
            network.text = "NETWORK: ${networkType()}"
        })

        root.addView(button("🛠️ PRIVATE DNS SETTINGS") {
            try {
                startActivity(
                    Intent("android.settings.PRIVATE_DNS_SETTINGS")
                )
            } catch (e: Exception) {
                startActivity(
                    Intent(Settings.ACTION_WIRELESS_SETTINGS)
                )
            }
        })

        root.addView(button("📱 NETWORK SETTINGS") {
            startActivity(
                Intent(Settings.ACTION_WIRELESS_SETTINGS)
            )
        })

        root.addView(
            text(
                "Supports WiFi and Mobile Networks",
                15f,
                Color.LTGRAY
            )
        )

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun networkType(): String {

        val manager = getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

        val active = manager.activeNetwork
            ?: return "OFFLINE"

        val cap = manager.getNetworkCapabilities(active)
            ?: return "UNKNOWN"

        return when {

            cap.hasTransport(
                NetworkCapabilities.TRANSPORT_VPN
            ) -> "VPN"

            cap.hasTransport(
                NetworkCapabilities.TRANSPORT_WIFI
            ) -> "WIFI"

            cap.hasTransport(
                NetworkCapabilities.TRANSPORT_CELLULAR
            ) -> "MOBILE DATA"

            else -> "OTHER"
        }
    }

    private fun ping(host: String): Long {

        return try {

            val start = System.nanoTime()

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
    }

    private fun testPing() {

        result.text = "TESTING..."
        details.text = "Collecting 10 samples"

        thread {

            val samples = mutableListOf<Long>()

            repeat(10) {

                samples.add(ping("1.1.1.1"))

                Thread.sleep(300)
            }

            val valid = samples.filter { it >= 0 }

            val average = if (valid.isNotEmpty()) {
                valid.average()
            } else -1.0

            val jitter = if (valid.size > 1) {

                valid.zipWithNext { a, b ->
                    abs(a - b).toDouble()
                }.average()

            } else 0.0

            val failed = samples.count { it < 0 }

            val failureRate = failed * 100 / samples.size

            handler.post {

                result.text = if (average >= 0) {
                    String.format(
                        Locale.US,
                        "%.1f ms",
                        average
                    )
                } else {
                    "FAILED"
                }

                details.text =
                    "Jitter: ${String.format(Locale.US, "%.1f", jitter)} ms\n" +
                    "Failed Connections: $failureRate%\n" +
                    "Server: Cloudflare"

                result.setTextColor(
                    if (average in 0.0..99.0) neon
                    else Color.RED
                )
            }
        }
    }

    private fun compareServers() {

        result.text = "SCANNING..."

        thread {

            val hosts = listOf(
                "Cloudflare" to "1.1.1.1",
                "Google" to "8.8.8.8",
                "Cloudflare 2" to "1.0.0.1",
                "Google 2" to "8.8.4.4"
            )

            val results = hosts.map { (name, host) ->
                Triple(name, host, ping(host))
            }

            val best = results
                .filter { it.third >= 0 }
                .minByOrNull { it.third }

            val report = results.joinToString("\n") {
                "${it.first}: " +
                if (it.third >= 0) {
                    "${it.third} ms"
                } else {
                    "FAILED"
                }
            }

            handler.post {

                result.text = if (best != null) {
                    "BEST: ${best.first}"
                } else {
                    "NO CONNECTION"
                }

                details.text = report
            }
        }
    }

    private fun testDownload() {

        result.text = "DOWNLOADING..."
        details.text = "Testing connection speed"

        thread {

            var connection: HttpsURLConnection? = null

            try {

                val url = URL(
                    "https://speed.cloudflare.com/__down?bytes=1000000"
                )

                connection =
                    url.openConnection() as HttpsURLConnection

                connection.connectTimeout = 10000
                connection.readTimeout = 10000
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
                    (System.nanoTime() - start) / 1_000_000_000.0

                val mbps =
                    (total * 8.0) / seconds / 1_000_000.0

                handler.post {

                    result.text = String.format(
                        Locale.US,
                        "%.2f Mbps",
                        mbps
                    )

                    details.text =
                        "Downloaded: $total bytes\n" +
                        "Time: ${String.format(Locale.US, "%.2f", seconds)} seconds\n" +
                        "Network: ${networkType()}"
                }

            } catch (e: Exception) {

                handler.post {

                    result.text = "TEST FAILED"

                    details.text =
                        "Check Internet Connection"
                }

            } finally {
                connection?.disconnect()
            }
        }
    }
}
