package com.vknxworks.synapsectrl.ui.setup

import com.vknxworks.synapsectrl.ui.theme.JetBrainsMono
import androidx.compose.foundation.background
import com.vknxworks.synapsectrl.ui.components.CtrlAlertDialog
import com.vknxworks.synapsectrl.ui.components.CtrlTextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.data.server.Tls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl

/** What the user is asked to trust: the server's certificate as fetched (nothing was sent to it). */
data class CertInfo(val host: String, val subject: String, val fingerprint: String)

/** Fetches the certificate [url] presents, without trusting it or sending anything to it. */
suspend fun fetchCertInfo(url: HttpUrl): CertInfo = withContext(Dispatchers.IO) {
    val leaf = Tls.fetchLeaf(url.host, url.port)
    CertInfo(url.host, leaf.subjectX500Principal.name, Tls.fingerprint(leaf))
}

/** Asks the user to confirm a self-signed certificate by its SHA-256 fingerprint (trust on first use). */
@Composable
fun CertTrustDialog(cert: CertInfo, changed: Boolean, onTrust: () -> Unit, onCancel: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    CtrlAlertDialog(
        onDismissRequest = onCancel,
        guard = true,
        icon = { Icon(Icons.Outlined.VerifiedUser, null) },
        title = { Text(stringResource(if (changed) R.string.cert_changed_title else R.string.cert_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (changed) {
                    Text(stringResource(R.string.cert_changed_warning, cert.host), color = cs.error)
                } else {
                    Text(stringResource(R.string.cert_intro, cert.host))
                }
                Text(stringResource(R.string.cert_check))
                Text(
                    cert.fingerprint,
                    fontFamily = JetBrainsMono,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(cs.surfaceContainerHighest).padding(10.dp),
                )
                if (cert.subject.isNotBlank()) {
                    Text(stringResource(R.string.cert_issued_to, cert.subject), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Text(stringResource(R.string.cert_pin_note), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        },
        confirmButton = { CtrlTextButton(onTrust) { Text(stringResource(R.string.cert_trust)) } },
        dismissButton = { CtrlTextButton(onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Confirms switching on plain HTTP for one server on the user's own network. */
@Composable
fun HttpWarningDialog(host: String, onAllow: () -> Unit, onCancel: () -> Unit) {
    CtrlAlertDialog(
        onDismissRequest = onCancel,
        guard = true,
        icon = { Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.http_warning_title)) },
        text = { Text(stringResource(R.string.http_warning_body, host)) },
        confirmButton = { CtrlTextButton(onAllow) { Text(stringResource(R.string.http_allow), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { CtrlTextButton(onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}
