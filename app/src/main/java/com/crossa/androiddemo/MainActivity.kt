@file:OptIn(ExperimentalLayoutApi::class)

package com.crossa.androiddemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.crossa.androiddemo.benchmark.BenchmarkConfiguration
import com.crossa.androiddemo.benchmark.BenchmarkRunResult
import com.crossa.androiddemo.benchmark.BenchmarkRunner
import com.crossa.androiddemo.benchmark.BenchmarkSummary
import com.crossa.androiddemo.benchmark.BenchmarkMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BenchmarkScreen(
                    autoRun = intent.getBooleanExtra("crossa.benchmark.auto", false),
                    cold = intent.getStringExtra("crossa.benchmark.mode") == "cold"
                )
            }
        }
    }
}

@Composable
private fun BenchmarkScreen(autoRun: Boolean, cold: Boolean) {
    val context = LocalContext.current
    val configuration = remember(cold) {
        BenchmarkConfiguration(mode = if (cold) BenchmarkMode.Cold else BenchmarkMode.Warm)
    }
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<BenchmarkRunResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val runBenchmark: () -> Unit = {
        scope.launch {
            loading = true
            error = null
            result = null
            println("CROSSA_BENCHMARK_STARTED")
            runCatching {
                withContext(Dispatchers.Default) {
                    BenchmarkRunner(configuration).run()
                }
            }.onSuccess {
                result = it
                writeBenchmarkResult(context, it)
                println("CROSSA_BENCHMARK_COMPLETED")
            }.onFailure {
                error = it.message ?: it::class.java.simpleName
                println("CROSSA_BENCHMARK_FAILED ${error}")
            }
            loading = false
        }
    }

    LaunchedEffect(autoRun) {
        if (autoRun) runBenchmark()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Header(
                    loading = loading,
                    configuration = configuration,
                    onRun = runBenchmark
                )
            }
            error?.let { item { ErrorPanel(it) } }
            if (loading) {
                item { LoadingPanel(configuration) }
            } else {
                result?.let { run ->
                    item { MetadataPanel(run) }
                    items(run.summaries, key = { it.implementation.name }) { summary ->
                        SummaryCard(summary, run)
                    }
                }
            }
        }
    }
}

private fun writeBenchmarkResult(context: android.content.Context, run: BenchmarkRunResult) {
    val metadata = run.metadata
    val json = JSONObject()
        .put("metadata", JSONObject()
            .put("deviceModel", metadata.deviceModel)
            .put("androidVersion", metadata.androidVersion)
            .put("abi", metadata.abi)
            .put("appVersion", metadata.appVersion)
            .put("buildType", metadata.buildType)
            .put("crossaArtifact", metadata.crossaArtifact)
            .put("crossaArtifactVersion", metadata.crossaArtifactVersion)
            .put("crossaArtifactSha256", metadata.crossaArtifactSha256)
            .put("crossaSourceCommit", metadata.crossaSourceCommit)
            .put("warmupIterations", metadata.warmupIterations)
            .put("measuredIterations", metadata.measuredIterations)
            .put("endpoint", metadata.endpoint)
            .put("endpointKind", metadata.endpointKind.name)
            .put("mode", metadata.mode.name)
            .put("timestampMillis", metadata.timestampMillis))
        .put("summaries", JSONArray().apply {
            run.summaries.forEach { summary ->
                put(JSONObject()
                    .put("implementation", summary.implementation.name)
                    .put("sampleCount", summary.sampleCount)
                    .put("successCount", summary.successCount)
                    .put("failureCount", summary.failureCount)
                    .put("medianNanos", summary.medianNanos)
                    .put("p95Nanos", summary.p95Nanos)
                    .put("meanNanos", summary.meanNanos))
            }
        })
        .put("samples", JSONArray().apply {
            run.samples.forEach { sample ->
                put(JSONObject()
                    .put("implementation", sample.implementation.name)
                    .put("iteration", sample.iteration)
                    .put("durationNanos", sample.durationNanos)
                    .put("success", sample.success)
                    .put("itemCount", sample.itemCount)
                    .put("materializationNanos", sample.materializationNanos))
            }
        })
        val resultDirectory = context.getExternalFilesDir(null) ?: context.filesDir
        java.io.File(resultDirectory, "benchmark-result.json").writeText(json.toString(2))
}

@Composable
private fun Header(
    loading: Boolean,
    configuration: BenchmarkConfiguration,
    onRun: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Crossa Network Benchmark", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(configuration.endpoint, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(enabled = !loading, onClick = onRun) {
                Text(if (loading) "Running" else "Run")
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricChip("Mode", configuration.mode.name)
            MetricChip("Warmups", configuration.warmupIterations.toString())
            MetricChip("Measured", configuration.measuredIterations.toString())
            MetricChip("Artifact", BuildConfig.CROSSA_ARTIFACT)
            MetricChip("Build", BuildConfig.BENCHMARK_BUILD)
        }
        Text(
            "Observations only. Remote JSONPlaceholder latency is not SDK overhead. No winner is declared from one average.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun MetadataPanel(run: BenchmarkRunResult) {
    val metadata = run.metadata
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Reproducibility", fontWeight = FontWeight.Bold)
            Text("Device ${metadata.deviceModel}", fontSize = 12.sp)
            Text("Android ${metadata.androidVersion}  ABI ${metadata.abi}", fontSize = 12.sp)
            Text("App ${metadata.appVersion}  ${metadata.buildType}", fontSize = 12.sp)
            Text("Crossa ${metadata.crossaArtifact} ${metadata.crossaArtifactVersion}", fontSize = 12.sp)
            Text("SHA-256 ${metadata.crossaArtifactSha256}", fontSize = 12.sp)
            Text("Source ${metadata.crossaSourceCommit}", fontSize = 12.sp)
            Text("${metadata.mode}  ${metadata.endpointKind}  ${metadata.endpoint}", fontSize = 12.sp)
            Text("Warmups ${metadata.warmupIterations}  Measured ${metadata.measuredIterations}", fontSize = 12.sp)
        }
    }
}

@Composable
private fun SummaryCard(summary: BenchmarkSummary, run: BenchmarkRunResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(summary.implementation.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricChip("p50", summary.medianNanos.toMs())
                MetricChip("p95", summary.p95Nanos.toMs())
                MetricChip("mean", summary.meanNanos.toMs())
                MetricChip("min", summary.minNanos.toMs())
                MetricChip("max", summary.maxNanos.toMs())
                MetricChip("success", "${summary.successCount}/${summary.sampleCount}")
            }
            if (summary.implementation.name == "Crossa") {
                run.crossaSplit?.let { split ->
                    CodeBlock(
                        "Crossa split",
                        "native-ready p50 ${split.nativeReady.medianNanos.toMs()}\n" +
                            "materialization p50 ${split.materialization?.medianNanos?.toMs() ?: "n/a"}\n" +
                            "application-ready p50 ${split.applicationReady.medianNanos.toMs()}"
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(6.dp))
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun CodeBlock(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                .padding(10.dp),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun LoadingPanel(configuration: BenchmarkConfiguration) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text("Running interleaved ${configuration.mode.name.lowercase()} rounds", fontWeight = FontWeight.Bold)
        Text(
            "Warmups ${configuration.warmupIterations} are excluded. Measured rounds ${configuration.measuredIterations}.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorPanel(message: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFE2DE), RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text("Benchmark failed", fontWeight = FontWeight.Bold, color = Color(0xFF8A1C12))
        Spacer(Modifier.height(6.dp))
        Text(message, color = Color(0xFF8A1C12))
    }
}

private fun Long.toMs(): String = String.format(Locale.US, "%.2f ms", this / 1_000_000.0)

private fun Double.toMs(): String = String.format(Locale.US, "%.2f ms", this / 1_000_000.0)
