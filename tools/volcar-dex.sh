#!/usr/bin/env bash
# Volca los DEX descifrados en memoria del APK original usando Frida.
set -uo pipefail

SDK="${ANDROID_SDK_ROOT:-$HOME/.android-sdk}"
export PATH="$SDK/platform-tools:$PATH"
FRIDA_SERVER="${FRIDA_SERVER:-/tmp/opencode/frida-server}"
FRIDA_DEXDUMP="${FRIDA_DEXDUMP:-/tmp/opencode/frida-venv/bin/frida-dexdump}"
PKG="${PKG:-com.android.mgstv}"
OUT="${OUT:-/tmp/opencode/xuper/dump}"

mkdir -p "$OUT"

echo "== dispositivo"
adb wait-for-device
adb root >/dev/null 2>&1 || true
sleep 2
adb wait-for-device

echo "== frida-server"
adb push "$FRIDA_SERVER" /data/local/tmp/frida-server >/dev/null
adb shell "chmod 755 /data/local/tmp/frida-server"
adb shell "pkill -f frida-server" >/dev/null 2>&1 || true
adb shell "/data/local/tmp/frida-server -D >/dev/null 2>&1 &"
sleep 3
adb shell "ps -A | grep frida-server" || { echo "frida-server no arranco"; exit 1; }

echo "== lanzando app"
adb shell "am force-stop $PKG"
adb shell "monkey -p $PKG -c android.intent.category.LEANBACK_LAUNCHER 1" >/dev/null 2>&1
sleep 8

echo "== volcando DEX"
"$FRIDA_DEXDUMP" -U -f "$PKG" -o "$OUT"

echo "== resultado"
ls -la "$OUT"
for f in "$OUT"/*.dex; do
  [ -e "$f" ] || continue
  head -c 8 "$f" | grep -q "dex" && echo "OK  $f ($(stat -c%s "$f") bytes)" || echo "MAL $f (cabecera no es dex)"
done
