package com.beauty.camera.selfie.camera.filter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class RetouchAdapter(
    private val items: List<RetouchOption>,
    private val onOptionSelected: (RetouchOption) -> Unit
) : RecyclerView.Adapter<RetouchAdapter.VH>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val label: TextView = itemView.findViewById(R.id.filterName)
        val thumb: ImageView = itemView.findViewById(R.id.filterThumbnail)
        val container: View = itemView.findViewById(R.id.filterContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_filter, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val opt = items[position]
        holder.label.text = holder.itemView.context.getString(opt.nameResId)

        // Try to resolve a thumbnail drawable named `thumb_retouch_<option>` added by the designer.
        // Example: thumb_retouch_crop, thumb_retouch_exposure, thumb_retouch_saturation, etc.
        val drawableBase = opt.name.lowercase()
        val drawableNamePrimary = "$drawableBase"
        val ctx = holder.itemView.context
        val pkg = ctx.packageName
        var resId = ctx.resources.getIdentifier(drawableNamePrimary, "drawable", pkg)
        val thumbRes = if (resId != 0) resId else R.drawable.ic_retouch
        holder.thumb.setImageDrawable(ContextCompat.getDrawable(ctx, thumbRes))

        val isSelected = position == selectedPosition
        holder.container.background = if (isSelected) {
            ContextCompat.getDrawable(holder.itemView.context, R.drawable.filter_item_selected)
        } else {
            ContextCompat.getDrawable(holder.itemView.context, R.drawable.filter_item_background)
        }

        holder.label.setTextColor(ContextCompat.getColor(holder.itemView.context, if (isSelected) R.color.pink_selected else R.color.grey_dark))
        holder.label.textSize = if (isSelected) 16f else 14f
        holder.label.typeface = if (isSelected) {
            android.graphics.Typeface.create(holder.label.typeface, android.graphics.Typeface.BOLD)
        } else {
            android.graphics.Typeface.create(holder.label.typeface, android.graphics.Typeface.NORMAL)
        }

        holder.itemView.setOnClickListener {
            val prev = selectedPosition
            selectedPosition = holder.bindingAdapterPosition
            notifyItemChanged(prev)
            notifyItemChanged(selectedPosition)
            onOptionSelected(opt)
        }
    }

    override fun getItemCount() = items.size
}
