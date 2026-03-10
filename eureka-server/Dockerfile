FROM mirror.gcr.io/library/eclipse-temurin:25-jdk-alpine AS builder
RUN apk add --no-cache maven
WORKDIR /build
# Copy the parent pom and then the module
COPY pom.xml .
COPY eureka-server/pom.xml eureka-server/
RUN mvn dependency:go-offline -B -pl eureka-server -am
COPY eureka-server/src eureka-server/src
RUN mvn clean package -DskipTests -B -pl eureka-server -am

FROM mirror.gcr.io/library/eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=builder /build/eureka-server/target/*.jar app.jar
RUN chown spring:spring app.jar
USER spring:spring
EXPOSE 8761
ENV JAVA_OPTS="-Xms128m -Xmx256m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
