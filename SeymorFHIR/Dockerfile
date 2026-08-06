# Multi-stage Dockerfile for Seymour FHIR Server
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml ../pom.xml
COPY SeymorFHIR/pom.xml ./pom.xml
COPY SeymorFHIR/src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/seymor-fhir-1.0.0.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
