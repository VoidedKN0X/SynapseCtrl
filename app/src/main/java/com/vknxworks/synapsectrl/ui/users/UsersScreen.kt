package com.vknxworks.synapsectrl.ui.users

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.container
import com.vknxworks.synapsectrl.data.admin.AdminUser
import com.vknxworks.synapsectrl.data.admin.UserOrder
import com.vknxworks.synapsectrl.data.admin.devices
import com.vknxworks.synapsectrl.data.admin.users
import com.vknxworks.synapsectrl.data.api.ApiError
import com.vknxworks.synapsectrl.ui.common.Avatar
import com.vknxworks.synapsectrl.ui.common.ErrorBlock
import com.vknxworks.synapsectrl.ui.common.FilterRow
import com.vknxworks.synapsectrl.ui.common.ListEndSpacer
import com.vknxworks.synapsectrl.ui.common.Loading
import com.vknxworks.synapsectrl.ui.common.SearchField
import com.vknxworks.synapsectrl.ui.common.Tag
import com.vknxworks.synapsectrl.ui.components.ChoiceSheet
import com.vknxworks.synapsectrl.ui.components.ConnectionBanner
import com.vknxworks.synapsectrl.ui.components.CtrlSquareButton
import com.vknxworks.synapsectrl.ui.components.CtrlTextButton
import com.vknxworks.synapsectrl.ui.components.EmptyState
import com.vknxworks.synapsectrl.ui.components.TabTitle
import com.vknxworks.synapsectrl.ui.components.relativeTime
import com.vknxworks.synapsectrl.ui.components.topDivider
import com.vknxworks.synapsectrl.ui.nav.CreateUserRoute
import com.vknxworks.synapsectrl.ui.nav.InvitesRoute
import com.vknxworks.synapsectrl.ui.nav.LocalNavigator
import com.vknxworks.synapsectrl.ui.nav.UserRoute
import com.vknxworks.synapsectrl.ui.theme.CtrlColors
import com.vknxworks.synapsectrl.ui.theme.CtrlType
import com.vknxworks.synapsectrl.ui.theme.Gutter
import com.vknxworks.synapsectrl.ui.theme.Health
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class UserFilter { ACTIVE, DEACTIVATED, ADMINS, GUESTS, ALL }

/** Local users: search, filter, sort, and open one to manage it. */
@Composable
fun UsersScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(UserFilter.ACTIVE) }
    var order by rememberSaveable { mutableStateOf(UserOrder.NAME) }
    var sortOpen by remember { mutableStateOf(false) }

    val users = remember { mutableStateListOf<AdminUser>() }
    val deviceCounts = remember { mutableStateMapOf<String, Long>() }
    var next by remember { mutableStateOf<String?>(null) }
    var total by remember { mutableStateOf(0L) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ApiError?>(null) }
    var reload by remember { mutableStateOf(0) }

    suspend fun load(from: String?) {
        val client = active?.client ?: return
        loading = true
        try {
            val newest = order == UserOrder.CREATED || order == UserOrder.LAST_SEEN
            val page = client.users(
                from = from,
                limit = 50,
                name = query.trim().takeIf { it.isNotEmpty() },
                deactivated = when (filter) {
                    UserFilter.ACTIVE, UserFilter.ADMINS, UserFilter.GUESTS -> false
                    UserFilter.DEACTIVATED -> true
                    UserFilter.ALL -> null
                },
                admins = if (filter == UserFilter.ADMINS) true else null,
                order = order,
                backwards = newest,
            )
            if (from == null) users.clear()
            // The admin API can't list only guests, so they're picked from each page.
            users += if (filter == UserFilter.GUESTS) page.users.filter { it.isGuest } else page.users
            next = page.nextToken
            total = page.total
            error = null
            // Device counts for the list, a few at a time; a failure just leaves the count out.
            page.users.filter { it.name !in deviceCounts }.chunked(8).forEach { batch ->
                batch.map { u -> scope.launch { runCatching { client.devices(u.name).total }.getOrNull()?.let { deviceCounts[u.name] = it } } }
            }
        } catch (e: ApiError) {
            error = e
            c.sessions.reportError(server?.id.orEmpty(), e)
        } finally {
            loading = false
        }
    }

    // A new search waits for typing to pause.
    LaunchedEffect(server?.id, query, filter, order, reload) {
        if (query.isNotEmpty()) delay(350)
        load(null)
    }

    val actions = com.vknxworks.synapsectrl.ui.common.rememberScreenActions()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TabTitle(stringResource(R.string.tab_users)) {
                CtrlSquareButton(Icons.Outlined.VpnKey, stringResource(R.string.invites_title), onClick = { nav.navigate(InvitesRoute()) })
                if (server?.readOnly == false) {
                    Spacer(Modifier.width(8.dp))
                    CtrlSquareButton(Icons.Outlined.PersonAdd, stringResource(R.string.create_user), onClick = { nav.navigate(CreateUserRoute) })
                }
            }
            ConnectionBanner()
            SearchField(query, { query = it }, stringResource(R.string.users_search))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    FilterRow(
                        listOf(
                            UserFilter.ACTIVE to stringResource(R.string.filter_active),
                            UserFilter.ADMINS to stringResource(R.string.filter_admins),
                            UserFilter.DEACTIVATED to stringResource(R.string.filter_deactivated),
                            UserFilter.GUESTS to stringResource(R.string.filter_guests),
                            UserFilter.ALL to stringResource(R.string.filter_all),
                        ),
                        filter,
                    ) { filter = it }
                }
                CtrlSquareButton(Icons.Outlined.Sort, stringResource(R.string.sort), onClick = { sortOpen = true }, modifier = Modifier.padding(end = Gutter))
            }
            PullToRefreshBox(
                isRefreshing = loading && users.isNotEmpty(),
                onRefresh = { reload++ },
                modifier = Modifier.weight(1f),
            ) {
                LazyColumn(Modifier.fillMaxSize()) {
                    val err = error
                    when {
                        users.isEmpty() && err != null -> item { ErrorBlock(err) { reload++ } }
                        users.isEmpty() && loading -> item { Loading() }
                        users.isEmpty() -> item {
                            EmptyState(Icons.Outlined.Group, stringResource(R.string.users_empty), stringResource(R.string.users_empty_body), Modifier.padding(top = 32.dp))
                        }
                        else -> {
                            item { Text(stringResource(R.string.users_count, total), style = CtrlType.Data, color = CtrlColors.Muted, modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp)) }
                            items(users, key = { it.name }) { u -> UserRow(u, deviceCounts[u.name]) { nav.navigate(UserRoute(u.name)) } }
                            if (next != null) {
                                item {
                                    CtrlTextButton(onClick = { scope.launch { load(next) } }, enabled = !loading, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                        Text(stringResource(R.string.load_more))
                                    }
                                }
                            }
                        }
                    }
                    item { ListEndSpacer() }
                }
            }
        }
        SnackbarHost(actions.snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (sortOpen) {
        ChoiceSheet(
            stringResource(R.string.sort),
            listOf(
                UserOrder.NAME to stringResource(R.string.sort_user_id),
                UserOrder.DISPLAYNAME to stringResource(R.string.sort_display_name),
                UserOrder.CREATED to stringResource(R.string.sort_newest),
                UserOrder.LAST_SEEN to stringResource(R.string.sort_last_seen),
            ),
            order,
            onDismiss = { sortOpen = false },
        ) { order = it; sortOpen = false }
    }
}

@Composable
fun UserRow(u: AdminUser, devices: Long?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().topDivider().clickable(onClick = onClick).padding(horizontal = Gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(u.avatarUrl, u.label)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(u.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (u.admin) Tag(stringResource(R.string.tag_admin))
                if (u.deactivated) Tag(stringResource(R.string.tag_deactivated), Health.critical)
                if (u.shadowBanned) Tag(stringResource(R.string.tag_shadow_banned), Health.warn)
                if (u.suspended) Tag(stringResource(R.string.tag_suspended), Health.warn)
                if (u.isGuest) Tag(stringResource(R.string.tag_guest), CtrlColors.Muted)
            }
            Text(u.name, style = CtrlType.Data, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val seen = u.lastSeenTs?.takeIf { it > 0 }?.let { stringResource(R.string.last_seen, relativeTime(it)) } ?: stringResource(R.string.never_seen)
            val devs = devices?.let { stringResource(R.string.device_count, it) }
            Text(listOfNotNull(seen, devs).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
        }
    }
}
