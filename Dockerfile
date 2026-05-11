FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build application
COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true -q

# Final stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Create a non-root user
RUN groupadd -r ims && useradd -r -g ims ims
USER ims

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 10000

# Enable CDS and optimize memory for small instances
ENTRYPOINT ["java", "-Xshare:on", "-Xmx512m", "-Xms256m", "-jar", "app.jar"]