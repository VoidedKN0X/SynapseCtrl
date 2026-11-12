package com.vknxworks.synapsectrl.ui.reports

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.container
import com.vknxworks.synapsectrl.data.admin.Destination
import com.vknxworks.synapsectrl.data.admin.EventReport
import com.vknxworks.synapsectrl.data.admin.UserReport
import com.vknxworks.synapsectrl.data.admin.deleteUserReport
import com.vknxworks.synapsectrl.data.admin.destinations
import com.vknxworks.synapsectrl.data.admin.eventReports
import com.vknxworks.synapsectrl.data.admin.resetConnection
import com.vknxworks.synapsectrl.data.admin.userReports
import com.vknxworks.synapsectrl.data.api.ApiError
import com.vknxworks.synapsectrl.ui.common.ErrorBlock
import com.vknxworks.synapsectrl.ui.common.FilterRow
import com.vknxworks.synapsectrl.ui.common.ListEndSpacer
import com.vknxworks.synapsectrl.ui.common.Loading
import com.vknxworks.synapsectrl.ui.common.ScreenActions
import com.vknxworks.synapsectrl.ui.common.formatDateTime
import com.vknxworks.synapsectrl.ui.common.rememberScreenActions
import com.vknxworks.synapsectrl.ui.components.ConfirmDialog
import com.vknxworks.synapsectrl.ui.components.ConnectionBanner
import com.vknxworks.synapsectrl.ui.components.CtrlSheet
import com.vknxworks.synapsectrl.ui.components.CtrlTextButton
import com.vknxworks.synapsectrl.ui.components.EmptyState
import com.vknxworks.synapsectrl.ui.components.Group
import com.vknxworks.synapsectrl.ui.components.GroupRow
import com.vknxworks.synapsectrl.ui.components.ScreenTopBar
import com.vknxworks.synapsectrl.ui.components.TabTitle
import com.vknxworks.synapsectrl.ui.components.relativeTime
import com.vknxworks.synapsectrl.ui.nav.LocalNavigator
import com.vknxworks.synapsectrl.ui.nav.ReportRoute
import com.vknxworks.synapsectrl.ui.nav.UserRoute
import com.vknxworks.synapsectrl.ui.theme.CtrlColors
import com.vknxworks.synapsectrl.ui.theme.CtrlType
import com.vknxworks.synapsectrl.ui.theme.Gutter
import com.vknxworks.synapsectrl.ui.theme.Health
import kotlinx.coroutines.launch

enum class ReportsView { EVENTS, USERS, FEDERATION }

/** Reported events, reported users and federation. */
@Composable
fun ReportsScreen() {
    var view by rememberSaveable { mutableStateOf(ReportsView.EVENTS) }
    val actions = rememberScreenActions()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TabTitle(stringResource(R.string.tab_reports))
            ConnectionBanner()
            FilterRow(
                listOf(
                    ReportsView.EVENTS to stringResource(R.string.reports_events),
                    ReportsView.USERS to stringResource(R.string.reports_users),
                    ReportsView.FEDERATION to stringResource(R.string.federation_title),
                ),
                view,
            ) { view = it }
            Box(Modifier.weight(1f)) {
                when (view) {
                    ReportsView.EVENTS -> EventReportList()
                    ReportsView.USERS -> UserReportList(actions)
                    ReportsView.FEDERATION -> FederationList(actions)
                }
            }
        }
        SnackbarHost(actions.snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
}

/** Federation on its own page, from the Overview. */
@Composable
fun FederationScreen() {
    val actions = rememberScreenActions()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(stringResource(R.string.federation_title))
            Box(Modifier.weight(1f).navigationBarsPadding()) { FederationList(actions) }
        }
        SnackbarHost(actions.snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
private fun EventReportList() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val reports = remember { mutableStateListOf<EventReport>() }
    var next by remember { mutableStateOf<String?>(null) }
    var total by remember { mutableStateOf(0L) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ApiError?>(null) }
    var reload by remember { mutableStateOf(0) }

    suspend fun load(from: String?) {
        val client = active?.client ?: return
        loading = true
        try {
            val page = client.eventReports(from = from, limit = 50)
            if (from == null) reports.clear()
            reports += page.reports
            next = page.nextToken
            total = page.total
            error = null
        } catch (e: ApiError) {
            error = e
        } finally {
            loading = false
        }
    }
    LaunchedEffect(active?.server?.id, reload) { load(null) }

    PullToRefreshBox(isRefreshing = loading && reports.isNotEmpty(), onRefresh = { reload++ }) {
        LazyColumn(Modifier.fillMaxSize()) {
            val err = error
            when {
                reports.isEmpty() && err != null -> item { ErrorBlock(err) { reload++ } }
                reports.isEmpty() && loading -> item { Loading() }
                reports.isEmpty() -> item { EmptyState(Icons.Outlined.Flag, stringResource(R.string.reports_none), stringResource(R.string.reports_none_body), Modifier.padding(top = 32.dp)) }
                else -> {
                    item { Text(stringResource(R.string.reports_count, total), style = CtrlType.Data, color = CtrlColors.Muted, modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp)) }
                    items(reports, key = { it.id }) { r ->
                        GroupRow(
                            r.reason?.takeIf { it.isNotBlank() } ?: stringResource(R.string.report_no_reason),
                            listOfNotNull(
                                stringResource(R.string.report_against, r.sender),
                                stringResource(R.string.report_in_room, r.name ?: r.canonicalAlias ?: r.roomId),
                                stringResource(R.string.report_by_when, r.userId, relativeTime(r.receivedTs)),
                                r.score?.let { stringResource(R.string.report_score, it) },
                            ).joinToString("\n"),
                            icon = Icons.Outlined.Flag,
                            iconTint = Health.warn,
                            showChevron = true,
                            onClick = { nav.navigate(ReportRoute(r.id)) },
                        )
                    }
                    if (next != null) {
                        item {
                            CtrlTextButton(onClick = { scope.launch { load(next) } }, enabled = !loading, modifier = Modifier.fillMaxWidth().padding(8.dp)) { Text(stringResource(R.string.load_more)) }
                        }
                    }
                }
            }
            item { ListEndSpacer() }
        }
    }
}

@Composable
private fun UserReportList(actions: ScreenActions) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server
    var reports by remember { mutableStateOf<List<UserReport>?>(null) }
    var error by remember { mutableStateOf<ApiError?>(null) }
    var reload by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<UserReport?>(null) }
    var deleting by remember { mutableStateOf<UserReport?>(null) }

    LaunchedEffect(server?.id, reload) {
        val client = active?.client ?: return@LaunchedEffect
        try {
            reports = client.userReports(limit = 100).reports
            error = null
        } catch (e: ApiError) {
            error = e
        }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        val list = reports
        val err = error
        when {
            list == null && err != null -> item { ErrorBlock(err) { reload++ } }
            list == null -> item { Loading() }
            list.isEmpty() -> item { EmptyState(Icons.Outlined.PersonSearch, stringResource(R.string.user_reports_none), stringResource(R.string.user_reports_none_body), Modifier.padding(top = 32.dp)) }
            else -> items(list, key = { it.id }) { r ->
                GroupRow(
                    r.targetUserId,
                    listOfNotNull(
                        r.reason?.takeIf { it.isNotBlank() } ?: stringResource(R.string.report_no_reason),
                        stringResource(R.string.report_by_when, r.userId, relativeTime(r.receivedTs)),
                    ).joinToString("\n"),
                    icon = Icons.Outlined.PersonSearch,
                    iconTint = Health.warn,
                    onClick = { selected = r },
                )
            }
        }
        item { ListEndSpacer() }
    }

    val client = active?.client ?: return
    selected?.let { r ->
        val local = r.targetUserId.endsWith(":${server?.serverName}")
        CtrlSheet(onDismissRequest = { selected = null }) {
            Text(r.targetUserId, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Text(r.reason ?: stringResource(R.string.report_no_reason), modifier = Modifier.padding(horizontal = 24.dp))
            Text(formatDateTime(context, r.receivedTs), style = CtrlType.Data, color = CtrlColors.Muted, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            Group {
                if (local) GroupRow(stringResource(R.string.open_user), stringResource(R.string.open_user_subtitle), showChevron = true, onClick = { selected = null; nav.navigate(UserRoute(r.targetUserId)) })
                if (server?.readOnly == false) GroupRow(stringResource(R.string.delete_report), stringResource(R.string.delete_report_subtitle), titleColor = MaterialTheme.colorScheme.error, onClick = { selected = null; deleting = r })
            }
            Box(Modifier.navigationBarsPadding().padding(bottom = 16.dp))
        }
    }
    deleting?.let { r ->
        ConfirmDialog(
            title = stringResource(R.string.delete_report),
            body = stringResource(R.string.delete_report_body),
            confirmLabel = stringResource(R.string.delete),
            onDismiss = { deleting = null },
            onConfirm = {
                deleting = null
                actions.run(context.getString(R.string.done_deleted), after = { reload++ }) { client.deleteUserReport(r.id) }
            },
        )
    }
}

@Composable
fun FederationList(actions: ScreenActions) {
    val context = LocalContext.current
    val c = context.container
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server
    var list by remember { mutableStateOf<List<Destination>?>(null) }
    var total by remember { mutableStateOf(0L) }
    var error by remember { mutableStateOf<ApiError?>(null) }
    var reload by remember { mutableStateOf(0) }
    var resetting by remember { mutableStateOf<Destination?>(null) }

    LaunchedEffect(server?.id, reload) {
        val client = active?.client ?: return@LaunchedEffect
        try {
            val page = client.destinations(limit = 200, failingFirst = true)
            // Failing ones first, then the rest alphabetically.
            list = page.destinations.sortedWith(compareByDescending<Destination> { it.failing }.thenBy { it.destination })
            total = page.total
            error = null
        } catch (e: ApiError) {
            error = e
        }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        val l = list
        val err = error
        when {
            l == null && err != null -> item { ErrorBlock(err) { reload++ } }
            l == null -> item { Loading() }
            l.isEmpty() -> item { EmptyState(Icons.Outlined.Hub, stringResource(R.string.federation_none), stringResource(R.string.federation_none_body), Modifier.padding(top = 32.dp)) }
            else -> {
                item {
                    Text(
                        stringResource(R.string.federation_count, total, l.count { it.failing }),
                        style = CtrlType.Data, color = CtrlColors.Muted, modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp),
                    )
                }
                items(l, key = { it.destination }) { d ->
                    GroupRow(
                        d.destination,
                        if (d.failing) {
                            listOfNotNull(
                                d.failureTs?.let { stringResource(R.string.dest_failing_since, formatDateTime(context, it)) },
                                stringResource(R.string.dest_last_try, relativeTime(d.retryLastTs)),
                                d.retryInterval.takeIf { it > 0 }?.let { stringResource(R.string.dest_retry_in, relativeTime(d.retryLastTs + it)) },
                            ).joinToString("\n")
                        } else {
                            stringResource(R.string.dest_ok)
                        },
                        icon = if (d.failing) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
                        iconTint = if (d.failing) Health.critical else Health.ok,
                        onClick = if (d.failing && server?.readOnly == false) ({ resetting = d }) else null,
                    )
                }
                item { Text(stringResource(R.string.federation_footer), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, modifier = Modifier.padding(Gutter)) }
            }
        }
        item { ListEndSpacer() }
    }
    val client = active?.client ?: return
    resetting?.let { d ->
        ConfirmDialog(
            title = stringResource(R.string.reset_connection_title, d.destination),
            body = stringResource(R.string.reset_connection_body),
            confirmLabel = stringResource(R.string.reset_connection),
            danger = false,
            onDismiss = { resetting = null },
            onConfirm = {
                resetting = null
                actions.run(context.getString(R.string.done_reset), after = { reload++ }) { client.resetConnection(d.destination) }
            },
        )
    }
}
