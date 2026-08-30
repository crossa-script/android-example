package com.crossa.androiddemo

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository by lazy { NetworkComparisonRepository() }
    private lateinit var content: LinearLayout
    private lateinit var runButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
        render(emptyList(), false)
    }

    override fun onDestroy() {
        repository.close()
        scope.cancel()
        super.onDestroy()
    }

    private fun createContentView(): View {
        val scrollView = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 36, 32, 36)
            setBackgroundColor(Color.rgb(247, 248, 250))
        }
        scrollView.addView(content)
        return scrollView
    }

    private fun runComparison() {
        scope.launch {
            render(emptyList(), true)
            val results = repository.runAll()
            render(results, false)
        }
    }

    private fun render(results: List<ScenarioResult>, loading: Boolean) {
        content.removeAllViews()
        content.addView(title("Crossa Network Demo", 28))
        content.addView(body("Same GET request to jsonplaceholder posts. Retrofit sends 5 requests, Ktor sends 5 requests, and Crossa CLI sends 5 requests with a 750ms delay."))
        runButton = Button(this).apply {
            text = if (loading) "Running..." else "Run comparison"
            isEnabled = !loading
            setOnClickListener { runComparison() }
        }
        content.addView(runButton)

        if (loading) {
            content.addView(section("Waiting for 5 requests per client..."))
            return
        }

        if (results.isEmpty()) {
            content.addView(section("Run the comparison to measure Retrofit and Ktor. Crossa CLI numbers are loaded from the generated 5-request result."))
            return
        }

        val winner = results
            .filter { it.requestCount == 5 && it.successCount == 5 }
            .minByOrNull { it.averageMs }

        content.addView(title("Comparison", 20))
        content.addView(
            winnerCard(
                winner?.let { "Winner: ${it.name} with ${it.averageMs}ms average" }
                    ?: "Winner: unavailable because one or more scenarios did not finish 5/5 requests"
            )
        )
        results.sortedBy { it.averageMs }.forEachIndexed { index, result ->
            content.addView(body("${index + 1}. ${result.name}: avg ${result.averageMs}ms, total ${result.totalMs}ms, success ${result.successCount}/${result.requestCount}"))
        }
        results.forEach { content.addView(resultCard(it)) }
    }

    private fun resultCard(result: ScenarioResult): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 12f
                setStroke(1, Color.rgb(224, 228, 234))
            }
            addView(title(result.name, 18))
            addView(body("Requests: ${result.successCount}/${result.requestCount} | posts: ${result.postCount} | min/avg/max: ${result.minMs}/${result.averageMs}/${result.maxMs}ms"))
            addView(body("URL: ${result.requestUrl}"))
            addView(body("Request headers: ${result.requestHeaders.entries.joinToString { "${it.key}=${it.value}" }}"))
            addView(body("Response: ${result.responseInfo}"))
            addView(body("Timings: ${result.timingsMs.joinToString(prefix = "[", postfix = "]")} ms"))
            result.firstPost?.let {
                addView(code("Mapped first post\nid=${it.id}\nuserId=${it.userId}\ntitle=${it.title}\nbody=${it.body.take(160)}"))
            }
            if (result.responsePreview.isNotBlank()) {
                addView(code("Mapped response preview\n${result.responsePreview}"))
            }
            result.error?.let {
                addView(code("Last error\n$it"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 18
            }
        }
    }

    private fun title(text: String, size: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(Color.rgb(24, 29, 39))
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, 10, 0, 10)
    }

    private fun body(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.rgb(65, 75, 91))
        setPadding(0, 6, 0, 6)
    }

    private fun section(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        setTextColor(Color.rgb(42, 51, 66))
        setPadding(0, 24, 0, 10)
    }

    private fun code(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        typeface = Typeface.MONOSPACE
        setTextColor(Color.rgb(20, 24, 31))
        setPadding(18, 18, 18, 18)
        background = GradientDrawable().apply {
            setColor(Color.rgb(241, 244, 248))
            cornerRadius = 8f
        }
    }

    private fun winnerCard(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.rgb(19, 92, 72))
        setPadding(22, 22, 22, 22)
        background = GradientDrawable().apply {
            setColor(Color.rgb(226, 246, 239))
            cornerRadius = 12f
            setStroke(1, Color.rgb(126, 205, 174))
        }
    }
}
