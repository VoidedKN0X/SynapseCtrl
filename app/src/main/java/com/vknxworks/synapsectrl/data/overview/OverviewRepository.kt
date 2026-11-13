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
    /** Called with every fresh summary (the widget shows the same numbers). */
    private val onSummary: (com.vknxworks.synapsectrl.data.server.Server, Summary) -> Unit = { _, _ -> },
) {
    private val _states = MutableStateFlow<Map<String, OverviewState>>(emptyMap())
    val states: StateFlow<Map<String, OverviewState>> = _states.asStateFlow()

    private fun update(id: String, f: (OverviewState) -> OverviewState) {
        _states.value = _states.value + (id to f(_states.value[id] ?: OverviewState()))
    }

    /** Reloads the active server's Overview. Skips if it loaded less than [maxAgeMs] ago, unless forced. */
    suspend fun refresh(force: Boolean, maxAgeMs: Long = 30_000) {
        val active = sessions.active.value ?: return
        val id = active.server.id
        val current = _states.value[id]
        if (!force && current?.summary != null && System.currentTimeMillis() - current.summary.loadedAt < maxAgeMs) return
        update(id) { it.copy(loading = true) }
        try {
            val summary = loadSummary(active.client, health)
            update(id) { OverviewState(summary = summary, loading = false, error = null) }
            sessions.reportStatus(id, summary.status)
            runCatching { onSummary(active.server, summary) }
        } catch (e: ApiError) {
            update(id) { it.copy(loading = false, error = e) }
            sessions.reportError(id, e)
        }
    }

    fun forget(id: String) {
        _states.value = _states.value - id
    }
}
