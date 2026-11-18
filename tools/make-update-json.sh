#!/usr/bin/env bash
# Genera update.json con la SHA-256 real de un APK.
# Uso: ./tools/make-update-json.sh <apk> <versionCode> <versionName> <apkUrl> [notes] [salida]
set -euo pipefail

if [ $# -lt 4 ]; then
  echo "Uso: $0 <apk> <versionCode> <versionName> <apkUrl> [notes] [salida]" >&2
  exit 2
fi

apk="$1"; code="$2"; name="$3"; url="$4"
notes="${5:-Nueva versión disponible}"
out="${6:-update.json}"

[ -f "$apk" ] || { echo "No existe: $apk" >&2; exit 1; }
[[ "$code" =~ ^[0-9]+$ ]] || { echo "versionCode debe ser entero: $code" >&2; exit 1; }

case "$url" in
  https://*) ;;
  *) echo "AVISO: en release solo se aceptan HTTPS; '$url' sera rechazado por la app." >&2 ;;
esac

sha=$(sha256sum "$apk" | awk '{print $1}')

jq -n \
  --argjson versionCode "$code" \
  --arg versionName "$name" \
  --arg apkUrl "$url" \
  --arg sha256 "$sha" \
  --arg notes "$notes" \
  '{versionCode:$versionCode, versionName:$versionName, apkUrl:$apkUrl, sha256:$sha256, notes:$notes}' \
  > "$out"

echo "Generado: $out"
cat "$out"
