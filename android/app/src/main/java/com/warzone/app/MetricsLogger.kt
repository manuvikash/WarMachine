package com.warzone.app

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

class MetricsLogger(private val context: Context) {

    companion object {
        private const val TAG = "MetricsLogger"
        private const val MAX_ENTRIES = 500
        private const val LOG_FILE = "warzone_metrics.jsonl"
    }

    data class MetricEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val event: String,
        val durationMs: Long? = null,
        val metadata: Map<String, Any?> = emptyMap()
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("timestamp", timestamp)
            put("event", event)
            put("time", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date(timestamp)))
            durationMs?.let { put("duration_ms", it) }
            if (metadata.isNotEmpty()) {
                put("metadata", JSONObject(metadata))
            }
        }
    }

    private val entries = ConcurrentLinkedQueue<MetricEntry>()
    private val activeTimers = mutableMapOf<String, Long>()

    fun startTimer(event: String): Long {
        val start = System.currentTimeMillis()
        activeTimers[event] = start
        Log.d(TAG, "TIMER START: $event")
        return start
    }

    fun stopTimer(event: String, metadata: Map<String, Any?> = emptyMap()): Long {
        val start = activeTimers.remove(event) ?: run {
            Log.w(TAG, "No timer found for: $event")
            return -1
        }
        val duration = System.currentTimeMillis() - start
        log(event, duration, metadata)
        return duration
    }

    fun log(event: String, durationMs: Long? = null, metadata: Map<String, Any?> = emptyMap()) {
        val entry = MetricEntry(event = event, durationMs = durationMs, metadata = metadata)
        entries.add(entry)

        while (entries.size > MAX_ENTRIES) {
            entries.poll()
        }

        val durationStr = durationMs?.let { " (${it}ms)" } ?: ""
        Log.i(TAG, "METRIC: $event$durationStr ${if (metadata.isNotEmpty()) metadata else ""}")

        appendToFile(entry)
    }

    fun getMetricsJson(): JSONArray {
        val arr = JSONArray()
        entries.forEach { arr.put(it.toJson()) }
        return arr
    }

    fun getSummary(): Map<String, Map<String, Any>> {
        val grouped = entries.filter { it.durationMs != null }.groupBy { it.event }
        return grouped.mapValues { (_, items) ->
            val durations = items.mapNotNull { it.durationMs }
            mapOf(
                "count" to durations.size,
                "avg_ms" to if (durations.isNotEmpty()) durations.average().toLong() else 0L,
                "min_ms" to (durations.minOrNull() ?: 0L),
                "max_ms" to (durations.maxOrNull() ?: 0L),
                "total_ms" to durations.sum()
            )
        }
    }

    fun getRecent(count: Int = 50): List<MetricEntry> {
        return entries.toList().takeLast(count)
    }

    fun clear() {
        entries.clear()
        activeTimers.clear()
        Log.d(TAG, "Metrics cleared")
    }

    private fun appendToFile(entry: MetricEntry) {
        try {
            val file = File(context.filesDir, LOG_FILE)
            file.appendText(entry.toJson().toString() + "\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write metric to file", e)
        }
    }

    fun getMetricsFilePath(): String {
        return File(context.filesDir, LOG_FILE).absolutePath
    }
}
