package com.vknxworks.synapsectrl.ui.rooms

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
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.container
import com.vknxworks.synapsectrl.data.admin.AdminRoom
import com.vknxworks.synapsectrl.data.admin.RoomOrder
import com.vknxworks.synapsectrl.data.admin.rooms
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
import com.vknxworks.synapsectrl.ui.components.topDivider
import com.vknxworks.synapsectrl.ui.nav.LocalNavigator
import com.vknxworks.synapsectrl.ui.nav.RoomRoute
import com.vknxworks.synapsectrl.ui.theme.CtrlColors
import com.vknxworks.synapsectrl.ui.theme.CtrlType
import com.vknxworks.synapsectrl.ui.theme.Gutter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class RoomFilter { ALL, PUBLIC, EMPTY, LARGE }

/** Rooms with at least this many members count as large. */
private const val LARGE_ROOM = 25L

@Composable
fun RoomsScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val active by c.sessions.active.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(RoomFilter.ALL) }
    var order by rememberSaveable { mutableStateOf(RoomOrder.NAME) }
    var sortOpen by remember { mutableStateOf(false) }

    val rooms = remember { mutableStateListOf<AdminRoom>() }
    var next by remember { mutableStateOf<String?>(null) }
    var total by remember { mutableStateOf(0L) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ApiError?>(null) }
    var reload by remember { mutableStateOf(0) }

    suspend fun load(from: String?) {
        val client = active?.client ?: return
        loading = true
        try {
            val page = client.rooms(
                from = from,
                limit = 50,
                search = query.trim().takeIf { it.isNotEmpty() },
                // Large rooms come biggest first.
                order = if (filter == RoomFilter.LARGE) RoomOrder.MEMBERS else order,
                publicOnly = if (filter == RoomFilter.PUBLIC) true else null,
                emptyOnly = if (filter == RoomFilter.EMPTY) true else null,
            )
            if (from == null) rooms.clear()
            rooms += if (filter == RoomFilter.LARGE) page.rooms.filter { it.joinedMembers >= LARGE_ROOM } else page.rooms
            // Sorted by size, the first room under the limit means the rest are small too.
            next = if (filter == RoomFilter.LARGE && page.rooms.any { it.joinedMembers < LARGE_ROOM }) null else page.nextBatch
            total = page.totalRooms
            error = null
        } catch (e: ApiError) {
            error = e
            active?.server?.id?.let { c.sessions.reportError(it, e) }
        } finally {
            loading = false
        }
    }

    LaunchedEffect(active?.server?.id, query, filter, order, reload) {
        if (query.isNotEmpty()) delay(350)
        load(null)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_rooms))
        ConnectionBanner()
        SearchField(query, { query = it }, stringResource(R.string.rooms_search))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                FilterRow(
                    listOf(
                        RoomFilter.ALL to stringResource(R.string.filter_all),
                        RoomFilter.PUBLIC to stringResource(R.string.filter_public),
                        RoomFilter.EMPTY to stringResource(R.string.filter_empty),
                        RoomFilter.LARGE to stringResource(R.string.filter_large),
                    ),
                    filter,
                ) { filter = it }
            }
            CtrlSquareButton(Icons.Outlined.Sort, stringResource(R.string.sort), onClick = { sortOpen = true }, modifier = Modifier.padding(end = Gutter))
        }
        PullToRefreshBox(isRefreshing = loading && rooms.isNotEmpty(), onRefresh = { reload++ }, modifier = Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize()) {
                val err = error
                when {
                    rooms.isEmpty() && err != null -> item { ErrorBlock(err) { reload++ } }
                    rooms.isEmpty() && loading -> item { Loading() }
                    rooms.isEmpty() -> item {
                        EmptyState(Icons.Outlined.Forum, stringResource(R.string.rooms_empty), stringResource(R.string.rooms_empty_body), Modifier.padding(top = 32.dp))
                    }
                    else -> {
                        item { Text(stringResource(R.string.rooms_count, total), style = CtrlType.Data, color = CtrlColors.Muted, modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp)) }
                        items(rooms, key = { it.roomId }) { r -> RoomRow(r) { nav.navigate(RoomRoute(r.roomId)) } }
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

    if (sortOpen) {
        ChoiceSheet(
            stringResource(R.string.sort),
            listOf(
                RoomOrder.NAME to stringResource(R.string.sort_room_name),
                RoomOrder.MEMBERS to stringResource(R.string.sort_members),
                RoomOrder.LOCAL_MEMBERS to stringResource(R.string.sort_local_members),
                RoomOrder.STATE to stringResource(R.string.sort_state_events),
            ),
            order,
            onDismiss = { sortOpen = false },
        ) { order = it; sortOpen = false }
    }
}

@Composable
fun RoomRow(r: AdminRoom, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().topDivider().clickable(onClick = onClick).padding(horizontal = Gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(r.avatar, r.label)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(r.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (r.isSpace) Tag(stringResource(R.string.tag_space), CtrlColors.Muted)
                if (r.public) Tag(stringResource(R.string.tag_public))
                if (r.encryption != null) Tag(stringResource(R.string.tag_encrypted), CtrlColors.Muted)
                if (!r.federatable) Tag(stringResource(R.string.tag_local_only), CtrlColors.Muted)
            }
            Text(r.canonicalAlias ?: r.roomId, style = CtrlType.Data, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    stringResource(R.string.room_members_line, r.joinedLocalMembers, r.joinedMembers),
                    r.creator?.let { stringResource(R.string.room_creator, it) },
                    stringResource(R.string.room_state_events, r.stateEvents),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = CtrlColors.Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
