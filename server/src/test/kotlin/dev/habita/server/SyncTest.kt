package dev.habita.server

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.habita.app.shared.api.SyncChanges
import dev.habita.app.shared.api.SyncCollections
import dev.habita.app.shared.api.SyncPull
import dev.habita.app.shared.api.SyncPush
import dev.habita.app.shared.api.SyncPushResult
import dev.habita.app.shared.api.SyncRecord
import dev.habita.app.shared.api.gymChanges
import dev.habita.app.shared.gym.Effort
import dev.habita.app.shared.gym.ExerciseStep
import dev.habita.app.shared.gym.WarmUpLog
import dev.habita.app.shared.gym.WarmUpStatus
import dev.habita.server.db.HabitaDatabase
import dev.habita.server.sync.SyncEngine
import dev.habita.server.sync.recordTables
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.io.path.createTempFile

class SyncTest {
    private val session = session("s1", "upper-a", NOW)
    private val changes = SyncChanges(
        sessions = listOf(session),
        sets = sets("s1", "dumbbell-bench-press", NOW, 20 * KG, 12, 12, null)
            .mapIndexed { index, set -> if (index == 0) set.copy(effort = Effort.EASY) else set },
        warmUps = listOf(WarmUpLog("w1", "s1", 0, WarmUpStatus.DONE, NOW, NOW)),
        steps = listOf(ExerciseStep("dumbbell-bench-press", 2500, NOW)),
    )

    private fun expense(id: String, amount: Int, updatedAt: Long = NOW, deleted: Boolean = false) = SyncRecord(
        id = id,
        updatedAt = updatedAt,
        deleted = deleted,
        data = buildJsonObject {
            put("amountPaise", amount)
            put("description", "Lunch")
        },
    )

    private suspend fun HttpClient.pull(token: String, since: Long = 0, limit: Int? = null) =
        get("/sync/pull?since=$since" + (limit?.let { "&limit=$it" } ?: "")) { bearerAuth(token) }.body<SyncPull>()

    @Test
    fun `pushed records come back on pull`() = serverTest { client ->
        val token = client.token()

        val result = client.push(token, changes).body<SyncPushResult>()
        val pulled = client.pull(token)

        assertEquals(6, result.cursor)
        assertTrue(result.rejected.isEmpty())
        assertEquals(result.cursor, pulled.cursor)
        assertEquals(false, pulled.hasMore)
        assertEquals(changes, gymChanges(pulled.collections))
        assertNull(gymChanges(pulled.collections).sets.last().achieved)
        assertEquals(Effort.EASY, gymChanges(pulled.collections).sets.first().effort)
    }

    @Test
    fun `pushing the same records twice changes nothing`() = serverTest { client ->
        val token = client.token()

        val first = client.push(token, changes).body<SyncPushResult>().cursor
        val second = client.push(token, changes).body<SyncPushResult>().cursor

        assertEquals(first, second)
        assertEquals(changes, gymChanges(client.pull(token).collections))
    }

    @Test
    fun `the newest update wins`() = serverTest { client ->
        val token = client.token()
        client.push(token, changes)

        val newer = session.copy(endedAt = NOW + 2 * 60 * 60 * 1000, updatedAt = NOW + 10)
        val older = session.copy(workoutId = "lower-a", updatedAt = NOW - 10)
        client.push(token, SyncChanges(sessions = listOf(newer)))
        client.push(token, SyncChanges(sessions = listOf(older)))

        assertEquals(newer, gymChanges(client.pull(token).collections).sessions.single())
    }

    @Test
    fun `pull returns only what changed after the cursor`() = serverTest { client ->
        val token = client.token()
        val cursor = client.push(token, changes).body<SyncPushResult>().cursor

        val deleted = changes.sets.first().copy(deleted = true, updatedAt = NOW + 1)
        client.push(token, SyncChanges(sets = listOf(deleted)))
        val pulled = client.pull(token, since = cursor)

        assertEquals(SyncChanges(sets = listOf(deleted)), gymChanges(pulled.collections))
        assertEquals(cursor + 1, pulled.cursor)
    }

    @Test
    fun `a bad record is refused on its own and the rest are saved`() = serverTest { client ->
        val token = client.token()
        val bad = changes.sets.first().copy(id = "bad", weightGrams = -1)

        val result = client.push(token, changes.copy(sets = changes.sets + bad)).body<SyncPushResult>()

        assertEquals(listOf("bad"), result.rejected.map { it.id })
        assertEquals(changes, gymChanges(client.pull(token).collections))
    }

    @Test
    fun `unknown collections and unreadable records are refused`() = serverTest { client ->
        val token = client.token()
        val unreadable = SyncRecord("x", NOW, data = buildJsonObject { put("effort", JsonPrimitive("NOT_A_LEVEL")) })

        val result = client.push(
            token,
            mapOf("todo.items" to listOf(expense("t1", 1)), SyncCollections.GYM_SETS to listOf(unreadable)),
        ).body<SyncPushResult>()

        assertEquals(setOf("todo.items" to "t1", SyncCollections.GYM_SETS to "x"), result.rejected.map { it.collection to it.id }.toSet())
        assertTrue(client.pull(token).collections.isEmpty())
    }

    @Test
    fun `expenses sync as records the server keeps as they came`() = serverTest { client ->
        val token = client.token()
        val lunch = expense("e1", 25_000)
        client.push(token, mapOf(SyncCollections.EXPENSES to listOf(lunch)))

        val edited = expense("e1", 30_000, updatedAt = NOW + 1)
        val stale = expense("e1", 1, updatedAt = NOW - 1)
        client.push(token, mapOf(SyncCollections.EXPENSES to listOf(edited)))
        client.push(token, mapOf(SyncCollections.EXPENSES to listOf(stale)))

        assertEquals(listOf(edited), client.pull(token).collections[SyncCollections.EXPENSES])
    }

    @Test
    fun `routine groups, tasks and ticks sync as records the server keeps as they came`() = serverTest { client ->
        val token = client.token()
        val group = SyncRecord("office", NOW, data = buildJsonObject { put("name", "Office") })
        val task = SyncRecord("office-01", NOW, data = buildJsonObject { put("title", "Clean shoes") })
        val tick = SyncRecord("2026-11-06/office-01", NOW, data = buildJsonObject { put("done", true) })

        val result = client.push(
            token,
            mapOf(
                SyncCollections.ROUTINE_GROUPS to listOf(group),
                SyncCollections.ROUTINE_TASKS to listOf(task),
                SyncCollections.ROUTINE_CHECKS to listOf(tick),
            ),
        ).body<SyncPushResult>()

        assertTrue(result.rejected.isEmpty())
        val pulled = client.pull(token).collections
        assertEquals(listOf(group), pulled[SyncCollections.ROUTINE_GROUPS])
        assertEquals(listOf(task), pulled[SyncCollections.ROUTINE_TASKS])
        assertEquals(listOf(tick), pulled[SyncCollections.ROUTINE_CHECKS])
    }

    @Test
    fun `preferences such as the theme sync like any record`() = serverTest { client ->
        val token = client.token()
        val accent = SyncRecord("accent", NOW, data = buildJsonObject { put("value", "plum") })

        val result = client.push(token, mapOf(SyncCollections.APP_PREFERENCES to listOf(accent))).body<SyncPushResult>()

        assertTrue(result.rejected.isEmpty())
        assertEquals(listOf(accent), client.pull(token).collections[SyncCollections.APP_PREFERENCES])
    }

    @Test
    fun `a clock far ahead can't lock a record forever`() {
        var now = NOW
        serverTest(clock = { now }) { client ->
            val token = client.token()
            client.push(token, mapOf(SyncCollections.EXPENSES to listOf(expense("e1", 100, updatedAt = Long.MAX_VALUE))))

            val stored = client.pull(token).collections[SyncCollections.EXPENSES]!!.single()
            assertEquals(NOW + 5 * 60 * 1000, stored.updatedAt)

            // Ten minutes on, an edit from a device with the right time replaces it.
            now += 10 * 60 * 1000
            val fix = expense("e1", 50, updatedAt = now)
            client.push(token, mapOf(SyncCollections.EXPENSES to listOf(fix)))
            assertEquals(listOf(fix), client.pull(token).collections[SyncCollections.EXPENSES])
        }
    }

    @Test
    fun `two edits at the same moment end the same whichever arrives first`() {
        val a = expense("e1", 100)
        val b = expense("e1", 200)
        val results = listOf(listOf(a, b), listOf(b, a)).map { order ->
            var stored: SyncRecord? = null
            serverTest { client ->
                val token = client.token()
                order.forEach { client.push(token, mapOf(SyncCollections.EXPENSES to listOf(it))) }
                stored = client.pull(token).collections[SyncCollections.EXPENSES]!!.single()
            }
            stored
        }

        assertEquals(results[0], results[1])
    }

    @Test
    fun `pull comes in pages`() = serverTest { client ->
        val token = client.token()
        client.push(token, changes)

        val first = client.pull(token, limit = 4)
        val second = client.pull(token, since = first.cursor, limit = 4)

        assertEquals(true, first.hasMore)
        assertEquals(4, first.collections.values.sumOf { it.size })
        assertEquals(false, second.hasMore)
        assertEquals(2, second.collections.values.sumOf { it.size })
        assertEquals(6, second.cursor)
    }

    @Test
    fun `malformed json and cursors are rejected`() = serverTest { client ->
        val token = client.token()
        val response = client.post("/sync/push") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("{ not json")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/sync/pull?since=abc") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/sync/pull?limit=0") { bearerAuth(token) }.status)
    }

    @Test
    fun `data and the server id survive a restart, and a new database gets a new id`() {
        val config = testConfig()
        lateinit var firstId: String
        serverTest(config) { client ->
            val token = client.token()
            client.push(token, changes)
            firstId = client.pull(token).serverId
        }

        serverTest(config) { client ->
            val pulled = client.pull(client.token())
            assertEquals(changes, gymChanges(pulled.collections))
            assertEquals(firstId, pulled.serverId)
        }
        serverTest { client -> assertNotEquals(firstId, client.pull(client.token()).serverId) }
    }

    @Test
    fun `a database from before this version migrates and syncs records`() {
        val path = createTempFile("habita", ".db").toString()
        JdbcSqliteDriver("jdbc:sqlite:$path").use { driver ->
            // The tables version 3 of the schema had that the migration touches.
            driver.execute(null, "CREATE TABLE sync_counter (id INTEGER NOT NULL PRIMARY KEY, value INTEGER NOT NULL)", 0)
            driver.execute(null, "INSERT INTO sync_counter VALUES (1, 7)", 0)
            driver.execute(null, "CREATE TABLE exercise_step (exercise_id TEXT NOT NULL PRIMARY KEY, step INTEGER NOT NULL, updated_at INTEGER NOT NULL, server_seq INTEGER NOT NULL)", 0)
            HabitaDatabase.Schema.migrate(driver, 3, 4)
            val database = HabitaDatabase(driver)
            val engine = SyncEngine(database, recordTables(database))

            val result = engine.push(SyncPush(mapOf(SyncCollections.EXPENSES to listOf(expense("e1", 100)))), NOW)

            assertEquals(8, result.cursor)
            assertEquals(listOf(expense("e1", 100)), engine.pull(7, 10).collections[SyncCollections.EXPENSES])
        }
    }
}