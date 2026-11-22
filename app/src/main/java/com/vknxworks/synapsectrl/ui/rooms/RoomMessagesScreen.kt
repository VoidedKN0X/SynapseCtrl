package com.vknxworks.synapsectrl.ui.rooms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.container
import com.vknxworks.synapsectrl.data.admin.AdminRoom
import com.vknxworks.synapsectrl.data.admin.PurgeStatus
import com.vknxworks.synapsectrl.data.admin.purgeStatus
import com.vknxworks.synapsectrl.data.admin.roomMessages
import com.vknxworks.synapsectrl.data.api.ApiError
import com.vknxworks.synapsectrl.ui.common.ErrorBlock
import com.vknxworks.synapsectrl.ui.common.Loading
import com.vknxworks.synapsectrl.ui.common.TypedConfirmDialog
import com.vknxworks.synapsectrl.ui.common.formatDateTime
import com.vknxworks.synapsectrl.ui.components.CtrlAlertDialog
import com.vknxworks.synapsectrl.ui.components.CtrlTextButton
import com.vknxworks.synapsectrl.ui.components.CtrlTextField
import com.vknxworks.synapsectrl.ui.components.EmptyState
import com.vknxworks.synapsectrl.ui.components.GroupSwitch
import com.vknxworks.synapsectrl.ui.components.ScreenTopBar
import com.vknxworks.synapsectrl.ui.components.copySensitive
import com.vknxworks.synapsectrl.ui.components.topDivider
import com.vknxworks.synapsectrl.ui.theme.CtrlColors
import com.vknxworks.synapsectrl.ui.theme.CtrlType
import com.vknxworks.synapsectrl.ui.theme.Gutter
import com.vknxworks.synapsectrl.ui.theme.Health
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import com.vknxworks.synapsectrl.data.admin.StatusPoll
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle

private val Pretty = Json { prettyPrint = true }

/** What the list shows of one room event, read leniently from its JSON. */
class RoomEvent(val json: JsonObject) {
    private fun str(key: String): String? = (json[key] as? JsonPrimitive)?.contentOrNull
    val eventId: String get() = str("event_id").orEmpty()
    val type: String get() = str("type").orEmpty()
    val sender: String get() = str("sender").orEmpty()
    val ts: Long get() = (json["origin_server_ts"] as? JsonPrimitive)?.longOrNull ?: 0
    val stateKey: String? get() = str("state_key")
    val content: JsonObject? get() = json["content"] as? JsonObject

    /** The message text, when this is a readable message. */
    val body: String? get() = (content?.get("body") as? JsonPrimitive)?.contentOrNull

    val isEncrypted: Boolean get() = type == "m.room.encrypted"

    /** Redacted events keep their type but lose their content. */
    val isRedacted: Boolean get() = (json["unsigned"] as? JsonObject)?.containsKey("redacted_because") == true
}

/**
 * The events of a room, newest first, straight from the server's database
 * (GET /_synapse/admin/v1/rooms/<room_id>/messages). Encrypted rooms only show that a message was
 * sent, by whom and when: the server can't read them either. Reads only.
 */
@Composable
fun RoomMessagesScreen(roomId: String) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server

    val events = remember { mutableStateListOf<RoomEvent>() }
    var next by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ApiError?>(null) }
    var reload by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf<RoomEvent?>(null) }

    suspend fun load(from: String?) {
        val client = active?.client ?: return
        if (loading) return
        loading = true
        try {
            val page = client.roomMessages(roomId, from = from, limit = 30)
            if (from == null) events.clear()
            val known = events.mapTo(HashSet()) { it.eventId }
            events += page.chunk.map { RoomEvent(it) }.filter { it.eventId.isNotEmpty() && known.add(it.eventId) }
            // No "end", or the same token again: nothing older.
            next = page.end?.takeIf { it.isNotBlank() && it != from }
            loaded = true
            error = null
        } catch (e: ApiError) {
            error = e
        } finally {
            loading = false
        }
    }

    LaunchedEffect(roomId, reload, server?.id) { load(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.room_messages_title))
        val err = error
        when {
            !loaded && err != null -> ErrorBlock(err) { reload++ }
            !loaded -> Loading()
            events.isEmpty() && next == null -> EmptyState(Icons.Outlined.Forum, stringResource(R.string.room_messages_empty), stringResource(R.string.room_messages_empty_body))
            else -> LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
                item(key = "note") {
                    Text(
                        stringResource(R.string.room_messages_note),
                        style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted,
                        modifier = Modifier.padding(horizontal = Gutter, vertical = 6.dp),
                    )
                }
                items(events, key = { it.eventId }, contentType = { "event" }) { e -> EventRow(e) { open = e } }
                if (err != null) item(key = "error") { ErrorBlock(err) { scope.launch { load(next) } } }
                if (next != null) {
                    item(key = "more") {
                        CtrlTextButton(onClick = { scope.launch { load(next) } }, enabled = !loading, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            Text(stringResource(R.string.room_messages_older))
                        }
                    }
                }
            }
        }
    }

    open?.let { e ->
        val text = runCatching { Pretty.encodeToString(JsonElement.serializer(), e.json) }.getOrDefault(e.json.toString())
        CtrlAlertDialog(
            onDismissRequest = { open = null },
            title = { Text(e.type.ifBlank { stringResource(R.string.event_unknown) }) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text, style = CtrlType.Data, color = CtrlColors.Muted)
                }
            },
            confirmButton = { CtrlTextButton(onClick = { open = null }) { Text(stringResource(R.string.lock_done)) } },
            dismissButton = { CtrlTextButton(onClick = { copySensitive(context, e.eventId, text) }) { Text(stringResource(R.string.copy)) } },
        )
    }
}

@Composable
private fun EventRow(e: RoomEvent, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().topDivider().clickable(onClick = onClick).padding(horizontal = Gutter, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.sender, style = CtrlType.Data, color = CtrlColors.AccentText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            if (e.ts > 0) Text(formatDateTime(context, e.ts), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
        }
        val body = e.body
        when {
            e.isRedacted -> Text(stringResource(R.string.room_messages_redacted), color = CtrlColors.Muted, style = MaterialTheme.typography.bodyMedium)
            e.isEncrypted -> Text(stringResource(R.string.room_messages_encrypted), color = CtrlColors.Muted, style = MaterialTheme.typography.bodyMedium)
            e.type == "m.room.message" && body != null -> Text(body, style = MaterialTheme.typography.bodyLarge, maxLines = 8, overflow = TextOverflow.Ellipsis)
            else -> Text(
                listOfNotNull(e.type.ifBlank { stringResource(R.string.event_unknown) }, e.stateKey?.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = CtrlType.Data, color = CtrlColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------- Purge history ----------

/**
 * "Purge old history": removes events older than a number of days from the server's database. Needs
 * the room ID typed. [onConfirm] gets the days and whether messages of this server's own users go too.
 */
@Composable
fun PurgeHistoryDialog(room: AdminRoom, onDismiss: () -> Unit, onConfirm: (days: Int, deleteLocal: Boolean) -> Unit) {
    var days by remember { mutableStateOf("90") }
    var local by remember { mutableStateOf(false) }
    val n = days.toIntOrNull()?.takeIf { it in 1..36_500 }
    TypedConfirmDialog(
        title = stringResource(R.string.purge_title, room.label),
        body = stringResource(R.string.purge_body),
        expected = room.roomId,
        confirmLabel = stringResource(R.string.purge_confirm),
        onDismiss = onDismiss,
        onConfirm = { if (n != null) onConfirm(n, local) },
        extra = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CtrlTextField(
                    value = days, onValueChange = { days = it.filter(Char::isDigit).take(5) }, singleLine = true,
                    label = { Text(stringResource(R.string.purge_days)) },
                    isError = n == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                GroupSwitch(stringResource(R.string.purge_local), stringResource(R.string.purge_local_sub), checked = local) { local = it }
            }
        },
    )
}

/** Follows the background purge with GET /_synapse/admin/v1/purge_history_status/<purge_id>. */
@Composable
fun PurgeProgressDialog(purgeId: String, onClose: () -> Unit) {
    val c = LocalContext.current.container
    var status by remember { mutableStateOf<PurgeStatus?>(null) }
    var failedToCheck by remember { mutableStateOf(false) }
    var gone by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(purgeId) {
        val poll = StatusPoll()
        while (true) {
            // Only while SynapseCtrl is on screen: nothing is asked in the background.
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
            val client = c.sessions.active.value?.client ?: break
            val next = try {
                val s = client.purgeStatus(purgeId)
                status = s
                failedToCheck = false
                poll.onStatus(s.finished)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failedToCheck = true
                poll.onFailure(notFound = e is ApiError.NotFound)
            }
            if (next == StatusPoll.Next.DONE) break
            if (next == StatusPoll.Next.GONE) {
                gone = true
                break
            }
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
                        "complete" -> R.string.purge_status_complete
                        "failed" -> R.string.purge_status_failed
                        else -> if (gone) R.string.purge_status_unknown else R.string.purge_status_running
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (gone) {
                    Text(stringResource(R.string.purge_status_gone), color = Health.warn)
                } else if (s?.finished != true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.purge_status_active))
                    }
                }
                if (failedToCheck && !gone) Text(stringResource(R.string.delete_status_check_failed), color = Health.warn, style = MaterialTheme.typography.bodySmall)
                s?.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (s?.status == "complete") Text(stringResource(R.string.purge_status_done_hint), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
                Text(stringResource(R.string.purge_status_id, purgeId), style = CtrlType.Data, color = CtrlColors.Muted)
            }
        },
        confirmButton = {
            CtrlTextButton(onClick = onClose) { Text(stringResource(if (s?.finished == true || gone) R.string.lock_done else R.string.close_keep_running)) }
        },
    )
}
