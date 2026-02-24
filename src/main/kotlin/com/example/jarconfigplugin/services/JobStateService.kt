package com.example.jarconfigplugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Project-scoped service that holds the current job ID
 * and notifies listeners (MonitorPanel) when a new job is submitted.
 */
@Service(Service.Level.PROJECT)
class JobStateService(@Suppress("unused") private val project: Project) {

    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val jobHistory = CopyOnWriteArrayList<String>()

    @Volatile
    var currentJobId: String = ""
        private set

    fun submitJobId(jobId: String) {
        jobHistory.add(0, jobId)
        currentJobId = jobId
        listeners.forEach { it(jobId) }
    }

    fun getJobHistory(): List<String> = jobHistory.toList()

    fun addJobListener(listener: (String) -> Unit) {
        listeners.add(listener)
    }

    fun removeJobListener(listener: (String) -> Unit) {
        listeners.remove(listener)
    }
}
