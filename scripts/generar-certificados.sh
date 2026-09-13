#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TMP_KEYSTORE="$ROOT_DIR/keystore.p12"

rm -f "$TMP_KEYSTORE"

keytool -genkeypair \
  -alias bff-local \
  -keyalg RSA \
  -keysize 2048 \
  -storetype PKCS12 \
  -keystore "$TMP_KEYSTORE" \
  -validity 365 \
  -storepass changeit \
  -keypass changeit \
  -dname "CN=localhost, OU=BackendIII, O=DuocUC, L=Santiago, ST=RegionMetropolitana, C=CL" \
  -ext "SAN=dns:localhost,ip:127.0.0.1"

for service in bff-web bff-mobile bff-atm; do
  cp "$TMP_KEYSTORE" "$ROOT_DIR/$service/src/main/resources/keystore.p12"
done

rm -f "$TMP_KEYSTORE"
echo "Certificado de laboratorio generado y copiado a los tres BFF."
