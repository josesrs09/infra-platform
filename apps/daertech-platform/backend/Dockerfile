FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache git openssh-client docker-cli docker-cli-compose curl \
    && addgroup -S app \
    && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/target/daertech-platform-0.1.0.jar app.jar
RUN mkdir -p /opt/infra-platform/workspaces /opt/daertech/config \
    && chown -R app:app /opt/infra-platform /opt/daertech /app
USER app
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
