# CHECKPOINT — Auditoría Xuper para MartinVidalTV

**Guardado:** 2026-11-18. Sesión interrumpida porque el usuario apaga el PC.
**Objetivo:** extraer los canales EXACTOS de Xuper y sus URLs reales para convertirlos en la M3U propia de MartinVidalTV. El usuario rechazó listas públicas y sustitutos.

---

## 1. Estado del proyecto (NO tocado por la auditoría)

- APK release entregado: `/home/mvrttin999/Descargas/MartinVidalTV-1.0.0.apk`
  - 3.470.483 bytes, SHA-256 `78c9aabc2c3cdba0dce7eab6e3c74ada469bb031ab974b9d15e21af6b24e508a`
  - **NO recompilado todavía** con la lista de Xuper.
- App: sin cuenta / sesión / activación. Paleta `#0B0B0B`/`#FFAA00`/`#FFFFFF`/`#C4C4C4`, 960×540dp, XML + Leanback, `applicationId com.martinvidal.tv`, minSdk 21 / targetSdk 34.
- Fuente actual: `assets/lista.m3u` con 42 canales de ejemplo en 8 grupos (lista pública). `BuildConfig.PLAYLIST_URL=""` (compilado sin `-PplaylistUrl`).
- Stack: Kotlin 2.0.21, AGP 8.7.3, Gradle 8.9, JDK 21, compileSdk 35, Retrofit/OkHttp/Moshi, Coil, Hilt, Coroutines, DataStore, Room, Media3, WorkManager 2.9.1 + hilt-work 1.2.0.
- Crypto propio: `PortalCrypto.KEY="213f24ac-14b7-4c03-a209-383ead1b014f"`, `X-Client-Meta=md5(X-Request-Trace+KEY)`, token NF (ya implementado en `/tmp/opencode/nf-token.py`).
- P01–P30 completados. P31 (auditoría Xuper) es el trabajo en curso. P22b parcial (AVD API 21 no arranca con emulator 37). P21/P24b/P27b pendientes. Publicación GitHub pendiente (sin `gh`/repo/token).

---

## 2. Xuper vigente encontrado

- **Build útil: v4.35.0**, `/tmp/opencode/Xuper-NF-4.35.0.apk`, 33 MB, `versionCode=43500`, `versionName=4.35.0`, paquete `com.android.mgstxNF`, SHA-256 guardado antes. Código Downloader `3435719` (Mediafire).
  - Instalar SIEMPRE con `adb install --abi arm64-v8a -r` (si no, falta `libranger-jni.so` y crashea con `UnsatisfiedLinkError`).
  - Arranca sin anti-tampering ni proxy. Muestra catálogo REAL actualizado (Spider-Man: Brand New Day, 2026).
- v4.35.1 (`8691626`) NO usar: crashea con `libranger-jni.so not found` + `SELF TAMPER got=d0093f1f baked=4e464d4f`.
- v4.34.9 quedó descartado: backend devuelve `{"returnCode":"portal200001","errorMessage":"版本已停止使用"}` para `apkVer=43409/43404/49902`.
- Hosts descifrados del `log.xml` (AES-128-CBC, clave `b972E8a5A4e0e8Ff`, IV = primeros 16 bytes del Base64): `/tmp/opencode/xuper-hosts.json`.
- Parámetros v4.34.9 de referencia: `userId=992997246`, `userToken=a08ae75d-1974-4411-970c-ecd0ffbaa96b`, `sn=f1a5f396dabc9892bf0e7dc73ebe7902`, `portal_code=6e54356f76774c54574b303d`.
- Catálogo LIVE visible en v4.35.0: **La Granja VIP 1 HD, La Granja VIP 2 HD, ECDF, ECDF FHD, NBA Eventos, A3S**. `cl.m3u`/`es.m3u` de iptv-org NO los contienen.

---

## 3. Hallazgo clave de esta sesión: cómo funciona el reproductor del mod

El mod es **Ranger/4.9.4-17294ac0** (`libranger-jni.so`, `lib/arm64-v8a`), un componente **NATIVO** que hace las peticiones HTTP por sockets propios. Por eso **ningún hook de OkHttp ve la URL real**.

Flujo confirmado por Frida:
1. La app llama a `com.titan.ranger.NativeJni.Call(String cmd, String json)`:
   - `PrepareProgram` → argumento JSON:
     ```
     {"instance":0,"name":"cyx_5D7BA85AE3F8A2A0921E_720p",
      "program":"{\"buss\":\"live\",\"cause\":\"user\",\"episode\":\"-1\",\"from\":\"channellist\",
        \"media\":\"cyx_5D7BA85AE3F8A2A0921E_720p\",
        \"medias\":[{\"format\":\"ts\",\"lang\":\"\",\"license\":\"app_id=com.android.msandroid&tag=free&scheme=md5-01&media_code=cyx_5D7BA85AE3F8A2A0921E_720p&expired=1795620916&token=FBA51F24769433F46381BA7B5D285339\",\"name\":\"cyx_5D7BA85AE3F8A2A0921E_720p\",\"quality\":\"480p\",\"vcodec\":\"h264\"}, ...],
        \"name\":\"cyx_5D7BA85AE3F8A2A0921E_720p\",\"start\":0,\"title\":\"La Granja VIP 1 HD\"}",
      "extra":"live"}
     ```
   - `GetStatus` / `StopProgram` → `{"instance":0,"name":"<media>"}`
2. El nativo resuelve la URL real internamente y devuelve (via `Call`/`a`):
   ```
   {"err":0,"res":"{\"buffer\":...,\"buss\":\"live\",\"media\":\"cyx_5D7BA85AE3F8A2A0921E_720p\",
     \"play_url\":\"mem://127.0.0.1:<PUERTO_DINAMICO>\",
     \"snapinfo_url\":\"http://127.0.0.1:<PUERTO>/vod/0/cyx_..._720p.snapinfo\",
     \"title\":\"La Granja VIP 1 HD\",\"url_modified\":true}"}
   ```
3. El puerto del proxy local cambia en cada arranque: observados `33343`, `38555`, `39900`, `43057`, `44351`, `27042` (este último es Frida).
4. Rutas probadas con `nc` contra el proxy local → `404`/`503`/`400`. El `snapinfo` da 503. Falta averiguar la ruta exacta o extraerla del binario nativo.

**Datos útiles para reconstruir la URL real:** `media_code=cyx_5D7BA85AE3F8A2A0921E_720p`, `token=FBA51F24769433F46381BA7B5D285339`, `expired=1795620916`, `quality=480p`, `format=ts`, `vcodec=h264`, `license` completa arriba. Es muy probable que la URL real se construya con `media_code` + `token` + uno de los hosts del portal.

Clases del mod (en `classes3.dex`): `com.titan.ranger.Status` (bean con `play_url`, `url_modified`), `com.titan.ranger.bean.RangerResult` (`err`/`res`), `com.titan.ranger.NativeJni` (`Call`, `a`, `b`, `c`, `d`, `e`, `f`, `g`, `h`, `i`, `j`), `com.titan.ranger.JniHandler`, `com.titan.ranger.bean.{Media,MediaFile,Entry,Program,Env}`.

---

## 4. QUÉ NO HACER (aprendido a base de crashes)

1. **NO** hookear `okhttp3.internal.http.RealInterceptorChain.proceed` ni `peekBody` sobre bodies consumidos → `NullPointerException` en `ConnectInterceptor`, mata la app. (Hook problemático: `frida-v435.js`.)
2. **NO** usar `Java.enumerateClassLoadersSync()` + `Java.classFactory.loader=ld` con `use()` → selecciona el classloader equivocado y no captura. Funciona **`Java.use(...)` directo**.
3. **NO** configurar proxy con `settings put global http_proxy 10.0.2.2:8080` → la app no carga `getLiveData` (solo `get_notice` 502 y `adserver`).
4. **NO** redirigir con iptables transparente (`REDIRECT --uid-owner 10177 --dport 80/443 → 8080`) → la app muestra "Network detection / The network connection failed". Ya limpiado con `iptables -t nat -F OUTPUT`.
5. **NO** usar `pkill -f`. Matar Frida por PID guardado en `/tmp/opencode/*.pid`.
6. **NO** reinstalar la v4.35.1 ni la v4.34.9 para esto.
7. `UID` es variable de sóla lectura en bash → usar `APP_UID`. Los `while read` en pipe corren en subshell con PATH alterado → usar `/usr/bin/adb` absoluto.

---

## 5. Hooks Frida que SÍ funcionan (todos con `Java.use` directo)

- `/tmp/opencode/frida-urls2.js` → `Gson.toJson/fromJson` + `ResponseBody.string`; captura los `Status` (`play_url mem://`).
- `/tmp/opencode/frida-gsonraw.js` → `Gson.fromJson` leyendo `StringReader` con `mark/reset` (JSON crudo). **Corregido**: `StringReader` no tiene `getLength()`, usar `mark(2000000)`.
- `/tmp/opencode/frida-ranger.js` → beans `com.titan.ranger.*`.
- `/tmp/opencode/frida-allbeans.js` → todos los beans de Gson.
- `/tmp/opencode/frida-jni.js` → enumera/hookea métodos JNI con String.
- **`/tmp/opencode/frida-call.js` → EL MÁS ÚTIL**: captura argumentos y retornos de `NativeJni.Call/a/b/f/h/i` sin filtro. Ver `/tmp/opencode/frida-call.log` (449 líneas, contiene TODOS los `PrepareProgram`).
- Frida CLI: `/tmp/opencode/mitmvenv/bin/frida`. Frida server en el emulador: PID `12178`, binario `/data/local/tmp/fsa`. Adjuntar: `frida -U -p <PID> -l <script>`. Spawn: `frida -U -f com.android.mgstxNF -l <script>`.

---

## 6. Estado del emulador al apagar

- `emulator-5554` (API 30 x86_64 con capa arm64), Xuper v4.35.0 enforeground, PID rotando por reinicios: `28228 → 28831 → 29339 → 30019 → 30457`.
- `http_proxy = :0`, iptables `OUTPUT` limpio, mitmdump detenido.
- Hook activo al apagar: `frida-call.js`, PID guardado en `/tmp/opencode/frida-call.pid`.
- Navegación: tooltips se cierran con taps en (640,480); LIVE en (186,279); canal "La Granja VIP 1 HD" en (1774,229), "La Granja VIP 2 HD" en (1755,210), ECDF en (1650,320). `uiautomator dump --compressed /sdcard/ui.xml`.
- Un intento de arrancar un AVD secundario llamado `xuper_dump` fue **cancelado por el reinicio del servidor**; no está confirmado que exista o se haya creado. No darlo por hecho.

---

## 7. Paso siguiente concreto al volver

1. Verificar `adb devices` y `pidof com.android.mgstxNF`; si no está, relanzar v4.35.0 (ya instalada) y pasar tooltips.
2. Extraer las cadenas/rutas del binario nativo `libranger-jni.so` (dentro del APK v4.35.0) para averiguar la ruta exacta del proxy local y/o el patrón de construcción de la URL real a partir de `media_code` + `token` + host. Herramientas: REA (`reverse-engineer-anything`), `strings`, `radare2`/`rizin`. El dump DEX ya está en `/tmp/opencode/x435-classes3.dump`.
3. Alternativa: reconstruir la request `startPlayLive` con `media_code`/`token`/`license` y los hosts de `/tmp/opencode/xuper-hosts.json`, firmando con `PortalCrypto.KEY` y el token `att` (ver `/tmp/opencode/nf-token.py`). Esto es lo que la app hace antes de pasarle el `PrepareProgram` al nativo.
4. Repetir clic en los 6 canales visibles (y recorrer el catálogo completo) para capturar TODOS los `PrepareProgram` → `media_code` + `token` de cada canal.
5. Generar M3U con nombres/grupos/URLs reales, integrar en `app/src/main/assets/lista.m3u`, recompilar release, verificar en emulador y actualizar `README.md`, `PENDIENTES.md` (P31), `update.json` y el SHA del APK (`tools/make-update-json.sh`).

---

## 8. Archivos de captura a conservar

En `/tmp/opencode/` (copiar al proyecto si `/tmp` se limpia):
`frida-call.log` (clave), `frida-ranger.log`, `frida-allbeans.log`, `frida-gsonraw2.log`, `frida-jni.log`, `x435-classes3.dump`, `xuper-hosts.json`, `nf-token.py`, `codes-vigentes.txt`, `xuper-capture.jsonl`, `Xuper-NF-4.35.0.apk`, todos los `frida-*.js`, `capture-addon.py`.

En el proyecto: `README.md`, `PENDIENTES.md`, `tools/make-update-json.sh`, `app/src/main/assets/lista.m3u`.
