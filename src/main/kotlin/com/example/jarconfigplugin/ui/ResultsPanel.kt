package com.example.jarconfigplugin.ui

import com.example.jarconfigplugin.model.TaskResult
import com.example.jarconfigplugin.model.TaskStatus
import com.example.jarconfigplugin.services.CoordinatorService
import com.example.jarconfigplugin.services.JobStateService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.io.PrintWriter
import javax.swing.*
import javax.swing.table.AbstractTableModel

class ResultsPanel(private val project: Project, parentDisposable: Disposable) : JPanel(BorderLayout()) {

    private val log = thisLogger()
    private val coordinatorService get() = project.getService(CoordinatorService::class.java)

    private val resultsModel = ResultsTableModel()
    private val resultsTable = JTable(resultsModel).apply {
        autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
    }
    private val chartPanel = FoptChartPanel()

    private val jobIdField = JBTextField().apply { emptyText.text = "Job ID" }
    private val loadButton = JButton("Load Results")
    private val exportCsvButton = JButton("Export CSV")
    private val exportJsonButton = JButton("Export JSON")
    private val summaryLabel = JLabel(" ")

    init {
        border = JBUI.Borders.empty(4)
        buildUi()

        loadButton.addActionListener { loadResults() }
        exportCsvButton.addActionListener { exportCsv() }
        exportJsonButton.addActionListener { exportJson() }

        val jobListener: (String) -> Unit = { jobId ->
            ApplicationManager.getApplication().invokeLater {
                jobIdField.text = jobId
            }
        }
        val jobStateService = project.getService(JobStateService::class.java)
        jobStateService.addJobListener(jobListener)

        Disposer.register(parentDisposable) {
            jobStateService.removeJobListener(jobListener)
        }
    }

    private fun buildUi() {
        val topBar = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(JLabel("Job ID: "))
            add(jobIdField.apply { maximumSize = java.awt.Dimension(200, 30) })
            add(Box.createHorizontalStrut(8))
            add(loadButton)
            add(Box.createHorizontalGlue())
            add(summaryLabel)
            add(Box.createHorizontalStrut(8))
            add(exportCsvButton)
            add(exportJsonButton)
        }

        val tabs = JTabbedPane().apply {
            addTab("Table", JBScrollPane(resultsTable))
            addTab("Chart", chartPanel)
        }

        add(topBar, BorderLayout.NORTH)
        add(tabs, BorderLayout.CENTER)
    }

    fun loadResultsForJob(jobId: String) {
        jobIdField.text = jobId
        loadResults()
    }

    private fun loadResults() {
        val jobId = jobIdField.text.trim().ifEmpty {
            Messages.showWarningDialog(project, "Enter a Job ID first.", "No Job ID")
            return
        }

        loadButton.isEnabled = false
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Loading results…", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val result = coordinatorService.getJobResults(jobId)
                    val done = result.results.filter { it.status == TaskStatus.DONE }
                    ApplicationManager.getApplication().invokeLater {
                        resultsModel.update(done)
                        chartPanel.update(done)
                        val bestFopt = done.mapNotNull { it.fopt }.minOrNull()
                        summaryLabel.text = "Tasks: ${done.size} done  |  Best fopt: ${bestFopt?.let { "%.4f".format(it) } ?: "—"}"
                        loadButton.isEnabled = true
                    }
                } catch (e: Exception) {
                    log.error("Failed to load results for job $jobId", e)
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Error: ${e.message}", "Load Failed")
                        loadButton.isEnabled = true
                    }
                }
            }
        })
    }

    private fun exportCsv() {
        val rows = resultsModel.rows
        if (rows.isEmpty()) { showNoDataWarning(); return }

        saveFile("Export Results as CSV", "csv") { writer ->
            writer.println("taskId,algorithm,iterations,agents,dimension,runtimeMs,iter,fopt,bestPos")
            rows.forEach { r ->
                writer.println(
                    listOf(
                        csvEscape(r.taskId),
                        csvEscape(r.algorithm ?: ""),
                        r.iterations?.toString() ?: "",
                        r.agents?.toString() ?: "",
                        r.dimension?.toString() ?: "",
                        r.runtimeMs?.toString() ?: "",
                        r.iter?.toString() ?: "",
                        r.fopt?.toString() ?: "",
                        csvEscape(r.bestPos?.joinToString(";") ?: "")
                    ).joinToString(",")
                )
            }
        }
    }

    private fun exportJson() {
        val rows = resultsModel.rows
        if (rows.isEmpty()) { showNoDataWarning(); return }

        saveFile("Export Results as JSON", "json") { writer ->
            writer.print(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rows))
        }
    }

    private fun csvEscape(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun saveFile(title: String, extension: String, write: (PrintWriter) -> Unit) {
        val descriptor = FileSaverDescriptor(title, "", extension)
        val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val wrapper = dialog.save(null as java.nio.file.Path?, "results.$extension") ?: return
        try {
            wrapper.file.printWriter().use(write)
            Messages.showInfoMessage(
                project, "Saved to ${wrapper.file.absolutePath}", "Export Complete"
            )
        } catch (e: Exception) {
            log.error("Export failed", e)
            Messages.showErrorDialog(project, "Export failed: ${e.message}", "Error")
        }
    }

    private fun showNoDataWarning() {
        Messages.showWarningDialog(project, "No completed results to export.", "No Data")
    }

    companion object {
        private val mapper = jacksonObjectMapper()
    }
}

private class ResultsTableModel : AbstractTableModel() {
    private val columns = listOf("Task ID", "Algorithm", "Iter", "Agents", "Dim", "Runtime (ms)", "Iterations done", "fopt", "bestPos")
    var rows: List<TaskResult> = emptyList()

    fun update(data: List<TaskResult>) {
        rows = data
        fireTableDataChanged()
    }

    override fun getRowCount() = rows.size
    override fun getColumnCount() = columns.size
    override fun getColumnName(col: Int) = columns[col]
    override fun getValueAt(row: Int, col: Int): Any? = rows[row].run {
        when (col) {
            0 -> taskId
            1 -> algorithm
            2 -> iterations
            3 -> agents
            4 -> dimension
            5 -> runtimeMs ?: "—"
            6 -> iter ?: "—"
            7 -> fopt?.let { "%.4f".format(it) } ?: "—"
            8 -> bestPos?.let { pos ->
                pos.take(4).joinToString(", ", "[", if (pos.size > 4) "…]" else "]") { "%.3f".format(it) }
            } ?: "—"
            else -> null
        }
    }
}
