FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw && ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:17-jdk AS healthcheck
WORKDIR /healthcheck
COPY docker/HealthCheck.java .
RUN javac HealthCheck.java

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring spring
RUN chown -R spring:spring /app
COPY --from=build --chown=spring:spring /workspace/target/*.jar app.jar
COPY --from=healthcheck --chown=spring:spring /healthcheck/HealthCheck.class /app/HealthCheck.class
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
