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

# Idle 컨테이너 실행
echo "[INFO] Starting $IDLE container..."
docker-compose -f docker-compose.prod.yml up -d $IDLE

# Health check
echo "[INFO] Checking health of $IDLE..."
for i in {1..15}; do
  sleep 10
  STATUS=$(docker exec $IDLE curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health || echo "000")

  echo "[INFO] Attempt $i - HTTP Status: $STATUS"
  if [ "$STATUS" = "200" ]; then
    echo "[SUCCESS] Health check passed."
    break
  fi

  # healthcheck 실패 시 rollback 보완
  if [ "$i" = 15 ]; then
    echo "[ERROR] Health check failed. Keeping $ACTIVE alive."
    docker-compose -f docker-compose.prod.yml stop $IDLE
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

# nginx 시작
echo "[INFO] Starting nginx..."
docker-compose -f docker-compose.prod.yml up -d nginx

# Reload Nginx
echo "Reloading Nginx to switch traffic..."
docker exec nginx nginx -s reload
echo "Switched traffic to $IDLE. Stopping $ACTIVE..."

# Stop previous active container
echo "[INFO] Stopping previous container: $ACTIVE"
docker-compose -f docker-compose.prod.yml stop $ACTIVE

echo "[SUCCESS] Blue-Green deployment completed successfully!"