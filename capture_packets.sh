#!/bin/bash

echo "=================================================="
echo " 🚦 TCP 패킷 캡처 (RST vs FIN) 교정 스크립트 🚦"
echo "=================================================="
echo ""

APP_CONTAINER="backend-blue"
CLIENT_CONTAINER="tcp-sniper"
NETWORK="zimeet_network"

rm -f before_graceful.pcap after_graceful.pcap

# ──────────────────────────────────────────────────────
# 공통 함수: sniper 컨테이너 준비
# ──────────────────────────────────────────────────────
start_sniper() {
  docker rm -f $CLIENT_CONTAINER > /dev/null 2>&1
  docker run -d --name $CLIENT_CONTAINER --network $NETWORK nicolaka/netshoot sleep 3600 > /dev/null 2>&1
  echo "=> 스나이퍼 컨테이너 준비 완료 🎯"
}

# ──────────────────────────────────────────────────────
# 공통 함수: 서버 부팅 대기 (헬스체크 통과까지)
# ──────────────────────────────────────────────────────
wait_for_server() {
  echo "=> 서버 부팅 대기 중..."
  for i in $(seq 1 30); do
    STATUS=$(docker exec $CLIENT_CONTAINER curl -s -o /dev/null -w "%{http_code}" http://$APP_CONTAINER:8080/api/health 2>/dev/null)
    if [ "$STATUS" = "200" ]; then
      echo "✅ 서버 준비 완료! (${i}초 소요)"
      return
    fi
    sleep 1
  done
  echo "❌ 서버가 30초 내에 응답하지 않았습니다."
  exit 1
}

# ──────────────────────────────────────────────────────
# 🔴 테스트 1: SIGKILL + SO_LINGER=0 (RST 패킷 강제 유발)
# ──────────────────────────────────────────────────────
echo "--------------------------------------------------"
echo " 🔴 테스트 1: SIGKILL + SO_LINGER=0 (RST 패킷 강제 유발)"
echo "--------------------------------------------------"

# [핵심] rst-test 프로파일 (SO_LINGER=0 적용)로 서버 재빌드
echo "=> rst-test 프로파일(SO_LINGER=0)로 서버 재시작 중..."
docker rm -f $APP_CONTAINER > /dev/null 2>&1
docker run -d \
  --name $APP_CONTAINER \
  --network $NETWORK \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=local,rst-test \
  -e JAVA_TOOL_OPTIONS="-Xmx512m -Xms512m" \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=3307 \
  -e DB_USERNAME=root \
  -e DB_PASSWORD= \
  -e MONGODB_HOST=host.docker.internal \
  -e REDIS_HOST=host.docker.internal \
  --add-host=host.docker.internal:host-gateway \
  zimeet-backend-v2-backend-blue > /dev/null 2>&1

# [중요] 깨끗한 캡처를 위해 녹화 직전에 스나이퍼 배치
start_sniper
wait_for_server

# [핵심] 헬스체크 패킷의 잔상이 남지 않도록 3초간 침묵 후 녹화 시작
echo "=> 헬스체크 잔상 정리 및 캡처 준비 중 (3초)..."
sleep 3

# tcpdump 시작
echo "=> 🫧 깨끗한 패킷 녹화 시작..."
docker exec -d $CLIENT_CONTAINER tcpdump -U -i any host $APP_CONTAINER and port 8080 -w /tmp/before.pcap
sleep 2

# [RST 유발 핵심] slow_sender.py를 sniper 컨테이너로 복사 후 실행
# Content-Length=10000 선언, 실제 바디는 50바이트만 전송 → 서버 수신 버퍼에 미읽은 데이터 적재
echo "=> slow_sender.py 업로드 및 실행 (부분 바디 전송으로 수신 버퍼 점유)..."
docker cp ./slow_sender.py $CLIENT_CONTAINER:/tmp/slow_sender.py
docker exec -d $CLIENT_CONTAINER python3 /tmp/slow_sender.py $APP_CONTAINER

# slow_sender가 헤더 + 부분 바디를 서버에 전달할 시간 확보
sleep 1

# 서버 강제 종료 → 수신 버퍼에 미읽은 데이터 남아있음 → 리눅스 커널이 RST 발사!
echo "=> 서버 즉시 강제 종료 (SIGKILL) 💣 → RST 발생 예상"
docker kill $APP_CONTAINER > /dev/null

# RST 패킷 수신 대기
sleep 3

# SIGINT로 tcpdump 정상 종료 → pcap flush
echo "=> 녹화 종료 및 파일 저장..."
docker exec $CLIENT_CONTAINER kill -2 $(docker exec $CLIENT_CONTAINER pgrep tcpdump) 2>/dev/null
sleep 1
docker cp $CLIENT_CONTAINER:/tmp/before.pcap ./before_graceful.pcap
echo "✅ [before_graceful.pcap] 저장 완료!"

# ──────────────────────────────────────────────────────
# 🟢 테스트 2: Graceful Shutdown 적용 (FIN 패킷 캡처)
# ──────────────────────────────────────────────────────
echo ""
echo "--------------------------------------------------"
echo " 🟢 테스트 2: SIGTERM 우아한 종료 (FIN 패킷 예측)"
echo "--------------------------------------------------"

# [수정] docker-compose 대신 직접 build한 이미지로 재시작 (정상 local 프로필)
# rst-test 프로파일을 명시적으로 제거하여 SO_LINGER=0 옵션을 비활성화합니다.
echo "=> 정상 local 프로필로 서버 재시작 중 (Graceful Shutdown 활성화)..."
docker rm -f $APP_CONTAINER > /dev/null 2>&1
docker run -d \
  --name $APP_CONTAINER \
  --network $NETWORK \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=local \
  -e JAVA_TOOL_OPTIONS="-Xmx512m -Xms512m" \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=3307 \
  -e DB_USERNAME=root \
  -e DB_PASSWORD= \
  -e MONGODB_HOST=host.docker.internal \
  -e REDIS_HOST=host.docker.internal \
  --add-host=host.docker.internal:host-gateway \
  zimeet-backend-v2-backend-blue > /dev/null 2>&1

wait_for_server

# tcpdump 재시작
echo "=> 패킷 녹화 재시작..."
docker exec -d $CLIENT_CONTAINER tcpdump -U -i any host $APP_CONTAINER and port 8080 -w /tmp/after.pcap
sleep 1

# [복구] 정상적인 curl 요청 발사 (200 OK와 FIN을 동시에 캡처하기 위함)
echo "=> 정상 결제 요청 발사 (200 OK 기대)..."
docker exec -d $CLIENT_CONTAINER curl -s -X POST http://$APP_CONTAINER:8080/api/test/payment/ready \
  -H "Content-Type: application/json" \
  -d '{"orderId": "FINAL-SUCCESS-TEST", "amount": 10000}'

# HTTP 요청이 확실히 전달될 시간 확보
sleep 1
echo "=> 서버 우아한 종료 (SIGTERM 발송) 🕊️"
docker stop -t 20 $APP_CONTAINER > /dev/null

# [핵심] 결제 로직(3초)이 수행되고 200 OK 응답이 나간 뒤 FIN이 찍히는 시간 충분히 확보
sleep 6

echo "=> 녹화 종료 및 파일 저장..."
docker exec $CLIENT_CONTAINER kill -2 $(docker exec $CLIENT_CONTAINER pgrep tcpdump) 2>/dev/null
sleep 1
docker cp $CLIENT_CONTAINER:/tmp/after.pcap ./after_graceful.pcap
echo "✅ [after_graceful.pcap] 저장 완료!"

# 정리
echo ""
echo "=> 스나이퍼 철수 및 서버 정상 복구 중..."
docker rm -f $CLIENT_CONTAINER > /dev/null 2>&1
# 마지막은 다시 docker-compose로 원상복구
docker-compose -f docker-compose.api-server.yml up -d $APP_CONTAINER > /dev/null 2>&1

echo "=================================================="
echo " 🎉 캡처 완료! Wireshark 필터 가이드:"
echo ""
echo " [before_graceful.pcap]"
echo "  - 필터 없이 전체 보기 → 응답(HTTP 200) 없이 소켓 종료 확인"
echo "  - tcp.flags.reset == 1 → RST 패킷 (있으면 RST, 없으면 FIN으로 종료)"
echo ""
echo " [after_graceful.pcap]"
echo "  - 필터 없이 전체 보기 → HTTP 200 응답 후 FIN 발생 확인"
echo "  - tcp.flags.fin == 1   → FIN 패킷 (3초 이후에 등장해야 정상)"
echo "=================================================="
