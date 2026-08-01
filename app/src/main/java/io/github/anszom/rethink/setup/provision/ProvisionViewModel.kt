package io.github.anszom.rethink.setup.provision

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
                val matches = ip != null && ip.startsWith(DeviceSetup.EXPECTED_SUBNET_PREFIX)
                val message = when {
                    ip == null -> "Waiting for Wi-Fi connection…"
                    matches -> "Connected — IP $ip ✓ (appliance network)"
                    else -> "Connected — IP $ip\nThis is not the appliance network " +
                            "(expected ${DeviceSetup.EXPECTED_SUBNET_PREFIX}x). Switch Wi-Fi networks."
                }
                stepTwo(message, matches)
                delay(1000)
            }
        }
    }

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
        _uiState.value = ProvisionScreenState.StepThree("")
        pollJob?.cancel()
        wifi.network?.let {
            viewModelScope.launch {
                try {
                    DeviceSetup.provision(
                        it,
                        DeviceSetup.DEFAULT_HOST,
                        DeviceSetup.DEFAULT_PORT,
                        ssid,
                        password,
                    ) { message ->
                        _uiState.value = ProvisionScreenState.StepThree(message)

                    }
                    _uiState.value =
                        ProvisionScreenState.StepThree("✓ Done. The appliance will now join \"$ssid\" and reach out to Rethink.")
                } catch (e: Exception) {
                    _uiState.value =
                        ProvisionScreenState.StepThree("✗ Setup failed: ${e.message}\nReconnect this phone to the appliance's Wi-Fi and try again.")
                }
            }
        } ?: run {
            _uiState.value = ProvisionScreenState.StepThree("Lost the appliance Wi-Fi connection")

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

        data class StepThree(val message: String) : ProvisionScreenState

    }
}