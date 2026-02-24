package com.example.jarconfigplugin.services

import com.example.jarconfigplugin.settings.CredentialStore
import com.example.jarconfigplugin.settings.PluginSettingsState
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import java.io.File
import java.net.URI
import java.time.Duration

@Service(Service.Level.PROJECT)
class S3Service(@Suppress("unused") private val project: Project) : Disposable {

    private val log = thisLogger()

    private data class ClientKey(val endpoint: String, val accessKey: String, val secretKey: String) {
        override fun toString() = "ClientKey(endpoint=$endpoint, accessKey=${accessKey.take(4)}***)"
    }

    private var clientKey: ClientKey? = null
    private var cachedClient: S3Client? = null

    fun testConnection(endpoint: String, bucket: String, accessKey: String, secretKey: String): String {
        if (accessKey.isBlank() || secretKey.isBlank()) {
            throw S3UploadException("S3 credentials are empty")
        }
        val client = buildS3Client(endpoint, accessKey, secretKey)
        try {
            client.headBucket { it.bucket(bucket) }
            return "OK — bucket '$bucket' is reachable at $endpoint"
        } finally {
            client.close()
        }
    }

    fun uploadJar(
        jarFile: File,
        objectKey: String,
        indicator: ProgressIndicator? = null
    ): String {
        val settings = PluginSettingsState.getInstance()
        val bucket = settings.s3Bucket

        indicator?.text = "Uploading JAR to S3…"
        indicator?.isIndeterminate = true

        log.info("Uploading ${jarFile.name} (${jarFile.length()} bytes) to s3://$bucket/$objectKey")

        val client = getOrCreateClient(settings)
        val uploader = S3Uploader(client, bucket)
        val s3Uri = uploader.upload(jarFile, objectKey)

        log.info("Upload complete: $s3Uri")
        indicator?.text = "Upload complete"
        return s3Uri
    }

    @Synchronized
    private fun getOrCreateClient(settings: PluginSettingsState): S3Client {
        val accessKey = CredentialStore.s3AccessKey
        val secretKey = CredentialStore.s3SecretKey

        if (accessKey.isBlank() || secretKey.isBlank()) {
            throw S3UploadException(
                "S3 credentials are not configured. Go to Settings | Tools | JAR Config Plugin."
            )
        }

        val key = ClientKey(settings.s3Endpoint, accessKey, secretKey)

        cachedClient?.let { client ->
            if (clientKey == key) return client
            client.close()
        }

        val client = buildS3Client(settings.s3Endpoint, accessKey, secretKey)
        cachedClient = client
        clientKey = key
        return client
    }

    private fun buildS3Client(endpoint: String, accessKey: String, secretKey: String): S3Client {
        val credentials = AwsBasicCredentials.create(accessKey, secretKey)
        return S3Client.builder()
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .endpointOverride(URI.create(endpoint))
            .region(Region.of("ru-central1"))
            .forcePathStyle(true)
            .serviceConfiguration(
                S3Configuration.builder()
                    .chunkedEncodingEnabled(false)
                    .build()
            )
            .httpClient(UrlConnectionHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(5))
                .socketTimeout(Duration.ofSeconds(8))
                .build())
            .build()
    }

    @Synchronized
    override fun dispose() {
        cachedClient?.close()
        cachedClient = null
    }

    companion object {
        /** Standalone connection test — called from settings without a project reference. */
        fun testConnectionStatic(endpoint: String, bucket: String, accessKey: String, secretKey: String): String {
            if (accessKey.isBlank() || secretKey.isBlank()) {
                throw S3UploadException("S3 credentials are empty")
            }
            val credentials = AwsBasicCredentials.create(accessKey, secretKey)
            val client = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .endpointOverride(URI.create(endpoint))
                .region(Region.of("ru-central1"))
                .forcePathStyle(true)
                .serviceConfiguration(
                    S3Configuration.builder()
                        .chunkedEncodingEnabled(false)
                        .build()
                )
                .httpClient(UrlConnectionHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(5))
                .socketTimeout(Duration.ofSeconds(8))
                .build())
                .build()
            try {
                client.headBucket { it.bucket(bucket) }
                return "Connected — bucket '$bucket' is reachable at $endpoint"
            } finally {
                client.close()
            }
        }
    }
}

class S3UploadException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
