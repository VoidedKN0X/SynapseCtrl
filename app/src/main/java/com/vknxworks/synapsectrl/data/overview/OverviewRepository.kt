package com.vknxworks.synapsectrl.data.overview

import com.vknxworks.synapsectrl.data.api.ApiError
import com.vknxworks.synapsectrl.data.health.HealthChecker
import com.vknxworks.synapsectrl.data.server.Sessions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The Overview of one server: the last summary, whether it's loading, and the last error. */
data class OverviewState(
    val summary: Summary? = null,
    val loading: Boolean = false,
    val error: ApiError? = null,
)

/** Loads and keeps the Overview of each server, in memory only. */
class OverviewRepository(
    private val sessions: Sessions,
    private val health: HealthChecker,
    @Suppress("unused") private val scope: CoroutineScope,
) {
    private val _states = MutableStateFlow<Map<String, OverviewState>>(emptyMap())
    val states: StateFlow<Map<String, OverviewState>> = _states.asStateFlow()

    private fun update(id: String, f: (OverviewState) -> OverviewState) {
        _states.value = _states.value + (id to f(_states.value[id] ?: OverviewState()))
    }

    /**
     * Reloads the active server's Overview. Skips if it loaded less than [maxAgeMs] ago, unless forced.
     *
     * Everything but the media usage is loaded and shown first. The media usage, which can take many
     * requests, follows: a total younger than [MediaTotalsCache.MAX_AGE_MS] is used as it is (never
     * when forced); otherwise the last known total stays on screen while a new one is added up, and
     * [OverviewState.loading] stays true until that is done.
     */
    suspend fun refresh(force: Boolean, maxAgeMs: Long = 30_000) {
        val active = sessions.active.value ?: return
        val id = active.server.id
        val current = _states.value[id]
        if (!force && current?.summary != null && System.currentTimeMillis() - current.summary.loadedAt < maxAgeMs) return
        update(id) { it.copy(loading = true) }
        try {
            val base = loadSummary(active.client, health, includeMedia = false)
            val key = MediaTotalsCache.keyFor(active.client)
            val reuse = if (force) null else MediaTotalsCache.fresh(key)
            val summary = base.withMedia(reuse ?: MediaTotalsCache.last(key))
            update(id) { OverviewState(summary = summary, loading = reuse == null, error = null) }
            sessions.reportStatus(id, summary.status)
            if (reuse == null) {
                val media = loadMediaTotals(active.client)
                if (media != null) MediaTotalsCache.put(key, media)
                // Only onto the summary this total was loaded for: a newer refresh brings its own.
                updateExisting(id) { state ->
                    val shown = state.summary
                    if (shown != null && shown.loadedAt == summary.loadedAt) state.copy(summary = shown.withMedia(media)) else state
                }
            }
        } catch (e: ApiError) {
            update(id) { it.copy(loading = false, error = e) }
            sessions.reportError(id, e)
        } finally {
            // Also when the screen was left half-way (the load is cancelled): never leave "loading" on.
            updateExisting(id) { if (it.loading) it.copy(loading = false) else it }
        }
    }

    /** Like [update], but leaves a server alone that has no Overview here (any more). */
    private fun updateExisting(id: String, f: (OverviewState) -> OverviewState) {
        val current = _states.value[id] ?: return
        val next = f(current)
        if (next !== current) _states.value = _states.value + (id to next)
    }

    fun forget(id: String) {
        _states.value = _states.value - id
        MediaTotalsCache.forget(id)
    }
}
