# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

# Resolve dependencies first so this layer is cached until pom.xml changes.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q clean package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home app

COPY --from=build /app/target/*.jar app.jar
USER app

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0" \
    SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

# DB_URL, DB_USERNAME, DB_PASSWORD, OTP_PEPPER, JWT/Resend secrets come from the environment.
ENTRYPOINT ["sh", "-c", "exec java -Dserver.port=${PORT:-8080} -jar app.jar"]
