# Local setup
The whole stack runs on a laptop with Docker Compose. You only need AWS if
you also want to deploy it.
## What you need
- Docker 24+ and Compose v2.
- At least 8 GB of free RAM. The Ollama model alone wants ~3 GB, then there
  are 8 Spring services, Postgres, RabbitMQ and Chroma on top.
- Optionally JDK 21 + Maven 3.9, if you want to run a single service from your
  IDE instead of as a container.
- Optionally Node 20 + pnpm, if you want the SvelteKit dev server with hot
  reload instead of the built static site.
## Bring it up
```bash
cp .env.example .env
make up                 # builds and starts everything
docker compose ps       # check that all containers are healthy
```
First boot does three things in order:
1. Runs `infra/local/postgres-init/00-create-databases.sql` against the
   Postgres container, which creates one logical database per service
   (`authdb`, `activitydb`, `trackingdb`, ...).
2. Starts the platform services: RabbitMQ (management UI on
   <http://localhost:15672>, guest/guest), Chroma on
   <http://localhost:8000>, Ollama on <http://localhost:11434>.
3. Builds and starts the 8 Spring services and the SvelteKit frontend.
The first Maven build inside the container can take a few minutes because the
dependency cache is empty. Subsequent rebuilds are fast.
## Pull the AI models
You only need to do this once per machine. The models are stored in a Docker
volume so they survive `docker compose down`.
```bash
make ollama-pull                                       # llama3.2:3b
docker compose exec ollama ollama pull nomic-embed-text
```
If you skip this step the Coach page will return an error the first time you
ask a question, because Ollama tries to pull the model on demand and the
request times out.
## Open the app
- Frontend: <http://localhost:5173>
- Gateway (API): <http://localhost:8080>
- RabbitMQ management UI: <http://localhost:15672> (guest/guest)
- Per-service Swagger: `http://localhost:<port>/swagger`, ports 8081–8087.
## A quick sanity check
1. Register a new user. You should land on the dashboard.
2. Add an activity, for example "Workout / workout / WEEKLY_3".
3. Mark it `done` once and `missed` once.
4. Open `/coach` and ask "How am I doing?". You should get an answer back
   plus the chunks it pulled from Chroma on the right.
5. Open `/recommendations`. You need around four logs on the same activity
   before the heuristics produce anything useful.
## Day-to-day commands
```bash
make logs svc=coach-service              # tail one service
docker compose restart tracking-service  # restart one container
docker compose down -v                   # wipe everything including DB volumes
```
## Running one service from your IDE
This is useful if you want to attach a debugger to a single service while the
rest of the stack keeps running in Compose.
```bash
docker compose stop auth-service         # stop the container
cd backend
mvn -pl auth-service -am spring-boot:run # run from source
```
Make sure the env vars from `.env` are exported in the shell, otherwise the
service will try to connect to `localhost:5432` for Postgres (which won't be
the Compose Postgres unless you forward the port).
