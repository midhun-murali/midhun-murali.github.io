package com.beauty.camera.selfie.camera.filter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class FilterAdapter(
    private val filters: List<Filter>,
    private val onFilterSelected: (Filter) -> Unit
) : RecyclerView.Adapter<FilterAdapter.FilterViewHolder>() {

    private var selectedPosition = 0

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

        // Map filter enum to drawable thumbnails (simple vector indicators)
        val thumbRes = when (filter) {
            Filter.SWEET -> R.drawable.ic_filter_sweet
            Filter.PASTEL -> R.drawable.ic_filter_pastel
            Filter.BLOOM -> R.drawable.ic_filter_bloom
            Filter.VINTAGE -> R.drawable.ic_filter_vintage
            Filter.MONO -> R.drawable.ic_filter_mono
            // use new thumb resources for new filters
            Filter.SEPIA -> R.drawable.ic_filter_sepia
            Filter.VIBRANT -> R.drawable.ic_filter_vibrant
            Filter.CINEMATIC -> R.drawable.ic_filter_cinematic
            Filter.COOL -> R.drawable.ic_filter_cool
            Filter.WARM -> R.drawable.ic_filter_warm
            Filter.FADE -> R.drawable.ic_filter_fade
            else -> R.drawable.ic_filter_sweet
        }
        holder.filterThumbnail.setImageDrawable(ContextCompat.getDrawable(holder.itemView.context, thumbRes))

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
