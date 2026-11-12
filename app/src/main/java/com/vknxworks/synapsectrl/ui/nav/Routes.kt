package com.vknxworks.synapsectrl.ui.nav

import kotlinx.serialization.Serializable

// Tabs
@Serializable data object OverviewRoute
@Serializable data object UsersRoute
@Serializable data object RoomsRoute
@Serializable data object MediaRoute
@Serializable data object ReportsRoute

// Users and invites
@Serializable data class UserRoute(val userId: String)
@Serializable data object CreateUserRoute
@Serializable data class UserMediaRoute(val userId: String)
@Serializable data class InvitesRoute(val create: Boolean = false)

// Rooms, reports, federation and notices
@Serializable data class RoomRoute(val roomId: String)
@Serializable data class ReportRoute(val id: Long)
@Serializable data object FederationRoute
@Serializable data class ServerNoticeRoute(val userId: String = "")

// Account and settings, opened from the profile picture
@Serializable data object YouRoute
@Serializable data object SettingsRoute
@Serializable data object NotificationsRoute
@Serializable data object ServersRoute
@Serializable data object AddServerRoute
@Serializable data class EditServerRoute(val id: String)
