package com.altertable.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ai.altertable.sdk.AltertableClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AltertableAppUI()
        }
    }
}

@Composable
fun AltertableAppUI() {
    val coroutineScope = rememberCoroutineScope()
    var currentStep by remember { mutableStateOf("home") }
    var userId by remember { mutableStateOf("") }
    var eventResult by remember { mutableStateOf("") }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "Altertable Kotlin SDK Example", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(24.dp))
                
                when (currentStep) {
                    "home" -> {
                        Button(onClick = { currentStep = "signup" }) {
                            Text("Start Signup Funnel")
                        }
                    }
                    "signup" -> {
                        OutlinedTextField(
                            value = userId,
                            onValueChange = { userId = it },
                            label = { Text("Enter User ID or Email") }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            coroutineScope.launch {
                                // Event Tracking
                                AltertableClient.track(
                                    eventName = "signup_started",
                                    properties = mapOf("method" to "email")
                                )
                                // Identity Management
                                AltertableClient.identify(
                                    id = userId,
                                    properties = mapOf("plan" to "free")
                                )
                                // Alias
                                AltertableClient.alias(userId)
                                
                                currentStep = "dashboard"
                            }
                        }) {
                            Text("Complete Signup")
                        }
                    }
                    "dashboard" -> {
                        Text(text = "Welcome, $userId!")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            coroutineScope.launch {
                                AltertableClient.track(
                                    eventName = "feature_used",
                                    properties = mapOf("feature_name" to "dashboard_click")
                                )
                                eventResult = "Tracked 'feature_used' event!"
                            }
                        }) {
                            Text("Use Feature")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            coroutineScope.launch {
                                AltertableClient.reset()
                                currentStep = "home"
                                userId = ""
                                eventResult = ""
                            }
                        }) {
                            Text("Logout (Reset Identity)")
                        }
                        
                        if (eventResult.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(text = eventResult, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
