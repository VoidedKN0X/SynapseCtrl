package com.vknxworks.synapsectrl.data.notify

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.data.settings.appDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Things SynapseCtrl can notify about, from its own background checks (Synapse can't push).
 * [defaultOn]: the important ones are on as soon as background checks are, the rest are opt-in.
 */
enum class NotifyEvent(@StringRes val label: Int, @StringRes val summary: Int, val defaultOn: Boolean) {
    REPORT(R.string.ev_report, R.string.ev_report_sub, true),
    HEALTH(R.string.ev_health, R.string.ev_health_sub, true),
    UNREACHABLE(R.string.ev_unreachable, R.string.ev_unreachable_sub, true),
    NEW_USER(R.string.ev_new_user, R.string.ev_new_user_sub, true),
    MEDIA(R.string.ev_media, R.string.ev_media_sub, false),
}

data class NotifySettings(
    /** Check the servers in the background with WorkManager. Off until turned on. */
    val polling: Boolean = false,
    val intervalMinutes: Int = 15,
    /**
     * Wake the background check through ntfy (UnifiedPush) whenever something posts to SynapseCtrl's push
     * address. Only works with [polling] on, which stays the fallback.
     */
    val push: Boolean = false,
    /** The events that are switched on. */
    val events: Set<NotifyEvent> = NotifyEvent.entries.filter { it.defaultOn }.toSet(),
    /** Local media above this many gigabytes counts as "over the limit" (Overview and notifications). */
    val mediaThresholdGb: Int = 50,
) {
    fun on(e: NotifyEvent): Boolean = e in events

    companion object {
        val INTERVALS = listOf(15, 30, 60, 120)
        val MEDIA_THRESHOLDS = listOf(5, 10, 25, 50, 100, 250, 500, 1000)
    }
}

class NotifySettingsRepository(private val context: Context, scope: CoroutineScope) {
    private object K {
        val polling = booleanPreferencesKey("notify_polling")
        val interval = intPreferencesKey("notify_interval")
        val push = booleanPreferencesKey("notify_push")
        val events = stringSetPreferencesKey("notify_events")
        val mediaThreshold = intPreferencesKey("notify_media_threshold_gb")
    }

    /** A copy of [NotifySettings.polling] that can be read without waiting, for the password sealing. */
    private val mirror = context.getSharedPreferences("notify_mirror", Context.MODE_PRIVATE)

    val settings: StateFlow<NotifySettings> = context.appDataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, NotifySettings(polling = pollingNow()))

    /** Whether background checks are on, read straight away (it decides whether passwords are sealed). */
    fun pollingNow(): Boolean = mirror.getBoolean("polling", false)

    /** The saved settings, read directly (for the background worker, before the flow has a value). */
    suspend fun current(): NotifySettings = context.appDataStore.data.first().toSettings()

    private fun Preferences.toSettings() = NotifySettings(
        polling = this[K.polling] ?: false,
        intervalMinutes = (this[K.interval] ?: 15).let { if (it in NotifySettings.INTERVALS) it else 15 },
        push = this[K.push] ?: false,
        events = this[K.events]?.mapNotNull { n -> NotifyEvent.entries.firstOrNull { it.name == n } }?.toSet()
            ?: NotifyEvent.entries.filter { it.defaultOn }.toSet(),
        mediaThresholdGb = (this[K.mediaThreshold] ?: 50).let { if (it in NotifySettings.MEDIA_THRESHOLDS) it else 50 },
    )

    suspend fun update(transform: (NotifySettings) -> NotifySettings) {
        var polling = false
        context.appDataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.polling] = s.polling
            p[K.interval] = s.intervalMinutes
            p[K.push] = s.push
            p[K.events] = s.events.map { it.name }.toSet()
            p[K.mediaThreshold] = s.mediaThresholdGb
            polling = s.polling
        }
        mirror.edit().putBoolean("polling", polling).commit()
    }
}
