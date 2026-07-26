package io.github.anszom.rethink.setup.main

import RethinkTheme
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import io.github.anszom.rethink.setup.R
import io.github.anszom.rethink.setup.dns.DnsScreen
import io.github.anszom.rethink.setup.provision.ProvisionScreen

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val backStack = remember { mutableStateListOf(NavLocation.Main) }
            val showBackButton = backStack.size > 1

            RethinkTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                val title = when {
                                    backStack.contains(NavLocation.DNS) -> {
                                        stringResource(R.string.title_dns)
                                    }

                                    backStack.contains(NavLocation.Provision) -> {
                                        stringResource(R.string.title_provision)
                                    }

                                    else -> stringResource(R.string.app_name)
                                }
                                Text(text = title, fontSize = 24.sp)
                            },
                            navigationIcon = {
                                if (showBackButton) {
                                    IconButton(onClick = { backStack.removeLastOrNull() }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = null
                                        )
                                    }
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {

                        NavDisplay(
                            backStack = backStack,
                            onBack = { backStack.removeLastOrNull() },
                            entryProvider = { key ->
                                when (key) {
                                    NavLocation.Main -> {
                                        NavEntry(key) {
                                            MainScreen(backStack)
                                        }

                                    }

                                    NavLocation.DNS -> {
                                        NavEntry(key) {
                                            DnsScreen()
                                        }
                                    }

                                    NavLocation.Provision -> {
                                        NavEntry(key) {
                                            ProvisionScreen()
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
