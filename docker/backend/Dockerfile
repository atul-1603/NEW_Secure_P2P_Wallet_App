FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /workspace
COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app app

COPY --from=builder --chown=app:app /workspace/target/*.jar /app/app.jar

ENV DATABASE_URL=jdbc:postgresql://postgres:5432/wallet_db \
    DATABASE_USERNAME=wallet_user \
    DATABASE_PASSWORD=wallet_pass \
    JWT_SECRET=change-this-in-production-jwt-secret-key-minimum-32-bytes

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

USER app

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
