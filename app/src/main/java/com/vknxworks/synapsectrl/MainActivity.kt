package com.vknxworks.synapsectrl

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.toArgb
import com.vknxworks.synapsectrl.ui.theme.CtrlColors
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.vknxworks.synapsectrl.ui.lock.AppLockOverlay
import com.vknxworks.synapsectrl.ui.lock.lockCovering
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.synapsectrl.ui.components.ScreenPrivacy
import com.vknxworks.synapsectrl.ui.nav.AppRoot
import com.vknxworks.synapsectrl.ui.theme.SynapseCtrlTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Blank Recents card and no screenshots until the settings have been read; the switches below lift it.
        setRecentsScreenshotEnabled(false)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // Edge to edge; the bar icons are set to match the theme below, once it is known.
        enableEdgeToEdge()
        val c = container
        setContent {
            val settings by c.settings.settings.collectAsStateWithLifecycle()
            // "Hide in screenshots": FLAG_SECURE (also blanks the Recents preview). Always on, whatever the
            // switch says, while server setup, the lock screen or a PIN screen is open.
            val secure = settings.hideScreenshots || ScreenPrivacy.forced > 0 || lockCovering(c.vault)
            // "Hide in recent apps": a blank Recents card; screenshots still work. Always with the app lock on,
            // so Recents never shows what's behind the lock.
            val blankRecents = settings.hideRecents || c.vault.enabled
            LaunchedEffect(secure, blankRecents) {
                if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                setRecentsScreenshotEnabled(!blankRecents)
            }
            // Ctrl / Signal follows the device: light status and navigation bar icons on the dark background,
            // dark icons on the light one. The window behind the app gets the theme's background too.
            val dark = isSystemInDarkTheme()
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                window.setBackgroundDrawable(ColorDrawable((if (dark) CtrlColors.Dark else CtrlColors.Light).background.toArgb()))
                onDispose { }
            }
            val covered = lockCovering(c.vault)
            SynapseCtrlTheme(dark) {
                Box(Modifier.fillMaxSize()) {
                    // The app stays underneath (so nothing you typed is lost), hidden from TalkBack too.
                    Box(if (covered) Modifier.fillMaxSize().clearAndSetSemantics { } else Modifier.fillMaxSize()) { AppRoot() }
                    // The app lock: lock screen, PIN screens and "Confirm changes".
                    AppLockOverlay()
                }
            }
        }
    }
}
