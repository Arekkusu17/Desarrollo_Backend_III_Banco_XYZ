#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TLS_DIR="${TLS_OUTPUT_DIR:-$ROOT_DIR/output/tls}"
TLS_PASSWORD="${TLS_STORE_PASSWORD:-changeit}"
SAN="dns:localhost,ip:127.0.0.1,dns:auth-server,dns:api-gateway,dns:bff-web,dns:bff-mobile,dns:bff-atm,dns:ms-account,dns:ms-payment,dns:ms-customer"

if [[ -n "${TLS_PUBLIC_IP:-}" ]]; then
  [[ "$TLS_PUBLIC_IP" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "TLS_PUBLIC_IP invalida"; exit 1; }
  SAN="$SAN,ip:$TLS_PUBLIC_IP"
fi
if [[ -n "${TLS_PUBLIC_DNS:-}" ]]; then
  [[ "$TLS_PUBLIC_DNS" =~ ^[a-zA-Z0-9.-]+$ ]] || { echo "TLS_PUBLIC_DNS invalido"; exit 1; }
  SAN="$SAN,dns:$TLS_PUBLIC_DNS"
fi

mkdir -p "$TLS_DIR"
if [[ -e "$TLS_DIR/bank.p12" || -e "$TLS_DIR/truststore.p12" || -e "$TLS_DIR/bank.crt" ]]; then
  for file in bank.p12 bank.crt truststore.p12; do
    [[ -f "$TLS_DIR/$file" ]] || { echo "Certificados incompletos en $TLS_DIR; falta $file."; exit 1; }
  done
  echo "Ya existen certificados en $TLS_DIR; se conservan."
else
  umask 077
  keytool -genkeypair -alias bff-local -keyalg RSA -keysize 2048 -storetype PKCS12 \
    -keystore "$TLS_DIR/bank.p12" -validity 365 -storepass "$TLS_PASSWORD" -keypass "$TLS_PASSWORD" \
    -dname "CN=localhost, OU=BackendIII, O=DuocUC, L=Santiago, C=CL" -ext "SAN=$SAN"
  keytool -exportcert -rfc -alias bff-local -keystore "$TLS_DIR/bank.p12" \
    -storepass "$TLS_PASSWORD" -file "$TLS_DIR/bank.crt"
  keytool -importcert -noprompt -alias bank-local -storetype PKCS12 \
    -keystore "$TLS_DIR/truststore.p12" -storepass "$TLS_PASSWORD" -file "$TLS_DIR/bank.crt"
  echo "Certificados de laboratorio generados en $TLS_DIR."
fi

if [[ "${TLS_COPY_BFF:-true}" == true ]]; then
  for service in bff-web bff-mobile bff-atm; do
    cp "$TLS_DIR/bank.p12" "$ROOT_DIR/$service/src/main/resources/keystore.p12"
  done
  echo "Certificado copiado a los tres BFF."
fi
