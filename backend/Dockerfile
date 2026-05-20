FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
COPY common-lib/pom.xml common-lib/
COPY gateway-service/pom.xml gateway-service/
COPY auth-service/pom.xml auth-service/
COPY activity-service/pom.xml activity-service/
COPY tracking-service/pom.xml tracking-service/
COPY analytics-service/pom.xml analytics-service/
COPY coach-service/pom.xml coach-service/
COPY recommendation-service/pom.xml recommendation-service/
COPY notification-service/pom.xml notification-service/
RUN mvn -q -B -e -DskipTests dependency:go-offline || true
COPY . .
ARG MODULE
RUN mvn -q -B -DskipTests -pl ${MODULE} -am package

FROM eclipse-temurin:21-jre-alpine AS service
WORKDIR /app
ARG MODULE
COPY --from=build /src/${MODULE}/target/*.jar /app/app.jar
RUN addgroup -S app && adduser -S app -G app
USER app
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]

