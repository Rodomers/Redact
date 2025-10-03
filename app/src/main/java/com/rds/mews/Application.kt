package com.rds.mews

import android.app.Application

class MewsApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        MewsRepository.initialize(this)
    }
}