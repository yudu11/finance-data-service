# syntax=docker/dockerfile:1
FROM eclipse-temurin:17-jdk

WORKDIR /app

RUN mkdir -p /app/data
ENV FINANCE_DATA_BASE_DIR=/app/data
VOLUME ["/app/data"]

# Copy the Spring Boot fat jar built by Gradle
COPY build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
