package dev.podder.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun createHttpClient(): HttpClient =
    HttpClient(OkHttp).withCommonConfig()
