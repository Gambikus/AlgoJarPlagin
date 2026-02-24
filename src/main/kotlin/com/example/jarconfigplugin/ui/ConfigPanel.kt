package com.example.jarconfigplugin.ui

import com.example.jarconfigplugin.model.JobRequest
import com.example.jarconfigplugin.model.RangeSpec
import com.example.jarconfigplugin.services.CoordinatorService
import com.example.jarconfigplugin.services.JarBuildService
import com.example.jarconfigplugin.services.JobStateService
import com.example.jarconfigplugin.services.S3Service
import com.example.jarconfigplugin.settings.PluginSettingsState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

class ConfigPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val log = thisLogger()

    private val jarBuildService get() = project.getService(JarBuildService::class.java)
    private val s3Service get() = project.getService(S3Service::class.java)
    private val coordinatorService get() = project.getService(CoordinatorService::class.java)
    private val jobStateService get() = project.getService(JobStateService::class.java)

    private val moduleCombo: ComboBox<String> = ComboBox<String>().apply { isEditable = false }
    private val mainClassField = JBTextField("algorithms.Main")
    private val algorithmsField = JBTextField("sphere,rosenbrock")

    private val iterMinField = JBTextField("100")
    private val iterMaxField = JBTextField("300")
    private val iterStepField = JBTextField("100")

    private val agentsMinField = JBTextField("25")
    private val agentsMaxField = JBTextField("75")
    private val agentsStepField = JBTextField("25")

    private val dimMinField = JBTextField("1")
    private val dimMaxField = JBTextField("2")
    private val dimStepField = JBTextField("1")

    private val submitButton = JButton("Build & Submit")
    private val clearLogButton = JButton("Clear")
    private val statusArea = JBTextArea(8, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        font = Font(Font.MONOSPACED, Font.PLAIN, 11)
    }

    init {
        border = JBUI.Borders.empty(8)
        buildUi()
        refreshModules()
        submitButton.addActionListener { onSubmitClicked() }
        clearLogButton.addActionListener { statusArea.text = "" }
    }

    private fun buildUi() {
        val form = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(3, 4)
            weightx = 0.0
            weighty = 0.0
        }

        var row = 0

        fun addRow(labelText: String, vararg fields: JComponent) {
            gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0
            form.add(JBLabel(labelText), gbc)
            if (fields.size == 1) {
                gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 5
                form.add(fields[0], gbc)
                gbc.gridwidth = 1
            } else {
                fields.forEachIndexed { i, f ->
                    gbc.gridx = i + 1; gbc.weightx = if (i == fields.lastIndex) 1.0 else 0.3
                    form.add(f, gbc)
                }
            }
            row++
        }

        fun sectionHeader(title: String) {
            gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 6; gbc.weightx = 1.0
            form.add(JSeparator(), gbc)
            gbc.gridwidth = 1; row++
            gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 6
            form.add(JBLabel("<html><b>$title</b></html>"), gbc)
            gbc.gridwidth = 1; row++
        }

        sectionHeader("Module & Entry Point")
        addRow("Module:", moduleCombo)
        addRow("Main Class:", mainClassField)
        addRow("Algorithms:", algorithmsField)

        sectionHeader("Iterations (min / max / step)")
        addRow("Iterations:", iterMinField, iterMaxField, iterStepField)

        sectionHeader("Agents (min / max / step)")
        addRow("Agents:", agentsMinField, agentsMaxField, agentsStepField)

        sectionHeader("Dimension (min / max / step)")
        addRow("Dimension:", dimMinField, dimMaxField, dimStepField)

        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 6; gbc.weighty = 1.0
        form.add(JPanel(), gbc)

        val topPanel = JPanel(BorderLayout()).apply { add(form, BorderLayout.CENTER) }

        val logPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Build Log")
            add(JBScrollPane(statusArea), BorderLayout.CENTER)
        }
        val bottomPanel = JPanel(BorderLayout(4, 4)).apply {
            border = JBUI.Borders.emptyTop(8)
            add(JPanel(BorderLayout(4, 0)).apply {
                add(submitButton, BorderLayout.WEST)
                add(clearLogButton, BorderLayout.EAST)
            }, BorderLayout.NORTH)
            add(logPanel, BorderLayout.CENTER)
        }

        add(topPanel, BorderLayout.CENTER)
        add(bottomPanel, BorderLayout.SOUTH)
    }

    fun refreshModules() {
        val modules = jarBuildService.availableModules()
        moduleCombo.model = CollectionComboBoxModel(modules)
    }

    private fun onSubmitClicked() {
        val moduleName = moduleCombo.selectedItem as? String
        if (moduleName.isNullOrBlank()) {
            Messages.showWarningDialog(project, "Please select a module.", "No Module Selected")
            return
        }

        val request = buildJobRequest() ?: return
        submitButton.isEnabled = false
        appendStatus("Starting build and submit for module '$moduleName'…\n")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "JAR Config: Build & Submit", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "Building JAR…"
                    appendStatus("Step 1/3: Building JAR…\n")
                    val jarFile = jarBuildService.buildJar(moduleName, indicator) { line ->
                        appendStatus("  $line\n")
                    }
                    if (indicator.isCanceled) return
                    appendStatus("  Built: ${jarFile.name} (${jarFile.length() / 1024} KB)\n")

                    val settings = PluginSettingsState.getInstance()
                    val objectKey = "${settings.s3KeyPrefix.trim('/')}/${jarFile.name}"
                    indicator.text = "Uploading to S3…"
                    appendStatus("Step 2/3: Uploading to S3 as '$objectKey'…\n")
                    val s3Uri = s3Service.uploadJar(jarFile, objectKey, indicator)
                    if (indicator.isCanceled) return
                    appendStatus("  Uploaded: $s3Uri\n")

                    val finalRequest = request.copy(jarPath = s3Uri)
                    indicator.text = "Submitting job to Coordinator…"
                    appendStatus("Step 3/3: Submitting job to Coordinator…\n")
                    val response = coordinatorService.submitJob(finalRequest)
                    appendStatus("SUCCESS — Job ID: ${response.jobId}\n")

                    jobStateService.submitJobId(response.jobId)

                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(
                            project,
                            "Job submitted successfully!\nJob ID: ${response.jobId}\n\nMonitor tab will start tracking automatically.",
                            "Job Submitted"
                        )
                    }
                } catch (e: Exception) {
                    if (indicator.isCanceled) {
                        appendStatus("Cancelled by user.\n")
                    } else {
                        log.error("Build & Submit failed", e)
                        appendStatus("ERROR: ${e.message}\n")
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showErrorDialog(project, e.message ?: "Unknown error", "Build & Submit Failed")
                        }
                    }
                } finally {
                    ApplicationManager.getApplication().invokeLater {
                        submitButton.isEnabled = true
                    }
                }
            }
        })
    }

    private fun buildJobRequest(): JobRequest? {
        return try {
            val iterMin = iterMinField.text.trim().toInt()
            val iterMax = iterMaxField.text.trim().toInt()
            val iterStep = iterStepField.text.trim().toInt()
            val agMin = agentsMinField.text.trim().toInt()
            val agMax = agentsMaxField.text.trim().toInt()
            val agStep = agentsStepField.text.trim().toInt()
            val dMin = dimMinField.text.trim().toInt()
            val dMax = dimMaxField.text.trim().toInt()
            val dStep = dimStepField.text.trim().toInt()

            validateRange("Iterations", iterMin, iterMax, iterStep)
            validateRange("Agents", agMin, agMax, agStep)
            validateRange("Dimension", dMin, dMax, dStep)

            val algorithms = algorithmsField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (algorithms.isEmpty()) {
                Messages.showErrorDialog(project, "At least one algorithm must be specified.", "Invalid Input")
                return null
            }

            val mainClass = mainClassField.text.trim()
            if (mainClass.isEmpty()) {
                Messages.showErrorDialog(project, "Main class must be specified.", "Invalid Input")
                return null
            }

            JobRequest(
                jarPath = "",
                mainClass = mainClass,
                algorithms = algorithms,
                iterations = RangeSpec(iterMin, iterMax, iterStep),
                agents = RangeSpec(agMin, agMax, agStep),
                dimension = RangeSpec(dMin, dMax, dStep)
            )
        } catch (e: NumberFormatException) {
            Messages.showErrorDialog(project, "All min/max/step fields must be integers.", "Invalid Input")
            null
        } catch (e: IllegalArgumentException) {
            Messages.showErrorDialog(project, e.message ?: "Invalid parameter range.", "Invalid Input")
            null
        }
    }

    private fun validateRange(name: String, min: Int, max: Int, step: Int) {
        require(step > 0) { "$name step must be positive (got $step)" }
        require(min <= max) { "$name min ($min) must be ≤ max ($max)" }
    }

    private fun appendStatus(text: String) {
        ApplicationManager.getApplication().invokeLater {
            statusArea.append(text)
            statusArea.caretPosition = statusArea.document.length
        }
    }
}
