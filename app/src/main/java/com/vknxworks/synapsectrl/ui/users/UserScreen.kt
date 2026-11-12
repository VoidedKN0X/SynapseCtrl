package com.vknxworks.synapsectrl.ui.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.container
import com.vknxworks.synapsectrl.data.admin.AdminDevice
import com.vknxworks.synapsectrl.data.admin.AdminUser
import com.vknxworks.synapsectrl.data.admin.RateLimit
import com.vknxworks.synapsectrl.data.admin.UserMediaStat
import com.vknxworks.synapsectrl.data.admin.clearRateLimit
import com.vknxworks.synapsectrl.data.admin.deactivate
import com.vknxworks.synapsectrl.data.admin.deleteDevice
import com.vknxworks.synapsectrl.data.admin.deleteDevices
import com.vknxworks.synapsectrl.data.admin.devices
import com.vknxworks.synapsectrl.data.admin.joinedRooms
import com.vknxworks.synapsectrl.data.admin.quarantineUserMedia
import com.vknxworks.synapsectrl.data.admin.rateLimit
import com.vknxworks.synapsectrl.data.admin.resetPassword
import com.vknxworks.synapsectrl.data.admin.setAdmin
import com.vknxworks.synapsectrl.data.admin.setDisplayName
import com.vknxworks.synapsectrl.data.admin.setLocked
import com.vknxworks.synapsectrl.data.admin.setRateLimit
import com.vknxworks.synapsectrl.data.admin.setShadowBanned
import com.vknxworks.synapsectrl.data.admin.setSuspended
import com.vknxworks.synapsectrl.data.admin.user
import com.vknxworks.synapsectrl.data.admin.userMediaStat
import com.vknxworks.synapsectrl.data.api.ApiError
import com.vknxworks.synapsectrl.ui.common.Avatar
import com.vknxworks.synapsectrl.ui.common.ErrorBlock
import com.vknxworks.synapsectrl.ui.common.Loading
import com.vknxworks.synapsectrl.ui.common.Tag
import com.vknxworks.synapsectrl.ui.common.TypedConfirmDialog
import com.vknxworks.synapsectrl.ui.common.formatBytes
import com.vknxworks.synapsectrl.ui.common.formatDateTime
import com.vknxworks.synapsectrl.ui.common.rememberScreenActions
import com.vknxworks.synapsectrl.ui.common.strongPassword
import com.vknxworks.synapsectrl.ui.components.ConfirmDialog
import com.vknxworks.synapsectrl.ui.components.CtrlAlertDialog
import com.vknxworks.synapsectrl.ui.components.CtrlTextButton
import com.vknxworks.synapsectrl.ui.components.CtrlTextField
import com.vknxworks.synapsectrl.ui.components.Group
import com.vknxworks.synapsectrl.ui.components.GroupRow
import com.vknxworks.synapsectrl.ui.components.GroupSwitch
import com.vknxworks.synapsectrl.ui.components.ScreenTopBar
import com.vknxworks.synapsectrl.ui.components.SecureScreen
import com.vknxworks.synapsectrl.ui.components.copySensitive
import com.vknxworks.synapsectrl.ui.components.relativeTime
import com.vknxworks.synapsectrl.ui.nav.LocalNavigator
import com.vknxworks.synapsectrl.ui.nav.RoomRoute
import com.vknxworks.synapsectrl.ui.nav.ServerNoticeRoute
import com.vknxworks.synapsectrl.ui.nav.UserMediaRoute
import com.vknxworks.synapsectrl.ui.theme.CtrlColors
import com.vknxworks.synapsectrl.ui.theme.CtrlType
import com.vknxworks.synapsectrl.ui.theme.Gutter
import com.vknxworks.synapsectrl.ui.theme.Health
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Everything about one user, and what can be done to the account. */
private class UserInfo(
    val user: AdminUser,
    val devices: List<AdminDevice>?,
    val rooms: List<String>?,
    val media: UserMediaStat?,
    val rateLimit: RateLimit?,
)

/** A dialog or confirmation that is open. */
private sealed class Pending {
    data object ResetPassword : Pending()
    data object DisplayName : Pending()
    data object RateLimitEdit : Pending()
    data class Admin(val on: Boolean) : Pending()
    data class ShadowBan(val on: Boolean) : Pending()
    data class Suspend(val on: Boolean) : Pending()
    data class Lock(val on: Boolean) : Pending()
    data object LogoutAll : Pending()
    data class LogoutDevice(val device: AdminDevice) : Pending()
    data object QuarantineMedia : Pending()
    data object Deactivate : Pending()
}

@Composable
fun UserScreen(userId: String) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server
    val readOnly = server?.readOnly != false
    val isMe = server?.userId == userId
    val actions = rememberScreenActions()

    var info by remember { mutableStateOf<UserInfo?>(null) }
    var error by remember { mutableStateOf<ApiError?>(null) }
    var reload by remember { mutableStateOf(0) }
    var pending by remember { mutableStateOf<Pending?>(null) }

    LaunchedEffect(userId, reload, server?.id) {
        val client = active?.client ?: return@LaunchedEffect
        try {
            info = coroutineScope {
                val user = async { client.user(userId) }
                val devices = async { runCatching { client.devices(userId).devices }.getOrNull() }
                val rooms = async { runCatching { client.joinedRooms(userId).joinedRooms }.getOrNull() }
                val media = async { runCatching { client.userMediaStat(userId) }.getOrNull() }
                val limit = async { runCatching { client.rateLimit(userId) }.getOrNull() }
                UserInfo(user.await(), devices.await(), rooms.await(), media.await(), limit.await())
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
            ScreenTopBar(info?.user?.label ?: userId)
            val i = info
            val err = error
            when {
                i == null && err != null -> ErrorBlock(err) { reload++ }
                i == null -> Loading()
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                    val u = i.user
                    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(u.avatarUrl, u.label, 64.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(u.label, style = MaterialTheme.typography.headlineSmall)
                            Text(u.name, style = CtrlType.Data, color = CtrlColors.Muted)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (u.admin) Tag(stringResource(R.string.tag_admin))
                                if (u.deactivated) Tag(stringResource(R.string.tag_deactivated), Health.critical)
                                if (u.erased) Tag(stringResource(R.string.tag_erased), Health.critical)
                                if (u.shadowBanned) Tag(stringResource(R.string.tag_shadow_banned), Health.warn)
                                if (u.suspended) Tag(stringResource(R.string.tag_suspended), Health.warn)
                                if (u.locked) Tag(stringResource(R.string.tag_locked), Health.warn)
                                if (u.isGuest) Tag(stringResource(R.string.tag_guest), CtrlColors.Muted)
                            }
                        }
                    }
                    if (isMe) {
                        Text(stringResource(R.string.user_is_you), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, modifier = Modifier.padding(horizontal = Gutter))
                    }

                    // --- Profile ---
                    Group(stringResource(R.string.group_profile)) {
                        GroupRow(
                            stringResource(R.string.display_name),
                            u.displayname?.takeIf { it.isNotBlank() } ?: stringResource(R.string.not_set),
                            icon = Icons.Outlined.Badge,
                            showChevron = !readOnly && !u.deactivated,
                            onClick = if (!readOnly && !u.deactivated) ({ pending = Pending.DisplayName }) else null,
                        )
                        if (u.createdMs > 0) GroupRow(stringResource(R.string.created), formatDateTime(context, u.createdMs), icon = Icons.Outlined.Schedule)
                        GroupRow(
                            stringResource(R.string.last_seen_title),
                            u.lastSeenTs?.takeIf { it > 0 }?.let { "${relativeTime(it)} · ${formatDateTime(context, it)}" } ?: stringResource(R.string.never_seen),
                            icon = Icons.Outlined.Schedule,
                        )
                        u.userType?.let { GroupRow(stringResource(R.string.user_type), it) }
                        u.appserviceId?.let { GroupRow(stringResource(R.string.appservice), it) }
                    }

                    // --- Devices ---
                    val devices = i.devices
                    Group(stringResource(R.string.group_devices_count, devices?.size ?: 0)) {
                        if (devices == null) GroupRow(stringResource(R.string.couldnt_load))
                        devices?.sortedByDescending { it.lastSeenTs ?: 0 }?.forEach { d ->
                            GroupRow(
                                d.displayName?.takeIf { it.isNotBlank() } ?: d.deviceId,
                                listOfNotNull(
                                    d.deviceId,
                                    d.lastSeenTs?.takeIf { it > 0 }?.let { stringResource(R.string.last_seen, relativeTime(it)) },
                                    d.lastSeenIp,
                                ).joinToString(" · "),
                                icon = Icons.Outlined.Devices,
                                onClick = if (!readOnly) ({ pending = Pending.LogoutDevice(d) }) else null,
                            )
                        }
                        if (!readOnly && !devices.isNullOrEmpty()) {
                            GroupRow(
                                stringResource(R.string.logout_all_devices), icon = Icons.Outlined.Logout,
                                iconTint = MaterialTheme.colorScheme.error, titleColor = MaterialTheme.colorScheme.error,
                                onClick = { pending = Pending.LogoutAll },
                            )
                        }
                    }

                    // --- Rooms and media ---
                    Group(stringResource(R.string.group_activity)) {
                        val rooms = i.rooms
                        GroupRow(
                            stringResource(R.string.joined_rooms_count, rooms?.size ?: 0),
                            rooms?.take(3)?.joinToString(", ")?.ifBlank { null },
                            icon = Icons.Outlined.Forum,
                        )
                        rooms?.take(25)?.forEach { r ->
                            GroupRow(r, icon = null, showChevron = true, onClick = { nav.navigate(RoomRoute(r)) })
                        }
                        GroupRow(
                            stringResource(R.string.media_uploaded),
                            i.media?.let { stringResource(R.string.media_usage, it.mediaCount, formatBytes(context, it.mediaLength)) } ?: stringResource(R.string.media_none),
                            icon = Icons.Outlined.PermMedia,
                            showChevron = true,
                            onClick = { nav.navigate(UserMediaRoute(userId)) },
                        )
                    }

                    // --- Rate limit ---
                    Group(stringResource(R.string.group_rate_limit), footer = stringResource(R.string.rate_limit_footer)) {
                        val rl = i.rateLimit
                        GroupRow(
                            stringResource(R.string.rate_limit),
                            when {
                                rl == null -> stringResource(R.string.couldnt_load)
                                !rl.isSet -> stringResource(R.string.rate_limit_default)
                                rl.messagesPerSecond == 0L && rl.burstCount == 0L -> stringResource(R.string.rate_limit_off)
                                else -> stringResource(R.string.rate_limit_value, rl.messagesPerSecond ?: 0, rl.burstCount ?: 0)
                            },
                            icon = Icons.Outlined.Speed,
                            showChevron = !readOnly,
                            onClick = if (!readOnly) ({ pending = Pending.RateLimitEdit }) else null,
                        )
                    }

                    // --- Actions ---
                    if (!readOnly && !u.deactivated) {
                        Group(stringResource(R.string.group_account_actions)) {
                            GroupRow(stringResource(R.string.reset_password), stringResource(R.string.reset_password_subtitle), icon = Icons.Outlined.Password, onClick = { pending = Pending.ResetPassword })
                            if (!isMe) {
                                GroupSwitch(stringResource(R.string.server_admin), stringResource(R.string.server_admin_subtitle), checked = u.admin, icon = Icons.Outlined.AdminPanelSettings) { pending = Pending.Admin(it) }
                                GroupSwitch(stringResource(R.string.shadow_ban), stringResource(R.string.shadow_ban_subtitle), checked = u.shadowBanned, icon = Icons.Outlined.VisibilityOff) { pending = Pending.ShadowBan(it) }
                                GroupSwitch(stringResource(R.string.suspend), stringResource(R.string.suspend_subtitle), checked = u.suspended, icon = Icons.Outlined.PauseCircle) { pending = Pending.Suspend(it) }
                                GroupSwitch(stringResource(R.string.lock_account), stringResource(R.string.lock_account_subtitle), checked = u.locked, icon = if (u.locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen) { pending = Pending.Lock(it) }
                            }
                            GroupRow(stringResource(R.string.send_notice), stringResource(R.string.send_notice_subtitle), icon = Icons.Outlined.Campaign, showChevron = true, onClick = { nav.navigate(ServerNoticeRoute(userId)) })
                            GroupRow(stringResource(R.string.quarantine_user_media), stringResource(R.string.quarantine_user_media_subtitle), icon = Icons.Outlined.GppBad, onClick = { pending = Pending.QuarantineMedia })
                        }
                        if (!isMe) {
                            Group(footer = stringResource(R.string.deactivate_footer)) {
                                GroupRow(
                                    stringResource(R.string.deactivate), stringResource(R.string.deactivate_subtitle),
                                    icon = Icons.Outlined.PersonOff, iconTint = MaterialTheme.colorScheme.error, titleColor = MaterialTheme.colorScheme.error,
                                    onClick = { pending = Pending.Deactivate },
                                )
                            }
                        }
                    } else if (!readOnly && u.deactivated) {
                        Group(footer = stringResource(R.string.reactivate_footer)) {
                            GroupRow(stringResource(R.string.deactivated_title), stringResource(R.string.deactivated_subtitle), icon = Icons.Outlined.Block)
                        }
                    }
                }
            }
        }
        SnackbarHost(actions.snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    val client = active?.client
    val u = info?.user
    if (client == null || u == null) return
    when (val p = pending) {
        null -> Unit
        Pending.ResetPassword -> ResetPasswordDialog(
            userId = userId,
            onDismiss = { pending = null },
        ) { password, logout -> change(R.string.done_password) { client.resetPassword(userId, password, logout) } }
        Pending.DisplayName -> TextInputDialog(
            title = stringResource(R.string.display_name),
            initial = u.displayname.orEmpty(),
            hint = stringResource(R.string.display_name_hint),
            onDismiss = { pending = null },
        ) { name -> change(R.string.done_saved) { client.setDisplayName(userId, name.trim()) } }
        Pending.RateLimitEdit -> RateLimitDialog(
            current = info?.rateLimit,
            onDismiss = { pending = null },
            onClear = { change(R.string.done_saved) { client.clearRateLimit(userId) } },
        ) { mps, burst -> change(R.string.done_saved) { client.setRateLimit(userId, mps, burst) } }
        is Pending.Admin -> ConfirmDialog(
            title = stringResource(if (p.on) R.string.make_admin_title else R.string.remove_admin_title, u.label),
            body = stringResource(if (p.on) R.string.make_admin_body else R.string.remove_admin_body),
            confirmLabel = stringResource(if (p.on) R.string.make_admin else R.string.remove_admin),
            danger = p.on,
            onDismiss = { pending = null },
            onConfirm = { change(R.string.done_saved) { client.setAdmin(userId, p.on) } },
        )
        is Pending.ShadowBan -> ConfirmDialog(
            title = stringResource(if (p.on) R.string.shadow_ban_title else R.string.unshadow_ban_title, u.label),
            body = stringResource(if (p.on) R.string.shadow_ban_body else R.string.unshadow_ban_body),
            confirmLabel = stringResource(if (p.on) R.string.shadow_ban else R.string.unshadow_ban),
            danger = p.on,
            onDismiss = { pending = null },
            onConfirm = { change(R.string.done_saved) { client.setShadowBanned(userId, p.on) } },
        )
        is Pending.Suspend -> ConfirmDialog(
            title = stringResource(if (p.on) R.string.suspend_title else R.string.unsuspend_title, u.label),
            body = stringResource(if (p.on) R.string.suspend_body else R.string.unsuspend_body),
            confirmLabel = stringResource(if (p.on) R.string.suspend else R.string.unsuspend),
            danger = p.on,
            onDismiss = { pending = null },
            onConfirm = { change(R.string.done_saved) { client.setSuspended(userId, p.on) } },
        )
        is Pending.Lock -> ConfirmDialog(
            title = stringResource(if (p.on) R.string.lock_account_title else R.string.unlock_account_title, u.label),
            body = stringResource(if (p.on) R.string.lock_account_body else R.string.unlock_account_body),
            confirmLabel = stringResource(if (p.on) R.string.lock_account else R.string.unlock_account),
            danger = p.on,
            onDismiss = { pending = null },
            onConfirm = { change(R.string.done_saved) { client.setLocked(userId, p.on) } },
        )
        Pending.LogoutAll -> ConfirmDialog(
            title = stringResource(R.string.logout_all_title, u.label),
            body = stringResource(R.string.logout_all_body, info?.devices?.size ?: 0),
            confirmLabel = stringResource(R.string.logout_all_devices),
            onDismiss = { pending = null },
            onConfirm = { change(R.string.done_logged_out) { client.deleteDevices(userId, client.devices(userId).devices.map { it.deviceId }) } },
        )
        is Pending.LogoutDevice -> ConfirmDialog(
            title = stringResource(R.string.logout_device_title, p.device.displayName ?: p.device.deviceId),
            body = stringResource(R.string.logout_device_body, p.device.deviceId),
            confirmLabel = stringResource(R.string.logout_device),
            onDismiss = { pending = null },
            onConfirm = { change(R.string.done_logged_out) { client.deleteDevice(userId, p.device.deviceId) } },
        )
        Pending.QuarantineMedia -> ConfirmDialog(
            title = stringResource(R.string.quarantine_user_media_title, u.label),
            body = stringResource(R.string.quarantine_user_media_body),
            confirmLabel = stringResource(R.string.quarantine),
            onDismiss = { pending = null },
            onConfirm = {
                pending = null
                actions.run(null, after = { reload++ }) {
                    val n = client.quarantineUserMedia(userId).numQuarantined
                    actions.say(context.getString(R.string.done_quarantined, n))
                }
            },
        )
        Pending.Deactivate -> DeactivateDialog(
            user = u,
            onDismiss = { pending = null },
        ) { erase -> change(R.string.done_deactivated) { client.deactivate(userId, erase) } }
    }
}

@Composable
private fun DeactivateDialog(user: AdminUser, onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    var erase by remember { mutableStateOf(false) }
    TypedConfirmDialog(
        title = stringResource(R.string.deactivate_title, user.label),
        body = stringResource(R.string.deactivate_body),
        expected = user.name,
        confirmLabel = stringResource(if (erase) R.string.deactivate_and_erase else R.string.deactivate),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(erase) },
        extra = {
            Column {
                GroupSwitch(stringResource(R.string.erase), stringResource(R.string.erase_explained), checked = erase) { erase = it }
            }
        },
    )
}

@Composable
private fun ResetPasswordDialog(userId: String, onDismiss: () -> Unit, onConfirm: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    var password by remember { mutableStateOf(strongPassword()) }
    var logout by remember { mutableStateOf(true) }
    SecureScreen()
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        // The new password is shown in this dialog's own window: keep that window out of screenshots too.
        secure = true,
        title = { Text(stringResource(R.string.reset_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.reset_password_body, userId), color = MaterialTheme.colorScheme.onSurfaceVariant)
                CtrlTextField(value = password, onValueChange = { password = it }, singleLine = true, textStyle = CtrlType.Data, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CtrlTextButton(onClick = { password = strongPassword() }) { Text(stringResource(R.string.generate)) }
                    CtrlTextButton(onClick = { copySensitive(context, userId, password) }) { Text(stringResource(R.string.copy)) }
                }
                GroupSwitch(stringResource(R.string.logout_devices_too), null, checked = logout) { logout = it }
            }
        },
        confirmButton = { CtrlTextButton(onClick = { onConfirm(password, logout) }, enabled = password.length >= 8) { Text(stringResource(R.string.reset_password)) } },
        dismissButton = { CtrlTextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun TextInputDialog(title: String, initial: String, hint: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                CtrlTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { CtrlTextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { CtrlTextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun RateLimitDialog(current: RateLimit?, onDismiss: () -> Unit, onClear: () -> Unit, onSave: (Long, Long) -> Unit) {
    var mps by remember { mutableStateOf((current?.messagesPerSecond ?: 0).toString()) }
    var burst by remember { mutableStateOf((current?.burstCount ?: 0).toString()) }
    val mpsN = mps.toLongOrNull()
    val burstN = burst.toLongOrNull()
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rate_limit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.rate_limit_body), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                CtrlTextField(
                    value = mps, onValueChange = { mps = it.filter(Char::isDigit) }, singleLine = true,
                    label = { Text(stringResource(R.string.messages_per_second)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                CtrlTextField(
                    value = burst, onValueChange = { burst = it.filter(Char::isDigit) }, singleLine = true,
                    label = { Text(stringResource(R.string.burst_count)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                if (current?.isSet == true) CtrlTextButton(onClick = onClear) { Text(stringResource(R.string.rate_limit_reset)) }
            }
        },
        confirmButton = { CtrlTextButton(onClick = { onSave(mpsN ?: 0, burstN ?: 0) }, enabled = mpsN != null && burstN != null) { Text(stringResource(R.string.save)) } },
        dismissButton = { CtrlTextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
