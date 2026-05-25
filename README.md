# Activityual
A habit tracker I built for the cloud case study. It tracks daily/weekly habits
(workout, reading, meditation, sleep, etc.), gives users an analytics view of
their streaks and consistency, and bolts on two AI-ish features: a Coach that
answers free-form questions about the user's own logs (RAG over a vector
store) and a Recommendation engine that suggests when to do each habit based
on past completions.
Stack:
- Backend: 8 Spring Boot 3 services on Java 21, fronted by Spring Cloud Gateway.
- Frontend: SvelteKit static site, served from S3 + CloudFront.
- Data: PostgreSQL 16 on RDS (one logical DB per service on one instance),
  Chroma for vectors, RabbitMQ for async fan-out.
- AI: Ollama running `llama3.2:3b` for generation and `nomic-embed-text` for
  embeddings.
- Cloud: AWS EKS 1.30 + ALB + CloudFront, all provisioned with Terraform, all
  workloads packaged as Helm charts, all deployed by GitHub Actions over OIDC.
It also runs locally with `docker compose` if you want to try it without AWS.
Live URL: https://d17bqzy8fgqhxi.cloudfront.net
Demo login: `aryantyagi0@gmail.com` / `Aryan1234`

**Github Repo link: https://github.com/aryantyagi022/Activityual**

---
## Architecture
```
                  +--------------+
   Browser ---->  |  CloudFront  |  ----> S3 (SvelteKit static)
                  +------+-------+
                         | /api/*
                         v
                  +--------------+
                  |     ALB      |
                  +------+-------+
                         |
                +--------+----------+
                | gateway-service   |   JWT, CORS, X-User-Id, correlation id
                +-+-+-+-+-+-+-+-+---+
                  | | | | | | | |
                  | | | | | | | +-> notification-service ---+
                  | | | | | | +---> recommendation-service  |
                  | | | | | +-----> coach-service ----------+  RabbitMQ
                  | | | | +-------> analytics-service ------+  topic exchange
                  | | | +---------> tracking-service -------+  activity.events
                  | | +-----------> activity-service
                  | +-------------> auth-service
                  +---------------> (per-service Postgres on one RDS)
  AI plane:   Ollama (llama3.2:3b)  +  Chroma  on a tainted EKS node group
```
There is also a network diagram exported from draw.io. The source is at
[`infra/network-diagram/network.drawio`](infra/network-diagram/network.drawio)
and the PNG export is below:
![Network architecture](docs/screenshots/NetworkDiagram.drawio.png)
For visual proof of the actual AWS deployment (VPC, EKS, ALB, CloudFront, S3,
RDS, ECR, CloudWatch, Secrets Manager, IAM, and the running app), see
[`docs/cloud-deployment-screenshots.md`](docs/cloud-deployment-screenshots.md).
### How requests move
Synchronous user calls go Browser -> CloudFront -> ALB -> Gateway -> service.
The gateway is the only thing that validates JWTs; downstream services trust
the `X-User-Id` header it injects.
Anything that doesn't have to be in the request path is fired off as an event.
`tracking-service` writes the log row and an outbox row in the same DB
transaction, then a relay reads the outbox every two seconds and publishes
`activity.logged` to the `activity.events` topic exchange. Four consumers bind
to that exchange (analytics, coach, recommendation, notification), each with
their own queue. If RabbitMQ is briefly down nothing is lost: the outbox row
stays, the relay catches up.
The Coach is the most interesting piece. Every time a log event arrives,
`coach-service` embeds it with `nomic-embed-text` and upserts the chunk into
Chroma with the user id as metadata. When the user asks a question on `/coach`,
the service embeds the question, queries Chroma for the top 4 chunks scoped to
that user, builds a prompt, and calls Ollama. The retrieved chunks are
returned to the UI so the user can see exactly what the model was reading.
---
## Microservices
| Service                  | Port | DB           | What it does |
|--------------------------|------|--------------|--------------|
| `gateway-service`        | 8080 | -            | JWT validation, CORS, routing, correlation id |
| `auth-service`           | 8081 | `authdb`     | Register, login, refresh; bcrypt + jjwt |
| `activity-service`       | 8082 | `activitydb` | CRUD on activities |
| `tracking-service`       | 8083 | `trackingdb` | Log progress, due-today / due-week, outbox |
| `analytics-service`      | 8084 | `analyticsdb`| Streaks, consistency %, most/least consistent |
| `coach-service`          | 8085 | (uses Chroma)| RAG: embeds logs, answers questions via Ollama |
| `recommendation-service` | 8086 | `recodb`     | Timing / day-of-week / frequency suggestions |
| `notification-service`   | 8087 | `notifdb`    | In-app notifications driven by events |
There's a `common-lib` Maven module that ships only cross-cutting concerns:
the JWT filter, the MDC correlation-id filter, the RabbitMQ exchange/routing
constants, and the global `@RestControllerAdvice`. No DTOs, no business logic.
That was deliberate so services can evolve their contracts independently.
---
## Repo layout
```
backend/                  Maven multi-module, all Java services + common-lib
frontend/                 SvelteKit static app
infra/
  helm/<service>/         one chart per service + a platform chart
  terraform/              VPC, EKS, RDS, ECR, S3, CloudFront, IAM, dashboard
  network-diagram/        draw.io source
docs/                     setup, walkthrough, cloud deployment, API, screenshots
.github/workflows/        backend, frontend, infra pipelines
docker-compose.yml        full local stack
Makefile                  convenience targets
```
---
## Design notes
A few choices that aren't obvious from the code:
- **DB-per-service, one RDS instance.** Each service only knows its own JDBC
  URL and schema. If anything outgrows the instance I can lift its schema out
  to its own RDS without code changes. Paying for 8 instances on day one was
  not an option.
- **Transactional outbox in `tracking-service`.** No 2PC; the relay reads
  outbox rows and publishes them. Consumers de-dupe on event id.
- **Heuristic recommendations.** SQL aggregates plus confidence scores. I
  considered training a small model but explainability matters more for this
  use-case, and the API contract is the same whether the engine is rules or
  ML.
- **Single ALB.** Path-based routing keeps it to one LB instead of seven.
- **3B-param LLM on CPU.** `llama3.2:3b` fits a `t3.xlarge`. An 8B model would
  have needed a GPU node, which felt excessive for the demo.
There is also a separate write-up of the Coach 504 incident I hit during the
case study and how I fixed it (CloudFront origin timeout, ALB idle timeout,
Spring Cloud Gateway response timeout, plus `num_predict` and `keep_alive`
on Ollama). The fix is documented in
[`docs/cloud-deployment-screenshots.md`](docs/cloud-deployment-screenshots.md)
section 4.3.
---
## Where to go next
- [`docs/setup.md`](docs/setup.md) – run it locally with Docker Compose.
- [`docs/walkthrough.md`](docs/walkthrough.md) – end-to-end user scenario.
- [`docs/cloud-deployment.md`](docs/cloud-deployment.md) – what's in AWS and
  how it was provisioned.
- [`docs/cloud-deployment-screenshots.md`](docs/cloud-deployment-screenshots.md)
  – 48 captioned AWS console screenshots.
- [`docs/api/openapi.yaml`](docs/api/openapi.yaml) – OpenAPI spec.
- [`docs/api/postman_collection.json`](docs/api/postman_collection.json) –
  Postman collection.
