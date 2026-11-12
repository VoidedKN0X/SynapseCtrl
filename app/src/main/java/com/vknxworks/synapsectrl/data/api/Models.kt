package com.vknxworks.synapsectrl.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Every field has a default, so a missing or new field never crashes (see ApiJson).

/** A Matrix error body: {"errcode": "M_FORBIDDEN", "error": "…", "retry_after_ms": 1000}. */
@Serializable
data class MatrixError(
    val errcode: String = "",
    val error: String = "",
    @SerialName("retry_after_ms") val retryAfterMs: Long? = null,
)

/** GET /_synapse/admin/v1/server_version (docs/admin_api/version_api.md). */
@Serializable
data class ServerVersion(@SerialName("server_version") val serverVersion: String = "")

/** GET /_matrix/client/v3/login: the login types the server offers. */
@Serializable
data class LoginFlows(val flows: List<LoginFlow> = emptyList()) {
    val password: Boolean get() = flows.any { it.type == "m.login.password" }
}

@Serializable
data class LoginFlow(val type: String = "")

/** POST /_matrix/client/v3/login with a password. */
@Serializable
data class LoginResponse(
    @SerialName("user_id") val userId: String = "",
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("device_id") val deviceId: String = "",
)

/** GET /_matrix/client/v3/account/whoami. */
@Serializable
data class Whoami(
    @SerialName("user_id") val userId: String = "",
    @SerialName("device_id") val deviceId: String = "",
)

/** GET /_synapse/admin/v1/users/<user_id>/admin (docs/admin_api/user_admin_api.md). */
@Serializable
data class AdminStatus(val admin: Boolean = false)

/** What a successful connection learned: who's signed in and which Synapse it is. */
data class ServerStatus(
    /** Synapse version, e.g. 1.163.2. */
    val version: String,
    /** The admin account, e.g. @axel:vkhub.org. */
    val userId: String,
    /** The device this access token belongs to; empty when Synapse didn't say. */
    val deviceId: String = "",
) {
    /** The server name: the part of the user ID after the first colon (vkhub.org). */
    val serverName: String get() = userId.substringAfter(':', "")
}
