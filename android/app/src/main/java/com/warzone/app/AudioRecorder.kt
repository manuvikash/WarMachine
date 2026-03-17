package com.warzone.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class AudioRecorder(
    private val context: Context,
    private val metrics: MetricsLogger
) {

    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isRecording = false

    suspend fun recordAudio(durationMs: Long): ByteArray? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "[RECORD] RECORD_AUDIO permission not granted")
            metrics.log("audio_record_permission_denied")
            return null
        }

        return withContext(Dispatchers.IO) {
            metrics.startTimer("audio_recording")
            try {
                val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                Log.d(TAG, "[RECORD] Buffer size: $bufferSize bytes")

                if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                    Log.e(TAG, "[RECORD] Invalid buffer size: $bufferSize")
                    return@withContext null
                }

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "[RECORD] AudioRecord failed to initialize")
                    return@withContext null
                }

                val outputStream = ByteArrayOutputStream()
                val buffer = ByteArray(bufferSize)

                val recordStartTime = System.currentTimeMillis()
                audioRecord?.startRecording()
                isRecording = true
                Log.i(TAG, "[RECORD] Recording started (target: ${durationMs}ms)")

                while (isRecording && (System.currentTimeMillis() - recordStartTime) < durationMs) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        outputStream.write(buffer, 0, read)
                    }
                }

                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
                isRecording = false

                val pcmData = outputStream.toByteArray()
                val actualDuration = System.currentTimeMillis() - recordStartTime

                metrics.stopTimer("audio_recording", mapOf(
                    "pcm_bytes" to pcmData.size,
                    "actual_duration_ms" to actualDuration
                ))
                Log.i(TAG, "[RECORD] Recorded ${pcmData.size} bytes in ${actualDuration}ms")

                pcmToWav(pcmData)

            } catch (e: Exception) {
                metrics.stopTimer("audio_recording")
                Log.e(TAG, "[RECORD] Recording failed", e)
                audioRecord?.release()
                audioRecord = null
                isRecording = false
                null
            }
        }
    }

    fun stopRecording() {
        isRecording = false
    }

    private fun pcmToWav(pcmData: ByteArray): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = SAMPLE_RATE * 1 * 16 / 8

        val header = ByteArray(44)

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()

        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0
        header[22] = 1; header[23] = 0

        header[24] = (SAMPLE_RATE and 0xff).toByte()
        header[25] = ((SAMPLE_RATE shr 8) and 0xff).toByte()
        header[26] = ((SAMPLE_RATE shr 16) and 0xff).toByte()
        header[27] = ((SAMPLE_RATE shr 24) and 0xff).toByte()

        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()

        header[32] = 2; header[33] = 0
        header[34] = 16; header[35] = 0

        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        header[40] = (pcmData.size and 0xff).toByte()
        header[41] = ((pcmData.size shr 8) and 0xff).toByte()
        header[42] = ((pcmData.size shr 16) and 0xff).toByte()
        header[43] = ((pcmData.size shr 24) and 0xff).toByte()

        return header + pcmData
    }
}
