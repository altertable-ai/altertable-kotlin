package com.altertable.example

import android.app.Application
import ai.altertable.sdk.AltertableClient
import ai.altertable.sdk.AltertableConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AltertableExampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Altertable
        val config = AltertableConfig(
            apiKey = "example_api_key_12345",
            environment = "development",
            debug = true
        )
        
        // Ensure configuration runs
        CoroutineScope(Dispatchers.IO).launch {
            AltertableClient.configure(config)
        }
    }
}
