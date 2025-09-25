#!/bin/bash

set -e

BLUE_CONTAINER="backend-blue"
GREEN_CONTAINER="backend-green"

# 현재 실행 중인 컨테이너 확인
CURRENT_CONTAINER=$(docker ps --filter "name=$BLUE_CONTAINER" --filter "status=running" -q)

if [ -n "$CURRENT_CONTAINER" ]; then
  ACTIVE=$BLUE_CONTAINER
  IDLE=$GREEN_CONTAINER
else
  ACTIVE=$GREEN_CONTAINER
  IDLE=$BLUE_CONTAINER
fi

echo "Active container: $ACTIVE"
echo "Deploying to: $IDLE"

# 이전 중지된 동일 이름 컨테이너 제거
echo "[INFO] Removing old $IDLE container if exists..."
docker rm -f $IDLE 2>/dev/null || true

# Idle 컨테이너 빌드 및 실행
echo "[INFO] Starting $IDLE container..."
docker-compose up -d --build $IDLE

# Health check
echo "[INFO] Checking health of $IDLE..."
for i in {1..10}; do
  sleep 8
  STATUS=$(docker exec $IDLE curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/health || echo "000")
  echo "[INFO] Attempt $i - HTTP Status: $STATUS"
  if [ "$STATUS" = "200" ]; then
    echo "[SUCCESS] Health check passed."
    break
  fi
  if [ "$i" = 10 ]; then
    echo "[ERROR] Health check failed. Rolling back..."
    docker-compose stop $IDLE
    exit 1
  fi
done

# nginx upstream 설정 전환
CONF_PATH="./nginx/backend_upstream.conf"

if [ "$IDLE" = "$GREEN_CONTAINER" ]; then
  echo "server $GREEN_CONTAINER:8080;" | sudo tee $CONF_PATH > /dev/null
else
  echo "server $BLUE_CONTAINER:8080;" | sudo tee $CONF_PATH > /dev/null
fi

# Reload Nginx ( 트래픽 자동 전환됨 )
echo "Reloading Nginx to switch traffic..."
docker exec nginx nginx -s reload
echo "Switched traffic to $IDLE. Stopping $ACTIVE..."

# Stop previous active container
echo "[INFO] Stopping previous container: $ACTIVE"
docker-compose stop $ACTIVE
