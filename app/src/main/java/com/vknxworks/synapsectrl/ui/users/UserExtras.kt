package com.vknxworks.synapsectrl.ui.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.container
import com.vknxworks.synapsectrl.data.admin.AdminUser
import com.vknxworks.synapsectrl.data.admin.RedactStatus
import com.vknxworks.synapsectrl.data.admin.Threepid
import com.vknxworks.synapsectrl.data.admin.redactStatus
import com.vknxworks.synapsectrl.ui.common.TypedConfirmDialog
import com.vknxworks.synapsectrl.ui.components.CtrlAlertDialog
import com.vknxworks.synapsectrl.ui.components.CtrlFilterChip
import com.vknxworks.synapsectrl.ui.components.CtrlTextButton
import com.vknxworks.synapsectrl.ui.components.CtrlTextField
import com.vknxworks.synapsectrl.ui.theme.CtrlColors
import com.vknxworks.synapsectrl.ui.theme.CtrlType
import com.vknxworks.synapsectrl.ui.theme.Health
import kotlinx.coroutines.delay

/** What Synapse accepts as a third-party ID, checked before anything is sent. */
object ThreepidInput {
    private val EMAIL = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")

    /** An e-mail address in lower case, or null when it doesn't look like one. */
    fun email(typed: String): String? = typed.trim().lowercase().takeIf { EMAIL.matches(it) }

    /** A phone number as digits only with the country code and no "+" or leading zeros (447470274584), or null. */
    fun phone(typed: String): String? {
        val digits = typed.trim().removePrefix("+").filter { it.isDigit() }.trimStart('0')
        return digits.takeIf { it.length in 7..15 && typed.trim().all { ch -> ch.isDigit() || ch in "+ -()." } }
    }
}

/**
 * Edits an account's e-mail addresses and phone numbers. Saving replaces the whole list on the server
 * (PUT /_synapse/admin/v2/users/<user_id> with "threepids"), so the dialog shows exactly what will be
 * there afterwards and what is removed.
 */
@Composable
fun ThreepidsDialog(user: AdminUser, onDismiss: () -> Unit, onSave: (List<Threepid>) -> Unit) {
    val list = remember { mutableStateListOf<Threepid>().apply { addAll(user.threepids) } }
    var phone by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val parsed = if (phone) ThreepidInput.phone(typed) else ThreepidInput.email(typed)
    val medium = if (phone) "msisdn" else "email"
    val duplicate = parsed != null && list.any { it.medium == medium && it.address == parsed }
    val before = user.threepids.map { it.medium to it.address }.toSet()
    val after = list.map { it.medium to it.address }.toSet()
    val removed = (before - after).size
    val added = (after - before).size
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        guard = true,
        title = { Text(stringResource(R.string.threepids_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.threepids_body, user.name), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (list.isEmpty()) Text(stringResource(R.string.threepids_none), color = CtrlColors.Muted)
                list.forEach { t ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.address, style = CtrlType.Data, color = CtrlColors.Text)
                            Text(
                                when (t.medium) {
                                    "email" -> stringResource(R.string.threepid_email)
                                    "msisdn" -> stringResource(R.string.threepid_phone)
                                    else -> t.medium
                                },
                                style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted,
                            )
                        }
                        CtrlTextButton(onClick = { list.remove(t) }) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CtrlFilterChip(!phone, { phone = false; typed = "" }, { Text(stringResource(R.string.threepid_email)) })
                    CtrlFilterChip(phone, { phone = true; typed = "" }, { Text(stringResource(R.string.threepid_phone)) })
                }
                CtrlTextField(
                    value = typed, onValueChange = { typed = it }, singleLine = true,
                    label = { Text(stringResource(if (phone) R.string.threepid_phone_hint else R.string.threepid_email_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = if (phone) KeyboardType.Phone else KeyboardType.Email, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                CtrlTextButton(
                    onClick = {
                        parsed?.let { list.add(Threepid(medium, it)) }
                        typed = ""
                    },
                    enabled = parsed != null && !duplicate,
                ) { Text(stringResource(R.string.threepid_add)) }
                if (removed > 0 || added > 0) {
                    Text(
                        stringResource(R.string.threepids_changes, added, removed),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (removed > 0) Health.warn else CtrlColors.Muted,
                    )
                }
            }
        },
        confirmButton = {
            CtrlTextButton(onClick = { onSave(list.toList()) }, enabled = removed > 0 || added > 0) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { CtrlTextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * "Redact all messages": says what happens and needs the user ID typed. [onConfirm] gets the reason
 * (shown to everyone in the redaction) and how many events per room to go through.
 */
@Composable
fun RedactUserDialog(user: AdminUser, rooms: Int?, onDismiss: () -> Unit, onConfirm: (reason: String?, limit: Int) -> Unit) {
    var reason by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf("1000") }
    val n = limit.toIntOrNull()?.takeIf { it in 1..100_000 }
    TypedConfirmDialog(
        title = stringResource(R.string.redact_user_title, user.label),
        body = if (rooms != null) pluralStringResource(R.plurals.redact_user_body, rooms, rooms) else stringResource(R.string.redact_user_body_unknown),
        expected = user.name,
        confirmLabel = stringResource(R.string.redact_user_confirm),
        onDismiss = onDismiss,
        onConfirm = { if (n != null) onConfirm(reason.trim().ifBlank { null }, n) },
        extra = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CtrlTextField(
                    value = reason, onValueChange = { reason = it }, singleLine = true,
                    label = { Text(stringResource(R.string.redact_user_reason)) }, modifier = Modifier.fillMaxWidth(),
                )
                CtrlTextField(
                    value = limit, onValueChange = { limit = it.filter(Char::isDigit).take(6) }, singleLine = true,
                    label = { Text(stringResource(R.string.redact_user_limit)) },
                    isError = n == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

/** Follows the background redaction with GET /_synapse/admin/v1/user/redact_status/<redact_id>. */
@Composable
fun RedactProgressDialog(redactId: String, onClose: () -> Unit) {
    val c = LocalContext.current.container
    var status by remember { mutableStateOf<RedactStatus?>(null) }
    var failedToCheck by remember { mutableStateOf(false) }
    LaunchedEffect(redactId) {
        while (status?.finished != true) {
            val client = c.sessions.active.value?.client ?: break
            val s = runCatching { client.redactStatus(redactId) }.getOrNull()
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
                        "complete" -> R.string.redact_status_complete
                        "failed" -> R.string.redact_status_failed
                        else -> R.string.redact_status_running
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
                        Text(stringResource(if (s?.status == "active") R.string.redact_status_active else R.string.redact_status_scheduled))
                    }
                }
                if (failedToCheck) Text(stringResource(R.string.delete_status_check_failed), color = Health.warn, style = MaterialTheme.typography.bodySmall)
                s?.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (s?.status == "complete") {
                    val failed = s.failedRedactions.size
                    Text(
                        if (failed == 0) stringResource(R.string.redact_status_all_done)
                        else pluralStringResource(R.plurals.redact_status_some_failed, failed, failed, s.failedRedactions.values.firstOrNull().orEmpty().take(200)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (failed == 0) CtrlColors.Muted else Health.warn,
                    )
                }
                Text(stringResource(R.string.redact_status_id, redactId), style = CtrlType.Data, color = CtrlColors.Muted)
            }
        },
        confirmButton = {
            CtrlTextButton(onClick = onClose) { Text(stringResource(if (s?.finished == true) R.string.lock_done else R.string.close_keep_running)) }
        },
    )
}
