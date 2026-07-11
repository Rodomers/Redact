package com.rds.mews.core

import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.rds.mews.repositories.MewsRepository

class MewsImageLoaderFactory(private val context: Context) : ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        val enableProxy = MewsRepository.proxyEnabled.value
        val httpClient = SharedHttpClient.createInstance(
            serverIp = MewsRepository.HUB_ADDRESS,
            rssHubKey = MewsRepository.SERVER_KEY,
            enableProxy = enableProxy
        ).okHttpClient.newBuilder()
            .dispatcher(okhttp3.Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 10
            })
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                response.newBuilder()
                    .header("Cache-Control", "max-age=2592000")
                    .build()
            }
            .build()

        return ImageLoader.Builder(context)
            .okHttpClient(httpClient)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("mews_image_cache"))
                    .maxSizeBytes(1024 * 1024 * 50)
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(200)
            .build()
    }
}