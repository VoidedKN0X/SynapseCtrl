package com.vknxworks.synapsectrl.data.server

import android.content.Context
import android.util.Base64
import com.vknxworks.synapsectrl.data.crypto.KeystoreAes
import com.vknxworks.synapsectrl.data.lock.LockVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Saved servers and the active one. Server settings are plain JSON; access tokens are encrypted with an
 * AES-GCM key that lives in the Android Keystore and never leaves it (done directly, without an extra
 * crypto library). With the app lock on, every token is also sealed with the lock's vault key, so
 * it can't be read while SynapseCtrl is locked. Nothing here is backed up or moved to another phone (see
 * data_extraction_rules).
 */
class ServerStore(context: Context, private val vault: LockVault) {
    private val sp = context.getSharedPreferences("servers", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listSerializer = ListSerializer(Server.serializer())

    private val _servers = MutableStateFlow(load())
    val servers: StateFlow<List<Server>> = _servers.asStateFlow()

    private val _activeId = MutableStateFlow(
        sp.getString(KEY_ACTIVE, null)?.takeIf { id -> _servers.value.any { it.id == id } } ?: _servers.value.firstOrNull()?.id,
    )
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    private fun load(): List<Server> = try {
        sp.getString(KEY_SERVERS, null)?.let { json.decodeFromString(listSerializer, it) } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    @Synchronized
    fun save(server: Server) {
        val old = _servers.value
        val list = if (old.any { it.id == server.id }) old.map { if (it.id == server.id) server else it } else old + server
        sp.edit().putString(KEY_SERVERS, json.encodeToString(listSerializer, list)).apply()
        _servers.value = list
    }

    @Synchronized
    fun update(id: String, transform: (Server) -> Server) {
        val current = _servers.value.firstOrNull { it.id == id } ?: return
        save(transform(current))
    }

    @Synchronized
    fun remove(id: String) {
        val list = _servers.value.filterNot { it.id == id }
        sp.edit()
            .putString(KEY_SERVERS, json.encodeToString(listSerializer, list))
            .remove(secretKey(id))
            .apply()
        _servers.value = list
        if (_activeId.value == id) setActive(list.firstOrNull()?.id)
    }

    fun setActive(id: String?) {
        sp.edit().putString(KEY_ACTIVE, id).apply()
        _activeId.value = id
    }

    /** The token is empty while it's sealed and SynapseCtrl is locked. */
    fun secrets(id: String): ServerSecrets = ServerSecrets(token = read(sp.getString(secretKey(id), null)))

    @Synchronized
    fun saveSecrets(id: String, s: ServerSecrets) {
        val seal = sealPolicy()
        // Locked: the key isn't in memory, so nothing can be sealed. Keep what's stored rather than
        // writing a token that isn't sealed (or losing it).
        if (seal && !vault.isOpen) return
        sp.edit().putString(secretKey(id), write(s.token, seal)).commit()
    }

    /**
     * Whether tokens are sealed with the app lock (set by AppContainer): with the lock on, unless
     * background checks are on, because those have to sign in while SynapseCtrl is locked.
     */
    var sealPolicy: () -> Boolean = { vault.enabled }

    /** The server's token is sealed and SynapseCtrl is locked, so nothing can sign in to it right now. */
    fun sealedNow(id: String): Boolean {
        if (vault.isOpen) return false
        return LockVault.isSealed(decrypt(sp.getString(secretKey(id), null)))
    }

    private fun read(stored: String?): String {
        val plain = decrypt(stored)
        return if (LockVault.isSealed(plain)) vault.open(plain).orEmpty() else plain
    }

    /** Keystore-encrypted, and sealed with the app lock's key when [seal]. Null when it can't be sealed. */
    private fun write(plain: String, seal: Boolean): String? {
        if (plain.isEmpty()) return null
        // Never fall back to a token that isn't sealed when it should be.
        return encrypt(if (seal) vault.seal(plain) ?: return null else plain)
    }

    /**
     * Seals or unseals every saved token to match [seal] (the app lock was turned on or off). Only
     * while unlocked; a sealed token that can't be opened stays as it is.
     */
    @Synchronized
    fun applySealing(seal: Boolean) {
        val edit = sp.edit()
        for ((key, value) in sp.all) {
            if (!key.startsWith("secret_") || value !is String) continue
            val plain = decrypt(value)
            val sealed = LockVault.isSealed(plain)
            if (sealed == seal) continue
            val clear = if (sealed) vault.open(plain) ?: continue else plain
            if (clear.isEmpty()) continue
            write(clear, seal)?.let { edit.putString(key, it) }
        }
        edit.commit()
    }

    /** Deletes every saved server and token ("Forgot PIN?"). */
    @Synchronized
    fun removeAll() {
        sp.edit().clear().commit()
        _servers.value = emptyList()
        _activeId.value = null
    }

    private fun secretKey(id: String) = "secret_${id}_token"

    // --- Keystore AES-GCM ---

    /** StrongBox for a new key when the phone has one; an existing key is kept (see [KeystoreAes]). */
    private fun key(): SecretKey = KeystoreAes.key(KEY_ALIAS)

    private fun encrypt(plain: String): String? {
        if (plain.isEmpty()) return null
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key())
            Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray()), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    private fun decrypt(stored: String?): String {
        if (stored.isNullOrEmpty()) return ""
        return try {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
            String(c.doFinal(raw, 12, raw.size - 12))
        } catch (_: Exception) {
            ""
        }
    }

    private companion object {
        const val KEY_SERVERS = "servers"
        const val KEY_ACTIVE = "active"
        const val KEY_ALIAS = "synapsectrl_secrets"
    }
}
