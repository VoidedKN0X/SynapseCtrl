package com.vknxworks.synapsectrl.ui.setup

import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import com.vknxworks.synapsectrl.ui.theme.CtrlColors
import com.vknxworks.synapsectrl.ui.components.CtrlWordmark
import androidx.compose.foundation.layout.Arrangement
import com.vknxworks.synapsectrl.ui.components.CtrlTextField
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Http
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.container
import com.vknxworks.synapsectrl.data.api.SynapseClient
import com.vknxworks.synapsectrl.ui.components.CtrlFilterChip
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Hub
import com.vknxworks.synapsectrl.data.server.LogoutResult
import com.vknxworks.synapsectrl.data.api.ApiError
import com.vknxworks.synapsectrl.data.api.ServerStatus
import com.vknxworks.synapsectrl.data.server.AddressProblem
import com.vknxworks.synapsectrl.data.server.Server
import com.vknxworks.synapsectrl.data.server.ServerAddress
import com.vknxworks.synapsectrl.data.server.ServerSecrets
import com.vknxworks.synapsectrl.ui.components.Group
import com.vknxworks.synapsectrl.ui.components.GroupRow
import com.vknxworks.synapsectrl.ui.components.GroupSwitch
import com.vknxworks.synapsectrl.ui.components.PillButton
import com.vknxworks.synapsectrl.ui.components.ScreenTopBar
import com.vknxworks.synapsectrl.ui.components.SecureScreen
import com.vknxworks.synapsectrl.ui.components.errorMessage
import com.vknxworks.synapsectrl.ui.theme.Gutter
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

/** Everything learned from a successful connection, shown before the server is saved. */
private class Connected(val server: Server, val secrets: ServerSecrets, val status: ServerStatus)

private class CertPrompt(val cert: CertInfo, val changed: Boolean)

/** How the admin account signs in. */
private enum class SignIn { PASSWORD, TOKEN }

/**
 * Adds a Synapse server, or changes the address or sign-in of an existing one ([editId]).
 *
 * 1. GET /_matrix/client/v3/login (no credentials): shows whether the address is Synapse and offers
 *    password login. An untrusted certificate is shown by its SHA-256 fingerprint to confirm and pin
 *    first; nothing secret has been sent at that point.
 * 2. Password: POST /_matrix/client/v3/login makes a new "SynapseCtrl (Android)" device and access
 *    token; the password is never stored. Or the user pastes an access token.
 * 3. whoami, then GET /_synapse/admin/v1/users/<me>/admin: the account must be a server admin. A token
 *    SynapseCtrl just made for an account that isn't one is logged out again straight away.
 * 4. The result is shown, and the server is saved on Continue.
 *
 * Only the address typed here is ever used for this server; there is no fallback address.
 */
@Composable
fun SetupScreen(editId: String? = null, onDone: () -> Unit, onCancel: (() -> Unit)?) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val existing = remember(editId) { editId?.let { id -> c.sessions.servers.value.firstOrNull { it.id == id } } }
    val existingSecrets = remember(editId) { editId?.let { c.sessions.secrets(it) } }

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var address by remember { mutableStateOf(existing?.url.orEmpty()) }
    var signIn by remember { mutableStateOf(SignIn.PASSWORD) }
    var username by remember { mutableStateOf(existing?.userId.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var allowHttp by remember { mutableStateOf(existing?.allowHttp ?: false) }
    var httpPrompt by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var certPrompt by remember { mutableStateOf<CertPrompt?>(null) }
    var connected by remember { mutableStateOf<Connected?>(null) }
    var readOnly by remember { mutableStateOf(existing?.readOnly ?: false) }

    // A certificate the user trusted in this session, or the one already pinned for this server.
    var pinnedHost by remember { mutableStateOf(existing?.host) }
    var pin by remember { mutableStateOf(existing?.certPin.orEmpty()) }

    val parsed = ServerAddress.parse(address)
    val resolved: HttpUrl? = parsed.url?.takeIf { parsed.problem == null }
    val isHttp = resolved?.isHttps == false
    val addressError = when {
        address.isBlank() -> null
        parsed.problem == AddressProblem.HTTP_PUBLIC -> stringResource(R.string.address_http_public)
        parsed.problem == AddressProblem.INVALID -> stringResource(R.string.address_invalid)
        else -> null
    }
    // The saved token is only ever sent to the address it was saved for. A changed address needs a new
    // sign-in, so an edited address can't receive the old token.
    val canReuseToken = existing != null && existingSecrets?.token?.isNotEmpty() == true &&
        resolved != null && ServerAddress.base(resolved) == existing.url
    val typedCredentials = when (signIn) {
        SignIn.PASSWORD -> username.isNotBlank() && password.isNotEmpty()
        SignIn.TOKEN -> token.isNotBlank()
    }
    val canConnect = !busy && resolved != null && (typedCredentials || canReuseToken) && (!isHttp || allowHttp)

    fun pinFor(url: HttpUrl): String = if (url.isHttps && pinnedHost.equals(url.host, ignoreCase = true)) pin else ""

    // Server setup is the one place an address that isn't saved yet may be contacted; never after leaving it.
    DisposableEffect(Unit) { onDispose { c.allowlist.endSetup() } }

    fun connect() {
        val url = resolved ?: return
        val base = ServerAddress.base(url)
        busy = true
        error = null
        val draft = Server(
            id = existing?.id.orEmpty(),
            name = name.trim(),
            url = base,
            userId = existing?.userId.orEmpty(),
            deviceId = existing?.deviceId.orEmpty(),
            tokenFromLogin = existing?.tokenFromLogin ?: true,
            certPin = pinFor(url),
            allowHttp = isHttp && allowHttp,
            readOnly = readOnly,
        )
        c.allowlist.beginSetup(url, draft.allowHttp)
        scope.launch {
            // A token made here that turned out unusable (not an admin) is logged out again.
            var madeToken: ServerSecrets? = null
            try {
                // No credentials yet: is this Synapse, and is the certificate trusted?
                val flows = SynapseClient(draft, c.http, ServerSecrets()).loginFlows()
                val (secrets, fromLogin) = when {
                    signIn == SignIn.PASSWORD && username.isNotBlank() && password.isNotEmpty() -> {
                        if (!flows.password) throw ApiError.NoPasswordLogin()
                        val login = SynapseClient(draft, c.http, ServerSecrets())
                            .login(username.trim(), password, context.getString(R.string.device_name))
                        password = ""
                        ServerSecrets(login.accessToken).also { madeToken = it } to true
                    }
                    signIn == SignIn.TOKEN && token.isNotBlank() -> ServerSecrets(token.trim()) to false
                    canReuseToken -> ServerSecrets(existingSecrets?.token.orEmpty()) to draft.tokenFromLogin
                    else -> throw ApiError.Unauthorized(null, "M_MISSING_TOKEN")
                }
                val status = SynapseClient(draft, c.http, secrets).status()
                madeToken = null
                connected = Connected(
                    server = draft.copy(
                        name = name.trim().ifBlank { status.serverName.ifBlank { url.host } },
                        userId = status.userId,
                        deviceId = status.deviceId,
                        tokenFromLogin = fromLogin,
                        version = status.version,
                    ),
                    secrets = secrets,
                    status = status,
                )
            } catch (e: ApiError.UntrustedCertificate) {
                askToTrust(url, changed = false, onCert = { certPrompt = it }, onFail = { error = errorMessage(context, it, url.host) })
            } catch (e: ApiError.PinMismatch) {
                askToTrust(url, changed = true, onCert = { certPrompt = it }, onFail = { error = errorMessage(context, it, url.host) })
            } catch (e: Exception) {
                error = errorMessage(context, e, url.host)
            } finally {
                madeToken?.let { t -> runCatching { SynapseClient(draft, c.http, t).logout() } }
                busy = false
            }
        }
    }

    fun save() {
        val result = connected ?: return
        val oldToken = existingSecrets?.token.orEmpty()
        val oldServer = existing
        c.sessions.add(result.server.copy(readOnly = readOnly), result.secrets)
        c.allowlist.endSetup()
        // Signed in again with a new token: log the old one out, so it stops working on the server too.
        if (oldServer != null && oldToken.isNotEmpty() && oldToken != result.secrets.token && oldServer.tokenFromLogin) {
            c.appScope.launch { runCatching { c.sessions.logoutToken(oldServer, ServerSecrets(oldToken)) } }
        }
        onDone()
    }

    // The password and token are typed here: never in screenshots or Recents.
    SecureScreen()

    val result = connected
    Column(Modifier.fillMaxSize()) {
        if (onCancel != null) {
            ScreenTopBar(stringResource(if (editId != null) R.string.edit_server_title else R.string.add_server))
        } else {
            Spacer(Modifier.statusBarsPadding())
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            if (result == null) {
                if (onCancel == null) {
                    Column(Modifier.padding(horizontal = Gutter, vertical = 16.dp)) {
                        CtrlWordmark()
                        Spacer(Modifier.height(28.dp))
                        Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(CtrlColors.Accent), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Hub, null, Modifier.size(32.dp), tint = CtrlColors.OnAccent)
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CtrlTextField(
                        value = address,
                        onValueChange = { address = it.trim(); error = null; connected = null },
                        label = { Text(stringResource(R.string.field_address)) },
                        placeholder = { Text(stringResource(R.string.field_address_hint)) },
                        singleLine = true,
                        isError = addressError != null,
                        supportingText = {
                            when {
                                addressError != null -> Text(addressError)
                                resolved != null -> Text(stringResource(R.string.address_resolves, ServerAddress.base(resolved)))
                                else -> Text(stringResource(R.string.address_only_this))
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CtrlTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.field_name)) },
                        placeholder = { Text(stringResource(R.string.field_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CtrlFilterChip(signIn == SignIn.PASSWORD, { signIn = SignIn.PASSWORD; error = null }, { Text(stringResource(R.string.signin_password)) })
                        CtrlFilterChip(signIn == SignIn.TOKEN, { signIn = SignIn.TOKEN; error = null }, { Text(stringResource(R.string.signin_token)) })
                    }
                    if (signIn == SignIn.PASSWORD) {
                        CtrlTextField(
                            value = username,
                            onValueChange = { username = it.trim() },
                            label = { Text(stringResource(R.string.field_username)) },
                            placeholder = { Text(stringResource(R.string.field_username_hint)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        CtrlTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.field_password)) },
                            supportingText = {
                                Text(stringResource(if (canReuseToken) R.string.password_keep_hint else R.string.password_not_stored))
                            },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        stringResource(if (showPassword) R.string.hide_password else R.string.show_password),
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        CtrlTextField(
                            value = token,
                            onValueChange = { token = it.trim() },
                            label = { Text(stringResource(R.string.field_token)) },
                            supportingText = { Text(stringResource(R.string.token_hint)) },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        stringResource(if (showPassword) R.string.hide_password else R.string.show_password),
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (isHttp) {
                    Group(footer = stringResource(R.string.http_switch_footer)) {
                        GroupSwitch(
                            stringResource(R.string.http_switch),
                            stringResource(R.string.http_switch_subtitle),
                            checked = allowHttp,
                            icon = Icons.Outlined.Http,
                        ) { on -> if (on) httpPrompt = true else allowHttp = false }
                    }
                }
                Column(Modifier.padding(horizontal = Gutter)) {
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    PillButton(
                        text = stringResource(if (busy) R.string.connecting else R.string.connect),
                        onClick = ::connect,
                        enabled = canConnect,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (busy) CircularProgressIndicator(Modifier.padding(top = 8.dp).size(24.dp), strokeWidth = 2.dp)
                }
            } else {
                ConnectedSummary(result, readOnly, onReadOnly = { readOnly = it })
                PillButton(
                    text = stringResource(if (editId != null) R.string.save_changes else R.string.continue_label),
                    onClick = ::save,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp),
                )
            }
        }
    }

    certPrompt?.let { prompt ->
        CertTrustDialog(
            cert = prompt.cert,
            changed = prompt.changed,
            onTrust = {
                certPrompt = null
                pinnedHost = prompt.cert.host
                pin = prompt.cert.fingerprint
                connect()
            },
            onCancel = { certPrompt = null },
        )
    }
    if (httpPrompt) {
        HttpWarningDialog(
            host = resolved?.host.orEmpty(),
            onAllow = { allowHttp = true; httpPrompt = false },
            onCancel = { httpPrompt = false },
        )
    }
}

/** Reads the certificate the server presents and asks the user to confirm it. */
private suspend fun askToTrust(url: HttpUrl, changed: Boolean, onCert: (CertPrompt) -> Unit, onFail: (ApiError) -> Unit) {
    try {
        onCert(CertPrompt(fetchCertInfo(url), changed))
    } catch (e: java.io.IOException) {
        onFail(ApiError.fromIo(e))
    } catch (e: Exception) {
        onFail(ApiError.UntrustedCertificate(e))
    }
}

@Composable
private fun ConnectedSummary(result: Connected, readOnly: Boolean, onReadOnly: (Boolean) -> Unit) {
    val server = result.server
    val status = result.status
    Column(Modifier.padding(horizontal = Gutter, vertical = 16.dp)) {
        Icon(Icons.Outlined.Dns, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.connected_to, server.name), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.synapse_version, status.version),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Group {
        GroupRow(
            status.userId,
            stringResource(R.string.admin_confirmed),
            icon = Icons.Outlined.AdminPanelSettings,
        )
        GroupRow(
            server.displayUrl,
            stringResource(
                when {
                    server.isHttp -> R.string.conn_http
                    server.certPin.isNotBlank() -> R.string.cert_pinned
                    else -> R.string.cert_system
                },
            ),
            icon = if (server.isHttp) Icons.Outlined.Http else Icons.Outlined.Lock,
        )
    }
    Group(footer = stringResource(R.string.read_only_mode_footer)) {
        GroupSwitch(
            stringResource(R.string.read_only_mode),
            stringResource(R.string.read_only_mode_subtitle),
            checked = readOnly,
            onChange = onReadOnly,
        )
    }
}
