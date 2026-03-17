package com.warzone.app

import android.app.Application
import android.util.Log

class WarzoneApp : Application() {

    companion object {
        private const val TAG = "WarzoneApp"
        lateinit var instance: WarzoneApp
            private set
    }

    lateinit var metricsLogger: MetricsLogger
        private set

    lateinit var glassesManager: GlassesManager
        private set

    lateinit var apiClient: ApiClient
        private set

    lateinit var audioRecorder: AudioRecorder
        private set

    lateinit var audioPlayer: AudioPlayer
        private set

    lateinit var featureManager: FeatureManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "=== Warzone App Starting ===")
        Log.i(TAG, "API Base URL: ${BuildConfig.API_BASE_URL}")

        metricsLogger = MetricsLogger(this)
        glassesManager = GlassesManager(this, metricsLogger)
        apiClient = ApiClient(BuildConfig.API_BASE_URL, metricsLogger)
        audioRecorder = AudioRecorder(this, metricsLogger)
        audioPlayer = AudioPlayer(this, metricsLogger)
        featureManager = FeatureManager(
            glassesManager = glassesManager,
            audioRecorder = audioRecorder,
            audioPlayer = audioPlayer,
            apiClient = apiClient,
            metricsLogger = metricsLogger
        )

        Log.i(TAG, "=== All managers initialized ===")
    }
}
