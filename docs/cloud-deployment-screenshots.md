# 🖼️ Cloud Deployment — Annotated Screenshots

This is Deliverable 8 of the case study: visual proof of every AWS
component that runs **Activityual** in production. All screenshots were
captured live from account `554608989720` (`activityual-admin`) in
region `us-east-1`. They follow the architecture from the outside in:
**Network → Compute → Edge & Storage → Data → Observability →
Identity → Live application**.

> Companion docs:
> - [README.md](../README.md) — system design.
> - [cloud-deployment.md](cloud-deployment.md) — what we provisioned and how.
> - [walkthrough.md](walkthrough.md) — end-to-end user scenario the app screenshots follow.

---

## Network architecture (reference diagram)

![Activityual network architecture](screenshots/NetworkDiagram.drawio.png)

Source: [`infra/network-diagram/network.drawio`](../infra/network-diagram/network.drawio).
The screenshots below prove each layer in this diagram is actually
deployed.

---

## 1. Networking — VPC, subnets, NAT, security groups

### 1.1 VPC overview
![VPC overview](screenshots/01-vpc-overview.png)
The `activityual-vpc` (`10.0.0.0/16`) spans two AZs in `us-east-1` —
the foundation for the 4-tier subnet design.

### 1.2 Subnets — 4 tiers × 2 AZs
![Subnets list](screenshots/02-vpc-subnets.png)
Public (`10.0.0.0/24`, `10.0.1.0/24`) for ALB + NAT + IGW;
private-app (`10.0.16.0/22`, `10.0.20.0/22`) for the general EKS node
group; private-ai (`10.0.32.0/24`, `10.0.33.0/24`) for the tainted AI
node group hosting Ollama; private-db (`10.0.40.0/27`, `10.0.40.32/27`)
sized small because only RDS lives there.

### 1.3 Route tables
![Route tables](screenshots/03-vpc-route-tables.png)
Private route tables forward egress through the single NAT Gateway —
no inbound internet path to the workloads or DB.

### 1.4 NAT Gateway
![NAT gateway](screenshots/04-vpc-nat-gateway.png)
A single NAT Gateway in public-AZ-a keeps cost down while still giving
private subnets outbound access (image pulls, Ollama model download).

### 1.5 Security groups — least privilege
![Security groups](screenshots/05-vpc-security-groups.png)
The `rds` security group accepts `tcp/5432` **only** from the EKS
nodes security group. No `0.0.0.0/0` ingress anywhere except the ALB
listener.

---

## 2. Compute — Amazon EKS

### 2.1 Cluster overview
![EKS overview](screenshots/06-eks-overview.png)
Cluster `activityual-eks` running Kubernetes 1.30, status `ACTIVE`,
public + private API endpoint.

### 2.2 Cluster configuration
![EKS cluster config](screenshots/07-eks-cluster-config.png)
Cluster networking (VPC + subnet selection), logging types enabled,
and IRSA enabled (`enable_irsa = true` in
[`eks.tf`](../infra/terraform/eks.tf)).

### 2.3 Compute — two managed node groups
![EKS compute](screenshots/08-eks-compute-nodegroups.png)
Two managed node groups: **general** for the Spring Boot fleet and
**ai** for the LLM workload.

### 2.4 `general` node group
![General nodegroup](screenshots/09-eks-nodegroup-general.png)
`t3.large` × 2-4 in the private-app subnets — runs all 7 user-facing
microservices.

### 2.5 `ai` node group (SPOT, tainted)
![AI nodegroup](screenshots/10-eks-nodegroup-ai.png)
`t3.xlarge` SPOT × 1-2 in the private-ai subnets with taint
`workload=ai:NoSchedule` so general workloads can't land on it. Only
the `ollama` pod (and the `coach-service` that talks to it) tolerate
this taint.

### 2.6 Workloads — `activityual` namespace
![Workloads activityual](screenshots/11-eks-workloads-activityual.png)
All 8 Spring Boot deployments running 1/1 ready:
`gateway-service`, `auth-service`, `activity-service`,
`tracking-service`, `analytics-service`, `coach-service`,
`recommendation-service`, `notification-service`.

### 2.7 Workloads — `platform` namespace
![Workloads platform](screenshots/12-eks-workloads-platform.png)
Platform plane managed by the `platform` Helm chart:
`rabbitmq` (StatefulSet, 5 Gi PVC), `chroma` (StatefulSet, 10 Gi PVC)
and `ollama` (Deployment with `keep_alive=30m` + readiness probe).

### 2.8 EKS add-ons
![EKS addons](screenshots/13-eks-addons.png)
`vpc-cni`, `coredns`, `kube-proxy`, `aws-ebs-csi-driver` — all managed
by EKS, kept on the latest minor versions.

---

## 3. Edge — Application Load Balancer

### 3.1 ALB list
![ALB list](screenshots/14-alb-list.png)
The ALB `k8s-activity-gateways-ca8ad37fa7` is provisioned by the AWS
Load Balancer Controller via the gateway-service Ingress annotation.

### 3.2 ALB listeners
![ALB listeners](screenshots/15-alb-listeners.png)
HTTP:80 listener routes 100% of traffic to the `gateway-service`
target group via the path rule `/*`.

### 3.3 ALB attributes — **idle timeout 120 s**
![ALB attributes](screenshots/16-alb-attributes-idle-timeout.png)
Idle timeout raised from the default 60 s to **120 s** via the Helm
annotation
`alb.ingress.kubernetes.io/load-balancer-attributes=idle_timeout.timeout_seconds=120`
so the long-running `/coach/ask` RAG calls don't get cut off mid-flight.

### 3.4 Target groups — healthy
![ALB target groups](screenshots/17-alb-target-groups.png)
Pod IPs from the `gateway-service` Deployment are registered directly
(target type `ip`) and reporting healthy on port 8080.

---

## 4. Edge — Amazon CloudFront

### 4.1 Distribution overview
![CloudFront distribution](screenshots/18-cloudfront-distribution.png)
Distribution `E3EDQVFJJY0O6I`, domain
`d17bqzy8fgqhxi.cloudfront.net`, status **Deployed**. Serves the
SvelteKit static site from S3 *and* proxies `/api/*` to the ALB.

### 4.2 Origins
![CloudFront origins](screenshots/19-cloudfront-origins.png)
Two origins: `s3-frontend` (S3 bucket via OAC) and `alb-gateway`
(the EKS ALB).

### 4.3 `alb-gateway` origin — **read timeout 60 s** (the 504 fix)
![CloudFront origin timeouts](screenshots/20-cloudfront-origin-alb-timeouts.png)
**Origin response (read) timeout = 60 s** and **keep-alive timeout =
60 s**, raised from the defaults of 30 s and 5 s. This is the change
that unblocked the production `POST /api/coach/ask` 504 — Ollama CPU
inference now has enough headroom inside CloudFront's window. Source:
[`infra/terraform/frontend.tf`](../infra/terraform/frontend.tf).

### 4.4 `/api/*` behavior — pass-through for the API
![CloudFront /api behavior](screenshots/21-cloudfront-behaviors-api.png)
All HTTP methods allowed (including `POST`), all caching TTLs = 0
(don't cache AI responses), `Authorization` / `Content-Type` /
`Origin` / `Accept` / `X-User-Id` headers forwarded.

### 4.5 CloudFront Function — strip `/api` prefix
![CloudFront function](screenshots/22-cloudfront-function.png)
A 4-line viewer-request function rewrites `/api/coach/ask` →
`/coach/ask` before forwarding to the ALB, keeping the frontend on a
clean same-origin URL while letting the gateway keep its native routes.

---

## 5. Storage — Amazon S3 (frontend bucket)

### 5.1 Bucket overview
![S3 overview](screenshots/23-s3-bucket-overview.png)
Bucket `activityual-frontend-554608989720` hosts the built SvelteKit
static site — `index.html`, the SvelteKit `_app/` assets, and other
build artefacts.

### 5.2 Permissions — fully private
![S3 permissions](screenshots/24-s3-bucket-permissions.png)
All four Block-Public-Access switches are **ON**. The bucket is
unreachable from the public internet.

### 5.3 Bucket policy — only CloudFront via OAC
![S3 policy](screenshots/25-s3-bucket-policy-oac.png)
The bucket policy allows `s3:GetObject` only when the request
originates from the `activityual-oac` Origin Access Control of the
CloudFront distribution. Every other principal is denied.

### 5.4 Bucket objects
![S3 objects](screenshots/26-s3-bucket-objects.png)
The deployed SvelteKit build artefacts (uploaded by the
[frontend GitHub Actions workflow](../.github/workflows/frontend.yml)).

---

## 6. Data — Amazon RDS for PostgreSQL

### 6.1 Instance overview
![RDS overview](screenshots/27-rds-overview.png)
`activityual-rds`: PostgreSQL 16, `db.t4g.medium`, status
`available`, encryption at rest enabled. One physical instance
hosts the **DB-per-service** logical schemas
(`authdb · activitydb · trackingdb · analyticsdb · recodb · notifdb`).

### 6.2 Connectivity & security
![RDS connectivity](screenshots/28-rds-connectivity.png)
Publicly accessible = **No**; private DB subnet group spanning two
AZs; security group locked to the EKS nodes SG on `tcp/5432` only.

### 6.3 Monitoring
![RDS monitoring](screenshots/29-rds-monitoring.png)
Enhanced monitoring + Performance Insights screenshots — connections,
CPU and storage stay well within bounds. The
`activityual-rds-high-connections` CloudWatch alarm sits on top of
this metric.

---

## 7. Container registry — Amazon ECR

### 7.1 Repositories
![ECR repositories](screenshots/30-ecr-repositories.png)
Eight private repos under `activityual/*`, one per microservice, all
with scan-on-push enabled and a "keep last 10 images" lifecycle
policy (see [`ecr.tf`](../infra/terraform/ecr.tf)).

### 7.2 `coach-service` images — fix tag visible
![ECR coach-service images](screenshots/31-ecr-coach-images.png)
The currently-deployed tag is **`coach-fix-20260523-123803`** — the
build that ships the Coach 504 fix (`num_predict=256`,
`keep_alive=30m`, RAG chunks 8→4). ECR's image-scanning shows no
critical CVEs.

---

## 8. Observability — Amazon CloudWatch

### 8.1 Log groups
![CloudWatch log groups](screenshots/32-cloudwatch-log-groups.png)
Container Insights creates four log groups:
`/aws/containerinsights/activityual-eks/{application,dataplane,host,performance}`.
The `application` group holds every pod's stdout/stderr via Fluent Bit.

### 8.2 Log stream sample
![CloudWatch log stream](screenshots/33-cloudwatch-log-stream.png)
Structured JSON log line from a Spring Boot service (timestamp,
logger, level, MDC correlation id) — exactly what an on-call engineer
needs to debug.

### 8.3 Alarms
![CloudWatch alarms](screenshots/34-cloudwatch-alarms.png)
Three alarms, all in state `OK`, publishing to the SNS topic
`activityual-alerts`:
`activityual-alb-5xx`, `activityual-eks-high-pod-cpu`,
`activityual-rds-high-connections`.

### 8.4 Alarm detail
![CloudWatch alarm detail](screenshots/35-cloudwatch-alarm-detail.png)
Drilling into one alarm shows the metric, threshold, evaluation
windows and the SNS action — proving the wiring is real, not just
present.

### 8.5 Custom dashboard — `activityual-overview`
![CloudWatch dashboard](screenshots/36-cloudwatch-dashboard.png)
Single-pane operational dashboard provisioned by
[`infra/terraform/cloudwatch_dashboard.tf`](../infra/terraform/cloudwatch_dashboard.tf):
ALB request count + 5xx/4xx, ALB p50/p90/p99 latency, EKS pod CPU &
memory, RDS connections + CPU, node CPU, and a Logs Insights table of
recent `coach-service` errors.

### 8.6 Container Insights performance map
![Container Insights](screenshots/37-cloudwatch-container-insights.png)
Per-pod CPU and memory utilisation across the cluster — the same data
that powers the HPAs (target CPU 70 %, min 1 / max 2).

---

## 9. Identity & secrets

### 9.1 AWS Secrets Manager
![Secrets Manager](screenshots/38-secrets-manager.png)
`activityual/db` holds the RDS master credentials. Pods receive them
as environment variables sourced from a Kubernetes `Secret` synced
from this Secrets-Manager entry. The value pane is never shown — only
metadata.

### 9.2 IAM role for GitHub Actions (OIDC)
![IAM OIDC role](screenshots/39-iam-github-oidc-role.png)
Role `github-actions-deploy` trusts GitHub's OIDC provider scoped to
the `aryantyagi022/Activityual` repo on `master` and tagged releases.
**No long-lived AWS access keys exist** — see
[`iam_github.tf`](../infra/terraform/iam_github.tf).

---

## 10. The running application

These screenshots prove the deployed stack actually serves the rubric
scenario from [`walkthrough.md`](walkthrough.md). Account name and the
CloudFront URL are visible in the address bar.

### 10.1 Login
![App login](screenshots/40-app-login.png)
SvelteKit login page served from S3 via CloudFront. Demo user:
`aryantyagi0@gmail.com`.

### 10.2 Dashboard
![App dashboard](screenshots/41-app-dashboard.png)
Headline cards (Total / Done / Missed / Consistency %) come from
`analytics-service` over the gateway. The amber nudge banner is fed
by `recommendation-service`.

### 10.3 Activities list
![App activities](screenshots/42-app-activities.png)
`GET /activities` via `activity-service` — user-scoped via the JWT.

### 10.4 Adding an activity
![Add activity](screenshots/43-app-add-activity.png)
`POST /activities` — the gateway validates the JWT, injects
`X-User-Id`, the row lands in `activitydb`.

### 10.5 Marking an activity Done
![Mark done](screenshots/44-app-mark-done.png)
`POST /logs` to `tracking-service`. The log row + an outbox row are
written in one DB transaction; the relay publishes `activity.logged`
to RabbitMQ within ~2 s, fanning out to analytics, coach, reco and
notification consumers.

### 10.6 Analytics
![Analytics](screenshots/45-app-analytics.png)
`GET /analytics/{userId}` from `analytics-service` — bar chart and
streak list driven by the denormalised facts written by the analytics
consumer.

### 10.7 Coach — question
![Coach question](screenshots/46-app-coach-question.png)
The user asks the AI Coach a natural-language question.

### 10.8 Coach — **grounded answer**
![Coach grounded answer](screenshots/47-app-coach-grounded-answer.png)
`POST /coach/ask` returns `{ answer, contextChunks }`. The right-hand
panel shows the **retrieved Chroma chunks** — the live proof that the
LLM was grounded in *this user's* activity logs (the per-user
`user_id` filter is enforced inside `coach-service`). The full path
hit by this request is **Browser → CloudFront → ALB → gateway-service
→ coach-service → Chroma + Ollama (`llama3.2:3b`)**, all of which are
visible in the previous sections of this document.

### 10.9 Recommendations
![Recommendations](screenshots/48-app-recommendations.png)
`GET /recommendations/{userId}` from `recommendation-service`,
showing ranked, explainable cards (time-of-day, day-of-week,
frequency) with confidence scores. The **Accept** button posts
`POST /recommendations/accept` so we can later measure whether
accepted nudges actually improve completion.

---

## Rubric mapping

| Rubric requirement (Deliverable 8 + 9) | Screenshots |
| -------------------------------------- | ----------- |
| VPC / subnets / NAT / SG               | 1.1–1.5 |
| Compute (EKS cluster, node groups, workloads) | 2.1–2.8 |
| Storage (S3, ECR, RDS, EBS PVCs) | 5.1–5.4, 7.1–7.2, 6.1–6.3 (PVCs visible in 2.7) |
| Database details | 6.1–6.3 |
| Load balancer / edge | 3.1–3.4, 4.1–4.5 |
| Observability (logs, metrics, alarms, dashboard) | 8.1–8.6 |
| Secrets / IAM (OIDC) | 9.1–9.2 |
| Account name + region visible | Top-right of every console screenshot |
| Running app: login → dashboard → add activity → mark status → analytics → AI Coach grounded answer → Recommendations | 10.1–10.9 |

