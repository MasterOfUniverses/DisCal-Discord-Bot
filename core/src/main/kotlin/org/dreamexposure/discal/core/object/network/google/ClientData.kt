package org.dreamexposure.discal.core.`object`.network.google

import kotlinx.serialization.Serializable

@Serializable
data class ClientData(val clientId: String, val clientSecret: String)
