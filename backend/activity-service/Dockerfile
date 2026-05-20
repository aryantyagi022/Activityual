FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN mvn -q -B -DskipTests -pl activity-service -am package
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /src/activity-service/target/*.jar /app/app.jar
RUN addgroup -S app && adduser -S app -G app && chown -R app:app /app
USER app
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
