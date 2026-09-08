# Stage 1: Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy root-level source directories required for Maven build and frontend copying
COPY rescue-room/ rescue-room/
COPY rescue-room-backend/ rescue-room-backend/

# Build the Spring Boot backend (invokes maven-resources-plugin to copy frontend into static classes)
WORKDIR /app/rescue-room-backend
RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy packaged JAR from builder stage
COPY --from=builder /app/rescue-room-backend/target/rescue-room-backend-0.0.1-SNAPSHOT.jar app.jar

# Expose default port (Render overrides with $PORT at runtime)
EXPOSE 8081

# Run with container-aware JVM memory settings suitable for 512 MB RAM instances
ENTRYPOINT ["java", "-Xmx350m", "-XX:+UseContainerSupport", "-jar", "app.jar"]
