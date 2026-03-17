package com.warzone.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiClient(
    private val baseUrl: String,
    private val metrics: MetricsLogger
) {

    companion object {
        private const val TAG = "ApiClient"
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        })
        .build()

    data class AnalysisResponse(
        val success: Boolean,
        val response: String = "",
        val ttsSummary: String = "",
        val rawJson: String = "",
        val error: String? = null
    )

    // ─── First Aid ────────────────────────────────────────────────

    suspend fun analyzeFirstAid(
        imageJpeg: ByteArray?,
        audioWav: ByteArray?,
        context: String = ""
    ): AnalysisResponse {
        return postImage("first-aid", "FIRST_AID", imageJpeg)
    }

    // ─── Hazard Detection ─────────────────────────────────────────

    suspend fun analyzeHazard(
        imageJpeg: ByteArray?,
        audioWav: ByteArray?,
        context: String = ""
    ): AnalysisResponse {
        return postImage("hazard", "HAZARD", imageJpeg)
    }

    // ─── Survival Guide ───────────────────────────────────────────

    suspend fun analyzeSurvival(
        imageJpeg: ByteArray?,
        audioWav: ByteArray?,
        context: String = ""
    ): AnalysisResponse {
        return withContext(Dispatchers.IO) {
            metrics.startTimer("api_survival")
            Log.i(TAG, "[SURVIVAL] Sending request")

            try {
                val jsonBody = JSONObject().apply {
                    put("question", context.ifEmpty { "What survival tips can you give based on my current situation?" })
                }.toString()

                val request = Request.Builder()
                    .url("$baseUrl/survival/")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

                Log.d(TAG, "[SURVIVAL] POST $baseUrl/survival/")

                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    val duration = metrics.stopTimer("api_survival", mapOf(
                        "status_code" to resp.code,
                        "response_size" to body.length
                    ))
                    Log.i(TAG, "[SURVIVAL] Response: ${resp.code} in ${duration}ms")
                    Log.d(TAG, "[SURVIVAL] Body: ${body.take(500)}")

                    if (resp.isSuccessful) {
                        parseResponse(body)
                    } else {
                        AnalysisResponse(success = false, error = "HTTP ${resp.code}: $body", rawJson = body)
                    }
                }
            } catch (e: IOException) {
                metrics.stopTimer("api_survival")
                Log.e(TAG, "[SURVIVAL] Network error", e)
                AnalysisResponse(success = false, error = "Network error: ${e.message}")
            } catch (e: Exception) {
                metrics.stopTimer("api_survival")
                Log.e(TAG, "[SURVIVAL] Request failed", e)
                AnalysisResponse(success = false, error = "Error: ${e.message}")
            }
        }
    }

    // ─── Health Check ─────────────────────────────────────────────

    suspend fun healthCheck(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/")
                    .get()
                    .build()
                Log.d(TAG, "[HEALTH] Checking: $baseUrl/")
                client.newCall(request).execute().use {
                    Log.i(TAG, "[HEALTH] Status: ${it.code}")
                    it.isSuccessful
                }
            } catch (e: Exception) {
                Log.e(TAG, "[HEALTH] Check failed", e)
                false
            }
        }
    }

    // ─── Shared ───────────────────────────────────────────────────

    private suspend fun postImage(endpoint: String, tag: String, imageJpeg: ByteArray?): AnalysisResponse {
        return withContext(Dispatchers.IO) {
            metrics.startTimer("api_$endpoint")
            Log.i(TAG, "[$tag] Sending request - image: ${imageJpeg?.size ?: 0}B")

            try {
                val multipartBuilder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)

                imageJpeg?.let {
                    multipartBuilder.addFormDataPart(
                        "image", "capture.jpg",
                        it.toRequestBody("image/jpeg".toMediaType())
                    )
                }

                val request = Request.Builder()
                    .url("$baseUrl/$endpoint/")
                    .post(multipartBuilder.build())
                    .build()

                Log.d(TAG, "[$tag] POST $baseUrl/$endpoint/")

                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    val duration = metrics.stopTimer("api_$endpoint", mapOf(
                        "status_code" to resp.code,
                        "response_size" to body.length
                    ))
                    Log.i(TAG, "[$tag] Response: ${resp.code} in ${duration}ms")
                    Log.d(TAG, "[$tag] Body: ${body.take(500)}")

                    if (resp.isSuccessful) {
                        parseResponse(body)
                    } else {
                        AnalysisResponse(success = false, error = "HTTP ${resp.code}: $body", rawJson = body)
                    }
                }
            } catch (e: IOException) {
                metrics.stopTimer("api_$endpoint")
                Log.e(TAG, "[$tag] Network error", e)
                AnalysisResponse(success = false, error = "Network error: ${e.message}")
            } catch (e: Exception) {
                metrics.stopTimer("api_$endpoint")
                Log.e(TAG, "[$tag] Request failed", e)
                AnalysisResponse(success = false, error = "Error: ${e.message}")
            }
        }
    }

    private fun parseResponse(body: String): AnalysisResponse {
        return try {
            val json = JSONObject(body)
            val response = json.optString("response", "")
            val ttsSummary = json.optString("tts_summary", "")

            AnalysisResponse(
                success = true,
                response = response,
                ttsSummary = ttsSummary.ifEmpty { response },
                rawJson = body
            )
        } catch (e: Exception) {
            Log.e(TAG, "[PARSE] Failed to parse response", e)
            AnalysisResponse(success = true, response = body, ttsSummary = body, rawJson = body)
        }
    }
}
