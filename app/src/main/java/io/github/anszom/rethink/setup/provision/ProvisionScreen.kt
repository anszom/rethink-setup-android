package io.github.anszom.rethink.setup.provision

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
    when (state) {
        ProvisionViewModel.ProvisionScreenState.StepOne -> {
            ProvisionStepOne({ ssid, password ->
                handleAction(
                    ProvisionViewModel.ProvisionAction.SaveCredentials(
                        ssid = ssid,
                        password = password
                    )
                )
            })
        }

        ProvisionViewModel.ProvisionScreenState.StepThree -> {

        }

        is ProvisionViewModel.ProvisionScreenState.StepTwo -> {
            ProvisionStepTwo(state.message)
        }
    }
}

@Composable
private fun ProvisionStepOne(saveInput: (String, String) -> Unit) {
    val wifiName = rememberTextFieldState("")
    val password = rememberSaveable { mutableStateOf("") }

    Column(Modifier.padding(16.dp)) {
        Text(text = "Step 1 of 3")
        Spacer(Modifier.size(16.dp))
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
private fun ProvisionStepTwo(message: String) {

    Column(Modifier.padding(16.dp)) {
        Text(text = "Step 2 of 3")
        Spacer(Modifier.size(16.dp))
        Text(text = message)
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
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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