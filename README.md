# Dormly Backend

Java 21 Spring Boot backend for Dormly. The local Docker stack runs the backend with SQL Server, Redis, Kafka, Kafka UI, and Redis Commander.

## Local-only Docker warning

This Compose setup is for local development. It exposes database/cache/broker ports, runs SQL Server with the `sa` account for the backend, and starts admin UIs by default. Do not use it as a production deployment template.

## Prerequisites

- Docker Desktop
- Java 21, if running without Docker
- Maven wrapper from this repo

## Environment setup

Create your local env file:

```powershell
Copy-Item .env.example .env
```

Set at least `MSSQL_SA_PASSWORD`, `DB_PASSWORD`, and `JWT_SECRET` in `.env`. For the agreed local setup, `DB_USER=sa` and `DB_PASSWORD` should match `MSSQL_SA_PASSWORD`.

SQL Server requires a strong password, for example `Your_strong_password123`.

Optional integrations use placeholder values by default. Set real values only when testing those features:

- `TWILIO_SID`, `TWILIO_TOKEN`, `TWILIO_FROM`
- `MAIL_USERNAME`, `MAIL_PASSWORD`
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- Firebase push credentials are app-specific and not configured by Docker.

## Run production-like Docker stack

```powershell
docker compose up --build
```

Backend URL:

- API: http://localhost:8080
- Health: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui.html

Admin tools:

- Kafka UI: http://localhost:8081
- Redis Commander: http://localhost:8082

Exposed service ports:

- SQL Server: `localhost:1433`
- Redis: `localhost:6379`
- Kafka: `localhost:9092`

Stop the stack:

```powershell
docker compose down
```

Reset local SQL Server data:

```powershell
docker compose down -v
```

## Run hot-reload development backend

The dev backend mounts the repo into the container, caches Maven dependencies in a named volume, and exposes Java debug port `5005`.

```powershell
docker compose --profile dev up backend-dev sqlserver sqlserver-init redis kafka kafka-ui redis-commander
```

Do not run `backend` and `backend-dev` at the same time because both bind host port `8080`.

## Run tests with Compose dependencies

Tests use the separate `${TEST_DB_NAME:-dormly_test}` database.

```powershell
docker compose --profile test up --build test
```

Clean up the test container after it exits:

```powershell
docker compose --profile test down
```

## Build the backend image

The Dockerfile builds a jar with tests skipped. Run the Compose test command above when you want full tests against container dependencies.

```powershell
docker build -t dormly-backend .
```

## Run without Docker

Start required dependencies yourself, then run:

```powershell
./mvnw.cmd spring-boot:run
```

Compile:

```powershell
./mvnw.cmd compile
```

Run tests:

```powershell
./mvnw.cmd test
```

Package:

```powershell
./mvnw.cmd clean package
```

## Configuration notes

Docker uses the `docker` Spring profile for the backend and the `test` Spring profile for the Compose test service.

- `application.properties` remains the default non-Docker config.
- `application-docker.properties` points services to Compose hostnames: `sqlserver`, `redis`, and `kafka`.
- `application-test.properties` uses the Compose test database.

Flyway migrations run on startup from `src/main/resources/db/migration`.
