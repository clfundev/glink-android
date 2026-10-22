package io.github.clfundev.glink.ui.serverdiscovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.clfundev.glink.discovery.LocalServerDiscovery
import io.github.clfundev.glink.discovery.ServerInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch

class ServerDiscoveryViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<DiscoveryState>(DiscoveryState.Pending)
    val uiState: StateFlow<DiscoveryState> = _uiState.asStateFlow()

    fun discover() {
        if (_uiState.value == DiscoveryState.Loading) return
        _uiState.value = DiscoveryState.Loading
        viewModelScope.launch {
            val infos = LocalServerDiscovery.discover().toList()
            _uiState.value = DiscoveryState.Success(infos)
        }
    }
}

sealed interface DiscoveryState {
    data object Pending : DiscoveryState
    data object Loading : DiscoveryState
    data class Success(val serverInfos: List<ServerInfo>) : DiscoveryState
}