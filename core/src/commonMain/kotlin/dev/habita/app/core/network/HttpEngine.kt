package dev.habita.app.core.network

import io.ktor.client.engine.HttpClientEngineFactory

/** The HTTP engine for this platform. */
internal expect val httpEngine: HttpClientEngineFactory<*>