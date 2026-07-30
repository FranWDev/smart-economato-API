FROM eclipse-temurin:25-jdk-alpine AS builder
RUN apk add --no-cache maven
WORKDIR /app
COPY pom.xml .
COPY config-service/pom.xml config-service/
COPY discovery-service/pom.xml discovery-service/
COPY gateway-service/pom.xml gateway-service/
COPY inventory-service/pom.xml inventory-service/
COPY user-service/pom.xml user-service/
COPY user-service/src user-service/src
RUN mvn clean package -pl user-service -DskipTests

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=builder /app/user-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
