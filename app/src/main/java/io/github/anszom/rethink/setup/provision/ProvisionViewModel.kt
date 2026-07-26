package io.github.anszom.rethink.setup.provision

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.anszom.rethink.setup.dns.RouteChecker
import io.github.anszom.rethink.setup.net.DeviceSetup
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
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProvisionScreenState>(ProvisionScreenState.StepOne)
    val uiState: StateFlow<ProvisionScreenState> = _uiState

    private val wifi = WifiMonitor(context)
    private var pollJob: Job? = null
    private lateinit var ssid: String
    private lateinit var password: String

    fun handleAction(action: ProvisionAction) {
        when (action) {
            is ProvisionAction.SaveCredentials -> saveCredentials(
                ssid = action.ssid,
                password = action.password
            )
        }
    }

    private fun monitorWifi() {
        wifi.start()
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                val ip = wifi.ipv4Address()?.hostAddress
                val matches = ip != null && ip.startsWith(DeviceSetup.EXPECTED_SUBNET_PREFIX)
                val message = when {
                    ip == null -> "Waiting for Wi-Fi connection…"
                    matches -> "Connected — IP $ip ✓ (appliance network)"
                    else -> "Connected — IP $ip\nThis is not the appliance network " +
                            "(expected ${DeviceSetup.EXPECTED_SUBNET_PREFIX}x). Switch Wi-Fi networks."
                }
                stepTwo(message)
                delay(1000)
            }
        }
    }

    private fun saveCredentials(ssid: String, password: String) {
        this.ssid = ssid
        this.password = password
        stepTwo("")
        monitorWifi()
    }

    private fun stepTwo(message: String) {
        _uiState.value = ProvisionScreenState.StepTwo(message)

    }

    sealed interface ProvisionAction {
        data class SaveCredentials(
            val ssid: String,
            val password: String
        ) : ProvisionAction
    }


    sealed interface ProvisionScreenState {
        data object StepOne : ProvisionScreenState
        data class StepTwo(val message: String) : ProvisionScreenState
        data object StepThree : ProvisionScreenState

    }
}