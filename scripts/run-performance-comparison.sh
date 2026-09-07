#!/usr/bin/env bash
set -euo pipefail

OUTPUT_DIR="output"
RESULTS_FILE="${OUTPUT_DIR}/performance-comparison.csv"

mkdir -p "${OUTPUT_DIR}"
printf 'scenario,chunk_size,thread_pool_size,partition_grid_size,duration_seconds\n' > "${RESULTS_FILE}"

run_scenario() {
  local scenario="$1"
  local chunk_size="$2"
  local thread_pool_size="$3"
  local partition_grid_size="$4"

  local start
  local end
  local duration

  start=$(date +%s)
  ./mvnw -q spring-boot:run \
    -Dspring-boot.run.arguments="--batch.chunk-size=${chunk_size} --batch.thread-pool-size=${thread_pool_size} --batch.partition-grid-size=${partition_grid_size}"
  end=$(date +%s)
  duration=$((end - start))

  printf '%s,%s,%s,%s,%s\n' "${scenario}" "${chunk_size}" "${thread_pool_size}" "${partition_grid_size}" "${duration}" >> "${RESULTS_FILE}"
}

run_scenario "baseline" 5 3 3
run_scenario "small_chunks" 3 3 3
run_scenario "more_parallelism" 10 4 4

echo "Performance comparison written to ${RESULTS_FILE}"
