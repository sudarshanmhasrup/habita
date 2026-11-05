package dev.habita.server

import dev.habita.app.shared.api.AndroidRelease
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpStatusCode
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ReleaseTest {
    /** Enough of an APK for the server: a zip header and some bytes. */
    private fun apk(seed: Int) = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4) + ByteArray(2048) { (it * seed).toByte() }

    private suspend fun HttpClient.publish(apk: ByteArray, versionCode: Int, token: String? = PUBLISH_TOKEN): HttpResponse =
        post("/app/android/releases") {
            token?.let { bearerAuth(it) }
            header("X-Version-Code", versionCode)
            header("X-Version-Name", "1.0.0 ($versionCode)")
            setBody(apk)
        }

    @Test
    fun `nothing is published at first`() = serverTest { client ->
        assertEquals(HttpStatusCode.NoContent, client.get("/app/android/latest") { bearerAuth(client.token()) }.status)
    }

    @Test
    fun `a published build is offered and downloads intact`() = serverTest { client ->
        val bytes = apk(7)

        assertEquals(HttpStatusCode.Created, client.publish(bytes, 105).status)

        val token = client.token()
        val latest = client.get("/app/android/latest") { bearerAuth(token) }.body<AndroidRelease>()
        assertEquals(105, latest.versionCode)
        assertEquals(bytes.size.toLong(), latest.sizeBytes)
        val downloaded = client.get("/app/android/apk") { bearerAuth(token) }.readRawBytes()
        assertContentEquals(bytes, downloaded)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, latest.sha256)
    }

    @Test
    fun `publishing needs the publish token, not a login`() = serverTest { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.publish(apk(1), 105, token = null).status)
        assertEquals(HttpStatusCode.Unauthorized, client.publish(apk(1), 105, token = client.token()).status)
        assertEquals(HttpStatusCode.Unauthorized, client.publish(apk(1), 105, token = PUBLISH_TOKEN.dropLast(1) + "x").status)
    }

    @Test
    fun `publishing is off without a publish token`() = serverTest(testConfig(publishToken = null)) { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.publish(apk(1), 105).status)
    }

    @Test
    fun `an older build or a file that isn't an APK is refused`() = serverTest { client ->
        client.publish(apk(1), 105)

        assertEquals(HttpStatusCode.BadRequest, client.publish(apk(2), 104).status)
        assertEquals(HttpStatusCode.BadRequest, client.publish(apk(2), 105).status)
        assertEquals(HttpStatusCode.BadRequest, client.publish("not an apk".encodeToByteArray(), 106).status)
        assertEquals(105, client.get("/app/android/latest") { bearerAuth(client.token()) }.body<AndroidRelease>().versionCode)
    }

    @Test
    fun `only the newest three builds are kept`() {
        val config = testConfig()
        serverTest(config) { client ->
            (101..105).forEach { client.publish(apk(it), it) }
        }

        val kept = File(config.releasesPath).list().orEmpty().filter { it.endsWith(".apk") }.sorted()
        assertEquals(listOf("habita-103.apk", "habita-104.apk", "habita-105.apk"), kept)
    }
}