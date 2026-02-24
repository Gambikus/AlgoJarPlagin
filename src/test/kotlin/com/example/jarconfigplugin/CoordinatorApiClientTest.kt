package com.example.jarconfigplugin

import com.example.jarconfigplugin.model.JobRequest
import com.example.jarconfigplugin.model.RangeSpec
import com.example.jarconfigplugin.model.TaskStatus
import com.example.jarconfigplugin.services.CoordinatorApiClient
import com.example.jarconfigplugin.services.CoordinatorException
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for [CoordinatorApiClient] using WireMock.
 * No IntelliJ Platform runtime required.
 */
class CoordinatorApiClientTest {

    companion object {
        private lateinit var wireMock: WireMockServer
        private lateinit var client: CoordinatorApiClient

        @BeforeAll
        @JvmStatic
        fun startServer() {
            wireMock = WireMockServer(wireMockConfig().dynamicPort())
            wireMock.start()
            client = CoordinatorApiClient("http://localhost:${wireMock.port()}")
        }

        @AfterAll
        @JvmStatic
        fun stopServer() {
            wireMock.stop()
        }
    }

    @BeforeEach
    fun resetStubs() = wireMock.resetAll()

    // ── submitJob ─────────────────────────────────────────────────────────────

    @Test
    fun `submitJob returns jobId on HTTP 200`() {
        wireMock.stubFor(
            post(urlEqualTo("/api/v1/jobs"))
                .willReturn(okJson("""{"jobId":"job-42"}"""))
        )

        val response = client.submitJob(sampleRequest())
        assertEquals("job-42", response.jobId)
    }

    @Test
    fun `submitJob posts correct JSON body`() {
        wireMock.stubFor(
            post(urlEqualTo("/api/v1/jobs"))
                .withRequestBody(matchingJsonPath("$.mainClass", equalTo("algorithms.Main")))
                .withRequestBody(matchingJsonPath("$.algorithms[0]", equalTo("sphere")))
                .withRequestBody(matchingJsonPath("$.iterations.min", equalTo("100")))
                .willReturn(okJson("""{"jobId":"job-99"}"""))
        )

        val response = client.submitJob(sampleRequest())
        assertEquals("job-99", response.jobId)
    }

    @Test
    fun `submitJob throws CoordinatorException on HTTP 500`() {
        wireMock.stubFor(
            post(urlEqualTo("/api/v1/jobs"))
                .willReturn(serverError().withBody("Internal Server Error"))
        )

        assertThrows<CoordinatorException> { client.submitJob(sampleRequest()) }
    }

    @Test
    fun `submitJob throws CoordinatorException on HTTP 400`() {
        wireMock.stubFor(
            post(urlEqualTo("/api/v1/jobs"))
                .willReturn(badRequest().withBody("""{"error":"invalid mainClass"}"""))
        )

        val ex = assertThrows<CoordinatorException> { client.submitJob(sampleRequest()) }
        assertTrue(ex.message!!.contains("400"))
    }

    // ── getJobStatus ──────────────────────────────────────────────────────────

    @Test
    fun `getJobStatus parses running job correctly`() {
        wireMock.stubFor(
            get(urlEqualTo("/api/v1/jobs/job-1"))
                .willReturn(okJson("""
                    {
                      "jobId": "job-1",
                      "status": "RUNNING",
                      "totalTasks": 36,
                      "completedTasks": 12,
                      "failedTasks": 0
                    }
                """.trimIndent()))
        )

        val status = client.getJobStatus("job-1")
        assertEquals("job-1", status.jobId)
        assertEquals("RUNNING", status.status)
        assertEquals(36, status.totalTasks)
        assertEquals(12, status.completedTasks)
        assertEquals(0, status.failedTasks)
    }

    @Test
    fun `getJobStatus parses completed job correctly`() {
        wireMock.stubFor(
            get(urlEqualTo("/api/v1/jobs/job-done"))
                .willReturn(okJson("""
                    {
                      "jobId": "job-done",
                      "status": "COMPLETED",
                      "totalTasks": 36,
                      "completedTasks": 36,
                      "failedTasks": 0
                    }
                """.trimIndent()))
        )

        val status = client.getJobStatus("job-done")
        assertEquals("COMPLETED", status.status)
        assertEquals(36, status.completedTasks)
    }

    @Test
    fun `getJobStatus throws CoordinatorException on 404`() {
        wireMock.stubFor(
            get(urlEqualTo("/api/v1/jobs/missing"))
                .willReturn(notFound().withBody("not found"))
        )

        assertThrows<CoordinatorException> { client.getJobStatus("missing") }
    }

    // ── getJobResults ─────────────────────────────────────────────────────────

    @Test
    fun `getJobResults parses full response correctly`() {
        wireMock.stubFor(
            get(urlEqualTo("/api/v1/jobs/job-1/results"))
                .willReturn(okJson("""
                    {
                      "jobId": "job-1",
                      "results": [
                        {
                          "taskId": "task-1",
                          "algorithm": "sphere",
                          "iterations": 100,
                          "agents": 25,
                          "dimension": 2,
                          "status": "DONE",
                          "runtimeMs": 5321,
                          "iter": 100,
                          "fopt": -123.456,
                          "assignedTo": "spot-1"
                        }
                      ]
                    }
                """.trimIndent()))
        )

        val result = client.getJobResults("job-1")
        assertEquals("job-1", result.jobId)
        assertEquals(1, result.results.size)

        val task = result.results[0]
        assertEquals("task-1", task.taskId)
        assertEquals("sphere", task.algorithm)
        assertEquals(TaskStatus.DONE, task.status)
        assertEquals(-123.456, task.fopt)
        assertEquals("spot-1", task.assignedTo)
    }

    @Test
    fun `getJobResults tolerates null optional fields`() {
        wireMock.stubFor(
            get(urlEqualTo("/api/v1/jobs/job-partial/results"))
                .willReturn(okJson("""
                    {
                      "jobId": "job-partial",
                      "results": [
                        {
                          "taskId": "task-2",
                          "status": "DONE",
                          "runtimeMs": 1000,
                          "iter": 50,
                          "fopt": 0.5
                        }
                      ]
                    }
                """.trimIndent()))
        )

        val result = client.getJobResults("job-partial")
        val task = result.results[0]
        assertNull(task.algorithm)
        assertNull(task.agents)
        assertNull(task.assignedTo)
    }

    @Test
    fun `getJobResults handles empty results array`() {
        wireMock.stubFor(
            get(urlEqualTo("/api/v1/jobs/job-empty/results"))
                .willReturn(okJson("""{"jobId":"job-empty","results":[]}"""))
        )

        val result = client.getJobResults("job-empty")
        assertTrue(result.results.isEmpty())
    }

    @Test
    fun `getJobResults throws CoordinatorException on 404`() {
        wireMock.stubFor(
            get(urlEqualTo("/api/v1/jobs/missing/results"))
                .willReturn(notFound().withBody("not found"))
        )

        assertThrows<CoordinatorException> { client.getJobResults("missing") }
    }

    // ── getSpots ──────────────────────────────────────────────────────────────

    @Test
    fun `getSpots returns list of SPOT nodes`() {
        wireMock.stubFor(
            get(urlEqualTo("/api/v1/spots"))
                .willReturn(okJson("""
                    {
                      "spots": [
                        {
                          "spotId": "spot-1",
                          "status": "READY",
                          "cpuLoad": 37.5,
                          "runningTasks": 1,
                          "totalCores": 4,
                          "lastHeartbeat": "2025-11-13T18:42:11Z"
                        },
                        {
                          "spotId": "spot-2",
                          "status": "BUSY",
                          "cpuLoad": 90.0,
                          "runningTasks": 2,
                          "totalCores": 8,
                          "lastHeartbeat": "2025-11-13T18:42:10Z"
                        }
                      ]
                    }
                """.trimIndent()))
        )

        val spots = client.getSpots()
        assertEquals(2, spots.size)
        assertEquals("spot-1", spots[0].spotId)
        assertEquals("READY", spots[0].status)
        assertEquals(37.5, spots[0].cpuLoad)
        assertEquals(1, spots[0].runningTasks)
        assertEquals("spot-2", spots[1].spotId)
        assertEquals(90.0, spots[1].cpuLoad)
    }

    @Test
    fun `getSpots tolerates null lastHeartbeat`() {
        wireMock.stubFor(
            get(urlEqualTo("/api/v1/spots"))
                .willReturn(okJson("""
                    {
                      "spots": [
                        {
                          "spotId": "spot-new",
                          "status": "READY",
                          "cpuLoad": 0.0,
                          "runningTasks": 0,
                          "totalCores": 4
                        }
                      ]
                    }
                """.trimIndent()))
        )

        val spots = client.getSpots()
        assertEquals(1, spots.size)
        assertNull(spots[0].lastHeartbeat)
    }

    @Test
    fun `getSpots returns empty list when spots array is empty`() {
        wireMock.stubFor(
            get(urlEqualTo("/api/v1/spots"))
                .willReturn(okJson("""{"spots":[]}"""))
        )

        assertTrue(client.getSpots().isEmpty())
    }

    @Test
    fun `getSpots throws CoordinatorException on network error`() {
        val deadClient = CoordinatorApiClient("http://localhost:1")
        assertThrows<CoordinatorException> { deadClient.getSpots() }
    }

    // ── testConnection ────────────────────────────────────────────────────────

    @Test
    fun `testConnection returns status string on healthy endpoint`() {
        wireMock.stubFor(
            get(urlEqualTo("/api/v1/health"))
                .willReturn(okJson("""{"status":"UP","database":"UP"}"""))
        )

        val msg = CoordinatorApiClient.testConnection("http://localhost:${wireMock.port()}")
        assertTrue(msg.contains("UP"), "Expected 'UP' in: $msg")
    }

    @Test
    fun `testConnection throws CoordinatorException on HTTP 500`() {
        wireMock.stubFor(
            get(urlEqualTo("/api/v1/health"))
                .willReturn(serverError().withBody("error"))
        )

        assertThrows<CoordinatorException> {
            CoordinatorApiClient.testConnection("http://localhost:${wireMock.port()}")
        }
    }

    @Test
    fun `testConnection throws CoordinatorException on connection refused`() {
        assertThrows<CoordinatorException> {
            CoordinatorApiClient.testConnection("http://localhost:1")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun sampleRequest() = JobRequest(
        jarPath = "s3://orhestra-algorithms/experiments/algo.jar",
        mainClass = "algorithms.Main",
        algorithms = listOf("sphere", "rosenbrock"),
        iterations = RangeSpec(100, 300, 100),
        agents = RangeSpec(25, 75, 25),
        dimension = RangeSpec(1, 2, 1)
    )
}
