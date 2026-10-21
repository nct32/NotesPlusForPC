import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    id("org.jetbrains.compose") version "1.8.2"
}

group = "notesplus"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.35.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("net.java.dev.jna:jna:5.17.0")
    implementation("net.java.dev.jna:jna-platform:5.17.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // Multi-Instance stress test length (quoted for PowerShell): .\gradlew.bat test "-Pstress.seconds=180"
    findProperty("stress.seconds")?.let { systemProperty("stress.seconds", it) }
    testLogging { showStandardStreams = true }
}

compose.desktop {
    application {
        mainClass = "notesplus.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "Notes+"
            packageVersion = "1.0.0"
            description = "Notes and reminders"
            vendor = "Notes+"
            // From `gradlew suggestRuntimeModules`: java.sql for SQLite, jdk.unsupported for JNA.
            modules("java.instrument", "java.sql", "jdk.unsupported")

            windows {
                iconFile.set(project.file("packaging/icon.ico"))
                menuGroup = "Notes+"
                menu = true          // Start Menu shortcut, needed for reliable toasts
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Fixed so future installers upgrade in place. Never change it.
                upgradeUuid = "6f1c2b8e-4d3a-4e5f-9a7b-2c8d1e0f3a4b"
            }
        }
    }
}
