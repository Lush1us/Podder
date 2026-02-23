package dev.podder.data.network

import io.ktor.client.HttpClient

expect fun createHttpClient(): HttpClient
