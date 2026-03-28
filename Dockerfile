FROM eclipse-temurin:17-jdk

# 빌드 인자 정의 (api 또는 worker)
ARG MODULE_NAME=zi-meet-application
ARG JAR_FILE=${MODULE_NAME}/build/libs/*.jar

# JAR 파일 복사
COPY ${JAR_FILE} app.jar

# 타임존 설정
RUN ln -sf /usr/share/zoneinfo/Asia/Seoul /etc/localtime && echo "Asia/Seoul" > /etc/timezone

ENV SPRING_PROFILES_ACTIVE=prod

# 시스템 진입점 정의
ENTRYPOINT ["java", "-jar", "-Duser.timezone=Asia/Seoul", "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}", "-Djdk.cgroup.enable=false", "app.jar"]