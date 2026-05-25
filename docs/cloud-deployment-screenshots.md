# Cloud deployment - screenshots
This is a tour of the live AWS deployment through the console. Same account
(`554608989720`) and region (`us-east-1`) in every shot. The order goes
roughly from the outside in: network, then compute, then edge, storage,
data, observability, identity, and finally the running app.
The exported draw.io diagram below is the reference map; everything you'll
see in the screenshots fits one of those boxes.
![Network architecture](screenshots/NetworkDiagram.drawio.png)
Source: [`infra/network-diagram/network.drawio`](../infra/network-diagram/network.drawio).
Related reading: [README](../README.md), [cloud-deployment.md](cloud-deployment.md),
[walkthrough.md](walkthrough.md).
---
## 1. Network
### 1.1 VPC
![VPC overview](screenshots/01-vpc-overview.png)
The whole stack lives in one VPC, `10.0.0.0/16`, spanning two AZs in
us-east-1.
### 1.2 Subnets
![Subnets list](screenshots/02-vpc-subnets.png)
Four tiers x 2 AZs = 8 subnets:
- Public `10.0.0.0/24`, `10.0.1.0/24` for the ALB, NAT Gateway and IGW.
- Private-app `10.0.16.0/22`, `10.0.20.0/22` for the general EKS node group.
- Private-ai `10.0.32.0/24`, `10.0.33.0/24` for the Ollama node group.
- Private-db `10.0.40.0/27`, `10.0.40.32/27` for RDS. These are tiny on
  purpose - only Postgres ever lives in them.
### 1.3 Route tables
![Route tables](screenshots/03-vpc-route-tables.png)
Private route tables forward egress through the single NAT. There's no
inbound internet path to anything in the private tiers.
### 1.4 NAT Gateway
![NAT gateway](screenshots/04-vpc-nat-gateway.png)
One NAT is intentional. A second one would double the hourly bill for a
demo workload. In a real prod environment I'd put one per AZ.
### 1.5 Security groups
![Security groups](screenshots/05-vpc-security-groups.png)
The `rds` SG accepts `tcp/5432` *only* from the EKS nodes SG. The only
`0.0.0.0/0` ingress anywhere is the ALB listener on 80 (and 443 once you
add an ACM cert).
---
## 2. Compute - EKS
### 2.1 Cluster overview
![EKS overview](screenshots/06-eks-overview.png)
`activityual-eks` is on Kubernetes 1.30, the API endpoint is public, and
status is ACTIVE.
### 2.2 Cluster configuration
![EKS cluster config](screenshots/07-eks-cluster-config.png)
VPC and subnet selection, logging types, and IRSA enabled
(`enable_irsa = true` in
[`eks.tf`](../infra/terraform/eks.tf)) so service accounts can assume IAM
roles.
### 2.3 Node groups
![EKS compute](screenshots/08-eks-compute-nodegroups.png)
Two managed node groups. They're split because Ollama wants its own beefier
node and I don't want a runaway Spring service evicting it.
### 2.4 `general`
![General nodegroup](screenshots/09-eks-nodegroup-general.png)
`t3.large` x 2-4 on-demand. The 7 user-facing services run here.
### 2.5 `ai`
![AI nodegroup](screenshots/10-eks-nodegroup-ai.png)
`t3.xlarge` x 1-2 SPOT, with the taint `workload=ai:NoSchedule`. Only the
`ollama` deployment tolerates it, so the Spring fleet can't accidentally
land on it. SPOT keeps the bill down; the readiness probe ensures traffic
only flows after the model finishes loading on a new instance.
### 2.6 Workloads - `activityual` namespace
![Workloads activityual](screenshots/11-eks-workloads-activityual.png)
All 8 Spring Boot deployments, 1/1 ready: gateway, auth, activity,
tracking, analytics, coach, recommendation, notification.
### 2.7 Workloads - `platform` namespace
![Workloads platform](screenshots/12-eks-workloads-platform.png)
`rabbitmq` (StatefulSet with a 5 Gi PVC), `chroma` (StatefulSet with a
10 Gi PVC) and `ollama` (Deployment, models persisted on a 20 Gi PVC).
### 2.8 Add-ons
![EKS addons](screenshots/13-eks-addons.png)
`vpc-cni`, `coredns`, `kube-proxy`, `aws-ebs-csi-driver`. All managed by
EKS so they update without manual chart upgrades.
---
## 3. Load balancer
### 3.1 ALB list
![ALB list](screenshots/14-alb-list.png)
The ALB is created by the AWS Load Balancer Controller off the
`gateway-service` Ingress, not by Terraform directly. That's why its name
has the `k8s-` prefix.
### 3.2 Listeners
![ALB listeners](screenshots/15-alb-listeners.png)
HTTP on port 80, single rule routing everything to the gateway target
group.
### 3.3 Attributes - idle timeout 120 s
![ALB attributes](screenshots/16-alb-attributes-idle-timeout.png)
Bumped from the 60 s default. The reason is `/coach/ask` - on a cold SPOT
instance the Ollama call can take ~40-50 s and I don't want the ALB
dropping the connection mid-generation. Set via the annotation
`alb.ingress.kubernetes.io/load-balancer-attributes=idle_timeout.timeout_seconds=120`.
### 3.4 Target groups
![ALB target groups](screenshots/17-alb-target-groups.png)
Pod IPs registered directly via `target-type: ip` (so I don't pay the
NodePort hop). All targets healthy on port 8080.
---
## 4. CloudFront
### 4.1 Distribution
![CloudFront distribution](screenshots/18-cloudfront-distribution.png)
`E3EDQVFJJY0O6I`, domain `d17bqzy8fgqhxi.cloudfront.net`, status Deployed.
Same distribution serves the SvelteKit static site *and* proxies the
`/api/*` traffic to the ALB. Same-origin from the browser's perspective,
which keeps CORS and mixed-content out of the picture.
### 4.2 Origins
![CloudFront origins](screenshots/19-cloudfront-origins.png)
Two origins: `s3-frontend` (the static site bucket via OAC) and
`alb-gateway` (the EKS ALB).
### 4.3 ALB origin timeouts - the 504 fix
![Origin timeouts](screenshots/20-cloudfront-origin-alb-timeouts.png)
This is the one I cared about most during the case study. The defaults
are 30 s for read and 5 s for keep-alive, and Ollama on a cold node
sometimes goes past 30 s. I bumped both to 60 s. Source in
[`infra/terraform/frontend.tf`](../infra/terraform/frontend.tf).
For full context: the original failure was
`POST https://d17bqzy8fgqhxi.cloudfront.net/api/coach/ask` returning a 504
generated by CloudFront. CloudFront default is 30 s on the origin response;
the request was hitting it. The fix was a budget chain:
- CloudFront 30 -> 60 s (read) and 5 -> 60 s (keep-alive).
- ALB idle timeout 60 -> 120 s.
- Spring Cloud Gateway `httpclient.response-timeout` set to 120 s.
- Inside the Coach: `num_predict=256`, `keep_alive=30m`, RAG chunks 8 -> 4,
  plus a `postStart` hook that warms the model so a fresh pod doesn't pay
  the cold-load cost.
After all that, `/coach/ask` consistently finishes inside 60 s.
### 4.4 `/api/*` behavior
![/api behavior](screenshots/21-cloudfront-behaviors-api.png)
All HTTP methods allowed (POST has to be in there for the Coach), all
caching TTLs set to 0 (don't cache AI answers or any other authenticated
JSON), and the relevant headers forwarded:
`Authorization`, `Content-Type`, `Origin`, `Accept`, `X-User-Id`.
### 4.5 Prefix-stripping function
![CloudFront function](screenshots/22-cloudfront-function.png)
A four-line viewer-request function rewrites `/api/coach/ask` to
`/coach/ask` before the request hits the ALB. The Spring routes don't have
to know about a CloudFront prefix and the frontend gets to use a clean
same-origin URL.
---
## 5. S3 - frontend bucket
### 5.1 Bucket overview
![S3 overview](screenshots/23-s3-bucket-overview.png)
`activityual-frontend-554608989720`. Holds the SvelteKit build output -
`index.html`, the `_app/` immutable folder, etc.
### 5.2 Block public access
![S3 permissions](screenshots/24-s3-bucket-permissions.png)
All four toggles are on. The bucket is not reachable from the public
internet at all.
### 5.3 Bucket policy
![S3 bucket policy](screenshots/25-s3-bucket-policy-oac.png)
Only one statement: allow `s3:GetObject` when the request source is the
`activityual-oac` CloudFront origin access control. Anything else - even
my own IAM identity - is denied at the bucket level.
### 5.4 Objects
![S3 objects](screenshots/26-s3-bucket-objects.png)
What the
[`frontend.yml`](../.github/workflows/frontend.yml) workflow drops here on
every push.
---
## 6. RDS
### 6.1 Instance
![RDS overview](screenshots/27-rds-overview.png)
`activityual-rds`, PostgreSQL 16, `db.t4g.medium`, encrypted at rest,
status available. One physical instance, six logical databases:
`authdb`, `activitydb`, `trackingdb`, `analyticsdb`, `recodb`, `notifdb`.
### 6.2 Connectivity
![RDS connectivity](screenshots/28-rds-connectivity.png)
Publicly accessible = No, sitting in the `private-db` subnet group with
the SG locked to the EKS nodes SG on 5432.
### 6.3 Monitoring
![RDS monitoring](screenshots/29-rds-monitoring.png)
Enhanced monitoring + Performance Insights. The
`activityual-rds-high-connections` CloudWatch alarm watches the
`DatabaseConnections` metric you can see here.
---
## 7. ECR
### 7.1 Repositories
![ECR list](screenshots/30-ecr-repositories.png)
One repo per service, all under `activityual/`. Scan-on-push is on and a
lifecycle policy keeps only the last 10 images per repo (see
[`ecr.tf`](../infra/terraform/ecr.tf)).
### 7.2 `coach-service` images
![ECR coach images](screenshots/31-ecr-coach-images.png)
The currently-deployed tag is `coach-fix-20260523-123803`, the build that
shipped the 504 fix (`num_predict=256`, `keep_alive=30m`, RAG chunks
reduced). Scan results are clean.
---
## 8. CloudWatch
### 8.1 Log groups
![Log groups](screenshots/32-cloudwatch-log-groups.png)
The four log groups Container Insights creates:
`/aws/containerinsights/activityual-eks/{application,dataplane,host,performance}`.
Pod stdout / stderr ends up in `application`.
### 8.2 Log stream
![Log stream](screenshots/33-cloudwatch-log-stream.png)
Structured JSON line from a Spring Boot service: timestamp, logger
name, level, message, MDC correlation id. That correlation id is set by
the gateway and propagated to every downstream service through the
`X-Correlation-Id` header, which makes tracing a request across services
in CloudWatch Logs Insights actually pleasant.
### 8.3 Alarms
![Alarms](screenshots/34-cloudwatch-alarms.png)
Three alarms, all currently OK, all wired to the `activityual-alerts` SNS
topic:
- `activityual-alb-5xx`
- `activityual-eks-high-pod-cpu`
- `activityual-rds-high-connections`
### 8.4 Alarm detail
![Alarm detail](screenshots/35-cloudwatch-alarm-detail.png)
Drilling into one of them so you can see the metric, threshold, evaluation
windows, and the SNS action.
### 8.5 Custom dashboard
![CloudWatch dashboard](screenshots/36-cloudwatch-dashboard.png)
The `activityual-overview` dashboard from
[`cloudwatch_dashboard.tf`](../infra/terraform/cloudwatch_dashboard.tf):
ALB requests + 5xx/4xx, ALB latency (p50/p90/p99), pod CPU and memory
across the `activityual` namespace, RDS connections and CPU, node CPU,
and a Logs Insights table of recent `coach-service` errors.
### 8.6 Container Insights
![Container Insights](screenshots/37-cloudwatch-container-insights.png)
Per-pod CPU and memory across the cluster. Same metrics that power the
HPAs (CPU target 70%, min 1, max 2).
---
## 9. Identity and secrets
### 9.1 Secrets Manager
![Secrets Manager](screenshots/38-secrets-manager.png)
`activityual/db` holds the RDS master credentials. The bootstrap step
syncs it into a Kubernetes Secret and pods read it as environment
variables. I'm only showing metadata here; the value is never on screen.
### 9.2 GitHub Actions OIDC role
![IAM role](screenshots/39-iam-github-oidc-role.png)
`github-actions-deploy` trusts GitHub's OIDC provider, scoped to the
`aryantyagi022/Activityual` repo on `master` and tagged releases. There
are no long-lived AWS access keys in the GitHub repo or anywhere else;
see [`iam_github.tf`](../infra/terraform/iam_github.tf).
---
## 10. The running app
These are taken in the browser against the production CloudFront URL,
signed in as the demo user.
### 10.1 Login
![Login](screenshots/40-app-login.png)
### 10.2 Dashboard
![Dashboard](screenshots/41-app-dashboard.png)
Headline cards (Total / Done / Missed / Consistency) come from
`analytics-service`. The amber nudge banner is fed by
`recommendation-service`.
### 10.3 Activities
![Activities](screenshots/42-app-activities.png)
`GET /activities` via `activity-service`. Everything user-scoped via the
JWT.
### 10.4 Add activity
![Add activity](screenshots/43-app-add-activity.png)
`POST /activities`. Gateway validates the JWT, strips it, injects
`X-User-Id`, the row lands in `activitydb`.
### 10.5 Mark done
![Mark done](screenshots/44-app-mark-done.png)
`POST /logs` to `tracking-service`. Log row + outbox row written in one
DB transaction; the relay publishes `activity.logged` to RabbitMQ within
two seconds. Four consumers pick it up (analytics, coach, recommendation,
notification).
### 10.6 Analytics
![Analytics](screenshots/45-app-analytics.png)
`GET /analytics/{userId}` from `analytics-service`. Bar chart and streak
list driven by the denormalised facts written by the analytics consumer.
### 10.7 Asking the Coach
![Coach question](screenshots/46-app-coach-question.png)
User types a question on `/coach`.
### 10.8 Coach answer with retrieved chunks
![Coach grounded answer](screenshots/47-app-coach-grounded-answer.png)
`POST /coach/ask` returns `{ answer, contextChunks }`. The right-hand
panel renders the chunks Chroma returned. The full path the request
takes is Browser -> CloudFront -> ALB -> `gateway-service` ->
`coach-service` -> Chroma + Ollama (`llama3.2:3b`), which are all
visible in the earlier sections.
### 10.9 Recommendations
![Recommendations](screenshots/48-app-recommendations.png)
`GET /recommendations/{userId}` from `recommendation-service`. Ranked
cards with a confidence score and an evidence string for each. Accept
posts back to `POST /recommendations/accept` so I can later measure
which nudges actually moved completion rates.
