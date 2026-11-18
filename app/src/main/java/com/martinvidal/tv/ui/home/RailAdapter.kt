package com.martinvidal.tv.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.martinvidal.tv.databinding.ItemRailBinding

data class Section(val id: String, val title: String)

class RailAdapter(
    private val sections: List<Section>,
    private val onSelected: (Section, Int) -> Unit
) : RecyclerView.Adapter<RailAdapter.Holder>() {

    private var selected = 0

    inner class Holder(val binding: ItemRailBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener { select(bindingAdapterPosition) }
            binding.root.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) select(bindingAdapterPosition)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemRailBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = sections.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.binding.title.text = sections[position].title
        holder.binding.title.isSelected = position == selected
    }

    private fun select(position: Int) {
        if (position == RecyclerView.NO_POSITION || position == selected) return
        val previous = selected
        selected = position
        notifyItemChanged(previous)
        notifyItemChanged(position)
        onSelected(sections[position], position)
    }
}
