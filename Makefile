.PHONY: up down logs seed build frontend backend ollama-pull

up:
	docker compose --env-file .env up -d --build

down:
	docker compose down -v

logs:
	docker compose logs -f $(svc)

seed:
	docker compose exec postgres psql -U $$POSTGRES_USER -d activitydb -f /seed/activity-seed.sql || true

build:
	cd backend && ./mvnw -T 1C -q -DskipTests package

frontend:
	cd frontend && pnpm install && pnpm dev

ollama-pull:
	docker compose exec ollama ollama pull llama3.2:3b

