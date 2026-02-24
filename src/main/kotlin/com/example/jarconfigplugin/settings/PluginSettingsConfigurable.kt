package com.example.jarconfigplugin.settings

import com.example.jarconfigplugin.services.CoordinatorApiClient
import com.example.jarconfigplugin.services.S3Service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.GridLayout
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

class PluginSettingsConfigurable : Configurable {

    private var panel: DialogPanel? = null
    private val state: PluginSettingsState get() = PluginSettingsState.getInstance()

    private var editableAccessKey: String = ""
    private var editableSecretKey: String = ""

    private val statusLabel = JLabel(" ").apply { font = font.deriveFont(11f) }
    private val coordStatusLabel = JLabel(" ").apply { font = font.deriveFont(11f) }

    override fun getDisplayName(): String = "JAR Config Plugin"

    override fun createComponent(): JComponent {
        editableAccessKey = CredentialStore.s3AccessKey
        editableSecretKey = CredentialStore.s3SecretKey

        panel = panel {
            group("Coordinator") {
                row("URL:") {
                    textField()
                        .bindText(state::coordinatorUrl)
                        .align(AlignX.FILL)
                        .comment("REST API base URL, e.g. http://coordinator-host:8081")
                }
            }

            group("S3 / Yandex Cloud Object Storage") {
                row("Endpoint:") {
                    textField()
                        .bindText(state::s3Endpoint)
                        .align(AlignX.FILL)
                        .comment("https://storage.yandexcloud.net or custom S3-compatible URL")
                }
                row("Bucket:") {
                    textField()
                        .bindText(state::s3Bucket)
                        .align(AlignX.FILL)
                }
                row("Access Key:") {
                    textField()
                        .bindText(::editableAccessKey)
                        .align(AlignX.FILL)
                }
                row("Secret Key:") {
                    passwordField()
                        .bindText(::editableSecretKey)
                        .align(AlignX.FILL)
                }
                row("Key Prefix:") {
                    textField()
                        .bindText(state::s3KeyPrefix)
                        .align(AlignX.FILL)
                        .comment("S3 object key prefix for uploaded JARs, e.g. experiments")
                }
            }
        }

        val testCoordButton = JButton("Test Coordinator")
        testCoordButton.addActionListener {
            panel?.apply()
            val url = state.coordinatorUrl
            testCoordButton.isEnabled = false
            coordStatusLabel.foreground = Color(0x808080)
            coordStatusLabel.text = "Connecting…"
            Thread {
                val result = runCatching { CoordinatorApiClient.testConnection(url) }
                SwingUtilities.invokeLater {
                    testCoordButton.isEnabled = true
                    result.fold(
                        onSuccess = { msg ->
                            coordStatusLabel.foreground = Color(0x00AA00)
                            coordStatusLabel.text = "✓ $msg"
                        },
                        onFailure = { e ->
                            coordStatusLabel.foreground = Color(0xCC0000)
                            coordStatusLabel.text = "✗ ${e.message?.take(80)}"
                        }
                    )
                }
            }.also { it.isDaemon = true }.start()
        }

        val testS3Button = JButton("Test S3 Connection")
        testS3Button.addActionListener {
            panel?.apply()
            val endpoint = state.s3Endpoint
            val bucket = state.s3Bucket
            val accessKey = editableAccessKey
            val secretKey = editableSecretKey
            testS3Button.isEnabled = false
            statusLabel.foreground = Color(0x808080)
            statusLabel.text = "Connecting…"
            Thread {
                val executor = Executors.newSingleThreadExecutor()
                val result = try {
                    val future = executor.submit(Callable {
                        S3Service.testConnectionStatic(endpoint, bucket, accessKey, secretKey)
                    })
                    Result.success(future.get(12, TimeUnit.SECONDS))
                } catch (e: TimeoutException) {
                    Result.failure(RuntimeException("Connection timed out (12s)"))
                } catch (e: java.util.concurrent.ExecutionException) {
                    Result.failure(e.cause ?: e)
                } finally {
                    executor.shutdown()
                }
                SwingUtilities.invokeLater {
                    testS3Button.isEnabled = true
                    result.fold(
                        onSuccess = { msg ->
                            statusLabel.foreground = Color(0x00AA00)
                            statusLabel.text = "✓ $msg"
                        },
                        onFailure = { e ->
                            statusLabel.foreground = Color(0xCC0000)
                            statusLabel.text = "✗ ${e.message?.take(80)}"
                        }
                    )
                }
            }.also { it.isDaemon = true }.start()
        }

        val coordRow = JPanel(BorderLayout(8, 0)).apply {
            add(testCoordButton, BorderLayout.WEST)
            add(coordStatusLabel, BorderLayout.CENTER)
        }
        val s3Row = JPanel(BorderLayout(8, 0)).apply {
            add(testS3Button, BorderLayout.WEST)
            add(statusLabel, BorderLayout.CENTER)
        }
        val bottomRow = JPanel(GridLayout(2, 1, 0, 4)).apply {
            border = JBUI.Borders.empty(6, 0, 0, 0)
            add(coordRow)
            add(s3Row)
        }

        return JPanel(BorderLayout()).apply {
            add(panel!!, BorderLayout.CENTER)
            add(bottomRow, BorderLayout.SOUTH)
        }
    }

    override fun isModified(): Boolean = panel?.isModified() ?: false

    override fun apply() {
        panel?.apply()
        CredentialStore.s3AccessKey = editableAccessKey
        CredentialStore.s3SecretKey = editableSecretKey
    }

    override fun reset() {
        editableAccessKey = CredentialStore.s3AccessKey
        editableSecretKey = CredentialStore.s3SecretKey
        panel?.reset()
    }

    override fun disposeUIResources() {
        panel = null
    }
}
