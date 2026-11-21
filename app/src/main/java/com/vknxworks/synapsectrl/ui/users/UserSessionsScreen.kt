package com.vknxworks.synapsectrl.ui.users

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Public
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.container
import com.vknxworks.synapsectrl.data.admin.Pusher
import com.vknxworks.synapsectrl.data.admin.Whois
import com.vknxworks.synapsectrl.data.admin.pushers
import com.vknxworks.synapsectrl.data.admin.whois
import com.vknxworks.synapsectrl.data.api.ApiError
import com.vknxworks.synapsectrl.ui.common.ErrorBlock
import com.vknxworks.synapsectrl.ui.common.Loading
import com.vknxworks.synapsectrl.ui.common.formatDateTime
import com.vknxworks.synapsectrl.ui.components.Group
import com.vknxworks.synapsectrl.ui.components.GroupRow
import com.vknxworks.synapsectrl.ui.components.ScreenTopBar
import com.vknxworks.synapsectrl.ui.components.relativeTime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** One connection of a device, flattened from the whois answer. */
private class Connection(val deviceId: String, val ip: String, val lastSeen: Long, val userAgent: String)

private fun Whois.connections(): List<Connection> = devices.flatMap { (deviceId, device) ->
    device.sessions.flatMap { it.connections }.map { Connection(deviceId, it.ip.orEmpty(), it.lastSeen ?: 0, it.userAgent.orEmpty()) }
}.sortedByDescending { it.lastSeen }

/**
 * Where a user is signed in from (GET /_synapse/admin/v1/whois/<user_id>: IP address, time and app per
 * device) and where their notifications are pushed to (GET …/users/<user_id>/pushers). Reads only.
 */
@Composable
fun UserSessionsScreen(userId: String) {
    val context = LocalContext.current
    val c = context.container
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server

    var whois by remember { mutableStateOf<Whois?>(null) }
    var pushers by remember { mutableStateOf<List<Pusher>?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ApiError?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(userId, reload, server?.id) {
        val client = active?.client ?: return@LaunchedEffect
        try {
            coroutineScope {
                val w = async { client.whois(userId) }
                val p = async { runCatching { client.pushers(userId).pushers }.getOrNull() }
                whois = w.await()
                pushers = p.await()
            }
            loaded = true
            error = null
        } catch (e: ApiError) {
            error = e
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.sessions_title))
        val err = error
        when {
            !loaded && err != null -> ErrorBlock(err) { reload++ }
            !loaded -> Loading()
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                val connections = whois?.connections().orEmpty()
                Group(stringResource(R.string.group_sessions, connections.size), footer = stringResource(R.string.sessions_footer)) {
                    if (connections.isEmpty()) GroupRow(stringResource(R.string.sessions_none), icon = Icons.Outlined.Public)
                    connections.take(200).forEach { conn ->
                        GroupRow(
                            conn.ip.ifBlank { stringResource(R.string.sessions_no_ip) },
                            listOfNotNull(
                                conn.deviceId.ifBlank { stringResource(R.string.sessions_no_device) },
                                conn.lastSeen.takeIf { it > 0 }?.let { "${relativeTime(it)} · ${formatDateTime(context, it)}" },
                                conn.userAgent.takeIf { it.isNotBlank() },
                            ).joinToString("\n"),
                            icon = Icons.Outlined.Devices,
                        )
                    }
                    if (connections.size > 200) GroupRow(stringResource(R.string.sessions_more, connections.size - 200))
                }

                val list = pushers
                Group(stringResource(R.string.group_pushers, list?.size ?: 0), footer = stringResource(R.string.pushers_footer)) {
                    if (list == null) GroupRow(stringResource(R.string.couldnt_load))
                    if (list != null && list.isEmpty()) GroupRow(stringResource(R.string.pushers_none), icon = Icons.Outlined.NotificationsActive)
                    list?.forEach { p ->
                        GroupRow(
                            listOfNotNull(p.appDisplayName?.takeIf { it.isNotBlank() }, p.deviceDisplayName?.takeIf { it.isNotBlank() })
                                .joinToString(" · ").ifBlank { p.appId },
                            listOfNotNull(
                                when (p.kind) {
                                    "email" -> stringResource(R.string.pusher_kind_email)
                                    "http" -> stringResource(R.string.pusher_kind_http)
                                    else -> p.kind.takeIf { it.isNotBlank() }
                                },
                                p.appId.takeIf { it.isNotBlank() },
                                p.data.url?.takeIf { it.isNotBlank() },
                                // For e-mail pushers the push key is the address; for apps it's a long device token, cut short.
                                p.pushkey.takeIf { it.isNotBlank() }?.let { if (it.length > 48) it.take(48) + "…" else it },
                            ).joinToString("\n"),
                            icon = Icons.Outlined.NotificationsActive,
                        )
                    }
                }
            }
        }
    }
}
