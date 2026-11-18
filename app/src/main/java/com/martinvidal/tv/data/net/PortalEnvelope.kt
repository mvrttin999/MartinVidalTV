package com.martinvidal.tv.data.net

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * Sobre de respuesta del portal:
 * `{"returnCode":"0","errorMessage":"","data":...}`.
 *
 * `data` puede llegar como objeto/lista o como cadena cifrada
 * (`hex(base64(3DES))`), por eso se modela como `Any?` y se normaliza en
 * [dataObject].
 */
@JsonClass(generateAdapter = false)
data class PortalEnvelope(
    @Json(name = "returnCode") val returnCode: String? = null,
    @Json(name = "errorMessage") val errorMessage: String? = null,
    @Json(name = "data") val data: Any? = null
) {

    val isSuccess: Boolean get() = returnCode == null || returnCode == "0"

    /** Mensaje de error listo para mostrar; `null` si la respuesta es correcta. */
    fun problem(): String? = when {
        isSuccess -> null
        returnCode == "portal200001" ->
            "El portal ha retirado esta versión del servicio (portal200001). Contacta con tu distribuidor."
        returnCode == "portal400002" ->
            "El portal ha rechazado la petición (portal400002). Contacta con tu distribuidor."
        else -> errorMessage?.takeIf { it.isNotBlank() } ?: returnCode
    }

    /** `data` descifrado y convertido a mapa cuando corresponde. */
    fun dataObject(moshi: Moshi): Map<String, Any?>? {
        when (val raw = data) {
            null -> return null
            is Map<*, *> -> return raw.entries
                .associate { it.key.toString() to it.value }
            is String -> {
                val json = PortalCrypto.decrypt(raw) ?: raw
                return runCatching {
                    moshi.adapter<Map<String, Any?>>(mapType).fromJson(json)
                }.getOrNull()
            }
            else -> return null
        }
    }

    private companion object {
        val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    }
}
