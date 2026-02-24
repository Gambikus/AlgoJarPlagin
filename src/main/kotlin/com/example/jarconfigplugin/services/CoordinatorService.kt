package com.example.jarconfigplugin.services

import com.example.jarconfigplugin.model.JobRequest
import com.example.jarconfigplugin.model.JobResult
import com.example.jarconfigplugin.model.JobStatus
import com.example.jarconfigplugin.model.JobSubmitResponse
import com.example.jarconfigplugin.model.SpotStatus
import com.example.jarconfigplugin.settings.PluginSettingsState
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * IntelliJ project-scoped service.
 * Delegates all HTTP work to [CoordinatorApiClient].
 * Caches the client and recreates it only when the coordinator URL changes.
 * Implements [Disposable] to shut down the underlying OkHttpClient on project close.
 */
@Service(Service.Level.PROJECT)
class CoordinatorService(@Suppress("unused") private val project: Project) : Disposable {

    private var cachedClient: CoordinatorApiClient? = null
    private var cachedUrl: String? = null

    @Synchronized
    private fun client(): CoordinatorApiClient {
        val currentUrl = PluginSettingsState.getInstance().coordinatorUrl
        val existing = cachedClient
        if (existing != null && cachedUrl == currentUrl) return existing

        existing?.shutdown()

        val newClient = CoordinatorApiClient(currentUrl)
        cachedClient = newClient
        cachedUrl = currentUrl
        return newClient
    }

    fun submitJob(request: JobRequest): JobSubmitResponse = client().submitJob(request)

    fun getJobStatus(jobId: String): JobStatus = client().getJobStatus(jobId)

    fun getJobResults(jobId: String): JobResult = client().getJobResults(jobId)

    fun getSpots(): List<SpotStatus> = client().getSpots()

    @Synchronized
    override fun dispose() {
        cachedClient?.shutdown()
        cachedClient = null
    }
}
