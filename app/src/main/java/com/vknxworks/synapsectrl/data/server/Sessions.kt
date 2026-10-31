package com.vknxworks.synapsectrl.data.server

import com.vknxworks.synapsectrl.data.api.SynapseClient
import com.vknxworks.synapsectrl.data.api.ApiError
import com.vknxworks.synapsectrl.data.api.ServerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** The active server and its API client. */
class ActiveServer(val server: Server, val client: SynapseClient)

/** Whether removing a server also logged its access token out on the server. */
enum class LogoutResult { LOGGED_OUT, FAILED, NOTHING }

/** The active server's last status: what it runs, who's signed in, and whether it can be reached right now. */
data class Connection(
    val status: ServerStatus? = null,
    val loading: Boolean = true,
    val error: ApiError? = null,
)

/**
 * Keeps the list of servers, which one is active, an API client for it and its status.
 * Switching servers rebuilds the client; any change to a server (address, certificate, read-only) does too.
 */
class Sessions(
    private val store: ServerStore,
    private val baseHttp: OkHttpClient,
    private val scope: CoroutineScope,
    /** The app lock is locked: the tokens are sealed, and requests wait until it's unlocked. */
    private val locked: StateFlow<Boolean>,
) {
    /** Decrypted tokens, kept in memory only while unlocked (see [forgetSecrets]). */
    private val secretCache = ConcurrentHashMap<String, ServerSecrets>()

    val servers: StateFlow<List<Server>> = store.servers

    /** Bumped when the token changes without other server settings changing, so the client is rebuilt. */
    private val revision = MutableStateFlow(0)

    val active: StateFlow<ActiveServer?> = combine(store.servers, store.activeId, revision) { list, id, rev -> list.firstOrNull { it.id == id } to rev }
        .distinctUntilChanged()
        .map { (server, _) -> server?.let { ActiveServer(it, clientFor(it)) } }
        .stateIn(scope, SharingStarted.Eagerly, store.servers.value.firstOrNull { it.id == store.activeId.value }?.let { ActiveServer(it, clientFor(it)) })

    private val _connection = MutableStateFlow(Connection())
    val connection: StateFlow<Connection> = _connection.asStateFlow()
    private var statusJob: Job? = null

    init {
        scope.launch {
            active.map { it?.server?.let { s -> Triple(s.id, s.url, s.certPin) } to revision.value }
                .distinctUntilChanged()
                .collect { refreshStatus() }
        }
    }

    fun clientFor(server: Server): SynapseClient = SynapseClient(
        server,
        baseHttp,
        secrets = { cachedSecrets(server.id) },
        // The saved setting, not this copy of the server: a client made before read-only was
        // switched on must not keep writing.
        isReadOnly = { store.servers.value.firstOrNull { it.id == server.id }?.readOnly ?: server.readOnly },
        awaitUnlocked = { locked.first { !it } },
    )

    /**
     * A client for background work (notifications, the widget) that reads the token straight from
     * storage and doesn't wait for the app lock: it only works while the token isn't sealed (see
     * [ServerStore.sealedNow]). Reads only; it refuses to send any change.
     */
    fun backgroundClient(server: Server): SynapseClient = SynapseClient(
        server,
        baseHttp,
        secrets = { store.secrets(server.id) },
        isReadOnly = { true },
    )

    private fun cachedSecrets(id: String): ServerSecrets =
        secretCache[id] ?: store.secrets(id).also { if (it.token.isNotEmpty() && !locked.value) secretCache[id] = it }

    /** The app locked: drop every decrypted token from memory. */
    fun forgetSecrets() = secretCache.clear()

    /** Reloads the active server's status: who's signed in, admin access and the Synapse version. */
    fun refreshStatus() {
        statusJob?.cancel()
        val current = active.value
        if (current == null) {
            _connection.value = Connection(loading = false)
            return
        }
        _connection.value = _connection.value.copy(loading = true)
        statusJob = scope.launch {
            try {
                val status = current.client.status()
                if (status.version != current.server.version) {
                    store.update(current.server.id) { it.copy(version = status.version) }
                }
                _connection.value = Connection(status = status, loading = false)
            } catch (e: ApiError) {
                _connection.value = _connection.value.copy(loading = false, error = e)
            }
        }
    }

    /** A screen's request to the active server failed in a way that affects the whole connection. */
    fun reportError(serverId: String, e: ApiError) {
        if (active.value?.server?.id != serverId) return
        val connectionWide = e is ApiError.Unreachable || e is ApiError.Unauthorized || e is ApiError.NotAdmin ||
            e is ApiError.AdminApiUnavailable || e is ApiError.PinMismatch || e is ApiError.UntrustedCertificate ||
            e is ApiError.CleartextRefused || e is ApiError.Redirect || e is ApiError.NotSynapse
        if (!connectionWide) return
        _connection.value = _connection.value.copy(loading = false, error = e)
    }

    fun add(server: Server, secrets: ServerSecrets): Server {
        val saved = if (server.id.isBlank()) server.copy(id = UUID.randomUUID().toString()) else server
        store.saveSecrets(saved.id, secrets)
        secretCache.remove(saved.id)
        store.save(saved)
        store.setActive(saved.id)
        // Editing a server can leave its settings unchanged but its token new: rebuild the client either way.
        revision.value++
        return saved
    }

    fun switchTo(id: String) = store.setActive(id)

    /**
     * Removes a server: first logs its access token out (POST /_matrix/client/v3/logout), so it stops
     * working on the server too, then forgets it. Removing still happens when the server can't be
     * reached; [LogoutResult] says whether the token was logged out.
     */
    suspend fun remove(id: String): LogoutResult {
        val server = store.servers.value.firstOrNull { it.id == id } ?: return LogoutResult.NOTHING
        val result = if (store.sealedNow(id)) {
            LogoutResult.FAILED
        } else {
            try {
                SynapseClient(server, baseHttp, { cachedSecrets(id) }, isReadOnly = { false }).logout()
                LogoutResult.LOGGED_OUT
            } catch (e: ApiError.Unauthorized) {
                // Already logged out or expired: nothing left to invalidate.
                LogoutResult.LOGGED_OUT
            } catch (e: Exception) {
                LogoutResult.FAILED
            }
        }
        secretCache.remove(id)
        store.remove(id)
        return result
    }

    /** Logs out a token that's no longer saved (replaced by signing in again). Best effort. */
    suspend fun logoutToken(server: Server, secrets: ServerSecrets) {
        SynapseClient(server, baseHttp, secrets).logout()
    }

    fun setReadOnly(id: String, readOnly: Boolean) = store.update(id) { it.copy(readOnly = readOnly) }

    fun secrets(id: String): ServerSecrets = store.secrets(id)
}
