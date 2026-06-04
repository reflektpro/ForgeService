package com.forgeservice.app

import android.app.Application

/**
 * Application class. Later can be used for DI (Hilt/Koin), initializing Coil, etc.
 */
class ForgeServiceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // TODO: Initialize DI, preferences (server IP), etc.
    }
}