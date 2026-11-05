package dev.habita.app.shared.api

import dev.habita.app.shared.gym.ExerciseStep
import dev.habita.app.shared.gym.SetLog
import dev.habita.app.shared.gym.WarmUpLog
import dev.habita.app.shared.gym.WorkoutSession
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * How synced records are written, the same on the server and in the app, so both sides produce
 * the same text for the same record (ties in [winsOver] compare it).
 */
val SyncJson: Json = Json { ignoreUnknownKeys = true }

/** The gym's records in the shared envelope, grouped by collection, leaving out empty ones. */
fun SyncChanges.toCollections(): Map<String, List<SyncRecord>> = buildMap {
    fun <T> add(collection: String, items: List<T>, serializer: KSerializer<T>, envelope: (T) -> Triple<String, Long, Boolean>) {
        if (items.isEmpty()) return
        put(
            collection,
            items.map { item ->
                val (id, updatedAt, deleted) = envelope(item)
                SyncRecord(id, updatedAt, deleted, SyncJson.encodeToJsonElement(serializer, item).jsonObject)
            },
        )
    }
    add(SyncCollections.GYM_SESSIONS, sessions, WorkoutSession.serializer()) { Triple(it.id, it.updatedAt, it.deleted) }
    add(SyncCollections.GYM_SETS, sets, SetLog.serializer()) { Triple(it.id, it.updatedAt, it.deleted) }
    add(SyncCollections.GYM_WARM_UPS, warmUps, WarmUpLog.serializer()) { Triple(it.id, it.updatedAt, it.deleted) }
    add(SyncCollections.GYM_STEPS, steps, ExerciseStep.serializer()) { Triple(it.exerciseId, it.updatedAt, false) }
}

/**
 * The gym records among [collections]. A record this version can't read, for example one written
 * by a newer app, is skipped instead of failing the rest.
 */
fun gymChanges(collections: Map<String, List<SyncRecord>>): SyncChanges {
    fun <T> read(collection: String, serializer: KSerializer<T>, envelope: (T, SyncRecord) -> T): List<T> =
        collections[collection].orEmpty().mapNotNull { record ->
            runCatching { envelope(SyncJson.decodeFromJsonElement(serializer, record.data), record) }.getOrNull()
        }
    return SyncChanges(
        sessions = read(SyncCollections.GYM_SESSIONS, WorkoutSession.serializer()) { it, r -> it.copy(id = r.id, updatedAt = r.updatedAt, deleted = r.deleted) },
        sets = read(SyncCollections.GYM_SETS, SetLog.serializer()) { it, r -> it.copy(id = r.id, updatedAt = r.updatedAt, deleted = r.deleted) },
        warmUps = read(SyncCollections.GYM_WARM_UPS, WarmUpLog.serializer()) { it, r -> it.copy(id = r.id, updatedAt = r.updatedAt, deleted = r.deleted) },
        steps = read(SyncCollections.GYM_STEPS, ExerciseStep.serializer()) { it, r -> it.copy(exerciseId = r.id, updatedAt = r.updatedAt) },
    )
}