# syntax=docker/dockerfile:1

# 1) Build the React editor and renderer bundle.
FROM node:22-alpine AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2) Build the Spring Boot application with the frontend output baked in.
FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -Dskip.frontend=true dependency:go-offline
COPY src ./src
COPY --from=frontend-build /workspace/src/main/resources/static ./src/main/resources/static
RUN mvn -B -DskipTests -Dskip.frontend=true package

# 3) Small runtime image.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S aster \
    && adduser -S aster -G aster \
    && mkdir -p /app/uploads \
    && chown -R aster:aster /app

COPY --from=backend-build /workspace/target/WebPorduce-*.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=prod \
    APP_STORAGE_ROOT=/app/uploads \
    SERVER_PORT=8080

USER aster
EXPOSE 8080
VOLUME ["/app/uploads"]

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
