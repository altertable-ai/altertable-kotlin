package ai.altertable.sdk.android

import ai.altertable.sdk.Storage
import ai.altertable.sdk.AltertableInternal
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [Storage] implementation using [SharedPreferences].
 *
 * Use this for Android apps to persist SDK state across app restarts.
 */
@OptIn(AltertableInternal::class)
public class SharedPreferencesStorage(
    private val preferences: SharedPreferences,
) : Storage {

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        preferences.getString(key, null)
    }

    override suspend fun set(key: String, value: String) {
        withContext(Dispatchers.IO) {
            preferences.edit().putString(key, value).apply()
        }
    }

    override suspend fun remove(key: String) {
        withContext(Dispatchers.IO) {
            preferences.edit().remove(key).apply()
        }
    }

    override suspend fun migrate(from: String, to: String) {
        withContext(Dispatchers.IO) {
            val value = preferences.getString(from, null)
            if (value != null) {
                preferences.edit()
                    .putString(to, value)
                    .remove(from)
                    .apply()
            }
        }
    }

    public companion object {
        private const val PREFS_NAME = "altertable_sdk"

        /**
         * Creates storage using the application's default SharedPreferences.
         */
        public fun create(context: Context): SharedPreferencesStorage =
            SharedPreferencesStorage(
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            )
    }
}
