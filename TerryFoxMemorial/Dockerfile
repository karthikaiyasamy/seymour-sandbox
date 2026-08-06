# Multi-stage Dockerfile for Terry Fox Cancer Hospital
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml ../pom.xml
COPY TerryFoxMemorial/pom.xml ./pom.xml
COPY TerryFoxMemorial/src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/terry-fox-memorial-1.0.0.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
