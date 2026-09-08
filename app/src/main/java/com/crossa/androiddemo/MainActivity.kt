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
import androidx.compose.material3.darkColorScheme
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
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFA997FF),
                    onPrimary = Color(0xFF171328),
                    primaryContainer = Color(0xFF272044),
                    onPrimaryContainer = Color(0xFFEAE5FF),
                    secondaryContainer = Color(0xFF1A293E),
                    onSecondaryContainer = Color(0xFFC7DFFF),
                    background = Color(0xFF08090D),
                    surface = Color(0xFF111722),
                    surfaceVariant = Color(0xFF1A2230),
                    onSurface = Color(0xFFF5F7FC),
                    onSurfaceVariant = Color(0xFFA4ADBD)
                )
            ) {
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
                item { LoadingPanel() }
            } else {
                result?.let { run ->
                    val ordered = run.summaries.sortedWith(compareBy({ if (it.successCount == 0) 1 else 0 }, { it.medianNanos }))
                    items(ordered, key = { it.implementation.name }) { summary ->
                        SummaryCard(summary, ordered.indexOf(summary), ordered)
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
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Benchmark scores", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Crossa · Retrofit · Ktor", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(enabled = !loading, onClick = onRun) {
                Text(if (loading) "Running" else "Run")
            }
        }
        Text(
            "Lower p50 is faster. Scores use the same measured request for every library.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SummaryCard(summary: BenchmarkSummary, rank: Int, ordered: List<BenchmarkSummary>) {
    val fastest = ordered.firstOrNull { it.successCount > 0 }
    val delta = if (rank > 0 && fastest != null && fastest.medianNanos > 0L) {
        ((summary.medianNanos.toDouble() / fastest.medianNanos) - 1.0) * 100.0
    } else null
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = if (rank == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${rank + 1}", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(summary.implementation.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${summary.successCount}/${summary.sampleCount} successful", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (rank == 0) Text("FASTEST p50", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricChip("p50", summary.medianNanos.toMs())
                MetricChip("p95", summary.p95Nanos.toMs())
                MetricChip("mean", summary.meanNanos.toMs())
            }
            delta?.let { Text("+${String.format(Locale.US, "%.1f", it)}% p50 vs fastest", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
private fun LoadingPanel() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text("Measuring library scores…", fontWeight = FontWeight.Bold)
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
