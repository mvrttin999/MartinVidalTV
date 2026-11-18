#!/usr/bin/env bash
# Captura el trafico de la app original (la que SI funciona) para reconstruir
# el contrato exacto: host, cabeceras, cuerpo y portalCode.
#
# Uso tipico con un equipo Android TV conectado por ADB:
#
#   ./tools/capturar-trafico.sh inicio
#   # abre la app en el equipo, inicia sesion, entra a Live/VOD y sal
#   ./tools/capturar-trafico.sh fin
#
# Resultado:
#   capturas/trafico.flow   (formato mitmproxy, para mi)
#   capturas/trafico.har    (formato HAR, legible con DevTools)
#   capturas/resumen.txt    (URLs, cabeceras y cuerpos en claro)
#
# Requisitos: adb con el equipo autorizado, y mitmproxy (el script instala un
# entorno local en .tools-venv si falta).
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${OUT:-$ROOT/capturas}"
PORT="${PORT:-8080}"
VENV="${VENV:-$ROOT/.tools-venv}"
MITM="${MITM:-}"

# Busca mitmdump en el PATH o en un entorno virtual cercano.
buscar_mitm() {
  if [ -n "${MITM:-}" ] && [ -x "$MITM" ]; then return 0; fi
  if command -v mitmdump >/dev/null 2>&1; then
    MITM="$(command -v mitmdump)"; return 0
  fi
  local candidato
  for candidato in "$VENV/bin/mitmdump" "$(dirname "$ROOT")/.tools-venv/bin/mitmdump"; do
    if [ -x "$candidato" ]; then MITM="$candidato"; return 0; fi
  done
  return 1
}

mkdir -p "$OUT"

SDK="${ANDROID_SDK_ROOT:-$HOME/.android-sdk}"
if [ -x "$SDK/platform-tools/adb" ]; then
  export PATH="$SDK/platform-tools:$PATH"
fi

asegurar_mitm() {
  buscar_mitm && return 0
  echo "Instalando mitmproxy en $VENV ..."
  python3 -m venv "$VENV" || return 1
  "$VENV/bin/pip" -q install --upgrade pip >/dev/null 2>&1
  "$VENV/bin/pip" -q install mitmproxy || return 1
  MITM="$VENV/bin/mitmdump"
}

ip_local() {
  ip -4 route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src") print $(i+1)}' | head -1
}

es_emulador() {
  adb shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r' | grep -q '^1$'
}

host_proxy() {
  if es_emulador; then echo "10.0.2.2"; else ip_local; fi
}

instalar_ca() {
  local pem="$HOME/.mitmproxy/mitmproxy-ca-cert.pem"
  if [ ! -f "$pem" ]; then
    "$MITM" --listen-port 0 >/dev/null 2>&1 &
    sleep 4
    pkill -f "mitmdump --listen-port 0" >/dev/null 2>&1
  fi
  [ -f "$pem" ] || { echo "No se pudo generar el CA de mitmproxy"; return 1; }

  adb root >/dev/null 2>&1 && sleep 2 && adb wait-for-device
  if adb shell "mount -o rw,remount /" >/dev/null 2>&1 || adb remount >/dev/null 2>&1; then
    local hash
    hash="$(openssl x509 -inform PEM -subject_hash_old -in "$pem" | head -1)"
    adb push "$pem" "/system/etc/security/cacerts/$hash.0" >/dev/null 2>&1 &&
      adb shell "chmod 644 /system/etc/security/cacerts/$hash.0" &&
      { echo "CA instalado como certificado de sistema ($hash.0)."; return 0; }
  fi

  echo "No se pudo escribir en /system (equipo sin root)."
  echo "Instala el CA a mano como certificado de usuario:"
  echo "  1) copia $pem al equipo (adb push \"$pem\" /sdcard/mitm.pem)"
  echo "  2) Ajustes > Seguridad > Cifrado y credenciales > Instalar certificado > CA"
  echo "  3) elige /sdcard/mitm.pem y aceptalo"
  return 2
}

case "${1:-}" in
  inicio)
    asegurar_mitm || exit 1
    : > "$OUT/trafico.flow"
    nohup "$MITM" --listen-host 0.0.0.0 --listen-port "$PORT" \
      --set confdir="$HOME/.mitmproxy" \
      -w "$OUT/trafico.flow" > "$OUT/mitm.log" 2>&1 &
    echo $! > "$OUT/mitm.pid"
    sleep 2
    echo "mitmdump escuchando en el puerto $PORT"

    local_rc=0
    instalar_ca || local_rc=$?
    if [ "$local_rc" -eq 2 ]; then
      echo
      echo "Aviso: si la app usa certificate pinning, la captura no se podra leer."
      echo "En ese caso usa extraer-sesion.sh (no necesita interceptar TLS)."
    fi

    PROXY="$(host_proxy)"
    adb shell settings put global http_proxy "$PROXY:$PORT" >/dev/null 2>&1 || true
    echo
    echo "Proxy del equipo: $PROXY:$PORT"
    if [ -n "$PROXY" ] && [ "$PROXY" != "10.0.2.2" ]; then
      echo "El equipo debe estar en la misma red que este PC."
    fi
    echo "Ahora abre la app, inicia sesion y navega un poco. Luego:"
    echo "  $0 fin"
    ;;

  fin)
    if [ -f "$OUT/mitm.pid" ]; then
      kill "$(cat "$OUT/mitm.pid")" >/dev/null 2>&1
      rm -f "$OUT/mitm.pid"
    fi
    adb shell settings put global http_proxy :0 >/dev/null 2>&1
    sleep 1

    [ -s "$OUT/trafico.flow" ] || { echo "No hay captura en $OUT/trafico.flow"; exit 1; }

    asegurar_mitm || exit 1
    echo "Exportando a HAR..."
    "$MITM" -nr "$OUT/trafico.flow" --set hardump="$OUT/trafico.har" >/dev/null 2>&1
    python3 "$ROOT/tools/analizar-captura.py" "$OUT/trafico.flow" > "$OUT/resumen.txt" 2>&1
    echo
    echo "Listo:"
    echo "  $OUT/trafico.flow"
    echo "  $OUT/trafico.har"
    echo "  $OUT/resumen.txt"
    head -40 "$OUT/resumen.txt"
    ;;

  *)
    echo "Uso: $0 {inicio|fin}"
    echo "  inicio  levanta la captura y configura el proxy del equipo"
    echo "  fin     la detiene, exporta HAR y genera resumen"
    exit 1
    ;;
esac
