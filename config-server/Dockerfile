FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY config-server/pom.xml config-server/pom.xml
COPY discovery-server/pom.xml discovery-server/pom.xml
COPY auth-server/pom.xml auth-server/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY shared-events/pom.xml shared-events/pom.xml
COPY core-banking/pom.xml core-banking/pom.xml
COPY bff-common/pom.xml bff-common/pom.xml
COPY bff-web/pom.xml bff-web/pom.xml
COPY bff-mobile/pom.xml bff-mobile/pom.xml
COPY bff-atm/pom.xml bff-atm/pom.xml
COPY risk-service/pom.xml risk-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
RUN mvn -q -pl config-server -am -DskipTests dependency:go-offline
COPY config-server/src config-server/src
RUN mvn -q -pl config-server -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/config-server/target/*.jar app.jar
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
