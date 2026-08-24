# Build stage
# NOTE: build context is the repo ROOT (see docker-compose.yml api.build.context: .)
# so repo-root assets/ — the single source of truth for demo profile JSON (ticket 01,
# release 0.6) — is visible to Gradle. build.gradle exposes it on the classpath via
# srcDir "${rootDir}/../assets"; in this image rootDir is /app, so assets must sit at /assets.
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY api/gradlew .
COPY api/gradle gradle
COPY api/build.gradle api/settings.gradle ./
COPY api/src src
# Repo-root assets/ → /assets so "${rootDir}/../assets" (= /app/../assets) resolves.
COPY assets /assets
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
