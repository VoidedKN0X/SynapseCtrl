package com.vknxworks.synapsectrl.data.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vknxworks.synapsectrl.container
import com.vknxworks.synapsectrl.data.widget.WidgetData
import com.vknxworks.synapsectrl.ui.widgets.StatusWidget
import com.vknxworks.synapsectrl.data.api.ApiJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Saved [CheckState] per server, in no-backup storage. */
object CheckStore {
    private val _lastCheck = MutableStateFlow<Map<String, CheckState>>(emptyMap())

    /** The last check of each server, for the notification settings page. */
    val lastCheck: StateFlow<Map<String, CheckState>> = _lastCheck.asStateFlow()

    private fun dir(context: Context) = File(context.noBackupFilesDir, "notify")

    private fun file(context: Context, serverId: String) = File(dir(context).apply { mkdirs() }, "$serverId.json")

    suspend fun read(context: Context, serverId: String): CheckState = withContext(Dispatchers.IO) {
        val s = runCatching { ApiJson.decodeFromString(CheckState.serializer(), file(context, serverId).readText()) }.getOrNull() ?: CheckState()
        _lastCheck.value = _lastCheck.value + (serverId to s)
        s
    }

    suspend fun write(context: Context, serverId: String, state: CheckState) = withContext(Dispatchers.IO) {
        runCatching { file(context, serverId).writeText(ApiJson.encodeToString(CheckState.serializer(), state)) }
        _lastCheck.value = _lastCheck.value + (serverId to state)
    }

    fun forget(context: Context, serverId: String) {
        runCatching { file(context, serverId).delete() }
        _lastCheck.value = _lastCheck.value - serverId
    }

    fun forgetAll(context: Context) {
        runCatching { dir(context).deleteRecursively() }
        _lastCheck.value = emptyMap()
    }
}

/**
 * Background check of every saved server (Synapse can't push notifications). Runs every 15,
 * 30, 60 or 120 minutes while background checks are on (Android doesn't allow less than 15), only
 * with a network connection, once right away when "Check now" is tapped, and right away when ntfy
 * delivers a push to SynapseCtrl's push address (see [Push]). Only reads; it never
 * changes anything on a server. A server whose password is sealed by the app lock is skipped while
 * SynapseCtrl is locked.
 */
class NotifyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val notify = c.notifySettings.current()
        if (!notify.polling && !inputData.getBoolean(KEY_FORCE, false)) return Result.success()
        val servers = c.sessions.servers.value
        val checker = Checker(applicationContext, c.health)
        for (server in servers) {
            if (c.serverStore.sealedNow(server.id)) continue
            val prev = CheckStore.read(applicationContext, server.id)
            val result = runCatching {
                checker.check(server, c.sessions.backgroundClient(server), notify, prev, many = servers.size > 1)
            }.getOrNull() ?: continue
            result.cancel.forEach { Notifier.cancel(applicationContext, it) }
            result.post.forEach { Notifier.post(applicationContext, it) }
            CheckStore.write(applicationContext, server.id, result.state)
        }
        // Widgets get fresh numbers with every check.
        if (StatusWidget.anyPlaced(applicationContext)) runCatching { WidgetData.refresh(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "synapsectrl-check"
        private const val ONCE = "synapsectrl-check-now"
        private const val PUSHED = "synapsectrl-check-push"
        const val KEY_FORCE = "force"

        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Starts, changes or stops the periodic check to match [settings]. */
        fun schedule(context: Context, settings: NotifySettings) {
            val wm = WorkManager.getInstance(context)
            if (!settings.polling) {
                wm.cancelUniqueWork(PERIODIC)
                return
            }
            val request = PeriodicWorkRequestBuilder<NotifyWorker>(settings.intervalMinutes.toLong(), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag(PERIODIC)
                .build()
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** One check now (from "Check now"), even when background checks are off. */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<NotifyWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_FORCE to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, request)
        }

        /**
         * One check because ntfy delivered a push (a wake-up call: Synapse can't push itself). Runs
         * as soon as possible (expedited while Android allows it), and only while background checks are on.
         * A check that's already waiting or running is kept rather than started again.
         */
        fun runFromPush(context: Context) {
            val request = OneTimeWorkRequestBuilder<NotifyWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(PUSHED, ExistingWorkPolicy.KEEP, request)
        }
    }
}
