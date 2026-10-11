#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUTPUT="$ROOT_DIR/output/eft-validation"
TLS_DIR="$ROOT_DIR/output/tls"
PG_PORT="${EFT_TEST_PG_PORT:-5432}"
DATABASE="${EFT_TEST_DATABASE:-banco_eft_tests}"
PG_USER="${EFT_TEST_PG_USER:-banco}"
PG_PASSWORD="${EFT_TEST_PG_PASSWORD:-banco}"
KAFKA_PORT="${EFT_TEST_KAFKA_PORT:-33092}"
SCHEMA="eft_api_$(date +%Y%m%d%H%M%S)"
GATEWAY="https://localhost:18084"
TOKEN=""
PIDS=()
RESPONSE="$OUTPUT/response.json"
mkdir -p "$OUTPUT"
[[ "$DATABASE" != "banco_xyz" && "$DATABASE" =~ ^[a-zA-Z0-9_]+$ ]] || exit 1
for command in java curl jq psql lsof uuidgen; do command -v "$command" >/dev/null; done
[[ -f "$TLS_DIR/bank.p12" ]] || bash "$ROOT_DIR/scripts/generar-certificados-cloud.sh"

cleanup() {
  for pid in "${PIDS[@]}"; do kill "$pid" 2>/dev/null || true; done
  for pid in "${PIDS[@]}"; do wait "$pid" 2>/dev/null || true; done
}
trap cleanup EXIT
trap 'exit 130' INT TERM

query() {
  PGPASSWORD="$PG_PASSWORD" PGOPTIONS="-c search_path=$SCHEMA" psql -X -At -v ON_ERROR_STOP=1 \
    -h localhost -p "$PG_PORT" -U "$PG_USER" -d "$DATABASE" "$@"
}
query -c "CREATE SCHEMA $SCHEMA" >/dev/null

start() {
  local module="$1" port="$2" secure="$3"
  local jar="$ROOT_DIR/$module/target/$module-0.0.1-SNAPSHOT.jar"
  [[ -f "$jar" ]] || { echo "Falta $jar; ejecutar ./mvnw package"; exit 1; }
  [[ -z "$(lsof -tiTCP:"$port" -sTCP:LISTEN)" ]] || { echo "Puerto $port ocupado"; exit 1; }
  local args=(
    "-Xmx128m" "-Dbank.instance=$port"
    "-Djavax.net.ssl.trustStore=$TLS_DIR/truststore.p12"
    "-Djavax.net.ssl.trustStorePassword=${TLS_STORE_PASSWORD:-changeit}"
    "-Djavax.net.ssl.trustStoreType=PKCS12" -jar "$jar"
    "--server.port=$port"
    "--spring.config.import=optional:configserver:http://localhost:18888"
    "--eureka.client.service-url.defaultZone=http://localhost:18761/eureka/"
    "--eureka.instance.ip-address=127.0.0.1" "--eureka.instance.prefer-ip-address=true"
    "--eureka.instance.instance-id=$module:$port"
    "--eureka.client.registry-fetch-interval-seconds=5"
    "--spring.cloud.loadbalancer.cache.ttl=2s"
    "--eureka.instance.lease-renewal-interval-in-seconds=5"
    "--spring.datasource.url=jdbc:postgresql://localhost:$PG_PORT/$DATABASE?currentSchema=$SCHEMA"
    "--spring.datasource.username=$PG_USER" "--spring.datasource.password=$PG_PASSWORD"
    "--spring.datasource.hikari.maximum-pool-size=3" "--spring.datasource.hikari.minimum-idle=1"
    "--spring.kafka.bootstrap-servers=localhost:$KAFKA_PORT"
    "--spring.security.oauth2.resourceserver.jwt.issuer-uri=https://localhost:19000"
    "--oauth.issuer=https://localhost:19000"
    "--app.oauth.token-url=https://localhost:19000/oauth2/token"
    "--core.banking.url=https://ms-account" "--account.service.url=https://ms-account"
    "--spring.cloud.gateway.server.webflux.httpclient.ssl.trusted-x509-certificates[0]=$TLS_DIR/bank.crt"
    "--bank.annual.report-output=$OUTPUT/annual.csv"
  )
  if [[ "$secure" == true ]]; then
    args+=("--server.ssl.enabled=true" "--server.ssl.key-store=file:$TLS_DIR/bank.p12"
      "--server.ssl.key-store-password=${TLS_STORE_PASSWORD:-changeit}" "--server.ssl.key-store-type=PKCS12"
      "--server.ssl.key-alias=bff-local"
      "--eureka.instance.secure-port-enabled=true" "--eureka.instance.non-secure-port-enabled=false"
      "--eureka.instance.secure-port=$port")
  fi
  CONFIG_SERVER_URL=http://localhost:18888 java "${args[@]}" > "$OUTPUT/$module-$port.log" 2>&1 &
  PIDS+=("$!")
  LAST_PID="$!"
}

wait_health() {
  local url="$1"
  for ((i=0; i<90; i++)); do
    if curl -fsS --cacert "$TLS_DIR/bank.crt" "$url/actuator/health" 2>/dev/null | jq -e '.status == "UP"' >/dev/null; then
      return
    fi
    sleep 2
  done
  echo "Servicio no disponible: $url"; exit 1
}

request() {
  local expected="$1" method="$2" url="$3" data="${4:-}" key="${5:-}"
  local options=(--silent --show-error --cacert "$TLS_DIR/bank.crt" --max-time 30
    --request "$method" --output "$RESPONSE" --write-out '%{http_code}')
  [[ -z "$TOKEN" ]] || options+=(-H "Authorization: Bearer $TOKEN")
  [[ -z "$data" ]] || options+=(-H "Content-Type: application/json" --data "$data")
  [[ -z "$key" ]] || options+=(-H "Idempotency-Key: $key")
  local actual
  actual="$(curl "${options[@]}" "$url")"
  if [[ "$actual" != "$expected" ]]; then
    echo "$method $url: esperado $expected, recibido $actual"
    jq . "$RESPONSE" || true
    exit 1
  fi
}

token() {
  curl --max-time 15 -fsS --cacert "$TLS_DIR/bank.crt" -u banco-xyz-demo:demo-secret \
    --data-urlencode grant_type=client_credentials --data-urlencode "scope=$1" \
    https://localhost:19000/oauth2/token | jq -er .access_token
}

poll_withdrawal() {
  local id="$1" status="$2"
  for ((i=0; i<60; i++)); do
    request 200 GET "$GATEWAY/gateway/atm/transacciones/$id"
    if jq -e --arg status "$status" '.status == $status' "$RESPONSE" >/dev/null; then return; fi
    sleep 1
  done
  echo "Retiro $id no llego a $status"; exit 1
}

start config-server 18888 false
wait_health http://localhost:18888
start discovery-server 18761 false
wait_health http://localhost:18761
start auth-server 19000 true
wait_health https://localhost:19000

start batch-service 18080 true
start ms-account 18091 true; ACCOUNT_PID="$LAST_PID"
start ms-account 19091 true; ACCOUNT_PID_2="$LAST_PID"
start ms-payment 18092 true; PAYMENT_PID="$LAST_PID"
start ms-payment 19092 true; PAYMENT_PID_2="$LAST_PID"
start ms-customer 18093 true
start ms-customer 19093 true
start bff-web 18081 true
start bff-mobile 18082 true
start bff-atm 18083 true; ATM_PID="$LAST_PID"
start risk-service 18090 true
start notification-service 18085 false
start api-gateway 18084 true
for port in 18080 18091 19091 18092 19092 18093 19093 18081 18082 18083 18090 18084; do
  wait_health "https://localhost:$port"
done
echo "Servicios HTTPS saludables"
sleep 10

TOKEN="$(token 'web.read mobile.read atm.read atm.write risk.read account.read account.write payment.read payment.write customer.read customer.write')"
for route in cuentas payments customers; do
  ready=false
  for ((i=0; i<60; i++)); do
    if curl --max-time 10 -fsS --cacert "$TLS_DIR/bank.crt" -H "Authorization: Bearer $TOKEN" \
      "$GATEWAY/gateway/$route" > "$RESPONSE" 2>/dev/null; then ready=true; break; fi
    sleep 1
  done
  [[ "$ready" == true ]] || { echo "Ruta $route no disponible"; exit 1; }
done
sleep 5
TOKEN="" request 401 GET "$GATEWAY/gateway/cuentas"
READ_TOKEN="$(token account.read)"
TOKEN="$READ_TOKEN" request 403 POST "$GATEWAY/gateway/cuentas" '{}'
request 201 POST "$GATEWAY/gateway/cuentas" '{"accountId":91001,"customerName":"EFT Test","accountType":"ahorro","age":30,"initialBalance":1000}'
request 201 POST "$GATEWAY/gateway/cuentas" '{"accountId":91002,"customerName":"EFT Target","accountType":"corriente","age":25,"initialBalance":0}'
request 200 PUT "$GATEWAY/gateway/cuentas/91001" '{"accountId":91001,"customerName":"EFT Updated","accountType":"ahorro","age":31,"initialBalance":0}'
request 409 POST "$GATEWAY/gateway/cuentas/91001/cierre"

PAYMENT_KEY="$(uuidgen | tr '[:upper:]' '[:lower:]')"
TRANSFER='{"sourceAccountId":91001,"targetAccountId":91002,"amount":100,"description":"EFT transfer"}'
request 201 POST "$GATEWAY/gateway/payments/transfers" "$TRANSFER" "$PAYMENT_KEY"
request 201 POST "$GATEWAY/gateway/payments/transfers" "$TRANSFER" "$PAYMENT_KEY"
request 200 GET "$GATEWAY/gateway/cuentas/91001/saldo"
jq -e '.availableBalance == 900' "$RESPONSE" >/dev/null
request 200 GET "$GATEWAY/gateway/cuentas/91002/saldo"
jq -e '.availableBalance == 100' "$RESPONSE" >/dev/null
request 409 POST "$GATEWAY/gateway/payments/transfers" '{"sourceAccountId":91001,"targetAccountId":91002,"amount":99999,"description":"EFT insufficient"}'
request 404 POST "$GATEWAY/gateway/payments/transfers" '{"sourceAccountId":91001,"targetAccountId":99999,"amount":1,"description":"EFT missing"}'
request 400 POST "$GATEWAY/gateway/payments/deposits" '{"targetAccountId":91002,"amount":0.001}'
request 201 POST "$GATEWAY/gateway/payments/payments" '{"sourceAccountId":91002,"amount":100,"description":"EFT payment"}'
request 200 POST "$GATEWAY/gateway/cuentas/91002/cierre"
echo "Cuentas y pagos idempotentes: OK"

request 201 POST "$GATEWAY/gateway/customers" '{"customerId":91001,"fullName":"EFT Customer","email":"eft@example.com","phone":"+56912345678","segment":"premium","status":"active"}'
request 200 PUT "$GATEWAY/gateway/customers/91001" '{"customerId":91001,"fullName":"EFT Updated","email":"eft@example.com","phone":"+56912345678","segment":"premium","status":"active"}'
request 200 GET "$GATEWAY/gateway/customers/91001"
jq -e '.fullName == "EFT Updated"' "$RESPONSE" >/dev/null
echo "Clientes: OK"

request 200 GET "$GATEWAY/gateway/web/cuentas/91001/dashboard"
request 200 GET "$GATEWAY/gateway/mobile/cuentas/91001/inicio"
request 200 GET "$GATEWAY/gateway/atm/cuentas/91001/saldo"
ATM_READ_TOKEN="$(token atm.read)"
TOKEN="$ATM_READ_TOKEN" request 403 POST "$GATEWAY/gateway/atm/cuentas/91001/retiros" '{"amount":25,"pin":"1234"}'
request 202 POST "$GATEWAY/gateway/atm/cuentas/91001/retiros" '{"amount":25,"pin":"1234"}'
WITHDRAWAL_ID="$(jq -r .transactionId "$RESPONSE")"
poll_withdrawal "$WITHDRAWAL_ID" CONFIRMED
request 200 GET "$GATEWAY/gateway/cuentas/91001/saldo"
jq -e '.availableBalance == 875' "$RESPONSE" >/dev/null

request 200 POST https://localhost:18090/api/risk/admin/failure?enabled=true
request 202 POST "$GATEWAY/gateway/atm/cuentas/91001/retiros" '{"amount":25,"pin":"1234"}'
REJECTED_ID="$(jq -r .transactionId "$RESPONSE")"
poll_withdrawal "$REJECTED_ID" RISK_REJECTED
for ((i=0; i<30; i++)); do
  request 200 GET "$GATEWAY/gateway/cuentas/91001/saldo"
  if jq -e '.availableBalance == 875' "$RESPONSE" >/dev/null; then break; fi
  sleep 1
done
jq -e '.availableBalance == 875' "$RESPONSE" >/dev/null
request 200 POST https://localhost:18090/api/risk/admin/failure?enabled=false

kill "$ATM_PID"; wait "$ATM_PID" || true
start bff-atm 18083 true
wait_health https://localhost:18083
request 200 GET "https://localhost:18083/atm/transacciones/$WITHDRAWAL_ID"
jq -e '.status == "CONFIRMED"' "$RESPONSE" >/dev/null
echo "Saga ATM y reinicio persistente: OK"

for route in cuentas payments customers; do
  for ((i=0; i<12; i++)); do
    curl -fsS --cacert "$TLS_DIR/bank.crt" -H "Authorization: Bearer $TOKEN" \
      --dump-header "$OUTPUT/header-$route-$i.txt" "$GATEWAY/gateway/$route" >/dev/null
  done
  count="$(awk 'tolower($1) == "x-service-instance:" {print $2}' "$OUTPUT/header-$route-"*.txt | sort -u | wc -l | tr -d ' ')"
  [[ "$count" == 2 ]] || { echo "Balanceo de $route alcanzo $count instancias"; exit 1; }
done

kill "$ACCOUNT_PID" "$ACCOUNT_PID_2"
wait "$ACCOUNT_PID" || true
wait "$ACCOUNT_PID_2" || true
RECOVERY_KEY="$(uuidgen | tr '[:upper:]' '[:lower:]')"
RECOVERY_PAYMENT='{"sourceAccountId":91001,"amount":1,"description":"EFT unavailable recovery"}'
request 503 POST https://localhost:18092/api/payments/payments "$RECOVERY_PAYMENT" "$RECOVERY_KEY"
[[ "$(query -c "SELECT status FROM payment_operations WHERE operation_id='$RECOVERY_KEY'")" == PENDING ]]
start ms-account 18091 true
start ms-account 19091 true
wait_health https://localhost:18091
wait_health https://localhost:19091
for ((i=0; i<30; i++)); do
  code="$(curl --silent --show-error --max-time 30 --cacert "$TLS_DIR/bank.crt" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -H "Idempotency-Key: $RECOVERY_KEY" --data "$RECOVERY_PAYMENT" \
    --output "$RESPONSE" --write-out '%{http_code}' https://localhost:18092/api/payments/payments)"
  [[ "$code" == 201 ]] && break
  [[ "$code" == 503 ]] || { echo "Recuperacion recibio $code"; cat "$RESPONSE"; exit 1; }
  sleep 3
done
[[ "$code" == 201 ]]
request 201 POST https://localhost:19092/api/payments/payments "$RECOVERY_PAYMENT" "$RECOVERY_KEY"
request 200 GET https://localhost:18091/api/cuentas/91001/saldo
jq -e '.availableBalance == 874' "$RESPONSE" >/dev/null
echo "Pago pendiente recuperado sin doble cargo: OK"

echo "HTTPS, cuentas, pagos, clientes, tres BFF, saga ATM persistente y balanceo de tres servicios: OK"
