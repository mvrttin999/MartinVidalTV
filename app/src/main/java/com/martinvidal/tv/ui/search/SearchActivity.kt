package com.martinvidal.tv.ui.search

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.martinvidal.tv.R
import com.martinvidal.tv.core.BaseActivity
import com.martinvidal.tv.databinding.ActivitySearchBinding
import com.martinvidal.tv.ui.detail.DetailActivity
import com.martinvidal.tv.ui.home.CardsAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchActivity : BaseActivity() {

    private lateinit var binding: ActivitySearchBinding
    private val viewModel: SearchViewModel by viewModels()
    private lateinit var adapter: CardsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = CardsAdapter { item ->
            startActivity(
                DetailActivity.newIntent(this, item, viewModel.state.value.demo)
            )
        }

        val layout = GridLayoutManager(this, 5)
        binding.results.layoutManager = layout
        binding.results.adapter = adapter
        binding.results.addItemDecoration(GridSpacing(10, 10))

        binding.action.setOnClickListener { viewModel.search(binding.query.text.toString()) }
        binding.query.setOnEditorActionListener { _, _, _ ->
            viewModel.search(binding.query.text.toString())
            true
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.loading.visibility = if (state.loading) View.VISIBLE else View.GONE
                    binding.notice.visibility = if (state.notice != null) View.VISIBLE else View.GONE
                    binding.notice.text = state.notice
                    binding.empty.visibility = when {
                        state.loading -> View.GONE
                        !state.searched -> View.VISIBLE
                        state.results.isEmpty() -> View.VISIBLE
                        else -> View.GONE
                    }
                    binding.empty.text = if (state.searched) {
                        getString(R.string.search_no_results)
                    } else {
                        getString(R.string.search_empty)
                    }
                    adapter.submit(state.results)
                }
            }
        }

        binding.query.requestFocus()
    }

    private class GridSpacing(
        private val horizontal: Int,
        private val vertical: Int
    ) : RecyclerView.ItemDecoration() {

        override fun getItemOffsets(
            outRect: android.graphics.Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            outRect.left = horizontal / 2
            outRect.right = horizontal / 2
            outRect.top = vertical / 2
            outRect.bottom = vertical / 2
        }
    }
}
