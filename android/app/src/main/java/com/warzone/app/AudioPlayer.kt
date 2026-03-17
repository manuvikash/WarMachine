package com.warzone.app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class AudioPlayer(
    private val context: Context,
    private val metricsLogger: MetricsLogger
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "AudioPlayer"
    }

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var mediaPlayer: MediaPlayer? = null
    private val pendingSpeak = mutableListOf<String>()

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setSpeechRate(1.1f)
            tts?.setPitch(1.0f)
            isReady = true
            Log.i(TAG, "[TTS] Initialized successfully")

            if (pendingSpeak.isNotEmpty()) {
                pendingSpeak.forEach { speak(it) }
                pendingSpeak.clear()
            }
        } else {
            Log.e(TAG, "[TTS] Initialization failed with status: $status")
            metricsLogger.log("tts_init_failed", metadata = mapOf("status" to status))
        }
    }

    fun speak(text: String, utteranceId: String = "warzone_${System.currentTimeMillis()}") {
        if (!isReady) {
            Log.w(TAG, "[TTS] Not ready yet, queuing: ${text.take(50)}...")
            pendingSpeak.add(text)
            return
        }

        metricsLogger.startTimer("tts_speak")
        Log.i(TAG, "[TTS] Speaking: ${text.take(80)}...")

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                Log.d(TAG, "[TTS] Utterance started: $id")
            }

            override fun onDone(id: String?) {
                val duration = metricsLogger.stopTimer("tts_speak")
                Log.d(TAG, "[TTS] Utterance done: $id (${duration}ms)")
            }

            @Deprecated("Deprecated in API")
            override fun onError(id: String?) {
                metricsLogger.stopTimer("tts_speak")
                Log.e(TAG, "[TTS] Utterance error: $id")
            }
        })

        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    fun speakUrgent(text: String) {
        if (!isReady) {
            pendingSpeak.clear()
            pendingSpeak.add(text)
            return
        }
        Log.w(TAG, "[TTS] URGENT: ${text.take(80)}...")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "urgent_${System.currentTimeMillis()}")
    }

    suspend fun playAudioBytes(audioData: ByteArray, format: String = "mp3"): Boolean {
        return withContext(Dispatchers.IO) {
            metricsLogger.startTimer("audio_playback")
            try {
                val tempFile = File(context.cacheDir, "playback_${System.currentTimeMillis()}.$format")
                FileOutputStream(tempFile).use { it.write(audioData) }

                val deferred = CompletableDeferred<Boolean>()

                withContext(Dispatchers.Main) {
                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                .build()
                        )
                        setDataSource(tempFile.absolutePath)
                        setOnCompletionListener {
                            val duration = metricsLogger.stopTimer("audio_playback")
                            Log.d(TAG, "[PLAY] Playback complete (${duration}ms)")
                            tempFile.delete()
                            deferred.complete(true)
                        }
                        setOnErrorListener { _, what, extra ->
                            metricsLogger.stopTimer("audio_playback")
                            Log.e(TAG, "[PLAY] Error: what=$what extra=$extra")
                            tempFile.delete()
                            deferred.complete(false)
                            true
                        }
                        prepare()
                        start()
                    }
                    Log.i(TAG, "[PLAY] Playing audio (${audioData.size} bytes)")
                }

                deferred.await()
            } catch (e: Exception) {
                metricsLogger.stopTimer("audio_playback")
                Log.e(TAG, "[PLAY] Playback failed", e)
                false
            }
        }
    }

    fun stop() {
        tts?.stop()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        Log.d(TAG, "[PLAY] Stopped all audio")
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isReady = false
        Log.d(TAG, "[PLAY] Shutdown")
    }
}
