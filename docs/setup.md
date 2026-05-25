# Local Setup

This guide brings the entire stack up on your laptop with **Docker Compose**.

## Prerequisites

* Docker 24+ and Docker Compose v2
* JDK 21 + Maven 3.9 (only if you want to run a service outside the container)
* Node 20 + pnpm/npm (only if you want to run the frontend dev server)
* At least 8 GB free RAM (Ollama + 8 Spring services + Postgres + Rabbit + Chroma)

## 1. Configure environment

```bash
cp .env.example .env
```

The defaults work out of the box; only the JWT secret needs replacing for any real deployment.

## 2. Start the stack

```bash
make up                  # builds and starts every service
docker compose ps        # confirm everything is healthy
```

First boot will:

* create one Postgres database per service (`authdb`, `activitydb`, …) via the init script,
* start RabbitMQ (management UI at <http://localhost:15672> — guest / guest),
* start Chroma at <http://localhost:8000>,
* start Ollama at <http://localhost:11434>,
* launch the 8 Spring microservices and the SvelteKit frontend.

## 3. Pull the AI models (one-time)

```bash
make ollama-pull         # pulls llama3.2:3b
docker compose exec ollama ollama pull nomic-embed-text
```

## 4. Open the app

* Frontend: <http://localhost:5173>
* Gateway:  <http://localhost:8080>
* RabbitMQ UI: <http://localhost:15672>
* Swagger per service: `http://localhost:<port>/swagger` (8081-8087)

## 5. Sample journey

1. Register a user → land on the dashboard.
2. Add an activity (e.g. *Workout · WEEKLY_3*).
3. Mark it `done` / `missed` / `completed`.
4. Watch the dashboard refresh with streaks and consistency.
5. Open `/coach` and ask *"Why do I keep missing my workout?"*.
6. Open `/recommendations` after ≥4 logs for the same activity.

## 6. Useful commands

```bash
make logs svc=coach-service        # tail logs of one service
docker compose restart tracking-service
docker compose down -v             # wipe everything including volumes
```

## 7. Running a single service outside Docker

```bash
cd backend
mvn -pl auth-service -am spring-boot:run
```

Set the env vars from `.env` first.

