package com.sleepyio.sleepyio.client.model.user

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SleeperUser(
    @SerialName("username")
    val userName: String? = null,
    @SerialName("user_id")
    val userId: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("avatar")
    val avatar: String? = null,
)
