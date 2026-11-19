package com.martinvidal.tv.data.net

import android.content.Context
import android.util.Log
import com.titan.ranger.JniHandler
import com.titan.ranger.NativeJni
import com.titan.ranger.Status
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap

/** Stream local servido por el SDK: URL mem:// y handle del buffer compartido. */
data class RangerStream(val playUrl: String, val sharedBuffer: Long)

/**
 * Puente al SDK nativo ranger-jni de Xuper 4.35.0.
 *
 * El propio SDK genera el Content-Auth y sirve el stream por un proxy local
 * mem://127.0.0.1:<puerto>. Java/Kotlin solo entrega la license del canal y
 * usa el play_url y el media_buffer que llegan en OnPrepareEvent.
 *
 * Secuencia real (verificada contra la app 4.35.0 con Frida):
 *   Init(work_path) -> OnSystemEvent(net,wlan,start) -> SetEnv(env+titan_port)
 *   -> SetEntries -> SetPlayer(0/1,ijk,-1) -> PrepareProgram(media_code,live)
 *   -> OnPrepareEvent(media_prepared) -> [ijk setSharedBuffer + setDataSource(mem://)]
 *   -> OnPlayerOperation(setDataSource / prepare(Async))
 */
object RangerBridge {
    private const val TAG = "RangerBridge"
    private const val INSTANCE = 0
    private const val PREPARE_TIMEOUT_MS = 25_000L

    @Volatile
    private var native: NativeJni? = null
    @Volatile
    private var initialized = false
    private var playInfo: PlayInfoServer? = null

    private val pending = ConcurrentHashMap<String, CompletableDeferred<RangerStream>>()

    /** Servidor minimo /playinfo que el SDK consulta por titan_port. */
    private class PlayInfoServer {
        private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val port: Int = server.localPort
        private val thread = Thread {
            while (!server.isClosed) {
                try {
                    val socket = server.accept()
                    Thread {
                        try {
                            val reader = socket.getInputStream().bufferedReader()
                            val line = reader.readLine().orEmpty()
                            while (reader.readLine()?.isNotEmpty() == true) { }
                            val inst = Regex("instance=([0-9]+)").find(line)?.groupValues?.get(1) ?: "0"
                            val body = JSONObject().apply {
                                put("a", 200); put("b", "none"); put("c", "Request successfully:$inst")
                            }.toString().toByteArray(Charsets.UTF_8)
                            val head = ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n")
                                .toByteArray(Charsets.UTF_8)
                            socket.getOutputStream().write(head)
                            socket.getOutputStream().write(body)
                            socket.getOutputStream().flush()
                        } catch (e: Exception) {
                            Log.d(TAG, "playinfo: ${e.message}")
                        } finally {
                            runCatching { socket.close() }
                        }
                    }.start()
                } catch (e: Exception) { break }
            }
        }.apply { isDaemon = true }

        fun start() { thread.start() }
        fun stop() { runCatching { server.close() } }
    }

    /** Carga la libreria y ejecuta la secuencia de arranque del SDK. */
    @Synchronized
    fun init(context: Context): Boolean {
        if (initialized) return true
        return try {
            // El callback se registra ANTES de cargar el singleton nativo.
            playInfo = PlayInfoServer().also { it.start() }
            installCallback()

            val n = NativeJni.c()
            native = n
            val wp = File(context.filesDir, "ranger").apply { mkdirs() }.absolutePath
            n.f(wp)                       // Init
            n.m("net", "wlan", "start")   // OnSystemEvent
            Thread.sleep(1500)

            val env = JSONObject(readAsset(context, "ranger/env.json")).apply {
                put("titan_port", playInfo!!.port)
            }.toString()
            n.t(env)                                     // SetEnv
            n.r(readAsset(context, "ranger/entries.json")) // SetEntries
            n.g(0, "ijk", -1)                            // SetPlayer instancia 0
            n.g(1, "ijk", -1)                            // SetPlayer instancia 1

            initialized = true
            Log.i(TAG, "SDK inicializado; work_path=$wp, titan_port=${playInfo!!.port}")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "init falló: ${t.javaClass.name}: ${t.message}", t)
            false
        }
    }

    private fun installCallback() {
        JniHandler.d(INSTANCE, object : JniHandler.a {
            override fun a(i: Int, str: String?, str2: String?, str3: String?): Int = 0
            override fun b(): Int = INSTANCE
            override fun c(str: String?, str2: String?, i: Int): Int = 0
            override fun d(i: Int, str: String?, obj: Any?, j: Long): Int {
                if (i == INSTANCE && obj is Status) {
                    when (str) {
                        "media_prepared" -> {
                            val url = obj.play_url.orEmpty()
                            if (url.startsWith("mem://")) {
                                val code = obj.media.orEmpty()
                                Log.i(TAG, "media_prepared code=$code url=$url buffer=${obj.media_buffer}")
                                pending.remove(code)?.complete(RangerStream(url, obj.media_buffer))
                                // Tambien completar cualquier espera anonima (un solo canal activo).
                                if (pending.isNotEmpty()) {
                                    val stream = RangerStream(url, obj.media_buffer)
                                    pending.keys().toList().forEach { pending.remove(it)?.complete(stream) }
                                }
                            }
                        }
                        "media_prepare_error" -> Log.w(TAG, "media_prepare_error err=$j")
                    }
                }
                return 0
            }
            override fun t(str: String?, str2: String?, j: Long): Int = 0
        })
    }

    /**
     * Prepara el canal y devuelve el stream local cuando el SDK termina.
     * @param mediaCode codigo `cyx_...` del canal.
     * @param programJson Program completo con la license del canal.
     */
    suspend fun prepare(mediaCode: String, programJson: String): RangerStream? {
        val n = native ?: return null
        val deferred = CompletableDeferred<RangerStream>()
        pending[mediaCode] = deferred
        return try {
            n.o(INSTANCE, mediaCode, programJson, "live")  // PrepareProgram
            withTimeoutOrNull(PREPARE_TIMEOUT_MS) { deferred.await() }.also {
                if (it == null) pending.remove(mediaCode)
            }
        } catch (t: Throwable) {
            pending.remove(mediaCode)
            Log.e(TAG, "prepare falló: ${t.javaClass.name}: ${t.message}", t)
            null
        }
    }

    /** Notifica al SDK que el reproductor abrio el mem:// (llenar el buffer). */
    fun onDataSourceOpened(playUrl: String) {
        native?.l(INSTANCE, "setDataSource", playUrl, 0)
    }

    /** Notifica al SDK que el reproductor llamo a prepareAsync. */
    fun onPrepareAsync() {
        native?.l(INSTANCE, "prepare(Async)", "", 0)
    }

    private fun readAsset(context: Context, path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
