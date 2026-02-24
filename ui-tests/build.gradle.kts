plugins {
    kotlin("jvm") version "2.1.10"
}

repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

val remoteRobotVersion = "0.11.23"

dependencies {
    testImplementation("com.intellij.remoterobot:remote-robot:$remoteRobotVersion")
    testImplementation("com.intellij.remoterobot:remote-fixtures:$remoteRobotVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(17)
}

tasks {
    test {
        useJUnitPlatform()
        // UI tests require a running IDE instance started by runIdeForUiTests
        // Run only when the REMOTE_ROBOT_URL env var is set
        onlyIf { System.getenv("REMOTE_ROBOT_URL") != null || project.hasProperty("runUiTests") }
    }
}
