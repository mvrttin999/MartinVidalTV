package com.martinvidal.tv.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.martinvidal.tv.R
import com.martinvidal.tv.core.BaseActivity
import com.martinvidal.tv.databinding.ActivityHomeBinding
import com.martinvidal.tv.ui.detail.DetailActivity
import com.martinvidal.tv.ui.search.SearchActivity
import com.martinvidal.tv.ui.settings.PortalActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeActivity : BaseActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var shelvesAdapter: ShelvesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sections = listOf(
            Section("live", getString(R.string.home_live)),
            Section("movies", getString(R.string.home_movies)),
            Section("series", getString(R.string.home_series)),
            Section("kids", getString(R.string.home_kids)),
            Section("docs", getString(R.string.home_docs)),
            Section("search", getString(R.string.home_search)),
            Section("settings", getString(R.string.home_settings))
        )

        binding.rail.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        binding.rail.adapter = RailAdapter(sections) { section, _ -> onSection(section) }

        shelvesAdapter = ShelvesAdapter { item ->
            startActivity(
                DetailActivity.newIntent(this, item, viewModel.state.value.demo)
            )
        }
        binding.shelves.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        binding.shelves.adapter = shelvesAdapter

        viewModel.start(sections)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.loading.visibility = if (state.loading) View.VISIBLE else View.GONE
                    shelvesAdapter.submit(state.shelves)

                    val message = when {
                        state.error != null -> buildString {
                            append(state.error)
                            if (state.demo) append("\n").append(getString(R.string.home_demo_notice))
                        }
                        state.demo -> getString(R.string.home_demo_notice)
                        state.bundled -> getString(R.string.home_bundled_notice)
                        else -> null
                    }
                    binding.notice.visibility = if (message != null) View.VISIBLE else View.GONE
                    binding.notice.text = message
                    binding.user.text = if (state.demo) getString(R.string.home_demo_user) else ""
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Al volver de Ajustes, recarga la portada si cambió la fuente.
        viewModel.refreshIfSourceChanged()
    }

    private fun onSection(section: Section) {
        when (section.id) {
            "search" -> startActivity(Intent(this, SearchActivity::class.java))
            "settings" -> startActivity(Intent(this, PortalActivity::class.java))
            else -> scrollToSection(section)
        }
    }

    private fun scrollToSection(section: Section) {
        val shelves = viewModel.state.value.shelves
        if (shelves.isEmpty()) return
        val index = shelves.indexOfFirst { it.id == section.id }
            .takeIf { it >= 0 }
            ?: shelves.indexOfFirst { it.title.equals(section.title, ignoreCase = true) }
                .takeIf { it >= 0 }
            ?: return
        (binding.shelves.layoutManager as? LinearLayoutManager)
            ?.scrollToPositionWithOffset(index, 0)
    }
}
