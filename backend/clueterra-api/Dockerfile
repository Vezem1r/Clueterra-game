# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS dependencies

WORKDIR /workspace/backend

COPY backend/mvnw .
COPY backend/.mvn .mvn
COPY backend/pom.xml .
COPY backend/clueterra-api/pom.xml clueterra-api/pom.xml

RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    chmod +x mvnw && \
    ./mvnw -f clueterra-api/pom.xml dependency:go-offline -B --no-transfer-progress

RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -f clueterra-api/pom.xml dependency:copy-dependencies \
        -DincludeScope=runtime \
        -DoutputDirectory=/opt/api-runtime-dependencies \
        -B --no-transfer-progress

FROM dependencies AS build

COPY backend/clueterra-api/src clueterra-api/src

RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -f clueterra-api/pom.xml package -DskipTests -B --no-transfer-progress

FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

ENV PLAYWRIGHT_BROWSERS_PATH=/opt/playwright

RUN --mount=type=bind,from=dependencies,source=/opt/api-runtime-dependencies,target=/tmp/api-runtime-dependencies \
    apt-get update && \
    apt-get install -y --no-install-recommends unzip && \
    java -cp "/tmp/api-runtime-dependencies/*" \
        com.microsoft.playwright.CLI install --with-deps --only-shell chromium && \
    apt-get purge -y unzip && apt-get autoremove -y && rm -rf /var/lib/apt/lists/* && \
    addgroup --gid 1001 appgroup && \
    adduser --uid 1001 --ingroup appgroup --disabled-password --gecos "" appuser && \
    chown -R appuser:appgroup /app && \
    chmod -R o+rx /opt/playwright

ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

COPY --from=build /workspace/backend/clueterra-api/target/*.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
