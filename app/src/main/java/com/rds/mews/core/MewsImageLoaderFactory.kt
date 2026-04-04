package com.rds.mews.core

import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.rds.mews.repositories.MewsRepository

class MewsImageLoaderFactory(private val context: Context) : ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        val enableProxy = MewsRepository.proxyEnabled.value
        val httpClient = SharedHttpClient.createInstance(
            serverIp = MewsRepository.HUB_ADDRESS,
            rssHubKey = MewsRepository.SERVER_KEY,
            enableProxy = enableProxy
        ).okHttpClient

        return ImageLoader.Builder(context)
            .okHttpClient(httpClient)
            .build()
    }
}