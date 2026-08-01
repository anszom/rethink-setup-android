package io.github.anszom.rethink.setup.provision

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun ProvisionScreen() {

    val viewModel: ProvisionViewModel = hiltViewModel()

    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    HandleUiState(uiState.value, viewModel::handleAction)
}

@Composable
private fun HandleUiState(
    state: ProvisionViewModel.ProvisionScreenState,
    handleAction: (ProvisionViewModel.ProvisionAction) -> Unit
) {
    Column {
        StepContainer(state)
        Spacer(Modifier.size(16.dp))
        when (state) {
            is ProvisionViewModel.ProvisionScreenState.StepOne -> {
                ProvisionStepOne(state.ssid, state.password) { ssid, password ->
                    handleAction(
                        ProvisionViewModel.ProvisionAction.SaveCredentials(
                            ssid = ssid,
                            password = password
                        )
                    )
                }
            }

            is ProvisionViewModel.ProvisionScreenState.StepTwo -> {
                ProvisionStepTwo(state.message, handleAction)
            }

            is ProvisionViewModel.ProvisionScreenState.StepThree -> {
                ProvisionStepThree(state.message, state.log, state.finished)
            }
        }
    }
}

@Composable
private fun StepContainer(state: ProvisionViewModel.ProvisionScreenState) {
    val step = when (state) {
        is ProvisionViewModel.ProvisionScreenState.StepOne -> 1
        is ProvisionViewModel.ProvisionScreenState.StepTwo -> 2
        is ProvisionViewModel.ProvisionScreenState.StepThree -> 3
    }
    Text(
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        text = "Step $step of 3",
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp
    )
}

@Composable
private fun ProvisionStepOne(
    initialSsid: String,
    initialPassword: String,
    saveInput: (String, String) -> Unit
) {
    // Seeded from the credentials cached by a previous run, so a retry needs no re-typing.
    val wifiName = rememberTextFieldState(initialSsid)
    val password = rememberSaveable { mutableStateOf(initialPassword) }

    Column(Modifier.padding(16.dp)) {
        Text(text = "Enter your home Wi-Fi credentials")
        Spacer(Modifier.size(8.dp))
        TextField(
            modifier = Modifier.fillMaxWidth(),
            state = wifiName,
            label = {
                Text(text = "Network name (SSID)")
            }
        )
        Spacer(Modifier.size(8.dp))
        PasswordTextField(password)
        Spacer(Modifier.size(16.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                saveInput(
                    wifiName.text.toString(),
                    password.value
                )
            },
            enabled = wifiName.text.isNotEmpty() && password.value.isNotEmpty()
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Next")
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
private fun ProvisionStepTwo(
    message: String,
    handleAction: (ProvisionViewModel.ProvisionAction) -> Unit
) {

    Column(Modifier.padding(16.dp)) {
        Text(
            text = "Connect to the appliance Wi-Fi",
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        )
        Spacer(Modifier.size(16.dp))
        Text(text = "In your phone's Wi-Fi settings, join the appliance's network (its name usually starts with \"LG_Smart\" or \"LGE_\" ). It has no internet — that is expected. This screen updates automatically once you are connected.")
        Spacer(Modifier.size(8.dp))
        Text(text = "Note: if the appliance's network does not appear, it may need to be put into Wi-Fi setup mode first. The exact action varies by model — for example, some air conditioners require a special two-button combination on the remote. Check your appliance's manual.")
        Spacer(Modifier.size(16.dp))
        Text(text = message)
        Spacer(Modifier.size(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                modifier = Modifier,
                onClick = {
                    handleAction(ProvisionViewModel.ProvisionAction.BackToStepOne)
                },
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null
                    )
                    Text("Back")
                }
            }
            Button(
                modifier = Modifier,
                onClick = {
                    handleAction(ProvisionViewModel.ProvisionAction.ProvisionDevice)
                },
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Start Setup")
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@Composable
private fun ProvisionStepThree(
    message: String,
    log: String,
    finished: Boolean,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Column(Modifier.padding(16.dp)) {
        Text(
            text = "Connect to the appliance Wi-Fi",
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        )
        Spacer(Modifier.size(16.dp))
        Text(text = message)
        if (finished) {
            Spacer(Modifier.size(16.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(
                            ClipData.newPlainText("Rethink setup log", log).toClipEntry()
                        )
                    }
                }
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Copy logs")
                }
            }
        }
    }
}

@Composable
fun PasswordTextField(password: MutableState<String>) {

    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    TextField(
        value = password.value,
        modifier = Modifier.fillMaxWidth(),
        onValueChange = { password.value = it },
        label = { Text("Password") },
        singleLine = true,
        placeholder = { Text("Password") },
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            val image = if (passwordVisible) {
                Icons.Filled.Visibility
            } else {
                Icons.Filled.VisibilityOff
            }

            val description = if (passwordVisible) "Hide password" else "Show password"

            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(imageVector = image, description)
            }
        }
    )
}