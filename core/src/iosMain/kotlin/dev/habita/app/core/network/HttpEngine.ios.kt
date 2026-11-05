package dev.habita.app.core.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

/** Apple's own networking, so requests follow the system's TLS and proxy settings. */
internal actual val httpEngine: HttpClientEngineFactory<*> = Darwin