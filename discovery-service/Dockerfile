FROM mirror.gcr.io/library/eclipse-temurin:21-jdk-alpine AS builder

RUN apk add --no-cache maven

WORKDIR /build

COPY pom.xml .
COPY config-service/pom.xml config-service/
COPY discovery-service/pom.xml discovery-service/
COPY gateway-service/pom.xml gateway-service/
COPY inventory-service/pom.xml inventory-service/

RUN mvn dependency:go-offline -B -pl discovery-service -am || true

COPY discovery-service/src discovery-service/src
RUN mvn clean package -DskipTests -B -pl discovery-service -am

FROM mirror.gcr.io/library/eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=builder /build/discovery-service/target/*.jar app.jar
RUN chown -R spring:spring /app

USER spring:spring

EXPOSE 8761

HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=5 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8761/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
