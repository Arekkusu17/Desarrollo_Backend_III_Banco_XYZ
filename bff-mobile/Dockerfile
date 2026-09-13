FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY core-banking/pom.xml core-banking/pom.xml
COPY bff-common/pom.xml bff-common/pom.xml
COPY bff-web/pom.xml bff-web/pom.xml
COPY bff-mobile/pom.xml bff-mobile/pom.xml
COPY bff-atm/pom.xml bff-atm/pom.xml
RUN mvn -q -pl bff-mobile -am -DskipTests dependency:go-offline
COPY bff-common/src bff-common/src
COPY bff-mobile/src bff-mobile/src
RUN mvn -q -pl bff-mobile -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/bff-mobile/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
