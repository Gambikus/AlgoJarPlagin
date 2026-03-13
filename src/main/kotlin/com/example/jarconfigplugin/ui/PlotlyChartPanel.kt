package com.example.jarconfigplugin.ui

import com.example.jarconfigplugin.model.TaskResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.swing.JPanel

/**
 * Interactive Plotly.js chart panel rendered via JCEF (embedded Chromium).
 * Falls back to [FoptChartPanel] if JCEF is unavailable.
 *
 * Use [createBestAvailable] factory.
 */
class PlotlyChartPanel(parentDisposable: Disposable) : JPanel(BorderLayout()), Disposable {

    private val browser = JBCefBrowser()
    private var ready = false
    private var pending: List<TaskResult>? = null
    private val tmpDir: Path = extractResources()

    init {
        Disposer.register(parentDisposable, this)
        add(browser.component, BorderLayout.CENTER)

        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (frame.isMain) {
                    ready = true
                    pending?.let { flush(it) }
                    pending = null
                }
            }
        }, browser.cefBrowser)

        browser.loadURL(tmpDir.resolve("fopt_chart.html").toUri().toURL().toExternalForm())
    }

    fun update(tasks: List<TaskResult>) {
        if (!ready) {
            pending = tasks
            return
        }
        flush(tasks)
    }

    private fun flush(tasks: List<TaskResult>) {
        val json = mapper.writeValueAsString(tasks.map { t ->
            val map = mutableMapOf<String, Any?>(
                "taskId"     to t.taskId,
                "algorithm"  to t.algorithm,
                "agents"     to t.agents,
                "iterations" to t.iterations,
                "dimension"  to t.dimension,
                "runtimeMs"  to t.runtimeMs,
                "iter"       to t.iter,
                "fopt"       to t.fopt,
                "assignedTo" to t.assignedTo
            )
            // Expose bestPos components as individual numeric fields
            t.bestPos?.forEachIndexed { i, v -> map["bestPos[$i]"] = v }
            map
        })
        val escaped = json.replace("\\", "\\\\").replace("`", "\\`")
        browser.cefBrowser.executeJavaScript("updateChart(`$escaped`);", browser.cefBrowser.url, 0)
    }

    override fun dispose() {
        browser.dispose()
        tmpDir.toFile().deleteRecursively()
    }

    private fun extractResources(): Path {
        val dir = Files.createTempDirectory("algojar-chart")
        listOf("fopt_chart.html", "plotly.min.js").forEach { name ->
            val stream = javaClass.classLoader.getResourceAsStream("chart/$name")
                ?: error("Missing resource: chart/$name")
            stream.use { Files.copy(it, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING) }
        }
        return dir
    }

    companion object {
        private val mapper = jacksonObjectMapper()

        fun createBestAvailable(parentDisposable: Disposable): ChartPanel =
            if (JBCefApp.isSupported()) PlotlyAdapter(parentDisposable)
            else SwingAdapter()
    }
}

interface ChartPanel {
    val component: JPanel
    fun update(tasks: List<TaskResult>)
}

private class PlotlyAdapter(parentDisposable: Disposable) : ChartPanel {
    private val panel = PlotlyChartPanel(parentDisposable)
    override val component get() = panel
    override fun update(tasks: List<TaskResult>) = panel.update(tasks)
}

private class SwingAdapter : ChartPanel {
    private val panel = FoptChartPanel()
    override val component get() = panel
    override fun update(tasks: List<TaskResult>) = panel.update(tasks)
}
