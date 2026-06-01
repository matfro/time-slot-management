FROM gradle:9.5.1-jdk26 AS builder
WORKDIR /app
COPY . .

RUN gradle bootJar -x test

FROM eclipse-temurin:26-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar


EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
