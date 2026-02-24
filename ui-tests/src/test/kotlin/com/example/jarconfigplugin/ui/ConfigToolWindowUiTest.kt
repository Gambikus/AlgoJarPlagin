package com.example.jarconfigplugin.ui

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.Duration

/**
 * UI-level tests for the JAR Config plugin tool windows and settings.
 *
 * Prerequisites:
 *   - Run `./gradlew runIdeForUiTests` in the project root first (keeps IDE running).
 *   - Set REMOTE_ROBOT_URL env var (default: http://localhost:8082) or pass -PrunUiTests.
 *
 * Tests are ordered — earlier tests open tool windows that later tests rely on.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ConfigToolWindowUiTest {

    companion object {
        private val robotUrl = System.getenv("REMOTE_ROBOT_URL") ?: "http://localhost:8082"
        private lateinit var robot: RemoteRobot

        @BeforeAll
        @JvmStatic
        fun connect() {
            robot = RemoteRobot(robotUrl)
            // Wait for the IDE to be fully loaded (up to 120s — IDE startup with plugins takes time)
            // NOTE: the IDE window must NOT be iconified (minimized) — Remote Robot
            // cannot find components in a minimized frame.
            waitFor(Duration.ofSeconds(120), Duration.ofSeconds(3)) {
                runCatching { robot.find<ComponentFixture>(byXpath("//div[@class='IdeFrameImpl']")) }.isSuccess
            }
        }
    }

    @Test
    @Order(1)
    fun `JAR Config tool window is present and can be opened`() {
        val ideFrame = robot.find<ContainerFixture>(byXpath("//div[@class='IdeFrameImpl']"))

        ideFrame.find<JMenuBarFixture>(byXpath("//div[@class='MenuBar']"))
            .select("Tools", "Open JAR Config")

        waitFor(Duration.ofSeconds(10)) {
            runCatching {
                ideFrame.find<ComponentFixture>(byXpath("//div[@text='JAR Config']"))
            }.isSuccess
        }
    }

    @Test
    @Order(2)
    fun `Config panel contains Build and Submit button`() {
        val ideFrame = robot.find<ContainerFixture>(byXpath("//div[@class='IdeFrameImpl']"))

        waitFor(Duration.ofSeconds(10)) {
            runCatching {
                ideFrame.find<JButtonFixture>(byXpath("//div[@text='Build & Submit']"))
            }.isSuccess
        }

        val button = ideFrame.find<JButtonFixture>(byXpath("//div[@text='Build & Submit']"))
        assertTrue(button.isShowing, "Build & Submit button should be visible")
    }

    @Test
    @Order(3)
    fun `Config panel has Clear button for build log`() {
        val ideFrame = robot.find<ContainerFixture>(byXpath("//div[@class='IdeFrameImpl']"))

        // "Clear" button sits next to "Build & Submit" above the Build Log area
        waitFor(Duration.ofSeconds(5)) {
            runCatching {
                ideFrame.find<JButtonFixture>(byXpath("//div[@text='Clear']"))
            }.isSuccess
        }
        val clearButton = ideFrame.find<JButtonFixture>(byXpath("//div[@text='Clear']"))
        assertTrue(clearButton.isShowing, "Clear button should be visible in the Config panel")
    }

    @Test
    @Order(4)
    fun `Main class field is editable and accepts input`() {
        val ideFrame = robot.find<ContainerFixture>(byXpath("//div[@class='IdeFrameImpl']"))

        val mainClassField = ideFrame.find<JTextFieldFixture>(
            byXpath("//div[@class='JBTextField' and @visible_text='algorithms.Main']")
        )

        mainClassField.click()
        mainClassField.text = "com.example.MyAlgorithm"
        assertEquals("com.example.MyAlgorithm", mainClassField.text)

        // Restore default so the test is repeatable
        mainClassField.text = "algorithms.Main"
    }

    @Test
    @Order(5)
    fun `JAR Monitor tool window has Live Monitor and Results tabs`() {
        val ideFrame = robot.find<ContainerFixture>(byXpath("//div[@class='IdeFrameImpl']"))

        waitFor(Duration.ofSeconds(5)) {
            runCatching {
                ideFrame.find<ComponentFixture>(byXpath("//div[@text='JAR Monitor']")).click()
            }.isSuccess
        }

        waitFor(Duration.ofSeconds(10)) {
            runCatching {
                ideFrame.find<ComponentFixture>(byXpath("//div[@text='Live Monitor']"))
            }.isSuccess
        }

        assertTrue(
            runCatching {
                ideFrame.find<ComponentFixture>(byXpath("//div[@text='Results']"))
            }.isSuccess,
            "Results tab should be present in JAR Monitor"
        )
    }

    @Test
    @Order(6)
    fun `Live Monitor panel has editable Job ID combo box`() {
        val ideFrame = robot.find<ContainerFixture>(byXpath("//div[@class='IdeFrameImpl']"))
        ideFrame.find<ComponentFixture>(byXpath("//div[@text='Live Monitor']")).click()

        // Job ID is now a JComboBox (editable, stores history) — not a plain text field
        waitFor(Duration.ofSeconds(5)) {
            runCatching {
                ideFrame.find<ComponentFixture>(byXpath("//div[@class='JComboBox']"))
            }.isSuccess
        }
        val combo = ideFrame.find<ComponentFixture>(byXpath("//div[@class='JComboBox']"))
        assertTrue(combo.isShowing, "Editable Job ID combo box should be visible in Live Monitor")
    }

    @Test
    @Order(7)
    fun `Live Monitor panel has Start and Stop polling buttons`() {
        val ideFrame = robot.find<ContainerFixture>(byXpath("//div[@class='IdeFrameImpl']"))
        ideFrame.find<ComponentFixture>(byXpath("//div[@text='Live Monitor']")).click()

        waitFor(Duration.ofSeconds(5)) {
            runCatching {
                ideFrame.find<JButtonFixture>(byXpath("//div[@text='Start']"))
            }.isSuccess
        }
        assertTrue(
            runCatching {
                ideFrame.find<JButtonFixture>(byXpath("//div[@text='Stop']"))
            }.isSuccess,
            "Stop button should be visible in Live Monitor"
        )
    }

    @Test
    @Order(8)
    fun `Results panel has Table and Chart sub-tabs`() {
        val ideFrame = robot.find<ContainerFixture>(byXpath("//div[@class='IdeFrameImpl']"))
        ideFrame.find<ComponentFixture>(byXpath("//div[@text='Results']")).click()

        waitFor(Duration.ofSeconds(10)) {
            runCatching {
                ideFrame.find<ComponentFixture>(byXpath("//div[@text='Table']"))
            }.isSuccess
        }
        assertTrue(
            runCatching {
                ideFrame.find<ComponentFixture>(byXpath("//div[@text='Chart']"))
            }.isSuccess,
            "Chart sub-tab should be present inside the Results panel"
        )
    }

    @Test
    @Order(9)
    fun `Results panel has Load Results and export buttons`() {
        val ideFrame = robot.find<ContainerFixture>(byXpath("//div[@class='IdeFrameImpl']"))
        ideFrame.find<ComponentFixture>(byXpath("//div[@text='Results']")).click()

        waitFor(Duration.ofSeconds(5)) {
            runCatching {
                ideFrame.find<JButtonFixture>(byXpath("//div[@text='Load Results']"))
            }.isSuccess
        }
        assertTrue(
            runCatching { ideFrame.find<JButtonFixture>(byXpath("//div[@text='Export CSV']")) }.isSuccess,
            "Export CSV button should be visible"
        )
        assertTrue(
            runCatching { ideFrame.find<JButtonFixture>(byXpath("//div[@text='Export JSON']")) }.isSuccess,
            "Export JSON button should be visible"
        )
    }

    @Test
    @Order(10)
    fun `Chart tab shows scatter plot axis controls`() {
        val ideFrame = robot.find<ContainerFixture>(byXpath("//div[@class='IdeFrameImpl']"))
        ideFrame.find<ComponentFixture>(byXpath("//div[@text='Results']")).click()
        ideFrame.find<ComponentFixture>(byXpath("//div[@text='Chart']")).click()

        // X axis label and selector
        waitFor(Duration.ofSeconds(5)) {
            runCatching {
                ideFrame.find<ComponentFixture>(byXpath("//div[@text='X axis:']"))
            }.isSuccess
        }

        // Point size label (has leading spaces in source — use contains())
        assertTrue(
            runCatching {
                ideFrame.find<ComponentFixture>(byXpath("//div[contains(@text,'Point size')]"))
            }.isSuccess,
            "Point size label should be visible in Chart toolbar"
        )
    }

    @Test
    @Order(11)
    fun `Chart tab X axis selector has expected options`() {
        val ideFrame = robot.find<ContainerFixture>(byXpath("//div[@class='IdeFrameImpl']"))
        ideFrame.find<ComponentFixture>(byXpath("//div[@text='Results']")).click()
        ideFrame.find<ComponentFixture>(byXpath("//div[@text='Chart']")).click()

        // Default selection is "Agents" — the first option in Param enum
        waitFor(Duration.ofSeconds(5)) {
            runCatching {
                ideFrame.find<ComponentFixture>(byXpath("//div[@class='JComboBox' and @visible_text='Agents']"))
            }.isSuccess
        }
        val xCombo = ideFrame.find<ComponentFixture>(byXpath("//div[@class='JComboBox' and @visible_text='Agents']"))
        assertTrue(xCombo.isShowing, "X axis combo should show 'Agents' as default selection")
    }

    @Test
    @Order(12)
    fun `Settings page shows JAR Config Plugin section with all fields and test buttons`() {
        robot.find<ContainerFixture>(byXpath("//div[@class='IdeFrameImpl']")).keyboard {
            hotKey(java.awt.event.KeyEvent.VK_CONTROL, java.awt.event.KeyEvent.VK_ALT, java.awt.event.KeyEvent.VK_S)
        }

        waitFor(Duration.ofSeconds(15)) {
            runCatching {
                robot.find<ComponentFixture>(byXpath("//div[@class='MyDialog']"))
            }.isSuccess
        }

        val settingsDialog = robot.find<ContainerFixture>(byXpath("//div[@class='MyDialog']"))

        // Navigate to our settings page
        waitFor(Duration.ofSeconds(10)) {
            runCatching {
                settingsDialog.find<ComponentFixture>(byXpath("//div[@text='JAR Config Plugin']")).click()
            }.isSuccess
        }

        // Coordinator URL field with its default value
        waitFor(Duration.ofSeconds(5)) {
            runCatching {
                settingsDialog.find<JTextFieldFixture>(byXpath("//div[@visible_text='http://localhost:8081']"))
            }.isSuccess
        }

        // Test Coordinator button (new)
        assertTrue(
            runCatching {
                settingsDialog.find<JButtonFixture>(byXpath("//div[@text='Test Coordinator']"))
            }.isSuccess,
            "Test Coordinator button should be visible in Settings"
        )

        // Test S3 Connection button (new)
        assertTrue(
            runCatching {
                settingsDialog.find<JButtonFixture>(byXpath("//div[@text='Test S3 Connection']"))
            }.isSuccess,
            "Test S3 Connection button should be visible in Settings"
        )

        settingsDialog.find<JButtonFixture>(byXpath("//div[@text='Cancel']")).click()
    }
}
