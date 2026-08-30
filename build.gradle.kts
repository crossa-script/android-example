import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.util.Locale

plugins {
    id("com.android.application") version "8.5.1" apply false
    kotlin("android") version "2.0.21" apply false
}

tasks.register("syncCrossaCliScenario") {
    val sourceFile = layout.projectDirectory.file("crossa/posts.cra")
    val outputFile = layout.projectDirectory.file("app/src/main/assets/crossa_metrics.json")
    inputs.file(sourceFile)
    outputs.upToDateWhen { false }

    doLast {
        val cli = layout.projectDirectory.file("../Crossa/build/crossa").asFile
        require(cli.exists()) {
            "Crossa CLI was not found at ${cli.absolutePath}. Build Crossa first."
        }

        val timings = mutableListOf<Long>()
        var lastOutput = ""
        var postCount = 0
        var firstPost = mapOf<String, Any?>()

        repeat(5) { index ->
            val start = System.nanoTime()
            val process = ProcessBuilder(
                cli.absolutePath,
                "run",
                sourceFile.asFile.absolutePath
            )
                .directory(layout.projectDirectory.asFile)
                .redirectErrorStream(false)
                .start()
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitValue = process.waitFor()
            val elapsed = (System.nanoTime() - start) / 1_000_000
            timings += elapsed

            if (exitValue != 0) {
                throw GradleException(stderr.ifBlank { "Crossa CLI request failed." })
            }

            lastOutput = stdout.trim()
            val parsed = JsonSlurper().parseText(lastOutput) as List<*>
            postCount = parsed.size
            firstPost = (parsed.firstOrNull() as? Map<*, *>)
                ?.mapKeys { it.key.toString() }
                ?: emptyMap()

            if (index < 4) {
                Thread.sleep(750)
            }
        }

        val average = timings.average()
        val payload = mapOf(
            "scenario" to "Crossa CLI",
            "requestUrl" to "https://jsonplaceholder.typicode.com/posts",
            "requestCount" to 5,
            "successCount" to 5,
            "totalMs" to timings.sum(),
            "averageMs" to String.format(Locale.US, "%.2f", average).toDouble(),
            "minMs" to timings.minOrNull(),
            "maxMs" to timings.maxOrNull(),
            "postCount" to postCount,
            "firstPost" to firstPost,
            "timingsMs" to timings,
            "requestHeaders" to mapOf(
                "Accept" to "application/json",
                "X-Crossa-Demo" to "android-example",
                "X-Crossa-Scenario" to "cli"
            ),
            "responsePreview" to lastOutput.take(700)
        )

        outputFile.asFile.parentFile.mkdirs()
        outputFile.asFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(payload)))
    }
}

tasks.register("verifyDemo") {
    dependsOn("syncCrossaCliScenario", ":app:assembleDebug")
}
