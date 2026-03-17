package com.warzone.app

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FeatureManager(
    private val glassesManager: GlassesManager,
    private val audioRecorder: AudioRecorder,
    private val audioPlayer: AudioPlayer,
    private val apiClient: ApiClient,
    private val metricsLogger: MetricsLogger,
    private val audioDurationMs: Long = 5_000L
) {

    companion object {
        private const val TAG = "FeatureManager"
    }

    enum class Feature {
        FIRST_AID, HAZARD, SURVIVAL
    }

    sealed class FeatureState {
        object Idle : FeatureState()
        data class Capturing(val feature: Feature) : FeatureState()
        data class Processing(val feature: Feature) : FeatureState()
        data class Speaking(val feature: Feature) : FeatureState()
        data class Result(val feature: Feature, val response: String, val rawJson: String) : FeatureState()
        data class Error(val feature: Feature, val message: String) : FeatureState()
    }

    private val _state = MutableStateFlow<FeatureState>(FeatureState.Idle)
    val state: StateFlow<FeatureState> = _state

    private val _lastApiResponse = MutableStateFlow<String?>(null)
    val lastApiResponse: StateFlow<String?> = _lastApiResponse

    private val _lastAudioText = MutableStateFlow<String?>(null)
    val lastAudioText: StateFlow<String?> = _lastAudioText

    private val _lastCapturedImage = MutableStateFlow<ByteArray?>(null)
    val lastCapturedImage: StateFlow<ByteArray?> = _lastCapturedImage

    private val _historyEntry = MutableStateFlow<HistoryEntry?>(null)
    val historyEntry: StateFlow<HistoryEntry?> = _historyEntry

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "[ERROR] Uncaught coroutine exception", throwable)
        _state.value = FeatureState.Error(Feature.FIRST_AID, throwable.message ?: "Unknown error")
    }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob() + exceptionHandler)

    @Volatile
    private var isProcessing = false

    // ─── Feature Triggers ─────────────────────────────────────────

    fun triggerFirstAid(contextHint: String = "") {
        executeFeature(Feature.FIRST_AID, contextHint)
    }

    fun triggerHazard(contextHint: String = "") {
        executeFeature(Feature.HAZARD, contextHint)
    }

    fun triggerSurvival(contextHint: String = "") {
        executeFeature(Feature.SURVIVAL, contextHint)
    }

    // ─── Core Feature Execution ───────────────────────────────────

    private fun executeFeature(feature: Feature, contextHint: String) {
        scope.launch {
            if (isProcessing) {
                Log.w(TAG, "[${feature.name}] Already processing, skipping")
                return@launch
            }
            isProcessing = true
            metricsLogger.startTimer("feature_${feature.name.lowercase()}_total")
            Log.i(TAG, "=== Starting ${feature.name} ===")

            try {
                // Step 1: Capture input from glasses
                _state.value = FeatureState.Capturing(feature)
                Log.d(TAG, "[${feature.name}] Capturing input from glasses...")

                val captureResult = captureInput()
                val imageData = captureResult.first
                val audioData = captureResult.second

                Log.i(TAG, "[${feature.name}] Captured - image: ${imageData?.size ?: 0}B, audio: ${audioData?.size ?: 0}B")
                _lastCapturedImage.value = imageData

                // Step 2: Send to backend
                _state.value = FeatureState.Processing(feature)
                Log.d(TAG, "[${feature.name}] Sending to backend...")

                val resp = when (feature) {
                    Feature.FIRST_AID -> {
                        val r = apiClient.analyzeFirstAid(imageData, audioData, contextHint)
                        if (!r.success) throw Exception(r.error ?: "First aid analysis failed")
                        r
                    }
                    Feature.HAZARD -> {
                        val r = apiClient.analyzeHazard(imageData, audioData, contextHint)
                        if (!r.success) throw Exception(r.error ?: "Hazard analysis failed")
                        r
                    }
                    Feature.SURVIVAL -> {
                        val r = apiClient.analyzeSurvival(imageData, audioData, contextHint)
                        if (!r.success) throw Exception(r.error ?: "Survival guide failed")
                        r
                    }
                }

                Log.i(TAG, "[${feature.name}] Response: ${resp.response.take(100)}")
                Log.i(TAG, "[${feature.name}] TTS: ${resp.ttsSummary.take(100)}")

                val totalDuration = metricsLogger.stopTimer("feature_${feature.name.lowercase()}_total")
                Log.i(TAG, "[${feature.name}] Complete in ${totalDuration}ms")

                _lastApiResponse.value = resp.rawJson
                _lastAudioText.value = resp.ttsSummary

                // Step 3: Speak TTS summary
                _state.value = FeatureState.Speaking(feature)
                Log.d(TAG, "[${feature.name}] Speaking TTS summary...")

                if (resp.ttsSummary.isNotEmpty()) {
                    audioPlayer.speak(resp.ttsSummary)
                }

                _state.value = FeatureState.Result(feature, resp.response, resp.rawJson)

                _historyEntry.value = HistoryEntry(
                    feature = feature,
                    timestamp = System.currentTimeMillis(),
                    capturedImage = _lastCapturedImage.value,
                    responseText = resp.response,
                    ttsSummary = resp.ttsSummary,
                    rawJson = resp.rawJson
                )

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                metricsLogger.stopTimer("feature_${feature.name.lowercase()}_total")
                Log.e(TAG, "[${feature.name}] Failed", e)
                _state.value = FeatureState.Error(feature, e.message ?: "Unknown error")

                _historyEntry.value = HistoryEntry(
                    feature = feature,
                    timestamp = System.currentTimeMillis(),
                    capturedImage = _lastCapturedImage.value,
                    responseText = e.message ?: "Unknown error",
                    rawJson = "",
                    isError = true
                )

                audioPlayer.speakUrgent("Error processing ${feature.name.lowercase().replace('_', ' ')}. ${e.message}")
            } finally {
                isProcessing = false
            }
        }
    }

    // ─── Input Capture ────────────────────────────────────────────

    private suspend fun captureInput(): Pair<ByteArray?, ByteArray?> {
        metricsLogger.startTimer("input_capture")

        return coroutineScope {
            val imageDeferred = async(Dispatchers.Default) {
                if (glassesManager.isStreaming.value) {
                    Log.d(TAG, "[CAPTURE] Getting current frame...")
                    glassesManager.getCurrentFrameAsJpeg()
                        ?: glassesManager.capturePhoto()
                } else {
                    Log.d(TAG, "[CAPTURE] Attempting photo capture (stream not active)")
                    glassesManager.capturePhoto()
                }
            }

            val audioDeferred = async(Dispatchers.IO) {
                Log.d(TAG, "[CAPTURE] Recording audio for ${audioDurationMs}ms...")
                audioRecorder.recordAudio(audioDurationMs)
            }

            val image = imageDeferred.await()
            val audio = audioDeferred.await()

            metricsLogger.stopTimer("input_capture", mapOf(
                "image_bytes" to (image?.size ?: 0),
                "audio_bytes" to (audio?.size ?: 0)
            ))

            Pair(image, audio)
        }
    }

    fun cancelCurrent() {
        audioPlayer.stop()
        _state.value = FeatureState.Idle
        isProcessing = false
        Log.i(TAG, "[CANCEL] Current operation cancelled")
    }
}
