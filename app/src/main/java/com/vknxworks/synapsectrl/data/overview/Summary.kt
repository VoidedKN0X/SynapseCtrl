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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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
 * Loads everything the Overview shows, with reads only: user counts (v3 users with limit 1, which
 * still reports the total), the room count, open event reports, media usage summed over the users'
 * media statistics, failing federation destinations, background updates and the health checks. Each
 * part is loaded on its own, so one failing part doesn't hide the rest; who's signed in and admin
 * access must work, or the whole summary fails with that error.
 */
suspend fun loadSummary(client: SynapseClient, health: HealthChecker?): Summary = coroutineScope {
    val status = client.status()
    val total = async { runCatching { client.users(limit = 1).total }.getOrNull() }
    val active = async { runCatching { client.users(limit = 1, deactivated = false).total }.getOrNull() }
    val deactivated = async { runCatching { client.users(limit = 1, deactivated = true).total }.getOrNull() }
    val rooms = async { runCatching { client.rooms(limit = 1).totalRooms }.getOrNull() }
    val reports = async { runCatching { client.eventReports(limit = 1).total }.getOrNull() }
    val media = async {
        runCatching {
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
            Triple(bytes, count, from != null)
        }.getOrNull()
    }
    val failing = async {
        runCatching { client.destinations(limit = 100, failingFirst = true).destinations.filter { it.failing }.map { it.destination } }.getOrDefault(emptyList())
    }
    val background = async { runCatching { client.backgroundUpdates() }.getOrNull() }
    val checks = async {
        if (health == null || status.serverName.isBlank()) emptyList()
        else runCatching { health.check(status.serverName, synapseOk = true, synapseDetail = client.server.displayUrl) }.getOrDefault(emptyList())
    }
    val m = media.await()
    Summary(
        status = status,
        usersTotal = total.await(),
        usersActive = active.await(),
        usersDeactivated = deactivated.await(),
        rooms = rooms.await(),
        mediaBytes = m?.first,
        mediaCount = m?.second,
        mediaPartial = m?.third == true,
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
