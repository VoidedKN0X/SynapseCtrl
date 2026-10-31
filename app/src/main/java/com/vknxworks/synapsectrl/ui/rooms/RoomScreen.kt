package com.vknxworks.synapsectrl.ui.rooms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.container
import com.vknxworks.synapsectrl.data.admin.AdminRoom
import com.vknxworks.synapsectrl.data.admin.BlockStatus
import com.vknxworks.synapsectrl.data.admin.DeleteStatus
import com.vknxworks.synapsectrl.data.admin.blockStatus
import com.vknxworks.synapsectrl.data.admin.deleteRoom
import com.vknxworks.synapsectrl.data.admin.deleteStatus
import com.vknxworks.synapsectrl.data.admin.kick
import com.vknxworks.synapsectrl.data.admin.makeRoomAdmin
import com.vknxworks.synapsectrl.data.admin.quarantineRoomMedia
import com.vknxworks.synapsectrl.data.admin.room
import com.vknxworks.synapsectrl.data.admin.roomMembers
import com.vknxworks.synapsectrl.data.admin.roomState
import com.vknxworks.synapsectrl.data.admin.setBlocked
import com.vknxworks.synapsectrl.data.api.ApiError
import com.vknxworks.synapsectrl.ui.common.Avatar
import com.vknxworks.synapsectrl.ui.common.ErrorBlock
import com.vknxworks.synapsectrl.ui.common.Loading
import com.vknxworks.synapsectrl.ui.common.TypedConfirmDialog
import com.vknxworks.synapsectrl.ui.common.rememberScreenActions
import com.vknxworks.synapsectrl.ui.components.ChoiceSheet
import com.vknxworks.synapsectrl.ui.components.ConfirmDialog
import com.vknxworks.synapsectrl.ui.components.CtrlAlertDialog
import com.vknxworks.synapsectrl.ui.components.CtrlTextButton
import com.vknxworks.synapsectrl.ui.components.CtrlTextField
import com.vknxworks.synapsectrl.ui.components.Group
import com.vknxworks.synapsectrl.ui.components.GroupRow
import com.vknxworks.synapsectrl.ui.components.GroupSwitch
import com.vknxworks.synapsectrl.ui.components.ScreenTopBar
import com.vknxworks.synapsectrl.ui.nav.LocalNavigator
import com.vknxworks.synapsectrl.ui.nav.UserRoute
import com.vknxworks.synapsectrl.ui.theme.CtrlColors
import com.vknxworks.synapsectrl.ui.theme.CtrlType
import com.vknxworks.synapsectrl.ui.theme.Gutter
import com.vknxworks.synapsectrl.ui.theme.Health
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private class RoomInfo(val room: AdminRoom, val members: List<String>?, val stateTypes: Map<String, Int>?, val block: BlockStatus?)

private sealed class Pending {
    data object MakeAdmin : Pending()
    data class Block(val on: Boolean) : Pending()
    data object Quarantine : Pending()
    data object PickKick : Pending()
    data class Kick(val userId: String) : Pending()
    data object Delete : Pending()
}

/** One room: its settings and members, and the admin actions for it. */
@Composable
fun RoomScreen(roomId: String) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server
    val readOnly = server?.readOnly != false
    val serverName = server?.serverName.orEmpty()
    val actions = rememberScreenActions()

    var info by remember { mutableStateOf<RoomInfo?>(null) }
    var error by remember { mutableStateOf<ApiError?>(null) }
    var reload by remember { mutableStateOf(0) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(roomId, reload, server?.id) {
        val client = active?.client ?: return@LaunchedEffect
        try {
            info = coroutineScope {
                val room = async { client.room(roomId) }
                val members = async { runCatching { client.roomMembers(roomId).members }.getOrNull() }
                val state = async {
                    runCatching {
                        client.roomState(roomId).state
                            .mapNotNull { (it["type"] as? JsonPrimitive)?.contentOrNull }
                            .groupingBy { it }.eachCount()
                    }.getOrNull()
                }
                val block = async { runCatching { client.blockStatus(roomId) }.getOrNull() }
                RoomInfo(room.await(), members.await(), state.await(), block.await())
            }
            error = null
        } catch (e: ApiError) {
            error = e
        }
    }

    fun change(success: Int, block: suspend () -> Unit) {
        pending = null
        actions.run(context.getString(success), after = { reload++ }) { block() }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(info?.room?.label ?: roomId)
            val i = info
            val err = error
            when {
                i == null && err != null -> ErrorBlock(err) { reload++ }
                i == null -> Loading()
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                    val r = i.room
                    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(r.avatar, r.label, 64.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(r.label, style = MaterialTheme.typography.headlineSmall)
                            Text(r.roomId, style = CtrlType.Data, color = CtrlColors.Muted)
                            r.topic?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, maxLines = 3) }
                        }
                    }
                    if (i.block?.block == true) {
                        Text(
                            stringResource(R.string.room_blocked_by, i.block.userId ?: "—"),
                            color = Health.critical,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = Gutter),
                        )
                    }

                    Group(stringResource(R.string.group_room_settings)) {
                        GroupRow(stringResource(R.string.room_members_title), stringResource(R.string.room_members_line, r.joinedLocalMembers, r.joinedMembers) + " · " + stringResource(R.string.room_local_devices, r.joinedLocalDevices), icon = Icons.Outlined.Person)
                        r.canonicalAlias?.let { GroupRow(stringResource(R.string.room_alias), it) }
                        r.creator?.let { GroupRow(stringResource(R.string.room_creator_title), it, onClick = if (it.endsWith(":$serverName")) ({ nav.navigate(UserRoute(it)) }) else null) }
                        GroupRow(
                            stringResource(R.string.room_access),
                            listOfNotNull(
                                stringResource(if (r.public) R.string.room_in_directory else R.string.room_not_in_directory),
                                r.joinRules?.let { stringResource(R.string.room_join_rule, it) },
                                stringResource(if (r.federatable) R.string.room_federated else R.string.room_not_federated),
                                r.guestAccess?.let { stringResource(R.string.room_guests, it) },
                            ).joinToString("\n"),
                            icon = Icons.Outlined.Public,
                        )
                        GroupRow(
                            stringResource(R.string.room_details),
                            listOfNotNull(
                                r.version?.let { stringResource(R.string.room_version, it) },
                                stringResource(if (r.encryption != null) R.string.room_encrypted else R.string.room_not_encrypted),
                                r.historyVisibility?.let { stringResource(R.string.room_history, it) },
                                stringResource(R.string.room_state_events, r.stateEvents),
                                if (r.isSpace) stringResource(R.string.room_is_space) else null,
                                if (r.tombstoned) stringResource(R.string.room_tombstoned, r.replacementRoom ?: "—") else null,
                            ).joinToString("\n"),
                            icon = Icons.Outlined.Info,
                        )
                        i.stateTypes?.takeIf { it.isNotEmpty() }?.let { types ->
                            GroupRow(
                                stringResource(R.string.room_state_title),
                                types.entries.sortedByDescending { it.value }.take(8).joinToString("\n") { "${it.key}: ${it.value}" },
                            )
                        }
                    }

                    val members = i.members
                    val local = members?.filter { it.endsWith(":$serverName") }.orEmpty()
                    Group(stringResource(R.string.group_room_members, members?.size ?: 0), footer = stringResource(R.string.room_members_footer)) {
                        if (members == null) GroupRow(stringResource(R.string.couldnt_load))
                        // Local members first: they can be opened and managed.
                        (local + (members.orEmpty() - local.toSet())).take(100).forEach { m ->
                            val isLocal = m in local
                            GroupRow(
                                m,
                                icon = Icons.Outlined.Person,
                                iconTint = if (isLocal) CtrlColors.AccentText else CtrlColors.Muted,
                                showChevron = isLocal,
                                onClick = if (isLocal) ({ nav.navigate(UserRoute(m)) }) else null,
                            )
                        }
                        if ((members?.size ?: 0) > 100) GroupRow(stringResource(R.string.room_more_members, members!!.size - 100))
                    }

                    if (!readOnly) {
                        Group(stringResource(R.string.group_room_actions)) {
                            GroupRow(stringResource(R.string.make_me_room_admin), stringResource(R.string.make_me_room_admin_subtitle), icon = Icons.Outlined.AdminPanelSettings, onClick = { pending = Pending.MakeAdmin })
                            if (local.isNotEmpty()) {
                                GroupRow(stringResource(R.string.remove_from_room), stringResource(R.string.remove_from_room_subtitle), icon = Icons.Outlined.PersonRemove, onClick = { pending = Pending.PickKick })
                            }
                            GroupSwitch(stringResource(R.string.block_room), stringResource(R.string.block_room_subtitle), checked = i.block?.block == true, icon = Icons.Outlined.Block) { pending = Pending.Block(it) }
                            GroupRow(stringResource(R.string.quarantine_room_media), stringResource(R.string.quarantine_room_media_subtitle), icon = Icons.Outlined.GppBad, onClick = { pending = Pending.Quarantine })
                        }
                        Group(footer = stringResource(R.string.delete_room_footer)) {
                            GroupRow(
                                stringResource(R.string.delete_room), stringResource(R.string.delete_room_subtitle),
                                icon = Icons.Outlined.DeleteForever, iconTint = MaterialTheme.colorScheme.error, titleColor = MaterialTheme.colorScheme.error,
                                onClick = { pending = Pending.Delete },
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(actions.snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    val client = active?.client ?: return
    val i = info ?: return
    when (val p = pending) {
        null -> Unit
        Pending.MakeAdmin -> ConfirmDialog(
            title = stringResource(R.string.make_me_room_admin),
            body = stringResource(R.string.make_me_room_admin_body, server?.userId.orEmpty()),
            confirmLabel = stringResource(R.string.make_me_room_admin_confirm),
            danger = false,
            onDismiss = { pending = null },
            onConfirm = { change(R.string.done_room_admin) { client.makeRoomAdmin(roomId, server?.userId.orEmpty()) } },
        )
        is Pending.Block -> ConfirmDialog(
            title = stringResource(if (p.on) R.string.block_room_title else R.string.unblock_room_title),
            body = stringResource(if (p.on) R.string.block_room_body else R.string.unblock_room_body),
            confirmLabel = stringResource(if (p.on) R.string.block else R.string.unblock),
            danger = p.on,
            onDismiss = { pending = null },
            onConfirm = { change(R.string.done_saved) { client.setBlocked(roomId, p.on) } },
        )
        Pending.Quarantine -> ConfirmDialog(
            title = stringResource(R.string.quarantine_room_media_title),
            body = stringResource(R.string.quarantine_room_media_body),
            confirmLabel = stringResource(R.string.quarantine),
            onDismiss = { pending = null },
            onConfirm = {
                pending = null
                actions.run(null) {
                    val n = client.quarantineRoomMedia(roomId).numQuarantined
                    actions.say(context.getString(R.string.done_quarantined, n))
                }
            },
        )
        Pending.PickKick -> ChoiceSheet(
            stringResource(R.string.remove_from_room),
            i.members.orEmpty().filter { it.endsWith(":$serverName") }.map { it to it },
            null,
            onDismiss = { pending = null },
        ) { pending = Pending.Kick(it) }
        is Pending.Kick -> KickDialog(
            userId = p.userId,
            onDismiss = { pending = null },
        ) { reason -> change(R.string.done_removed) { client.kick(roomId, p.userId, reason) } }
        Pending.Delete -> DeleteRoomDialog(
            room = i.room,
            adminUserId = server?.userId.orEmpty(),
            onDismiss = { pending = null },
        ) { block, purge, moveTo, name, message ->
            pending = null
            actions.run(null) {
                deleting = client.deleteRoom(roomId, block, purge, moveTo, name, message).deleteId
            }
        }
    }

    deleting?.let { id ->
        DeleteProgressDialog(deleteId = id, onClose = {
            deleting = null
            nav.back()
        })
    }
}

@Composable
private fun KickDialog(userId: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.kick_title, userId)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.kick_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                CtrlTextField(value = reason, onValueChange = { reason = it }, label = { Text(stringResource(R.string.reason_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { CtrlTextButton(onClick = { onConfirm(reason.trim()) }) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { CtrlTextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun DeleteRoomDialog(
    room: AdminRoom,
    adminUserId: String,
    onDismiss: () -> Unit,
    onConfirm: (block: Boolean, purge: Boolean, moveTo: String?, name: String?, message: String?) -> Unit,
) {
    var block by remember { mutableStateOf(true) }
    var purge by remember { mutableStateOf(true) }
    var move by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    TypedConfirmDialog(
        title = stringResource(R.string.delete_room_title, room.label),
        body = stringResource(R.string.delete_room_body, room.joinedLocalMembers),
        expected = room.roomId,
        confirmLabel = stringResource(R.string.delete_room),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(block, purge, if (move) adminUserId else null, name.trim().ifBlank { null }, message.trim().ifBlank { null }) },
        extra = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GroupSwitch(stringResource(R.string.delete_room_block), stringResource(R.string.delete_room_block_sub), checked = block) { block = it }
                GroupSwitch(stringResource(R.string.delete_room_purge), stringResource(R.string.delete_room_purge_sub), checked = purge) { purge = it }
                GroupSwitch(stringResource(R.string.delete_room_move), stringResource(R.string.delete_room_move_sub, adminUserId), checked = move) { move = it }
                if (move) {
                    CtrlTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.delete_room_new_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    CtrlTextField(value = message, onValueChange = { message = it }, label = { Text(stringResource(R.string.delete_room_message)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
            }
        },
    )
}

/** Follows the background deletion with GET /_synapse/admin/v2/rooms/delete_status/<delete_id>. */
@Composable
private fun DeleteProgressDialog(deleteId: String, onClose: () -> Unit) {
    val c = LocalContext.current.container
    var status by remember { mutableStateOf<DeleteStatus?>(null) }
    var failedToCheck by remember { mutableStateOf(false) }
    LaunchedEffect(deleteId) {
        while (status?.finished != true) {
            val client = c.sessions.active.value?.client ?: break
            val s = runCatching { client.deleteStatus(deleteId) }.getOrNull()
            if (s != null) {
                status = s
                failedToCheck = false
            } else {
                failedToCheck = true
            }
            if (status?.finished == true) break
            delay(2000)
        }
    }
    val s = status
    CtrlAlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                stringResource(
                    when (s?.status) {
                        "complete" -> R.string.delete_status_complete
                        "failed" -> R.string.delete_status_failed
                        else -> R.string.delete_status_running
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s?.finished != true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(if (s?.status == "active") R.string.delete_status_active else R.string.delete_status_scheduled))
                    }
                }
                if (failedToCheck) Text(stringResource(R.string.delete_status_check_failed), color = Health.warn, style = MaterialTheme.typography.bodySmall)
                s?.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                s?.shutdownRoom?.let { r ->
                    if (r.kickedUsers.isNotEmpty()) Text(stringResource(R.string.delete_status_kicked, r.kickedUsers.size), style = MaterialTheme.typography.bodySmall)
                    if (r.failedToKickUsers.isNotEmpty()) Text(stringResource(R.string.delete_status_not_kicked, r.failedToKickUsers.joinToString(", ")), style = MaterialTheme.typography.bodySmall, color = Health.warn)
                    if (r.localAliases.isNotEmpty()) Text(stringResource(R.string.delete_status_aliases, r.localAliases.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
                    r.newRoomId?.let { Text(stringResource(R.string.delete_status_new_room, it), style = MaterialTheme.typography.bodySmall) }
                }
                Text(stringResource(R.string.delete_status_id, deleteId), style = CtrlType.Data, color = CtrlColors.Muted)
            }
        },
        confirmButton = {
            CtrlTextButton(onClick = onClose) { Text(stringResource(if (s?.finished == true) R.string.lock_done else R.string.close_keep_running)) }
        },
    )
}
