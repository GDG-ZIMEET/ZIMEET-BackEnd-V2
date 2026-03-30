#!/bin/bash

echo "=================================================="
echo " 🚦 애플리케이션 리소스 정리(2단계) 검증 스크립트 🚦"
echo "=================================================="
echo ""

APP_CONTAINER="backend-blue"
NETWORK="zimeet_network"

echo "=> 1. 기존 컨테이너 및 로그 정리"
docker rm -f $APP_CONTAINER > /dev/null 2>&1
rm -f graceful_shutdown_evidence.log

echo "=> 1.5 최신 코드(로거 포함)로 Docker 이미지 재빌드"
docker-compose -f docker-compose.api-server.yml build backend-blue > /dev/null 2>&1

echo "=> 2. 테스트용 스프링 부트 서버 재시작 (방금 추가한 로거 포함)"
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

echo "=> 3. 서버 부팅 및 WarmUp(예열) 대기 중..."
for i in $(seq 1 30); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/health)
  if [ "$STATUS" = "200" ]; then
    echo "✅ 서버 준비 완료! (${i}초 소요)"
    break
  fi
  sleep 1
done

if [ "$STATUS" != "200" ]; then
  echo "❌ 서버 시작 실패. 로그를 확인하세요."
  docker logs $APP_CONTAINER
  exit 1
fi

echo "   => WarmUpListener 통과 여부는 이후 로그 파일에서 확인 가능"

echo "=> 4. 정상적인 결제 요청(3초 슬립 등) 발사"
# (서버에 트래픽 인입)
curl -s -X POST http://localhost:8080/api/test/payment/ready \
  -H "Content-Type: application/json" \
  -d '{"orderId": "FINAL-SUCCESS-TEST", "amount": 10000}' > /dev/null &

sleep 1

echo "=> 5. SIGTERM 전송 (우아한 종료 시작)"
docker stop -t 20 $APP_CONTAINER > /dev/null

echo "=> 6. 종료 완료! 컨테이너 로그 추출 (Step 0 ~ Step 2 증거 추출)"
# 최신 100줄의 로그를 추출하여 파일로 저장
docker logs $APP_CONTAINER 2>&1 | tail -n 150 > graceful_shutdown_evidence.log

echo "=================================================="
echo " 🎉 검증 완료! [graceful_shutdown_evidence.log] 파일이 생성되었습니다."
echo " 문서/블로그용으로 해당 로그 파일 내용을 복사해서 사용하세요!"
echo "=================================================="
