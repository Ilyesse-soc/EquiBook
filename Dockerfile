FROM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace
COPY build.gradle settings.gradle ./
COPY shared shared
COPY rider-service rider-service
COPY horse-service horse-service
COPY booking-service booking-service
COPY payment-service payment-service
COPY dashboard-service dashboard-service
RUN --mount=type=cache,target=/home/gradle/.gradle gradle --no-daemon build

FROM eclipse-temurin:21-jre-jammy AS runtime
ARG SERVICE
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --system app
COPY --from=build /workspace/${SERVICE}/build/libs/app.jar /app/app.jar
USER app
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
