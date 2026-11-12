package com.vknxworks.synapsectrl.data.api

import com.vknxworks.synapsectrl.data.server.BlockedHostException
import com.vknxworks.synapsectrl.data.server.CleartextRefusedException
import com.vknxworks.synapsectrl.data.server.Tls
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 * Everything that can go wrong talking to Synapse, sorted into what the user can act on.
 * The UI turns these into messages from strings.xml. [detail] is Synapse's own wording ("error" in its
 * JSON) and [errcode] its Matrix error code (M_FORBIDDEN, M_NOT_FOUND, …) when it gave them.
 */
sealed class ApiError(val detail: String? = null, val errcode: String? = null, cause: Throwable? = null) : IOException(detail, cause) {
    /** No route, DNS failure, refused or timed out. */
    class Unreachable(cause: Throwable?) : ApiError(null, null, cause)

    /** The certificate isn't trusted by the phone and hasn't been pinned. */
    class UntrustedCertificate(cause: Throwable?) : ApiError(null, null, cause)

    /** The server now presents a different certificate from the pinned one. */
    class PinMismatch(cause: Throwable?) : ApiError(null, null, cause)

    /** Login refused: wrong username or password (M_FORBIDDEN on /login), or too many attempts. */
    class BadLogin(detail: String?) : ApiError(detail, "M_FORBIDDEN")

    /** 401: the access token is unknown, logged out or expired (M_UNKNOWN_TOKEN, M_MISSING_TOKEN). */
    class Unauthorized(detail: String?, errcode: String?) : ApiError(detail, errcode)

    /** Signed in, but the account isn't a Synapse server admin, so the admin API refuses it. */
    class NotAdmin : ApiError(null, "M_FORBIDDEN")

    /** 403 for another reason: Synapse refused this request. */
    class Forbidden(detail: String?, errcode: String?) : ApiError(detail, errcode)

    /**
     * The client API answers, but /_synapse/admin doesn't (a reverse proxy blocks it, or it's on another
     * listener). Usually the address is the public one while the admin API is only open on the LAN.
     */
    class AdminApiUnavailable(val status: Int) : ApiError(null, null)

    /** The address answered, but not like Synapse (a reverse proxy page, another app). */
    class NotSynapse : ApiError(null, null)

    /** Synapse only offers single sign-on or Matrix Authentication Service logins, no password login. */
    class NoPasswordLogin : ApiError(null, null)

    /** The server answered with a redirect (for example to HTTPS); SynapseCtrl never follows one. */
    class Redirect(val location: String?) : ApiError(null, null)

    /** 429: too many requests. [retryAfterMs] when Synapse said how long to wait. */
    class RateLimited(val retryAfterMs: Long?) : ApiError(null, "M_LIMIT_EXCEEDED")

    /** Plain HTTP to an address outside the user's own network, or without the server's HTTP switch on. */
    class CleartextRefused : ApiError(null, null)

    /** A request to a host that isn't a configured server; stopped before it was sent. */
    class BlockedHost : ApiError(null, null)

    /** The server is in read-only mode, so SynapseCtrl refused to send a change. Raised before anything is sent. */
    class ReadOnly : ApiError(null, null)

    /** The change wasn't confirmed with the fingerprint or app PIN ("Confirm changes"); nothing was sent. */
    class Cancelled : ApiError(null, null)

    /** 404 with M_NOT_FOUND: the user, room, media or report doesn't exist (any more). */
    class NotFound(detail: String?) : ApiError(detail, "M_NOT_FOUND")

    /** Any other error status from Synapse (bad request, internal error, …). */
    class Server(val code: Int, detail: String?, errcode: String?) : ApiError(detail, errcode)

    companion object {
        private fun causes(e: Throwable): Sequence<Throwable> = generateSequence(e) { it.cause.takeIf { c -> c !== it } }

        /** Sorts a network-level failure from OkHttp. */
        fun fromIo(e: IOException): ApiError = when {
            e is ApiError -> e
            causes(e).any { it is BlockedHostException } -> BlockedHost()
            causes(e).any { it is CleartextRefusedException } -> CleartextRefused()
            Tls.isPinMismatch(e) -> PinMismatch(e)
            Tls.isCertificateError(e) -> UntrustedCertificate(e)
            e is SSLHandshakeException -> UntrustedCertificate(e)
            e is UnknownHostException || e is ConnectException || e is NoRouteToHostException || e is SocketTimeoutException -> Unreachable(e)
            else -> Unreachable(e)
        }
    }
}
