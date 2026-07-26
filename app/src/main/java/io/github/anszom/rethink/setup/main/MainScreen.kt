package io.github.anszom.rethink.setup.main

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
import io.github.anszom.rethink.setup.R

@Composable
fun MainScreen(backStack: MutableList<NavLocation> ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(state = rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = stringResource(R.string.app_name))
        Spacer(Modifier.padding(16.dp))
        Text(text = stringResource(R.string.main_description))
        Spacer(Modifier.padding(24.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                backStack.add(NavLocation.DNS)
            },
        ) {
            Text(stringResource(R.string.title_dns))
        }
        Spacer(Modifier.padding(8.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                backStack.add(NavLocation.Provision)

            },
        ) {
            Text(stringResource(R.string.title_provision))
        }
    }
}