package com.vknxworks.synapsectrl.data.server

import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.Inet6Address
import java.net.InetAddress

/**
 * A saved Synapse server. The access token is never part of this object: it is stored separately,
 * encrypted with a Keystore key (see [ServerStore]). SynapseCtrl only ever talks to exactly [url];
 * there is no second or fallback address.
 */
@Serializable
data class Server(
    val id: String,
    /** What the user calls it; defaults to the server name (vkhub.org). */
    val name: String,
    /**
     * Base address of Synapse's HTTP listener without a path, e.g. https://chat.example.org or
     * http://192.168.1.20:8008 (the LAN address, when /_synapse/admin is only open on the LAN).
     */
    val url: String,
    /** The admin account, e.g. @axel:vkhub.org. */
    val userId: String,
    /** The device of SynapseCtrl's access token; empty when Synapse didn't say. */
    val deviceId: String = "",
    /** SynapseCtrl made the token by logging in (rather than the user pasting one). */
    val tokenFromLogin: Boolean = true,
    /** SHA-256 fingerprint of the pinned self-signed certificate; empty when the certificate is trusted normally. */
    val certPin: String = "",
    /** The user allowed plain HTTP for this server (only possible for addresses on their own network). */
    val allowHttp: Boolean = false,
    /** Hide every action that changes something on this server, and refuse to send one. */
    val readOnly: Boolean = false,
    /** Last seen Synapse version, e.g. 1.163.2. */
    val version: String = "",
) {
    val parsedUrl: HttpUrl? get() = url.toHttpUrlOrNull()

    val host: String get() = parsedUrl?.host ?: url

    val isHttp: Boolean get() = parsedUrl?.isHttps == false

    /** The Matrix server name from the user ID (vkhub.org); empty if the ID has none. */
    val serverName: String get() = userId.substringAfter(':', "")

    /**
     * The letter shown in the profile picture: the first letter of the name, or null when the name
     * doesn't start with one (like an IP address), and an icon is shown instead.
     */
    val initial: String? get() = name.trim().firstOrNull()?.takeIf { it.isLetter() }?.uppercase()

    /** The address as shown to the user: no https://, but http:// stays visible as a warning. */
    val displayUrl: String get() = url.removePrefix("https://")
}

/** The access token for one server. Persisted encrypted by [ServerStore]. */
data class ServerSecrets(val token: String = "") {
    /** Never prints the token, should this object end up in a log or error message. */
    override fun toString(): String = "ServerSecrets(redacted)"
}

/** Why an address can't be used. */
enum class AddressProblem { INVALID, HTTP_PUBLIC }

object ServerAddress {

    class Result(val url: HttpUrl?, val problem: AddressProblem?)

    /**
     * Turns what the user typed into a base URL. No scheme means HTTPS. A port is kept when typed,
     * otherwise the standard one (443, or 80 for http://). Paths are dropped: Synapse's APIs
     * live at /_matrix/ and /_synapse/ on the root of its address.
     *
     * Plain http:// is only accepted for addresses on the user's own network (see [isPrivateHost]);
     * whether it's actually used is a separate, explicit per-server switch.
     */
    fun parse(input: String): Result {
        val text = input.trim().trimEnd('/')
        if (text.isEmpty()) return Result(null, AddressProblem.INVALID)
        val http = text.startsWith("http://", ignoreCase = true)
        val hasScheme = http || text.startsWith("https://", ignoreCase = true)
        val withScheme = if (hasScheme) text else "https://$text"
        val parsed = withScheme.toHttpUrlOrNull() ?: return Result(null, AddressProblem.INVALID)
        if (parsed.host.isBlank()) return Result(null, AddressProblem.INVALID)
        val url = HttpUrl.Builder().scheme(if (http) "http" else "https").host(parsed.host).port(parsed.port).build()
        if (http && !isPrivateHost(url.host)) return Result(url, AddressProblem.HTTP_PUBLIC)
        return Result(url, null)
    }

    /** The base URL as stored: no trailing slash. */
    fun base(url: HttpUrl): String = url.toString().trimEnd('/')

    /** scheme://host:port, the unit the host allowlist works on. */
    fun origin(url: HttpUrl): String = "${url.scheme}://${url.host.lowercase()}:${url.port}"

    private val LOCAL_SUFFIXES = listOf(".lan", ".local", ".home.arpa", ".internal", ".ts.net")

    /**
     * True for addresses that can only be on the user's own network or tailnet: private and
     * link-local IPv4 (10/8, 172.16/12, 192.168/16, 169.254/16), Tailscale's 100.64/10, unique-local
     * and link-local IPv6 (fc00::/7, which includes Tailscale's fd7a:115c:a1e0::/48, and fe80::/10),
     * single-label names and the .lan, .local, .home.arpa, .internal and Tailscale MagicDNS .ts.net
     * names. A host name must also resolve to a private address before plain HTTP is used
     * (see [isPrivateAddress]).
     */
    fun isPrivateHost(host: String): Boolean {
        val h = host.lowercase().trim('[', ']')
        ipLiteral(h)?.let { return isPrivateAddress(it) }
        if (!h.contains('.')) return h.isNotEmpty()
        return LOCAL_SUFFIXES.any { h.endsWith(it) }
    }

    fun isPrivateAddress(addr: InetAddress): Boolean {
        val b = addr.address
        if (addr is Inet6Address) {
            val first = b[0].toInt() and 0xFF
            val second = b[1].toInt() and 0xFF
            // fc00::/7 (unique-local, which includes Tailscale's fd7a:115c:a1e0::/48) and fe80::/10.
            return (first and 0xFE) == 0xFC || (first == 0xFE && (second and 0xC0) == 0x80)
        }
        if (b.size != 4) return false
        val a0 = b[0].toInt() and 0xFF
        val a1 = b[1].toInt() and 0xFF
        return a0 == 10 ||
            (a0 == 172 && a1 in 16..31) ||
            (a0 == 192 && a1 == 168) ||
            (a0 == 169 && a1 == 254) ||
            // 100.64.0.0/10: carrier-grade NAT space, where Tailscale gives every device its address.
            (a0 == 100 && a1 in 64..127)
    }

    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    /** An IP literal parsed without any DNS lookup, or null for a host name. */
    private fun ipLiteral(h: String): InetAddress? = when {
        IPV4.matches(h) -> runCatching { InetAddress.getByAddress(h.split('.').map { it.toInt().also { n -> require(n in 0..255) }.toByte() }.toByteArray()) }.getOrNull()
        h.contains(':') -> runCatching { InetAddress.getByName(h) }.getOrNull()
        else -> null
    }
}
