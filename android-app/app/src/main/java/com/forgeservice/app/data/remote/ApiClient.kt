package com.forgeservice.app.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Central place to configure connection to ForgeService server.
 *
 * For local development:
 *   - Android Emulator:   "http://10.0.2.2:8000"
 *   - Real phone on same Wi-Fi: "http://192.168.X.X:8000" (your PC LAN IP)
 *
 * For deployed server (Railway / Render / etc.):
 *   Use the public HTTPS URL from your Railway service (e.g. "https://forgeservice-production-abc123.up.railway.app")
 *   IMPORTANT: Must be https and without trailing slash. Update the baseUrl below!
 */
object ApiClient {

    // === CHANGE THIS FOR YOUR DEPLOYMENT ===
    // 1. Find your Railway public URL in the service page (e.g. https://forgeservice-production-abc123.up.railway.app )
    // 2. Replace below with it (https, no trailing slash)
    //
    // Local examples (commented):
    // private var baseUrl: String = "http://10.0.2.2:8000"
    // private var baseUrl: String = "http://192.168.1.74:8000"

    // Production Railway URL - UPDATE THIS!
    private var baseUrl: String = "https://YOUR-RAILWAY-URL.up.railway.app"

    val currentBaseUrl: String
        get() = baseUrl

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY   // See all requests in Logcat (great for debugging)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("$baseUrl/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: ForgeServiceApi by lazy {
        retrofit.create(ForgeServiceApi::class.java)
    }
}