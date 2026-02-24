package com.example.jarconfigplugin.services

import java.io.File

object JarLocator {
    /**
     * Searches [dirs] recursively (up to depth 2) for the most recently modified JAR,
     * excluding sources and javadoc artifacts.
     */
    fun findLatestJar(dirs: List<File>): File? =
        dirs.asSequence()
            .filter { it.exists() }
            .flatMap { it.walk().maxDepth(2) }
            .filter { f ->
                f.isFile &&
                    f.extension == "jar" &&
                    !f.name.contains("sources") &&
                    !f.name.contains("javadoc")
            }
            .maxByOrNull { it.lastModified() }
}
