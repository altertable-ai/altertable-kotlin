package com.altertable.example

import android.app.Application
import ai.altertable.sdk.android.AltertableAndroid

class AltertableExampleApp : Application() {
    override fun onCreate() {
        super.onCreate()

        AltertableAndroid.setup(this) {
            apiKey = BuildConfig.ALTERTABLE_API_KEY
            environment = "production"
            debug = true
        }
    }
}
