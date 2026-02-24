package com.example.jarconfigplugin

import com.example.jarconfigplugin.settings.PluginSettingsState
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PluginSettingsStateTest : BasePlatformTestCase() {

    fun `test default coordinator URL is localhost`() {
        val settings = PluginSettingsState()
        assertEquals("http://localhost:8081", settings.coordinatorUrl)
    }

    fun `test default S3 endpoint points to Yandex`() {
        val settings = PluginSettingsState()
        assertEquals("https://storage.yandexcloud.net", settings.s3Endpoint)
    }

    fun `test default bucket is orhestra-algorithms`() {
        val settings = PluginSettingsState()
        assertEquals("orhestra-algorithms", settings.s3Bucket)
    }

    fun `test state serializes and deserializes via loadState`() {
        val original = PluginSettingsState().apply {
            coordinatorUrl = "http://custom-host:9999"
            s3Bucket = "my-bucket"
            s3Endpoint = "https://custom.s3.endpoint"
        }

        val restored = PluginSettingsState()
        restored.loadState(original)

        assertEquals("http://custom-host:9999", restored.coordinatorUrl)
        assertEquals("my-bucket", restored.s3Bucket)
        assertEquals("https://custom.s3.endpoint", restored.s3Endpoint)
    }

    fun `test getState returns same instance`() {
        val settings = PluginSettingsState()
        assertSame(settings, settings.state)
    }
}
