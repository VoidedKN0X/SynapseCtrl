package com.vknxworks.synapsectrl.data.health

import com.vknxworks.synapsectrl.BuildConfig
import com.vknxworks.synapsectrl.data.api.ApiJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The health checks on the Overview. */
enum class HealthId { SYNAPSE, WELL_KNOWN_CLIENT, WELL_KNOWN_SERVER, FEDERATION, RTC }

enum class HealthState { PASS, WARN, FAIL }

/** One check's result. [detail] is the technical part shown under the explanation (an address, an error). */
data class HealthCheck(val id: HealthId, val state: HealthState, val detail: String = "")

/** A request to something other than the public discovery endpoints of your own server. */
private class HealthBlocked : IOException("health checks only read public HTTPS endpoints")

/**
 * Health checks against your server's public face, without any outside tester service:
 *
 * - well-known client (https://<server name>/.well-known/matrix/client): has m.homeserver.base_url,
 *   and that address answers /_matrix/client/versions;
 * - well-known server (…/.well-known/matrix/server): says where other servers reach yours;
 * - federation: that address answers /_matrix/federation/v1/version and /_matrix/key/v2/server with
 *   your server name and a signing key;
 * - MatrixRTC: well-known client lists a LiveKit focus (org.matrix.msc4143.rtc_foci), and its
 *   service (lk-jwt-service) answers.
 *
 * These are plain public GET requests over HTTPS, with no token or other credentials, only to your
 * server name and the hosts its own well-known files name. They use a separate HTTP client for that
 * reason: it can't reach anything else, and it never carries the admin token.
 */
class HealthChecker {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(Interceptor { chain ->
            val r = chain.request()
            if (!r.url.isHttps || r.method != "GET" || r.header("Authorization") != null) throw HealthBlocked()
            chain.proceed(r.newBuilder().header("User-Agent", "SynapseCtrl/${BuildConfig.VERSION_NAME} (Android)").build())
        })
        .build()

    private class Fetched(val code: Int, val json: JsonObject?)

    private companion object {
        const val MAX_BODY = 256L * 1024
    }

    private suspend fun fetch(url: HttpUrl): Fetched = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).get().header("Accept", "application/json").build()).execute().use { r ->
            // These answers can come from any host a well-known file names, so only the first 256 KB is read
            // (discovery files and version answers are a few hundred bytes); anything bigger fails the check.
            val text = runCatching {
                val source = r.body?.source() ?: return@runCatching ""
                if (source.request(MAX_BODY + 1)) "" else source.buffer.readUtf8()
            }.getOrDefault("")
            Fetched(r.code, runCatching { ApiJson.parseToJsonElement(text) as? JsonObject }.getOrNull())
        }
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun shortError(e: Throwable): String = (e.message ?: e.javaClass.simpleName).take(160)

    /**
     * Runs every check for [serverName] (e.g. vkhub.org). [synapseOk] is whether the admin API answered
     * at the address saved in SynapseCtrl, which is the "Synapse reachable" row.
     */
    suspend fun check(serverName: String, synapseOk: Boolean, synapseDetail: String): List<HealthCheck> = coroutineScope {
        val synapse = HealthCheck(HealthId.SYNAPSE, if (synapseOk) HealthState.PASS else HealthState.FAIL, synapseDetail)
        val base = "https://$serverName".toHttpUrlOrNull()
            ?: return@coroutineScope listOf(synapse) + listOf(HealthId.WELL_KNOWN_CLIENT, HealthId.WELL_KNOWN_SERVER, HealthId.FEDERATION, HealthId.RTC)
                .map { HealthCheck(it, HealthState.FAIL, serverName) }

        val clientJob = async { runCatching { fetch(base.newBuilder().encodedPath("/.well-known/matrix/client").build()) } }
        val serverJob = async { runCatching { fetch(base.newBuilder().encodedPath("/.well-known/matrix/server").build()) } }
        val client = clientJob.await()
        val server = serverJob.await()

        // --- well-known client: m.homeserver.base_url, and that it answers ---
        val clientJson = client.getOrNull()?.json
        val baseUrl = clientJson?.obj("m.homeserver")?.str("base_url")?.trimEnd('/')
        val wkClient = when {
            client.isFailure -> HealthCheck(HealthId.WELL_KNOWN_CLIENT, HealthState.FAIL, shortError(client.exceptionOrNull()!!))
            clientJson == null -> HealthCheck(HealthId.WELL_KNOWN_CLIENT, HealthState.FAIL, "HTTP ${client.getOrNull()?.code}")
            baseUrl == null -> HealthCheck(HealthId.WELL_KNOWN_CLIENT, HealthState.FAIL, "m.homeserver.base_url missing")
            else -> {
                val versions = baseUrl.toHttpUrlOrNull()?.newBuilder()?.addPathSegments("_matrix/client/versions")?.build()
                val answer = versions?.let { u -> runCatching { fetch(u) }.getOrNull() }
                if (answer?.json?.get("versions") is JsonArray) HealthCheck(HealthId.WELL_KNOWN_CLIENT, HealthState.PASS, baseUrl)
                else HealthCheck(HealthId.WELL_KNOWN_CLIENT, HealthState.WARN, baseUrl)
            }
        }

        // --- well-known server: m.server ---
        val serverJson = server.getOrNull()?.json
        val mServer = serverJson?.str("m.server")
        val wkServer = when {
            mServer != null -> HealthCheck(HealthId.WELL_KNOWN_SERVER, HealthState.PASS, mServer)
            // Without delegation, other servers use port 8448 on the server name; that can be fine.
            else -> HealthCheck(HealthId.WELL_KNOWN_SERVER, HealthState.WARN, "$serverName:8448")
        }

        // --- federation: version and signing keys where other servers will connect ---
        val target = mServer ?: "$serverName:8448"
        val fedBase = "https://$target".toHttpUrlOrNull()
        val federation = if (fedBase == null) {
            HealthCheck(HealthId.FEDERATION, HealthState.FAIL, target)
        } else {
            val version = runCatching { fetch(fedBase.newBuilder().encodedPath("/_matrix/federation/v1/version").build()) }
            val keys = runCatching { fetch(fedBase.newBuilder().encodedPath("/_matrix/key/v2/server").build()) }
            val versionOk = version.getOrNull()?.json?.obj("server") != null
            val keyJson = keys.getOrNull()?.json
            val keysOk = keyJson?.str("server_name") == serverName && (keyJson.obj("verify_keys")?.isNotEmpty() == true)
            when {
                versionOk && keysOk -> HealthCheck(HealthId.FEDERATION, HealthState.PASS, target)
                versionOk -> HealthCheck(HealthId.FEDERATION, HealthState.WARN, "$target · keys")
                else -> HealthCheck(
                    HealthId.FEDERATION, HealthState.FAIL,
                    version.exceptionOrNull()?.let { "$target · ${shortError(it)}" } ?: "$target · HTTP ${version.getOrNull()?.code}",
                )
            }
        }

        // --- MatrixRTC: a LiveKit focus in well-known, and its service answers ---
        val foci = clientJson?.get("org.matrix.msc4143.rtc_foci") as? JsonArray
        val livekit = foci?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it.str("type") == "livekit" }
            ?.str("livekit_service_url")
        val rtc = when {
            clientJson == null -> HealthCheck(HealthId.RTC, HealthState.FAIL, "")
            livekit == null -> HealthCheck(HealthId.RTC, HealthState.FAIL, "org.matrix.msc4143.rtc_foci missing")
            else -> {
                val url = livekit.toHttpUrlOrNull()
                val health = url?.newBuilder()?.addPathSegment("healthz")?.build()
                val answer = health?.let { u -> runCatching { fetch(u) } }
                when {
                    answer == null -> HealthCheck(HealthId.RTC, HealthState.FAIL, livekit)
                    answer.isFailure -> HealthCheck(HealthId.RTC, HealthState.FAIL, "$livekit · ${shortError(answer.exceptionOrNull()!!)}")
                    // Any answer means the service is up; lk-jwt-service answers /healthz with 200.
                    answer.getOrNull()!!.code < 500 -> HealthCheck(HealthId.RTC, HealthState.PASS, livekit)
                    else -> HealthCheck(HealthId.RTC, HealthState.WARN, "$livekit · HTTP ${answer.getOrNull()!!.code}")
                }
            }
        }
        listOf(synapse, wkClient, wkServer, federation, rtc)
    }
}
