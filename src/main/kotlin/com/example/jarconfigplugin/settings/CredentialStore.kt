package com.example.jarconfigplugin.settings

/**
 * S3 credential accessor backed by [PluginSettingsState] (plain XML storage).
 * Simpler than PasswordSafe and works reliably in the sandbox IDE.
 */
object CredentialStore {

    var s3AccessKey: String
        get() = PluginSettingsState.getInstance().s3AccessKey
        set(value) { PluginSettingsState.getInstance().s3AccessKey = value }

    var s3SecretKey: String
        get() = PluginSettingsState.getInstance().s3SecretKey
        set(value) { PluginSettingsState.getInstance().s3SecretKey = value }
}
