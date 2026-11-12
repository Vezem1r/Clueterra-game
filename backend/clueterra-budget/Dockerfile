# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace/backend

COPY backend/mvnw .
COPY backend/.mvn .mvn
COPY backend/pom.xml .
COPY backend/clueterra-budget/pom.xml clueterra-budget/pom.xml

RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    chmod +x mvnw && \
    ./mvnw -f clueterra-budget/pom.xml dependency:go-offline -B --no-transfer-progress

COPY backend/clueterra-budget/src clueterra-budget/src

RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -f clueterra-budget/pom.xml package -DskipTests -B --no-transfer-progress

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup && \
    chown appuser:appgroup /app

COPY --from=build --chown=appuser:appgroup /workspace/backend/clueterra-budget/target/*.jar app.jar

USER appuser

EXPOSE 8082

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
