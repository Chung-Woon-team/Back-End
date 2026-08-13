# Cloud Run 배포용. 로컬에 JDK 가 없어도 이 안에서 빌드된다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 레이어를 먼저 캐싱해 두면 소스만 바뀔 때 빌드가 훨씬 빠르다.
COPY gradlew ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --version --no-daemon

COPY build.gradle settings.gradle ./
COPY src ./src
RUN ./gradlew clean bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Seoul"
ENV SPRING_PROFILES_ACTIVE=prod

# Cloud Run 이 PORT 환경변수를 주입한다 (application.yaml 의 ${PORT:8080} 이 받는다).
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
