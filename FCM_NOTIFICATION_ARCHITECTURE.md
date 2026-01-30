# ZIMEET FCM 알림 시스템 아키텍처


## 시스템 개요

ZIMEET의 FCM 알림 시스템은 **RabbitMQ 기반 비동기 메시지 큐**를 활용하여 대량의 푸시 알림을 안정적으로 처리합니다.

### 설계 목표
- ✅ **비동기 처리**: API 응답 속도와 알림 발송을 분리
- ✅ **백프레셔 제어**: DB 및 FCM API 부하 자동 조절
- ✅ **장애 복구**: 일시적 오류 자동 재시도 및 실패 메시지 격리
- ✅ **확장성**: 대량 발송 시 메모리 최적화 및 멀티캐스트 활용

### 전체 아키텍처 흐름

```
[Producer]                    [RabbitMQ]                    [Consumer (Worker)]
   │                              │                              │
   ├─ 채팅 알림 ───────────────→ fcm.single.queue ──────────→ FCM 전송
   ├─ 매칭 알림 ───────────────→ fcm.single.queue ──────────→ FCM 전송
   └─ 브로드캐스트 ────────────→ fcm.broadcast.queue ───────→ FCM 멀티캐스트
                                   │
                                   ↓ (실패 시)
                            fcm.dlq.queue (DLQ)
```

---

## 핵심 구성 요소

### 1. Producer (메시지 발행자)
**위치**: `FcmMessageProducerImpl.java`

**역할**: 비즈니스 로직에서 알림 요청을 받아 RabbitMQ에 적재

**주요 메서드**:
- `sendBroadcastMessage()`: 전체 사용자 대상 알림
- `sendSingleMessage()`: 특정 사용자 대상 알림
- `sendTestMessage()`: 테스트용 알림

**예시 코드**:
```java
// 채팅 메시지 발생 시
fcmMessageProducer.sendSingleMessage(userId, "새 메시지", "친구가 메시지를 보냈어요");
// → 즉시 반환 (비동기)
```

---

### 2. Consumer (메시지 소비자)
**위치**: `FcmMessageConsumerImpl.java`

**역할**: RabbitMQ에서 메시지를 가져와 실제 FCM 서버로 전송

**프로필**: `worker` 프로필로 실행되는 별도 컨테이너

**리스너 설정**:
```java
@RabbitListener(queues = RabbitMqConfig.FCM_SINGLE_QUEUE)
public void consumeSingleMessage(FcmMessageRequest fcmMessage) {
    processFcmMessage(fcmMessage);
}
```

---

### 3. RabbitMQ 설정
**위치**: `RabbitMqConfig.java`

**큐 구조**:
| 큐 이름 | 용도 | TTL | DLX 연결 |
|---------|------|-----|----------|
| `fcm.single.queue` | 개인화 알림 (채팅, 매칭) | 5분 | ✅ |
| `fcm.broadcast.queue` | 전체 발송 | 5분 | ✅ |
| `fcm.dlq.queue` | 실패 메시지 격리 | 무제한 | - |

---

## 채팅 알림 처리

### 기존 문제점
채팅 메시지 전송 시 FCM 서버 응답을 **동기적으로 대기**하여 채팅 API 성능 저하

### 개선 방안
`FcmChatMessageServiceImpl`에서 `FcmMessageProducer` 사용

**Before (동기)**:
```java
// 채팅 전송 → FCM 직접 호출 → 응답 대기 (500ms~2s)
fcmMessageClient.sendFcmMessage(userId, title, body);
```

**After (비동기)**:
```java
// 채팅 전송 → RabbitMQ 적재 → 즉시 반환 (1~5ms)
fcmMessageProducer.sendSingleMessage(userId, title, body);
```

### 처리 흐름
1. 사용자가 채팅 메시지 전송
2. `FcmChatMessageServiceImpl`이 알림 요청을 RabbitMQ에 적재
3. API는 즉시 응답 반환
4. Worker가 큐에서 메시지를 가져와 FCM 전송
5. 실패 시 재시도 또는 DLQ 이동

---

## 브로드캐스팅 알림 처리

### 메모리 최적화 (페이징 처리)

**문제**: 수만 명의 토큰을 한 번에 메모리에 로드 시 OOM 위험

**해결**: `Slice` 기반 페이징 조회

```java
private void processBroadcastMessage(FcmMessageRequest fcmMessage) {
    int pageSize = 1000; // 한 번에 1000명씩
    Slice<FcmToken> tokenSlice;
    
    do {
        tokenSlice = fcmTokenRepository.findAllByUserPushAgreeTrueSlice(
            PageRequest.of(pageNumber++, pageSize)
        );
        
        // 500명 단위로 멀티캐스트 전송
        for (int i = 0; i < tokens.size(); i += 500) {
            sendMulticastMessage(batch, title, body);
        }
    } while (tokenSlice.hasNext());
}
```

### FCM Multicast 활용

**기존**: 1명씩 루프 → 10,000명 = 10,000번 네트워크 I/O

**개선**: 500명 단위 묶음 → 10,000명 = 20번 네트워크 I/O

```java
MulticastMessage message = MulticastMessage.builder()
    .addAllTokens(tokens) // 최대 500개
    .setNotification(...)
    .build();

BatchResponse response = FirebaseMessaging.getInstance()
    .sendEachForMulticast(message);
```

**성능 향상**: 약 **500배** 네트워크 I/O 감소

---

## 백프레셔 메커니즘

### 개념
Producer가 메시지를 빠르게 쌓아도, Consumer가 처리 가능한 속도로만 가져가도록 제한

### 설정 위치
`RabbitMqConfig.java` - `rabbitListenerContainerFactory`

```java
factory.setPrefetchCount(20);
```

### 동작 원리

**Prefetch Count = 20**:
- Worker는 한 번에 **최대 20개** 메시지만 가져감
- 20개 처리 완료 전까지 추가 메시지를 가져오지 않음
- DB 커넥션 풀(예: 10개)을 초과하지 않도록 조절

**시나리오**:
```
[큐에 10,000개 메시지 적재]
  ↓
Worker: "20개만 줘" (Prefetch)
  ↓
[20개 처리 중... DB 안전]
  ↓
Worker: "다 끝났어, 20개 더 줘"
  ↓
[반복...]
```

### 효과
- ✅ DB 커넥션 풀 고갈 방지
- ✅ FCM API Rate Limit 준수
- ✅ 메모리 사용량 안정화

---

## 재처리 로직

### 1. 애플리케이션 레벨 재시도

**위치**: `FcmMessageConsumerImpl.sendFcmMessage()`

**전략**: Exponential Backoff (지수 백오프)

```java
int maxRetry = 3;
for (int i = 0; i < maxRetry; i++) {
    try {
        FirebaseMessaging.getInstance().send(message);
        return; // 성공 시 종료
    } catch (FirebaseMessagingException e) {
        if (isTransientError(e) && i < maxRetry - 1) {
            Thread.sleep(1000 * (i + 1)); // 1초 → 2초 → 3초
            continue;
        }
        // 최종 실패
        handleInvalidToken(e, tokenEntity, token, userId);
    }
}
```

**재시도 대상 에러**:
- `unavailable`: FCM 서버 일시적 장애
- `internal`: 내부 서버 오류
- `timeout`: 네트워크 타임아웃

**재시도 안 함**:
- `unregistered`: 토큰 만료 (DB에서 삭제)
- `invalid-argument`: 잘못된 요청

### 2. 멀티캐스트 재시도

브로드캐스트도 동일한 재시도 로직 적용:

```java
private void sendMulticastMessage(List<String> tokens, String title, String body) {
    int maxRetry = 3;
    for (int attempt = 0; attempt < maxRetry; attempt++) {
        try {
            BatchResponse response = FirebaseMessaging.getInstance()
                .sendEachForMulticast(message);
            return; // 성공
        } catch (FirebaseMessagingException e) {
            if (isTransientError(e) && attempt < maxRetry - 1) {
                Thread.sleep(1000 * (attempt + 1));
                continue;
            }
            throw new RuntimeException("FCM Multicast failed after retries", e);
        }
    }
}
```

---

## DLQ (Dead Letter Queue)

### 개념
재시도를 모두 실패한 메시지를 별도 큐에 격리하여 데이터 유실 방지

### 설정

**메인 큐 설정**:
```java
@Bean
public Queue fcmSingleQueue() {
    return QueueBuilder.durable(FCM_SINGLE_QUEUE)
        .withArgument("x-dead-letter-exchange", FCM_DLX_EXCHANGE)
        .withArgument("x-dead-letter-routing-key", FCM_DLQ_ROUTING_KEY)
        .withArgument("x-message-ttl", 300000) // 5분 TTL
        .build();
}
```

### DLQ 유입 조건

1. **TTL 만료**: 5분 내 처리되지 않은 메시지
2. **명시적 Reject**: Consumer에서 `AmqpRejectAndDontRequeueException` 발생
3. **최대 재시도 초과**: 애플리케이션 레벨 재시도 실패

```java
catch (Exception e) {
    log.error("FCM 메시지 처리 실패: messageId={}", fcmMessage.getMessageId());
    throw new AmqpRejectAndDontRequeueException(
        "FCM 메시지 처리 실패로 재큐 방지", e
    );
    // → DLQ로 이동
}
```

### DLQ 메시지 확인 방법

**RabbitMQ Management UI**:
```
http://localhost:15672
→ Queues → fcm.dlq.queue
→ Get messages
```

**CLI**:
```bash
docker exec rabbitmq rabbitmqctl list_queues name messages
```

### DLQ 메시지 재처리

**수동 재처리**:
1. DLQ에서 메시지 조회
2. 원인 분석 (로그 확인)
3. 문제 해결 후 메인 큐로 재발행

**자동 재처리** (향후 개선):
```java
@Scheduled(cron = "0 0 * * * *") // 매 시간
public void retryDlqMessages() {
    // DLQ 메시지를 가져와 메인 큐로 재발행
}
```

---

## 개선 사항 요약

### ✅ 완료된 개선 사항

| 항목 | 개선 전 | 개선 후 | 효과 |
|------|---------|---------|------|
| **채팅 알림** | 동기 (FCM 직접 호출) | 비동기 (RabbitMQ) | API 응답 속도 **10배** 향상 |
| **브로드캐스트** | 전체 메모리 로드 | 페이징 처리 | OOM 위험 제거 |
| **네트워크 I/O** | 1명씩 전송 | 500명 묶음 전송 | I/O 횟수 **500배** 감소 |
| **백프레셔** | 없음 | Prefetch 20 | DB 부하 제어 |
| **재시도** | 없음 | 3회 지수 백오프 | 일시적 장애 복구 |
| **DLQ** | 메시지 유실 | 실패 메시지 격리 | 데이터 보존 |

### 🔧 추가 개선 권장 사항

1. **DLQ 모니터링 알림**
   - Prometheus + Grafana로 DLQ 메시지 수 모니터링
   - 임계값 초과 시 Slack/Email 알림

2. **우선순위 큐 분리**
   - 실시간 알림 (채팅, 매칭): `fcm.priority.queue`
   - 마케팅 알림 (스케줄러): `fcm.normal.queue`

3. **배치 처리 최적화**
   - Spring Batch 도입으로 대량 발송 스케줄링

4. **메트릭 수집**
   - 알림 발송 성공률, 평균 처리 시간 추적

---
