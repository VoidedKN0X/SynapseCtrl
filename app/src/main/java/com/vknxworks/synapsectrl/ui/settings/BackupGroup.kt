package com.vknxworks.synapsectrl.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vknxworks.synapsectrl.BuildConfig
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.container
import com.vknxworks.synapsectrl.data.settings.BackupFormatException
import com.vknxworks.synapsectrl.data.settings.SettingsBackup
import com.vknxworks.synapsectrl.data.settings.SettingsBackups
import com.vknxworks.synapsectrl.ui.components.ConfirmDialog
import com.vknxworks.synapsectrl.ui.components.Group
import com.vknxworks.synapsectrl.ui.components.GroupRow
import com.vknxworks.synapsectrl.ui.lock.AppLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Back up and restore SynapseCtrl's own settings: a small JSON file in a place the user picks. No servers,
 * tokens, PIN or certificates are in it, and restoring shows what will be replaced and asks first.
 */
@Composable
fun BackupGroup() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<SettingsBackup?>(null) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val text = SettingsBackups.export(c.settings.current(), c.notifySettings.current(), BuildConfig.VERSION_NAME, OffsetDateTime.now().toString())
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: throw IOException()
                }
            }.isSuccess
            Toast.makeText(context, if (ok) R.string.backup_saved else R.string.backup_save_failed, Toast.LENGTH_LONG).show()
        }
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        // A settings file is well under a kilobyte; anything far bigger isn't one.
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            if (out.size() > SettingsBackups.MAX_BYTES) throw BackupFormatException(newer = false)
                        }
                        out.toString(Charsets.UTF_8.name())
                    } ?: throw IOException()
                }
                SettingsBackups.parse(text)
            }
            result.onSuccess { pending = it }.onFailure { e ->
                val msg = if (e is BackupFormatException && e.newer) R.string.backup_too_new else R.string.backup_not_valid
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    Group(stringResource(R.string.backup_title), footer = stringResource(R.string.backup_footer)) {
        GroupRow(
            stringResource(R.string.backup_save),
            stringResource(R.string.backup_save_sub),
            Icons.Outlined.FileDownload,
            onClick = { AppLock.expectReturn(); save.launch("synapsectrl-settings-${LocalDate.now()}.json") },
        )
        GroupRow(
            stringResource(R.string.backup_restore),
            stringResource(R.string.backup_restore_sub),
            Icons.Outlined.FileUpload,
            onClick = { AppLock.expectReturn(); open.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
        )
    }

    pending?.let { b ->
        val lines = buildList {
            if (b.hideScreenshots == true) add(stringResource(R.string.backup_line_hide_screenshots))
            if (b.hideRecents == true) add(stringResource(R.string.backup_line_hide_recents))
            b.validInterval?.let { add(stringResource(R.string.backup_line_interval, it)) }
            b.knownEvents?.let { events ->
                val names = events.sortedBy { it.ordinal }.map { stringResource(it.label) }
                add(stringResource(R.string.backup_line_events, names.joinToString(", ").ifEmpty { stringResource(R.string.backup_line_events_none) }))
            }
            b.validMediaThreshold?.let { add(stringResource(R.string.backup_line_media, it)) }
        }
        ConfirmDialog(
            title = stringResource(R.string.backup_restore_title),
            body = stringResource(
                R.string.backup_restore_body,
                b.createdAt.take(10).ifBlank { "?" },
                lines.joinToString("\n") { "• $it" }.ifEmpty { stringResource(R.string.backup_line_nothing) },
            ),
            confirmLabel = stringResource(R.string.backup_restore_confirm),
            onDismiss = { pending = null },
            onConfirm = {
                pending = null
                scope.launch {
                    val ok = runCatching { SettingsBackups.restore(b, c.settings, c.notifySettings) }.isSuccess
                    Toast.makeText(context, if (ok) R.string.backup_restored else R.string.backup_restore_failed, Toast.LENGTH_LONG).show()
                }
            },
            danger = false,
        )
    }
}
