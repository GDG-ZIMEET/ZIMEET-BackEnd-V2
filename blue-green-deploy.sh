#!/bin/bash

set -e

BLUE_CONTAINER="backend-blue"
GREEN_CONTAINER="backend-green"

# 현재 실행 중인 컨테이너 확인 (running 또는 created 상태 모두 확인)
BLUE_RUNNING=$(docker ps -a --filter "name=$BLUE_CONTAINER" --filter "status=running" -q)
GREEN_RUNNING=$(docker ps -a --filter "name=$GREEN_CONTAINER" --filter "status=running" -q)

if [ -n "$BLUE_RUNNING" ]; then
  ACTIVE=$BLUE_CONTAINER
  IDLE=$GREEN_CONTAINER
elif [ -n "$GREEN_RUNNING" ]; then
  ACTIVE=$GREEN_CONTAINER
  IDLE=$BLUE_CONTAINER
else
  # 둘 다 없으면 green을 active로 가정하고 blue로 배포
  ACTIVE=$GREEN_CONTAINER
  IDLE=$BLUE_CONTAINER
fi

echo "Active container: $ACTIVE"
echo "Deploying to: $IDLE"

echo "[INFO] Removing old $IDLE container if exists..."
docker rm -f $IDLE 2>/dev/null || true

echo "[INFO] Starting $IDLE container..."
docker-compose -f docker-compose.prod.yml up -d $IDLE

echo "[INFO] Waiting for container to stabilize..."
sleep 10

echo "[INFO] Checking health of $IDLE..."
for i in {1..20}; do
  sleep 10
  
  if ! docker ps --filter "name=$IDLE" --filter "status=running" -q | grep -q .; then
    echo "[ERROR] Container $IDLE is not running!"
    docker logs $IDLE --tail 50
    echo "[ERROR] Health check failed. Rolling back..."
    docker-compose -f docker-compose.prod.yml stop $IDLE
    exit 1
  fi
  
  STATUS=$(docker exec $IDLE curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health || echo "000")
  echo "[INFO] Attempt $i - HTTP Status: $STATUS"
  if [ "$STATUS" = "200" ]; then
    echo "[SUCCESS] Health check passed."
    break
  fi
  if [ "$i" = 20 ]; then
    echo "[ERROR] Health check failed after 20 attempts"
    docker logs $IDLE --tail 50
    docker-compose -f docker-compose.prod.yml stop $IDLE
    exit 1
  fi
done

CONF_PATH="./nginx/backend_upstream.conf"

if [ "$IDLE" = "$GREEN_CONTAINER" ]; then
  echo "server $GREEN_CONTAINER:8080;" | sudo tee $CONF_PATH > /dev/null
else
  echo "server $BLUE_CONTAINER:8080;" | sudo tee $CONF_PATH > /dev/null
fi

echo "[INFO] Starting nginx..."
docker-compose -f docker-compose.prod.yml up -d nginx

echo "[INFO] Reloading Nginx..."
docker exec nginx nginx -s reload

echo "[INFO] Stopping previous container: $ACTIVE"
docker-compose -f docker-compose.prod.yml stop $ACTIVE

echo "[SUCCESS] Blue-Green deployment completed successfully!"
echo "[INFO] Current active: $IDLE"