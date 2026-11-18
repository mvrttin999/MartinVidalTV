#!/usr/bin/env bash
# Extrae la configuracion local de la app original en un equipo donde SI
# funciona (host del portal, portalCode, sn, deviceToken...). No intercepta
# TLS, asi que sirve aunque la app use certificate pinning.
#
#   ./tools/extraer-sesion.sh                 # detecta el paquete solo
#   ./tools/extraer-sesion.sh com.android.mgstv
#
# Deja el resultado en capturas/sesion/ (XML/DB copiados y un resumen.txt).
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${OUT:-$ROOT/capturas/sesion}"
SDK="${ANDROID_SDK_ROOT:-$HOME/.android-sdk}"
if [ -x "$SDK/platform-tools/adb" ]; then
  export PATH="$SDK/platform-tools:$PATH"
fi

mkdir -p "$OUT"
PKG="${1:-}"

if ! adb get-state >/dev/null 2>&1; then
  echo "No hay equipo conectado por adb. Conecta el equipo y autoriza la depuracion."
  exit 1
fi

if [ -z "$PKG" ]; then
  echo "Paquetes candidatos:"
  adb shell pm list packages 2>/dev/null | tr -d '\r' | sed 's/^package://' |
    grep -Ei 'mgs|brasil|xuper|interactive|magis|tv' | tee "$OUT/paquetes.txt"
  PKG="$(grep -Ei 'mgs|brasil|xuper' "$OUT/paquetes.txt" | head -1)"
  [ -n "$PKG" ] || { echo "Indica el paquete: $0 <paquete>"; exit 1; }
  echo "Usando: $PKG"
fi

# Ruta del APK (util para saber la version instalada)
adb shell pm path "$PKG" 2>/dev/null | tr -d '\r' | tee "$OUT/apk-path.txt"

copiar_datos() {
  adb root >/dev/null 2>&1 && { sleep 2; adb wait-for-device; }
  # 1) run-as (apps debuggable)
  if adb shell "run-as $PKG ls /data/data/$PKG" >/dev/null 2>&1; then
    echo "via run-as"
    adb shell "run-as $PKG tar -cf - /data/data/$PKG/shared_prefs /data/data/$PKG/databases /data/data/$PKG/files 2>/dev/null" \
      > "$OUT/datos.tar" 2>/dev/null && tar -xf "$OUT/datos.tar" -C "$OUT" 2>/dev/null && return 0
  fi
  # 2) root directo
  if adb shell "ls /data/data/$PKG/shared_prefs" >/dev/null 2>&1; then
    echo "via root"
    adb shell "tar -cf - /data/data/$PKG/shared_prefs /data/data/$PKG/databases /data/data/$PKG/files 2>/dev/null" \
      > "$OUT/datos.tar" 2>/dev/null && tar -xf "$OUT/datos.tar" -C "$OUT" 2>/dev/null && return 0
  fi
  # 3) adb backup (Android 11 o anterior, allowBackup=true)
  echo "via adb backup"
  adb backup -f "$OUT/backup.ab" -noapk "$PKG" >/dev/null 2>&1
  if [ -s "$OUT/backup.ab" ]; then
    (cd "$OUT" && dd if=backup.ab bs=24 skip=1 2>/dev/null | zlib-flate -uncompress > backup.tar 2>/dev/null)
    [ -s "$OUT/backup.tar" ] && tar -xf "$OUT/backup.tar" -C "$OUT" 2>/dev/null && return 0
    (cd "$OUT" && dd if=backup.ab bs=24 skip=1 2>/dev/null | python3 -c "
import sys, zlib
data = sys.stdin.buffer.read()
out = zlib.decompress(data, 16 + zlib.MAX_WBITS)
sys.stdout.buffer.write(out)" > backup.tar 2>/dev/null)
    [ -s "$OUT/backup.tar" ] && tar -xf "$OUT/backup.tar" -C "$OUT" 2>/dev/null && return 0
  fi
  return 1
}

if ! copiar_datos; then
  echo "No se pudo leer /data/data/$PKG."
  echo "Alternativas: root en el equipo, o APK debuggable, o 'adb backup' en Android <= 11."
  exit 1
fi

echo
echo "== Claves de configuracion encontradas =="
{
  echo "# extraido de $PKG"
  grep -rhoE '<string name="[^"]+">[^<]*</string>|<int name="[^"]+" value="[^"]+"|<boolean name="[^"]+" value="[^"]+"' \
    "$OUT" --include='*.xml' 2>/dev/null |
    grep -viE 'pwd|password|passwd' |
    sed 's/<string name="//; s/<int name="//; s/<boolean name="//; s/">/ = /; s/" value="/ = /; s/"\/$//; s/<\/string>$//' |
    sort -u
} | tee "$OUT/claves.txt"

echo
echo "== Valores interesantes (host, portal, token, sn) =="
grep -inE 'host|portal|domain|token|sn|mac|url' "$OUT/claves.txt" | head -40 | tee "$OUT/resumen.txt"

echo
echo "Archivos copiados en: $OUT"
echo "Pegame el contenido de $OUT/claves.txt (o de resumen.txt) y ajusto el cliente."
