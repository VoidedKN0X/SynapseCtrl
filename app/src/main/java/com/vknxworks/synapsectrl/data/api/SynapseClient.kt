package com.vknxworks.synapsectrl.data.api

import com.vknxworks.synapsectrl.data.server.PrivateOnlyDns
import com.vknxworks.synapsectrl.data.server.Server
import com.vknxworks.synapsectrl.data.server.ServerSecrets
import com.vknxworks.synapsectrl.data.server.Tls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

/** Lenient JSON for Synapse responses: unknown fields ignored, nulls and bad values fall back to defaults. */
val ApiJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

private val JSON_TYPE = "application/json".toMediaType()

/**
 * OkHttp client for one server: normal certificate checks, or only the pinned certificate when the
 * user confirmed a self-signed one (never "trust all"). Plain-HTTP servers only resolve to private
 * addresses ([PrivateOnlyDns]).
 */
fun httpFor(base: OkHttpClient, url: HttpUrl, certPin: String): OkHttpClient {
    if (!url.isHttps) return base.newBuilder().dns(PrivateOnlyDns).build()
    if (certPin.isBlank()) return base
    val tm = Tls.PinnedTrustManager(certPin, Tls.systemTrustManager())
    val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(tm), null) }
    return base.newBuilder()
        .sslSocketFactory(ctx.socketFactory, tm)
        .hostnameVerifier(Tls.pinnedHostnameVerifier(certPin, url.host))
        .build()
}

/** Which API a request goes to: Synapse's admin API at a given version, or the Matrix client API (v3). */
sealed class Api {
    /** /_synapse/admin/v<n>/… (v1, v2 or v3 depending on the endpoint; see docs/admin_api). */
    data class Admin(val version: Int = 1) : Api()

    /** /_matrix/client/v3/… */
    data object Client : Api()
}

/**
 * Synapse client: the admin API (/_synapse/admin/…) and the few Matrix client endpoints it needs
 * (login, whoami, logout). Signs in with the access token in the Authorization header on every
 * request; the token never goes in a URL. Never logs requests, responses or the token.
 *
 * Paths are given as segments ("users", userId, "admin"), so IDs like @axel:vkhub.org or !room:vkhub.org
 * are encoded as one path segment each.
 */
class SynapseClient(
    val server: Server,
    base: OkHttpClient,
    /** The saved access token, read when a request is sent: it's sealed while the app lock is locked. */
    private val secrets: () -> ServerSecrets,
    /**
     * Whether the server is in read-only mode right now. Read on every request (not copied from
     * [server] once), so switching read-only on also stops clients a screen is still holding.
     */
    private val isReadOnly: () -> Boolean = { server.readOnly },
    /** Waits while SynapseCtrl is locked, so nothing is sent without the token (or while you're away). */
    private val awaitUnlocked: suspend () -> Unit = {},
) {
    /** A client for a server being set up, with the token just made or pasted. */
    constructor(server: Server, base: OkHttpClient, secrets: ServerSecrets) : this(server, base, { secrets })

    private val baseUrl: HttpUrl = server.url.toHttpUrl()
    val http: OkHttpClient = httpFor(base, baseUrl, server.certPin)

    fun url(api: Api, segments: List<String>, query: List<Pair<String, String>> = emptyList()): HttpUrl {
        val b = baseUrl.newBuilder()
        when (api) {
            is Api.Admin -> b.addPathSegment("_synapse").addPathSegment("admin").addPathSegment("v${api.version}")
            Api.Client -> b.addPathSegment("_matrix").addPathSegment("client").addPathSegment("v3")
        }
        segments.forEach { b.addPathSegment(it) }
        query.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build()
    }

    // --- Requests ---

    suspend fun get(api: Api, vararg segments: String, query: Map<String, String> = emptyMap()): JsonElement =
        call("GET", api, segments.toList(), query.toList(), null)

    suspend fun <T> get(strategy: DeserializationStrategy<T>, api: Api, vararg segments: String, query: Map<String, String> = emptyMap()): T =
        decode(strategy, get(api, *segments, query = query))

    suspend fun post(api: Api, vararg segments: String, body: JsonElement? = null, query: Map<String, String> = emptyMap()): JsonElement =
        call("POST", api, segments.toList(), query.toList(), body ?: JsonObject(emptyMap()))

    suspend fun put(api: Api, vararg segments: String, body: JsonElement? = null): JsonElement =
        call("PUT", api, segments.toList(), emptyList(), body ?: JsonObject(emptyMap()))

    suspend fun delete(api: Api, vararg segments: String, body: JsonElement? = null): JsonElement =
        call("DELETE", api, segments.toList(), emptyList(), body)

    // --- Sign-in ---

    /** GET /_matrix/client/v3/login without a token: which login types the server offers. */
    suspend fun loginFlows(): LoginFlows =
        decodeOrNotSynapse(LoginFlows.serializer(), call("GET", Api.Client, listOf("login"), emptyList(), null, auth = false, write = false))

    /**
     * POST /_matrix/client/v3/login with a username (or full user ID) and password. Makes a new device
     * named "SynapseCtrl (Android)" with its own access token; the password itself is never stored.
     */
    suspend fun login(user: String, password: String, deviceName: String): LoginResponse {
        val body = buildJsonObject {
            put("type", "m.login.password")
            putJsonObject("identifier") {
                put("type", "m.id.user")
                put("user", user)
            }
            put("password", password)
            put("initial_device_display_name", deviceName)
        }
        val element = try {
            call("POST", Api.Client, listOf("login"), emptyList(), body, auth = false, write = false)
        } catch (e: ApiError.Forbidden) {
            throw ApiError.BadLogin(e.detail)
        }
        val r = decodeOrNotSynapse(LoginResponse.serializer(), element)
        if (r.accessToken.isBlank() || r.userId.isBlank()) throw ApiError.NotSynapse()
        return r
    }

    /** GET /_matrix/client/v3/account/whoami: whose token this is. */
    suspend fun whoami(): Whoami {
        val w = decodeOrNotSynapse(Whoami.serializer(), call("GET", Api.Client, listOf("account", "whoami"), emptyList(), null))
        if (w.userId.isBlank()) throw ApiError.NotSynapse()
        return w
    }

    /**
     * Checks that [userId] is a server admin: GET /_synapse/admin/v1/users/<user_id>/admin. Synapse
     * only answers this for admins (others get 403 M_FORBIDDEN "You are not a server admin").
     */
    suspend fun requireAdmin(userId: String) {
        val status = try {
            decode(AdminStatus.serializer(), call("GET", Api.Admin(1), listOf("users", userId, "admin"), emptyList(), null))
        } catch (e: ApiError.Forbidden) {
            throw ApiError.NotAdmin()
        }
        if (!status.admin) throw ApiError.NotAdmin()
    }

    /** GET /_synapse/admin/v1/server_version. */
    suspend fun serverVersion(): String =
        decode(ServerVersion.serializer(), get(Api.Admin(1), "server_version")).serverVersion

    /**
     * Who's signed in, that it's a server admin, and the Synapse version: everything setup and the
     * connection card need, with reads only.
     */
    suspend fun status(): ServerStatus {
        val me = whoami()
        requireAdmin(me.userId)
        val version = serverVersion()
        if (version.isBlank()) throw ApiError.NotSynapse()
        return ServerStatus(version = version, userId = me.userId, deviceId = me.deviceId)
    }

    /**
     * POST /_matrix/client/v3/logout: invalidates this access token (and removes its device). Sent
     * when a server is removed from SynapseCtrl, even in read-only mode, because it only ends
     * SynapseCtrl's own session.
     */
    suspend fun logout() {
        call("POST", Api.Client, listOf("logout"), emptyList(), JsonObject(emptyMap()), write = false)
    }

    // --- Plumbing ---

    private fun <T> decode(strategy: DeserializationStrategy<T>, element: JsonElement): T = try {
        ApiJson.decodeFromJsonElement(strategy, element)
    } catch (e: SerializationException) {
        throw ApiError.NotSynapse()
    } catch (e: IllegalArgumentException) {
        throw ApiError.NotSynapse()
    }

    private fun <T> decodeOrNotSynapse(strategy: DeserializationStrategy<T>, element: JsonElement): T =
        if (element !is JsonObject) throw ApiError.NotSynapse() else decode(strategy, element)

    private suspend fun call(
        method: String,
        api: Api,
        segments: List<String>,
        query: List<Pair<String, String>>,
        body: JsonElement?,
        auth: Boolean = true,
        write: Boolean = method != "GET",
    ): JsonElement {
        // Read-only mode is enforced here, for every request that could change something, not only by hiding buttons.
        if (write && isReadOnly()) throw ApiError.ReadOnly()
        if (auth) awaitUnlocked()
        if (write && !WriteGuard.check()) throw ApiError.Cancelled()
        val token = if (auth) secrets().token else ""
        if (auth && token.isEmpty()) throw ApiError.Unauthorized(null, "M_MISSING_TOKEN")
        return withContext(Dispatchers.IO) {
            val requestBody = when {
                method == "GET" -> null
                body == null -> ByteArray(0).toRequestBody(null)
                else -> ApiJson.encodeToString(JsonElement.serializer(), body).toByteArray(Charsets.UTF_8).toRequestBody(JSON_TYPE)
            }
            val request = Request.Builder()
                .url(url(api, segments, query))
                .method(method, requestBody)
                .header("Accept", "application/json")
                .apply { if (auth) header("Authorization", "Bearer $token") }
                .build()
            val response = try {
                http.newCall(request).execute()
            } catch (e: IOException) {
                throw ApiError.fromIo(e)
            }
            response.use { parse(it, api) }
        }
    }

    private fun parse(r: Response, api: Api): JsonElement {
        val text = try {
            r.body?.string().orEmpty()
        } catch (e: IOException) {
            throw ApiError.fromIo(e)
        }
        val trimmed = text.trimStart()
        // A web page (or nothing useful) where JSON was expected: a reverse proxy or another app answered.
        val looksJson = trimmed.startsWith("{") || trimmed.startsWith("[")
        val err = if (looksJson && !r.isSuccessful) runCatching { ApiJson.decodeFromString(MatrixError.serializer(), text) }.getOrNull() else null
        val detail = err?.error?.takeIf { it.isNotBlank() }?.take(300)
        val code = err?.errcode?.takeIf { it.isNotBlank() }
        when {
            r.code in 300..399 -> throw ApiError.Redirect(r.header("Location")?.let { loc -> runCatching { r.request.url.resolve(loc)?.host }.getOrNull() })
            // The admin API not answering in Matrix JSON: blocked by a proxy or served on another listener.
            api is Api.Admin && !r.isSuccessful && err == null -> throw ApiError.AdminApiUnavailable(r.code)
            api is Api.Admin && r.code == 404 && code == "M_UNRECOGNIZED" -> throw ApiError.AdminApiUnavailable(r.code)
            r.code == 401 -> throw ApiError.Unauthorized(detail, code)
            r.code == 403 -> throw ApiError.Forbidden(detail, code)
            r.code == 404 && code == "M_NOT_FOUND" -> throw ApiError.NotFound(detail)
            r.code == 429 -> throw ApiError.RateLimited(err?.retryAfterMs ?: r.header("Retry-After")?.toLongOrNull()?.times(1000))
            !r.isSuccessful && err == null -> throw ApiError.NotSynapse()
            !r.isSuccessful -> throw ApiError.Server(r.code, detail, code)
        }
        if (trimmed.isEmpty()) return JsonNull
        if (!looksJson) throw ApiError.NotSynapse()
        return try {
            ApiJson.parseToJsonElement(text)
        } catch (_: SerializationException) {
            throw ApiError.NotSynapse()
        }
    }
}
