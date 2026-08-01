package io.github.anszom.rethink.setup.provision

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Caches the home Wi-Fi credentials entered in step 1 so the user never has to re-type them,
 * even across failed attempts or a full restart of the app. Provisioning frequently needs a
 * second try (the appliance drops the AP, the phone falls back to mobile data), and re-typing a
 * long WPA passphrase on a phone each time is the worst part of that loop.
 */
class CredentialStore @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var ssid: String
        get() = prefs.getString(KEY_SSID, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_SSID, value) }

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_PASSWORD, value) }

    private companion object {
        const val PREFS = "provisioning"
        const val KEY_SSID = "ssid"
        const val KEY_PASSWORD = "password"
    }
}
