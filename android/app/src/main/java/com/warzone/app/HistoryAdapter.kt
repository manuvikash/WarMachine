package com.warzone.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class HistoryEntry(
    val feature: FeatureManager.Feature,
    val timestamp: Long,
    val capturedImage: ByteArray?,
    val responseText: String,
    val ttsSummary: String = "",
    val rawJson: String = "",
    val isError: Boolean = false
) {
    val featureLabel: String
        get() = when (feature) {
            FeatureManager.Feature.FIRST_AID -> "FIRST AID"
            FeatureManager.Feature.HAZARD -> "HAZARD"
            FeatureManager.Feature.SURVIVAL -> "SURVIVAL"
        }

    val featureColor: Int
        get() = when (feature) {
            FeatureManager.Feature.FIRST_AID -> 0xFFC62828.toInt()
            FeatureManager.Feature.HAZARD -> 0xFFE65100.toInt()
            FeatureManager.Feature.SURVIVAL -> 0xFF1B5E20.toInt()
        }

    fun decodeBitmap(): Bitmap? {
        val bytes = capturedImage ?: return null
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    fun formatResponse(): String {
        if (isError) return responseText.ifEmpty { "Unknown error" }

        val sb = StringBuilder()

        val mainText = responseText.ifEmpty { "No response" }
        sb.appendLine(mainText)

        if (ttsSummary.isNotEmpty() && ttsSummary != mainText) {
            sb.appendLine()
            sb.appendLine("--- TTS Summary ---")
            sb.appendLine(ttsSummary)
        }

        return sb.toString().trimEnd()
    }

    fun formatSummary(): String {
        if (isError) return responseText.ifEmpty { "Error" }
        return responseText.ifEmpty { ttsSummary.ifEmpty { "No response" } }
    }
}

class HistoryAdapter(
    private val onItemClick: (HistoryEntry) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val items = mutableListOf<HistoryEntry>()

    fun addEntry(entry: HistoryEntry) {
        items.add(0, entry)
        notifyItemInserted(0)
    }

    fun getEntries(): List<HistoryEntry> = items.toList()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumbnail: ImageView = view.findViewById(R.id.ivHistoryThumb)
        val tvFeature: TextView = view.findViewById(R.id.tvHistoryFeature)
        val tvTime: TextView = view.findViewById(R.id.tvHistoryTime)
        val tvResponse: TextView = view.findViewById(R.id.tvHistoryResponse)
        val vFeatureStripe: View = view.findViewById(R.id.vFeatureStripe)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = items[position]

        holder.tvFeature.text = entry.featureLabel
        holder.tvFeature.setTextColor(entry.featureColor)
        holder.vFeatureStripe.setBackgroundColor(entry.featureColor)

        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        holder.tvTime.text = sdf.format(java.util.Date(entry.timestamp))

        val preview = entry.formatSummary().take(140).replace("\n", " ")
        holder.tvResponse.text = if (entry.isError) "ERROR: $preview" else preview
        if (entry.isError) {
            holder.tvResponse.setTextColor(0xFFFF5555.toInt())
        } else {
            holder.tvResponse.setTextColor(0xFFAAFFAA.toInt())
        }

        val bitmap = entry.decodeBitmap()
        if (bitmap != null) {
            holder.ivThumbnail.setImageBitmap(bitmap)
            holder.ivThumbnail.visibility = View.VISIBLE
        } else {
            holder.ivThumbnail.setImageResource(android.R.drawable.ic_menu_camera)
            holder.ivThumbnail.visibility = View.VISIBLE
        }

        holder.itemView.setOnClickListener { onItemClick(entry) }
    }

    override fun getItemCount() = items.size
}
