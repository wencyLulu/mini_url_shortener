# syntax=docker/dockerfile:1

# --- build stage -------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn -B -ntp -DskipTests package

# --- runtime stage -----------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/url-shortener-1.0.0.jar /app/url-shortener.jar

# H2 file database lives here; mount a volume so links survive container restarts.
ENV APP_BASE_URL=http://localhost:8080
ENV SERVER_PORT=8080

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/url-shortener.jar"]
