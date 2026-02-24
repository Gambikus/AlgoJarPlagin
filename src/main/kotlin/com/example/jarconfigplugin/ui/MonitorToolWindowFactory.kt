package com.example.jarconfigplugin.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Registers the "JAR Monitor" Tool Window (bottom panel) with two tabs:
 * live monitoring and completed results.
 */
class MonitorToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()

        val monitorPanel = MonitorPanel(project, toolWindow.disposable)
        val monitorContent = contentFactory.createContent(monitorPanel, "Live Monitor", false)

        val resultsPanel = ResultsPanel(project, toolWindow.disposable)
        val resultsContent = contentFactory.createContent(resultsPanel, "Results", false)

        toolWindow.contentManager.addContent(monitorContent)
        toolWindow.contentManager.addContent(resultsContent)
    }
}
