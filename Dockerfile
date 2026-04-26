# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — Build
# Uses a full JDK image to compile and package the application.
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Copy dependency descriptors first to leverage layer caching.
# Dependencies are only re-downloaded when pom.xml changes.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and package (skip tests — they run in CI separately)
COPY src ./src
RUN mvn package -DskipTests -q

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — Extract layers for efficient Docker layer caching
# Spring Boot layertools splits the fat JAR into dependency layers.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS layers
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ─────────────────────────────────────────────────────────────────────────────
# Stage 3 — Production Image
# Minimal JRE runtime; no build tools, no source code.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache wget
RUN addgroup -S ims && adduser -S ims -G ims
WORKDIR /app

# Copy layers in order of change frequency (most stable first)
COPY --from=layers /app/dependencies/ ./
COPY --from=layers /app/spring-boot-loader/ ./
COPY --from=layers /app/snapshot-dependencies/ ./
COPY --from=layers /app/application/ ./

RUN chown -R ims:ims /app
USER ims

EXPOSE 8080

# JVM tuning for containerised environments:
#   -XX:+UseContainerSupport        — respect cgroup memory limits
#   -XX:MaxRAMPercentage=75         — use 75% of container RAM for heap
#   -Djava.security.egd=...         — faster random for token generation
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

# Health check — Kubernetes uses its own probes, this covers Docker/Swarm
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1