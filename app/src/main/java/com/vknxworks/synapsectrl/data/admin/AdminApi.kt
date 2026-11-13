package com.vknxworks.synapsectrl.data.admin

import com.vknxworks.synapsectrl.data.api.Api
import com.vknxworks.synapsectrl.data.api.ApiJson
import com.vknxworks.synapsectrl.data.api.SynapseClient
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

// Every Synapse admin endpoint SynapseCtrl uses, checked against docs/admin_api/ and
// synapse/rest/admin/ at Synapse 1.163.2. Admin API versions differ per endpoint: users are listed
// with v3 and read or changed with v2, devices are v2, room deletion is v2, the rest is v1.

private val V1 = Api.Admin(1)
private val V2 = Api.Admin(2)
private val V3 = Api.Admin(3)

private fun <T> decode(strategy: DeserializationStrategy<T>, e: JsonElement): T =
    if (e is JsonNull) ApiJson.decodeFromJsonElement(strategy, JsonObject(emptyMap())) else ApiJson.decodeFromJsonElement(strategy, e)

private fun Map<String, String?>.clean(): Map<String, String> = mapNotNull { (k, v) -> v?.let { k to it } }.toMap()

// ---------- Server ----------

/** GET /_synapse/admin/v1/background_updates/status. */
suspend fun SynapseClient.backgroundUpdates(): BackgroundUpdates =
    decode(BackgroundUpdates.serializer(), get(V1, "background_updates", "status"))

/** POST /_synapse/admin/v1/send_server_notice: needs server notices enabled in homeserver.yaml. */
suspend fun SynapseClient.sendServerNotice(userId: String, text: String): SentNotice =
    decode(
        SentNotice.serializer(),
        post(
            V1, "send_server_notice",
            body = buildJsonObject {
                put("user_id", userId)
                putJsonObject("content") {
                    put("msgtype", "m.text")
                    put("body", text)
                }
            },
        ),
    )

// ---------- Users ----------

/** Sort orders of the user list (order_by). */
enum class UserOrder(val param: String) { NAME("name"), DISPLAYNAME("displayname"), CREATED("creation_ts"), LAST_SEEN("last_seen_ts") }

/**
 * GET /_synapse/admin/v3/users. [deactivated]: true only deactivated, false only active, null both
 * (v3 semantics). [admins] the same for admins. [name] searches localparts and display names.
 */
suspend fun SynapseClient.users(
    from: String? = null,
    limit: Int = 50,
    name: String? = null,
    deactivated: Boolean? = null,
    admins: Boolean? = null,
    guests: Boolean? = null,
    order: UserOrder = UserOrder.NAME,
    backwards: Boolean = false,
): UserList = decode(
    UserList.serializer(),
    get(
        V3, "users",
        query = mapOf(
            "from" to from,
            "limit" to limit.toString(),
            "name" to name?.takeIf { it.isNotBlank() },
            "deactivated" to deactivated?.toString(),
            "admins" to admins?.toString(),
            "guests" to guests?.toString(),
            "order_by" to order.param,
            "dir" to if (backwards) "b" else "f",
        ).clean(),
    ),
)

/** GET /_synapse/admin/v2/users/<user_id>. */
suspend fun SynapseClient.user(userId: String): AdminUser = decode(AdminUser.serializer(), get(V2, "users", userId))

/**
 * PUT /_synapse/admin/v2/users/<user_id>: creates the account (or changes it, if it exists). Only
 * the given fields are sent.
 */
suspend fun SynapseClient.createUser(userId: String, password: String, displayName: String?, admin: Boolean): AdminUser =
    decode(
        AdminUser.serializer(),
        put(
            V2, "users", userId,
            body = buildJsonObject {
                put("password", password)
                if (!displayName.isNullOrBlank()) put("displayname", displayName)
                put("admin", admin)
            },
        ),
    )

/** PUT /_synapse/admin/v2/users/<user_id> with only a display name. An empty one removes it. */
suspend fun SynapseClient.setDisplayName(userId: String, displayName: String) {
    put(V2, "users", userId, body = buildJsonObject { put("displayname", displayName) })
}

/** POST /_synapse/admin/v1/reset_password/<user_id>. */
suspend fun SynapseClient.resetPassword(userId: String, newPassword: String, logoutDevices: Boolean) {
    post(
        V1, "reset_password", userId,
        body = buildJsonObject {
            put("new_password", newPassword)
            put("logout_devices", logoutDevices)
        },
    )
}

/** PUT /_synapse/admin/v1/users/<user_id>/admin. Synapse refuses to demote yourself. */
suspend fun SynapseClient.setAdmin(userId: String, admin: Boolean) {
    put(V1, "users", userId, "admin", body = buildJsonObject { put("admin", admin) })
}

/** POST (ban) or DELETE (unban) /_synapse/admin/v1/users/<user_id>/shadow_ban. */
suspend fun SynapseClient.setShadowBanned(userId: String, banned: Boolean) {
    if (banned) post(V1, "users", userId, "shadow_ban") else delete(V1, "users", userId, "shadow_ban")
}

/** PUT /_synapse/admin/v1/suspend/<user_id>. */
suspend fun SynapseClient.setSuspended(userId: String, suspended: Boolean) {
    put(V1, "suspend", userId, body = buildJsonObject { put("suspend", suspended) })
}

/** PUT /_synapse/admin/v2/users/<user_id> with "locked". */
suspend fun SynapseClient.setLocked(userId: String, locked: Boolean) {
    put(V2, "users", userId, body = buildJsonObject { put("locked", locked) })
}

/** POST /_synapse/admin/v1/deactivate/<user_id>. [erase] also removes the profile and marks them erased. */
suspend fun SynapseClient.deactivate(userId: String, erase: Boolean) {
    post(V1, "deactivate", userId, body = buildJsonObject { put("erase", erase) })
}

/** GET /_synapse/admin/v2/users/<user_id>/devices. */
suspend fun SynapseClient.devices(userId: String): DeviceList = decode(DeviceList.serializer(), get(V2, "users", userId, "devices"))

/** DELETE /_synapse/admin/v2/users/<user_id>/devices/<device_id>: logs that device out. */
suspend fun SynapseClient.deleteDevice(userId: String, deviceId: String) {
    delete(V2, "users", userId, "devices", deviceId)
}

/** POST /_synapse/admin/v2/users/<user_id>/delete_devices: logs these devices out. */
suspend fun SynapseClient.deleteDevices(userId: String, deviceIds: List<String>) {
    if (deviceIds.isEmpty()) return
    post(V2, "users", userId, "delete_devices", body = buildJsonObject { put("devices", JsonArray(deviceIds.map { JsonPrimitive(it) })) })
}

/** GET /_synapse/admin/v1/users/<user_id>/joined_rooms. */
suspend fun SynapseClient.joinedRooms(userId: String): JoinedRooms = decode(JoinedRooms.serializer(), get(V1, "users", userId, "joined_rooms"))

/** GET /_synapse/admin/v1/users/<user_id>/override_ratelimit. */
suspend fun SynapseClient.rateLimit(userId: String): RateLimit = decode(RateLimit.serializer(), get(V1, "users", userId, "override_ratelimit"))

/** POST /_synapse/admin/v1/users/<user_id>/override_ratelimit. 0 and 0 turns rate limiting off for them. */
suspend fun SynapseClient.setRateLimit(userId: String, messagesPerSecond: Long, burstCount: Long) {
    post(
        V1, "users", userId, "override_ratelimit",
        body = buildJsonObject {
            put("messages_per_second", messagesPerSecond)
            put("burst_count", burstCount)
        },
    )
}

/** DELETE /_synapse/admin/v1/users/<user_id>/override_ratelimit: back to the server's limits. */
suspend fun SynapseClient.clearRateLimit(userId: String) {
    delete(V1, "users", userId, "override_ratelimit")
}

// ---------- Media ----------

/** GET /_synapse/admin/v1/statistics/users/media, biggest users first. */
suspend fun SynapseClient.mediaStats(from: String? = null, limit: Int = 100): MediaStats =
    decode(
        MediaStats.serializer(),
        get(V1, "statistics", "users", "media", query = mapOf("from" to from, "limit" to limit.toString(), "order_by" to "media_length", "dir" to "b").clean()),
    )

/** GET /_synapse/admin/v1/users/<user_id>/media, newest first (or biggest first). */
suspend fun SynapseClient.userMedia(userId: String, from: String? = null, limit: Int = 100, bySize: Boolean = false): MediaList =
    decode(
        MediaList.serializer(),
        get(
            V1, "users", userId, "media",
            query = mapOf(
                "from" to from,
                "limit" to limit.toString(),
                "order_by" to if (bySize) "media_length" else "created_ts",
                "dir" to "b",
            ).clean(),
        ),
    )

/** DELETE /_synapse/admin/v1/media/<server_name>/<media_id>: deletes one piece of local media. */
suspend fun SynapseClient.deleteMedia(serverName: String, mediaId: String): DeletedMedia =
    decode(DeletedMedia.serializer(), delete(V1, "media", serverName, mediaId, body = JsonObject(emptyMap())))

/**
 * POST /_synapse/admin/v1/media/delete?before_ts=…&size_gt=…&keep_profiles=…: deletes local media
 * last accessed before [beforeTs] (and larger than [sizeGt] bytes).
 */
suspend fun SynapseClient.deleteLocalMedia(beforeTs: Long, sizeGt: Long, keepProfiles: Boolean): DeletedMedia =
    decode(
        DeletedMedia.serializer(),
        post(
            V1, "media", "delete",
            query = mapOf("before_ts" to beforeTs.toString(), "size_gt" to sizeGt.toString(), "keep_profiles" to keepProfiles.toString()),
        ),
    )

/** POST /_synapse/admin/v1/purge_media_cache?before_ts=…: removes cached remote media last used before [beforeTs]. */
suspend fun SynapseClient.purgeRemoteMedia(beforeTs: Long): PurgedRemote =
    decode(PurgedRemote.serializer(), post(V1, "purge_media_cache", query = mapOf("before_ts" to beforeTs.toString())))

/** POST /_synapse/admin/v1/media/quarantine/<server_name>/<media_id> (or unquarantine). */
suspend fun SynapseClient.setMediaQuarantined(serverName: String, mediaId: String, quarantined: Boolean) {
    post(V1, "media", if (quarantined) "quarantine" else "unquarantine", serverName, mediaId)
}

/** POST /_synapse/admin/v1/room/<room_id>/media/quarantine: every piece of media in a room. */
suspend fun SynapseClient.quarantineRoomMedia(roomId: String): Quarantined =
    decode(Quarantined.serializer(), post(V1, "room", roomId, "media", "quarantine"))

/** POST /_synapse/admin/v1/user/<user_id>/media/quarantine: every piece of a local user's media. */
suspend fun SynapseClient.quarantineUserMedia(userId: String): Quarantined =
    decode(Quarantined.serializer(), post(V1, "user", userId, "media", "quarantine"))

// ---------- Registration tokens ----------

/** GET /_synapse/admin/v1/registration_tokens. */
suspend fun SynapseClient.registrationTokens(): List<RegistrationToken> =
    decode(RegistrationTokens.serializer(), get(V1, "registration_tokens")).tokens

/** POST /_synapse/admin/v1/registration_tokens/new. A blank [token] lets Synapse make a random one. */
suspend fun SynapseClient.createRegistrationToken(token: String?, usesAllowed: Int?, expiryTime: Long?): RegistrationToken =
    decode(
        RegistrationToken.serializer(),
        post(
            V1, "registration_tokens", "new",
            body = buildJsonObject {
                if (!token.isNullOrBlank()) put("token", token) else put("length", 16)
                put("uses_allowed", usesAllowed?.let { JsonPrimitive(it) } ?: JsonNull)
                put("expiry_time", expiryTime?.let { JsonPrimitive(it) } ?: JsonNull)
            },
        ),
    )

/** PUT /_synapse/admin/v1/registration_tokens/<token>: null means unlimited / never expires. */
suspend fun SynapseClient.updateRegistrationToken(token: String, usesAllowed: Int?, expiryTime: Long?): RegistrationToken =
    decode(
        RegistrationToken.serializer(),
        put(
            V1, "registration_tokens", token,
            body = buildJsonObject {
                put("uses_allowed", usesAllowed?.let { JsonPrimitive(it) } ?: JsonNull)
                put("expiry_time", expiryTime?.let { JsonPrimitive(it) } ?: JsonNull)
            },
        ),
    )

/** DELETE /_synapse/admin/v1/registration_tokens/<token>. */
suspend fun SynapseClient.deleteRegistrationToken(token: String) {
    delete(V1, "registration_tokens", token)
}

// ---------- Rooms ----------

/** Sort orders of the room list (order_by). */
enum class RoomOrder(val param: String) { NAME("name"), MEMBERS("joined_members"), LOCAL_MEMBERS("joined_local_members"), STATE("state_events") }

/** GET /_synapse/admin/v1/rooms. [publicOnly]/[emptyOnly]: true only those, null no filter. */
suspend fun SynapseClient.rooms(
    from: String? = null,
    limit: Int = 50,
    search: String? = null,
    order: RoomOrder = RoomOrder.NAME,
    publicOnly: Boolean? = null,
    emptyOnly: Boolean? = null,
): RoomList = decode(
    RoomList.serializer(),
    get(
        V1, "rooms",
        query = mapOf(
            "from" to from,
            "limit" to limit.toString(),
            "search_term" to search?.takeIf { it.isNotBlank() },
            "order_by" to order.param,
            // Member and state counts sort largest first already; names A to Z.
            "dir" to "f",
            "public_rooms" to publicOnly?.toString(),
            "empty_rooms" to emptyOnly?.toString(),
        ).clean(),
    ),
)

/** GET /_synapse/admin/v1/rooms/<room_id>. */
suspend fun SynapseClient.room(roomId: String): AdminRoom = decode(AdminRoom.serializer(), get(V1, "rooms", roomId))

/** GET /_synapse/admin/v1/rooms/<room_id>/members. */
suspend fun SynapseClient.roomMembers(roomId: String): RoomMembers = decode(RoomMembers.serializer(), get(V1, "rooms", roomId, "members"))

/** GET /_synapse/admin/v1/rooms/<room_id>/state. */
suspend fun SynapseClient.roomState(roomId: String): RoomState = decode(RoomState.serializer(), get(V1, "rooms", roomId, "state"))

/** GET /_synapse/admin/v1/rooms/<room_id>/block. */
suspend fun SynapseClient.blockStatus(roomId: String): BlockStatus = decode(BlockStatus.serializer(), get(V1, "rooms", roomId, "block"))

/** PUT /_synapse/admin/v1/rooms/<room_id>/block. */
suspend fun SynapseClient.setBlocked(roomId: String, blocked: Boolean): BlockStatus =
    decode(BlockStatus.serializer(), put(V1, "rooms", roomId, "block", body = buildJsonObject { put("block", blocked) }))

/** POST /_synapse/admin/v1/rooms/<room_id>/make_room_admin: makes [userId] (default: you) room admin. */
suspend fun SynapseClient.makeRoomAdmin(roomId: String, userId: String) {
    post(V1, "rooms", roomId, "make_room_admin", body = buildJsonObject { put("user_id", userId) })
}

/**
 * POST /_matrix/client/v3/rooms/<room_id>/kick. The admin API can't remove someone from a room, so
 * this is a normal kick by your admin account, which needs to be in the room with enough power
 * (see [makeRoomAdmin]).
 */
suspend fun SynapseClient.kick(roomId: String, userId: String, reason: String?) {
    post(
        Api.Client, "rooms", roomId, "kick",
        body = buildJsonObject {
            put("user_id", userId)
            if (!reason.isNullOrBlank()) put("reason", reason)
        },
    )
}

/**
 * DELETE /_synapse/admin/v2/rooms/<room_id>: starts deleting the room in the background. Follow it
 * with [deleteStatus]. With [newRoomUserId], local members are moved to a new room with [message].
 */
suspend fun SynapseClient.deleteRoom(roomId: String, block: Boolean, purge: Boolean, newRoomUserId: String?, roomName: String?, message: String?): DeleteStarted =
    decode(
        DeleteStarted.serializer(),
        delete(
            V2, "rooms", roomId,
            body = buildJsonObject {
                put("block", block)
                put("purge", purge)
                if (!newRoomUserId.isNullOrBlank()) {
                    put("new_room_user_id", newRoomUserId)
                    if (!roomName.isNullOrBlank()) put("room_name", roomName)
                    if (!message.isNullOrBlank()) put("message", message)
                }
            },
        ),
    )

/** GET /_synapse/admin/v2/rooms/delete_status/<delete_id>. */
suspend fun SynapseClient.deleteStatus(deleteId: String): DeleteStatus =
    decode(DeleteStatus.serializer(), get(V2, "rooms", "delete_status", deleteId))

// ---------- Reports ----------

/** GET /_synapse/admin/v1/event_reports, newest first. */
suspend fun SynapseClient.eventReports(from: String? = null, limit: Int = 50, roomId: String? = null, sender: String? = null): EventReports =
    decode(
        EventReports.serializer(),
        get(
            V1, "event_reports",
            query = mapOf("from" to from, "limit" to limit.toString(), "dir" to "b", "room_id" to roomId, "event_sender_user_id" to sender).clean(),
        ),
    )

/** GET /_synapse/admin/v1/event_reports/<report_id>: includes the reported event. */
suspend fun SynapseClient.eventReport(id: Long): EventReport = decode(EventReport.serializer(), get(V1, "event_reports", id.toString()))

/** DELETE /_synapse/admin/v1/event_reports/<report_id>. */
suspend fun SynapseClient.deleteEventReport(id: Long) {
    delete(V1, "event_reports", id.toString())
}

/** GET /_synapse/admin/v1/user_reports, newest first. */
suspend fun SynapseClient.userReports(from: String? = null, limit: Int = 50): UserReports =
    decode(UserReports.serializer(), get(V1, "user_reports", query = mapOf("from" to from, "limit" to limit.toString(), "dir" to "b").clean()))

/** DELETE /_synapse/admin/v1/user_reports/<report_id>. */
suspend fun SynapseClient.deleteUserReport(id: Long) {
    delete(V1, "user_reports", id.toString())
}

// ---------- Federation ----------

/** GET /_synapse/admin/v1/federation/destinations, the most recently failing first when [failingFirst]. */
suspend fun SynapseClient.destinations(from: String? = null, limit: Int = 100, failingFirst: Boolean = true): Destinations =
    decode(
        Destinations.serializer(),
        get(
            V1, "federation", "destinations",
            query = mapOf(
                "from" to from,
                "limit" to limit.toString(),
                "order_by" to if (failingFirst) "failure_ts" else "destination",
                "dir" to if (failingFirst) "b" else "f",
            ).clean(),
        ),
    )

/** POST /_synapse/admin/v1/federation/destinations/<destination>/reset_connection. */
suspend fun SynapseClient.resetConnection(destination: String) {
    post(V1, "federation", "destinations", destination, "reset_connection")
}

/** One user's media usage, from GET /_synapse/admin/v1/statistics/users/media searched by localpart. */
suspend fun SynapseClient.userMediaStat(userId: String): UserMediaStat? {
    val localpart = userId.removePrefix("@").substringBefore(':')
    val page = decode(
        MediaStats.serializer(),
        get(V1, "statistics", "users", "media", query = mapOf("search_term" to localpart, "limit" to "100")),
    )
    return page.users.firstOrNull { it.userId == userId }
}
