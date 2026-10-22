package io.github.clfundev.glink.discovery

import kotlinx.serialization.Serializable

@Serializable
data class ServerInfo(
    val id: String,
    val address: String,
    val name: String
)