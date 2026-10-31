package com.vknxworks.synapsectrl.data.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vknxworks.synapsectrl.container
import com.vknxworks.synapsectrl.data.admin.eventReports
import com.vknxworks.synapsectrl.data.admin.users
import com.vknxworks.synapsectrl.data.api.ApiJson
import com.vknxworks.synapsectrl.data.overview.Summary
import com.vknxworks.synapsectrl.data.server.Server
import com.vknxworks.synapsectrl.ui.widgets.StatusWidget
import kotlinx.serialization.Serializable
import java.io.File

/** What the widget shows: the active server's state, active users and open reports. No-backup storage. */
@Serializable
data class WidgetSnapshot(
    val serverId: String = "",
    val serverName: String = "",
    val online: Boolean = false,
    /** -1: unknown. */
    val usersActive: Long = -1,
    val reports: Long = -1,
    val updatedAt: Long = 0,
)

object WidgetStore {
    private fun file(context: Context) = File(context.noBackupFilesDir, "widget.json")

    fun read(context: Context): WidgetSnapshot? =
        runCatching { ApiJson.decodeFromString(WidgetSnapshot.serializer(), file(context).readText()) }.getOrNull()

    fun write(context: Context, s: WidgetSnapshot) {
        runCatching { file(context).writeText(ApiJson.encodeToString(WidgetSnapshot.serializer(), s)) }
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}

object WidgetData {
    /** The Overview just loaded: the widget shows the same numbers. */
    fun fromSummary(context: Context, server: Server, s: Summary) {
        if (!StatusWidget.anyPlaced(context)) return
        WidgetStore.write(
            context,
            WidgetSnapshot(server.id, server.name, true, s.usersActive ?: -1, s.reportsTotal ?: -1, System.currentTimeMillis()),
        )
        StatusWidget.redraw(context)
    }

    /**
     * Asks the active server for its state, active users and open reports (reads only), then redraws.
     * Skipped while the token is sealed by the app lock: the widget then only says SynapseCtrl is locked.
     */
    suspend fun refresh(context: Context) {
        val c = context.container
        val server = c.sessions.active.value?.server ?: run { StatusWidget.redraw(context); return }
        if (c.serverStore.sealedNow(server.id)) {
            StatusWidget.redraw(context)
            return
        }
        val client = c.sessions.backgroundClient(server)
        val snap = try {
            client.status()
            WidgetSnapshot(
                server.id, server.name, true,
                runCatching { client.users(limit = 1, deactivated = false).total }.getOrDefault(-1),
                runCatching { client.eventReports(limit = 1).total }.getOrDefault(-1),
                System.currentTimeMillis(),
            )
        } catch (e: Exception) {
            val old = WidgetStore.read(context)?.takeIf { it.serverId == server.id }
            (old ?: WidgetSnapshot(server.id, server.name)).copy(online = false, updatedAt = System.currentTimeMillis())
        }
        WidgetStore.write(context, snap)
        StatusWidget.redraw(context)
    }
}

/** Refreshes the widget in the background (when placed, and every 30 minutes). */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (StatusWidget.anyPlaced(applicationContext)) WidgetData.refresh(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "synapsectrl-widget"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
