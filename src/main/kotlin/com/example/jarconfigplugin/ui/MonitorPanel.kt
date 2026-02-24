package com.example.jarconfigplugin.ui

import com.example.jarconfigplugin.model.SpotStatus
import com.example.jarconfigplugin.model.TaskResult
import com.example.jarconfigplugin.model.TaskStatus
import com.example.jarconfigplugin.services.CoordinatorService
import com.example.jarconfigplugin.services.JobStateService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

class MonitorPanel(private val project: Project, parentDisposable: Disposable) : JPanel(BorderLayout()) {

    private val log = thisLogger()
    private val coordinatorService get() = project.getService(CoordinatorService::class.java)

    @Volatile
    private var currentJobId: String = ""
    @Volatile
    private var pollingFuture: ScheduledFuture<*>? = null

    private val spotsModel = SpotsTableModel()
    private val tasksModel = TasksTableModel()

    private val jobComboModel = DefaultComboBoxModel<String>()
    private val jobCombo = JComboBox(jobComboModel).apply {
        isEditable = true
        toolTipText = "Enter Job ID or select from history"
    }
    private val startButton = JButton("Start")
    private val stopButton = JButton("Stop").apply { isEnabled = false }
    private val statusLabel = JBLabel("Idle")

    private val spotsTable = JTable(spotsModel).apply {
        autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
    }
    private val tasksTable = JTable(tasksModel).apply {
        autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
    }

    init {
        border = JBUI.Borders.empty(4)
        buildUi()
        setupRenderers()
        startButton.addActionListener { startPolling() }
        stopButton.addActionListener { stopPolling() }

        val jobStateService = project.getService(JobStateService::class.java)

        // Populate from session history on open
        jobStateService.getJobHistory().forEach { jobComboModel.addElement(it) }

        val jobListener: (String) -> Unit = { jobId ->
            ApplicationManager.getApplication().invokeLater {
                jobComboModel.insertElementAt(jobId, 0)
                jobCombo.selectedIndex = 0
                startPolling()
            }
        }

        jobStateService.addJobListener(jobListener)

        Disposer.register(parentDisposable) {
            stopPolling()
            jobStateService.removeJobListener(jobListener)
        }
    }

    private fun setupRenderers() {
        spotsTable.columnModel.getColumn(2).cellRenderer = PercentRenderer()
        tasksTable.columnModel.getColumn(2).cellRenderer = StatusRenderer()
    }

    private fun buildUi() {
        val controlBar = JPanel(BorderLayout(4, 0)).apply {
            border = JBUI.Borders.emptyBottom(6)
            add(JBLabel("Job ID:"), BorderLayout.WEST)
            add(jobCombo, BorderLayout.CENTER)
            val btnPanel = JPanel().apply {
                add(startButton)
                add(stopButton)
                add(statusLabel)
            }
            add(btnPanel, BorderLayout.EAST)
        }

        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
            resizeWeight = 0.35
            topComponent = JPanel(BorderLayout()).apply {
                border = BorderFactory.createTitledBorder("SPOT Nodes")
                add(JBScrollPane(spotsTable), BorderLayout.CENTER)
            }
            bottomComponent = JPanel(BorderLayout()).apply {
                border = BorderFactory.createTitledBorder("Tasks (completed)")
                add(JBScrollPane(tasksTable), BorderLayout.CENTER)
            }
        }

        add(controlBar, BorderLayout.NORTH)
        add(splitPane, BorderLayout.CENTER)
    }

    fun startMonitoringJob(jobId: String) {
        jobCombo.selectedItem = jobId
        startPolling()
    }

    private fun selectedJobId(): String =
        (jobCombo.editor?.item as? String)?.trim()
            ?: jobCombo.selectedItem?.toString()?.trim()
            ?: ""

    private fun startPolling() {
        val jobId = selectedJobId()
        if (jobId.isEmpty()) {
            Messages.showWarningDialog(project, "Please enter a Job ID.", "No Job ID")
            return
        }
        currentJobId = jobId
        pollingFuture?.cancel(false)

        statusLabel.text = "Polling…"
        startButton.isEnabled = false
        stopButton.isEnabled = true

        pollingFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            ::poll, 0L, 4L, TimeUnit.SECONDS
        )
    }

    private fun stopPolling() {
        pollingFuture?.cancel(false)
        pollingFuture = null
        ApplicationManager.getApplication().invokeLater {
            statusLabel.text = "Stopped"
            startButton.isEnabled = true
            stopButton.isEnabled = false
        }
    }

    private fun poll() {
        try {
            val spots = coordinatorService.getSpots()
            val jobStatus = if (currentJobId.isNotEmpty()) coordinatorService.getJobStatus(currentJobId) else null
            val jobResults = if (currentJobId.isNotEmpty()) {
                try { coordinatorService.getJobResults(currentJobId) } catch (_: Exception) { null }
            } else null

            val activeFuture = pollingFuture

            ApplicationManager.getApplication().invokeLater {
                spotsModel.update(spots)
                jobResults?.let { tasksModel.update(it.results) }

                // Job is terminal when coordinator reports COMPLETED, FAILED, or CANCELLED
                val isFinished = jobStatus != null &&
                    (jobStatus.status == "COMPLETED" || jobStatus.status == "FAILED" || jobStatus.status == "CANCELLED")

                if (isFinished && pollingFuture === activeFuture) {
                    pollingFuture?.cancel(false)
                    pollingFuture = null
                    val done = jobStatus!!.completedTasks
                    val total = jobStatus.totalTasks
                    statusLabel.text = "Done: $done/$total tasks (${jobStatus.status})"
                    startButton.isEnabled = true
                    stopButton.isEnabled = false
                } else if (pollingFuture != null) {
                    val now = java.time.LocalTime.now()
                    val progress = jobStatus?.let { "${it.completedTasks}/${it.totalTasks}" } ?: "…"
                    statusLabel.text = "[$progress] %02d:%02d:%02d".format(now.hour, now.minute, now.second)
                }
            }
        } catch (e: Exception) {
            log.warn("Polling error: ${e.message}")
            ApplicationManager.getApplication().invokeLater {
                statusLabel.text = "Error: ${e.message?.take(60)}"
            }
        }
    }
}

private class SpotsTableModel : AbstractTableModel() {
    private val columns = listOf("Spot ID", "Status", "CPU %", "Running Tasks", "Total Cores", "Last Heartbeat")
    private var rows: List<SpotStatus> = emptyList()

    fun update(data: List<SpotStatus>) {
        rows = data
        fireTableDataChanged()
    }

    override fun getRowCount() = rows.size
    override fun getColumnCount() = columns.size
    override fun getColumnName(col: Int) = columns[col]
    override fun getValueAt(row: Int, col: Int): Any? = rows[row].run {
        when (col) {
            0 -> spotId; 1 -> status; 2 -> cpuLoad; 3 -> runningTasks; 4 -> totalCores; 5 -> lastHeartbeat ?: "—"
            else -> null
        }
    }
}

private class TasksTableModel : AbstractTableModel() {
    private val columns = listOf("Task ID", "Algorithm", "Status", "Node", "Iter", "fopt", "Runtime (ms)")
    private var rows: List<TaskResult> = emptyList()

    fun update(data: List<TaskResult>) {
        rows = data
        fireTableDataChanged()
    }

    override fun getRowCount() = rows.size
    override fun getColumnCount() = columns.size
    override fun getColumnName(col: Int) = columns[col]
    override fun getValueAt(row: Int, col: Int): Any? = rows[row].run {
        when (col) {
            0 -> taskId; 1 -> algorithm ?: "—"; 2 -> status; 3 -> assignedTo ?: "—"
            4 -> iter ?: "—"; 5 -> fopt ?: "—"; 6 -> runtimeMs ?: "—"
            else -> null
        }
    }
}

private class StatusRenderer : DefaultTableCellRenderer() {
    override fun setValue(value: Any?) {
        val status = value as? TaskStatus
        text = status?.name ?: value?.toString() ?: ""
        foreground = when (status) {
            TaskStatus.NEW -> Color(128, 128, 128)
            TaskStatus.RUNNING -> Color(0, 128, 0)
            TaskStatus.DONE -> Color(0, 100, 200)
            TaskStatus.FAILED -> Color(200, 0, 0)
            else -> null
        }
    }
}

private class PercentRenderer : DefaultTableCellRenderer() {
    override fun setValue(value: Any?) {
        text = (value as? Double)?.let { "%.1f%%".format(it) } ?: value?.toString() ?: ""
    }
}
