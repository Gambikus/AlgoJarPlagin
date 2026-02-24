package com.example.jarconfigplugin.model

import com.fasterxml.jackson.annotation.JsonProperty

/** Single SPOT node status entry from GET /api/v1/spots */
data class SpotStatus(
    @JsonProperty("spotId") val spotId: String,
    @JsonProperty("status") val status: String,
    @JsonProperty("cpuLoad") val cpuLoad: Double,
    @JsonProperty("runningTasks") val runningTasks: Int,
    @JsonProperty("totalCores") val totalCores: Int,
    @JsonProperty("lastHeartbeat") val lastHeartbeat: String?
)
