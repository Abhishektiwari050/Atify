package io.github.sekademi.spotufi.data.api

import com.metrolist.innertube.YouTube
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP network client providing a single pooled [OkHttpClient] instance
 * across the app. Sharing the [ConnectionPool] and thread pools reduces socket churn,
 * memory overhead, and TLS handshake latency.
 */
object NetworkClient {
    val sharedConnectionPool = ConnectionPool(8, 5, TimeUnit.MINUTES)

    val baseOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(sharedConnectionPool)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .proxy(YouTube.proxy)
            .build()
    }
}
