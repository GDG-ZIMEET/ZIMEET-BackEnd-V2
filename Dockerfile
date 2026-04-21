# =========================================================================
# Multi-stage Dockerfile — 프로덕션 환경 기준으로 구성
#
# 빌드 인자:
#   MODULE_NAME : 빌드·실행할 Gradle 모듈 이름 (zi-meet-core | zi-meet-payment)
#
# 사용 예:
#   docker build --build-arg MODULE_NAME=zi-meet-core    -t zimeet/core:local    .
#   docker build --build-arg MODULE_NAME=zi-meet-payment -t zimeet/payment:local .
# =========================================================================

# ---------- Stage 1. 빌드 ----------
FROM eclipse-temurin:17-jdk-jammy AS builder

ARG MODULE_NAME=zi-meet-core
WORKDIR /workspace

# 의존성 캐시 최적화: 루트 설정 파일 먼저 복사
COPY gradlew gradlew.bat ./
COPY gradle gradle
COPY settings.gradle build.gradle ./

# 각 모듈의 build.gradle을 먼저 복사하여 의존성 레이어 재사용
COPY zi-meet-common/build.gradle  zi-meet-common/build.gradle
COPY zi-meet-core/build.gradle    zi-meet-core/build.gradle
COPY zi-meet-payment/build.gradle zi-meet-payment/build.gradle

RUN chmod +x ./gradlew

# 의존성 사전 다운로드 (소스 변경 시 이 레이어는 캐시됨)
RUN ./gradlew --no-daemon :${MODULE_NAME}:dependencies || true

# 전체 소스 복사 후 bootJar 빌드
COPY zi-meet-common  zi-meet-common
COPY zi-meet-core    zi-meet-core
COPY zi-meet-payment zi-meet-payment

RUN ./gradlew --no-daemon :${MODULE_NAME}:bootJar -x test

# 산출물 경로를 고정된 이름으로 정리
RUN cp ${MODULE_NAME}/build/libs/*.jar /workspace/app.jar

# ---------- Stage 2. 런타임 ----------
FROM eclipse-temurin:17-jre-jammy AS runtime

ARG MODULE_NAME=zi-meet-core
ENV MODULE_NAME=${MODULE_NAME}

# 런타임 필수 패키지 + 타임존 (healthcheck용 curl 포함)
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl tzdata \
 && ln -sf /usr/share/zoneinfo/Asia/Seoul /etc/localtime \
 && echo "Asia/Seoul" > /etc/timezone \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/*

# 비-root 사용자
RUN groupadd --system zimeet && useradd --system --gid zimeet --home /app zimeet
WORKDIR /app

COPY --from=builder --chown=zimeet:zimeet /workspace/app.jar /app/app.jar

USER zimeet

# JVM 컨테이너 인식·힙 비율 기본값 (필요 시 JAVA_OPTS 오버라이드)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Seoul"

EXPOSE 8080 8081

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
