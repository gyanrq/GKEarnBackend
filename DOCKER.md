# Docker Local Setup

## Run PostgreSQL + Redis only

Use this when you want to run the Spring Boot app from terminal with Maven:

```bash
cp .env.example .env
docker compose up -d postgres redis
DB_USER=earnx_user DB_PASS=changeme DB_NAME=earnx3_dev REDIS_HOST=localhost ./mvnw spring-boot:run
```

Health check:

```bash
curl http://localhost:8080/actuator/health
```

## Run the full backend stack in Docker

```bash
cp .env.example .env
docker compose --profile app up --build
```

Backend URL:

```text
http://localhost:8080
```

## Stop services

```bash
docker compose down
```

To delete local DB/Redis data too:

```bash
docker compose down -v
```
