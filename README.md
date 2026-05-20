# Activityual — Cloud-Native Activity Tracker
Activityual helps users build consistency in habits like reading, workout, meditation, and sleep. It is built as a set of **Java 21 / Spring Boot 3** microservices behind a **Spring Cloud Gateway**, with a **SvelteKit** frontend, an **AI Coach** powered by a local **Ollama** LLM + **Chroma** vector store (RAG), and a **Personalized Recommendation** engine driven by RabbitMQ events. It runs locally on Docker Compose and deploys to **AWS EKS** via **Helm**, provisioned by **Terraform**, through **GitHub Actions**.
---
## 1. Architecture
```
                 ┌──────────────┐
   Browser ────► │  CloudFront  │ ──► S3 (SvelteKit static)
                 └──────────────┘
                                 ┌──────────────────────────────┐
                 ┌──────────────►│  ALB (api.activityual.*)     │
                 │               └──────────────┬───────────────┘
                                                │
                                  ┌─────────────▼─────────────┐
                                  │   gateway-service (JWT)   │
                                  └─┬──┬──┬──┬──┬──┬──┬───────┘
                                    │  │  │  │  │  │  └► notification-service ─┐
                                    │  │  │  │  │  └───► recommendation-service┤
                                    │  │  │  │  └──────► coach-service ────────┤  RabbitMQ
                                    │  │  │  └─────────► analytics-service ────┤ (topic exch
                                    │  │  └────────────► tracking-service ─────┘  activity.events)
                                    │  └───────────────► activity-service
                                    └──────────────────► auth-service
                                                              │
                                            ┌──────────────┐  │
                                            │  RDS Postgres│◄─┘  (DB per service)
                                            └──────────────┘
         Coach plane:  Ollama (llama3.2:3b)  +  Chroma (vector store)
```
* **Sync**: SvelteKit → ALB → Gateway → service.
* **Async**: `tracking-service` publishes `activity.logged` via a RabbitMQ topic exchange. Consumers (`analytics`, `coach`, `recommendation`, `notification`) each bind their own queue.
* **AI plane**: `coach-service` embeds every log into Chroma; `POST /coach/ask` does RAG against Ollama. `recommendation-service` recomputes time-of-day / day-of-week / frequency heuristics on each event.
## 2. Microservices
| Service | Port | DB | Responsibility |
|---|---|---|---|
| `gateway-service` | 8080 | – | JWT validation, CORS, routing, correlation id |
| `auth-service` | 8081 | `authdb` | Register / login / refresh, bcrypt, JWT |
| `activity-service` | 8082 | `activitydb` | CRUD activities |
| `tracking-service` | 8083 | `trackingdb` | Log progress, due-today/week, outbox |
| `analytics-service` | 8084 | `analyticsdb` | Streaks, consistency %, most/least consistent |
| `coach-service` | 8085 | – (Chroma) | RAG: embed logs, answer questions via Ollama |
| `recommendation-service` | 8086 | `recodb` | Timing / day / frequency suggestions |
| `notification-service` | 8087 | `notifdb` | In-app notifications fed by events |
All services share `common-lib` for the JWT filter, MDC correlation id, RabbitMQ topology constants, and the global error handler.
## 3. Repository Layout
```
backend/                 Maven multi-module, all Java services
frontend/                SvelteKit static app
infra/
  helm/<service>/        one chart per service + platform chart
  terraform/             VPC, EKS, RDS, ECR, S3, CloudFront, IAM
  network-diagram/       draw.io source
docs/                    setup, walkthrough, api, cloud, demo script
.github/workflows/       backend / frontend / infra pipelines
docker-compose.yml       full local stack
Makefile                 convenience targets
```
## 4. Quick Links
* [Local setup](docs/setup.md)
* [End-to-end walkthrough](docs/walkthrough.md)
* [API (OpenAPI / Postman)](docs/api/)
* [Cloud deployment](docs/cloud-deployment.md)
* [Demo script](docs/demo-script.md)
* [Network diagram](infra/network-diagram/)
## 5. Tech Stack
* **Backend** — Java 21, Spring Boot 3.3, Spring Cloud Gateway, Spring Data JPA, Spring AMQP, springdoc-openapi, Flyway, Lombok, jjwt.
* **Frontend** — SvelteKit 2 + adapter-static, TypeScript, TailwindCSS, Chart.js.
* **Data** — PostgreSQL 16 (RDS), Chroma, RabbitMQ 3.13.
* **AI** — Ollama with `llama3.2:3b` for generation and `nomic-embed-text` for embeddings.
* **Cloud** — AWS EKS 1.30, ECR, RDS, S3, CloudFront, ALB, CloudWatch.
* **DevOps** — Docker (multi-stage), Helm 3, Terraform 1.9, GitHub Actions (OIDC).
## 6. Design Notes
* **DB per service on one RDS instance** — logical isolation at low cost; can be split later.
* **Transactional outbox + relay** in `tracking-service` — guarantees at-least-once event delivery.
* **Heuristic recommendations** (SQL aggregations + confidence scoring) — deterministic and explainable; can be swapped for a trained model later without changing the API contract.
* **Single ALB with path/host routing** — cheaper than multiple LBs.
* **3B local LLM on CPU** — fits a `t3.xlarge` node; an 8B model would need GPU.
