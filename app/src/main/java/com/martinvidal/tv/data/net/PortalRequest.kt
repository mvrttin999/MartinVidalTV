package com.martinvidal.tv.data.net

import com.martinvidal.tv.data.session.SettingsStore

/**
 * Construcción del cuerpo de petición.
 *
 * El portal no envía los campos planos: usa
 * `{"commonParams":{...},"specificParams":{...}}`, tal y como arma
 * `core.request.proxy.bean.CombinedParams`.
 *
 * `commonParams` describe el equipo y la app; `specificParams` lleva los
 * campos propios de cada endpoint. No hay usuario, token ni sesión: el
 * distribuidor gestiona el acceso fuera de la app.
 */
object PortalRequest {

    fun common(state: SettingsStore.Snapshot, device: DeviceProfile): Map<String, Any?> = linkedMapOf(
        "loginType" to device.loginType,
        "appLanguage" to device.appLanguage,
        "apkVersion" to device.appVersion,
        "sysVersion" to device.sysVersion,
        "appId" to device.appId,
        "hardwareInfo" to device.hardware,
        "model" to device.model,
        "product" to device.product,
        "cpu" to device.cpu,
        "b29" to state.b29.ifBlank { device.sn },
        "reserve1" to state.reserve1.ifBlank { device.reserve1 },
        "portalCode" to state.portalCode,
        "deviceToken" to state.deviceToken,
        "sn" to state.sn.ifBlank { device.sn },
        "sdkVer" to device.sdkVer
    )

    fun of(common: Map<String, Any?>, specific: Map<String, Any?>): Map<String, Any?> =
        linkedMapOf(
            "commonParams" to common,
            "specificParams" to specific
        )

    fun build(
        state: SettingsStore.Snapshot,
        device: DeviceProfile,
        specific: Map<String, Any?>
    ): Map<String, Any?> = of(common(state, device), specific)
}
