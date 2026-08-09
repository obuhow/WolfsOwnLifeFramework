# Build - uses pre-built JAR from host
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY api/build/libs/*.jar app.jar

# Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
