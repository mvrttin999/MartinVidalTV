package com.martinvidal.tv.ui.player

import android.os.Bundle
import android.util.Log
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
import com.martinvidal.tv.data.net.RangerBridge
import com.martinvidal.tv.databinding.ActivityPlayerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import javax.inject.Inject

/**
 * Reproductor unico para directo y VOD.
 *
 * Los canales de la lista M3U llevan xup-license y se reproducen por el SDK
 * ranger: este genera el Content-Auth y entrega un play_url mem:// junto con
 * el handle de un buffer compartido, que se conecta al player ijk con
 * setSharedBuffer(). El resto de fuentes siguen usando Media3/ExoPlayer.
 */
@AndroidEntryPoint
class PlayerActivity : BaseActivity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_IS_LIVE = "is_live"
        const val EXTRA_MEDIA_CODE = "media_code"
        const val EXTRA_PROGRAM = "program"

        private const val TAG = "PlayerActivity"
        private const val MEDIA_UA =
            "Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }

    private lateinit var binding: ActivityPlayerBinding
    private var exoPlayer: ExoPlayer? = null
    private var ijkPlayer: IjkMediaPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Inject
    lateinit var httpClient: OkHttpClient

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.title.text = intent.getStringExtra(EXTRA_TITLE).orEmpty()

        val url = intent.getStringExtra(EXTRA_URL)
        val mediaCode = intent.getStringExtra(EXTRA_MEDIA_CODE)
        val program = intent.getStringExtra(EXTRA_PROGRAM)
        if (url.isNullOrBlank() && mediaCode.isNullOrBlank()) {
            finish()
            return
        }

        binding.close.setOnClickListener { finish() }
        binding.retry.setOnClickListener {
            hideError()
            if (mediaCode != null && program != null) prepareSdk(mediaCode, program)
            else url?.let { prepareExo(it) }
        }

        if (mediaCode != null && program != null) {
            binding.playerView.visibility = View.GONE
            prepareSdk(mediaCode, program)
        } else {
            url?.let { prepareExo(it) }
        }
    }

    /** Ruta del SDK ranger: prepare -> mem:// + sharedBuffer -> ijk. */
    private fun prepareSdk(mediaCode: String, program: String) {
        binding.loading.visibility = View.VISIBLE
        scope.launch {
            val stream = withContext(Dispatchers.IO) {
                if (RangerBridge.init(this@PlayerActivity)) {
                    RangerBridge.prepare(mediaCode, program)
                } else null
            }
            if (stream == null) {
                showError(getString(R.string.player_error_source))
                return@launch
            }
            startIjk(stream.playUrl, stream.sharedBuffer)
        }
    }

    private fun startIjk(playUrl: String, sharedBuffer: Long) {
        Log.i(TAG, "startIjk url=$playUrl buffer=$sharedBuffer")
        // Orden real de la app: ranger-jni se carga primero y las librerias ijk
        // despues, explicitamente, antes de crear el player.
        try {
            Log.i(TAG, "loadLibrariesOnce=${IjkMediaPlayer.loadLibrariesOnce(null)}")
        } catch (t: Throwable) {
            Log.e(TAG, "loadLibrariesOnce fallo: ${t.javaClass.name}: ${t.message}", t)
        }

        try {
            val mp = IjkMediaPlayer().apply {
                IjkMediaPlayer.native_setLogLevel(6)
                setOption(4, "mediacodec", 1L)
                setOption(4, "mediacodec-hevc", 1L)
                setOption(4, "overlay-format", "fcc-_es2")
                setOption(4, "opensles", 0L)
                setOption(1, "http-detect-range-support", 0L)
                setOption(2, "skip_loop_filter", 48L)
                setOption(4, "live-streaming", 1L)
                setOption(4, "delay-optimization", 1L)
                setOption(4, "start-on-prepared", 1L)
                setOption(4, "timeout", 20000L)
                setOption(4, "get-av-frame-timeout", 20000L)
                setOption(1, "flush_packets", 1L)
                setOption(4, "framedrop", 1L)
                setOption(4, "render-wait-start", 1L)
                setOption(1, "reconnect", 1L)
                setOption(1, "reconnect_delay_max", 5L)
                setOption(1, "http_multiple", 0L)
                setOption(4, "max-buffer-audio-tracks", 4L)
                setOption(4, "live_mode", 1L)
                setOption(4, "avio", 1L)
                setOption(4, "min-frames", 30L)
                setSharedBuffer(sharedBuffer)
                setOnPreparedListener {
                    Log.i(TAG, "IJK_PREPARED")
                    binding.loading.visibility = View.GONE
                    hideError()
                    start()
                    Log.i(TAG, "IJK_STARTED")
                }
                setOnInfoListener { _, what, _ ->
                    if (what == IjkMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                        Log.i(TAG, "IJK_RENDERING_START")
                        binding.loading.visibility = View.GONE
                    }
                    false
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "IJK_ERROR what=$what extra=$extra")
                    showError(getString(R.string.player_error, "$what/$extra"))
                    true
                }
            }
            ijkPlayer = mp
            Log.i(TAG, "IJK_CREATED sharedBuffer ok")
            mp.setDataSource(playUrl)
            RangerBridge.onDataSourceOpened(playUrl)
            Log.i(TAG, "IJK_SET_DATASOURCE + SDK notificado")
            mp.prepareAsync()
            RangerBridge.onPrepareAsync()
            Log.i(TAG, "IJK_PREPARE_ASYNC + SDK notificado")
        } catch (t: Throwable) {
            Log.e(TAG, "startIjk fallo: ${t.javaClass.name}: ${t.message}", t)
            showError(getString(R.string.player_error, t.message.orEmpty()))
        }
    }

    @UnstableApi
    private fun prepareExo(source: String) {
        binding.loading.visibility = View.VISIBLE
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(this)
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
                            showError(messageFor(error))
                        }
                    })
                    binding.playerView.player = exo
                }
        }
        val exo = exoPlayer ?: return
        exo.setMediaItem(MediaItem.fromUri(source))
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun hideError() {
        binding.errorPanel.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.loading.visibility = View.GONE
        binding.error.text = message
        binding.errorPanel.visibility = View.VISIBLE
        binding.retry.post { binding.retry.requestFocus() }
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
        exoPlayer?.pause()
        ijkPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        binding.playerView.player = null
        exoPlayer?.release()
        exoPlayer = null
        ijkPlayer?.release()
        ijkPlayer = null
    }
}
