


FROM eclipse-temurin:25-jdk-alpine AS builder

RUN apk add --no-cache maven

WORKDIR /build


COPY pom.xml .


RUN mvn dependency:go-offline -B


COPY src ./src


RUN mvn clean package -DskipTests -B




FROM eclipse-temurin:25-jre-alpine

WORKDIR /app


RUN addgroup -S spring && adduser -S spring -G spring


COPY --from=builder /build/target/*.jar app.jar


RUN chown spring:spring app.jar


USER spring:spring


EXPOSE 8081


ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseZGC -XX:+ZGenerational -XX:MaxGCPauseMillis=200"


HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1


ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
