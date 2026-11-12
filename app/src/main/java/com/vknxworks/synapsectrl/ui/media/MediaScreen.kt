package com.vknxworks.synapsectrl.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.vknxworks.synapsectrl.data.admin.MediaStats
import com.vknxworks.synapsectrl.data.admin.deleteLocalMedia
import com.vknxworks.synapsectrl.data.admin.mediaStats
import com.vknxworks.synapsectrl.data.admin.purgeRemoteMedia
import com.vknxworks.synapsectrl.data.admin.setMediaQuarantined
import com.vknxworks.synapsectrl.data.admin.userMedia
import com.vknxworks.synapsectrl.data.api.ApiError
import com.vknxworks.synapsectrl.data.api.SynapseClient
import com.vknxworks.synapsectrl.ui.common.ErrorBlock
import com.vknxworks.synapsectrl.ui.common.ListEndSpacer
import com.vknxworks.synapsectrl.ui.common.Loading
import com.vknxworks.synapsectrl.ui.common.StatTile
import com.vknxworks.synapsectrl.ui.common.TypedConfirmDialog
import com.vknxworks.synapsectrl.ui.common.formatBytes
import com.vknxworks.synapsectrl.ui.common.formatDate
import com.vknxworks.synapsectrl.ui.common.rememberScreenActions
import com.vknxworks.synapsectrl.ui.components.ConfirmDialog
import com.vknxworks.synapsectrl.ui.components.ConnectionBanner
import com.vknxworks.synapsectrl.ui.components.CtrlAlertDialog
import com.vknxworks.synapsectrl.ui.components.CtrlTextButton
import com.vknxworks.synapsectrl.ui.components.CtrlTextField
import com.vknxworks.synapsectrl.ui.components.Group
import com.vknxworks.synapsectrl.ui.components.GroupChoice
import com.vknxworks.synapsectrl.ui.components.GroupRow
import com.vknxworks.synapsectrl.ui.components.GroupSwitch
import com.vknxworks.synapsectrl.ui.components.TabTitle
import com.vknxworks.synapsectrl.ui.components.formatCount
import com.vknxworks.synapsectrl.ui.nav.LocalNavigator
import com.vknxworks.synapsectrl.ui.nav.UserMediaRoute
import com.vknxworks.synapsectrl.ui.theme.Gutter

private const val DAY = 86_400_000L

/** What a local media cleanup would delete, counted from each user's media list beforehand. */
data class Estimate(val files: Long, val bytes: Long, val complete: Boolean)

/**
 * Estimates a local media deletion: media last accessed (or, never accessed, uploaded) before
 * [beforeTs] and larger than [sizeGt]. Synapse has no dry run, and it also skips media still used
 * as a profile picture or room avatar when "keep" is on, so this is an estimate, not a promise.
 */
suspend fun estimateLocalDeletion(client: SynapseClient, beforeTs: Long, sizeGt: Long): Estimate {
    var files = 0L
    var bytes = 0L
    var complete = true
    var from: String? = null
    var userPages = 0
    do {
        val stats = client.mediaStats(from = from, limit = 100)
        for (u in stats.users) {
            var mFrom: String? = null
            var pages = 0
            do {
                val page = client.userMedia(u.userId, from = mFrom, limit = 100)
                page.media.forEach { m ->
                    val used = m.lastAccessTs ?: m.createdTs
                    if (used < beforeTs && m.mediaLength > sizeGt) {
                        files++
                        bytes += m.mediaLength
                    }
                }
                mFrom = page.nextToken
                pages++
            } while (mFrom != null && pages < 20)
            if (mFrom != null) complete = false
        }
        from = stats.nextToken
        userPages++
    } while (from != null && userPages < 10)
    if (from != null) complete = false
    return Estimate(files, bytes, complete)
}

private sealed class Pending {
    data object PurgeRemote : Pending()
    data class DeleteLocal(val estimate: Estimate?) : Pending()
    data object QuarantineId : Pending()
}

/** Media storage: who uses how much, cleaning up, and quarantine. */
@Composable
fun MediaScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server
    val readOnly = server?.readOnly != false
    val actions = rememberScreenActions()

    var stats by remember { mutableStateOf<MediaStats?>(null) }
    var error by remember { mutableStateOf<ApiError?>(null) }
    var reload by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pending?>(null) }

    var remoteDays by remember { mutableStateOf(30) }
    var localDays by remember { mutableStateOf(365) }
    var localSize by remember { mutableStateOf(0L) }
    var keepProfiles by remember { mutableStateOf(true) }
    var estimating by remember { mutableStateOf(false) }

    LaunchedEffect(server?.id, reload) {
        val client = active?.client ?: return@LaunchedEffect
        loading = true
        try {
            stats = client.mediaStats(limit = 100)
            error = null
        } catch (e: ApiError) {
            error = e
            server?.id?.let { c.sessions.reportError(it, e) }
        } finally {
            loading = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TabTitle(stringResource(R.string.tab_media))
            ConnectionBanner()
            PullToRefreshBox(isRefreshing = loading && stats != null, onRefresh = { reload++ }, modifier = Modifier.weight(1f)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    val s = stats
                    val err = error
                    when {
                        s == null && err != null -> ErrorBlock(err) { reload++ }
                        s == null -> Loading()
                        else -> {
                            val bytes = s.users.sumOf { it.mediaLength }
                            val count = s.users.sumOf { it.mediaCount }
                            Row(Modifier.padding(horizontal = Gutter, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatTile(stringResource(R.string.stat_media), formatBytes(context, bytes), Modifier.weight(1f), sub = stringResource(R.string.stat_media_sub, formatCount(count)))
                                StatTile(stringResource(R.string.media_users), formatCount(s.total), Modifier.weight(1f), sub = stringResource(R.string.media_users_sub))
                            }
                            if (s.nextToken != null) {
                                Text(stringResource(R.string.media_top_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Gutter))
                            }
                            Group(stringResource(R.string.group_media_by_user)) {
                                s.users.take(30).forEach { u ->
                                    GroupRow(
                                        u.displayname?.takeIf { it.isNotBlank() } ?: u.userId,
                                        stringResource(R.string.media_user_line, u.userId, formatCount(u.mediaCount), formatBytes(context, u.mediaLength)),
                                        icon = Icons.Outlined.Person,
                                        showChevron = true,
                                        onClick = { nav.navigate(UserMediaRoute(u.userId)) },
                                    )
                                }
                                if (s.users.isEmpty()) GroupRow(stringResource(R.string.media_none))
                            }
                        }
                    }

                    if (!readOnly) {
                        Group(stringResource(R.string.group_remote_media), footer = stringResource(R.string.remote_media_footer)) {
                            GroupChoice(
                                stringResource(R.string.not_used_for),
                                listOf(7, 30, 90, 180, 365).map { it to context.resources.getQuantityString(R.plurals.days, it, it) },
                                remoteDays,
                            ) { remoteDays = it }
                            GroupRow(stringResource(R.string.purge_remote), stringResource(R.string.purge_remote_subtitle), icon = Icons.Outlined.CloudOff, onClick = { pending = Pending.PurgeRemote })
                        }
                        Group(stringResource(R.string.group_local_media), footer = stringResource(R.string.local_media_footer)) {
                            GroupChoice(
                                stringResource(R.string.not_used_for),
                                listOf(30, 90, 180, 365, 730).map { it to context.resources.getQuantityString(R.plurals.days, it, it) },
                                localDays,
                            ) { localDays = it }
                            GroupChoice(
                                stringResource(R.string.larger_than),
                                listOf(0L to stringResource(R.string.any_size)) + listOf(1L, 10L, 50L, 100L).map { mb -> mb * 1_000_000L to formatBytes(context, mb * 1_000_000L) },
                                localSize,
                            ) { localSize = it }
                            GroupSwitch(stringResource(R.string.keep_profiles), stringResource(R.string.keep_profiles_subtitle), checked = keepProfiles) { keepProfiles = it }
                            GroupRow(
                                stringResource(if (estimating) R.string.estimating else R.string.delete_local),
                                stringResource(R.string.delete_local_subtitle),
                                icon = Icons.Outlined.DeleteSweep,
                                iconTint = MaterialTheme.colorScheme.error,
                                titleColor = MaterialTheme.colorScheme.error,
                                onClick = {
                                    val client = active?.client ?: return@GroupRow
                                    if (estimating) return@GroupRow
                                    estimating = true
                                    actions.run(null) {
                                        val est = try {
                                            estimateLocalDeletion(client, System.currentTimeMillis() - localDays * DAY, localSize)
                                        } catch (e: ApiError) {
                                            null
                                        } finally {
                                            estimating = false
                                        }
                                        pending = Pending.DeleteLocal(est)
                                    }
                                },
                            )
                        }
                        Group(stringResource(R.string.group_quarantine), footer = stringResource(R.string.quarantine_footer)) {
                            GroupRow(stringResource(R.string.quarantine_by_id), stringResource(R.string.quarantine_by_id_subtitle), icon = Icons.Outlined.GppBad, onClick = { pending = Pending.QuarantineId })
                        }
                    }
                    ListEndSpacer()
                }
            }
        }
        SnackbarHost(actions.snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }

    val client = active?.client ?: return
    val serverName = server?.serverName.orEmpty()
    when (val p = pending) {
        null -> Unit
        Pending.PurgeRemote -> ConfirmDialog(
            title = stringResource(R.string.purge_remote_title),
            body = stringResource(R.string.purge_remote_body, context.resources.getQuantityString(R.plurals.days, remoteDays, remoteDays)),
            confirmLabel = stringResource(R.string.purge),
            onDismiss = { pending = null },
            onConfirm = {
                pending = null
                actions.run(null, after = { reload++ }) {
                    val n = client.purgeRemoteMedia(System.currentTimeMillis() - remoteDays * DAY).deleted
                    actions.say(context.getString(R.string.done_purged, n))
                }
            },
        )
        is Pending.DeleteLocal -> TypedConfirmDialog(
            title = stringResource(R.string.delete_local_title),
            body = listOf(
                stringResource(
                    R.string.delete_local_body,
                    context.resources.getQuantityString(R.plurals.days, localDays, localDays),
                    if (localSize > 0) formatBytes(context, localSize) else stringResource(R.string.any_size),
                ),
                p.estimate?.let {
                    stringResource(if (it.complete) R.string.delete_local_estimate else R.string.delete_local_estimate_partial, formatCount(it.files), formatBytes(context, it.bytes))
                } ?: stringResource(R.string.delete_local_no_estimate),
                stringResource(if (keepProfiles) R.string.delete_local_keeps_profiles else R.string.delete_local_deletes_profiles),
            ).joinToString("\n\n"),
            expected = serverName,
            confirmLabel = stringResource(R.string.delete),
            onDismiss = { pending = null },
            onConfirm = {
                pending = null
                actions.run(null, after = { reload++ }) {
                    val n = client.deleteLocalMedia(System.currentTimeMillis() - localDays * DAY, localSize, keepProfiles).total
                    actions.say(context.getString(R.string.done_media_deleted, n))
                }
            },
        )
        Pending.QuarantineId -> QuarantineByIdDialog(
            defaultServer = serverName,
            onDismiss = { pending = null },
        ) { origin, mediaId, quarantine ->
            pending = null
            actions.run(context.getString(if (quarantine) R.string.done_quarantined_one else R.string.done_unquarantined)) {
                client.setMediaQuarantined(origin, mediaId, quarantine)
            }
        }
    }
}

/** Takes mxc://server/mediaId, or a media ID on [defaultServer]. */
@Composable
private fun QuarantineByIdDialog(defaultServer: String, onDismiss: () -> Unit, onConfirm: (String, String, Boolean) -> Unit) {
    var text by remember { mutableStateOf("") }
    val t = text.trim()
    val parsed = when {
        t.startsWith("mxc://") -> t.removePrefix("mxc://").split('/', limit = 2).takeIf { it.size == 2 && it.all { p -> p.isNotBlank() } }?.let { it[0] to it[1] }
        t.isNotEmpty() && !t.contains('/') -> defaultServer to t
        else -> null
    }
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quarantine_by_id)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.quarantine_by_id_body), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                CtrlTextField(value = text, onValueChange = { text = it }, singleLine = true, placeholder = { Text("mxc://$defaultServer/…") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Row {
                CtrlTextButton(onClick = { parsed?.let { onConfirm(it.first, it.second, false) } }, enabled = parsed != null) { Text(stringResource(R.string.unquarantine)) }
                CtrlTextButton(onClick = { parsed?.let { onConfirm(it.first, it.second, true) } }, enabled = parsed != null) { Text(stringResource(R.string.quarantine), color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { CtrlTextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Date of a media item for its row: uploaded and last used. */
fun mediaDates(context: android.content.Context, created: Long, lastAccess: Long?): String =
    context.getString(R.string.media_dates, formatDate(context, created), lastAccess?.let { formatDate(context, it) } ?: context.getString(R.string.never))
