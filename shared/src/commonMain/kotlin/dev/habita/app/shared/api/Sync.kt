package dev.habita.app.shared.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * One synced record in any collection. Every feature uses the same envelope: [id], [updatedAt]
 * and [deleted] drive syncing, and [data] holds the feature's own fields. Deleting keeps the
 * record as a tombstone, so the deletion reaches every device.
 */
@Serializable
data class SyncRecord(
    val id: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val data: JsonObject,
)

/**
 * Whether this record should replace [other], the same rule on the server and in the app: the
 * later [SyncRecord.updatedAt] wins. On a tie, the record whose data sorts last wins, so every
 * device ends up with the same copy whichever pushed first.
 */
fun SyncRecord.winsOver(other: SyncRecord?): Boolean = when {
    other == null -> true
    updatedAt != other.updatedAt -> updatedAt > other.updatedAt
    deleted != other.deleted -> deleted
    else -> data.toString() > other.data.toString()
}

/** The names of synced collections. A collection is only accepted once it is listed here. */
object SyncCollections {
    const val GYM_SESSIONS = "gym.sessions"
    const val GYM_SETS = "gym.sets"
    const val GYM_WARM_UPS = "gym.warmUps"
    const val GYM_STEPS = "gym.steps"
    const val GYM_BODY = "gym.body"
    const val GYM_SWAPS = "gym.swaps"
    const val GYM_CHECK_INS = "gym.checkIns"
    const val GYM_SETTINGS = "gym.settings"
    const val EXPENSES = "expense.expenses"
    const val EXPENSE_CATEGORIES = "expense.categories"
    const val EXPENSE_SETTINGS = "expense.settings"
    const val ROUTINE_GROUPS = "routine.groups"
    const val ROUTINE_TASKS = "routine.tasks"
    const val ROUTINE_CHECKS = "routine.checks"
    const val APP_PREFERENCES = "app.preferences"
}

/** Local changes for the server, grouped by collection. */
@Serializable
data class SyncPush(
    val collections: Map<String, List<SyncRecord>>,
)

/** A pushed record the server refused, and why. Retrying it unchanged won't help. */
@Serializable
data class RejectedRecord(
    val collection: String,
    val id: String,
    val reason: String,
    /**
     * Whether a newer server could accept it, such as one that knows its collection or fields.
     * The app keeps those to send again and drops the rest, which no server will take.
     */
    val retryable: Boolean = true,
)

/** [cursor] is the server position after the push. */
@Serializable
data class SyncPushResult(
    val cursor: Long,
    val rejected: List<RejectedRecord> = emptyList(),
)

/**
 * Changes after the cursor the app sent, oldest first. Pass [cursor] as `since` next time, and
 * pull again while [hasMore]. [serverId] changes only when the server's database is replaced, for
 * example by restoring a backup: the app then sends everything it has again.
 */
@Serializable
data class SyncPull(
    val serverId: String,
    val cursor: Long,
    val hasMore: Boolean,
    val collections: Map<String, List<SyncRecord>>,
)