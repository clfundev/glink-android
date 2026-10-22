package io.github.clfundev.glink.ui

import androidx.lifecycle.ViewModel
import io.github.clfundev.glink.discovery.ServerInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel : ViewModel() {
    private val _serverInfo = MutableStateFlow<ServerInfo?>(null)
    val serverInfo = _serverInfo.asStateFlow()

    fun onServerSelected(serverInfo: ServerInfo) {
        _serverInfo.value = serverInfo
    }
}