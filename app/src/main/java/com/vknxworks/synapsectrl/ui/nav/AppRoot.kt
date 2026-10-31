package com.vknxworks.synapsectrl.ui.nav

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PermMedia
import com.vknxworks.synapsectrl.ShortcutAction
import com.vknxworks.synapsectrl.ui.invites.InvitesScreen
import com.vknxworks.synapsectrl.ui.media.MediaScreen
import com.vknxworks.synapsectrl.ui.media.UserMediaScreen
import com.vknxworks.synapsectrl.ui.notices.ServerNoticeScreen
import com.vknxworks.synapsectrl.ui.reports.FederationScreen
import com.vknxworks.synapsectrl.ui.reports.ReportScreen
import com.vknxworks.synapsectrl.ui.reports.ReportsScreen
import com.vknxworks.synapsectrl.ui.rooms.RoomScreen
import com.vknxworks.synapsectrl.ui.rooms.RoomsScreen
import com.vknxworks.synapsectrl.ui.users.CreateUserScreen
import com.vknxworks.synapsectrl.ui.users.UserScreen
import com.vknxworks.synapsectrl.ui.users.UsersScreen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.ui.components.CtrlBottomBar
import com.vknxworks.synapsectrl.ui.components.LocalBottomClearance
import com.vknxworks.synapsectrl.ui.components.TabItem
import com.vknxworks.synapsectrl.ui.theme.CtrlColors
import com.vknxworks.synapsectrl.ui.components.rememberNotificationAccess
import com.vknxworks.synapsectrl.ui.overview.OverviewScreen
import com.vknxworks.synapsectrl.ui.settings.ServersScreen
import com.vknxworks.synapsectrl.ui.settings.NotificationsScreen
import com.vknxworks.synapsectrl.ui.settings.SettingsScreen
import com.vknxworks.synapsectrl.ui.setup.SetupScreen
import com.vknxworks.synapsectrl.container
import com.vknxworks.synapsectrl.ui.lock.lockCovering
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.toRoute
import com.vknxworks.synapsectrl.ui.settings.YouScreen

@Composable
fun AppRoot() {
    val c = LocalContext.current.container
    val servers by c.sessions.servers.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (servers.isEmpty()) {
            // First start (or every server removed): setup fills the screen, no tabs.
            SetupScreen(onDone = {}, onCancel = null)
        } else {
            MainScaffold()
        }
    }
}

private data class TopDest(
    val route: Any,
    @StringRes val label: Int,
    @StringRes val hint: Int,
    val icon: ImageVector,
)

/** The five tabs. Overview is the only summary screen; account and settings open from the profile picture. */
private val topDestinations = listOf(
    TopDest(OverviewRoute, R.string.tab_overview, R.string.tab_overview_hint, Icons.Outlined.Dashboard),
    TopDest(UsersRoute, R.string.tab_users, R.string.tab_users_hint, Icons.Outlined.Group),
    TopDest(RoomsRoute, R.string.tab_rooms, R.string.tab_rooms_hint, Icons.Outlined.Forum),
    TopDest(MediaRoute, R.string.tab_media, R.string.tab_media_hint, Icons.Outlined.PermMedia),
    TopDest(ReportsRoute, R.string.tab_reports, R.string.tab_reports_hint, Icons.Outlined.Flag),
)

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val navigator = remember(navController) { Navigator(navController) }
    val backStack by navController.currentBackStackEntryAsState()
    val dest = backStack?.destination
    val selectedIndex = topDestinations.indexOfFirst { top -> dest?.hierarchy?.any { it.hasRoute(top.route::class) } == true }
    val tabs = topDestinations.map { TabItem(stringResource(it.label), it.icon) }

    fun selectTab(i: Int) {
        navController.navigate(topDestinations[i].route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Launcher shortcuts: open the right place once SynapseCtrl is unlocked.
    val c = LocalContext.current.container
    val shortcut by c.pendingShortcut.collectAsStateWithLifecycle()
    val covered = lockCovering(c.vault)
    LaunchedEffect(shortcut, covered, backStack != null) {
        val action = shortcut ?: return@LaunchedEffect
        if (covered || backStack == null) return@LaunchedEffect
        c.pendingShortcut.value = null
        when (action) {
            ShortcutAction.USERS -> selectTab(1)
            ShortcutAction.REPORTS -> selectTab(4)
            ShortcutAction.INVITE -> {
                selectTab(0)
                navController.navigate(InvitesRoute(create = true)) { launchSingleTop = true }
            }
        }
    }

    // Android 13+ drops notifications until they're allowed: with background checks on, ask once,
    // as soon as SynapseCtrl is unlocked. After that, Notifications in Settings shows it and offers the fix.
    val notifySettings by c.notifySettings.settings.collectAsStateWithLifecycle()
    val notificationAccess = rememberNotificationAccess()
    LaunchedEffect(covered, notifySettings.polling, notificationAccess.granted) {
        if (!covered && notifySettings.polling) notificationAccess.askOnce()
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (maxWidth >= 600.dp) {
                // Tablets, unfolded foldables and landscape: a side rail.
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxHeight().statusBarsPadding(),
                    ) {
                        Spacer(Modifier.weight(1f))
                        tabs.forEachIndexed { i, tab ->
                            val selected = i == selectedIndex
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectTab(i) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = CtrlColors.OnAccent,
                                    selectedTextColor = CtrlColors.Text,
                                    indicatorColor = CtrlColors.Accent,
                                    unselectedIconColor = CtrlColors.Muted,
                                    unselectedTextColor = CtrlColors.Muted,
                                ),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().navigationBarsPadding()) { AppNavHost(navController) }
                }
            } else {
                PhoneLayout(
                    navController = navController,
                    tabs = tabs,
                    showTabs = selectedIndex >= 0,
                    selectedIndex = selectedIndex,
                    onSelect = ::selectTab,
                )
            }
        }
    }
}

/**
 * Phones: content runs edge to edge and scrolls under the floating Ctrl tab bar.
 * The bar shows on the main tabs; pushed screens get the full height.
 */
@Composable
private fun PhoneLayout(
    navController: NavHostController,
    tabs: List<TabItem>,
    showTabs: Boolean,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val density = LocalDensity.current
    var overlayHeight by remember { mutableStateOf(0.dp) }

    CompositionLocalProvider(
        LocalBottomClearance provides if (showTabs) overlayHeight else 0.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                AppNavHost(navController)
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { overlayHeight = with(density) { it.height.toDp() } }
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(
                    visible = showTabs,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    CtrlBottomBar(items = tabs, selectedIndex = selectedIndex, onSelect = onSelect)
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = OverviewRoute) {
        topDestinations.forEach { top ->
            when (top.route) {
                OverviewRoute -> composable<OverviewRoute> { OverviewScreen() }
                UsersRoute -> composable<UsersRoute> { UsersScreen() }
                RoomsRoute -> composable<RoomsRoute> { RoomsScreen() }
                MediaRoute -> composable<MediaRoute> { MediaScreen() }
                ReportsRoute -> composable<ReportsRoute> { ReportsScreen() }
            }
        }
        composable<UserRoute> { UserScreen(it.toRoute<UserRoute>().userId) }
        composable<CreateUserRoute> { CreateUserScreen() }
        composable<UserMediaRoute> { UserMediaScreen(it.toRoute<UserMediaRoute>().userId) }
        composable<InvitesRoute> { InvitesScreen(createOnOpen = it.toRoute<InvitesRoute>().create) }
        composable<RoomRoute> { RoomScreen(it.toRoute<RoomRoute>().roomId) }
        composable<ReportRoute> { ReportScreen(it.toRoute<ReportRoute>().id) }
        composable<FederationRoute> { FederationScreen() }
        composable<ServerNoticeRoute> { ServerNoticeScreen(it.toRoute<ServerNoticeRoute>().userId) }
        composable<YouRoute> { YouScreen() }
        composable<SettingsRoute> { SettingsScreen() }
        composable<NotificationsRoute> { NotificationsScreen() }
        composable<ServersRoute> { ServersScreen() }
        composable<AddServerRoute> {
            val navigator = LocalNavigator.current
            SetupScreen(onDone = { navController.popBackStack(OverviewRoute, false) }, onCancel = navigator::back)
        }
        composable<EditServerRoute> {
            val navigator = LocalNavigator.current
            SetupScreen(editId = it.toRoute<EditServerRoute>().id, onDone = navigator::back, onCancel = navigator::back)
        }
    }
}
