package com.beauty.camera.selfie.camera.filter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class FilterAdapter(
    private val filters: List<Filter>,
    private val onFilterSelected: (Filter) -> Unit
) : RecyclerView.Adapter<FilterAdapter.FilterViewHolder>() {

    private var selectedPosition = 0
    // Lazily computed thumbnail resource ids cached per filter
    private val thumbResByFilter: MutableMap<Filter, Int> = mutableMapOf()

    class FilterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val filterThumbnail: ImageView = itemView.findViewById(R.id.filterThumbnail)
        val filterName: TextView = itemView.findViewById(R.id.filterName)
        val filterContainer: View = itemView.findViewById(R.id.filterContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_filter, parent, false)
        return FilterViewHolder(view)
    }

    override fun onBindViewHolder(holder: FilterViewHolder, position: Int) {
        val filter = filters[position]
        holder.filterName.text = holder.itemView.context.getString(filter.nameResId)

        val ctx = holder.itemView.context

        // Look up cached id first
        var thumbResId = thumbResByFilter[filter] ?: 0
        if (thumbResId == 0) {
            // Try to load a drawable resource that matches the filter name (e.g., "pastel", "sepia").
            // If not present, fall back to prefixed ic_filter_* drawable names, and finally to a default.
            val baseName = filter.name.lowercase(Locale.US)
            thumbResId = ctx.resources.getIdentifier(baseName, "drawable", ctx.packageName)
            thumbResByFilter[filter] = thumbResId
        }

        holder.filterThumbnail.setImageDrawable(ContextCompat.getDrawable(holder.itemView.context, thumbResId))

        val isSelected = position == selectedPosition
        holder.filterContainer.background = if (isSelected) {
            ContextCompat.getDrawable(holder.itemView.context, R.drawable.filter_item_selected)
        } else {
            ContextCompat.getDrawable(holder.itemView.context, R.drawable.filter_item_background)
        }

        holder.filterName.setTextColor(
            ContextCompat.getColor(
                holder.itemView.context,
                if (isSelected) R.color.pink_selected else R.color.grey_dark
            )
        )

        holder.filterName.textSize = if (isSelected) 16f else 14f
        holder.filterName.typeface = if (isSelected) {
            android.graphics.Typeface.create(holder.filterName.typeface, android.graphics.Typeface.BOLD)
        } else {
            android.graphics.Typeface.create(holder.filterName.typeface, android.graphics.Typeface.NORMAL)
        }

        holder.itemView.setOnClickListener {
            val adapterPos = holder.bindingAdapterPosition
            if (adapterPos == RecyclerView.NO_POSITION) return@setOnClickListener
            val previousSelected = selectedPosition
            selectedPosition = adapterPos
            notifyItemChanged(previousSelected)
            notifyItemChanged(selectedPosition)
            onFilterSelected(filters[adapterPos])
        }
    }

    override fun getItemCount() = filters.size
}
