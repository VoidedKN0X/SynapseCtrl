package com.vknxworks.synapsectrl.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.container
import com.vknxworks.synapsectrl.data.api.ApiError
import com.vknxworks.synapsectrl.ui.nav.EditServerRoute
import com.vknxworks.synapsectrl.ui.nav.LocalNavigator
import com.vknxworks.synapsectrl.ui.theme.Gutter

/**
 * Shown on the tabs when the active server can't be used right now: unreachable, a changed
 * certificate, a rejected password or a version that's too old. Offers the one action that fixes it.
 * There is no fallback address: SynapseCtrl only ever uses the address saved for the server.
 */
@Composable
fun ConnectionBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val connection by c.sessions.connection.collectAsStateWithLifecycle()
    val server = active?.server ?: return
    val error = connection.error ?: return

    val needsSetup = error is ApiError.PinMismatch || error is ApiError.Unauthorized ||
        error is ApiError.UntrustedCertificate || error is ApiError.Redirect || error is ApiError.CleartextRefused ||
        error is ApiError.NotAdmin || error is ApiError.AdminApiUnavailable
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.CloudOff, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(stringResource(R.string.connection_problem), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        errorMessage(context, error, server.host),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (needsSetup) {
                    CtrlTextButton(onClick = { nav.navigate(EditServerRoute(server.id)) }) {
                        Text(
                            stringResource(
                                when (error) {
                                    is ApiError.PinMismatch, is ApiError.UntrustedCertificate -> R.string.check_again
                                    is ApiError.Unauthorized -> R.string.sign_in_again
                                    else -> R.string.edit_server
                                },
                            ),
                        )
                    }
                }
                // Retry checks the connection and admin access again.
                CtrlTextButton(onClick = c.sessions::refreshStatus) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}
