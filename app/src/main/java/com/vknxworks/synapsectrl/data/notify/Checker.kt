package com.vknxworks.synapsectrl.data.notify

import android.content.Context
import android.text.format.Formatter
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.data.admin.UserOrder
import com.vknxworks.synapsectrl.data.admin.users
import com.vknxworks.synapsectrl.data.api.ApiError
import com.vknxworks.synapsectrl.data.api.ServerStatus
import com.vknxworks.synapsectrl.data.api.SynapseClient
import com.vknxworks.synapsectrl.data.health.HealthChecker
import com.vknxworks.synapsectrl.data.overview.Severity
import com.vknxworks.synapsectrl.data.overview.loadSummary
import com.vknxworks.synapsectrl.data.server.Server
import com.vknxworks.synapsectrl.ui.components.errorMessage
import com.vknxworks.synapsectrl.ui.overview.healthTitle
import kotlinx.serialization.Serializable

/** What a server looked like at the last check, so only changes are notified. Kept per server. */
@Serializable
data class CheckState(
    /** Conditions that were true last time (and have been notified). */
    val active: Set<String> = emptySet(),
    /** Checks in a row that couldn't reach the server (or sign in). */
    val unreachableCount: Int = 0,
    /** Event reports and users at the last check; -1 before the first one, which only records them. */
    val reportsTotal: Long = -1,
    val usersTotal: Long = -1,
    val lastCheckAt: Long = 0,
    /** "ok" or "failed". */
    val lastResult: String = "",
)

/** Result of one check: notifications to show, keys whose notifications to remove, the new state and the status. */
class CheckResult(val post: List<Alert>, val cancel: Set<String>, val state: CheckState, val status: ServerStatus?)

/**
 * One background check of one server, with reads only: the same summary as the Overview (counts,
 * media usage, health checks). Synapse can't push notifications, so these checks are what notices
 * things. Conditions (health failing, media over the limit, unreachable) are notified when they start
 * and removed when they end; new reports and new users are notified once each.
 */
class Checker(private val context: Context, private val health: HealthChecker) {

    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    /** "Server: title" when more than one server is saved, else just the title. */
    private fun title(server: Server, many: Boolean, text: String) = if (many) s(R.string.nt_title_server, server.name, text) else text

    suspend fun check(server: Server, client: SynapseClient, settings: NotifySettings, prev: CheckState, many: Boolean): CheckResult {
        val now = System.currentTimeMillis()
        val summary = try {
            loadSummary(client, if (settings.on(NotifyEvent.HEALTH)) health else null)
        } catch (e: ApiError) {
            return unreachable(server, settings, prev, e, now)
        }
        val conditions = LinkedHashMap<String, Alert>()
        val events = mutableListOf<Alert>()

        // --- Health checks failing (well-known, federation, calls) ---
        if (settings.on(NotifyEvent.HEALTH)) {
            val failing = summary.failingHealth
            if (failing.isNotEmpty()) {
                val key = "${server.id}:health"
                conditions[key] = Alert(
                    key, NotifyEvent.HEALTH, Severity.CRITICAL,
                    title(server, many, context.resources.getQuantityString(R.plurals.att_health, failing.size, failing.size)),
                    failing.joinToString(", ") { s(healthTitle(it.id)) },
                )
            }
        }

        // --- Local media over the limit ---
        val mediaLimit = settings.mediaThresholdGb * 1_000_000_000L
        if (settings.on(NotifyEvent.MEDIA) && (summary.mediaBytes ?: 0) >= mediaLimit) {
            val key = "${server.id}:media"
            conditions[key] = Alert(
                key, NotifyEvent.MEDIA, Severity.WARNING,
                title(server, many, s(R.string.att_media, Formatter.formatShortFileSize(context, summary.mediaBytes ?: 0))),
                s(R.string.nt_media_text, Formatter.formatShortFileSize(context, mediaLimit)),
            )
        }

        // --- New event reports (once per new report) ---
        val reports = summary.reportsTotal
        if (settings.on(NotifyEvent.REPORT) && reports != null && prev.reportsTotal >= 0 && reports > prev.reportsTotal) {
            val n = (reports - prev.reportsTotal).toInt()
            events += Alert(
                "${server.id}:report:$reports", NotifyEvent.REPORT, Severity.WARNING,
                title(server, many, context.resources.getQuantityString(R.plurals.nt_new_reports, n, n)),
                s(R.string.nt_new_reports_text),
            )
        }

        // --- New users (registration tokens or created by an admin) ---
        val users = summary.usersTotal
        if (settings.on(NotifyEvent.NEW_USER) && users != null && prev.usersTotal >= 0 && users > prev.usersTotal) {
            val n = (users - prev.usersTotal).toInt()
            val newest = runCatching {
                client.users(limit = n.coerceAtMost(5), order = UserOrder.CREATED, backwards = true).users.map { it.name }
            }.getOrDefault(emptyList())
            events += Alert(
                "${server.id}:user:$users", NotifyEvent.NEW_USER, Severity.INFO,
                title(server, many, context.resources.getQuantityString(R.plurals.nt_new_users, n, n)),
                newest.joinToString(", ").ifBlank { s(R.string.nt_new_users_text) },
            )
        }

        // Conditions are notified once when they start and removed when they end.
        val post = conditions.filterKeys { it !in prev.active }.values.toList() + events
        val cancel = prev.active - conditions.keys
        val state = CheckState(
            active = conditions.keys,
            unreachableCount = 0,
            reportsTotal = reports ?: prev.reportsTotal,
            usersTotal = users ?: prev.usersTotal,
            lastCheckAt = now,
            lastResult = "ok",
        )
        return CheckResult(post, cancel, state, summary.status)
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
