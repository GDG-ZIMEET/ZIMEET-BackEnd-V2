#!/bin/bash

# Check if script name is provided
if [ -z "$1" ]; then
  echo "Usage: ./run-k6.sh <script-name>.js"
  echo "Example: ./run-k6.sh circuit-breaker-dynamic-test.js"
  exit 1
fi

SCRIPT_PATH=$1

# Add k6/ prefix if not provided
if [[ "$SCRIPT_PATH" != k6/* ]]; then
  SCRIPT_PATH="k6/$SCRIPT_PATH"
fi

# Check if file exists
if [ ! -f "$SCRIPT_PATH" ]; then
  echo "Error: File $SCRIPT_PATH not found."
  exit 1
fi

# Generate a unique Test ID based on timestamp
TEST_ID=$(date +%s)
echo "🚀 Running k6 test via Docker: $SCRIPT_PATH"
echo "🆔 Test ID: $TEST_ID"
echo "📊 Exporting to Prometheus: http://prometheus:9090"

# Run k6 using Docker
docker run --rm -i \
  --network zimeet_network \
  --add-host=host.docker.internal:host-gateway \
  -v "$(pwd):/scripts" \
  -e BASE_URL=http://host.docker.internal:8080 \
  -e TEST_ID=$TEST_ID \
  -e K6_PROMETHEUS_RW_SERVER_URL=http://prometheus:9090/api/v1/write \
  grafana/k6 run -o experimental-prometheus-rw \
  --tag "test_id=$TEST_ID" \
  "/scripts/$SCRIPT_PATH"




