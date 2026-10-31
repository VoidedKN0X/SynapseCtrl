package com.vknxworks.synapsectrl.ui.components

import android.content.Context
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.data.api.ApiError

/** A message for [e] the user can act on, from strings.xml. [host] names the server in the message. */
fun errorMessage(context: Context, e: Throwable, host: String): String {
    val base = when (e) {
        is ApiError.Unreachable -> context.getString(R.string.error_unreachable, host)
        is ApiError.UntrustedCertificate -> context.getString(R.string.error_untrusted_cert, host)
        is ApiError.PinMismatch -> context.getString(R.string.error_pin_mismatch, host)
        is ApiError.BadLogin -> context.getString(R.string.error_bad_login)
        is ApiError.Unauthorized -> context.getString(R.string.error_token)
        is ApiError.NotAdmin -> context.getString(R.string.error_not_admin)
        is ApiError.AdminApiUnavailable -> context.getString(R.string.error_admin_api, host, e.status)
        is ApiError.NoPasswordLogin -> context.getString(R.string.error_no_password_login, host)
        is ApiError.Forbidden -> context.getString(R.string.error_forbidden)
        is ApiError.NotSynapse -> context.getString(R.string.error_not_synapse, host)
        is ApiError.NotFound -> context.getString(R.string.error_not_found)
        is ApiError.RateLimited -> context.getString(R.string.error_rate_limited)
        is ApiError.Redirect -> if (e.location.isNullOrBlank()) context.getString(R.string.error_redirect, host)
        else context.getString(R.string.error_redirect_to, host, e.location)
        is ApiError.CleartextRefused -> context.getString(R.string.error_cleartext, host)
        is ApiError.BlockedHost -> context.getString(R.string.error_blocked_host)
        is ApiError.ReadOnly -> context.getString(R.string.error_read_only)
        is ApiError.Cancelled -> context.getString(R.string.error_cancelled)
        is ApiError.Server -> context.getString(R.string.error_server, e.code)
        else -> context.getString(R.string.error_generic)
    }
    // Synapse's own wording helps for refused and failed requests; network errors' detail is noise.
    val detail = when (e) {
        is ApiError.Forbidden, is ApiError.Server, is ApiError.BadLogin -> e.detail
        else -> null
    }
    return if (detail.isNullOrBlank()) base else context.getString(R.string.error_with_detail, base, detail)
}
