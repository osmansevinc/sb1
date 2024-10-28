FROM eclipse-temurin:17-jdk-alpine

RUN apk add --no-cache ffmpeg

WORKDIR /app
COPY target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]