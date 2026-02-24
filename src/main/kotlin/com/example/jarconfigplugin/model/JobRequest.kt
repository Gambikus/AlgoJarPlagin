package com.example.jarconfigplugin.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * POST /api/jobs request body sent to the Coordinator.
 * Ranges (min/max/step) are expanded server-side into atomic tasks.
 */
data class JobRequest(
    @JsonProperty("jarPath") val jarPath: String,
    @JsonProperty("mainClass") val mainClass: String,
    @JsonProperty("algorithms") val algorithms: List<String>,
    @JsonProperty("iterations") val iterations: RangeSpec,
    @JsonProperty("agents") val agents: RangeSpec,
    @JsonProperty("dimension") val dimension: RangeSpec
)

data class RangeSpec(
    @JsonProperty("min") val min: Int,
    @JsonProperty("max") val max: Int,
    @JsonProperty("step") val step: Int
)

/** Response body from POST /api/jobs */
data class JobSubmitResponse(
    @JsonProperty("jobId") val jobId: String
)
