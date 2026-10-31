package com.vknxworks.synapsectrl

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vknxworks.synapsectrl.data.api.WriteGuard
import com.vknxworks.synapsectrl.data.lock.LockVault
import com.vknxworks.synapsectrl.data.notify.CheckStore
import com.vknxworks.synapsectrl.data.notify.Notifier
import com.vknxworks.synapsectrl.data.notify.NotifySettingsRepository
import com.vknxworks.synapsectrl.data.notify.NotifyWorker
import com.vknxworks.synapsectrl.data.notify.Push
import com.vknxworks.synapsectrl.data.notify.PushStatus
import com.vknxworks.synapsectrl.ui.lock.AppLock
import com.vknxworks.synapsectrl.data.server.HostAllowlist
import com.vknxworks.synapsectrl.data.health.HealthChecker
import com.vknxworks.synapsectrl.data.overview.OverviewRepository
import com.vknxworks.synapsectrl.data.widget.WidgetData
import com.vknxworks.synapsectrl.data.widget.WidgetRefreshWorker
import com.vknxworks.synapsectrl.data.widget.WidgetStore
import com.vknxworks.synapsectrl.ui.widgets.StatusWidget
import com.vknxworks.synapsectrl.data.server.ServerStore
import com.vknxworks.synapsectrl.data.server.Sessions
import com.vknxworks.synapsectrl.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** A launcher shortcut, waiting for the main screen (and the app lock) to handle it. */
enum class ShortcutAction { INVITE, USERS, REPORTS }

/** Manual dependency container; one per process. */
class AppContainer(val context: Context) {
    /**
     * Fire-and-forget work that outlives a screen (saving a setting, a refresh after a change, a push
     * from ntfy). A failure there (storage, network, a server answering oddly) must never crash SynapseCtrl,
     * least of all while it's being closed, so it ends that job only. Nothing is logged.
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })

    val settings = SettingsRepository(context, appScope)

    /** Background checks and what they notify about. */
    val notifySettings = NotifySettingsRepository(context, appScope)

    /** The app lock's PIN, fingerprint key and the key that seals the saved access tokens while locked. */
    val vault = LockVault(context, "synapsectrl")

    /** The app lock is locked: the access tokens are sealed and requests wait. Locked from the start when the lock is on. */
    val locked = MutableStateFlow(vault.enabled)

    val serverStore = ServerStore(context, vault).apply {
        // Background checks sign in while SynapseCtrl is locked, so with them on the access tokens stay
        // encrypted with the Keystore only (as without the app lock).
        sealPolicy = { sealing() }
    }

    /** Passwords are sealed with the app lock: the lock is on and background checks are off. */
    fun sealing(): Boolean = vault.enabled && !notifySettings.pollingNow()

    /** The saved servers (and the one being set up) are the only hosts SynapseCtrl may connect to. */
    val allowlist = HostAllowlist(serverStore)

    /**
     * Shared HTTP client. Each server gets a copy with its own certificate pin (see httpFor), so a
     * pinned certificate is only ever accepted for the server it belongs to. Redirects are not
     * followed: Synapse's API never needs them, and following one could send the access token to
     * another host. No cookies are kept and nothing is cached on disk.
     */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(allowlist.interceptor)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "SynapseCtrl/${BuildConfig.VERSION_NAME} (Android)")
                    .build(),
            )
        }
        .build()

    val sessions = Sessions(serverStore, http, appScope, locked)

    /** Health checks against the public discovery endpoints; their own HTTP client, never with the token. */
    val health = HealthChecker()
    val overview = OverviewRepository(sessions, health, appScope) { server, summary -> WidgetData.fromSummary(context, server, summary) }

    /** Set by MainActivity when a launcher shortcut opened SynapseCtrl; the main screen handles it after unlocking. */
    val pendingShortcut = MutableStateFlow<ShortcutAction?>(null)

    /** Turns the app lock on with [pin]: every saved password is sealed with it from now on. */
    suspend fun enableLock(pin: String) {
        // Unlocked first, so the lock screen doesn't flash up while the PIN is being saved.
        AppLock.markUnlocked()
        withContext(Dispatchers.Default) {
            vault.setUp(pin)
            serverStore.applySealing(sealing())
        }
        locked.value = false
    }

    /** Turns the app lock off (after the PIN was confirmed): access tokens go back to Keystore-only encryption. */
    suspend fun disableLock() {
        withContext(Dispatchers.Default) { serverStore.applySealing(false) }
        vault.disable()
        locked.value = false
        settings.update { it.copy(lockChanges = false) }
    }

    /** "Forgot PIN?": the sealed access tokens can't be read any more, so remove every server and start over. */
    suspend fun forgetEverything() {
        withContext(Dispatchers.IO) {
            serverStore.removeAll()
            CheckStore.forgetAll(context)
            WidgetStore.clear(context)
        }
        Notifier.cancelAll(context)
        sessions.forgetSecrets()
        vault.disable()
        AppLock.markUnlocked()
        locked.value = false
        settings.update { it.copy(lockChanges = false) }
        StatusWidget.redraw(context)
    }

    /** Locked: wipe the key and every decrypted password from memory; requests wait until unlocked. */
    fun onLocked() {
        vault.close()
        if (vault.enabled) {
            locked.value = true
            sessions.forgetSecrets()
        }
        // With sealed tokens the widget now only says that SynapseCtrl is locked.
        StatusWidget.redraw(context)
    }

    fun onUnlocked() {
        AppLock.markUnlocked()
        locked.value = false
        // Seal any password left unsealed (SynapseCtrl stopped between setting up the lock and sealing them),
        // and match background checks turned on or off since.
        if (vault.enabled) appScope.launch { runCatching { serverStore.applySealing(sealing()) } }
        if (StatusWidget.anyPlaced(context)) {
            StatusWidget.redraw(context)
            WidgetRefreshWorker.enqueue(context)
        }
    }

    /** A server was removed: its notifications and check state go too. */
    fun forgetServer(id: String) {
        overview.forget(id)
        CheckStore.forget(context, id)
        Notifier.cancelServer(context, id)
        if (StatusWidget.anyPlaced(context)) WidgetRefreshWorker.enqueue(context)
    }

    init {
        AppLock.onLocked = { onLocked() }
        // Background checks follow their settings; turning them on or off unseals or seals the
        // access tokens again (only possible while unlocked, where the settings are).
        appScope.launch {
            val first = notifySettings.current()
            NotifyWorker.schedule(context, first)
            // Push through ntfy: register again at every start (same address), register when turned on, drop
            // it when turned off. It follows its own switch only, so pausing background checks keeps the address.
            Push.start(context, first.push)
            launch {
                notifySettings.settings.drop(1).map { it.push }.distinctUntilChanged().collect { on ->
                    when {
                        on -> Push.register(context)
                        Push.status.value !is PushStatus.Off -> Push.unregister(context)
                    }
                }
            }
            notifySettings.settings.drop(1).map { it.polling to it.intervalMinutes }.distinctUntilChanged().collect { (polling, _) ->
                NotifyWorker.schedule(context, notifySettings.current())
                if (vault.isOpen) withContext(Dispatchers.Default) { serverStore.applySealing(vault.enabled && !polling) }
            }
        }
        // "Confirm changes": every request that could change something asks for the fingerprint or PIN first.
        WriteGuard.check = { AppLock.confirmChange(vault.enabled && settings.settings.value.lockChanges) }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                AppLock.onForeground(vault.enabled, settings.settings.value.lockTimeout.millis)
            }

            override fun onStop(owner: LifecycleOwner) {
                AppLock.onBackground(vault.enabled, settings.settings.value.lockTimeout.millis)
            }
        })
    }
}

val Context.container: AppContainer
    get() = (applicationContext as SynapseCtrlApp).container
