package com.example.jarconfigplugin.model

import com.fasterxml.jackson.annotation.JsonProperty

/** GET /api/v1/jobs/{jobId}/results response */
data class JobResult(
    @JsonProperty("jobId") val jobId: String,
    @JsonProperty("results") val results: List<TaskResult>
)

data class TaskResult(
    @JsonProperty("taskId") val taskId: String,
    @JsonProperty("algorithm") val algorithm: String?,
    @JsonProperty("iterations") val iterations: Int?,
    @JsonProperty("agents") val agents: Int?,
    @JsonProperty("dimension") val dimension: Int?,
    @JsonProperty("status") val status: TaskStatus = TaskStatus.DONE,
    @JsonProperty("runtimeMs") val runtimeMs: Long?,
    @JsonProperty("iter") val iter: Int?,
    @JsonProperty("fopt") val fopt: Double?,
    @JsonProperty("bestPos") val bestPos: List<Double>?,
    @JsonProperty("assignedTo") val assignedTo: String?
)

enum class TaskStatus {
    NEW, RUNNING, DONE, FAILED
}
