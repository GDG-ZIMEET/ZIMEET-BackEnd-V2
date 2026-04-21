#!/bin/bash

WEBHOOK_URL="https://discord.com/api/webhooks/1381215630325317632/3b7vst9Skuxu9a6vujhsx28_UjcX7QI3kQze4c0FdfWstCksrHzAflGKaZwd1pSCfKKt"  # 여기에 본인의 웹훅 URL 입력
ERRORS=""

for name in redis1 mongodb1 nginx; do
    STATUS=$(docker inspect -f '{{.State.Health.Status}}' $name 2>/dev/null)

    if [ -z "$STATUS" ] || [[ "$STATUS" != "healthy" ]]; then
        ERRORS+="❌ 컨테이너 ${name} 상태 이상 ❌: ${STATUS:-not found}\n"
    fi
done

# backend-blue / backend-green 둘 중 하나만 healthy면 정상
BLUE_STATUS=$(docker inspect -f '{{.State.Health.Status}}' backend-blue 2>/dev/null)
GREEN_STATUS=$(docker inspect -f '{{.State.Health.Status}}' backend-green 2>/dev/null)

if [[ "$BLUE_STATUS" != "healthy" && "$GREEN_STATUS" != "healthy" ]]; then
    ERRORS+="❌ backend-blue / backend-green 둘 다 비정상 ❌: blue=$BLUE_STATUS, green=$GREEN_STATUS\n"
fi


if [ ! -z "$ERRORS" ]; then
  curl -H "Content-Type: application/json" \
       -X POST \
       -d "{\"content\": \"⚠️ 지밋 서버 컨테이너 상태 문제 발생:\n${ERRORS}\"}" \
       $WEBHOOK_URL
fi
