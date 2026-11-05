package dev.habita.app.core.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

internal actual val httpEngine: HttpClientEngineFactory<*> = CIO