package io.github.clfundev.glink.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.clfundev.glink.ui.remotedesktop.RemoteDesktopScreen
import io.github.clfundev.glink.ui.serverdiscovery.ServerDiscoveryScreen
import io.github.clfundev.glink.ui.theme.GlinkTheme
import kotlinx.serialization.Serializable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlinkTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavigation(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val viewModel = viewModel<MainViewModel>()

    NavHost(
        navController = navController,
        startDestination = ServerDiscovery
    ) {
        composable<ServerDiscovery> {
            ServerDiscoveryScreen(modifier = modifier) { serverInfo ->
                viewModel.onServerSelected(serverInfo)
                navController.navigate(RemoteDesktop)
            }
        }

        composable<RemoteDesktop> {
            val serverInfo by viewModel.serverInfo.collectAsState()
            RemoteDesktopScreen(serverInfo) {
                navController.popBackStack()
            }
        }
    }
}

@Serializable
object ServerDiscovery

@Serializable
object RemoteDesktop