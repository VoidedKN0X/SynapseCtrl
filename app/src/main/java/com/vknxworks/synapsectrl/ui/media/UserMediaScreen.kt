package com.vknxworks.synapsectrl.ui.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.container
import com.vknxworks.synapsectrl.data.admin.LocalMedia
import com.vknxworks.synapsectrl.data.admin.deleteMedia
import com.vknxworks.synapsectrl.data.admin.setMediaQuarantined
import com.vknxworks.synapsectrl.data.admin.userMedia
import com.vknxworks.synapsectrl.data.api.ApiError
import com.vknxworks.synapsectrl.ui.common.ErrorBlock
import com.vknxworks.synapsectrl.ui.common.FilterRow
import com.vknxworks.synapsectrl.ui.common.Loading
import com.vknxworks.synapsectrl.ui.common.formatBytes
import com.vknxworks.synapsectrl.ui.common.rememberScreenActions
import com.vknxworks.synapsectrl.ui.components.ConfirmDialog
import com.vknxworks.synapsectrl.ui.components.CtrlSheet
import com.vknxworks.synapsectrl.ui.components.CtrlTextButton
import com.vknxworks.synapsectrl.ui.components.EmptyState
import com.vknxworks.synapsectrl.ui.components.Group
import com.vknxworks.synapsectrl.ui.components.GroupRow
import com.vknxworks.synapsectrl.ui.components.ScreenTopBar
import com.vknxworks.synapsectrl.ui.components.copySensitive
import com.vknxworks.synapsectrl.ui.theme.CtrlColors
import com.vknxworks.synapsectrl.ui.theme.CtrlType
import com.vknxworks.synapsectrl.ui.theme.Gutter
import com.vknxworks.synapsectrl.ui.theme.Health
import kotlinx.coroutines.launch

/** One user's uploaded media, newest or biggest first, with quarantine and delete per file. */
@Composable
fun UserMediaScreen(userId: String) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server
    val readOnly = server?.readOnly != false
    val serverName = server?.serverName.orEmpty()
    val actions = rememberScreenActions()

    var bySize by remember { mutableStateOf(false) }
    val media = remember { mutableStateListOf<LocalMedia>() }
    var next by remember { mutableStateOf<String?>(null) }
    var total by remember { mutableStateOf(0L) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ApiError?>(null) }
    var reload by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<LocalMedia?>(null) }
    var deleting by remember { mutableStateOf<LocalMedia?>(null) }

    suspend fun load(from: String?) {
        val client = active?.client ?: return
        loading = true
        try {
            val page = client.userMedia(userId, from = from, limit = 50, bySize = bySize)
            if (from == null) media.clear()
            media += page.media
            next = page.nextToken
            total = page.total
            error = null
        } catch (e: ApiError) {
            error = e
        } finally {
            loading = false
        }
    }

    LaunchedEffect(userId, bySize, reload, server?.id) { load(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(stringResource(R.string.user_media_title, userId))
            FilterRow(listOf(false to stringResource(R.string.sort_newest), true to stringResource(R.string.sort_biggest)), bySize) { bySize = it }
            LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
                val err = error
                when {
                    media.isEmpty() && err != null -> item { ErrorBlock(err) { reload++ } }
                    media.isEmpty() && loading -> item { Loading() }
                    media.isEmpty() -> item { EmptyState(Icons.Outlined.PermMedia, stringResource(R.string.media_none), stringResource(R.string.media_none_body), Modifier.padding(top = 32.dp)) }
                    else -> {
                        item { Text(stringResource(R.string.files_count, total), style = CtrlType.Data, color = CtrlColors.Muted, modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp)) }
                        items(media, key = { it.mediaId }) { m ->
                            GroupRow(
                                m.uploadName?.takeIf { it.isNotBlank() } ?: m.mediaId,
                                listOfNotNull(
                                    "${formatBytes(context, m.mediaLength)} · ${m.mediaType ?: "?"}",
                                    mediaDates(context, m.createdTs, m.lastAccessTs),
                                    m.quarantinedBy?.let { stringResource(R.string.quarantined_by, it) },
                                ).joinToString("\n"),
                                icon = mediaIcon(m.mediaType),
                                iconTint = if (m.quarantinedBy != null) Health.critical else CtrlColors.Muted,
                                onClick = { selected = m },
                            )
                        }
                        if (next != null) {
                            item {
                                CtrlTextButton(onClick = { scope.launch { load(next) } }, enabled = !loading, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                    Text(stringResource(R.string.load_more))
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(actions.snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    val client = active?.client ?: return
    selected?.let { m ->
        CtrlSheet(onDismissRequest = { selected = null }) {
            Text(m.uploadName ?: m.mediaId, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Text("mxc://$serverName/${m.mediaId}", style = CtrlType.Data, color = CtrlColors.Muted, modifier = Modifier.padding(horizontal = 24.dp))
            Group {
                GroupRow(stringResource(R.string.copy_mxc), onClick = {
                    copySensitive(context, m.mediaId, "mxc://$serverName/${m.mediaId}")
                    selected = null
                    actions.say(context.getString(R.string.copied))
                })
                if (!readOnly) {
                    val quarantined = m.quarantinedBy != null
                    GroupRow(
                        stringResource(if (quarantined) R.string.unquarantine else R.string.quarantine),
                        stringResource(if (quarantined) R.string.unquarantine_subtitle else R.string.quarantine_one_subtitle),
                        icon = Icons.Outlined.GppBad,
                        onClick = {
                            selected = null
                            actions.run(context.getString(if (quarantined) R.string.done_unquarantined else R.string.done_quarantined_one), after = { reload++ }) {
                                client.setMediaQuarantined(serverName, m.mediaId, !quarantined)
                            }
                        },
                    )
                    GroupRow(
                        stringResource(R.string.delete_file), stringResource(R.string.delete_file_subtitle),
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { selected = null; deleting = m },
                    )
                }
            }
            Box(Modifier.navigationBarsPadding().padding(bottom = 16.dp))
        }
    }
    deleting?.let { m ->
        ConfirmDialog(
            title = stringResource(R.string.delete_file_title, m.uploadName ?: m.mediaId),
            body = stringResource(R.string.delete_file_body, formatBytes(context, m.mediaLength)),
            confirmLabel = stringResource(R.string.delete),
            onDismiss = { deleting = null },
            onConfirm = {
                deleting = null
                actions.run(context.getString(R.string.done_deleted), after = { reload++ }) { client.deleteMedia(serverName, m.mediaId) }
            },
        )
    }
}

private fun mediaIcon(type: String?) = when {
    type == null -> Icons.Outlined.Description
    type.startsWith("image/") -> Icons.Outlined.Image
    type.startsWith("video/") -> Icons.Outlined.Movie
    type.startsWith("audio/") -> Icons.Outlined.AudioFile
    else -> Icons.Outlined.Description
}
