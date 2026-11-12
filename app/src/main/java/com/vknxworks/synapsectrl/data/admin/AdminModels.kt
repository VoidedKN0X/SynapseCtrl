package com.vknxworks.synapsectrl.data.admin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Synapse admin API objects, as documented in docs/admin_api/ for Synapse 1.163. Every field has a
// default, so a missing or new field never crashes (see ApiJson). "next_token" is a number for some
// endpoints and a string for others; lenient parsing reads both as a string.

// ---------- Users (docs/admin_api/user_admin_api.md) ----------

/** One entry of GET /_synapse/admin/v3/users, and GET /_synapse/admin/v2/users/<user_id>. */
@Serializable
data class AdminUser(
    val name: String = "",
    val displayname: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_guest") val isGuest: Boolean = false,
    val admin: Boolean = false,
    @SerialName("user_type") val userType: String? = null,
    val deactivated: Boolean = false,
    val erased: Boolean = false,
    @SerialName("shadow_banned") val shadowBanned: Boolean = false,
    val locked: Boolean = false,
    val suspended: Boolean = false,
    /** Milliseconds in the list; the single-user API has returned seconds in older versions (see [createdMs]). */
    @SerialName("creation_ts") val creationTs: Long = 0,
    @SerialName("last_seen_ts") val lastSeenTs: Long? = null,
    @SerialName("appservice_id") val appserviceId: String? = null,
) {
    /** The name to show: the display name, or the user ID without "@" and the server. */
    val label: String get() = displayname?.takeIf { it.isNotBlank() } ?: name.removePrefix("@").substringBefore(':')

    /** [creationTs] in milliseconds, whether Synapse sent seconds or milliseconds. */
    val createdMs: Long get() = if (creationTs in 1..99_999_999_999L) creationTs * 1000 else creationTs
}

@Serializable
data class UserList(
    val users: List<AdminUser> = emptyList(),
    @SerialName("next_token") val nextToken: String? = null,
    val total: Long = 0,
)

/** GET /_synapse/admin/v2/users/<user_id>/devices. */
@Serializable
data class DeviceList(val devices: List<AdminDevice> = emptyList(), val total: Long = 0)

@Serializable
data class AdminDevice(
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("last_seen_ip") val lastSeenIp: String? = null,
    @SerialName("last_seen_user_agent") val lastSeenUserAgent: String? = null,
    @SerialName("last_seen_ts") val lastSeenTs: Long? = null,
    val dehydrated: Boolean = false,
)

/** GET /_synapse/admin/v1/users/<user_id>/joined_rooms. */
@Serializable
data class JoinedRooms(@SerialName("joined_rooms") val joinedRooms: List<String> = emptyList(), val total: Long = 0)

/** GET /_synapse/admin/v1/users/<user_id>/override_ratelimit: empty when there's no override. */
@Serializable
data class RateLimit(
    @SerialName("messages_per_second") val messagesPerSecond: Long? = null,
    @SerialName("burst_count") val burstCount: Long? = null,
) {
    val isSet: Boolean get() = messagesPerSecond != null || burstCount != null
}

/** GET /_synapse/admin/v1/users/<user_id>/media and the media in it. */
@Serializable
data class MediaList(
    val media: List<LocalMedia> = emptyList(),
    @SerialName("next_token") val nextToken: String? = null,
    val total: Long = 0,
)

@Serializable
data class LocalMedia(
    @SerialName("media_id") val mediaId: String = "",
    @SerialName("media_length") val mediaLength: Long = 0,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("upload_name") val uploadName: String? = null,
    @SerialName("created_ts") val createdTs: Long = 0,
    @SerialName("last_access_ts") val lastAccessTs: Long? = null,
    @SerialName("quarantined_by") val quarantinedBy: String? = null,
    @SerialName("safe_from_quarantine") val safeFromQuarantine: Boolean = false,
)

/** Answer of the media delete endpoints. */
@Serializable
data class DeletedMedia(@SerialName("deleted_media") val deletedMedia: List<String> = emptyList(), val total: Long = 0)

/** GET /_synapse/admin/v1/statistics/users/media. */
@Serializable
data class MediaStats(
    val users: List<UserMediaStat> = emptyList(),
    @SerialName("next_token") val nextToken: String? = null,
    val total: Long = 0,
)

@Serializable
data class UserMediaStat(
    @SerialName("user_id") val userId: String = "",
    val displayname: String? = null,
    @SerialName("media_count") val mediaCount: Long = 0,
    @SerialName("media_length") val mediaLength: Long = 0,
)

// ---------- Registration tokens (docs/usage/administration/admin_api/registration_tokens.md) ----------

@Serializable
data class RegistrationToken(
    val token: String = "",
    /** Null: unlimited. */
    @SerialName("uses_allowed") val usesAllowed: Int? = null,
    val pending: Int = 0,
    val completed: Int = 0,
    /** Milliseconds; null: never expires. */
    @SerialName("expiry_time") val expiryTime: Long? = null,
) {
    /** Uses left, or null when unlimited. */
    val remaining: Int? get() = usesAllowed?.let { (it - completed - pending).coerceAtLeast(0) }

    fun isValid(now: Long): Boolean = (expiryTime == null || expiryTime > now) && (remaining == null || remaining!! > 0)
}

@Serializable
data class RegistrationTokens(@SerialName("registration_tokens") val tokens: List<RegistrationToken> = emptyList())

// ---------- Rooms (docs/admin_api/rooms.md) ----------

/** One entry of GET /_synapse/admin/v1/rooms, and GET /_synapse/admin/v1/rooms/<room_id>. */
@Serializable
data class AdminRoom(
    @SerialName("room_id") val roomId: String = "",
    val name: String? = null,
    val topic: String? = null,
    val avatar: String? = null,
    @SerialName("canonical_alias") val canonicalAlias: String? = null,
    @SerialName("joined_members") val joinedMembers: Long = 0,
    @SerialName("joined_local_members") val joinedLocalMembers: Long = 0,
    @SerialName("joined_local_devices") val joinedLocalDevices: Long = 0,
    val version: String? = null,
    val creator: String? = null,
    val encryption: String? = null,
    val federatable: Boolean = true,
    val public: Boolean = false,
    @SerialName("join_rules") val joinRules: String? = null,
    @SerialName("guest_access") val guestAccess: String? = null,
    @SerialName("history_visibility") val historyVisibility: String? = null,
    @SerialName("state_events") val stateEvents: Long = 0,
    @SerialName("room_type") val roomType: String? = null,
    val forgotten: Boolean = false,
    val tombstoned: Boolean = false,
    @SerialName("replacement_room") val replacementRoom: String? = null,
) {
    val label: String get() = name?.takeIf { it.isNotBlank() } ?: canonicalAlias?.takeIf { it.isNotBlank() } ?: roomId
    val isSpace: Boolean get() = roomType == "m.space"
}

@Serializable
data class RoomList(
    val rooms: List<AdminRoom> = emptyList(),
    val offset: Long = 0,
    @SerialName("total_rooms") val totalRooms: Long = 0,
    @SerialName("next_batch") val nextBatch: String? = null,
)

/** GET /_synapse/admin/v1/rooms/<room_id>/members. */
@Serializable
data class RoomMembers(val members: List<String> = emptyList(), val total: Long = 0)

/** GET /_synapse/admin/v1/rooms/<room_id>/state. */
@Serializable
data class RoomState(val state: List<JsonObject> = emptyList())

/** GET /_synapse/admin/v1/rooms/<room_id>/block. */
@Serializable
data class BlockStatus(val block: Boolean = false, @SerialName("user_id") val userId: String? = null)

/** DELETE /_synapse/admin/v2/rooms/<room_id>. */
@Serializable
data class DeleteStarted(@SerialName("delete_id") val deleteId: String = "")

/** GET /_synapse/admin/v2/rooms/delete_status/<delete_id>. */
@Serializable
data class DeleteStatus(
    @SerialName("delete_id") val deleteId: String = "",
    @SerialName("room_id") val roomId: String = "",
    /** scheduled, active, complete or failed. */
    val status: String = "",
    val error: String? = null,
    @SerialName("shutdown_room") val shutdownRoom: ShutdownRoom = ShutdownRoom(),
) {
    val finished: Boolean get() = status == "complete" || status == "failed"
}

@Serializable
data class ShutdownRoom(
    @SerialName("kicked_users") val kickedUsers: List<String> = emptyList(),
    @SerialName("failed_to_kick_users") val failedToKickUsers: List<String> = emptyList(),
    @SerialName("local_aliases") val localAliases: List<String> = emptyList(),
    @SerialName("new_room_id") val newRoomId: String? = null,
)

/** Answer of the quarantine-a-room and quarantine-a-user endpoints. */
@Serializable
data class Quarantined(@SerialName("num_quarantined") val numQuarantined: Long = 0)

/** POST /_synapse/admin/v1/purge_media_cache. */
@Serializable
data class PurgedRemote(val deleted: Long = 0)

// ---------- Reports (docs/admin_api/event_reports.md, synapse/rest/admin/user_reports.py) ----------

@Serializable
data class EventReport(
    val id: Long = 0,
    @SerialName("received_ts") val receivedTs: Long = 0,
    @SerialName("room_id") val roomId: String = "",
    val name: String? = null,
    @SerialName("event_id") val eventId: String = "",
    /** Who reported it. */
    @SerialName("user_id") val userId: String = "",
    val reason: String? = null,
    val score: Long? = null,
    /** Who sent the reported event. */
    val sender: String = "",
    @SerialName("canonical_alias") val canonicalAlias: String? = null,
    /** Only in the single-report API. */
    @SerialName("event_json") val eventJson: JsonObject? = null,
)

@Serializable
data class EventReports(
    @SerialName("event_reports") val reports: List<EventReport> = emptyList(),
    @SerialName("next_token") val nextToken: String? = null,
    val total: Long = 0,
)

/** A report about a user rather than an event (GET /_synapse/admin/v1/user_reports). */
@Serializable
data class UserReport(
    val id: Long = 0,
    @SerialName("received_ts") val receivedTs: Long = 0,
    @SerialName("target_user_id") val targetUserId: String = "",
    @SerialName("user_id") val userId: String = "",
    val reason: String? = null,
)

@Serializable
data class UserReports(
    @SerialName("user_reports") val reports: List<UserReport> = emptyList(),
    @SerialName("next_token") val nextToken: String? = null,
    val total: Long = 0,
)

// ---------- Federation (docs/usage/administration/admin_api/federation.md) ----------

@Serializable
data class Destination(
    val destination: String = "",
    /** 0 when the last attempt worked. */
    @SerialName("retry_last_ts") val retryLastTs: Long = 0,
    @SerialName("retry_interval") val retryInterval: Long = 0,
    /** When it started failing; null if it never failed. */
    @SerialName("failure_ts") val failureTs: Long? = null,
    @SerialName("last_successful_stream_ordering") val lastSuccessfulStreamOrdering: Long? = null,
) {
    /** Synapse is backing off from this server right now. */
    val failing: Boolean get() = failureTs != null && retryLastTs > 0
}

@Serializable
data class Destinations(
    val destinations: List<Destination> = emptyList(),
    @SerialName("next_token") val nextToken: String? = null,
    val total: Long = 0,
)

// ---------- Background updates (docs/usage/administration/admin_api/background_updates.md) ----------

@Serializable
data class BackgroundUpdates(
    val enabled: Boolean = true,
    @SerialName("current_updates") val currentUpdates: Map<String, BackgroundUpdate> = emptyMap(),
)

@Serializable
data class BackgroundUpdate(
    val name: String = "",
    @SerialName("total_item_count") val totalItemCount: Long = 0,
    @SerialName("total_duration_ms") val totalDurationMs: Double = 0.0,
)

/** POST /_synapse/admin/v1/send_server_notice. */
@Serializable
data class SentNotice(@SerialName("event_id") val eventId: String = "")
