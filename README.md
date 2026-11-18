# Martin Vidal TV

App nueva (código propio) para **Android TV y Android normal** (móvil, tablet,
TV box). Habla con el backend `portalCore` del portal del distribuidor y, si no
hay portal, con una **lista M3U/M3U8 propia**. No contiene código del APK
original: se reconstruye la funcionalidad con librerías estándar.

## Alcance

- **Sin pagos ni suscripciones.** No hay pasarela, planes, VIP, códigos de
  canje ni renovaciones dentro de la app. El acceso lo gestiona el
  distribuidor por fuera (host y `portalCode`).
- Sin código del APK original: solo se reimplementa la funcionalidad con
  librerías estándar (Media3, AndroidX, Retrofit/OkHttp, Hilt).
- **Sin cuenta, sesión ni activación**: no hay usuario, token, código de
  activación ni cierre de sesión. La app elige la fuente y arranca directo.
- **Dos fuentes de contenido**: portal del distribuidor o lista M3U propia.

## Estado

El avance detallado, los pendientes con identificador y los bloqueos externos
están en [`PENDIENTES.md`](PENDIENTES.md).

| Módulo | Estado |
|---|---|
| Esqueleto Gradle + Hilt + diseño 960×540 escalable | hecho |
| Contrato de red reconstruido (campos, cabeceras, 3DES, respuesta) | hecho |
| Red (Retrofit/OkHttp/Moshi) + 82 rutas `portalCore` | hecho |
| Ajustes e identidad de equipo (DataStore) | hecho |
| Arranque directo a la portada (sin cuenta ni sesión) | hecho |
| Portada, ficha, búsqueda y reproductor Media3 | hecho |
| Ajustes (portal, lista M3U y actualizaciones) | hecho |
| Compatible con Android normal y Android TV (LAUNCHER + LEANBACK) | hecho |
| Lista M3U propia: portada, búsqueda y reproducción directa | hecho |
| Lista de canales incluida (42 canales públicos) cuando no hay lista | hecho |
| Reproductor con reintento y errores legibles en español | hecho |
| Auto-actualización: manifiesto, descarga y verificación SHA-256 | hecho |
| Firma release propia + R8 | hecho |
| Contenido de ejemplo navegable sin portal | hecho |
| Herramientas de captura de tráfico y análisis | hecho |
| EPG, favoritos/historial y control parental | pendiente |
| Pagos / suscripción | **fuera de alcance** |

## Requisitos

- JDK 21 (Gradle 8.9 no soporta JDK 27; ver `org.gradle.java.home`)
- Android SDK (platform 35, build-tools 34)
- `keystore.properties` + `martinvidal.jks` en la raíz para el build release

## Compilar

```bash
export JAVA_HOME=/home/mvrttin999/.jdks/temurin-21
export ANDROID_SDK_ROOT=/ruta/a/android-sdk
./gradlew assembleDebug
./gradlew assembleRelease   # requiere keystore.properties
```

## Crear la firma propia

```bash
keytool -genkeypair -v -keystore martinvidal.jks -alias martinvidal \
  -keyalg RSA -keysize 2048 -validity 10000
```

Después copia `keystore.properties.example` a `keystore.properties` y rellena
las contraseñas.

## Estructura

```
app/src/main/java/com/martinvidal/tv/
├── core/            AutoLayout (rejilla 1920×1080), BaseActivity
├── data/net/        PortalApi (82 endpoints), PortalInterceptor
├── data/session/    SettingsStore (host, portalCode, appId, dispositivo, fuentes)
├── data/repo/       CatalogRepository, PlaylistRepository, PlaybackRepository,
│                    UpdateRepository, UpdateScheduler, UpdateNotifier
├── di/              NetworkModule (Hilt)
└── ui/              welcome, home, player, settings, update
```

## Escalado de diseño

La app original usa `com.zhy.autolayout.AutoFrameLayout` sobre una rejilla de
1920×1080 px. Aquí `AutoLayout.wrap()` ajusta la densidad para que 1920dp de
diseño equivalgan al ancho real de pantalla, de modo que los layouts se escriben
con las mismas proporciones y se adaptan a 720p/1080p/4K.

## Pendiente crítico

El contrato ya está reconstruido y validado contra los hosts reales
(`portal_main`/`portal_backup`):

- Cabeceras: `apk` = appId, `apkVer` = versión, `spkgVer` = sysVersion, más
  `X-Request-Trace`/`X-Client-Meta` (MD5 de traza + clave).
- Cuerpo: JSON con `loginType, appLanguage, apkVersion, sysVersion, appId,
  hardwareInfo, model, product, cpu, b29, reserve1, portalCode, deviceToken,
  sn, sdkVer`.
- Cifrado de recursos/respuestas: `hex(base64(3DES/ECB/PKCS5))` con clave
  `213f24ac-14b7-4c03-a209-383ead1b014f`.

Sin embargo, **el portal responde `portal200001` ("versión detenida") a todos
los endpoints de negocio** de esta versión (4.99.20). El propio servicio de
actualización indica que esta versión dejará de recibir soporte. Mientras no
haya un portal vivo o un backend propio, la app obtiene el catálogo de la
lista M3U incluida o de la que configures en Ajustes. Ver `docs/pantallas.md`
y el estado del proyecto.

## Capturar el contrato real

Con el portal bloqueado, el siguiente paso es obtener el tráfico de una
instalación que sí funcione. Ver [docs/captura-trafico.md](docs/captura-trafico.md).

```bash
./tools/extraer-sesion.sh          # copia host/portalCode del APK original
./tools/capturar-trafico.sh inicio # o intercepta con mitmproxy
./tools/capturar-trafico.sh fin
```

## Fuente M3U (sin portal)

En **Ajustes** hay un campo "Lista M3U propia". Si se rellena, la app:

- descarga la lista al arrancar (cabecera `User-Agent` de VLC),
- agrupa las entradas por `group-title` en estanterías de la portada,
- busca por nombre o grupo,
- reproduce directamente la URL de la entrada (`http(s)`, `rtmp`, `rtsp` o
  `udp`), sin pasar por el portal ni por el interceptor.

La URL también se puede **fijar al compilar** para no tener que pegarla nunca:

```
./gradlew assembleRelease -PplaylistUrl=https://ejemplo.com/lista.m3u
```

Esa URL queda como valor por defecto (`BuildConfig.PLAYLIST_URL`). Si no hay
red o la fuente cae, la app usa la última copia buena guardada en
`filesDir/playlist-cache.m3u`; si no hay copia, cae a la lista incluida. El APK
entregado ya lleva fijada la lista pública de Chile de `iptv-org`, así que la
portada se llena sola. Para cambiarla sin recompilar, basta con pegar otra URL
en Ajustes.

Formato admitido: `#EXTM3U` / `#EXTINF` con `tvg-name`, `tvg-logo` y
`group-title`; también `#EXTGRP` y listas sin atributos. Probado con una lista
pública real (portada, búsqueda y reproducción HLS con segmentos `.ts`).

### Lista incluida: catálogo LIVE de Xuper

Si no hay lista configurada, la app usa `app/src/main/assets/lista.m3u` con
**677 canales en directo** extraídos del catálogo de Xuper v4.35.0 (auditado en
emulador; ver P31). Cada entrada apunta a la URL real:

```
http://64.31.43.179:33984/live/<media_code>.m3u8?xup-license=<license>
```

y lleva su `Content-License` como parámetro `xup-license`. El
`XuperAuthInterceptor` lo convierte en cabecera y añade las demás cabeceras que
exige el servidor (`Content-Auth`, `App`, `App-Version`, `Ranger-Id`,
`User-Agent`, `X-Buffer`), tanto para el manifiesto como para los segmentos
`.ts`.

**Importante — validez de las cabeceras.** El `Content-Auth` de Xuper es de un
solo uso y caduca en minutos: el incluido en `assets/xuper_auth.txt` ya no
sirve. Para que los directos abren, pega en **Ajustes → Content-Auth de los
directos** un valor capturado recién de la app original. Si se deja el campo
vacío se usa el incluido (caducado). La app sigue sin cuenta, sesión ni
activación propias: pegar una cabecera no inicia sesión en MartinVidalTV.

Verificado en emulador (2026-11-18): con un `Content-Auth` fresco pegado en
Ajustes, `La Granja VIP 1 HD` reprodujo con el manifiesto y los segmentos
devueltos como `200 OK` (sin `409`) y audio/vídeo activos. Sin esa cabecera
vigente, el servidor responde `409`/`401`/`410` y la ficha muestra "El canal no
responde ahora mismo", aunque las demás cabeceras estén correctas.

Los 172 canales sin `Content-License` en el catálogo no pueden abrir; el resto
sí con una cabecera vigente. Ver detalle y límites verificados en P31.

## Auto-actualización

La app puede buscar versiones nuevas **por sí sola**, descargar el APK y
verificarlo antes de ofrecerlo. Se configura en **Ajustes → Actualizaciones**
o en **Ajustes → URL del manifiesto de actualizaciones**.
También se puede fijar al compilar:

```bash
./gradlew assembleRelease -PupdateUrl=https://ejemplo.com/update.json
```

En la entrega publicada la URL ya va fijada en el APK:

```text
https://raw.githubusercontent.com/mvrttin999/MartinVidalTV/main/update.json
```

El `update.json` de la raíz del repo apunta al APK
`MartinVidalTV-1.1.0.apk` publicado en el mismo repo; la app compara
`versionCode`, descarga por HTTPS y valida el SHA-256 antes de ofrecer la
instalación. El repositorio es público para que `raw.githubusercontent.com`
sirva ambos archivos sin autenticación.

### Formato del manifiesto

El distribuidor publica un JSON en esa URL:

```json
{
  "versionCode": 2,
  "versionName": "1.0.1",
  "apkUrl": "https://ejemplo.com/MartinVidalTV-1.0.1.apk",
  "sha256": "a519a52536eb98596083c9b78cd4c923fbaad8185b54ec9ca139b77dbd8b8853",
  "notes": "Novedades de esta versión"
}
```

- `versionCode` debe ser mayor que el de la app instalada; si no, no se descarga.
- `sha256` son 64 hexadecimales del APK publicado. La descarga se escribe en
  `filesDir/updates/app-update.apk` solo si coincide byte a byte; si no, se
  descarta.
- En **release solo se aceptan URLs `https`**. En debug se permite `http` en
  `localhost` / `127.0.0.1` / `10.0.2.2` para probar con un servidor local.
- WorkManager comprueba cada 6 horas (con 3 de margen) mientras haya red;
  "Buscar ahora" fuerza una comprobación inmediata. Si la versión ya está en
  disco y verificada, solo reaparece el aviso.
- Al terminar se muestra una notificación y el botón **Instalar** de la
  pantalla de actualizaciones.

Android **no permite instalar sin intervención del usuario** en un equipo no
rooteado: el sistema pide confirmación (y, la primera vez, activar "Instalar
apps desconocidas"). Lo que se automatiza por completo es la detección, la
descarga, la verificación y el salto al instalador; el último toque lo pone
Android, no la app.

Verificado en emulador: descarga de un APK de 3,3 MB con SHA coincidente,
rechazo de un manifiesto con SHA falso y rechazo de HTTP en la build release.

## Bloqueo del portal (verificado)

La peticion de acceso al portal del APK original se construye con el contrato
recuperado (`commonParams` + `specificParams`, cabeceras
`apk`/`apkVer`/`spkgVer`, `X-Request-Trace`/`X-Client-Meta`) y llega al portal
real:

```
POST https://fuxok.nguvmqhpk.com/api/portalCore/v8/login  ->  HTTP 200
{"returnCode":"portal200001","errorMessage":"版本已停止使用"}
```

El mismo codigo lo devuelven el resto de rutas de negocio, con cualquier
combinacion de `apk`, `apkVer`, `spkgVer` (y tambien por HTTP/2 y contra
`portal_backup`). `config/get` si pasa la puerta pero responde `500`.

Es decir: el portal rechaza la version del cliente de forma global, antes de
leer el cuerpo: el par `appId` + version esta retirado en el tenant. Los hosts
se volvieron a probar el 2026-11-16 (`fuxok` y `mds` devuelven 200 ·
`portal200001`; `payima` ya no sirve `portalCore`, responde 404 de nginx).

Salidas reales:

1. Que el distribuidor rehabilite esta version y entregue host + `portalCode`.
2. Cambiar de backend: **la lista M3U propia ya esta implementada** (arriba).
3. Captura de trafico de una instalacion que siga funcionando, si el
   distribuidor la facilita.

Mientras tanto la app arranca, navega, reproduce la lista M3U si esta
configurada y, si no, la lista de canales incluida en `assets/lista.m3u`;
el contenido de ejemplo queda como ultimo recurso. El interceptor reescribe el
host con `BuildConfig.PORTAL_HOST` (compilado con `-PportalHost=...`) o con el
host guardado en `SettingsStore`.

## Entrega

| Dato | Valor |
|---|---|
| APK | `MartinVidalTV-1.1.0.apk` |
| Paquete | `com.martinvidal.tv` |
| Versión | `1.1.0` (versionCode 2), `minSdk 21`, `targetSdk 34` |
| Tamaño | 3524895 bytes (3.4 MiB) |
| Firma | `CN=Martin Vidal, O=Martin Vidal TV, C=BR` (`martinvidal.jks`) |
| SHA-256 del APK | `78bbe71438ad1dbf2e5ccbfb70571bd4a0404eea17f73e38a027e0e72c561a3a` |
| SHA-256 del certificado | `3192796cd3cd3eac7281412ae6be4cbd584da36fbe4479af1c5427e6c8df23f1` |

Instalación: `adb install -r MartinVidalTV-1.1.0.apk`. Para actualizar sin
perder la firma hay que conservar `martinvidal.jks` y `keystore.properties`
(no versionados).
