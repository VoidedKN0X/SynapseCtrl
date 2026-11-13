package com.vknxworks.synapsectrl.ui.settings

import androidx.compose.foundation.layout.Column
import com.vknxworks.synapsectrl.ui.components.CtrlAlertDialog
import com.vknxworks.synapsectrl.ui.components.CtrlTextButton
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.vknxworks.synapsectrl.data.server.LogoutResult
import com.vknxworks.synapsectrl.data.server.Server
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vknxworks.synapsectrl.ui.components.Group
import com.vknxworks.synapsectrl.ui.components.GroupRow
import com.vknxworks.synapsectrl.ui.components.GroupSwitch
import com.vknxworks.synapsectrl.ui.components.ScreenTopBar
import com.vknxworks.synapsectrl.ui.nav.AddServerRoute
import com.vknxworks.synapsectrl.ui.nav.EditServerRoute
import com.vknxworks.synapsectrl.ui.nav.LocalNavigator
import com.vknxworks.synapsectrl.ui.theme.Health

/** One line about a server: user, version and how the connection is secured. */
@Composable
fun serverSummary(server: Server): String = listOfNotNull(
    stringResource(R.string.signed_in_as, server.userId),
    server.version.takeIf { it.isNotBlank() }?.let { stringResource(R.string.synapse_version, it) },
    stringResource(
        when {
            server.isHttp -> R.string.conn_http
            server.certPin.isNotBlank() -> R.string.cert_pinned
            else -> R.string.cert_system
        },
    ),
).joinToString("  ·  ")

/** All saved servers: switch between them, change one, set read-only mode, or remove one. */
@Composable
fun ServersScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val servers by c.sessions.servers.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<Server?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.servers_title))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            servers.forEach { server ->
                val isActive = server.id == active?.server?.id
                Group(server.name) {
                    GroupRow(
                        server.displayUrl,
                        serverSummary(server),
                        icon = Icons.Outlined.Dns,
                        trailing = if (isActive) {
                            { Icon(Icons.Outlined.CheckCircle, stringResource(R.string.active_server), tint = Health.ok) }
                        } else {
                            null
                        },
                    )
                    if (!isActive) {
                        GroupRow(stringResource(R.string.switch_to), icon = Icons.Outlined.SwapHoriz, onClick = { c.sessions.switchTo(server.id) })
                    }
                    GroupSwitch(
                        stringResource(R.string.read_only_mode),
                        stringResource(R.string.read_only_mode_subtitle),
                        checked = server.readOnly,
                        icon = Icons.Outlined.Visibility,
                    ) { c.sessions.setReadOnly(server.id, it) }
                    GroupRow(stringResource(R.string.edit_server), icon = Icons.Outlined.Edit, showChevron = true, onClick = { nav.navigate(EditServerRoute(server.id)) })
                    GroupRow(
                        stringResource(R.string.remove_server),
                        icon = Icons.Outlined.Delete,
                        iconTint = MaterialTheme.colorScheme.error,
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { removing = server },
                    )
                }
            }
            Group {
                GroupRow(stringResource(R.string.add_server), icon = Icons.Outlined.Add, showChevron = true, onClick = { nav.navigate(AddServerRoute) })
            }
        }
    }

    removing?.let { server ->
        CtrlAlertDialog(
            onDismissRequest = { removing = null },
            guard = true,
            title = { Text(stringResource(R.string.remove_server_title, server.name)) },
            text = { Text(stringResource(R.string.remove_server_body)) },
            confirmButton = {
                CtrlTextButton(onClick = {
                    removing = null
                    // Log the access token out on the server first, then forget it here.
                    c.appScope.launch {
                        val result = c.sessions.remove(server.id)
                        c.forgetServer(server.id)
                        if (result == LogoutResult.FAILED) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, context.getString(R.string.logout_failed, server.name), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { CtrlTextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
