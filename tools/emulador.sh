#!/usr/bin/env bash
# Emulador API 30 (x86_64 con traduccion ARM) para probar la app.
#
#   tools/emulador.sh start    # arranca en segundo plano (sin ventana)
#   tools/emulador.sh stop     # lo apaga
#   tools/emulador.sh status   # estado y dispositivos conectados
#
# El log queda en capturas/logs/emulator.log.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="${ANDROID_SDK_ROOT:-$HOME/.android-sdk}"
export ANDROID_SDK_ROOT="$SDK"
export ANDROID_HOME="$SDK"
export PATH="$SDK/platform-tools:$SDK/emulator:$SDK/cmdline-tools/latest/bin:$PATH"

AVD="${AVD:-xuper_dump}"
API="system-images;android-30;google_apis;x86_64"
LOG_DIR="$ROOT/capturas/logs"
LOG="$LOG_DIR/emulator.log"

start() {
  if adb get-state >/dev/null 2>&1; then
    echo "ya hay un dispositivo conectado:"
    adb devices
    return 0
  fi

  mkdir -p "$LOG_DIR"

  if ! avdmanager list avd 2>/dev/null | grep -q "Name: $AVD"; then
    echo "creando AVD $AVD ($API)"
    echo "no" | avdmanager create avd -n "$AVD" -k "$API" --force
  fi

  emulator -avd "$AVD" \
    -no-window -no-audio -no-boot-anim -no-snapshot \
    -gpu swiftshader_indirect -memory 2048 -cores 4 \
    -dns-server 1.1.1.1,8.8.8.8 \
    -netdelay none -netspeed full \
    > "$LOG" 2>&1 &

  echo "emulador lanzado (log: $LOG)"
  echo "esperando arranque..."
  adb wait-for-device
  until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    sleep 3
  done
  # Sin teclado en pantalla al enfocar campos: en TV se navega con el D-pad.
  adb shell settings put secure show_ime_with_hard_keyboard 0 || true
  echo "listo: $(adb devices | sed -n '2p')"
}

case "${1:-start}" in
  start) start ;;
  stop)
    adb emu kill 2>/dev/null || pkill -f "qemu-system-x86_64.*$AVD" || true
    echo "emulador detenido"
    ;;
  status)
    adb devices
    echo "---"
    adb shell getprop sys.boot_completed 2>/dev/null || echo "sin dispositivo"
    ;;
  *)
    echo "uso: $0 {start|stop|status}" >&2
    exit 2
    ;;
esac
