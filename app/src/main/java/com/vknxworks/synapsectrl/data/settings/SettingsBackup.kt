package com.vknxworks.synapsectrl.data.settings

import com.vknxworks.synapsectrl.data.api.ApiJson
import com.vknxworks.synapsectrl.data.notify.NotifyEvent
import com.vknxworks.synapsectrl.data.notify.NotifySettings
import com.vknxworks.synapsectrl.data.notify.NotifySettingsRepository
import kotlinx.serialization.Serializable

/**
 * SynapseCtrl's own settings as a file, to carry them to another phone or keep them through a reinstall.
 *
 * What's in it: the two privacy switches and what the background checks notify about. What is never in
 * it: servers, access tokens, the app PIN or anything else of the app lock, pinned certificates, and the
 * ntfy push address. Restoring can't make the app less protected: it never touches the app lock, never
 * turns background checks or push on or off (that changes how the tokens are encrypted), never turns a
 * privacy switch off, and never adds a server, a certificate or plain HTTP.
 */
@Serializable
data class SettingsBackup(
    val app: String = "",
    val format: Int = 0,
    val appVersion: String = "",
    val createdAt: String = "",
    val hideScreenshots: Boolean? = null,
    val hideRecents: Boolean? = null,
    val intervalMinutes: Int? = null,
    val events: List<String>? = null,
    val mediaThresholdGb: Int? = null,
) {
    /** The events named in the file that this version knows; null when the file has none. */
    val knownEvents: Set<NotifyEvent>? get() = events?.mapNotNull { n -> NotifyEvent.entries.firstOrNull { it.name == n } }?.toSet()
    val validInterval: Int? get() = intervalMinutes?.takeIf { it in NotifySettings.INTERVALS }
    val validMediaThreshold: Int? get() = mediaThresholdGb?.takeIf { it in NotifySettings.MEDIA_THRESHOLDS }
}

/** The file isn't a SynapseCtrl settings file, or ([newer]) it was made by a newer version. */
class BackupFormatException(val newer: Boolean) : Exception()

object SettingsBackups {
    const val APP = "SynapseCtrl"
    const val FORMAT = 1
    const val MAX_BYTES = 64 * 1024

    fun export(settings: AppSettings, notify: NotifySettings, appVersion: String, createdAt: String): String =
        ApiJson.encodeToString(
            SettingsBackup.serializer(),
            SettingsBackup(
                app = APP,
                format = FORMAT,
                appVersion = appVersion,
                createdAt = createdAt,
                hideScreenshots = settings.hideScreenshots,
                hideRecents = settings.hideRecents,
                intervalMinutes = notify.intervalMinutes,
                events = notify.events.map { it.name }.sorted(),
                mediaThresholdGb = notify.mediaThresholdGb,
            ),
        )

    fun parse(text: String): SettingsBackup {
        val b = try {
            ApiJson.decodeFromString(SettingsBackup.serializer(), text)
        } catch (e: Exception) {
            throw BackupFormatException(newer = false)
        }
        if (b.app != APP || b.format < 1) throw BackupFormatException(newer = false)
        if (b.format > FORMAT) throw BackupFormatException(newer = true)
        return b
    }

    /** Applies [b] with the limits described on [SettingsBackup]. */
    suspend fun restore(b: SettingsBackup, settings: SettingsRepository, notify: NotifySettingsRepository) {
        settings.update {
            it.copy(
                hideScreenshots = it.hideScreenshots || b.hideScreenshots == true,
                hideRecents = it.hideRecents || b.hideRecents == true,
            )
        }
        notify.update { cur ->
            cur.copy(
                intervalMinutes = b.validInterval ?: cur.intervalMinutes,
                events = b.knownEvents ?: cur.events,
                mediaThresholdGb = b.validMediaThreshold ?: cur.mediaThresholdGb,
            )
        }
    }
}
