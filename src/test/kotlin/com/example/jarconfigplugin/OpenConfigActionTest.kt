package com.example.jarconfigplugin

import com.example.jarconfigplugin.actions.OpenConfigAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Platform test for [OpenConfigAction].
 * Runs in a "light" headless IntelliJ environment — no real IDE needed.
 *
 * BasePlatformTestCase uses JUnit 4 (required by IntelliJ Platform test framework).
 */
class OpenConfigActionTest : BasePlatformTestCase() {

    private val action = OpenConfigAction()

    fun `test action is enabled when project is open`() {
        val event = TestActionEvent.createTestEvent(action) { dataId ->
            when (dataId) {
                CommonDataKeys.PROJECT.name -> project
                else -> null
            }
        }
        action.update(event)
        assertTrue("Action should be enabled when project is open", event.presentation.isEnabled)
    }

    fun `test action is disabled when no project`() {
        val event = TestActionEvent.createTestEvent(action) { _ -> null }
        action.update(event)
        assertFalse("Action should be disabled when no project", event.presentation.isEnabled)
    }
}
