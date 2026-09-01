plugins {
    id("com.android.application") version "8.5.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    kotlin("android") version "2.0.21" apply false
}

tasks.register("generateCrossaAar") {
    val sourceDirectory = layout.projectDirectory.dir("crossa")
    val generatedDirectory = layout.buildDirectory.dir("generated-crossa-aar")
    val aarFile = layout.projectDirectory.file("app/libs/crossa-generated-debug.aar")
    inputs.dir(sourceDirectory)
    outputs.upToDateWhen { false }

    doLast {
        val cli = layout.projectDirectory.file("../Crossa/build-host/crossa").asFile
        require(cli.exists()) {
            "Crossa CLI was not found at ${cli.absolutePath}. Build Crossa first."
        }

        val outputDirectory = generatedDirectory.get().asFile
        outputDirectory.deleteRecursively()
        val generateProcess = ProcessBuilder(
            cli.absolutePath,
            "generate-build",
            "android",
            sourceDirectory.asFile.absolutePath,
            "--output",
            outputDirectory.absolutePath
        )
            .directory(layout.projectDirectory.asFile)
            .redirectErrorStream(false)
            .start()
        val generateError = generateProcess.errorStream.bufferedReader().readText()
        if (generateProcess.waitFor() != 0) {
            throw GradleException(generateError.ifBlank { "Crossa Android generation failed." })
        }

        outputDirectory.resolve("local.properties")
            .writeText("sdk.dir=/Users/yazantarifi/Crossa/Crossa/build/android-sdk\n")

        val gradle = layout.projectDirectory.file("gradlew").asFile
        val buildProcess = ProcessBuilder(
            gradle.absolutePath,
            "-p",
            outputDirectory.absolutePath,
            ":library:assembleDebug"
        )
            .directory(layout.projectDirectory.asFile)
            .redirectErrorStream(true)
            .start()
        val buildOutput = buildProcess.inputStream.bufferedReader().readText()
        if (buildProcess.waitFor() != 0) {
            throw GradleException(buildOutput.ifBlank { "Crossa AAR build failed." })
        }

        val generatedAar = outputDirectory.resolve("library/build/outputs/aar/library-debug.aar")
        require(generatedAar.exists()) {
            "Generated Crossa AAR was not found at ${generatedAar.absolutePath}."
        }
        aarFile.asFile.parentFile.mkdirs()
        generatedAar.copyTo(aarFile.asFile, overwrite = true)
    }
}

tasks.register("verifyDemo") {
    dependsOn("generateCrossaAar", ":app:assembleDebug")
}

gradle.projectsEvaluated {
    project(":app").tasks.named("preBuild").configure {
        dependsOn(rootProject.tasks.named("generateCrossaAar"))
    }
}
