package com.vknxworks.synapsectrl.data.health

import com.vknxworks.synapsectrl.BuildConfig
import com.vknxworks.synapsectrl.data.api.ApiJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The health checks on the Overview. */
enum class HealthId { SYNAPSE, WELL_KNOWN_CLIENT, WELL_KNOWN_SERVER, FEDERATION, LARGE_REQUEST, RTC, RTC_SFU }

/** [SKIP]: the check couldn't be made from here (nothing is known to be wrong). */
enum class HealthState { PASS, WARN, FAIL, SKIP }

/** One check's result. [detail] is the technical part shown under the explanation (an address, an error). */
data class HealthCheck(val id: HealthId, val state: HealthState, val detail: String = "")

/** The saved answer of the last large-request test for one public address. */
@Serializable
data class LargeResult(val state: String = "", val detail: String = "", val at: Long = 0)

/** What the answer to the large test request means. Pure, so it can be tested without a server. */
object LargeRequest {
    /** The size of the test request's body: well above what a proxy keeps in memory (nginx: 8 or 16 kB). */
    const val BODY_BYTES = 300 * 1024

    /** A passed test is repeated after this long; pulling down on the Overview repeats it at once. */
    const val PASS_MAX_AGE_MS = 3 * 60 * 60_000L

    /** A test that didn't pass is repeated sooner, so a repaired proxy shows up quickly. */
    const val OTHER_MAX_AGE_MS = 30 * 60_000L

    /**
     * [code]: the HTTP status; [matrixJson]: the body was a Matrix JSON error (an object with "errcode")
     * or another JSON object, which only Synapse sends; [server]: the Server header, for the detail line.
     *
     * - Matrix JSON, whatever the status (401 M_MISSING_TOKEN is the expected one): the request got
     *   through the proxy to Synapse. Pass.
     * - 413 or 5xx that isn't JSON: the proxy itself rejected or failed on the large body. Fail.
     * - Anything else that isn't JSON (a redirect, a 403 page): can't tell. Warn.
     */
    fun classify(code: Int, matrixJson: Boolean, server: String?): Pair<HealthState, String> {
        val by = server?.trim()?.takeIf { it.isNotEmpty() }?.take(40)
        val detail = listOfNotNull("HTTP $code", by).joinToString(" · ")
        return when {
            matrixJson -> HealthState.PASS to ""
            code == 413 || code in 500..599 -> HealthState.FAIL to detail
            else -> HealthState.WARN to detail
        }
    }

    fun fresh(r: LargeResult?, now: Long): Boolean {
        if (r == null || r.state.isEmpty()) return false
        val age = now - r.at
        val max = if (r.state == HealthState.PASS.name) PASS_MAX_AGE_MS else OTHER_MAX_AGE_MS
        return age in 0L until max
    }
}

/** Where LiveKit itself is expected next to the JWT service, from the two documented layouts. */
object RtcLayout {
    /**
     * - …/livekit/jwt (Element's self-hosting layout): LiveKit at …/livekit/sfu on the same host.
     * - the bare host (the JWT service answers /sfu/get and /healthz there): LiveKit at the root.
     * Anything else: unknown (null).
     */
    fun sfuUrl(jwtServiceUrl: HttpUrl): HttpUrl? {
        val path = jwtServiceUrl.encodedPath.trimEnd('/')
        return when {
            path.endsWith("/livekit/jwt") -> jwtServiceUrl.newBuilder().encodedPath(path.removeSuffix("/jwt") + "/sfu").query(null).build()
            path.isEmpty() -> jwtServiceUrl.newBuilder().encodedPath("/").query(null).build()
            else -> null
        }
    }

    /** LiveKit answers GET / with 200 "OK" when ready and 406 "Not Ready…" when not (pkg/service/server.go). */
    fun classify(code: Int, body: String): HealthState = when {
        code == 200 && body.trim() == "OK" -> HealthState.PASS
        code == 406 && body.trimStart().startsWith("Not Ready") -> HealthState.FAIL
        code in 500..599 -> HealthState.WARN
        else -> HealthState.SKIP
    }
}

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
 * - large requests: one PUT of about 300 kB, without a token, to the public client address. Synapse
 *   refuses it with a small JSON error (401 M_MISSING_TOKEN) before doing anything with it, so nothing
 *   changes on the server; a reverse proxy that can't take large request bodies answers with its own
 *   error page instead (413, or a 500 when its temporary folder is gone);
 * - MatrixRTC: well-known client lists a LiveKit focus (org.matrix.msc4143.rtc_foci), and its
 *   service (lk-jwt-service) answers /healthz;
 * - LiveKit: the media server next to that service answers its own health check, where the two
 *   documented layouts put it.
 *
 * These are plain public requests over HTTPS, with no token or other credentials, only to your
 * server name and the hosts its own well-known files name. They use a separate HTTP client for that
 * reason: it can't reach anything else, and it never carries the admin token. Everything is a GET,
 * except the one large test request described above.
 *
 * [cacheDir]: where the last large-request result per address is kept (a state, a status line and a
 * time), so the 300 kB isn't sent on every refresh and background check. Null: memory only.
 */
class HealthChecker(private val cacheDir: File? = null) {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(Interceptor { chain ->
            val r = chain.request()
            val largeTest = r.method == "PUT" && r.tag(LargeTest::class.java) != null
            if (!r.url.isHttps || (r.method != "GET" && !largeTest) || r.header("Authorization") != null) throw HealthBlocked()
            chain.proceed(r.newBuilder().header("User-Agent", "SynapseCtrl/${BuildConfig.VERSION_NAME} (Android)").build())
        })
        .build()

    private class Fetched(val code: Int, val json: JsonObject?, val text: String = "", val server: String? = null)

    /** Marks the one request that may be something other than a GET. */
    private object LargeTest

    private companion object {
        const val MAX_BODY = 256L * 1024
        val JSON_TYPE = "application/json".toMediaType()
        val CACHE = MapSerializer(String.serializer(), LargeResult.serializer())
    }

    private val largeLock = Any()
    private var largeResults: MutableMap<String, LargeResult>? = null

    private fun largeCache(): MutableMap<String, LargeResult> = synchronized(largeLock) {
        largeResults ?: run {
            val read = cacheDir?.let { dir ->
                runCatching { ApiJson.decodeFromString(CACHE, File(dir, "large.json").readText()) }.getOrNull()
            }
            HashMap(read ?: emptyMap()).also { largeResults = it }
        }
    }

    private fun rememberLarge(key: String, result: LargeResult) = synchronized(largeLock) {
        val all = largeCache()
        all[key] = result
        // Only the addresses checked lately are kept.
        val cutoff = result.at - 7 * 24 * 60 * 60_000L
        all.entries.removeAll { it.value.at < cutoff }
        cacheDir?.let { dir ->
            runCatching {
                dir.mkdirs()
                File(dir, "large.json").writeText(ApiJson.encodeToString(CACHE, all))
            }
        }
    }

    /**
     * PUT /_matrix/client/v3/rooms/<a room that can't exist>/send/m.room.message/<txn> with about 300 kB
     * of JSON and no token. Synapse checks the token first and answers 401 M_MISSING_TOKEN; the event is
     * never looked at, so nothing is created or changed.
     */
    private suspend fun largeRequest(baseUrl: HttpUrl, serverName: String): HealthCheck = withContext(Dispatchers.IO) {
        val url = baseUrl.newBuilder()
            .addPathSegments("_matrix/client/v3/rooms")
            .addPathSegment("!synapsectrl-health-check:$serverName")
            .addPathSegments("send/m.room.message")
            .addPathSegment("synapsectrl-health-check")
            .build()
        val padding = "0".repeat(LargeRequest.BODY_BYTES)
        val body = "{\"msgtype\":\"m.text\",\"body\":\"$padding\"}".toByteArray(Charsets.UTF_8).toRequestBody(JSON_TYPE)
        val request = Request.Builder().url(url).put(body).header("Accept", "application/json").tag(LargeTest::class.java, LargeTest).build()
        try {
            // More time than the small reads get: 300 kB over a slow mobile connection.
            val patient = http.newBuilder().writeTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build()
            patient.newCall(request).execute().use { r ->
                val text = runCatching {
                    val source = r.body?.source() ?: return@runCatching ""
                    source.request(MAX_BODY)
                    source.buffer.snapshot().utf8()
                }.getOrDefault("")
                val json = runCatching { ApiJson.parseToJsonElement(text) as? JsonObject }.getOrNull()
                val (state, detail) = LargeRequest.classify(r.code, json != null, r.header("Server"))
                HealthCheck(HealthId.LARGE_REQUEST, state, detail)
            }
        } catch (e: IOException) {
            // Cut off half-way: could be the proxy, could be the phone's connection.
            HealthCheck(HealthId.LARGE_REQUEST, HealthState.WARN, shortError(e))
        }
    }

    private suspend fun fetch(url: HttpUrl): Fetched = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).get().header("Accept", "application/json").build()).execute().use { r ->
            // These answers can come from any host a well-known file names, so only the first 256 KB is read
            // (discovery files and version answers are a few hundred bytes); anything bigger fails the check.
            val text = runCatching {
                val source = r.body?.source() ?: return@runCatching ""
                if (source.request(MAX_BODY + 1)) "" else source.buffer.readUtf8()
            }.getOrDefault("")
            Fetched(r.code, runCatching { ApiJson.parseToJsonElement(text) as? JsonObject }.getOrNull(), text.take(200), r.header("Server"))
        }
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun shortError(e: Throwable): String = (e.message ?: e.javaClass.simpleName).take(160)

    /**
     * Runs every check for [serverName] (e.g. vkhub.org). [synapseOk] is whether the admin API answered
     * at the address saved in SynapseCtrl, which is the "Synapse reachable" row. With [forceLarge] the
     * large test request is sent now; otherwise its last result is used while it's recent enough.
     */
    suspend fun check(serverName: String, synapseOk: Boolean, synapseDetail: String, forceLarge: Boolean = false): List<HealthCheck> = coroutineScope {
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

        // --- large requests through the public address (only when small ones work there) ---
        val publicBase = baseUrl?.toHttpUrlOrNull()
        val large = if (wkClient.state == HealthState.PASS && publicBase != null) {
            async {
                val key = publicBase.toString()
                val kept = largeCache()[key]
                if (!forceLarge && LargeRequest.fresh(kept, System.currentTimeMillis())) {
                    val state = runCatching { HealthState.valueOf(kept!!.state) }.getOrDefault(HealthState.WARN)
                    HealthCheck(HealthId.LARGE_REQUEST, state, kept!!.detail)
                } else {
                    largeRequest(publicBase, serverName).also {
                        rememberLarge(key, LargeResult(it.state.name, it.detail, System.currentTimeMillis()))
                    }
                }
            }
        } else {
            null
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
        val jwtUrl = livekit?.toHttpUrlOrNull()
        val rtc = when {
            clientJson == null -> HealthCheck(HealthId.RTC, HealthState.FAIL, "")
            livekit == null -> HealthCheck(HealthId.RTC, HealthState.FAIL, "org.matrix.msc4143.rtc_foci missing")
            else -> {
                val health = jwtUrl?.newBuilder()?.addPathSegment("healthz")?.build()
                val answer = health?.let { u -> runCatching { fetch(u) } }
                when {
                    answer == null -> HealthCheck(HealthId.RTC, HealthState.FAIL, livekit)
                    answer.isFailure -> HealthCheck(HealthId.RTC, HealthState.FAIL, "$livekit · ${shortError(answer.exceptionOrNull()!!)}")
                    // lk-jwt-service answers /healthz with 200; anything else below 500 still means something is there.
                    answer.getOrNull()!!.code < 500 -> HealthCheck(HealthId.RTC, HealthState.PASS, livekit)
                    else -> HealthCheck(HealthId.RTC, HealthState.WARN, "$livekit · HTTP ${answer.getOrNull()!!.code}")
                }
            }
        }

        // --- LiveKit itself, where the documented layouts put it next to the JWT service ---
        val sfu = if (jwtUrl == null) {
            null
        } else {
            val sfuUrl = RtcLayout.sfuUrl(jwtUrl)
            if (sfuUrl == null) {
                HealthCheck(HealthId.RTC_SFU, HealthState.SKIP, "")
            } else {
                val shown = sfuUrl.toString().trimEnd('/')
                val answer = runCatching { fetch(sfuUrl) }
                val got = answer.getOrNull()
                when {
                    got == null -> HealthCheck(HealthId.RTC_SFU, HealthState.FAIL, "$shown · ${shortError(answer.exceptionOrNull()!!)}")
                    else -> when (val state = RtcLayout.classify(got.code, got.text)) {
                        HealthState.PASS -> HealthCheck(HealthId.RTC_SFU, state, shown)
                        HealthState.SKIP -> HealthCheck(HealthId.RTC_SFU, state, "")
                        else -> HealthCheck(HealthId.RTC_SFU, state, "$shown · HTTP ${got.code}")
                    }
                }
            }
        }
        listOfNotNull(synapse, wkClient, wkServer, federation, large?.await(), rtc, sfu)
    }
}
