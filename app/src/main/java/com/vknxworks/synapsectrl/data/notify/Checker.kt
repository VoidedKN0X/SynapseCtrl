package com.vknxworks.synapsectrl.data.notify

import android.content.Context
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.data.api.ApiError
import com.vknxworks.synapsectrl.data.api.ServerStatus
import com.vknxworks.synapsectrl.data.api.SynapseClient
import com.vknxworks.synapsectrl.data.overview.Severity
import com.vknxworks.synapsectrl.data.server.Server
import com.vknxworks.synapsectrl.ui.components.errorMessage
import kotlinx.serialization.Serializable

/** What a server looked like at the last check, so only changes are notified. Kept per server. */
@Serializable
data class CheckState(
    /** Conditions that were true last time (and have been notified). */
    val active: Set<String> = emptySet(),
    /** Checks in a row that couldn't reach the server (or sign in). */
    val unreachableCount: Int = 0,
    val lastCheckAt: Long = 0,
    /** "ok" or "failed". */
    val lastResult: String = "",
)

/** Result of one check: notifications to show, keys whose notifications to remove, the new state and the status. */
class CheckResult(val post: List<Alert>, val cancel: Set<String>, val state: CheckState, val status: ServerStatus?)

/**
 * One background check of one server, with reads only: whoami, the admin check and the Synapse
 * version (the same as the connection card). Synapse can't push notifications, so these checks are
 * what notices problems. Conditions are notified when they start and removed when they end.
 */
class Checker(private val context: Context) {

    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    suspend fun check(server: Server, client: SynapseClient, settings: NotifySettings, prev: CheckState, many: Boolean): CheckResult {
        val now = System.currentTimeMillis()
        val status = try {
            client.status()
        } catch (e: ApiError) {
            return unreachable(server, settings, prev, e, now)
        }
        val conditions = LinkedHashMap<String, Alert>()
        // Conditions are notified once when they start and removed when they end.
        val post = conditions.filterKeys { it !in prev.active }.values.toList()
        val cancel = prev.active - conditions.keys
        val state = CheckState(active = conditions.keys, unreachableCount = 0, lastCheckAt = now, lastResult = "ok")
        return CheckResult(post, cancel, state, status)
    }

    /** The server didn't answer (or refused the token or admin access): notify after two failed checks in a row. */
    private fun unreachable(server: Server, settings: NotifySettings, prev: CheckState, e: ApiError, now: Long): CheckResult {
        val count = prev.unreachableCount + 1
        val key = "${server.id}:unreachable"
        val keep = prev.active - key
        val post = mutableListOf<Alert>()
        var active = keep
        if (settings.on(NotifyEvent.UNREACHABLE) && count >= 2) {
            active = keep + key
            if (key !in prev.active) {
                val signIn = e is ApiError.Unauthorized || e is ApiError.NotAdmin
                post += Alert(
                    key, NotifyEvent.UNREACHABLE, Severity.WARNING,
                    s(if (signIn) R.string.nt_signin_title else R.string.nt_unreachable_title, server.name),
                    errorMessage(context, e, server.host),
                )
            }
        }
        val cancel = if (key in prev.active && key !in active) setOf(key) else emptySet()
        return CheckResult(post, cancel, prev.copy(active = active, unreachableCount = count, lastCheckAt = now, lastResult = "failed"), null)
    }
}
