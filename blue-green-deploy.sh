#!/bin/bash

set -e

BLUE_CONTAINER="backend-blue"
GREEN_CONTAINER="backend-green"
CONF_PATH="./nginx/backend_upstream.conf"

# 롤백 함수 정의
rollback() {
  local reason=$1
  echo "[경고] 롤백 시작: $reason"
  
  # 새 컨테이너 로그 출력
  echo "[정보] 실패한 컨테이너 로그 (마지막 50줄):"
  docker logs $IDLE --tail 50 2>&1 || true
  
  # 새 컨테이너 중지
  echo "[정보] $IDLE 컨테이너 중지 중..."
  docker-compose -f docker-compose.prod.yml stop $IDLE 2>&1 || true
  
  # 이전 컨테이너가 여전히 실행 중인지 확인
  if [ -z "$(docker ps -q -f name=$ACTIVE -f status=running)" ]; then
    echo "[긴급] 이전 컨테이너도 중지됨. 재시작 시도..."
    docker-compose -f docker-compose.prod.yml up -d $ACTIVE
    sleep 10
    
    # 이전 컨테이너 헬스체크
    for i in {1..10}; do
      ACTIVE_STATUS=$(docker exec $ACTIVE curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/health 2>/dev/null || echo "000")
      if [ "$ACTIVE_STATUS" = "200" ]; then
        echo "[성공] 이전 컨테이너 복구 완료"
        break
      fi
      sleep 3
    done
  fi
  
  # Nginx 설정 복구
  if [ -f "${CONF_PATH}.backup" ]; then
    echo "[정보] Nginx 설정 복구 중..."
    mv ${CONF_PATH}.backup $CONF_PATH
    docker exec nginx nginx -s reload 2>&1 || true
  fi
  
  echo "[완료] 롤백 완료. 활성 컨테이너: $ACTIVE"
  exit 1
}

CURRENT_CONTAINER=$(docker ps --filter "name=$BLUE_CONTAINER" --filter "status=running" -q)

if [ -n "$CURRENT_CONTAINER" ]; then
  ACTIVE=$BLUE_CONTAINER
  IDLE=$GREEN_CONTAINER
else
  ACTIVE=$GREEN_CONTAINER
  IDLE=$BLUE_CONTAINER
fi

echo "========================================="
echo "배포 시작: $(date '+%Y-%m-%d %H:%M:%S')"
echo "========================================="
echo "현재 활성 컨테이너: $ACTIVE"
echo "배포 대상 컨테이너: $IDLE"

echo "[정보] 기존 $IDLE 컨테이너 제거 중 입니다."
docker rm -f $IDLE 2>/dev/null || true

echo "[정보] $IDLE 컨테이너 시작 중 입니다."
docker-compose -f docker-compose.prod.yml up -d $IDLE

# 컨테이너 시작 대기
echo "[정보] 컨테이너 시작 대기 중..."
sleep 5

# 컨테이너 상태 검증
echo "[정보] 컨테이너 상태 검증 중..."
CONTAINER_STATUS=$(docker inspect -f '{{.State.Status}}' $IDLE 2>/dev/null || echo "not_found")
if [ "$CONTAINER_STATUS" != "running" ]; then
  rollback "컨테이너가 실행 중이 아닙니다: $CONTAINER_STATUS"
fi

# 네트워크 연결 확인
NETWORK_CONNECTED=$(docker inspect -f '{{range $key, $value := .NetworkSettings.Networks}}{{$key}}{{end}}' $IDLE 2>/dev/null || echo "")
if [ -z "$NETWORK_CONNECTED" ]; then
  rollback "컨테이너가 네트워크에 연결되지 않았습니다"
fi

echo "[성공] 컨테이너 상태: $CONTAINER_STATUS, 네트워크: $NETWORK_CONNECTED"

# 지수 백오프 헬스체크
echo "[정보] $IDLE 헬스체크 진행 중 입니다."
RETRY_COUNT=0
MAX_RETRIES=30
INITIAL_WAIT=5
MAX_WAIT=30

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
  # 지수 백오프 계산 (5초 시작, 2초씩 증가, 최대 30초)
  WAIT_TIME=$((INITIAL_WAIT + RETRY_COUNT * 2))
  if [ $WAIT_TIME -gt $MAX_WAIT ]; then
    WAIT_TIME=$MAX_WAIT
  fi
  
  sleep $WAIT_TIME
  
  # 헬스체크 실행
  STATUS=$(docker exec $IDLE curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/health 2>/dev/null || echo "000")
  
  echo "[정보] 시도 $((RETRY_COUNT + 1))/$MAX_RETRIES - HTTP 상태: $STATUS (${WAIT_TIME}초 대기)"
  
  if [ "$STATUS" = "200" ]; then
    echo "[성공] 헬스체크 통과."
    break
  fi
  
  RETRY_COUNT=$((RETRY_COUNT + 1))
done

if [ $RETRY_COUNT -eq $MAX_RETRIES ]; then
  rollback "헬스체크 최대 재시도 횟수 초과"
fi

# Nginx 설정 백업
echo "[정보] Nginx upstream 설정 전환 입니다."
cp $CONF_PATH ${CONF_PATH}.backup

# Nginx 설정 변경
if [ "$IDLE" = "$GREEN_CONTAINER" ]; then
  echo "server $GREEN_CONTAINER:8080;" | sudo tee $CONF_PATH > /dev/null
else
  echo "server $BLUE_CONTAINER:8080;" | sudo tee $CONF_PATH > /dev/null
fi

# Nginx 설정 검증
echo "[정보] Nginx 설정 검증 중..."
if ! docker exec nginx nginx -t 2>&1; then
  mv ${CONF_PATH}.backup $CONF_PATH
  rollback "Nginx 설정 검증 실패"
fi

# Nginx 재로드
echo "[정보] Nginx 재로드하여 트래픽 전환 입니다."
if ! docker exec nginx nginx -s reload 2>&1; then
  mv ${CONF_PATH}.backup $CONF_PATH
  docker exec nginx nginx -s reload 2>&1 || true
  rollback "Nginx 재로드 실패"
fi

# 트래픽 전환 검증
echo "[정보] 트래픽 전환 검증 중..."
VERIFY_SUCCESS=false
for i in {1..5}; do
  NGINX_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost/api/health 2>/dev/null || echo "000")
  echo "[정보] Nginx 헬스체크 시도 $i/5 - HTTP 상태: $NGINX_STATUS"
  
  if [ "$NGINX_STATUS" = "200" ]; then
    echo "[성공] Nginx를 통한 헬스체크 성공"
    VERIFY_SUCCESS=true
    break
  fi
  sleep 2
done

if [ "$VERIFY_SUCCESS" = false ]; then
  # Nginx 설정 복구
  if [ "$ACTIVE" = "$GREEN_CONTAINER" ]; then
    echo "server $GREEN_CONTAINER:8080;" | sudo tee $CONF_PATH > /dev/null
  else
    echo "server $BLUE_CONTAINER:8080;" | sudo tee $CONF_PATH > /dev/null
  fi
  docker exec nginx nginx -s reload 2>&1 || true
  rollback "트래픽 전환 검증 실패"
fi

# 안전 대기 시간
echo "[정보] 안정화 대기 중 (30초)..."
sleep 30

# 백업 파일 정리
rm -f ${CONF_PATH}.backup

echo "[정보] $IDLE로 트래픽 전환 완료. $ACTIVE Graceful Shutdown 준비 중..."

# 이전 컨테이너의 활성 연결 확인 및 대기
echo "[정보] $ACTIVE 컨테이너의 활성 연결 확인 중..."
ACTIVE_CONNECTIONS=$(docker exec $ACTIVE sh -c "netstat -an 2>/dev/null | grep :8080 | grep ESTABLISHED | wc -l" 2>/dev/null || echo "0")
ACTIVE_CONNECTIONS=$(echo $ACTIVE_CONNECTIONS | tr -d '[:space:]')

echo "[정보] 현재 활성 연결 수: $ACTIVE_CONNECTIONS"

if [ "$ACTIVE_CONNECTIONS" -gt 0 ]; then
  echo "[정보] 활성 연결 종료 대기 중 (최대 60초)..."
  for i in {1..12}; do
    sleep 5
    ACTIVE_CONNECTIONS=$(docker exec $ACTIVE sh -c "netstat -an 2>/dev/null | grep :8080 | grep ESTABLISHED | wc -l" 2>/dev/null || echo "0")
    ACTIVE_CONNECTIONS=$(echo $ACTIVE_CONNECTIONS | tr -d '[:space:]')
    echo "[정보] 대기 중... 남은 연결 수: $ACTIVE_CONNECTIONS (${i}번째 체크)"
    
    if [ "$ACTIVE_CONNECTIONS" -eq 0 ]; then
      echo "[성공] 모든 활성 연결 종료됨"
      break
    fi
  done
  
  if [ "$ACTIVE_CONNECTIONS" -gt 0 ]; then
    echo "[경고] 60초 대기 후에도 $ACTIVE_CONNECTIONS 개의 연결이 남아있음. Graceful Shutdown 진행..."
  fi
else
  echo "[성공] 활성 연결 없음. 즉시 종료 가능"
fi

echo "[정보] 이전 활성 컨테이너 Graceful Shutdown 시작: $ACTIVE"
echo "[정보] Spring Boot가 진행 중인 요청을 완료할 때까지 최대 60초 대기..."
docker-compose -f docker-compose.prod.yml stop $ACTIVE

echo "========================================="
echo "[성공] 블루-그린 배포 완료!"
echo "[정보] 현재 활성 컨테이너: $IDLE"
echo "배포 완료: $(date '+%Y-%m-%d %H:%M:%S')"
echo "========================================="