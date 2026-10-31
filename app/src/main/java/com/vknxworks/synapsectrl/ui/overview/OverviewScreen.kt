package com.vknxworks.synapsectrl.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.Http
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.container
import com.vknxworks.synapsectrl.ui.components.ConnectionBanner
import com.vknxworks.synapsectrl.ui.components.CtrlLabel
import com.vknxworks.synapsectrl.ui.components.CtrlWordmark
import com.vknxworks.synapsectrl.ui.components.Group
import com.vknxworks.synapsectrl.ui.components.GroupRow
import com.vknxworks.synapsectrl.ui.components.LocalBottomClearance
import com.vknxworks.synapsectrl.ui.components.ProfileButton
import com.vknxworks.synapsectrl.ui.theme.CtrlColors
import com.vknxworks.synapsectrl.ui.theme.CtrlType
import com.vknxworks.synapsectrl.ui.theme.Gutter

/**
 * The only summary screen. For now: who's signed in, the connection and Synapse's version. Server
 * counts, health checks and what needs attention come in the next version.
 */
@Composable
fun OverviewScreen() {
    val c = LocalContext.current.container
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val connection by c.sessions.connection.collectAsStateWithLifecycle()
    val server = active?.server
    val status = connection.status

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        OverviewHeader(
            serverName = server?.name.orEmpty(),
            online = status != null && connection.error == null,
            version = status?.version ?: server?.version.orEmpty(),
        )
        if (connection.error != null) ConnectionBanner()
        PullToRefreshBox(
            isRefreshing = connection.loading && status != null,
            onRefresh = c.sessions::refreshStatus,
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = LocalBottomClearance.current + 16.dp)) {
                if (server != null) {
                    item(key = "account") {
                        Group(stringResource(R.string.group_signed_in)) {
                            GroupRow(
                                status?.userId ?: server.userId,
                                stringResource(if (status != null) R.string.admin_confirmed else R.string.admin_unchecked),
                                icon = Icons.Outlined.AdminPanelSettings,
                            )
                            GroupRow(
                                server.displayUrl,
                                stringResource(
                                    when {
                                        server.isHttp -> R.string.conn_http
                                        server.certPin.isNotBlank() -> R.string.cert_pinned
                                        else -> R.string.cert_system
                                    },
                                ),
                                icon = if (server.isHttp) Icons.Outlined.Http else Icons.Outlined.Lock,
                            )
                            if (server.readOnly) {
                                GroupRow(stringResource(R.string.read_only_mode), stringResource(R.string.read_only_on), icon = Icons.Outlined.Visibility)
                            }
                        }
                    }
                }
                item(key = "next") {
                    Group(footer = stringResource(R.string.coming_next_footer)) {
                        GroupRow(stringResource(R.string.coming_next), stringResource(R.string.coming_next_overview), icon = Icons.Outlined.Construction)
                    }
                }
            }
        }
    }
}

/**
 * The top of the Overview, as in every Ctrl app: the wordmark and the profile picture, then a mono
 * status line and the server's name in display size, then the Synapse version.
 */
@Composable
private fun OverviewHeader(serverName: String, online: Boolean, version: String) {
    Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 12.dp, bottom = 14.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            CtrlWordmark(Modifier.weight(1f))
            ProfileButton()
        }
        Spacer(Modifier.height(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CtrlLabel(
                stringResource(R.string.overview_status_line, stringResource(if (online) R.string.overview_online else R.string.overview_offline)),
                color = if (online) CtrlColors.Muted else CtrlColors.Error,
            )
            Text(
                serverName.ifBlank { stringResource(R.string.tab_overview) },
                style = MaterialTheme.typography.displayLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (version.isNotBlank()) {
                Text(stringResource(R.string.synapse_version, version), style = CtrlType.Data, color = CtrlColors.Muted)
            }
        }
    }
}
