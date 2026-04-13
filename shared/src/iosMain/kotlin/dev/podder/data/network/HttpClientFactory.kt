package dev.podder.data.network

import dev.podder.logging.PodderLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun createHttpClient(logger: PodderLogger?): HttpClient =
    HttpClient(Darwin).withCommonConfig()
