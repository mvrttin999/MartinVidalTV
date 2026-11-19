package com.martinvidal.tv.ui.detail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.martinvidal.tv.R
import com.martinvidal.tv.core.BaseActivity
import com.martinvidal.tv.databinding.ActivityDetailBinding
import com.martinvidal.tv.ui.home.CardItem
import com.martinvidal.tv.ui.player.PlayerActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Ficha de contenido. Con sesion real pide los datos al portal; si el portal
 * no responde, muestra la ficha de ejemplo para que la navegacion no se rompa.
 */
@AndroidEntryPoint
class DetailActivity : BaseActivity() {

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_POSTER = "poster"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_DEMO = "demo"
        private const val EXTRA_URL = "url"
        private const val EXTRA_GROUP = "group"

        fun newIntent(context: Context, item: CardItem, demo: Boolean): Intent =
            Intent(context, DetailActivity::class.java)
                .putExtra(EXTRA_ID, item.id)
                .putExtra(EXTRA_TITLE, item.title)
                .putExtra(EXTRA_POSTER, item.poster)
                .putExtra(EXTRA_KIND, item.kind)
                .putExtra(EXTRA_DEMO, demo)
                .putExtra(EXTRA_URL, item.url)
                .putExtra(EXTRA_GROUP, item.group)
    }

    private lateinit var binding: ActivityDetailBinding
    private val viewModel: DetailViewModel by viewModels()

    private val item: CardItem
        get() = CardItem(
            id = intent.getStringExtra(EXTRA_ID).orEmpty(),
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            poster = intent.getStringExtra(EXTRA_POSTER),
            kind = intent.getStringExtra(EXTRA_KIND),
            url = intent.getStringExtra(EXTRA_URL),
            group = intent.getStringExtra(EXTRA_GROUP)
        )

    // PENDIENTE(P17): pedir PIN de control parental antes de reproducir
    // contenido marcado +16/+18, si el distribuidor lo exige.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.back.setOnClickListener { finish() }
        binding.play.setOnClickListener { viewModel.play(item) }

        viewModel.load(item, intent.getBooleanExtra(EXTRA_DEMO, false))

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.loading.visibility = if (state.loading) View.VISIBLE else View.GONE
                    binding.title.text = state.title
                    binding.meta.text = state.meta
                    binding.synopsis.text = state.synopsis
                    binding.poster.load(state.poster) { crossfade(true) }

                    binding.notice.visibility =
                        if (state.notice != null || state.playError != null) View.VISIBLE else View.GONE
                    binding.notice.text = state.playError ?: state.notice

                    binding.play.isEnabled = !state.playLoading
                    binding.play.text = if (state.playLoading) {
                        getString(R.string.detail_play_loading)
                    } else {
                        getString(R.string.detail_play)
                    }

                    state.playback?.let { playback ->
                        viewModel.consumePlayback()
                        startActivity(
                            Intent(this@DetailActivity, PlayerActivity::class.java)
                                .putExtra(PlayerActivity.EXTRA_URL, playback.url)
                                .putExtra(PlayerActivity.EXTRA_TITLE, state.title)
                                .putExtra(PlayerActivity.EXTRA_IS_LIVE, playback.isLive)
                                .putExtra(PlayerActivity.EXTRA_MEDIA_CODE, playback.mediaCode)
                                .putExtra(PlayerActivity.EXTRA_PROGRAM, playback.programJson)
                        )
                    }
                }
            }
        }

        binding.play.requestFocus()
    }
}
