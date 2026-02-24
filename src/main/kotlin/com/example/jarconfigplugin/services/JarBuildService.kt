package com.example.jarconfigplugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class JarBuildService(private val project: Project) {

    private val log = thisLogger()

    fun buildJar(moduleName: String, indicator: ProgressIndicator? = null, onOutput: ((String) -> Unit)? = null): File {
        val module = ModuleManager.getInstance(project).findModuleByName(moduleName)
            ?: throw JarBuildException("Module '$moduleName' not found in project '${project.name}'")
        val projectDir = requireNotNull(project.basePath) { "Project base path is null" }
            .let { File(it) }

        val buildTool = BuildToolDetector.detect(projectDir)
        log.info("Detected build tool: $buildTool for module '$moduleName'")

        indicator?.text = "Building JAR with $buildTool…"
        indicator?.isIndeterminate = true

        val exitCode = when (buildTool) {
            BuildTool.GRADLE -> runGradle(projectDir, moduleName, indicator, onOutput)
            BuildTool.MAVEN -> runMaven(projectDir, indicator, onOutput)
        }

        if (exitCode != 0) {
            throw JarBuildException("Build failed with exit code $exitCode")
        }

        val searchDirs = when (buildTool) {
            BuildTool.GRADLE -> listOf(
                File(projectDir, "build/libs"),
                File(projectDir, "${module.name}/build/libs")
            )
            BuildTool.MAVEN -> listOf(
                File(projectDir, "target"),
                File(projectDir, "${module.name}/target")
            )
        }

        return JarLocator.findLatestJar(searchDirs)
            ?: throw JarBuildException("No JAR found after build. Searched: ${searchDirs.map { it.path }}")
    }

    fun availableModules(): List<String> =
        ModuleManager.getInstance(project).modules.map { it.name }

    private fun runGradle(projectDir: File, moduleName: String, indicator: ProgressIndicator?, onOutput: ((String) -> Unit)?): Int {
        val tasks = buildGradleTasks(projectDir, moduleName)
        for (task in tasks) {
            indicator?.text = "Running: ./gradlew $task"
            val code = executeProcess(projectDir, gradlewCmd(projectDir) + listOf(task), indicator, onOutput)
            if (code == 0) return 0
        }
        return 1
    }

    private fun buildGradleTasks(projectDir: File, moduleName: String): List<String> {
        val hasShadow = listOf(
            File(projectDir, "$moduleName/build.gradle.kts"),
            File(projectDir, "$moduleName/build.gradle"),
            File(projectDir, "build.gradle.kts"),
            File(projectDir, "build.gradle")
        ).any { it.exists() && it.readText().contains("shadow", ignoreCase = true) }

        return if (hasShadow) {
            listOf("shadowJar", ":$moduleName:shadowJar", "jar", ":$moduleName:jar")
        } else {
            listOf("jar", ":$moduleName:jar")
        }
    }

    private fun runMaven(projectDir: File, indicator: ProgressIndicator?, onOutput: ((String) -> Unit)?): Int {
        indicator?.text = "Running: mvn package"
        return executeProcess(projectDir, mvnCmd(projectDir) + listOf("package", "-DskipTests"), indicator, onOutput)
    }

    private fun executeProcess(
        workDir: File,
        command: List<String>,
        indicator: ProgressIndicator? = null,
        onOutput: ((String) -> Unit)? = null
    ): Int {
        log.info("Executing: ${command.joinToString(" ")}")
        val process = ProcessBuilder(command)
            .directory(workDir)
            .redirectErrorStream(true)
            .start()

        val outputThread = Thread({
            process.inputStream.bufferedReader().forEachLine { line ->
                log.debug("[build] $line")
                onOutput?.invoke(line)
            }
        }, "jar-build-output-reader")
        outputThread.isDaemon = true
        outputThread.start()

        val startTime = System.currentTimeMillis()
        val timeoutMs = TimeUnit.MINUTES.toMillis(10)

        while (!process.waitFor(2, TimeUnit.SECONDS)) {
            if (indicator?.isCanceled == true) {
                process.destroyForcibly()
                throw JarBuildException("Build cancelled by user")
            }
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                process.destroyForcibly()
                throw JarBuildException("Build timed out after 10 minutes")
            }
        }

        outputThread.join(5000)
        return process.exitValue()
    }

    private fun gradlewCmd(projectDir: File): List<String> {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val wrapper = if (isWindows) "gradlew.bat" else "gradlew"
        val wrapperFile = File(projectDir, wrapper)
        if (!wrapperFile.exists()) {
            throw JarBuildException(
                "Gradle wrapper not found: ${wrapperFile.absolutePath}. " +
                    "Run 'gradle wrapper' to generate it."
            )
        }
        return if (isWindows)
            listOf("cmd", "/c", wrapperFile.absolutePath)
        else
            listOf(wrapperFile.absolutePath)
    }

    private fun mvnCmd(projectDir: File): List<String> {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val wrapper = File(projectDir, if (isWindows) "mvnw.cmd" else "mvnw")
        val mvn = if (wrapper.exists()) wrapper.absolutePath else "mvn"
        return if (isWindows) listOf("cmd", "/c", mvn) else listOf(mvn)
    }
}

class JarBuildException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
