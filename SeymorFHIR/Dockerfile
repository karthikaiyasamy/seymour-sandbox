# Dockerfile for Seymour FHIR Server (Port 8090)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/seymor-fhir-1.0.0.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
