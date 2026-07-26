package io.github.anszom.rethink.setup.dns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anszom.rethink.setup.R

@Composable
fun DnsScreen() {

    val viewModel: DnsScreenViewModel = hiltViewModel()

    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    HandleUiState(uiState.value, viewModel::checkDns)
}

@Composable
private fun HandleUiState(state: String, checkDns: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(state = rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.dns_description)
        )
        Spacer(Modifier.padding(16.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = checkDns,
        ) {
            Text(stringResource(R.string.dns_check))
        }
        Text(
            text = state,
            modifier = Modifier.fillMaxWidth()
        )
    }
}