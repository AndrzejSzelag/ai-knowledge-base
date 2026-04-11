# ---------------------------
# STAGE 1: Frontend Build
# ---------------------------
  FROM node:20-alpine AS frontend-build
  WORKDIR /app/frontend
  
  # Leverage Docker layer caching for node_modules
  COPY ai-frontend/package*.json ./
  RUN npm ci
  
  COPY ai-frontend/ ./
  RUN npm run build
  
  # ---------------------------
  # STAGE 2: Backend Build
  # ---------------------------
  FROM maven:3.9-eclipse-temurin-21 AS backend-build
  WORKDIR /app
  
  COPY pom.xml ./
  
  # Download dependencies into BuildKit cache to avoid re-fetching on subsequent builds
  RUN --mount=type=cache,target=/root/.m2 \
      mvn dependency:go-offline -B
  
  COPY src ./src
  
  # Integrate frontend build into Spring Boot static resources
  COPY --from=frontend-build /app/frontend/dist ./src/main/resources/static
  
  # Build the application in offline mode using cached dependencies
  RUN --mount=type=cache,target=/root/.m2 \
      mvn package -DskipTests -B -Dmaven.wagon.http.retryHandler.count=3
  
  # ---------------------------
  # STAGE 3: Final Runtime Image
  # ---------------------------
  FROM eclipse-temurin:21-jre-alpine
  WORKDIR /app
  
  # Install wget for health check support in Alpine
  RUN apk add --no-cache wget
  
  # Secure the container by running as a non-root user
  RUN addgroup -S spring && adduser -S spring -G spring
  
  COPY --from=backend-build /app/target/*.jar app.jar
  RUN chown spring:spring app.jar
  
  USER spring:spring
  EXPOSE 8080
  
  # Health check using Spring Boot Actuator
  HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1
  
  # Run with container-aware JVM settings for optimal memory management
  ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "app.jar"]