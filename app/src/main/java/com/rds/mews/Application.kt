package com.rds.mews

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.rds.mews.core.MewsImageLoaderFactory
import com.rds.mews.repositories.MewsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MewsApplication: Application(), ImageLoaderFactory {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        MewsRepository.initialize(this, applicationScope)
    }

    override fun newImageLoader(): ImageLoader {
        return MewsImageLoaderFactory(this).newImageLoader()
    }
}