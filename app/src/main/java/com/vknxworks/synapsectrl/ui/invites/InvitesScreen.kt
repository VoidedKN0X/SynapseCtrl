package com.vknxworks.synapsectrl.ui.invites

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.VpnKeyOff
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.container
import com.vknxworks.synapsectrl.data.admin.RegistrationToken
import com.vknxworks.synapsectrl.data.admin.createRegistrationToken
import com.vknxworks.synapsectrl.data.admin.deleteRegistrationToken
import com.vknxworks.synapsectrl.data.admin.registrationTokens
import com.vknxworks.synapsectrl.data.admin.updateRegistrationToken
import com.vknxworks.synapsectrl.data.api.ApiError
import com.vknxworks.synapsectrl.data.health.HealthId
import com.vknxworks.synapsectrl.ui.common.ErrorBlock
import com.vknxworks.synapsectrl.ui.common.Loading
import com.vknxworks.synapsectrl.ui.common.formatDate
import com.vknxworks.synapsectrl.ui.common.formatDateTime
import com.vknxworks.synapsectrl.ui.common.rememberScreenActions
import com.vknxworks.synapsectrl.ui.components.CtrlAlertDialog
import com.vknxworks.synapsectrl.ui.components.CtrlSquareButton
import com.vknxworks.synapsectrl.ui.components.CtrlTextButton
import com.vknxworks.synapsectrl.ui.components.CtrlTextField
import com.vknxworks.synapsectrl.ui.components.ConfirmDialog
import com.vknxworks.synapsectrl.ui.components.Group
import com.vknxworks.synapsectrl.ui.components.GroupChoice
import com.vknxworks.synapsectrl.ui.components.GroupRow
import com.vknxworks.synapsectrl.ui.components.PillButton
import com.vknxworks.synapsectrl.ui.components.ScreenTopBar
import com.vknxworks.synapsectrl.ui.lock.AppLock
import com.vknxworks.synapsectrl.ui.theme.CtrlColors
import com.vknxworks.synapsectrl.ui.theme.Gutter
import com.vknxworks.synapsectrl.ui.theme.Health
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** How long a new or edited token stays valid. */
enum class Expiry(val days: Int?) { DAY(1), WEEK(7), MONTH(30), NEVER(null) }

private sealed class Pending {
    data object Create : Pending()
    data class Edit(val token: RegistrationToken) : Pending()
    data class Delete(val token: RegistrationToken) : Pending()
}

/**
 * Registration tokens: people can only sign up with one when registration_requires_token is on in
 * homeserver.yaml. Create, change, delete and share them as a ready-made invite.
 */
@Composable
fun InvitesScreen(createOnOpen: Boolean = false) {
    val context = LocalContext.current
    val c = context.container
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val states by c.overview.states.collectAsStateWithLifecycle()
    val server = active?.server
    val readOnly = server?.readOnly != false
    val actions = rememberScreenActions()

    var tokens by remember { mutableStateOf<List<RegistrationToken>?>(null) }
    var error by remember { mutableStateOf<ApiError?>(null) }
    var reload by remember { mutableStateOf(0) }
    var pending by remember { mutableStateOf<Pending?>(if (createOnOpen) Pending.Create else null) }

    LaunchedEffect(server?.id, reload) {
        val client = active?.client ?: return@LaunchedEffect
        try {
            tokens = client.registrationTokens()
            error = null
        } catch (e: ApiError) {
            error = e
        }
    }

    // Where people sign up: the server name (vkhub.org), and the homeserver address from client
    // discovery (chat.vkhub.org) when the Overview found one.
    val homeserverHost = server?.id?.let { states[it] }?.summary?.health
        ?.firstOrNull { it.id == HealthId.WELL_KNOWN_CLIENT }?.detail?.toHttpUrlOrNull()?.host
    fun share(t: RegistrationToken) {
        val s = server ?: return
        val text = inviteMessage(context, s.serverName.ifBlank { s.host }, homeserverHost, t)
        AppLock.expectReturn()
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                context.getString(R.string.share_invite),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(stringResource(R.string.invites_title)) {
                if (!readOnly) CtrlSquareButton(Icons.Outlined.Add, stringResource(R.string.new_token), onClick = { pending = Pending.Create })
            }
            val list = tokens
            val err = error
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                when {
                    list == null && err != null -> ErrorBlock(err) { reload++ }
                    list == null -> Loading()
                    else -> {
                        val now = System.currentTimeMillis()
                        val (valid, used) = list.partition { it.isValid(now) }
                        if (!readOnly) {
                            PillButton(
                                stringResource(R.string.invite_someone),
                                onClick = { pending = Pending.Create },
                                icon = Icons.Outlined.Share,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
                            )
                        }
                        Group(stringResource(R.string.group_valid_tokens), footer = stringResource(R.string.tokens_footer)) {
                            if (valid.isEmpty()) GroupRow(stringResource(R.string.no_tokens), icon = Icons.Outlined.VpnKeyOff)
                            valid.forEach { t -> TokenRow(t, readOnly, onShare = { share(t) }, onEdit = { pending = Pending.Edit(t) }) }
                        }
                        if (used.isNotEmpty()) {
                            Group(stringResource(R.string.group_used_tokens)) {
                                used.forEach { t -> TokenRow(t, readOnly, onShare = null, onEdit = { pending = Pending.Edit(t) }) }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(actions.snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    val client = active?.client ?: return
    when (val p = pending) {
        null -> Unit
        Pending.Create -> TokenDialog(
            existing = null,
            onDismiss = { pending = null },
            onDelete = null,
        ) { token, uses, expiry ->
            pending = null
            actions.run(null, after = { reload++ }) {
                val t = client.createRegistrationToken(token, uses, expiry)
                share(t)
            }
        }
        is Pending.Edit -> TokenDialog(
            existing = p.token,
            onDismiss = { pending = null },
            onDelete = { pending = Pending.Delete(p.token) },
        ) { _, uses, expiry ->
            pending = null
            actions.run(context.getString(R.string.done_saved), after = { reload++ }) { client.updateRegistrationToken(p.token.token, uses, expiry) }
        }
        is Pending.Delete -> ConfirmDialog(
            title = stringResource(R.string.delete_token_title, p.token.token),
            body = stringResource(R.string.delete_token_body),
            confirmLabel = stringResource(R.string.delete),
            onDismiss = { pending = null },
            onConfirm = {
                pending = null
                actions.run(context.getString(R.string.done_deleted), after = { reload++ }) { client.deleteRegistrationToken(p.token.token) }
            },
        )
    }
}

@Composable
private fun TokenRow(t: RegistrationToken, readOnly: Boolean, onShare: (() -> Unit)?, onEdit: () -> Unit) {
    val context = LocalContext.current
    val uses = when (val left = t.remaining) {
        null -> stringResource(R.string.token_unlimited, t.completed)
        else -> stringResource(R.string.token_uses, t.completed, t.usesAllowed ?: 0, left)
    }
    val expiry = t.expiryTime?.let {
        if (it > System.currentTimeMillis()) stringResource(R.string.token_expires, formatDateTime(context, it))
        else stringResource(R.string.token_expired, formatDate(context, it))
    } ?: stringResource(R.string.token_no_expiry)
    GroupRow(
        t.token,
        "$uses\n$expiry" + if (t.pending > 0) "\n" + stringResource(R.string.token_pending, t.pending) else "",
        icon = Icons.Outlined.VpnKey,
        iconTint = if (onShare != null) Health.ok else CtrlColors.Muted,
        onClick = if (!readOnly) onEdit else null,
        trailing = if (onShare != null) {
            { CtrlSquareButton(Icons.Outlined.Share, stringResource(R.string.share_invite), onClick = onShare) }
        } else {
            null
        },
    )
}

@Composable
private fun TokenDialog(
    existing: RegistrationToken?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (token: String?, uses: Int?, expiry: Long?) -> Unit,
) {
    var token by remember { mutableStateOf("") }
    var uses by remember { mutableStateOf(existing?.usesAllowed?.toString() ?: if (existing == null) "1" else "") }
    var expiry by remember { mutableStateOf(if (existing == null) Expiry.WEEK else if (existing.expiryTime == null) Expiry.NEVER else Expiry.WEEK) }
    var keepExpiry by remember { mutableStateOf(existing?.expiryTime != null) }
    val tokenOk = token.isEmpty() || (token.length <= 64 && token.all { it.isLetterOrDigit() && it.code < 128 || it in "._~-" })
    val usesN = uses.toIntOrNull()
    val usesOk = uses.isEmpty() || (usesN != null && usesN > 0)
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) stringResource(R.string.new_token) else existing.token) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (existing == null) {
                    CtrlTextField(
                        value = token, onValueChange = { token = it.trim() }, singleLine = true,
                        label = { Text(stringResource(R.string.token_custom)) },
                        supportingText = { Text(stringResource(if (tokenOk) R.string.token_custom_hint else R.string.token_custom_invalid)) },
                        isError = !tokenOk,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                CtrlTextField(
                    value = uses, onValueChange = { uses = it.filter(Char::isDigit) }, singleLine = true,
                    label = { Text(stringResource(R.string.token_uses_allowed)) },
                    supportingText = { Text(stringResource(R.string.token_uses_hint)) },
                    isError = !usesOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Column {
                    if (existing?.expiryTime != null && keepExpiry) {
                        GroupRow(
                            stringResource(R.string.token_valid_until),
                            formatDateTime(LocalContext.current, existing.expiryTime),
                            onClick = { keepExpiry = false },
                            showChevron = true,
                        )
                    } else {
                        GroupChoice(
                            stringResource(R.string.token_valid_for),
                            listOf(
                                Expiry.DAY to stringResource(R.string.expiry_day),
                                Expiry.WEEK to stringResource(R.string.expiry_week),
                                Expiry.MONTH to stringResource(R.string.expiry_month),
                                Expiry.NEVER to stringResource(R.string.expiry_never),
                            ),
                            expiry,
                        ) { expiry = it }
                    }
                }
                if (onDelete != null) CtrlTextButton(onClick = onDelete) { Text(stringResource(R.string.delete_token), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            CtrlTextButton(
                onClick = {
                    val expiryMs = if (existing?.expiryTime != null && keepExpiry) existing.expiryTime
                    else expiry.days?.let { System.currentTimeMillis() + it * 86_400_000L }
                    onSave(token.ifBlank { null }, usesN, expiryMs)
                },
                enabled = tokenOk && usesOk,
            ) { Text(stringResource(if (existing == null) R.string.create_and_share else R.string.save)) }
        },
        dismissButton = { CtrlTextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The ready-made invite for the share sheet. */
private fun inviteMessage(context: Context, serverName: String, homeserverHost: String?, t: RegistrationToken): String {
    val server = if (homeserverHost != null && homeserverHost != serverName) "$serverName ($homeserverHost)" else serverName
    val limits = buildList {
        t.expiryTime?.let { add(context.getString(R.string.invite_valid_until, formatDateTime(context, it))) }
        t.remaining?.let { add(context.getResources().getQuantityString(R.plurals.invite_uses_left, it, it)) }
    }.joinToString(" ")
    return context.getString(R.string.invite_message, server, t.token, serverName) + if (limits.isNotEmpty()) "\n\n$limits" else ""
}
