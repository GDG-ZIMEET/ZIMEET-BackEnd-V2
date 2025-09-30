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

echo "현재 활성 컨테이너: $ACTIVE"
echo "배포 대상 컨테이너: $IDLE"

echo "[정보] 기존 $IDLE 컨테이너 제거 중 입니다."
docker rm -f $IDLE 2>/dev/null || true

echo "[정보] $IDLE 컨테이너 시작 중 입니다."
docker-compose -f docker-compose.prod.yml up -d $IDLE

# Health check
echo "[정보] $IDLE 헬스체크 진행 중 입니다."
for i in {1..40}; do
  sleep 20
  STATUS=$(docker exec $IDLE curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/health || echo "000")
  echo "[정보] 시도 $i - HTTP 상태: $STATUS"
  if [ "$STATUS" = "200" ]; then
    echo "[성공] 헬스체크 통과."
    break
  fi
  if [ "$i" = 40 ]; then
    echo "[오류] 헬스체크 실패. 롤백을 진행합니다."
    docker-compose -f docker-compose.prod.yml stop $IDLE
    exit 1
  fi
done



echo "[정보] Nginx upstream 설정 전환 입니다."
CONF_PATH="./nginx/backend_upstream.conf"
if [ "$IDLE" = "$GREEN_CONTAINER" ]; then
  echo "server $GREEN_CONTAINER:8080;" | sudo tee $CONF_PATH > /dev/null
else
  echo "server $BLUE_CONTAINER:8080;" | sudo tee $CONF_PATH > /dev/null
fi

echo "[정보] Nginx 재로드하여 트래픽 전환 입니다."
docker exec nginx nginx -s reload

echo "[정보] $IDLE로 트래픽 전환 완료. $ACTIVE 중지 입니다."

echo "[정보] 이전 활성 컨테이너 중지: $ACTIVE"
docker-compose -f docker-compose.prod.yml stop $ACTIVE

echo "[성공] 블루-그린 배포 완료!"
echo "[정보] 현재 활성 컨테이너: $IDLE"