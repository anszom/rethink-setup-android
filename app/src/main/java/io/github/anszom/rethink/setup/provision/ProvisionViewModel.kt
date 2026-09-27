package io.github.anszom.rethink.setup.provision

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.anszom.rethink.setup.net.DeviceSetup
import io.github.anszom.rethink.setup.net.DeviceSetup.SoftAp
import io.github.anszom.rethink.setup.net.WifiMonitor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProvisionViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val credentials: CredentialStore,
) : ViewModel() {

    private val wifi = WifiMonitor(context)
    private var pollJob: Job? = null
    private var ssid: String = credentials.ssid
    private var password: String = credentials.password

    private val _uiState = MutableStateFlow<ProvisionScreenState>(
        ProvisionScreenState.StepOne(ssid, password)
    )
    val uiState: StateFlow<ProvisionScreenState> = _uiState

    override fun onCleared() {
        pollJob?.cancel()
        wifi.stop()
    }

    fun handleAction(action: ProvisionAction) {
        when (action) {
            is ProvisionAction.SaveCredentials -> saveCredentials(
                ssid = action.ssid,
                password = action.password
            )

            ProvisionAction.BackToStepOne -> {
                stepOne()
            }

            ProvisionAction.ProvisionDevice -> {
                provisionDevice()
            }
        }
    }

    private fun monitorWifi() {
        wifi.start()
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                val ip = wifi.ipv4Address()?.hostAddress
                val softAp = currentSoftAp()
                val message = when {
                    ip == null -> "Waiting for Wi-Fi connection…"
                    wifi.hasValidatedInternet() -> "Connected — IP $ip\nThis network has internet " +
                            "access, so it is not the appliance network. Switch Wi-Fi networks."
                    softAp == SoftAp.THINQ -> "Connected — IP $ip ✓ (appliance network)"
                    // Plenty of home routers use 192.168.1.x too, and internet validation lags the
                    // connection by a few seconds, so keep the warning.
                    softAp == SoftAp.WHISEN -> "Connected — IP $ip ✓ (Whisen appliance network)\n" +
                            "Make sure this is the appliance's Wi-Fi and not your home network."
                    else -> "Connected — IP $ip\nThis is not the appliance network (expected " +
                            SoftAp.entries.joinToString(" or ") { "${it.subnetPrefix}x" } +
                            "). Switch Wi-Fi networks."
                }
                stepTwo(message, softAp != null)
                delay(1000)
            }
        }
    }

    /**
     * The appliance AP the phone is on, judged by its subnet. Appliance APs have no internet, so a
     * network with validated internet access is ruled out first.
     */
    private fun currentSoftAp(): SoftAp? =
        if (wifi.hasValidatedInternet()) null
        else wifi.ipv4Address()?.hostAddress?.let { SoftAp.forAddress(it) }

    private fun saveCredentials(ssid: String, password: String) {
        this.ssid = ssid
        this.password = password
        // Cache them so a retry (or a later run of the app) starts pre-filled.
        credentials.ssid = ssid
        credentials.password = password
        stepTwo("")
        monitorWifi()
    }

    private fun provisionDevice() {
        // Every progress line is kept, so the whole run can be copied out afterwards.
        val log = mutableListOf<String>()
        fun stepThree(message: String, finished: Boolean = false) {
            log += message
            _uiState.value = ProvisionScreenState.StepThree(
                message = message,
                log = log.joinToString("\n"),
                finished = finished
            )
        }

        _uiState.value = ProvisionScreenState.StepThree("")
        pollJob?.cancel()
        val network = wifi.network
        val softAp = currentSoftAp()
        if (network != null && softAp != null) {
            viewModelScope.launch {
                try {
                    DeviceSetup.provision(
                        network,
                        softAp,
                        ssid,
                        password,
                    ) { message ->
                        stepThree(message)
                    }
                    stepThree(
                        "✓ Done. The appliance will now join \"$ssid\" and reach out to Rethink.",
                        finished = true
                    )
                } catch (e: Exception) {
                    stepThree(
                        "✗ Setup failed: ${e.message}\nReconnect this phone to the appliance's Wi-Fi and try again.",
                        finished = true
                    )
                }
            }
        } else {
            stepThree("Lost the appliance Wi-Fi connection", finished = true)
        }
    }

    private fun stepTwo(message: String, canProceed: Boolean = false) {
        _uiState.value = ProvisionScreenState.StepTwo(message, canProceed)
    }

    private fun stepOne() {
        wifi.stop()
        pollJob?.cancel()
        _uiState.value = ProvisionScreenState.StepOne(ssid, password)
    }

    sealed interface ProvisionAction {
        data class SaveCredentials(
            val ssid: String,
            val password: String
        ) : ProvisionAction

        data object BackToStepOne : ProvisionAction

        data object ProvisionDevice : ProvisionAction
    }


    sealed interface ProvisionScreenState {
        data class StepOne(
            val ssid: String,
            val password: String
        ) : ProvisionScreenState

        data class StepTwo(
            val message: String,
            val canProceed: Boolean
        ) : ProvisionScreenState

        data class StepThree(
            val message: String,
            /** Every progress line seen so far, for the "copy logs" button. */
            val log: String = "",
            /** True once the run has ended, successfully or not. */
            val finished: Boolean = false
        ) : ProvisionScreenState

    }
}