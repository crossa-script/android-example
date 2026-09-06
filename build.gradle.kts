plugins {
    id("com.android.application") version "8.5.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    kotlin("android") version "2.0.21" apply false
}

tasks.register<Exec>("generateCrossaAar") {
    val sourceDirectory = layout.projectDirectory.dir("crossa")
    val aarFile = layout.projectDirectory.file("app/libs/crossa-generated-release.aar")
    val script = layout.projectDirectory.file("scripts/generate-crossa-aar.sh")
    inputs.dir(sourceDirectory)
    inputs.file(script)
    outputs.file(aarFile)
    outputs.upToDateWhen { false }
    commandLine(script.asFile.absolutePath)
    workingDir(layout.projectDirectory.asFile)
}

tasks.register("verifyDemo") {
    dependsOn("generateCrossaAar")
    finalizedBy(":app:assembleDebug")
}
