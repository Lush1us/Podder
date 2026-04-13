package dev.podder.data.network

import dev.podder.logging.PodderLogger
import io.ktor.client.HttpClient

expect fun createHttpClient(logger: PodderLogger?): HttpClient
