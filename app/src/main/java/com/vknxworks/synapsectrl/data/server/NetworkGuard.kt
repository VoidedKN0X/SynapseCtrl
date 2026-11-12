package com.vknxworks.synapsectrl.data.server

import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.Interceptor
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException

/** A request to anything but a configured Synapse server; it was stopped before it was sent. */
class BlockedHostException(val host: String) : IOException("blocked request to a host that isn't a configured server")

/** Plain HTTP to an address outside the user's own network, or to a server without the HTTP switch on. */
class CleartextRefusedException(val host: String) : IOException("plain HTTP refused")

/**
 * The only places SynapseCtrl may connect to: the saved servers, plus the one address being tested in
 * server setup. Every request from the app's HTTP client goes through [interceptor], which stops
 * anything else before it's sent. Redirects are never followed (see AppContainer), so a server
 * can't send the token on to another host either.
 */
class HostAllowlist(private val store: ServerStore) {

    /** The address server setup is testing right now, and whether plain HTTP was allowed for it. */
    private data class Pending(val origin: String, val allowHttp: Boolean)

    @Volatile
    private var pending: Pending? = null

    /** Allows [url] while setup tests it. Cleared with [endSetup]. */
    fun beginSetup(url: HttpUrl, allowHttp: Boolean) {
        pending = Pending(ServerAddress.origin(url), allowHttp)
    }

    fun endSetup() {
        pending = null
    }

    /** Null when [url] may be requested, otherwise why not. */
    fun check(url: HttpUrl): IOException? {
        val origin = ServerAddress.origin(url)
        val saved = store.servers.value.firstOrNull { s -> s.parsedUrl?.let { ServerAddress.origin(it) } == origin }
        val p = pending?.takeIf { it.origin == origin }
        if (saved == null && p == null) return BlockedHostException(url.host)
        if (!url.isHttps) {
            val httpAllowed = (saved?.allowHttp == true || p?.allowHttp == true) && ServerAddress.isPrivateHost(url.host)
            if (!httpAllowed) return CleartextRefusedException(url.host)
        }
        return null
    }

    val interceptor = Interceptor { chain ->
        val request = chain.request()
        check(request.url)?.let { throw it }
        chain.proceed(request)
    }
}

/**
 * For plain-HTTP servers given by name (adguard.lan): only private addresses are used, so a name
 * that resolves to a public address never receives the token unencrypted.
 */
object PrivateOnlyDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val all = Dns.SYSTEM.lookup(hostname)
        val private = all.filter { ServerAddress.isPrivateAddress(it) }
        if (private.isEmpty()) throw CleartextRefusedException(hostname)
        return private
    }
}
