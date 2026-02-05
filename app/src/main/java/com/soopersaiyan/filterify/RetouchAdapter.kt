package com.soopersaiyan.filterify

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_filter, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val opt = items[position]
        holder.label.text = holder.itemView.context.getString(opt.nameResId)
        val isSelected = position == selectedPosition
        holder.label.setTextColor(ContextCompat.getColor(holder.itemView.context, if (isSelected) R.color.pink_selected else R.color.grey_dark))
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
