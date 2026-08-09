# ── Stage 1: Download dependencies (cached riêng, chỉ rebuild khi pom.xml thay đổi) ──
FROM eclipse-temurin:21-jdk AS deps
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw && ./mvnw -B dependency:go-offline -q

# ── Stage 2: Build (chỉ compile source, dependencies đã có sẵn từ cache) ──
FROM deps AS build
COPY src src
RUN ./mvnw -B package -DskipTests -q

# ── Stage 3: Healthcheck helper ──
FROM eclipse-temurin:21-jdk AS healthcheck
WORKDIR /healthcheck
COPY docker/HealthCheck.java .
RUN javac HealthCheck.java

# ── Stage 4: Runtime image (nhỏ gọn, chỉ JRE) ──
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring spring \
    && chown -R spring:spring /app
COPY --from=build --chown=spring:spring /workspace/target/*.jar app.jar
COPY --from=healthcheck --chown=spring:spring /healthcheck/HealthCheck.class /app/HealthCheck.class
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

