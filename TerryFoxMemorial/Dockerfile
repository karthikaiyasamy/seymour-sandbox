# Dockerfile for Terry Fox Cancer Hospital (Port 8085)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/terry-fox-memorial-1.0.0.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
