FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common-contracts/pom.xml common-contracts/pom.xml
COPY workload-service/pom.xml workload-service/pom.xml
COPY enterprise-app/pom.xml enterprise-app/pom.xml
COPY common-contracts/src common-contracts/src
COPY workload-service/src workload-service/src
RUN mvn -pl workload-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY tools/container-healthcheck.sh /app/healthcheck.sh
COPY --from=build /workspace/workload-service/target/workload-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
