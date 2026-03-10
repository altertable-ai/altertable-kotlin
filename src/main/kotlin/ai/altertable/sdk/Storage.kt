package ai.altertable.sdk

import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import androidx.datastore.core.DataStore

interface StorageApi {
    fun getItem(key: String): String?
    fun setItem(key: String, value: String)
    fun removeItem(key: String)
    fun migrate(from: String, to: String)
}

class InMemoryStorage : StorageApi {
    private val store = ConcurrentHashMap<String, String>()

    override fun getItem(key: String): String? = store[key]

    override fun setItem(key: String, value: String) {
        store[key] = value
    }

    override fun removeItem(key: String) {
        store.remove(key)
    }

    override fun migrate(from: String, to: String) {
        val value = store.remove(from)
        if (value != null) {
            store[to] = value
        }
    }
}

class DataStoreStorage(
    private val dataStore: DataStore<Preferences>
) : StorageApi {

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    override fun getItem(key: String): String? {
        val prefKey = stringPreferencesKey(key)
        return try {
            runBlocking {
                dataStore.data.map { preferences -> preferences[prefKey] }.first()
            }
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
            null
        }
    }

    override fun setItem(key: String, value: String) {
        val prefKey = stringPreferencesKey(key)
        runBlocking {
            dataStore.edit { preferences ->
                preferences[prefKey] = value
            }
        }
    }

    override fun removeItem(key: String) {
        val prefKey = stringPreferencesKey(key)
        runBlocking {
            dataStore.edit { preferences ->
                preferences.remove(prefKey)
            }
        }
    }

    override fun migrate(from: String, to: String) {
        val fromKey = stringPreferencesKey(from)
        val toKey = stringPreferencesKey(to)
        runBlocking {
            dataStore.edit { preferences ->
                val value = preferences[fromKey]
                if (value != null) {
                    preferences.remove(fromKey)
                    preferences[toKey] = value
                }
            }
        }
    }
}
