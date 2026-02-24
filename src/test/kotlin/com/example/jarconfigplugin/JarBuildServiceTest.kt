package com.example.jarconfigplugin

import com.example.jarconfigplugin.services.BuildTool
import com.example.jarconfigplugin.services.BuildToolDetector
import com.example.jarconfigplugin.services.JarBuildException
import com.example.jarconfigplugin.services.JarLocator
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Unit tests for [BuildToolDetector] and [JarLocator] — the same
 * production classes used by [com.example.jarconfigplugin.services.JarBuildService].
 */
class JarBuildServiceTest {

    @TempDir
    lateinit var projectDir: Path

    // ── Build tool detection ──────────────────────────────────────────────────

    @Test
    fun `detects Gradle project via build_gradle_kts`() {
        projectDir.resolve("build.gradle.kts").toFile().writeText("// gradle kts")
        assertEquals(BuildTool.GRADLE, BuildToolDetector.detect(projectDir.toFile()))
    }

    @Test
    fun `detects Gradle project via build_gradle`() {
        projectDir.resolve("build.gradle").toFile().writeText("// gradle groovy")
        assertEquals(BuildTool.GRADLE, BuildToolDetector.detect(projectDir.toFile()))
    }

    @Test
    fun `detects Maven project via pom_xml`() {
        projectDir.resolve("pom.xml").toFile().writeText("<project/>")
        assertEquals(BuildTool.MAVEN, BuildToolDetector.detect(projectDir.toFile()))
    }

    @Test
    fun `throws when no build file found`() {
        assertThrows<JarBuildException> { BuildToolDetector.detect(projectDir.toFile()) }
    }

    @Test
    fun `Gradle takes precedence over Maven when both present`() {
        projectDir.resolve("build.gradle.kts").toFile().writeText("// gradle")
        projectDir.resolve("pom.xml").toFile().writeText("<project/>")
        assertEquals(BuildTool.GRADLE, BuildToolDetector.detect(projectDir.toFile()))
    }

    // ── JAR location logic ────────────────────────────────────────────────────

    @Test
    fun `locates newest JAR in build_libs directory`() {
        val libsDir = projectDir.resolve("build/libs").toFile().apply { mkdirs() }
        libsDir.resolve("algo-1.0.jar").apply { writeBytes(ByteArray(100)); setLastModified(1000) }
        val newer = libsDir.resolve("algo-1.1.jar").apply { writeBytes(ByteArray(200)); setLastModified(2000) }

        val found = JarLocator.findLatestJar(listOf(libsDir))
        assertEquals(newer.name, found?.name)
    }

    @Test
    fun `ignores sources and javadoc JARs`() {
        val libsDir = projectDir.resolve("build/libs").toFile().apply { mkdirs() }
        libsDir.resolve("algo-sources.jar").writeBytes(ByteArray(50))
        libsDir.resolve("algo-javadoc.jar").writeBytes(ByteArray(50))
        val main = libsDir.resolve("algo.jar").apply { writeBytes(ByteArray(300)) }

        val found = JarLocator.findLatestJar(listOf(libsDir))
        assertEquals(main.name, found?.name)
    }

    @Test
    fun `returns null when no JARs found`() {
        val emptyDir = projectDir.resolve("build/libs").toFile().apply { mkdirs() }
        assertNull(JarLocator.findLatestJar(listOf(emptyDir)))
    }

    @Test
    fun `throws JarBuildException message format`() {
        val ex = JarBuildException("Build failed with exit code 1")
        assertTrue(ex.message!!.contains("exit code 1"))
    }

    @Test
    fun `JarBuildException preserves cause`() {
        val cause = RuntimeException("root cause")
        val ex = JarBuildException("wrapper", cause)
        assertEquals(cause, ex.cause)
    }
}
