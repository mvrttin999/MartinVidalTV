package com.martinvidal.tv.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.martinvidal.tv.databinding.ItemCardBinding
import com.martinvidal.tv.databinding.ItemShelfBinding

data class CardItem(
    val id: String,
    val title: String,
    val poster: String? = null,
    /** `contentType`/`programType` del portal: movie, series, live... */
    val kind: String? = null,
    /** URL directa cuando la entrada viene de una lista M3U propia. */
    val url: String? = null,
    /** Grupo de la lista M3U (`group-title`). */
    val group: String? = null
)

data class Shelf(
    val title: String,
    val items: List<CardItem>,
    /** Identificador de seccion cuando el catalogo es local (`live`, `movies`...). */
    val id: String? = null
)

class ShelvesAdapter(
    private val onCardClick: (CardItem) -> Unit
) : RecyclerView.Adapter<ShelvesAdapter.ShelfHolder>() {

    private val shelves = mutableListOf<Shelf>()

    fun submit(items: List<Shelf>) {
        shelves.clear()
        shelves.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShelfHolder {
        val binding = ItemShelfBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.shelfItems.layoutManager =
            LinearLayoutManager(parent.context, LinearLayoutManager.HORIZONTAL, false)
        return ShelfHolder(binding)
    }

    override fun getItemCount(): Int = shelves.size

    override fun onBindViewHolder(holder: ShelfHolder, position: Int) {
        holder.bind(shelves[position])
    }

    inner class ShelfHolder(private val binding: ItemShelfBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(shelf: Shelf) {
            binding.shelfTitle.text = shelf.title
            val adapter = CardsAdapter(onCardClick)
            binding.shelfItems.adapter = adapter
            adapter.submit(shelf.items)
        }
    }
}

class CardsAdapter(
    private val onCardClick: (CardItem) -> Unit
) : RecyclerView.Adapter<CardsAdapter.CardHolder>() {

    private val items = mutableListOf<CardItem>()

    fun submit(newItems: List<CardItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardHolder =
        CardHolder(ItemCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: CardHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class CardHolder(private val binding: ItemCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CardItem) {
            binding.cardTitle.text = item.title
            binding.poster.load(item.poster) {
                crossfade(true)
            }
            binding.root.setOnClickListener { onCardClick(item) }
        }
    }
}
