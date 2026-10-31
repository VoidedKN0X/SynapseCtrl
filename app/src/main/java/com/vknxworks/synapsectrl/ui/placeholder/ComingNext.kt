package com.vknxworks.synapsectrl.ui.placeholder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.ui.components.ConnectionBanner
import com.vknxworks.synapsectrl.ui.components.EmptyState
import com.vknxworks.synapsectrl.ui.components.LocalBottomClearance
import com.vknxworks.synapsectrl.ui.components.TabTitle

/** A tab that arrives in a later version: its title, and what it will do. */
@Composable
fun ComingNextTab(title: String, icon: ImageVector, body: String) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(title)
        ConnectionBanner()
        EmptyState(
            icon = icon,
            title = stringResource(R.string.coming_next),
            body = body,
            modifier = Modifier.padding(bottom = LocalBottomClearance.current),
        )
    }
}
