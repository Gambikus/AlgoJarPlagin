package com.example.jarconfigplugin.services

import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.File

/**
 * Pure S3 upload logic, decoupled from IntelliJ Platform.
 * Accepts an [S3Client] via constructor for direct unit-testability with MockK.
 */
class S3Uploader(private val s3Client: S3Client, private val bucket: String) {

    fun upload(jarFile: File, objectKey: String): String {
        if (!jarFile.exists()) {
            throw S3UploadException("File not found: ${jarFile.absolutePath}")
        }
        try {
            val bytes = jarFile.readBytes()
            val request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType("application/java-archive")
                .build()
            s3Client.putObject(request, RequestBody.fromBytes(bytes))
            return "s3://$bucket/$objectKey"
        } catch (e: Exception) {
            throw S3UploadException("Upload failed: ${e.message}", e)
        }
    }
}
