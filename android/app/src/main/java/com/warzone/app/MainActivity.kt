package com.warzone.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.warzone.app.databinding.ActivityMainBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding

    private val glassesManager by lazy { WarzoneApp.instance.glassesManager }
    private val apiClient by lazy { WarzoneApp.instance.apiClient }
    private val metricsLogger by lazy { WarzoneApp.instance.metricsLogger }
    private val featureManager by lazy { WarzoneApp.instance.featureManager }
    private val audioPlayer by lazy { WarzoneApp.instance.audioPlayer }

    private var isStreamActive = false
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private lateinit var historyAdapter: HistoryAdapter

    private val requiredPermissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.BLUETOOTH)
        }
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            log("All permissions granted")
            initializeWearables()
        } else {
            val denied = permissions.filter { !it.value }.keys.map { it.substringAfterLast(".") }
            log("Missing permissions: ${denied.joinToString()}")
            initializeWearables()
        }
    }

    private val wearablesPermissionLauncher = registerForActivityResult(
        Wearables.RequestPermissionContract()
    ) { result ->
        val status = result.getOrDefault(PermissionStatus.Denied)
        if (status == PermissionStatus.Granted) {
            log("Glasses camera permission granted")
        } else {
            log("Glasses camera permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeState()

        log("Warzone started")
        checkPermissionsAndInit()
        testApiConnection()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isStreamActive) {
            glassesManager.stopStreaming()
        }
        audioPlayer.shutdown()
        glassesManager.cleanup()
    }

    private fun log(message: String) {
        val time = timeFormat.format(Date())
        val logLine = "[$time] $message\n"
        runOnUiThread {
            binding.tvLog.append(logLine)
            binding.scrollLog.post {
                binding.scrollLog.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun setupUI() {
        binding.btnConnect.setOnClickListener {
            when (glassesManager.connectionState.value) {
                is GlassesManager.ConnectionState.WaitingForRegistration -> {
                    log("Opening Meta AI for registration...")
                    glassesManager.startRegistration(this@MainActivity)
                }
                is GlassesManager.ConnectionState.Connected -> {
                    stopStream()
                    glassesManager.disconnect()
                }
                is GlassesManager.ConnectionState.Disconnected,
                is GlassesManager.ConnectionState.Error -> {
                    checkPermissionsAndInit()
                }
                else -> {}
            }
        }

        binding.btnStream.setOnClickListener {
            if (isStreamActive) {
                stopStream()
            } else {
                startStream()
            }
        }

        binding.btnFirstAid.setOnClickListener {
            if (!checkReady()) return@setOnClickListener
            log(">>> FIRST AID triggered")
            featureManager.triggerFirstAid()
        }

        binding.btnHazard.setOnClickListener {
            if (!checkReady()) return@setOnClickListener
            log(">>> HAZARD DETECTION triggered")
            featureManager.triggerHazard()
        }

        binding.btnSurvival.setOnClickListener {
            if (!checkReady()) return@setOnClickListener
            log(">>> SURVIVAL GUIDE triggered")
            featureManager.triggerSurvival()
        }

        binding.btnMap.setOnClickListener {
            Toast.makeText(this, "Map coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.btnCancel.setOnClickListener {
            featureManager.cancelCurrent()
            log("Operation cancelled")
        }

        binding.btnClearLog.setOnClickListener {
            binding.tvLog.text = ""
            binding.tvApiResponse.text = "No API response yet"
            log("Log cleared")
        }

        binding.btnMetrics.setOnClickListener {
            showMetricsSummary()
        }

        binding.cardPreview.setOnClickListener {
            glassesManager.lastFrame.value?.let { bitmap ->
                showFullscreenImage(bitmap)
            }
        }

        historyAdapter = HistoryAdapter { entry -> showHistoryDetail(entry) }
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = historyAdapter
    }

    private fun checkReady(): Boolean {
        val state = glassesManager.connectionState.value
        if (state !is GlassesManager.ConnectionState.Connected) {
            Toast.makeText(this, "Connect glasses first", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun observeState() {
        lifecycleScope.launch {
            glassesManager.connectionState.collectLatest { state ->
                updateConnectionUI(state)
            }
        }

        lifecycleScope.launch {
            glassesManager.lastFrame.collectLatest { bitmap ->
                bitmap?.let {
                    binding.ivPreview.setImageBitmap(it)
                }
            }
        }

        lifecycleScope.launch {
            glassesManager.frameCount.collectLatest { count ->
                if (count > 0) {
                    binding.tvFrameCount.text = "Frames: $count"
                    binding.tvFrameCount.visibility = View.VISIBLE
                }
            }
        }

        lifecycleScope.launch {
            featureManager.state.collectLatest { state ->
                updateFeatureUI(state)
            }
        }

        lifecycleScope.launch {
            featureManager.lastApiResponse.collectLatest { response ->
                response?.let { displayApiResponse(it) }
            }
        }

        lifecycleScope.launch {
            featureManager.historyEntry.collectLatest { entry ->
                entry?.let {
                    historyAdapter.addEntry(it)
                    binding.rvHistory.scrollToPosition(0)
                    binding.tvHistoryEmpty.visibility = View.GONE
                    binding.rvHistory.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun updateConnectionUI(state: GlassesManager.ConnectionState) {
        when (state) {
            is GlassesManager.ConnectionState.Disconnected -> {
                binding.tvStatus.text = "DISCONNECTED"
                binding.tvStatus.setTextColor(getColor(android.R.color.holo_red_light))
                binding.btnConnect.text = "Initialize"
                binding.btnConnect.isEnabled = true
                binding.btnStream.isEnabled = false
                setFeatureButtonsEnabled(false)
            }
            is GlassesManager.ConnectionState.Initializing -> {
                binding.tvStatus.text = "INITIALIZING..."
                binding.tvStatus.setTextColor(getColor(android.R.color.holo_orange_light))
                binding.btnConnect.text = "Connect"
                binding.btnConnect.isEnabled = true
            }
            is GlassesManager.ConnectionState.WaitingForRegistration -> {
                binding.tvStatus.text = "TAP CONNECT"
                binding.tvStatus.setTextColor(getColor(android.R.color.holo_orange_light))
                binding.btnConnect.text = "Connect"
                binding.btnConnect.isEnabled = true
                binding.btnStream.isEnabled = false
            }
            is GlassesManager.ConnectionState.Registered -> {
                binding.tvStatus.text = "REGISTERED"
                binding.tvStatus.setTextColor(getColor(android.R.color.holo_orange_light))
                binding.btnConnect.text = "Connect"
                binding.btnConnect.isEnabled = true
                binding.btnStream.isEnabled = false
            }
            is GlassesManager.ConnectionState.Connected -> {
                binding.tvStatus.text = "CONNECTED"
                binding.tvStatus.setTextColor(getColor(android.R.color.holo_green_light))
                binding.btnConnect.text = "Disconnect"
                binding.btnConnect.isEnabled = true
                binding.btnStream.isEnabled = true
                setFeatureButtonsEnabled(true)
                log("GLASSES CONNECTED: ${state.deviceName}")
            }
            is GlassesManager.ConnectionState.Error -> {
                binding.tvStatus.text = "ERROR"
                binding.tvStatus.setTextColor(getColor(android.R.color.holo_red_light))
                binding.btnConnect.text = "Retry"
                binding.btnConnect.isEnabled = true
                binding.btnStream.isEnabled = false
                setFeatureButtonsEnabled(false)
                log("Error: ${state.message}")
            }
        }
    }

    private fun updateFeatureUI(state: FeatureManager.FeatureState) {
        when (state) {
            is FeatureManager.FeatureState.Idle -> {
                binding.tvFeatureStatus.text = "Ready"
                binding.tvFeatureStatus.setTextColor(getColor(android.R.color.white))
                binding.progressFeature.visibility = View.GONE
                binding.btnCancel.visibility = View.GONE
                setFeatureButtonsEnabled(isStreamActive)
            }
            is FeatureManager.FeatureState.Capturing -> {
                val name = state.feature.name.replace('_', ' ')
                binding.tvFeatureStatus.text = "CAPTURING: $name"
                binding.tvFeatureStatus.setTextColor(getColor(android.R.color.holo_orange_light))
                binding.progressFeature.visibility = View.VISIBLE
                binding.btnCancel.visibility = View.VISIBLE
                setFeatureButtonsEnabled(false)
                log("Capturing input for $name...")
            }
            is FeatureManager.FeatureState.Processing -> {
                val name = state.feature.name.replace('_', ' ')
                binding.tvFeatureStatus.text = "PROCESSING: $name"
                binding.tvFeatureStatus.setTextColor(getColor(android.R.color.holo_blue_light))
                binding.progressFeature.visibility = View.VISIBLE
                log("Backend processing $name...")
            }
            is FeatureManager.FeatureState.Speaking -> {
                val name = state.feature.name.replace('_', ' ')
                binding.tvFeatureStatus.text = "SPEAKING: $name"
                binding.tvFeatureStatus.setTextColor(getColor(android.R.color.holo_green_light))
                binding.progressFeature.visibility = View.GONE
                log("Speaking response for $name...")
            }
            is FeatureManager.FeatureState.Result -> {
                val name = state.feature.name.replace('_', ' ')
                binding.tvFeatureStatus.text = "DONE: $name"
                binding.tvFeatureStatus.setTextColor(getColor(android.R.color.holo_green_light))
                binding.progressFeature.visibility = View.GONE
                binding.btnCancel.visibility = View.GONE
                setFeatureButtonsEnabled(isStreamActive)
                log("Result for $name: ${state.response.take(100)}")
            }
            is FeatureManager.FeatureState.Error -> {
                val name = state.feature.name.replace('_', ' ')
                binding.tvFeatureStatus.text = "FAILED: $name"
                binding.tvFeatureStatus.setTextColor(getColor(android.R.color.holo_red_light))
                binding.progressFeature.visibility = View.GONE
                binding.btnCancel.visibility = View.GONE
                setFeatureButtonsEnabled(isStreamActive)
                log("ERROR in $name: ${state.message}")
            }
        }
    }

    private fun displayApiResponse(rawJson: String) {
        runOnUiThread {
            try {
                val formatted = JSONObject(rawJson).toString(2)
                binding.tvApiResponse.text = formatted
            } catch (e: Exception) {
                binding.tvApiResponse.text = rawJson
            }
        }
    }

    private fun setFeatureButtonsEnabled(enabled: Boolean) {
        binding.btnFirstAid.isEnabled = enabled
        binding.btnHazard.isEnabled = enabled
        binding.btnSurvival.isEnabled = enabled
    }

    private fun startStream() {
        isStreamActive = true
        binding.btnStream.text = "STOP STREAM"
        binding.btnStream.setBackgroundColor(getColor(android.R.color.holo_red_dark))
        log("Starting camera stream...")

        lifecycleScope.launch {
            val permStatus = glassesManager.checkCameraPermission()
            if (permStatus != PermissionStatus.Granted) {
                log("Requesting glasses camera permission...")
                wearablesPermissionLauncher.launch(Permission.CAMERA)
                delay(3000)
            }

            log("Starting camera stream...")
            glassesManager.startStreaming()

            var waitMs = 0
            while (glassesManager.lastFrame.value == null && waitMs < 10_000) {
                delay(250)
                waitMs += 250
            }

            if (glassesManager.lastFrame.value != null) {
                log("Camera active (${waitMs}ms)")
            } else {
                log("Warning: No frames after ${waitMs}ms")
            }
        }
    }

    private fun stopStream() {
        glassesManager.stopStreaming()
        isStreamActive = false
        binding.btnStream.text = "START STREAM"
        binding.btnStream.setBackgroundColor(getColor(android.R.color.holo_green_dark))
        log("Camera stream stopped")
    }

    private fun showMetricsSummary() {
        val summary = metricsLogger.getSummary()
        if (summary.isEmpty()) {
            log("No metrics recorded yet")
            return
        }

        val sb = StringBuilder()
        sb.appendLine("=== PERFORMANCE METRICS ===")
        summary.forEach { (event, stats) ->
            sb.appendLine("$event: count=${stats["count"]}, avg=${stats["avg_ms"]}ms, min=${stats["min_ms"]}ms, max=${stats["max_ms"]}ms")
        }
        sb.appendLine("===========================")
        log(sb.toString())

        val dialogMsg = summary.entries.joinToString("\n\n") { (event, stats) ->
            "$event\n  Count: ${stats["count"]}\n  Avg: ${stats["avg_ms"]}ms\n  Min: ${stats["min_ms"]}ms\n  Max: ${stats["max_ms"]}ms"
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Performance Metrics")
            .setMessage(dialogMsg)
            .setPositiveButton("OK", null)
            .setNeutralButton("Clear") { _, _ ->
                metricsLogger.clear()
                log("Metrics cleared")
            }
            .show()
    }

    private fun checkPermissionsAndInit() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            initializeWearables()
        } else {
            log("Requesting permissions...")
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun initializeWearables() {
        try {
            glassesManager.initialize()
        } catch (e: Exception) {
            log("Init error: ${e.message}")
        }
    }

    private fun testApiConnection() {
        lifecycleScope.launch {
            log("Testing server connection...")
            val healthy = apiClient.healthCheck()
            if (healthy) {
                log("Server connected: ${BuildConfig.API_BASE_URL}")
            } else {
                log("Server not reachable: ${BuildConfig.API_BASE_URL}")
            }
        }
    }

    private fun showHistoryDetail(entry: HistoryEntry) {
        val density = resources.displayMetrics.density

        val scrollView = android.widget.ScrollView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(0xFF0D0D0D.toInt())
        }

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, 0, 0, (16 * density).toInt())
        }
        scrollView.addView(container)

        val bitmap = entry.decodeBitmap()
        if (bitmap != null) {
            val imageView = android.widget.ImageView(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    (200 * density).toInt()
                )
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(android.graphics.Color.BLACK)
                setImageBitmap(bitmap)
            }
            container.addView(imageView)
        }

        val responseText = entry.formatResponse()

        val textView = android.widget.TextView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = responseText
            setTextColor(if (entry.isError) 0xFFFF5555.toInt() else 0xFFCCCCCC.toInt())
            textSize = 13f
            setPadding(
                (16 * density).toInt(),
                (12 * density).toInt(),
                (16 * density).toInt(),
                (8 * density).toInt()
            )
            setLineSpacing(4 * density, 1f)
        }
        container.addView(textView)

        if (responseText.isBlank()) {
            textView.text = entry.rawJson.ifBlank { entry.responseText.ifBlank { "No response data" } }
        }

        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val title = "${entry.featureLabel} — ${sdf.format(Date(entry.timestamp))}"

        androidx.appcompat.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showFullscreenImage(bitmap: android.graphics.Bitmap) {
        val imageView = android.widget.ImageView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(android.graphics.Color.BLACK)
            setImageBitmap(bitmap.copy(bitmap.config ?: android.graphics.Bitmap.Config.ARGB_8888, false))
        }

        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(imageView)
        dialog.setCancelable(true)
        imageView.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
