FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common-contracts/pom.xml common-contracts/pom.xml
COPY workload-service/pom.xml workload-service/pom.xml
COPY enterprise-app/pom.xml enterprise-app/pom.xml
COPY common-contracts/src common-contracts/src
COPY enterprise-app/src enterprise-app/src
RUN mvn -pl enterprise-app -am clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/enterprise-app/target/enterprise-app-*.jar app.jar
RUN mkdir -p /data/files
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
