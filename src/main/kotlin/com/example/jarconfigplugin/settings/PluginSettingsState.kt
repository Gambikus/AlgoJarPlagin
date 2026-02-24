package com.example.jarconfigplugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Application-level persistent state for JARConfigPlugin settings.
 * Stored in <config>/options/JARConfigPlugin.xml
 *
 * S3 credentials (accessKey, secretKey) are stored separately via
 * [CredentialStore] using IntelliJ PasswordSafe.
 */
@State(
    name = "JARConfigPluginSettings",
    storages = [Storage("JARConfigPlugin.xml")]
)
class PluginSettingsState : PersistentStateComponent<PluginSettingsState> {

    var coordinatorUrl: String = "http://localhost:8081"

    var s3Endpoint: String = "https://storage.yandexcloud.net"
    var s3Bucket: String = "orhestra-algorithms"
    var s3KeyPrefix: String = "experiments"
    var s3AccessKey: String = ""
    var s3SecretKey: String = ""

    override fun getState(): PluginSettingsState = this

    override fun loadState(state: PluginSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): PluginSettingsState =
            ApplicationManager.getApplication()
                .getService(PluginSettingsState::class.java)
    }
}
