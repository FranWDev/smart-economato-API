# Fase de construcción usando el mirror de Google
FROM mirror.gcr.io/library/eclipse-temurin:25-jdk-alpine AS builder

RUN apk add --no-cache maven

WORKDIR /build

# Copy the parent pom and all module poms to satisfy Maven reactor
COPY pom.xml .
COPY config-service/pom.xml config-service/
COPY inventory-service/pom.xml inventory-service/
COPY discovery-service/pom.xml discovery-service/
COPY gateway-service/pom.xml gateway-service/
COPY user-service/pom.xml user-service/
RUN mvn dependency:go-offline -B -pl inventory-service -am
COPY inventory-service/src inventory-service/src
RUN mvn clean package -DskipTests -B -pl inventory-service -am

FROM mirror.gcr.io/library/eclipse-temurin:25-jre-alpine

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=builder /build/inventory-service/target/*.jar app.jar

RUN mkdir -p /app/uploads && chown -R spring:spring /app

USER spring:spring

EXPOSE 8081

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseZGC -XX:+ZGenerational -XX:MaxGCPauseMillis=200"

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]