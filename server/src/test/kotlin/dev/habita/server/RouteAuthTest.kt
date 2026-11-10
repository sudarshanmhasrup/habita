package dev.habita.server

import io.ktor.client.request.request
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.plugin
import io.ktor.server.auth.AuthenticationRouteSelector
import io.ktor.server.routing.HttpMethodRouteSelector
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.RoutingRoot
import io.ktor.server.routing.getAllRoutes
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards against a new route being added outside `authenticate { }` by mistake, which would make
 * it public with every other test still passing. To make a route public on purpose, add it to
 * [PUBLIC] and say why in the review.
 */
class RouteAuthTest {
    private data class Endpoint(val method: HttpMethod, val path: String, val protected: Boolean)

    private fun endpoints(): List<Endpoint> {
        lateinit var root: RoutingNode
        testApplication {
            application {
                module(testConfig())
                root = plugin(RoutingRoot)
            }
            startApplication()
        }
        return root.getAllRoutes().mapNotNull { node ->
            val method = (node.selector as? HttpMethodRouteSelector)?.method ?: return@mapNotNull null
            val chain = generateSequence(node) { it.parent }.toList()
            // Rate limits, authentication and the method show up as "(…)" parts; keep the path.
            val path = node.toString().replace(Regex("""/\([^)]*\)"""), "").ifEmpty { "/" }
            Endpoint(method, path, chain.any { it.selector is AuthenticationRouteSelector })
        }
    }

    @Test
    fun `every route needs a login except the public ones`() {
        val open = endpoints().filter { !it.protected }.map { "${it.method.value} ${it.path}" }.toSet()

        assertEquals(PUBLIC, open)
    }

    @Test
    fun `every protected route refuses a request without a token`() = serverTest { client ->
        val protectedRoutes = endpoints().filter { it.protected }
        assertTrue(protectedRoutes.size >= 11, "Expected the data routes, found $protectedRoutes")

        protectedRoutes.forEach { endpoint ->
            val path = endpoint.path.replace(Regex("""\{[^}]*}"""), "x")
            val status = client.request(path) { method = endpoint.method }.status
            assertEquals(HttpStatusCode.Unauthorized, status, "${endpoint.method.value} $path")
        }
    }

    private companion object {
        // GET /download is the download page: a login form with no data in it. The builds it
        // lists come from /app/{platform}/..., which need a login.
        val PUBLIC = setOf("GET /health", "POST /auth/login", "GET /download")
    }
}