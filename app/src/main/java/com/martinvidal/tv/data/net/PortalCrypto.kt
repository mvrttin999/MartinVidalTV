package com.martinvidal.tv.data.net

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.DESedeKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifrado del portal (reconstruido del comportamiento del APK original).
 *
 * - Clave de trabajo: la cadena que el portal deja en `jb.b.a` tras descifrar
 *   la constante de `p6.a.j()`. Se usa como clave DESede tomando los primeros
 *   24 bytes de su forma base64 (los guiones se ignoran).
 * - Cifrado: `hex(base64(DESede/ECB/PKCS5))`; el hex va por caracter, sin
 *   relleno a cero (igual que `jb.a.c`).
 * - Descifrado: el proceso inverso.
 */
object PortalCrypto {

    /** `jb.b.a` en tiempo de ejecucion. */
    const val KEY = "213f24ac-14b7-4c03-a209-383ead1b014f"

    private const val ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private val decodeTable = IntArray(256) { -1 }.also { table ->
        ALPHABET.forEachIndexed { index, c -> table[c.code] = index }
    }

    fun encrypt(plain: String, keyText: String = KEY): String {
        val cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, desKey(keyText))
        val encoded = Base64.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return toHex(encoded)
    }

    fun decrypt(encoded: String, keyText: String = KEY): String? = runCatching {
        val cipherText = decodeBase64(fromHexToAscii(encoded))
        val cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, desKey(keyText))
        String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }.getOrNull()

    /**
     * Decodifica base64 ignorando los caracteres que no pertenecen al alfabeto
     * (el APK usa variantes con `*&@!`, `-`, `^` y `%`). Es la lectura estable
     * de claves tipo UUID.
     */
    fun decodeBase64(text: String): ByteArray {
        val clean = StringBuilder(text.length)
        for (c in text) {
            when {
                c == '=' -> clean.append(c)
                c.code in 0..255 && decodeTable[c.code] >= 0 -> clean.append(c)
            }
        }
        while (clean.length % 4 != 0) clean.append('=')
        return Base64.decode(clean.toString(), Base64.DEFAULT)
    }

    /**
     * Replica de `hb.a.e()`: cada grupo de 4 caracteres se decodifica aunque
     * contenga caracteres invalidos (el original los convierte en -1).
     * Se conserva para leer recursos antiguos que dependan de ese detalle.
     */
    fun decodeBase64Compat(text: String): ByteArray {
        val clean = text.replace("\r", "").replace("\n", "")
        val out = ArrayList<Byte>(clean.length * 3 / 4)
        var i = 0
        while (i + 4 <= clean.length) {
            var a = -1
            var b = -1
            var c = -1
            var d = -1
            var j = 0
            while (j < 4) {
                val code = clean[i + j].code
                val value = if (code in 0..255) decodeTable[code] else -1
                when (j) {
                    0 -> a = value
                    1 -> b = value
                    2 -> c = value
                    else -> d = value
                }
                j++
            }
            out.add((((a shl 2) and 0xFC) or ((b ushr 4) and 0x03)).toByte())
            out.add((((b shl 4) and 0xF0) or ((c ushr 2) and 0x0F)).toByte())
            out.add((((c shl 6) and 0xC0) or (d and 0x3F)).toByte())
            i += 4
        }
        return out.toByteArray()
    }

    fun toHex(text: String): String = buildString(text.length * 2) {
        for (c in text) append(c.code.toString(16))
    }

    /** `jb.a.b`: hex -> bytes -> String (los bytes son ASCII en la practica). */
    private fun fromHexToAscii(hex: String): String {
        require(hex.length % 2 == 0) { "hex incompleto" }
        val bytes = ByteArray(hex.length / 2)
        var i = 0
        while (i < hex.length) {
            bytes[i / 2] = ((hexValue(hex[i]) shl 4) or hexValue(hex[i + 1])).toByte()
            i += 2
        }
        return String(bytes, Charsets.ISO_8859_1)
    }

    private fun hexValue(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> error("hex invalido: $c")
    }

    private fun desKey(keyText: String): SecretKeySpec {
        val raw = decodeBase64(keyText)
        require(raw.size >= 24) { "clave DESede invalida (${raw.size} bytes)" }
        val spec = DESedeKeySpec(raw)
        return SecretKeySpec(spec.key, "DESede")
    }

    fun md5(text: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}
