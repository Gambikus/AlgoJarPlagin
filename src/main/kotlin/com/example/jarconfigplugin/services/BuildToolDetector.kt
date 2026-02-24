package com.example.jarconfigplugin.services

import java.io.File

enum class BuildTool { GRADLE, MAVEN }

object BuildToolDetector {
    fun detect(projectDir: File): BuildTool = when {
        File(projectDir, "build.gradle.kts").exists() -> BuildTool.GRADLE
        File(projectDir, "build.gradle").exists() -> BuildTool.GRADLE
        File(projectDir, "pom.xml").exists() -> BuildTool.MAVEN
        else -> throw JarBuildException(
            "No build file found in ${projectDir.absolutePath}. " +
                "Expected build.gradle.kts, build.gradle, or pom.xml."
        )
    }
}
