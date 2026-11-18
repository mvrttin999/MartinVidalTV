package com.martinvidal.tv.core

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import kotlin.math.max
import kotlin.math.min

/**
 * Escalado proporcional sobre la rejilla de diseño 960x540 que usa la app.
 * Las medidas de los layouts se escriben en "dp de diseño": 960 unidades de
 * ancho y 540 de alto equivalen al área útil de la pantalla.
 *
 * Se envuelve el Context de cada Activity antes de inflar la vista.
 *
 * La escala se calcula con el **menor** de los dos factores (ancho/960 y
 * alto/540), no solo con el ancho. Asi el contenido cabe entero en pantallas
 * que no son 16:9 (moviles 18:9, 19.5:9, 20:9 y tablets), donde escalar solo
 * por el ancho hacia que la parte inferior se saliera de la pantalla.
 */
object AutoLayout {

    const val DESIGN_WIDTH = 960f
    const val DESIGN_HEIGHT = 540f

    fun wrap(base: Context): Context {
        val metrics = base.resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()
        if (width <= 0f || height <= 0f) return base

        // Se trabaja con lado largo y corto para que la escala no dependa de si
        // el Context se creo antes o despues de aplicar la orientacion.
        val longSide = max(width, height)
        val shortSide = min(width, height)

        val scale = min(longSide / DESIGN_WIDTH, shortSide / DESIGN_HEIGHT)
        if (scale <= 0f) return base

        val config = Configuration(base.resources.configuration)
        config.densityDpi = (scale * DisplayMetrics.DENSITY_DEFAULT).toInt()

        val scaled = base.createConfigurationContext(config)
        // Se fija la densidad efectiva sin alterar la escala de fuente elegida
        // por el usuario, para que los textos no crezcan de forma desproporcionada.
        scaled.resources.displayMetrics.apply {
            density = scale
            scaledDensity = scale
            widthPixels = metrics.widthPixels
            heightPixels = metrics.heightPixels
        }
        return scaled
    }
}
