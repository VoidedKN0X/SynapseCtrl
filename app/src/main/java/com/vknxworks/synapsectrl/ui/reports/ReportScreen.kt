package com.vknxworks.synapsectrl.ui.reports

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonOff
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
import com.vknxworks.synapsectrl.data.admin.EventReport
import com.vknxworks.synapsectrl.data.admin.deleteEventReport
import com.vknxworks.synapsectrl.data.admin.eventReport
import com.vknxworks.synapsectrl.data.api.ApiError
import com.vknxworks.synapsectrl.ui.common.ErrorBlock
import com.vknxworks.synapsectrl.ui.common.Loading
import com.vknxworks.synapsectrl.ui.common.formatDateTime
import com.vknxworks.synapsectrl.ui.common.rememberScreenActions
import com.vknxworks.synapsectrl.ui.components.ConfirmDialog
import com.vknxworks.synapsectrl.ui.components.CtrlCard
import com.vknxworks.synapsectrl.ui.components.CtrlLabel
import com.vknxworks.synapsectrl.ui.components.Group
import com.vknxworks.synapsectrl.ui.components.GroupRow
import com.vknxworks.synapsectrl.ui.components.ScreenTopBar
import com.vknxworks.synapsectrl.ui.nav.LocalNavigator
import com.vknxworks.synapsectrl.ui.nav.RoomRoute
import com.vknxworks.synapsectrl.ui.nav.UserRoute
import com.vknxworks.synapsectrl.ui.theme.CtrlColors
import com.vknxworks.synapsectrl.ui.theme.CtrlType
import com.vknxworks.synapsectrl.ui.theme.Gutter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val Pretty = Json { prettyPrint = true }

/** One event report: who reported what, the reported event itself, and what to do about it. */
@Composable
fun ReportScreen(reportId: Long) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server
    val readOnly = server?.readOnly != false
    val local = { id: String -> id.endsWith(":${server?.serverName}") }
    val actions = rememberScreenActions()

    var report by remember { mutableStateOf<EventReport?>(null) }
    var error by remember { mutableStateOf<ApiError?>(null) }
    var reload by remember { mutableStateOf(0) }
    var deleting by remember { mutableStateOf(false) }

    LaunchedEffect(reportId, reload, server?.id) {
        val client = active?.client ?: return@LaunchedEffect
        try {
            report = client.eventReport(reportId)
            error = null
        } catch (e: ApiError) {
            error = e
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(stringResource(R.string.report_title, reportId))
            val r = report
            val err = error
            when {
                r == null && err != null -> ErrorBlock(err) { reload++ }
                r == null -> Loading()
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                    Group(stringResource(R.string.group_report)) {
                        GroupRow(r.reason?.takeIf { it.isNotBlank() } ?: stringResource(R.string.report_no_reason), formatDateTime(context, r.receivedTs), icon = Icons.Outlined.Flag)
                        r.score?.let { GroupRow(stringResource(R.string.report_score, it), stringResource(R.string.report_score_hint)) }
                        GroupRow(
                            stringResource(R.string.reported_by), r.userId, icon = Icons.Outlined.Person,
                            showChevron = local(r.userId), onClick = if (local(r.userId)) ({ nav.navigate(UserRoute(r.userId)) }) else null,
                        )
                    }

                    val event = r.eventJson
                    val content = event?.get("content") as? JsonObject
                    val body = (content?.get("body") as? JsonPrimitive)?.contentOrNull
                    val type = (event?.get("type") as? JsonPrimitive)?.contentOrNull
                    Group(stringResource(R.string.group_reported_event)) {
                        GroupRow(
                            stringResource(R.string.sent_by), r.sender, icon = Icons.Outlined.Person,
                            showChevron = local(r.sender), onClick = if (local(r.sender)) ({ nav.navigate(UserRoute(r.sender)) }) else null,
                        )
                        GroupRow(
                            stringResource(R.string.in_room), r.name ?: r.canonicalAlias ?: r.roomId, icon = Icons.Outlined.Forum,
                            showChevron = true, onClick = { nav.navigate(RoomRoute(r.roomId)) },
                        )
                    }
                    CtrlCard(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp)) {
                        CtrlLabel(type ?: stringResource(R.string.event_unknown))
                        if (body != null) {
                            Text(body, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 6.dp))
                        } else if (event == null) {
                            Text(stringResource(R.string.event_not_returned), color = CtrlColors.Muted)
                        }
                        if (content != null) {
                            Text(
                                runCatching { Pretty.encodeToString(JsonElement.serializer(), content) }.getOrDefault(content.toString()),
                                style = CtrlType.Data, color = CtrlColors.Muted,
                            )
                        }
                        Text(r.eventId, style = CtrlType.Data, color = CtrlColors.Muted, modifier = Modifier.padding(top = 6.dp))
                    }

                    if (!readOnly) {
                        Group(stringResource(R.string.group_report_actions), footer = stringResource(R.string.report_actions_footer)) {
                            if (local(r.sender)) {
                                GroupRow(stringResource(R.string.manage_sender), stringResource(R.string.manage_sender_subtitle), icon = Icons.Outlined.PersonOff, showChevron = true, onClick = { nav.navigate(UserRoute(r.sender)) })
                            }
                            GroupRow(
                                stringResource(R.string.delete_report), stringResource(R.string.delete_report_subtitle),
                                icon = Icons.Outlined.Delete, iconTint = MaterialTheme.colorScheme.error, titleColor = MaterialTheme.colorScheme.error,
                                onClick = { deleting = true },
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(actions.snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    val client = active?.client ?: return
    if (deleting) {
        ConfirmDialog(
            title = stringResource(R.string.delete_report),
            body = stringResource(R.string.delete_report_body),
            confirmLabel = stringResource(R.string.delete),
            onDismiss = { deleting = false },
            onConfirm = {
                deleting = false
                actions.run(null, after = { nav.back() }) { client.deleteEventReport(reportId) }
            },
        )
    }
}
