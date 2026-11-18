package com.martinvidal.tv.ui.player

import android.os.Bundle
import android.view.View
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.martinvidal.tv.R
import com.martinvidal.tv.core.BaseActivity
import com.martinvidal.tv.databinding.ActivityPlayerBinding
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * Reproductor unico para directo y VOD (Media3/ExoPlayer, HLS y DASH).
 *
 * La URL de reproduccion la entrega el portal en startPlayLive / startPlayVOD
 * (con token de sesion y, en algunos casos, seleccion de SLB) o la propia
 * lista M3U.
 */
@AndroidEntryPoint
class PlayerActivity : BaseActivity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_IS_LIVE = "is_live"

        /**
         * Muchos CDN de television rechazan el User-Agent por defecto de
         * OkHttp; se anuncia un navegador de Android.
         */
        private const val MEDIA_UA =
            "Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var url: String? = null

    @Inject
    lateinit var httpClient: OkHttpClient

    // PENDIENTE(P14): selector de audio/subtitulos, distintivo de directo y
    // soporte de RTMP/BTV. Ahora solo reproduce HLS/MP4 por Media3.
    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.title.text = intent.getStringExtra(EXTRA_TITLE).orEmpty()

        url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            finish()
            return
        }

        binding.close.setOnClickListener { finish() }
        binding.retry.setOnClickListener {
            hideError()
            prepare()
        }

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    OkHttpDataSource.Factory(httpClient).setUserAgent(MEDIA_UA)
                )
            )
            .build()
            .also { exo ->
                exo.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        binding.loading.visibility =
                            if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                        if (state == Player.STATE_READY) hideError()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        binding.loading.visibility = View.GONE
                        binding.error.text = messageFor(error)
                        binding.errorPanel.visibility = View.VISIBLE
                        binding.retry.post { binding.retry.requestFocus() }
                    }
                })
                binding.playerView.player = exo
            }
        prepare()
    }

    @UnstableApi
    private fun prepare() {
        val exo = player ?: return
        val source = url ?: return
        binding.loading.visibility = View.VISIBLE
        exo.setMediaItem(MediaItem.fromUri(source))
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun hideError() {
        binding.errorPanel.visibility = View.GONE
    }

    /** Traduce el codigo de Media3 a algo legible en la pantalla. */
    private fun messageFor(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> getString(R.string.player_error_source)
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> getString(R.string.player_error_network)
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> getString(R.string.player_error_format)
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> getString(R.string.player_error_codec)
        else -> getString(R.string.player_error, error.errorCodeName)
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.playerView.player = null
        player?.release()
        player = null
    }
}
