package io.github.anszom.rethink.setup

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.anszom.rethink.setup.net.RouteCheck
import io.github.anszom.rethink.setup.net.requestWifiNetwork
import kotlinx.coroutines.launch

class DnsCheckActivity : AppCompatActivity() {

    private lateinit var logView: TextView
    private val logBuffer = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dns_check)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        logView = findViewById(R.id.log)
        val checkButton = findViewById<Button>(R.id.btnCheck)

        checkButton.setOnClickListener {
            checkButton.isEnabled = false
            logBuffer.setLength(0)
            runCheck { checkButton.isEnabled = true }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun runCheck(onDone: () -> Unit) {
        lifecycleScope.launch {
            try {
                log("Looking for a Wi-Fi connection with internet…")
                requestWifiNetwork(this@DnsCheckActivity, requireInternet = true).use { lease ->
                    log("")
                    log("ThinQ2: fetching https://common.lgthinq.com/route …")
                    val thinq2 = RouteCheck.check(lease.network)
                    log("HTTP ${thinq2.httpStatus}")
                    log("x-rethink: ${thinq2.headerValue ?: "(absent)"}")
                    log("Response body:")
                    log(if (thinq2.body.isEmpty()) "(empty)" else thinq2.body)

                    log("")
                    log("ThinQ1: fetching https://rethink.lgthinq.com:46030/rethink …")
                    // The point of this leg is DNS resolution: rethink.lgthinq.com only
                    // resolves when redirection is configured. A lookup/connection failure
                    // is therefore a negative result, not an error — handle it locally so it
                    // doesn't abort the combined verdict below.
                    val thinq1Redirected = try {
                        val thinq1 = RouteCheck.checkThinq1(lease.network)
                        log("HTTP ${thinq1.httpStatus}")
                        log("Response body:")
                        log(if (thinq1.body.isEmpty()) "(empty)" else thinq1.body)
                        thinq1.redirected
                    } catch (e: Exception) {
                        log("rethink.lgthinq.com did not resolve / could not be reached: ${e.message}")
                        false
                    }

                    log("")
                    if (thinq2.redirected && thinq1Redirected) {
                        log("✓ DNS redirection is active for both ThinQ1 and ThinQ2 — LG's cloud is being served locally by Rethink.")
                    } else {
                        log("✗ Not fully redirected. Requests to LG still reach the real cloud.")
                        if (!thinq2.redirected) log("  • ThinQ2 (common.lgthinq.com) is not redirected.")
                        if (!thinq1Redirected) log("  • ThinQ1 (rethink.lgthinq.com:46030) is not redirected.")
                        log("Check that this phone is on the Wi-Fi where Rethink runs and that DNS is pointed at it.")
                    }
                }
            } catch (e: Exception) {
                log("")
                log("✗ Could not run the check over Wi-Fi: ${e.message}")
                log("Connect this phone to your home Wi-Fi (not mobile data) and try again.")
            } finally {
                onDone()
            }
        }
    }

    private fun log(line: String) {
        logBuffer.append(line).append('\n')
        logView.text = logBuffer.toString()
    }
}
