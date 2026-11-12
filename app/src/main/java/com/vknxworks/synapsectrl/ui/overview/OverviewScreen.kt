package com.vknxworks.synapsectrl.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Sd
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.container
import com.vknxworks.synapsectrl.data.health.HealthCheck
import com.vknxworks.synapsectrl.data.health.HealthId
import com.vknxworks.synapsectrl.data.health.HealthState
import com.vknxworks.synapsectrl.data.overview.Attention
import com.vknxworks.synapsectrl.data.overview.AttentionItem
import com.vknxworks.synapsectrl.data.overview.Severity
import com.vknxworks.synapsectrl.ui.common.ErrorBlock
import com.vknxworks.synapsectrl.ui.common.ListEndSpacer
import com.vknxworks.synapsectrl.ui.common.Loading
import com.vknxworks.synapsectrl.ui.common.StatTile
import com.vknxworks.synapsectrl.ui.common.formatBytes
import com.vknxworks.synapsectrl.ui.components.ConnectionBanner
import com.vknxworks.synapsectrl.ui.components.CtrlLabel
import com.vknxworks.synapsectrl.ui.components.CtrlWordmark
import com.vknxworks.synapsectrl.ui.components.Group
import com.vknxworks.synapsectrl.ui.components.GroupRow
import com.vknxworks.synapsectrl.ui.components.ProfileButton
import com.vknxworks.synapsectrl.ui.components.formatCount
import com.vknxworks.synapsectrl.ui.nav.FederationRoute
import com.vknxworks.synapsectrl.ui.nav.InvitesRoute
import com.vknxworks.synapsectrl.ui.nav.LocalNavigator
import com.vknxworks.synapsectrl.ui.nav.MediaRoute
import com.vknxworks.synapsectrl.ui.nav.ReportsRoute
import com.vknxworks.synapsectrl.ui.nav.ServerNoticeRoute
import com.vknxworks.synapsectrl.ui.theme.CtrlColors
import com.vknxworks.synapsectrl.ui.theme.CtrlType
import com.vknxworks.synapsectrl.ui.theme.Gutter
import com.vknxworks.synapsectrl.ui.theme.Health
import kotlinx.coroutines.launch

/**
 * The only summary screen: the server, its numbers, what needs attention and the health checks.
 * Everything here is read-only; the rows lead to the screens where things can be changed.
 */
@Composable
fun OverviewScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val connection by c.sessions.connection.collectAsStateWithLifecycle()
    val states by c.overview.states.collectAsStateWithLifecycle()
    val notify by c.notifySettings.settings.collectAsStateWithLifecycle()
    val server = active?.server
    val state = server?.id?.let { states[it] }
    val summary = state?.summary

    LaunchedEffect(server?.id, server?.url) { c.overview.refresh(force = false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        OverviewHeader(
            serverName = server?.name.orEmpty(),
            online = connection.status != null && connection.error == null,
            version = summary?.status?.version ?: connection.status?.version ?: server?.version.orEmpty(),
        )
        if (connection.error != null) ConnectionBanner()
        PullToRefreshBox(
            isRefreshing = state?.loading == true && summary != null,
            onRefresh = { scope.launch { c.overview.refresh(force = true) } },
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp)) {
                if (summary == null) {
                    item(key = "loading") {
                        val err = state?.error
                        if (err != null && state?.loading != true) ErrorBlock(err) { scope.launch { c.overview.refresh(force = true) } } else Loading()
                    }
                } else {
                    item(key = "stats") {
                        Column(Modifier.padding(horizontal = Gutter, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatTile(
                                    stringResource(R.string.stat_users),
                                    summary.usersActive?.let { formatCount(it) } ?: "—",
                                    Modifier.weight(1f),
                                    sub = summary.usersDeactivated?.let { stringResource(R.string.stat_users_sub, formatCount(it), formatCount(summary.usersTotal ?: 0)) },
                                )
                                StatTile(
                                    stringResource(R.string.stat_rooms),
                                    summary.rooms?.let { formatCount(it) } ?: "—",
                                    Modifier.weight(1f),
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatTile(
                                    stringResource(R.string.stat_media),
                                    summary.mediaBytes?.let { formatBytes(context, it) } ?: "—",
                                    Modifier.weight(1f),
                                    sub = summary.mediaCount?.let {
                                        stringResource(if (summary.mediaPartial) R.string.stat_media_sub_partial else R.string.stat_media_sub, formatCount(it))
                                    },
                                )
                                StatTile(
                                    stringResource(R.string.stat_reports),
                                    summary.reportsTotal?.let { formatCount(it) } ?: "—",
                                    Modifier.weight(1f),
                                    sub = stringResource(R.string.stat_reports_sub),
                                )
                            }
                        }
                    }
                    val attention = Attention.of(summary, notify.mediaThresholdGb * 1_000_000_000L)
                    if (attention.isNotEmpty()) {
                        item(key = "attention") {
                            Group(stringResource(R.string.group_attention)) {
                                attention.forEach { a -> AttentionRow(a) }
                            }
                        }
                    }
                    item(key = "health") {
                        Group(stringResource(R.string.group_health), footer = stringResource(R.string.health_footer)) {
                            summary.health.forEach { HealthRow(it) }
                            if (summary.health.isEmpty()) GroupRow(stringResource(R.string.health_unavailable), icon = Icons.Outlined.Info)
                        }
                    }
                }
                if (server != null) {
                    item(key = "actions") {
                        Group(stringResource(R.string.group_server)) {
                            GroupRow(stringResource(R.string.invites_title), stringResource(R.string.invites_subtitle), icon = Icons.Outlined.PersonAdd, showChevron = true, onClick = { nav.navigate(InvitesRoute()) })
                            GroupRow(stringResource(R.string.federation_title), stringResource(R.string.federation_subtitle), icon = Icons.Outlined.Hub, showChevron = true, onClick = { nav.navigate(FederationRoute) })
                            if (!server.readOnly) {
                                GroupRow(stringResource(R.string.notice_title), stringResource(R.string.notice_subtitle), icon = Icons.Outlined.Campaign, showChevron = true, onClick = { nav.navigate(ServerNoticeRoute()) })
                            }
                        }
                    }
                }
                item(key = "end") { ListEndSpacer() }
            }
        }
    }
}

@Composable
private fun severityIcon(s: Severity): Pair<ImageVector, androidx.compose.ui.graphics.Color> = when (s) {
    Severity.CRITICAL -> Icons.Outlined.ErrorOutline to Health.critical
    Severity.WARNING -> Icons.Outlined.WarningAmber to Health.warn
    Severity.INFO -> Icons.Outlined.Info to CtrlColors.Muted
}

@Composable
private fun AttentionRow(a: AttentionItem) {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val (icon, tint) = severityIcon(a.severity)
    when (a) {
        is AttentionItem.HealthFailing -> GroupRow(
            pluralStringResource(R.plurals.att_health, a.checks.size, a.checks.size),
            a.checks.joinToString(", ") { context.getString(healthTitle(it.id)) },
            icon = icon, iconTint = tint,
        )
        is AttentionItem.Reports -> GroupRow(
            pluralStringResource(R.plurals.att_reports, a.count.toInt(), a.count.toInt()),
            stringResource(R.string.att_reports_detail),
            icon = Icons.Outlined.Flag, iconTint = tint, showChevron = true, onClick = { nav.tab(ReportsRoute) },
        )
        is AttentionItem.FederationFailing -> GroupRow(
            pluralStringResource(R.plurals.att_federation, a.destinations.size, a.destinations.size),
            a.destinations.take(4).joinToString(", "),
            icon = Icons.Outlined.Hub, iconTint = tint, showChevron = true, onClick = { nav.navigate(FederationRoute) },
        )
        is AttentionItem.MediaOver -> GroupRow(
            stringResource(R.string.att_media, formatBytes(context, a.bytes)),
            stringResource(R.string.att_media_detail, formatBytes(context, a.thresholdBytes)),
            icon = Icons.Outlined.Sd, iconTint = tint, showChevron = true, onClick = { nav.tab(MediaRoute) },
        )
        is AttentionItem.BackgroundRunning -> GroupRow(
            stringResource(R.string.att_background),
            a.names.joinToString(", "),
            icon = Icons.Outlined.Sync, iconTint = tint,
        )
    }
}

fun healthTitle(id: HealthId): Int = when (id) {
    HealthId.SYNAPSE -> R.string.health_synapse
    HealthId.WELL_KNOWN_CLIENT -> R.string.health_wk_client
    HealthId.WELL_KNOWN_SERVER -> R.string.health_wk_server
    HealthId.FEDERATION -> R.string.health_federation
    HealthId.RTC -> R.string.health_rtc
}

private fun healthExplanation(check: HealthCheck): Int = when (check.id) {
    HealthId.SYNAPSE -> if (check.state == HealthState.PASS) R.string.health_synapse_pass else R.string.health_synapse_fail
    HealthId.WELL_KNOWN_CLIENT -> when (check.state) {
        HealthState.PASS -> R.string.health_wk_client_pass
        HealthState.WARN -> R.string.health_wk_client_warn
        HealthState.FAIL -> R.string.health_wk_client_fail
    }
    HealthId.WELL_KNOWN_SERVER -> if (check.state == HealthState.PASS) R.string.health_wk_server_pass else R.string.health_wk_server_warn
    HealthId.FEDERATION -> when (check.state) {
        HealthState.PASS -> R.string.health_federation_pass
        HealthState.WARN -> R.string.health_federation_warn
        HealthState.FAIL -> R.string.health_federation_fail
    }
    HealthId.RTC -> when (check.state) {
        HealthState.PASS -> R.string.health_rtc_pass
        HealthState.WARN -> R.string.health_rtc_warn
        HealthState.FAIL -> R.string.health_rtc_fail
    }
}

@Composable
private fun HealthRow(check: HealthCheck) {
    val (icon, tint) = when (check.state) {
        HealthState.PASS -> Icons.Outlined.CheckCircle to Health.ok
        HealthState.WARN -> Icons.Outlined.WarningAmber to Health.warn
        HealthState.FAIL -> Icons.Outlined.ErrorOutline to Health.critical
    }
    val explanation = stringResource(healthExplanation(check))
    GroupRow(
        stringResource(healthTitle(check.id)),
        if (check.detail.isBlank()) explanation else "$explanation\n${check.detail}",
        icon = icon,
        iconTint = tint,
    )
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
