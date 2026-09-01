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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CrossaDemoTheme {
                BenchmarkScreen()
            }
        }
    }
}

@Composable
private fun BenchmarkScreen() {
    val repository = remember { NetworkComparisonRepository() }
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<ScenarioResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(repository) {
        onDispose { repository.close() }
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
                    iterations = repository.requestIterations,
                    delayMs = repository.requestDelayMs,
                    onRun = {
                        scope.launch {
                            loading = true
                            error = null
                            results = emptyList()
                            runCatching { repository.runAll() }
                                .onSuccess { results = it }
                                .onFailure { error = it.message ?: it::class.java.simpleName }
                            loading = false
                        }
                    }
                )
            }

            error?.let {
                item { ErrorPanel(it) }
            }

            if (loading) {
                item { LoadingPanel(repository.requestIterations, repository.requestDelayMs) }
            } else if (results.isEmpty()) {
                item { EmptyPanel() }
            } else {
                item { SummaryPanel(results) }
                items(results, key = { it.name }) { result ->
                    ResultPanel(result)
                }
            }
        }
    }
}

@Composable
private fun Header(
    loading: Boolean,
    iterations: Int,
    delayMs: Long,
    onRun: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Crossa Network Benchmark",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "GET https://jsonplaceholder.typicode.com/posts",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                enabled = !loading,
                onClick = onRun
            ) {
                Text(if (loading) "Running" else "Run")
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricChip("Clients", "3")
            MetricChip("Requests each", iterations.toString())
            MetricChip("Delay", "${delayMs}ms")
            MetricChip("Cache", "disabled")
        }
    }
}

@Composable
private fun SummaryPanel(results: List<ScenarioResult>) {
    val completed = results.filter { it.successCount == it.requestCount }
    val winner = completed.minByOrNull { it.averageMs }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = winner?.let { "Winner: ${it.name}" } ?: "Winner unavailable",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = winner?.let { "Average ${it.averageMs.formatMs()}ms across ${it.requestCount} uncached requests" }
                ?: "One or more clients did not finish all requests.",
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f))
        results.sortedBy { it.averageMs }.forEachIndexed { index, result ->
            Text(
                text = "${index + 1}. ${result.name}: avg ${result.averageMs.formatMs()}ms, success ${result.successCount}/${result.requestCount}",
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ResultPanel(result: ScenarioResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                StatusBadge(result.successCount, result.requestCount)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricChip("Avg", "${result.averageMs.formatMs()}ms")
                MetricChip("Min", "${result.minMs}ms")
                MetricChip("Max", "${result.maxMs}ms")
                MetricChip("Posts", result.postCount.toString())
            }
            Text(
                text = result.responseInfo,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CodeBlock("Timings", result.timingsMs.joinToString(prefix = "[", postfix = "]") { "${it}ms" })
            CodeBlock("Headers", result.requestHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" })
            result.firstPost?.let {
                CodeBlock(
                    title = "Mapped first post",
                    value = "id=${it.id}\nuserId=${it.userId}\ntitle=${it.title}\nbody=${it.body.take(180)}"
                )
            }
            if (result.responsePreview.isNotBlank()) {
                CodeBlock("Mapped response preview", result.responsePreview)
            }
            result.error?.let {
                CodeBlock("Last error", it)
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun StatusBadge(successCount: Int, requestCount: Int) {
    val complete = successCount == requestCount
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (complete) Color(0xFFDFF4E8) else Color(0xFFFFE2DE)
    ) {
        Text(
            text = "$successCount/$requestCount",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (complete) Color(0xFF145235) else Color(0xFF8A1C12)
        )
    }
}

@Composable
private fun CodeBlock(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                .padding(10.dp),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LoadingPanel(iterations: Int, delayMs: Long) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Running benchmark", fontWeight = FontWeight.Bold)
        Text(
            text = "Each client sends $iterations uncached requests with ${delayMs}ms delay between calls.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyPanel() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text("Run the benchmark to compare Retrofit, Ktor, and Crossa AAR.")
    }
}

@Composable
private fun ErrorPanel(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFE2DE), RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text("Benchmark failed", fontWeight = FontWeight.Bold, color = Color(0xFF8A1C12))
        Spacer(modifier = Modifier.height(6.dp))
        Text(message, color = Color(0xFF8A1C12))
    }
}

@Composable
private fun CrossaDemoTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

private fun Double.formatMs(): String = String.format(Locale.US, "%.2f", this)
