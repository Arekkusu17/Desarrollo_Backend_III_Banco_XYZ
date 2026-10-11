#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUTPUT_DIR="$REPO_ROOT/output/batch-validation"
JAR="$REPO_ROOT/batch-service/target/batch-service-0.0.1-SNAPSHOT.jar"
TEST_HOST="${BATCH_TEST_HOST:-localhost}"
TEST_PORT="${BATCH_TEST_PORT:-15432}"
TEST_DATABASE="${BATCH_TEST_DATABASE:-banco_eft_tests}"
TEST_USER="${BATCH_TEST_USER:-banco}"
TEST_PASSWORD="${BATCH_TEST_PASSWORD:-banco}"
RUN_ID="$(date +%Y%m%d%H%M%S)"

[[ "$TEST_DATABASE" =~ ^[a-zA-Z0-9_]+$ ]] || { echo "Nombre de base invalido"; exit 1; }
[[ "$TEST_DATABASE" != "banco_xyz" ]] || { echo "Usar una base de pruebas separada de banco_xyz"; exit 1; }
[[ -f "$JAR" ]] || { echo "Primero ejecutar ./mvnw -pl batch-service -am package"; exit 1; }
command -v psql >/dev/null
mkdir -p "$OUTPUT_DIR"
printf 'scenario,threads,partitions,job_duration_seconds\n' > "$OUTPUT_DIR/performance-comparison.csv"

query() {
  PGPASSWORD="$TEST_PASSWORD" psql -X -v ON_ERROR_STOP=1 -h "$TEST_HOST" -p "$TEST_PORT" \
    -U "$TEST_USER" -d "$TEST_DATABASE" "$@"
}

run_scenario() {
  local scenario="$1" threads="$2" partitions="$3"
  local schema="eft_${scenario}_${RUN_ID}"
  query -c "CREATE SCHEMA $schema" >/dev/null
  java -jar "$JAR" \
    --spring.main.web-application-type=none --spring.cloud.config.enabled=false \
    --eureka.client.enabled=false --spring.cloud.discovery.enabled=false \
    "--spring.datasource.url=jdbc:postgresql://$TEST_HOST:$TEST_PORT/$TEST_DATABASE?currentSchema=$schema" \
    "--spring.datasource.username=$TEST_USER" "--spring.datasource.password=$TEST_PASSWORD" \
    --batch.exit-after-run=true "--batch.run-key=$RUN_ID-$scenario" --legacy.data.week=eft \
    "--batch.thread-pool-size=$threads" "--batch.partition-grid-size=$partitions" \
    "--bank.annual.report-output=$OUTPUT_DIR/$scenario-annual.csv" \
    > "$OUTPUT_DIR/$scenario.log" 2>&1

  local duration
  duration="$(PGOPTIONS="-c search_path=$schema" query -At -c \
    "SELECT ROUND(SUM(EXTRACT(EPOCH FROM (end_time-start_time)))::numeric,3) FROM batch_job_execution WHERE status='COMPLETED'")"
  printf '%s,%s,%s,%s\n' "$scenario" "$threads" "$partitions" "$duration" >> "$OUTPUT_DIR/performance-comparison.csv"
  for table in daily_transaction_summary monthly_interest_results annual_statement_entries rejected_records; do
    local columns order
    case "$table" in
      annual_statement_entries)
        columns="account_id,transaction_date,transaction_type,amount,description,audit_flag"
        order="account_id,transaction_date,transaction_type,amount,description" ;;
      rejected_records)
        columns="process_name,record_key,reason,payload,fingerprint"
        order="process_name,record_key,reason,payload" ;;
      daily_transaction_summary) columns="*"; order="transaction_id" ;;
      monthly_interest_results) columns="*"; order="account_id" ;;
    esac
    PGOPTIONS="-c search_path=$schema" query -c \
      "COPY (SELECT $columns FROM $table ORDER BY $order) TO STDOUT WITH CSV HEADER" \
      > "$OUTPUT_DIR/$scenario-$table.csv"
  done
}

run_scenario sequential 1 1
run_scenario parallel 3 3
for table in daily_transaction_summary monthly_interest_results annual_statement_entries rejected_records; do
  cmp "$OUTPUT_DIR/sequential-$table.csv" "$OUTPUT_DIR/parallel-$table.csv"
done
cmp "$OUTPUT_DIR/sequential-annual.csv" "$OUTPUT_DIR/parallel-annual.csv"
echo "Resultados y rechazos equivalentes; mediciones en $OUTPUT_DIR/performance-comparison.csv"
