package com.vknxworks.synapsectrl.ui.common

import android.content.Context
import android.graphics.BitmapFactory
import android.text.format.DateFormat
import android.text.format.Formatter
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.container
import com.vknxworks.synapsectrl.data.api.Api
import com.vknxworks.synapsectrl.data.api.ApiError
import com.vknxworks.synapsectrl.ui.components.CtrlAlertDialog
import com.vknxworks.synapsectrl.ui.components.CtrlCard
import com.vknxworks.synapsectrl.ui.components.CtrlFilterChip
import com.vknxworks.synapsectrl.ui.components.CtrlLabel
import com.vknxworks.synapsectrl.ui.components.CtrlTextButton
import com.vknxworks.synapsectrl.ui.components.CtrlTextField
import com.vknxworks.synapsectrl.ui.components.errorMessage
import com.vknxworks.synapsectrl.ui.theme.CtrlColors
import com.vknxworks.synapsectrl.ui.theme.CtrlType
import com.vknxworks.synapsectrl.ui.theme.Gutter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Date

// ---------- Formatting ----------

/** 1.2 GB, with the phone's locale. */
fun formatBytes(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes.coerceAtLeast(0))

/** Date and time in the phone's format, e.g. 31/10/2026 14:02. */
fun formatDateTime(context: Context, ms: Long): String =
    "${DateFormat.getDateFormat(context).format(Date(ms))} ${DateFormat.getTimeFormat(context).format(Date(ms))}"

fun formatDate(context: Context, ms: Long): String = DateFormat.getDateFormat(context).format(Date(ms))

/** The localpart of a user ID: @axel:vkhub.org → axel. */
fun localpart(userId: String): String = userId.removePrefix("@").substringBefore(':')

/** A full user ID from what was typed: "axel" or "@axel:vkhub.org", on [serverName]. */
fun fullUserId(typed: String, serverName: String): String {
    val t = typed.trim()
    return when {
        t.startsWith("@") && t.contains(':') -> t
        t.startsWith("@") -> "$t:$serverName"
        else -> "@$t:$serverName"
    }
}

/** A strong random password: 20 characters from letters, digits and a few symbols. */
fun strongPassword(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789-_.!@#%"
    val rnd = java.security.SecureRandom()
    return (1..20).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
}

// ---------- Running actions ----------

/**
 * Runs a change on the server for a screen: one at a time ([busy]), shows [success] in the snackbar
 * when it worked, and Synapse's error (in words the user can act on) when it didn't.
 */
class ScreenActions(
    val snackbar: SnackbarHostState,
    private val scope: CoroutineScope,
    private val context: Context,
    private val host: () -> String,
    private val reportError: (ApiError) -> Unit,
) {
    var busy by mutableStateOf(false)
        private set

    fun run(success: String?, after: suspend () -> Unit = {}, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
                if (success != null) snackbar.showSnackbar(success)
                after()
            } catch (e: ApiError) {
                reportError(e)
                snackbar.showSnackbar(errorMessage(context, e, host()))
            } catch (e: Exception) {
                snackbar.showSnackbar(errorMessage(context, e, host()))
            } finally {
                busy = false
            }
        }
    }

    /** Shows [message] in the snackbar. */
    fun say(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }
}

@Composable
fun rememberScreenActions(snackbar: SnackbarHostState = remember { SnackbarHostState() }): ScreenActions {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    return remember(snackbar) {
        ScreenActions(
            snackbar, scope, context,
            host = { c.sessions.active.value?.server?.host.orEmpty() },
            reportError = { e -> c.sessions.active.value?.server?.id?.let { c.sessions.reportError(it, e) } },
        )
    }
}

// ---------- Search and filters ----------

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    CtrlTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = if (value.isNotEmpty()) {
            { IconButton(onClick = { onChange("") }) { Icon(Icons.Outlined.Close, stringResource(R.string.clear_search)) } }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
        modifier = modifier.fillMaxWidth().padding(horizontal = Gutter),
    )
}

/** A row of filter chips that scrolls sideways. */
@Composable
fun <T> FilterRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) -> CtrlFilterChip(value == selected, { onSelect(value) }, { Text(label) }) }
    }
}

// ---------- Avatars ----------

/**
 * A Matrix server name (host, IPv4, [IPv6], optional port) and a media ID ([A-Za-z0-9_-], per the spec).
 * Host labels are dot-separated and never empty, so "." and ".." (which OkHttp resolves as path steps) can't match.
 */
private val MXC_SERVER = Regex("""^(\[[0-9A-Fa-f:.]+]|[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.?)(:[0-9]{1,5})?$""")
private val MXC_MEDIA_ID = Regex("""^[A-Za-z0-9_-]+$""")

/** Thumbnails already loaded, by mxc:// address, for this run of the app only (never written to disk). */
private object AvatarCache {
    val cache = LruCache<String, ImageBitmap>(200)
    val missing = HashSet<String>()
}

/**
 * A rounded avatar: the mxc:// picture when there is one (a 96 px thumbnail through the authenticated
 * media API, with the same token, certificate pin and address as every other request), otherwise
 * the first letter of [label].
 */
@Composable
fun Avatar(mxc: String?, label: String, size: Dp = 40.dp) {
    val c = LocalContext.current.container
    val bitmap by produceState<ImageBitmap?>(initialValue = mxc?.let { AvatarCache.cache.get(it) }, mxc) {
        val uri = mxc?.takeIf { it.startsWith("mxc://") } ?: return@produceState
        AvatarCache.cache.get(uri)?.let { value = it; return@produceState }
        if (uri in AvatarCache.missing) return@produceState
        val (server, mediaId) = uri.removePrefix("mxc://").split('/', limit = 2).let { if (it.size == 2) it[0] to it[1] else return@produceState }
        // The address comes from profiles and room state other people set: only a real server name and media ID
        // (no "..", "/" or other path tricks) is ever put into a request that carries the admin token.
        if (!MXC_SERVER.matches(server) || !MXC_MEDIA_ID.matches(mediaId)) return@produceState
        val client = c.sessions.active.value?.client ?: return@produceState
        val bytes = client.bytes(Api.Media, listOf("thumbnail", server, mediaId), mapOf("width" to "96", "height" to "96", "method" to "crop"))
        val bmp = bytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() }
        if (bmp != null) {
            AvatarCache.cache.put(uri, bmp)
            value = bmp
        } else {
            AvatarCache.missing += uri
        }
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(CtrlColors.Raised),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                label.trim().removePrefix("@").removePrefix("#").removePrefix("!").firstOrNull()?.uppercase() ?: "?",
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.SemiBold,
                color = CtrlColors.AccentText,
            )
        }
    }
}

// ---------- Small pieces ----------

/** A short status tag: ADMIN, DEACTIVATED, SHADOW-BANNED. */
@Composable
fun Tag(text: String, color: androidx.compose.ui.graphics.Color = CtrlColors.AccentText) {
    Text(
        text.uppercase(),
        style = CtrlType.Status,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CtrlColors.Raised)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** A number with a label, for the Overview and summaries. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null) {
    CtrlCard(modifier, contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CtrlLabel(label)
        Text(value, style = CtrlType.Metric, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A centred spinner while a screen loads for the first time. */
@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
    }
}

/** An error in a list, with what went wrong and a retry. */
@Composable
fun ErrorBlock(error: Throwable, onRetry: () -> Unit) {
    val context = LocalContext.current
    val host = context.container.sessions.active.value?.server?.host.orEmpty()
    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(errorMessage(context, error, host), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        CtrlTextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

/**
 * The confirmation for actions that can't be undone: says what will happen, and only allows the
 * action once [expected] (a user or room ID) is typed exactly.
 */
@Composable
fun TypedConfirmDialog(
    title: String,
    body: String,
    expected: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    var typed by remember { mutableStateOf("") }
    val matches = typed.trim() == expected
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        guard = true,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                extra?.invoke()
                Text(stringResource(R.string.type_to_confirm), style = MaterialTheme.typography.bodySmall)
                Text(expected, style = CtrlType.Data, color = CtrlColors.AccentText)
                CtrlTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            CtrlTextButton(onClick = onConfirm, enabled = matches) {
                Text(confirmLabel, color = if (matches) MaterialTheme.colorScheme.error else CtrlColors.Disabled)
            }
        },
        dismissButton = { CtrlTextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Space at the end of a list so the last row clears the floating tab bar. */
@Composable
fun ListEndSpacer() {
    Spacer(Modifier.height(com.vknxworks.synapsectrl.ui.components.LocalBottomClearance.current + 16.dp))
}
