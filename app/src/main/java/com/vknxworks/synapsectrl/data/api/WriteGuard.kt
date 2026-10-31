package com.vknxworks.synapsectrl.data.api

/**
 * Asked before every request that could change something on a server (anything but GET), after the
 * read-only check. With the app lock's "Confirm changes" on, [check] asks for the fingerprint or app
 * PIN (set by AppContainer); it returns false when the user cancels, and the request fails with
 * [ApiError.Cancelled] without being sent. By default everything is allowed.
 */
object WriteGuard {
    @Volatile
    var check: suspend () -> Boolean = { true }
}
