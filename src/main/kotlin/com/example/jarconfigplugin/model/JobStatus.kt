package com.example.jarconfigplugin.model

import com.fasterxml.jackson.annotation.JsonProperty

/** GET /api/v1/jobs/{jobId} response — job-level status with progress counters */
data class JobStatus(
    @JsonProperty("jobId") val jobId: String,
    @JsonProperty("status") val status: String,
    @JsonProperty("totalTasks") val totalTasks: Int,
    @JsonProperty("completedTasks") val completedTasks: Int,
    @JsonProperty("failedTasks") val failedTasks: Int
)
