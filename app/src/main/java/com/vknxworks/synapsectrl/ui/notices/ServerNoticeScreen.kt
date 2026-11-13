package com.vknxworks.synapsectrl.ui.notices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.vknxworks.synapsectrl.data.admin.sendServerNotice
import com.vknxworks.synapsectrl.data.admin.users
import com.vknxworks.synapsectrl.data.api.ApiError
import com.vknxworks.synapsectrl.ui.common.FilterRow
import com.vknxworks.synapsectrl.ui.common.fullUserId
import com.vknxworks.synapsectrl.ui.common.rememberScreenActions
import com.vknxworks.synapsectrl.ui.components.ConfirmDialog
import com.vknxworks.synapsectrl.ui.components.CtrlCard
import com.vknxworks.synapsectrl.ui.components.CtrlLabel
import com.vknxworks.synapsectrl.ui.components.CtrlTextField
import com.vknxworks.synapsectrl.ui.components.PillButton
import com.vknxworks.synapsectrl.ui.components.ScreenTopBar
import com.vknxworks.synapsectrl.ui.components.errorMessage
import com.vknxworks.synapsectrl.ui.theme.CtrlColors
import com.vknxworks.synapsectrl.ui.theme.Gutter

/**
 * Sends a server notice (POST /_synapse/admin/v1/send_server_notice) to one user, or to every active
 * local user one by one. Synapse only accepts it when server_notices is set up in homeserver.yaml;
 * otherwise its error is shown.
 */
@Composable
fun ServerNoticeScreen(initialUser: String) {
    val context = LocalContext.current
    val c = context.container
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server
    val serverName = server?.serverName.orEmpty()
    val actions = rememberScreenActions()

    var everyone by remember { mutableStateOf(false) }
    var user by remember { mutableStateOf(initialUser) }
    var text by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<String?>(null) }

    val target = if (everyone) null else user.trim().takeIf { it.isNotEmpty() }?.let { fullUserId(it, serverName) }
    val canSend = text.isNotBlank() && (everyone || target != null) && !actions.busy && server?.readOnly == false

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(stringResource(R.string.notice_title))
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.notice_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Gutter))
                FilterRow(listOf(false to stringResource(R.string.notice_one_user), true to stringResource(R.string.notice_everyone)), everyone) { everyone = it }
                Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!everyone) {
                        CtrlTextField(
                            value = user, onValueChange = { user = it.trim() }, singleLine = true,
                            label = { Text(stringResource(R.string.notice_to)) },
                            supportingText = { Text(target?.let { stringResource(R.string.notice_goes_to, it) } ?: stringResource(R.string.field_username_hint)) },
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    CtrlTextField(
                        value = text, onValueChange = { text = it },
                        label = { Text(stringResource(R.string.notice_message)) },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (text.isNotBlank()) {
                        CtrlCard(Modifier.fillMaxWidth()) {
                            CtrlLabel(stringResource(R.string.notice_preview))
                            Text(stringResource(R.string.notice_preview_from), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, modifier = Modifier.padding(vertical = 4.dp))
                            Text(text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    progress?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted) }
                    PillButton(stringResource(R.string.notice_send), onClick = { confirm = true }, enabled = canSend, icon = Icons.Outlined.Campaign, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.notice_footer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        SnackbarHost(actions.snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    val client = active?.client ?: return
    if (confirm) {
        ConfirmDialog(
            title = stringResource(if (everyone) R.string.notice_confirm_everyone else R.string.notice_confirm_one, target ?: ""),
            body = text,
            confirmLabel = stringResource(R.string.notice_send),
            danger = false,
            onDismiss = { confirm = false },
            onConfirm = {
                confirm = false
                val message = text.trim()
                actions.run(null) {
                    if (target != null) {
                        client.sendServerNotice(target, message)
                        actions.say(context.getString(R.string.notice_sent_one, target))
                    } else {
                        // Every active local user, one notice each.
                        val all = mutableListOf<String>()
                        var from: String? = null
                        do {
                            val page = client.users(from = from, limit = 100, deactivated = false, guests = false)
                            all += page.users.filter { it.userType != "bot" && it.appserviceId == null }.map { it.name }
                            from = page.nextToken
                        } while (from != null)
                        var sent = 0
                        var firstError: ApiError? = null
                        all.forEachIndexed { i, id ->
                            progress = context.getString(R.string.notice_progress, i + 1, all.size)
                            try {
                                client.sendServerNotice(id, message)
                                sent++
                            } catch (e: ApiError) {
                                if (firstError == null) firstError = e
                                // Server notices not enabled: every other one would fail the same way.
                                if (sent == 0 && i == 0) throw e
                            }
                        }
                        progress = null
                        val err = firstError
                        actions.say(
                            if (err == null) context.getString(R.string.notice_sent_all, sent)
                            else context.getString(R.string.notice_sent_some, sent, all.size, errorMessage(context, err, server?.host.orEmpty())),
                        )
                    }
                    text = ""
                }
            },
        )
    }
}
