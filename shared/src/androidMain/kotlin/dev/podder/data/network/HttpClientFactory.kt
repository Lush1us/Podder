package dev.podder.data.network

import dev.podder.logging.LogEvent
import dev.podder.logging.LogLevel
import dev.podder.logging.PodderLogger
import dev.podder.logging.Subsystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun createHttpClient(logger: PodderLogger?): HttpClient =
    HttpClient(OkHttp) {
        engine {
            if (logger != null) {
                config {
                    addInterceptor { chain ->
                        val request = chain.request()
                        val start = System.currentTimeMillis()
                        try {
                            val response = chain.proceed(request)
                            val latencyMs = System.currentTimeMillis() - start
                            val bodyBytes = response.body?.contentLength() ?: -1L
                            logger.log(
                                LogLevel.INFO, Subsystem.NETWORK,
                                LogEvent.Network.RequestCompleted(
                                    url        = request.url.toString(),
                                    statusCode = response.code,
                                    latencyMs  = latencyMs,
                                    bodyBytes  = bodyBytes,
                                )
                            )
                            response
                        } catch (e: Exception) {
                            logger.log(
                                LogLevel.ERROR, Subsystem.NETWORK,
                                LogEvent.Network.RequestFailed(
                                    url    = request.url.toString(),
                                    reason = e.message ?: "unknown",
                                )
                            )
                            throw e
                        }
                    }
                }
            }
        }
    }.withCommonConfig()
