package com.vknxworks.synapsectrl.ui.settings

import androidx.compose.foundation.BorderStroke
import android.content.Intent
import com.vknxworks.synapsectrl.ui.components.CtrlTextButton
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sd
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.container
import com.vknxworks.synapsectrl.data.notify.CheckStore
import com.vknxworks.synapsectrl.data.notify.Checker
import com.vknxworks.synapsectrl.data.notify.NotifyEvent
import com.vknxworks.synapsectrl.data.notify.NotifySettings
import com.vknxworks.synapsectrl.data.notify.NotifyWorker
import com.vknxworks.synapsectrl.data.notify.Push
import com.vknxworks.synapsectrl.data.notify.PushStatus
import com.vknxworks.synapsectrl.data.notify.UnifiedPush
import com.vknxworks.synapsectrl.ui.components.CtrlSecondaryButton
import com.vknxworks.synapsectrl.ui.components.copySensitive
import com.vknxworks.synapsectrl.ui.components.Group
import com.vknxworks.synapsectrl.ui.components.GroupChoice
import com.vknxworks.synapsectrl.ui.components.GroupRow
import com.vknxworks.synapsectrl.ui.components.GroupSwitch
import com.vknxworks.synapsectrl.ui.components.ScreenTopBar
import com.vknxworks.synapsectrl.ui.components.SecureScreen
import com.vknxworks.synapsectrl.ui.components.relativeTime
import com.vknxworks.synapsectrl.ui.components.rememberNotificationAccess
import com.vknxworks.synapsectrl.ui.lock.AppLock
import com.vknxworks.synapsectrl.ui.theme.Gutter
import kotlinx.coroutines.launch

/**
 * Background checks (Synapse can't push notifications, so SynapseCtrl checks your servers itself),
 * push through ntfy that wakes those checks at once, what to notify about, and battery advice.
 */
@Composable
fun NotificationsScreen() {
    val context = LocalContext.current
    val c = context.container
    val settings by c.notifySettings.settings.collectAsStateWithLifecycle()
    val servers by c.sessions.servers.collectAsStateWithLifecycle()
    val lastCheck by CheckStore.lastCheck.collectAsStateWithLifecycle()
    val pushStatus by Push.status.collectAsStateWithLifecycle()
    val access = rememberNotificationAccess()
    val canPost = access.canPost
    var batteryVersion by remember { mutableIntStateOf(0) }
    val unrestricted = remember(batteryVersion) {
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    val ntfyInstalled = remember(batteryVersion) { UnifiedPush.isNtfyInstalled(context) }
    val ntfyUnrestricted = remember(batteryVersion) { UnifiedPush.isNtfyUnrestricted(context) }

    // Re-check permission, battery and push state when coming back from system settings or ntfy.
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            batteryVersion++
            servers.forEach { CheckStore.read(context, it.id) }
            Push.load(context, c.notifySettings.current().push)
        }
    }

    /** Turning a notification feature on asks for the permission (or opens Android's settings) when notifications are blocked. */
    fun ensurePermission() {
        if (!canPost) access.fix()
    }
    fun update(transform: (NotifySettings) -> NotifySettings) {
        c.appScope.launch { c.notifySettings.update(transform) }
    }
    fun copyEndpoint(text: String) {
        // The push address holds the secret ntfy topic: keep it out of clipboard previews and keyboard history.
        copySensitive(context, "SynapseCtrl", text)
        Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
    }
    fun openOutside(intent: Intent) {
        AppLock.expectReturn()
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.notifications))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.notif_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
            )

            if (!canPost && settings.polling) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.notif_permission_off), color = MaterialTheme.colorScheme.onErrorContainer)
                        CtrlTextButton(onClick = { access.fix() }) { Text(stringResource(R.string.notif_permission_allow)) }
                    }
                }
            }

            // --- Background checks ---
            Group(stringResource(R.string.group_polling), footer = stringResource(R.string.polling_footer)) {
                GroupSwitch(
                    stringResource(R.string.polling_title),
                    stringResource(R.string.polling_subtitle),
                    // Shown off while notifications are blocked: the checks can't tell you anything then.
                    checked = settings.polling && canPost,
                    icon = Icons.Outlined.Schedule,
                ) { on ->
                    if (on) ensurePermission()
                    update { it.copy(polling = on) }
                }
                if (settings.polling && canPost) {
                    GroupChoice(
                        stringResource(R.string.polling_interval),
                        NotifySettings.INTERVALS.map { it to pluralStringResource(R.plurals.polling_interval_value, it, it) },
                        settings.intervalMinutes,
                        icon = Icons.Outlined.Timer,
                    ) { m -> update { it.copy(intervalMinutes = m) } }
                }
                servers.forEach { server ->
                    val st = lastCheck[server.id]
                    val subtitle = when {
                        st == null || st.lastCheckAt == 0L -> stringResource(R.string.last_check_never)
                        st.lastResult == "ok" -> stringResource(R.string.last_check, relativeTime(st.lastCheckAt))
                        else -> stringResource(R.string.last_check_failed, relativeTime(st.lastCheckAt))
                    }
                    GroupRow(server.name, subtitle)
                }
                GroupRow(
                    stringResource(R.string.check_now),
                    icon = Icons.Outlined.Refresh,
                    onClick = {
                        access.askIfPossible()
                        NotifyWorker.runNow(context)
                        Toast.makeText(context, context.getString(R.string.check_started), Toast.LENGTH_SHORT).show()
                    },
                )
            }

            // --- Push through ntfy: wakes the background check right away ---
            Group(stringResource(R.string.group_push), footer = stringResource(R.string.push_footer)) {
                GroupSwitch(
                    stringResource(R.string.push_title),
                    stringResource(R.string.push_subtitle),
                    checked = settings.push && canPost,
                    icon = Icons.Outlined.Campaign,
                ) { on ->
                    if (on) ensurePermission()
                    update { it.copy(push = on) }
                }
                if (!ntfyInstalled) {
                    PushNote(stringResource(R.string.push_no_ntfy)) {
                        CtrlSecondaryButton(onClick = { openOutside(Intent(Intent.ACTION_VIEW, Uri.parse(UnifiedPush.NTFY_DOWNLOAD))) }) {
                            Text(stringResource(R.string.push_install_ntfy))
                        }
                    }
                }
                if (settings.push && canPost && ntfyInstalled) {
                    if (!settings.polling) PushNote(stringResource(R.string.push_needs_polling)) {}
                    when (val st = pushStatus) {
                        PushStatus.NoNtfy, PushStatus.Off, PushStatus.Registering -> PushNote(stringResource(R.string.push_registering)) {
                            CtrlTextButton(onClick = { Push.register(context) }) { Text(stringResource(R.string.retry)) }
                        }
                        is PushStatus.Failed -> PushNote(stringResource(R.string.push_failed, st.reason)) {
                            CtrlTextButton(onClick = { Push.register(context) }) { Text(stringResource(R.string.retry)) }
                        }
                        is PushStatus.Unavailable -> PushNote(stringResource(R.string.push_unavailable)) {}
                        is PushStatus.Registered -> {
                            // The push address holds the secret ntfy topic: kept out of screenshots while it's shown.
                            SecureScreen()
                            GroupRow(
                                stringResource(R.string.push_endpoint),
                                st.endpoint,
                                icon = Icons.Outlined.NotificationsActive,
                                trailing = { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy)) },
                                onClick = { copyEndpoint(st.endpoint) },
                            )
                            PushNote(stringResource(R.string.push_endpoint_note)) {}
                        }
                    }
                }
            }

            // --- What to notify about ---
            Group(stringResource(R.string.group_events), footer = stringResource(R.string.events_footer)) {
                NotifyEvent.entries.forEach { e ->
                    GroupSwitch(
                        stringResource(e.label),
                        stringResource(e.summary),
                        checked = settings.on(e) && canPost,
                    ) { on ->
                        if (on) ensurePermission()
                        update { s -> s.copy(events = if (on) s.events + e else s.events - e) }
                    }
                    if (e == NotifyEvent.MEDIA && settings.on(e) && canPost) {
                        GroupChoice(
                            stringResource(R.string.media_limit),
                            NotifySettings.MEDIA_THRESHOLDS.map { it to android.text.format.Formatter.formatShortFileSize(context, it * 1_000_000_000L) },
                            settings.mediaThresholdGb,
                            icon = Icons.Outlined.Sd,
                        ) { gb -> update { it.copy(mediaThresholdGb = gb) } }
                    }
                }
            }

            // --- Battery ---
            Group(stringResource(R.string.group_battery), footer = stringResource(R.string.battery_footer)) {
                GroupRow(
                    stringResource(if (unrestricted) R.string.battery_unrestricted else R.string.battery_restricted),
                    icon = if (unrestricted) Icons.Outlined.BatteryFull else Icons.Outlined.BatteryAlert,
                    iconTint = if (unrestricted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                if (settings.push && ntfyInstalled && !ntfyUnrestricted) {
                    GroupRow(
                        stringResource(R.string.battery_ntfy_restricted),
                        stringResource(R.string.battery_ntfy_restricted_body),
                        icon = Icons.Outlined.BatteryAlert,
                        iconTint = MaterialTheme.colorScheme.error,
                        showChevron = true,
                        onClick = { openOutside(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${UnifiedPush.NTFY}"))) },
                    )
                }
                GroupRow(
                    stringResource(R.string.battery_open),
                    showChevron = true,
                    onClick = {
                        // App info is where ColorOS keeps "Allow background activity"; fall back to the battery list.
                        val appInfo = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        AppLock.expectReturn()
                        runCatching { context.startActivity(appInfo) }.onFailure {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                    },
                )
            }
        }
    }
}

/** A line of explanation inside the push group, with an optional action under it. */
@Composable
private fun PushNote(text: String, action: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action()
    }
}
