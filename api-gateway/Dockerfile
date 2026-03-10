FROM mirror.gcr.io/library/eclipse-temurin:25-jdk-alpine AS builder
RUN apk add --no-cache maven
WORKDIR /build
# Copy the parent pom and all module poms to satisfy Maven reactor
COPY pom.xml .
COPY eureka-server/pom.xml eureka-server/
COPY api-gateway/pom.xml api-gateway/
COPY inventory-service/pom.xml inventory-service/
RUN mvn dependency:go-offline -B -pl api-gateway -am
COPY api-gateway/src api-gateway/src
RUN mvn clean package -DskipTests -B -pl api-gateway -am

FROM mirror.gcr.io/library/eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=builder /build/api-gateway/target/*.jar app.jar
RUN chown spring:spring app.jar
USER spring:spring
EXPOSE 8080
ENV JAVA_OPTS="-Xms128m -Xmx256m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
