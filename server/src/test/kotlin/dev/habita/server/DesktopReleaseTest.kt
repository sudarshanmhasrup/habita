package dev.habita.server

import dev.habita.app.shared.api.AppRelease
import dev.habita.server.updates.Platform
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpStatusCode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopReleaseTest {
    /** Enough of each kind of build for the server to recognise it. */
    private fun build(platform: Platform, seed: Int): ByteArray {
        val body = ByteArray(4096) { (it * seed).toByte() }
        return when (platform) {
            Platform.ANDROID -> byteArrayOf(0x50, 0x4B, 3, 4) + body
            Platform.WINDOWS -> byteArrayOf(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1) + body
            Platform.LINUX -> "!<arch>\n".encodeToByteArray() + body
            Platform.MAC -> body + "koly".encodeToByteArray() + ByteArray(508)
        }
    }

    private fun byteArrayOf(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private suspend fun HttpClient.publish(platform: String, bytes: ByteArray, versionCode: Int): HttpResponse =
        post("/app/$platform/releases") {
            bearerAuth(PUBLISH_TOKEN)
            header("X-Version-Code", versionCode)
            header("X-Version-Name", "1.0.$versionCode")
            setBody(bytes)
        }

    @Test
    fun `each desktop platform publishes, offers and downloads its own build`() = serverTest { client ->
        val token = client.token()
        listOf(Platform.WINDOWS, Platform.MAC, Platform.LINUX).forEachIndexed { index, platform ->
            val bytes = build(platform, index + 3)

            assertEquals(HttpStatusCode.Created, client.publish(platform.id, bytes, 20 + index).status)

            val latest = client.get("/app/${platform.id}/latest") { bearerAuth(token) }.body<AppRelease>()
            assertEquals(20 + index, latest.versionCode)
            val downloaded = client.get("/app/${platform.id}/download") { bearerAuth(token) }
            assertContentEquals(bytes, downloaded.readRawBytes())
            assertTrue(downloaded.headers["Content-Disposition"]!!.contains("habita-${20 + index}.${platform.extension}"))
        }
        // Android is untouched by desktop builds.
        assertEquals(HttpStatusCode.NoContent, client.get("/app/android/latest") { bearerAuth(token) }.status)
    }

    @Test
    fun `a build for the wrong platform is refused`() = serverTest { client ->
        assertEquals(HttpStatusCode.BadRequest, client.publish("windows", build(Platform.LINUX, 1), 5).status)
        assertEquals(HttpStatusCode.BadRequest, client.publish("mac", build(Platform.WINDOWS, 1), 5).status)
        assertEquals(HttpStatusCode.BadRequest, client.publish("linux", build(Platform.ANDROID, 1), 5).status)
        assertEquals(HttpStatusCode.NoContent, client.get("/app/windows/latest") { bearerAuth(client.token()) }.status)
    }

    @Test
    fun `an unknown platform is not found`() = serverTest { client ->
        val token = client.token()
        assertEquals(HttpStatusCode.NotFound, client.get("/app/ios/latest") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/app/ios/download") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.NotFound, client.publish("ios", build(Platform.ANDROID, 1), 5).status)
    }

    @Test
    fun `android builds stay where older apps look for them`() {
        val config = testConfig()
        serverTest(config) { client ->
            val bytes = build(Platform.ANDROID, 9)
            client.publish("android", bytes, 1001)
            assertContentEquals(bytes, client.get("/app/android/apk") { bearerAuth(client.token()) }.readRawBytes())
        }

        assertTrue(File(config.releasesPath, "habita-1001.apk").isFile)
    }

    @Test
    fun `desktop builds need a login to check or download`() = serverTest { client ->
        client.publish("windows", build(Platform.WINDOWS, 1), 5)

        assertEquals(HttpStatusCode.Unauthorized, client.get("/app/windows/latest").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/app/windows/download").status)
    }

    @Test
    fun `the download page is a login form that can only run its own script`() = serverTest { client ->
        val page = client.get("/download")

        assertEquals(HttpStatusCode.OK, page.status)
        val html = page.bodyAsText()
        assertTrue(html.contains("id=\"login-form\""))
        val policy = page.headers["Content-Security-Policy"]!!
        assertTrue(policy.contains("default-src 'none'"))
        assertTrue(policy.contains("script-src 'sha256-"))
        assertTrue(policy.contains("connect-src 'self'"))
        assertFalse(policy.contains("unsafe-eval"))
        assertFalse(policy.contains("script-src 'unsafe-inline'"))
    }
}