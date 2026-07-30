FROM mirror.gcr.io/library/eclipse-temurin:21-jdk-alpine AS builder

RUN apk add --no-cache maven

WORKDIR /build

COPY pom.xml .
COPY config-service/pom.xml config-service/
COPY discovery-service/pom.xml discovery-service/
COPY gateway-service/pom.xml gateway-service/
COPY inventory-service/pom.xml inventory-service/

RUN mvn dependency:go-offline -B -pl config-service -am || true

COPY config-service/src config-service/src
RUN mvn clean package -DskipTests -B -pl config-service -am

FROM mirror.gcr.io/library/eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=builder /build/config-service/target/*.jar app.jar
RUN chown -R spring:spring /app

USER spring:spring

EXPOSE 8888

HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=5 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8888/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
