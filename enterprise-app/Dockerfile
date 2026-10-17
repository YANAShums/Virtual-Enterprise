FROM node:22-alpine AS frontend
WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common-contracts/pom.xml common-contracts/pom.xml
COPY workload-service/pom.xml workload-service/pom.xml
COPY enterprise-app/pom.xml enterprise-app/pom.xml
COPY common-contracts/src common-contracts/src
COPY enterprise-app/src enterprise-app/src
COPY --from=frontend /workspace/enterprise-app/target/generated-resources/frontend/js/react enterprise-app/src/main/resources/static/js/react
RUN --mount=type=cache,target=/root/.m2 mvn -pl enterprise-app -am clean package -DskipTests -Dfrontend.skip=true

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY tools/container-healthcheck.sh /app/healthcheck.sh
COPY --from=build /workspace/enterprise-app/target/enterprise-app-*.jar app.jar
RUN mkdir -p /data/files
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
