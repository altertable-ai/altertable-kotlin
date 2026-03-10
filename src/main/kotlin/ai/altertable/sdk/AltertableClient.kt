package ai.altertable.sdk

class AltertableClient(val config: MobileConfig) {
    val storage: StorageApi
    val identityManager: IdentityManager
    val sessionManager: SessionManager

    init {
        // Since we don't have an Android context here natively for DataStore setup,
        // and tests might run on Linux CI, we use InMemoryStorage as a fallback or 
        // rely on a provider. In a real Android context, DataStoreStorage would be injected.
        // For the sake of the requirements, we default to InMemoryStorage here if no specific provider is given,
        // but typically one would construct this with an Android context to initialize DataStore.
        storage = InMemoryStorage() // Fallback initialized

        identityManager = IdentityManager(storage, config.apiKey, config.environment)
        sessionManager = SessionManager(storage, config.apiKey, config.environment)
    }

    fun identify(userId: String) {
        identityManager.identify(userId)
    }

    fun reset(resetDeviceId: Boolean = false) {
        identityManager.reset(resetDeviceId)
        sessionManager.renewSession()
    }

    @Suppress("UnusedPrivateProperty", "UnusedParameter", "UNUSED_PARAMETER")
    fun track(event: String, properties: Map<String, Any> = emptyMap()) {
        val sessionId = sessionManager.getSessionIdAndTouch()
        // TODO: Enqueue event with identity and session info (Phase 7+)
    }

    companion object {
        private var instance: AltertableClient? = null

        fun setup(config: MobileConfig): AltertableClient {
            return AltertableClient(config).also { instance = it }
        }

        fun shared(): AltertableClient {
            return instance ?: throw AltertableError("AltertableClient not configured. Call setup() first.")
        }
    }
}
