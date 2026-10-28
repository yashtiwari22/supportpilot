# syntax=docker/dockerfile:1
# Multi-stage Dockerfile for SupportPilot Spring Boot Backend

# Stage 1: Build JAR with Maven wrapper
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace/app

# Cache Maven wrapper and POM dependencies
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B || true

# Copy source code and build production jar
COPY src/ src/
RUN ./mvnw clean package -DskipTests

# Stage 2: Extract Spring Boot layered jar
FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /workspace/app
COPY --from=builder /workspace/app/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 3: Minimal, unprivileged runtime image
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Run as non-root user
RUN addgroup -S supportpilot && adduser -S supportpilot -G supportpilot
USER supportpilot:supportpilot

# Copy layered jar directories for optimal caching
COPY --from=extractor --chown=supportpilot:supportpilot /workspace/app/dependencies/ ./
COPY --from=extractor --chown=supportpilot:supportpilot /workspace/app/spring-boot-loader/ ./
COPY --from=extractor --chown=supportpilot:supportpilot /workspace/app/snapshot-dependencies/ ./
COPY --from=extractor --chown=supportpilot:supportpilot /workspace/app/application/ ./

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseSerialGC -XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
