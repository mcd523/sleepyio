package com.sleepyio.sleepyio.client.model.user

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SleeperUser(
    @SerialName("username")
    val userName: String,
    @SerialName("user_id")
    val userId: Long,
    @SerialName("display_name")
    val displayName: String,
    @SerialName("avatar")
    val avatar: String?,
)
