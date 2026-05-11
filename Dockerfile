FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Optimize dependency resolution
COPY pom.xml .
RUN mvn -q -DskipTests dependency:resolve

# Build application
COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true -q

# Final stage (Debian-based for performance)
FROM eclipse-temurin:21-jre
WORKDIR /app

# Create a non-root user
RUN groupadd -r ims && useradd -r -g ims ims
USER ims

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 10000

# Optimize for low-memory/low-CPU environments like Render Free Tier
# SerialGC: Faster startup, lower overhead for single-core/small instances
# Xshare:on: Enables Class Data Sharing
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-Xshare:on", "-Xmx512m", "-Xms256m", "-jar", "app.jar"]