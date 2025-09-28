#!/bin/bash

set -e

BLUE_CONTAINER="backend-blue"
GREEN_CONTAINER="backend-green"

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

echo "[INFO] Removing old $IDLE container if exists..."
docker rm -f $IDLE 2>/dev/null || true

echo "[INFO] Starting $IDLE container..."
docker-compose -f docker-compose.prod.yml up -d $IDLE

echo "[INFO] Waiting for container to stabilize..."
sleep 5

echo "[INFO] Container status:"
docker ps -a --filter "name=$IDLE"

echo "[INFO] Container logs:"
docker logs $IDLE --tail 20

#echo "[INFO] Checking health of $IDLE..."
#for i in {1..15}; do
#  sleep 10
#
#  if ! docker ps --filter "name=$IDLE" --filter "status=running" -q | grep -q .; then
#    echo "[ERROR] $IDLE exited!"
#    docker logs $IDLE --tail 50
#    exit 1
#  fi
#
#  STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health || echo "000")
#  echo "[INFO] Attempt $i - HTTP Status: $STATUS"
#
#  if [ "$STATUS" = "200" ]; then
#    echo "[SUCCESS] Health check passed"
#    break
#  fi
#
#  if [ "$i" = 15 ]; then
#    echo "[ERROR] Health check failed after 15 attempts"
#    docker logs $IDLE --tail 50
#    exit 1
#  fi
#done

CONF_PATH="./nginx/backend_upstream.conf"

if [ "$IDLE" = "$GREEN_CONTAINER" ]; then
  echo "server $GREEN_CONTAINER:8080;" | sudo tee $CONF_PATH > /dev/null
else
  echo "server $BLUE_CONTAINER:8080;" | sudo tee $CONF_PATH > /dev/null
fi

echo "[INFO] Starting nginx..."
docker-compose -f docker-compose.prod.yml up -d nginx

echo "Reloading Nginx to switch traffic..."
docker exec nginx nginx -s reload
echo "Switched traffic to $IDLE. Stopping $ACTIVE..."

echo "[INFO] Stopping previous container: $ACTIVE"
docker-compose -f docker-compose.prod.yml stop $ACTIVE

echo "[SUCCESS] Blue-Green deployment completed successfully!"
