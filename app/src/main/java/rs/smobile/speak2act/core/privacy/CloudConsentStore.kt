package rs.smobile.speak2act.core.privacy

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's one-time answer to "may Speak2Act use the cloud when the device can't understand?".
 */
interface CloudConsentStore {
    /** `true` = allowed, `false` = on-device only, `null` = not asked yet. */
    val granted: StateFlow<Boolean?>

    suspend fun set(granted: Boolean)
}

/**
 * [CloudConsentStore] persisted in SharedPreferences under [KEY].
 *
 * TODO(H4): move to Preferences DataStore once `androidx.datastore:datastore-preferences` is approved.
 */
@Singleton
class SharedPreferencesCloudConsentStore @Inject constructor(
    @param:ApplicationContext context: Context,
) : CloudConsentStore {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    // Loaded once at startup; a tiny file, so reading it eagerly is fine.
    private val _granted = MutableStateFlow(
        if (preferences.contains(KEY)) preferences.getBoolean(KEY, false) else null
    )
    override val granted: StateFlow<Boolean?> = _granted.asStateFlow()

    override suspend fun set(granted: Boolean) {
        withContext(Dispatchers.IO) {
            preferences.edit(commit = true) { putBoolean(KEY, granted) }
        }
        _granted.value = granted
    }

    private companion object {
        private const val PREFERENCES_NAME = "privacy"
        private const val KEY = "cloud_fallback_consent"
    }
}
