package com.example.jarconfigplugin.services

import com.example.jarconfigplugin.model.JobRequest
import com.example.jarconfigplugin.model.JobResult
import com.example.jarconfigplugin.model.JobStatus
import com.example.jarconfigplugin.model.JobSubmitResponse
import com.example.jarconfigplugin.model.SpotStatus
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Pure HTTP client for the Coordinator REST API.
 *
 * Has no dependency on IntelliJ Platform — fully unit-testable with WireMock.
 * [CoordinatorService] is the IntelliJ service wrapper that supplies [baseUrl].
 */
class CoordinatorApiClient(
    val baseUrl: String,
    private val httpClient: OkHttpClient = defaultClient()
) {

    private val json = "application/json; charset=utf-8".toMediaType()

    fun submitJob(request: JobRequest): JobSubmitResponse {
        val body = mapper.writeValueAsString(request).toRequestBody(json)
        val url = "${baseUrl.trimEnd('/')}/api/v1/jobs".toHttpUrl()
        val req = Request.Builder().url(url).post(body).build()
        return execute(req) { mapper.readValue(it) }
    }

    /** GET /api/v1/jobs/{jobId} — job-level status with progress counters */
    fun getJobStatus(jobId: String): JobStatus {
        val url = "${baseUrl.trimEnd('/')}/api/v1/jobs".toHttpUrl()
            .newBuilder()
            .addPathSegment(jobId)
            .build()
        val req = Request.Builder().url(url).get().build()
        return execute(req) { mapper.readValue(it) }
    }

    /** GET /api/v1/jobs/{jobId}/results — completed task results */
    fun getJobResults(jobId: String): JobResult {
        val url = "${baseUrl.trimEnd('/')}/api/v1/jobs".toHttpUrl()
            .newBuilder()
            .addPathSegment(jobId)
            .addPathSegment("results")
            .build()
        val req = Request.Builder().url(url).get().build()
        return execute(req) { mapper.readValue(it) }
    }

    fun getSpots(): List<SpotStatus> {
        val url = "${baseUrl.trimEnd('/')}/api/v1/spots".toHttpUrl()
        val req = Request.Builder().url(url).get().build()
        return execute(req) { body ->
            val wrapper = mapper.readValue<SpotsWrapper>(body)
            wrapper.spots
        }
    }

    fun shutdown() {
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    private fun <T> execute(request: Request, parse: (String) -> T): T {
        try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                    ?: throw CoordinatorException("Empty response body from ${request.url}")
                if (!response.isSuccessful) {
                    throw CoordinatorException("HTTP ${response.code}: $body")
                }
                return parse(body)
            }
        } catch (e: IOException) {
            throw CoordinatorException("Network error: ${e.message}", e)
        }
    }

    private data class SpotsWrapper(
        @JsonProperty("spots") val spots: List<SpotStatus> = emptyList()
    )

    companion object {
        val mapper = jacksonObjectMapper().apply {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        /** Quick connectivity check — hits /api/v1/health, no project needed. */
        fun testConnection(baseUrl: String): String {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
            try {
                val url = "${baseUrl.trimEnd('/')}/api/v1/health".toHttpUrl()
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) throw CoordinatorException("HTTP ${response.code}: $body")
                    val status = mapper.readTree(body).path("status").asText("?")
                    val db = mapper.readTree(body).path("database").asText("?")
                    return "OK — status=$status, database=$db"
                }
            } catch (e: IOException) {
                throw CoordinatorException("Network error: ${e.message}", e)
            } finally {
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
            }
        }
    }
}

class CoordinatorException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
