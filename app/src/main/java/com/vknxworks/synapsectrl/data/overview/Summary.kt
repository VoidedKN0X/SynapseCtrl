package com.vknxworks.synapsectrl.data.overview

import com.vknxworks.synapsectrl.data.admin.BackgroundUpdates
import com.vknxworks.synapsectrl.data.admin.backgroundUpdates
import com.vknxworks.synapsectrl.data.admin.destinations
import com.vknxworks.synapsectrl.data.admin.eventReports
import com.vknxworks.synapsectrl.data.admin.mediaStats
import com.vknxworks.synapsectrl.data.admin.rooms
import com.vknxworks.synapsectrl.data.admin.users
import com.vknxworks.synapsectrl.data.api.ApiError
import com.vknxworks.synapsectrl.data.api.ServerStatus
import com.vknxworks.synapsectrl.data.api.SynapseClient
import com.vknxworks.synapsectrl.data.health.HealthCheck
import com.vknxworks.synapsectrl.data.health.HealthChecker
import com.vknxworks.synapsectrl.data.health.HealthState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

/** The numbers on the Overview. Null means that part couldn't be loaded. */
data class Summary(
    val status: ServerStatus,
    val usersTotal: Long? = null,
    val usersActive: Long? = null,
    val usersDeactivated: Long? = null,
    val rooms: Long? = null,
    val mediaBytes: Long? = null,
    val mediaCount: Long? = null,
    /** True when there were more users with media than SynapseCtrl added up. */
    val mediaPartial: Boolean = false,
    val reportsTotal: Long? = null,
    val failingDestinations: List<String> = emptyList(),
    val background: BackgroundUpdates? = null,
    val health: List<HealthCheck> = emptyList(),
    val loadedAt: Long = System.currentTimeMillis(),
) {
    val failingHealth: List<HealthCheck> get() = health.filter { it.state == HealthState.FAIL }

    /** This summary with [media] as its media usage (null: not loaded). */
    fun withMedia(media: MediaTotals?): Summary = copy(mediaBytes = media?.bytes, mediaCount = media?.count, mediaPartial = media?.partial == true)
}

/** Something on the Overview that needs a look, most important first. */
sealed class AttentionItem(val severity: Severity) {
    data class Reports(val count: Long) : AttentionItem(Severity.WARNING)
    data class HealthFailing(val checks: List<HealthCheck>) : AttentionItem(Severity.CRITICAL)
    data class FederationFailing(val destinations: List<String>) : AttentionItem(Severity.WARNING)
    data class MediaOver(val bytes: Long, val thresholdBytes: Long) : AttentionItem(Severity.WARNING)
    data class BackgroundRunning(val names: List<String>) : AttentionItem(Severity.INFO)
}

object Attention {
    fun of(s: Summary, mediaThresholdBytes: Long): List<AttentionItem> = buildList {
        if (s.failingHealth.isNotEmpty()) add(AttentionItem.HealthFailing(s.failingHealth))
        s.reportsTotal?.takeIf { it > 0 }?.let { add(AttentionItem.Reports(it)) }
        if (s.failingDestinations.isNotEmpty()) add(AttentionItem.FederationFailing(s.failingDestinations))
        s.mediaBytes?.takeIf { mediaThresholdBytes > 0 && it >= mediaThresholdBytes }?.let { add(AttentionItem.MediaOver(it, mediaThresholdBytes)) }
        s.background?.currentUpdates?.values?.map { it.name }?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
            ?.let { add(AttentionItem.BackgroundRunning(it)) }
    }.sortedBy { it.severity.ordinal }
}

/**
 * Local media usage, added up over the users' media statistics. [partial]: there were more users with
 * media than SynapseCtrl added up.
 */
data class MediaTotals(val bytes: Long, val count: Long, val partial: Boolean, val loadedAt: Long = System.currentTimeMillis())

/**
 * The last media totals per server, in memory only. Adding them up takes one request per 100 users
 * with media, one after the other, and the result changes slowly, so the Overview and the background
 * checks share a total for [MAX_AGE_MS] instead of adding it up again on every refresh. Deleting
 * media in the app drops the server's total ([forget]), and so does pulling down to refresh.
 */
object MediaTotalsCache {
    const val MAX_AGE_MS = 10 * 60_000L

    private val totals = ConcurrentHashMap<String, MediaTotals>()

    /** One entry per server and address: a server whose address was changed starts over. */
    fun keyFor(client: SynapseClient): String = client.server.id + "|" + client.server.url

    /** The total for [key] when it's younger than [MAX_AGE_MS]. */
    fun fresh(key: String): MediaTotals? = totals[key]?.takeIf { System.currentTimeMillis() - it.loadedAt in 0L until MAX_AGE_MS }

    /** The last total for [key], however old. */
    fun last(key: String): MediaTotals? = totals[key]

    fun put(key: String, value: MediaTotals) {
        totals[key] = value
    }

    /** Drops what's kept for the server [serverId] (it was removed, or media was just deleted on it). */
    fun forget(serverId: String) {
        totals.keys.removeAll { it.startsWith("$serverId|") }
    }
}

/**
 * Adds up the users' media statistics, biggest users first: pages of 100, at most 20 pages. Null when
 * it couldn't be loaded.
 */
suspend fun loadMediaTotals(client: SynapseClient): MediaTotals? = try {
    var bytes = 0L
    var count = 0L
    var from: String? = null
    var pages = 0
    do {
        val page = client.mediaStats(from = from, limit = 100)
        page.users.forEach { bytes += it.mediaLength; count += it.mediaCount }
        from = page.nextToken
        pages++
    } while (from != null && pages < 20)
    MediaTotals(bytes, count, from != null)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

/**
 * Loads everything the Overview shows, with reads only: user counts (v3 users with limit 1, which
 * still reports the total; active and deactivated are asked for, and all users is their sum, since
 * v3 returns both kinds when the filter is left out), the room count, open event reports, media
 * usage, failing federation destinations, background updates and the health checks. Each part is
 * loaded on its own, so one failing part doesn't hide the rest; who's signed in and admin access must
 * work, or the whole summary fails with that error.
 *
 * Media usage is taken from [MediaTotalsCache] when a total younger than its maximum age is there,
 * and added up (and kept there) otherwise. With [includeMedia] false it's left out (null), for a
 * caller that loads it afterwards or doesn't need it. With [forceHealth] the large-request test is
 * sent now instead of using its last result (see [HealthChecker]).
 */
suspend fun loadSummary(client: SynapseClient, health: HealthChecker?, includeMedia: Boolean = true, forceHealth: Boolean = false): Summary = coroutineScope {
    val status = client.status()
    val active = async { runCatching { client.users(limit = 1, deactivated = false).total }.getOrNull() }
    val deactivated = async { runCatching { client.users(limit = 1, deactivated = true).total }.getOrNull() }
    val rooms = async { runCatching { client.rooms(limit = 1).totalRooms }.getOrNull() }
    val reports = async { runCatching { client.eventReports(limit = 1).total }.getOrNull() }
    val media = async {
        if (!includeMedia) {
            null
        } else {
            val key = MediaTotalsCache.keyFor(client)
            MediaTotalsCache.fresh(key) ?: loadMediaTotals(client)?.also { MediaTotalsCache.put(key, it) }
        }
    }
    val failing = async {
        runCatching { client.destinations(limit = 100, failingFirst = true).destinations.filter { it.failing }.map { it.destination } }.getOrDefault(emptyList())
    }
    val background = async { runCatching { client.backgroundUpdates() }.getOrNull() }
    val checks = async {
        if (health == null || status.serverName.isBlank()) emptyList()
        else runCatching { health.check(status.serverName, synapseOk = true, synapseDetail = client.server.displayUrl, forceLarge = forceHealth) }.getOrDefault(emptyList())
    }
    val m = media.await()
    val usersActive = active.await()
    val usersDeactivated = deactivated.await()
    Summary(
        status = status,
        usersTotal = if (usersActive != null && usersDeactivated != null) usersActive + usersDeactivated else null,
        usersActive = usersActive,
        usersDeactivated = usersDeactivated,
        rooms = rooms.await(),
        mediaBytes = m?.bytes,
        mediaCount = m?.count,
        mediaPartial = m?.partial == true,
        reportsTotal = reports.await(),
        failingDestinations = failing.await(),
        background = background.await(),
        health = checks.await(),
    )
}

/** True for errors that mean the server (or admin access) can't be used at all right now. */
fun ApiError.isConnectionWide(): Boolean = this is ApiError.Unreachable || this is ApiError.Unauthorized || this is ApiError.NotAdmin ||
    this is ApiError.AdminApiUnavailable || this is ApiError.PinMismatch || this is ApiError.UntrustedCertificate ||
    this is ApiError.CleartextRefused || this is ApiError.Redirect || this is ApiError.NotSynapse
