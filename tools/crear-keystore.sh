#!/usr/bin/env bash
# Crea la firma propia de la app. Guarda las contraseñas en un lugar seguro.
set -euo pipefail

OUT="${1:-martinvidal.jks}"
ALIAS="${2:-martinvidal}"

if [ -f "$OUT" ]; then
  echo "Ya existe $OUT"
  exit 1
fi

keytool -genkeypair -v \
  -keystore "$OUT" \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Martin Vidal, O=Martin Vidal TV, C=BR"

echo
echo "Creado $OUT"
echo "Copia keystore.properties.example a keystore.properties y rellena las contraseñas."
