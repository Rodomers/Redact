package com.rds.mews

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.rds.mews.core.SharedHttpClient
import com.rds.mews.repositories.MewsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Call

class MewsApplication : Application(), ImageLoaderFactory {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentProxyState: Boolean? = null
    private var activeClient: SharedHttpClient.JdkClient? = null
    private val clientLock = Any()

    override fun onCreate() {
        super.onCreate()
        MewsRepository.initialize(this, applicationScope)
    }

    override fun newImageLoader(): ImageLoader {
        val callFactory = Call.Factory { request ->
            val proxyEnabled = MewsRepository.proxyEnabled.value

            val clientToUse = synchronized(clientLock) {
                if (currentProxyState != proxyEnabled || activeClient == null) {
                    activeClient?.close()
                    activeClient = SharedHttpClient.createInstance(
                        serverIp = MewsRepository.PROXY_ADDRESS,
                        rssHubKey = MewsRepository.SERVER_KEY,
                        enableProxy = proxyEnabled
                    )
                    currentProxyState = proxyEnabled
                }
                activeClient!!.okHttpClient
            }

            clientToUse.newCall(request)
        }

        return ImageLoader.Builder(this)
            .callFactory(callFactory)
            .build()
    }
}