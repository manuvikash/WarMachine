package com.warzone.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import com.meta.wearable.dat.camera.StreamSession
import com.meta.wearable.dat.camera.startStreamSession
import com.meta.wearable.dat.camera.types.PhotoData
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamSessionState
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class GlassesManager(
    private val application: Application,
    private val metrics: MetricsLogger
) {

    companion object {
        private const val TAG = "GlassesManager"
    }

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Initializing : ConnectionState()
        object WaitingForRegistration : ConnectionState()
        object Registered : ConnectionState()
        data class Connected(val deviceName: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing

    private val _lastFrame = MutableStateFlow<Bitmap?>(null)
    val lastFrame: StateFlow<Bitmap?> = _lastFrame

    private val _frameCount = MutableStateFlow(0L)
    val frameCount: StateFlow<Long> = _frameCount

    private val frameLock = Any()

    private val scope = CoroutineScope(Dispatchers.Main)
    private val deviceSelector = AutoDeviceSelector()

    private var streamSession: StreamSession? = null
    private var videoJob: Job? = null
    private var stateJob: Job? = null
    private var registrationJob: Job? = null
    private var deviceJob: Job? = null

    private var isInitialized = false
    private var streamStartRequestTime = 0L

    fun initialize() {
        if (isInitialized) return

        _connectionState.value = ConnectionState.Initializing
        metrics.startTimer("sdk_initialization")

        try {
            Wearables.initialize(application)
            isInitialized = true
            val duration = metrics.stopTimer("sdk_initialization")
            Log.i(TAG, "SDK initialized in ${duration}ms")
            startMonitoring()
        } catch (e: Exception) {
            metrics.stopTimer("sdk_initialization")
            metrics.log("sdk_initialization_error", metadata = mapOf("error" to e.message))
            Log.e(TAG, "Failed to initialize Wearables SDK", e)
            _connectionState.value = ConnectionState.Error("SDK init failed: ${e.message}")
        }
    }

    private fun startMonitoring() {
        metrics.startTimer("device_discovery")

        registrationJob = scope.launch {
            Wearables.registrationState.collect { state ->
                Log.d(TAG, "Registration state: $state")
                metrics.log("registration_state_change", metadata = mapOf("state" to state.toString()))

                when (state) {
                    is RegistrationState.Unavailable,
                    is RegistrationState.Available -> {
                        _connectionState.value = ConnectionState.WaitingForRegistration
                    }
                    is RegistrationState.Registering,
                    is RegistrationState.Unregistering -> {
                        _connectionState.value = ConnectionState.Initializing
                    }
                    is RegistrationState.Registered -> {
                        _connectionState.value = ConnectionState.Registered
                    }
                }
            }
        }

        deviceJob = scope.launch {
            deviceSelector.activeDevice(Wearables.devices).collect { device ->
                if (device != null) {
                    metrics.stopTimer("device_discovery")
                    metrics.log("device_connected", metadata = mapOf("device" to device.toString()))
                    _connectionState.value = ConnectionState.Connected(device.toString())
                } else if (_connectionState.value is ConnectionState.Connected) {
                    metrics.log("device_disconnected")
                    _connectionState.value = ConnectionState.Registered
                }
            }
        }
    }

    fun startRegistration(activity: android.app.Activity) {
        metrics.log("registration_started")
        Wearables.startRegistration(activity)
    }

    suspend fun checkCameraPermission(): PermissionStatus {
        metrics.startTimer("camera_permission_check")
        val result = Wearables.checkPermissionStatus(Permission.CAMERA)
        val status = result.getOrDefault(PermissionStatus.Denied)
        metrics.stopTimer("camera_permission_check", mapOf("status" to status.toString()))
        return status
    }

    fun startStreaming() {
        if (streamSession != null) {
            val currentState = streamSession?.state?.value
            if (currentState == StreamSessionState.STREAMING) {
                Log.d(TAG, "Stream already active")
                return
            } else {
                Log.d(TAG, "Restarting stream (was $currentState)")
                stopStreaming()
            }
        }

        streamStartRequestTime = System.currentTimeMillis()
        metrics.startTimer("camera_turn_on")
        metrics.startTimer("stream_initialization")
        _frameCount.value = 0

        scope.launch {
            try {
                val session = Wearables.startStreamSession(
                    application,
                    deviceSelector,
                    StreamConfiguration(videoQuality = VideoQuality.MEDIUM, 24)
                )
                streamSession = session

                val sessionCreatedTime = System.currentTimeMillis()
                metrics.log(
                    "stream_session_created",
                    durationMs = sessionCreatedTime - streamStartRequestTime,
                    metadata = mapOf("quality" to "MEDIUM", "fps" to 24)
                )

                stateJob = scope.launch {
                    session.state.collect { state ->
                        Log.d(TAG, "Stream state: $state")
                        when (state) {
                            StreamSessionState.STREAMING -> {
                                val totalDelay = metrics.stopTimer("camera_turn_on")
                                metrics.stopTimer("stream_initialization")
                                metrics.log(
                                    "stream_active",
                                    durationMs = totalDelay,
                                    metadata = mapOf(
                                        "total_startup_delay_ms" to totalDelay,
                                        "session_creation_ms" to (sessionCreatedTime - streamStartRequestTime)
                                    )
                                )
                                _isStreaming.value = true
                                Log.i(TAG, "Camera ON — took ${totalDelay}ms total")
                            }
                            StreamSessionState.STOPPED -> {
                                metrics.log("stream_stopped")
                                _isStreaming.value = false
                            }
                            else -> {
                                Log.d(TAG, "Stream state: $state")
                            }
                        }
                    }
                }

                videoJob = scope.launch {
                    var firstFrameLogged = false
                    session.videoStream.collect { frame ->
                        try {
                            if (!firstFrameLogged) {
                                val firstFrameDelay = System.currentTimeMillis() - streamStartRequestTime
                                metrics.log(
                                    "first_frame_received",
                                    durationMs = firstFrameDelay,
                                    metadata = mapOf(
                                        "width" to frame.width,
                                        "height" to frame.height
                                    )
                                )
                                firstFrameLogged = true
                                Log.i(TAG, "First frame in ${firstFrameDelay}ms (${frame.width}x${frame.height})")
                            }

                            val buffer = frame.buffer
                            val dataSize = buffer.remaining()
                            if (dataSize <= 0) return@collect
                            val byteArray = ByteArray(dataSize)
                            buffer.get(byteArray)

                            val bitmap = convertFrameToBitmap(byteArray, frame.width, frame.height)
                            synchronized(frameLock) {
                                _lastFrame.value = bitmap
                            }
                            _frameCount.value++

                            if (_frameCount.value % 100 == 0L) {
                                metrics.log(
                                    "frame_milestone",
                                    metadata = mapOf(
                                        "frame_count" to _frameCount.value,
                                        "width" to frame.width,
                                        "height" to frame.height,
                                        "data_size_bytes" to dataSize
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Frame processing error", e)
                        }
                    }
                }

            } catch (e: Exception) {
                metrics.stopTimer("camera_turn_on")
                metrics.stopTimer("stream_initialization")
                metrics.log("stream_start_error", metadata = mapOf("error" to e.message))
                Log.e(TAG, "Failed to start streaming", e)
                _connectionState.value = ConnectionState.Error("Stream failed: ${e.message}")
            }
        }
    }

    fun stopStreaming() {
        Log.d(TAG, "Stopping stream...")
        metrics.log("stream_stop_requested", metadata = mapOf("frame_count" to _frameCount.value))

        videoJob?.cancel()
        videoJob = null
        stateJob?.cancel()
        stateJob = null

        try {
            streamSession?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing stream", e)
        }
        streamSession = null
        _isStreaming.value = false
        _isCapturing.value = false

        synchronized(frameLock) {
            _lastFrame.value = null
        }
    }

    suspend fun capturePhoto(): ByteArray? {
        val session = streamSession ?: run {
            Log.e(TAG, "No active stream for photo capture")
            return null
        }

        _isCapturing.value = true
        metrics.startTimer("photo_capture")

        return try {
            val result = session.capturePhoto()
            val photoData = result.getOrNull() ?: run {
                metrics.stopTimer("photo_capture")
                metrics.log("photo_capture_failed", metadata = mapOf("reason" to "null_result"))
                return null
            }

            val jpeg = when (photoData) {
                is PhotoData.Bitmap -> {
                    bitmapToJpeg(photoData.bitmap)
                }
                is PhotoData.HEIC -> {
                    val bytes = ByteArray(photoData.data.remaining())
                    photoData.data.get(bytes)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    bitmap?.let { bitmapToJpeg(it) }
                }
            }

            val duration = metrics.stopTimer("photo_capture", mapOf(
                "size_bytes" to (jpeg?.size ?: 0),
                "format" to photoData::class.simpleName
            ))
            Log.i(TAG, "Photo captured in ${duration}ms (${jpeg?.size ?: 0} bytes)")
            jpeg
        } catch (e: Exception) {
            metrics.stopTimer("photo_capture")
            metrics.log("photo_capture_error", metadata = mapOf("error" to e.message))
            Log.e(TAG, "Photo capture failed", e)
            null
        } finally {
            _isCapturing.value = false
        }
    }

    fun getCurrentFrameAsJpeg(quality: Int = 85): ByteArray? {
        return synchronized(frameLock) {
            _lastFrame.value?.let { bitmap ->
                try {
                    if (bitmap.isRecycled) null else bitmapToJpeg(bitmap, quality)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to convert frame to JPEG", e)
                    null
                }
            }
        }
    }

    suspend fun captureFrameSequence(
        durationMs: Long,
        intervalMs: Long = 500
    ): List<Pair<Long, ByteArray>> {
        val frames = mutableListOf<Pair<Long, ByteArray>>()
        metrics.startTimer("frame_sequence_capture")

        val startTime = System.currentTimeMillis()
        var lastCaptureTime = 0L

        while (System.currentTimeMillis() - startTime < durationMs) {
            val now = System.currentTimeMillis()
            if (now - lastCaptureTime >= intervalMs) {
                getCurrentFrameAsJpeg()?.let { jpeg ->
                    frames.add(Pair(now, jpeg))
                }
                lastCaptureTime = now
            }
            kotlinx.coroutines.delay(50)
        }

        metrics.stopTimer("frame_sequence_capture", mapOf(
            "frames_captured" to frames.size,
            "duration_ms" to durationMs,
            "interval_ms" to intervalMs
        ))
        Log.i(TAG, "Captured ${frames.size} frames over ${durationMs}ms")
        return frames
    }

    private fun bitmapToJpeg(bitmap: Bitmap, quality: Int = 90): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    private fun convertFrameToBitmap(data: ByteArray, width: Int, height: Int): Bitmap {
        val nv21 = convertI420toNV21(data, width, height)
        val image = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val stream = ByteArrayOutputStream()
        image.compressToJpeg(Rect(0, 0, width, height), 80, stream)
        val jpegBytes = stream.toByteArray()
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    private fun convertI420toNV21(input: ByteArray, width: Int, height: Int): ByteArray {
        val output = ByteArray(input.size)
        val size = width * height
        val quarter = size / 4
        input.copyInto(output, 0, 0, size)
        for (n in 0 until quarter) {
            output[size + n * 2] = input[size + quarter + n]
            output[size + n * 2 + 1] = input[size + n]
        }
        return output
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting...")
        metrics.log("glasses_disconnect")
        stopStreaming()
    }

    fun cleanup() {
        Log.d(TAG, "Cleanup...")
        stopStreaming()
        registrationJob?.cancel()
        registrationJob = null
        deviceJob?.cancel()
        deviceJob = null
        _connectionState.value = ConnectionState.Disconnected
        _lastFrame.value = null
    }
}
