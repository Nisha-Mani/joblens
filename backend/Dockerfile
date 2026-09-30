# syntax=docker/dockerfile:1
# ---- build ----
FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /workspace
# Resolve dependencies first so they are cached until the POM changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
# Tests run in CI; the image build only packages.
RUN mvn -B -q package -DskipTests -Djacoco.skip=true

# ---- runtime ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app && mkdir -p /data/uploads && chown -R app:app /data
WORKDIR /app
COPY --from=build /workspace/target/joblens-backend-*.jar app.jar
USER app
ENV STORAGE_LOCAL_DIR=/data/uploads \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness | grep -q '"UP"' || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
