package com.example.jarconfigplugin

import com.example.jarconfigplugin.services.S3UploadException
import com.example.jarconfigplugin.services.S3Uploader
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception
import java.io.File
import java.nio.file.Path

/**
 * Unit tests for [S3Uploader] — the same production class
 * used by [com.example.jarconfigplugin.services.S3Service].
 */
class S3ServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var mockS3Client: S3Client
    private lateinit var uploader: S3Uploader

    @BeforeEach
    fun setUp() {
        mockS3Client = mockk()
        uploader = S3Uploader(mockS3Client, bucket = "orhestra-algorithms")
    }

    @Test
    fun `upload returns correct s3 URI on success`() {
        val jar = createTempJar("algo-pack.jar")
        every { mockS3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) } returns
            PutObjectResponse.builder().build()

        val uri = uploader.upload(jar, "experiments/algo-pack.jar")
        assertEquals("s3://orhestra-algorithms/experiments/algo-pack.jar", uri)
    }

    @Test
    fun `upload sends PutObject with correct bucket and key`() {
        val jar = createTempJar("algo.jar")
        val capturedRequest = slot<PutObjectRequest>()
        every { mockS3Client.putObject(capture(capturedRequest), any<RequestBody>()) } returns
            PutObjectResponse.builder().build()

        uploader.upload(jar, "runs/algo.jar")

        assertEquals("orhestra-algorithms", capturedRequest.captured.bucket())
        assertEquals("runs/algo.jar", capturedRequest.captured.key())
        assertEquals("application/java-archive", capturedRequest.captured.contentType())
    }

    @Test
    fun `upload throws S3UploadException when SDK throws`() {
        val jar = createTempJar("algo.jar")
        every { mockS3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) } throws
            S3Exception.builder().message("Access Denied").statusCode(403).build()

        assertThrows<S3UploadException> { uploader.upload(jar, "key.jar") }
    }

    @Test
    fun `upload throws S3UploadException for missing file`() {
        val missingFile = File(tempDir.toFile(), "nonexistent.jar")
        assertThrows<S3UploadException> { uploader.upload(missingFile, "key.jar") }
    }

    private fun createTempJar(name: String): File {
        return tempDir.resolve(name).toFile().apply {
            writeBytes(ByteArray(1024) { 0xCA.toByte() })
        }
    }
}
