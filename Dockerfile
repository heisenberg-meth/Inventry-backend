FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true -X

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache wget
RUN addgroup -S ims && adduser -S ims -G ims

COPY --from=builder /app/target/*.jar app.jar

USER ims
EXPOSE 8080

HEALTHCHECK --interval=5m --timeout=30s --start-period=10m \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-Xmx512m", "-Xms256m", "-jar", "app.jar"]