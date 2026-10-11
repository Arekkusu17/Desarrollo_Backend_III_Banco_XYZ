#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TLS_DIR="${TLS_OUTPUT_DIR:-$ROOT_DIR/output/tls}"
TLS_PASSWORD="${TLS_STORE_PASSWORD:-changeit}"
SAN="dns:localhost,ip:127.0.0.1,dns:auth-server,dns:api-gateway,dns:bff-web,dns:bff-mobile,dns:bff-atm,dns:ms-account,dns:ms-payment,dns:ms-customer"

validate_ipv4() {
  local ip="$1" octet
  local -a octets
  [[ "$ip" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || return 1
  IFS=. read -r -a octets <<< "$ip"
  for octet in "${octets[@]}"; do
    [[ ${#octet} -le 3 && "$octet" == "$((10#$octet))" ]] || return 1
    (( 10#$octet <= 255 )) || return 1
  done
}

if [[ -n "${TLS_PUBLIC_IP:-}" ]]; then
  validate_ipv4 "$TLS_PUBLIC_IP" || { echo "TLS_PUBLIC_IP invalida"; exit 1; }
  SAN="$SAN,ip:$TLS_PUBLIC_IP"
fi
EXTRA_IPS=()
[[ "${TLS_SCALE_NETWORK:-false}" == true || "${TLS_SCALE_NETWORK:-false}" == false ]] || { echo "TLS_SCALE_NETWORK debe ser true o false"; exit 1; }
if [[ "${TLS_SCALE_NETWORK:-false}" == true ]]; then
  # Cover the entire allocation pool in docker-compose.scale.yml, not a snapshot of running IPs.
  for ((host=0; host<64; host++)); do
    EXTRA_IPS+=("172.30.90.$host")
  done
fi
if [[ -n "${TLS_EXTRA_IPS:-}" ]]; then
  [[ "$TLS_EXTRA_IPS" != ,* && "$TLS_EXTRA_IPS" != *, && "$TLS_EXTRA_IPS" != *,,* ]] || { echo "TLS_EXTRA_IPS invalida"; exit 1; }
  IFS=, read -r -a REQUESTED_IPS <<< "$TLS_EXTRA_IPS"
  EXTRA_IPS+=("${REQUESTED_IPS[@]}")
fi
if [[ ${#EXTRA_IPS[@]} -gt 0 ]]; then
  for ip in "${EXTRA_IPS[@]}"; do
    validate_ipv4 "$ip" || { echo "IP invalida en TLS_EXTRA_IPS: $ip"; exit 1; }
    SAN="$SAN,ip:$ip"
  done
fi
if [[ -n "${TLS_PUBLIC_DNS:-}" ]]; then
  [[ "$TLS_PUBLIC_DNS" =~ ^[a-zA-Z0-9.-]+$ ]] || { echo "TLS_PUBLIC_DNS invalido"; exit 1; }
  SAN="$SAN,dns:$TLS_PUBLIC_DNS"
fi

[[ "${TLS_REGENERATE:-false}" == true || "${TLS_REGENERATE:-false}" == false ]] || { echo "TLS_REGENERATE debe ser true o false"; exit 1; }
umask 077
mkdir -p "$TLS_DIR"
GENERATE=true
if [[ -e "$TLS_DIR/bank.p12" || -e "$TLS_DIR/truststore.p12" || -e "$TLS_DIR/bank.crt" ]]; then
  for file in bank.p12 bank.crt truststore.p12; do
    [[ -f "$TLS_DIR/$file" ]] || { echo "Certificados incompletos en $TLS_DIR; falta $file."; exit 1; }
  done
  if [[ "${TLS_REGENERATE:-false}" != true ]]; then
    if [[ ${#EXTRA_IPS[@]} -gt 0 ]]; then
      CERT_INFO=$(keytool -J-Duser.language=en -J-Duser.country=US -printcert -file "$TLS_DIR/bank.crt")
      for ip in "${EXTRA_IPS[@]}"; do
        if ! grep -Eq "^[[:space:]]*IPAddress: ${ip//./[.]}[[:space:]]*$" <<< "$CERT_INFO"; then
          echo "El certificado no cubre $ip. Usar TLS_REGENERATE=true y reiniciar los clientes TLS."
          exit 1
        fi
      done
    fi
    GENERATE=false
    echo "Ya existen certificados en $TLS_DIR; se conservan."
  fi
fi

if [[ "$GENERATE" == true ]]; then
  STAGING=$(mktemp -d "$TLS_DIR/.new.XXXXXX")
  trap 'rm -rf "$STAGING"' EXIT
  keytool -genkeypair -alias bff-local -keyalg RSA -keysize 2048 -storetype PKCS12 \
    -keystore "$STAGING/bank.p12" -validity 365 -storepass "$TLS_PASSWORD" -keypass "$TLS_PASSWORD" \
    -dname "CN=localhost, OU=BackendIII, O=DuocUC, L=Santiago, C=CL" -ext "SAN=$SAN"
  keytool -exportcert -rfc -alias bff-local -keystore "$STAGING/bank.p12" \
    -storepass "$TLS_PASSWORD" -file "$STAGING/bank.crt"
  keytool -importcert -noprompt -alias bank-local -storetype PKCS12 \
    -keystore "$STAGING/truststore.p12" -storepass "$TLS_PASSWORD" -file "$STAGING/bank.crt"
  if [[ -f "$TLS_DIR/bank.p12" ]]; then
    BACKUP=$(mktemp -d "$TLS_DIR/backup.XXXXXX")
    cp "$TLS_DIR/bank.p12" "$TLS_DIR/bank.crt" "$TLS_DIR/truststore.p12" "$BACKUP/"
    echo "Certificados anteriores respaldados en $BACKUP."
  fi
  # Keep the mounted directory in place; clients must reload both identity and trust.
  cp "$STAGING/bank.p12" "$STAGING/bank.crt" "$STAGING/truststore.p12" "$TLS_DIR/"
  echo "Certificados de laboratorio generados en $TLS_DIR."
  echo "Si el stack esta activo, reiniciar todos los servicios que usan TLS y reimportar bank.crt en Postman."
fi

if [[ "${TLS_COPY_BFF:-true}" == true ]]; then
  for service in bff-web bff-mobile bff-atm; do
    cp "$TLS_DIR/bank.p12" "$ROOT_DIR/$service/src/main/resources/keystore.p12"
  done
  echo "Certificado copiado a los tres BFF."
fi
