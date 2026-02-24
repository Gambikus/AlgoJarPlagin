package com.example.jarconfigplugin.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Registers the "JAR Config" Tool Window (right panel).
 * DumbAware = available even during indexing.
 */
class ConfigToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ConfigPanel(project)
        val content = ContentFactory.getInstance()
            .createContent(panel, "Configuration", false)
        toolWindow.contentManager.addContent(content)
    }
}
