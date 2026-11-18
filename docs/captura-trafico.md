# Capturar el tráfico de la app que funciona

El portal rechaza nuestra versión con `portal200001` en todas las rutas de
negocio, así que necesitamos el contrato exacto (host, cabeceras y
`portalCode`) de una instalación que sí funcione. Hay dos caminos; el segundo
no necesita interceptar TLS.

Estas herramientas son solo de análisis del APK original: la app entregada no
tiene cuenta, sesión ni activación.

## Opción A · Copiar la configuración del equipo (más rápido)

Muchas veces no hace falta ver el tráfico: el host y el `portalCode` están
guardados en la app. Con el equipo conectado por ADB y la app original
instalada:

```bash
./tools/extraer-sesion.sh
```

El script detecta el paquete, copia `shared_prefs`, bases de datos y ficheros,
y escribe un resumen en `capturas/sesion/claves.txt` (oculta contraseñas).

Envíame `capturas/sesion/resumen.txt` y con eso ajusto `SettingsStore` (solo
host y `portalCode`) y el interceptor. Funciona con equipos rooteados, APK
debuggable o `adb backup` en Android 11 o anterior.

## Opción B · Interceptar el tráfico (mitmproxy)

Necesario si el `portalCode` no está en disco o si el servidor valida algo
más. Requiere que el equipo acepte un certificado de usuario o tenga root.

```bash
./tools/capturar-trafico.sh inicio
# abre la app en el equipo, inicia sesión y entra a Live y a una película
./tools/capturar-trafico.sh fin
```

`inicio` levanta mitmproxy, instala el CA en el equipo (o te indica cómo
hacerlo a mano) y configura el proxy. `fin` lo apaga, restaura el proxy y
genera:

| Fichero | Contenido |
|---|---|
| `capturas/trafico.flow` | captura completa, para mí |
| `capturas/trafico.har` | mismo contenido en formato HAR |
| `capturas/resumen.txt` | URLs, cabeceras y cuerpos en claro |

Puedes revisar `resumen.txt` antes de enviarlo: no incluye contraseñas porque
la app las envía transformadas, pero sí el token de sesión.

### Si la app no deja leer el tráfico

1. **Certificate pinning**: la captura saldrá con errores de TLS. En ese caso
   usa la opción A o un equipo con root para instalar el CA en el sistema.
2. **Equipo sin root y Android 12+**: instala el CA como certificado de
   usuario (`Ajustes > Seguridad > Cifrado y credenciales > Instalar
   certificado > CA`). Muchas apps de TV lo aceptan al no fijar certificados.
3. **Proxy manual**: si el equipo no acepta `settings put global http_proxy`,
   configúralo en `Ajustes > Red > Proxy` con la IP del PC y el puerto 8080.

## Qué hago yo con la captura

```bash
python3 tools/analizar-captura.py capturas/trafico.flow
```

Resume cada petición con las cabeceras `apk`, `apkVer`, `spkgVer`,
`X-Request-Trace`, `X-Client-Meta`, el `portalCode` del cuerpo y el
`returnCode` del portal. Con eso verifico o corrijo:

- host real y si cambia entre endpoints;
- `portalCode`, `sn`, `deviceToken` y `b29` efectivos;
- si el cuerpo va cifrado o en claro;
- qué endpoints usa la app realmente (y con qué parámetros).
