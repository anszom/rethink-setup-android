package io.github.anszom.rethink.setup

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.anszom.rethink.setup.net.DeviceSetup
import io.github.anszom.rethink.setup.net.WifiMonitor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ProvisionActivity : AppCompatActivity() {

    private lateinit var step1: View
    private lateinit var step2: View
    private lateinit var step3: View

    private lateinit var ssidField: EditText
    private lateinit var passwordField: EditText
    private lateinit var ipStatus: TextView
    private lateinit var startButton: Button
    private lateinit var logView: TextView
    private lateinit var homeButton: Button
    private lateinit var scroll: ScrollView

    private lateinit var wifi: WifiMonitor
    private var pollJob: Job? = null

    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    private val logBuffer = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_provision)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        wifi = WifiMonitor(this)

        step1 = findViewById(R.id.step1)
        step2 = findViewById(R.id.step2)
        step3 = findViewById(R.id.step3)

        ssidField = findViewById(R.id.ssid)
        passwordField = findViewById(R.id.password)
        ipStatus = findViewById(R.id.ipStatus)
        startButton = findViewById(R.id.btnStart)
        logView = findViewById(R.id.log)
        homeButton = findViewById(R.id.btnHome)
        scroll = findViewById(R.id.scroll)

        // Pre-fill credentials cached from a previous run so the user never re-types
        // them, even across failed attempts.
        ssidField.setText(prefs.getString(KEY_SSID, ""))
        passwordField.setText(prefs.getString(KEY_PASSWORD, ""))

        findViewById<CheckBox>(R.id.showPassword).setOnCheckedChangeListener { _, checked ->
            passwordField.inputType = if (checked) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            passwordField.setSelection(passwordField.text.length)
        }

        findViewById<Button>(R.id.btnNext).setOnClickListener {
            if (ssidField.text.isNullOrEmpty()) {
                ssidField.error = "Enter your home Wi-Fi name"
            } else {
                saveCredentials()
                enterStep2()
            }
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener { enterStep1() }
        startButton.setOnClickListener { enterStep3() }
        homeButton.setOnClickListener { finish() }
    }

    // --- Step 1: credentials --------------------------------------------------

    private fun enterStep1() {
        stopPolling()
        wifi.stop()
        showStep(step1)
    }

    // --- Step 2: connect to the appliance Wi-Fi (poll IP) ---------------------

    private fun enterStep2() {
        showStep(step2)
        startButton.isEnabled = false
        wifi.start()
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (isActive) {
                val ip = wifi.ipv4Address()?.hostAddress
                val matches = ip != null && ip.startsWith(DeviceSetup.EXPECTED_SUBNET_PREFIX)
                ipStatus.text = when {
                    ip == null -> "Waiting for Wi-Fi connection…"
                    matches -> "Connected — IP $ip ✓ (appliance network)"
                    else -> "Connected — IP $ip\nThis is not the appliance network " +
                        "(expected ${DeviceSetup.EXPECTED_SUBNET_PREFIX}x). Switch Wi-Fi networks."
                }
                startButton.isEnabled = matches
                delay(1000)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    // --- Step 3: run setup (non-interactive) ----------------------------------

    private fun enterStep3() {
        stopPolling()
        val network = wifi.network
        val ssid = ssidField.text.toString()
        val password = passwordField.text.toString()

        showStep(step3)
        logBuffer.setLength(0)
        homeButton.visibility = View.GONE

        lifecycleScope.launch {
            try {
                if (network == null) throw IllegalStateException("Lost the appliance Wi-Fi connection")
                DeviceSetup.provision(
                    network,
                    DeviceSetup.DEFAULT_HOST,
                    DeviceSetup.DEFAULT_PORT,
                    ssid,
                    password,
                ) { line -> runOnUiThread { log(line) } }
                log("")
                log("✓ Done. The appliance will now join \"$ssid\" and reach out to Rethink.")
            } catch (e: Exception) {
                log("")
                log("✗ Setup failed: ${e.message}")
                log("Reconnect this phone to the appliance's Wi-Fi and try again.")
            } finally {
                homeButton.visibility = View.VISIBLE
            }
        }
    }

    // --- helpers --------------------------------------------------------------

    private fun saveCredentials() {
        prefs.edit()
            .putString(KEY_SSID, ssidField.text.toString())
            .putString(KEY_PASSWORD, passwordField.text.toString())
            .apply()
    }

    private fun showStep(step: View) {
        step1.visibility = if (step === step1) View.VISIBLE else View.GONE
        step2.visibility = if (step === step2) View.VISIBLE else View.GONE
        step3.visibility = if (step === step3) View.VISIBLE else View.GONE
    }

    private fun log(line: String) {
        logBuffer.append(line).append('\n')
        logView.text = logBuffer.toString()
        // Scroll the whole view to the bottom after layout settles.
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        stopPolling()
        wifi.stop()
        super.onDestroy()
    }

    private companion object {
        const val PREFS = "provisioning"
        const val KEY_SSID = "ssid"
        const val KEY_PASSWORD = "password"
    }
}
