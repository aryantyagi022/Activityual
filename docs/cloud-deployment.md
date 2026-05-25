# What's in AWS
Everything in this section is provisioned by Terraform under
[`infra/terraform/`](../infra/terraform), with one exception called out at
the bottom. The account is `554608989720` and the region is `us-east-1`.
## First-time setup
Terraform state lives in S3. The bucket has to exist before
`terraform init`, so create it once and forget about it:
```bash
aws s3 mb s3://<account>-tfstate --region us-east-1
cd infra/terraform
terraform init -backend-config="bucket=<account>-tfstate"
terraform apply
```
The apply produces a handful of outputs that the CI pipelines read from
GitHub repo secrets:
| Secret              | Where it comes from                          |
|---------------------|----------------------------------------------|
| `AWS_ACCOUNT_ID`    | the 12-digit account id                      |
| `FRONTEND_BUCKET`   | `terraform output frontend_bucket`           |
| `CLOUDFRONT_ID`     | `terraform output cloudfront_distribution_id`|
| `PUBLIC_API_BASE`   | the CloudFront domain plus `/api`            |
## Cluster bootstrap
After Terraform creates the EKS cluster, two things need to be installed
into it before the GitHub Actions deploy workflow can run:
```bash
aws eks update-kubeconfig --name activityual-eks --region us-east-1
# Platform services: RabbitMQ, Chroma, Ollama
helm upgrade --install platform infra/helm/platform \
  -n platform --create-namespace
# AWS Load Balancer Controller (managed Helm chart from AWS)
helm repo add eks https://aws.github.io/eks-charts
helm upgrade --install aws-lbc eks/aws-load-balancer-controller \
  -n kube-system --set clusterName=activityual-eks
```
After this point the [`backend.yml`](../.github/workflows/backend.yml),
[`frontend.yml`](../.github/workflows/frontend.yml) and
[`infra.yml`](../.github/workflows/infra.yml) workflows handle everything.
## What's actually deployed
| Layer        | AWS resource                          | Notes |
|--------------|---------------------------------------|-------|
| Network      | VPC `activityual-vpc`, `10.0.0.0/16`  | Two AZs, four subnet tiers (public, app, ai, db). |
| Network      | NAT Gateway                           | One only. Cost over redundancy for a demo. |
| Network      | Security groups                       | `rds` SG accepts `tcp/5432` only from the `nodes` SG. |
| Compute      | EKS 1.30 (`activityual-eks`)          | Two managed node groups (`general`, `ai`). |
| Compute      | `general` node group                  | `t3.large` x 2-4, on-demand, in `private-app` subnets. |
| Compute      | `ai` node group                       | `t3.xlarge` x 1-2, SPOT, in `private-ai` subnets, taint `workload=ai:NoSchedule`. |
| Data         | RDS PostgreSQL 16 (`activityual-rds`) | `db.t4g.medium`, encrypted at rest, private DB subnets only. |
| Data         | Per-service logical DBs               | `authdb`, `activitydb`, `trackingdb`, `analyticsdb`, `recodb`, `notifdb`. |
| Secrets      | AWS Secrets Manager `activityual/db`  | RDS master credentials. Synced into a Kubernetes Secret. |
| Images       | ECR (8 repos under `activityual/*`)   | Scan-on-push, keep-last-10 lifecycle policy. |
| Edge         | ALB (created by AWS LBC)              | Idle timeout 120 s via Ingress annotation. |
| Edge         | S3 bucket `activityual-frontend-…`    | Fully private, accessed only by CloudFront via OAC. |
| Edge         | CloudFront `E3EDQVFJJY0O6I`           | Two origins (S3 static, ALB API), `/api/*` proxied with TTL 0. |
| Logging      | CloudWatch Container Insights         | Fluent Bit DaemonSet ships every pod's stdout. |
| Alerts       | 3 CloudWatch alarms + SNS topic       | ALB 5xx, pod CPU, RDS connections. |
| Identity     | IAM role `github-actions-deploy`      | Trusts GitHub OIDC; no long-lived AWS keys exist. |
The one thing that isn't created by Terraform is the ALB. The AWS Load
Balancer Controller creates it on demand based on the Ingress annotations
on the `gateway-service` Helm release. That's why
[`cloudwatch_dashboard.tf`](../infra/terraform/cloudwatch_dashboard.tf)
looks the ALB up with a `data` source instead of referencing a managed
resource - the ARN simply doesn't exist until the Ingress reconciles.
## Visual proof
The annotated screenshot doc walks through every layer above with one
captioned image per resource: see
[`cloud-deployment-screenshots.md`](cloud-deployment-screenshots.md).
## CloudWatch overview dashboard
[`infra/terraform/cloudwatch_dashboard.tf`](../infra/terraform/cloudwatch_dashboard.tf)
creates a dashboard called `activityual-overview` with seven widgets:
- ALB request count + 5xx + 4xx (sum, 1 min).
- ALB target response time at p50, p90, p99.
- Pod CPU utilisation across the `activityual` namespace (Container Insights).
- Pod memory utilisation across the `activityual` namespace.
- RDS connections and CPU on `activityual-rds`.
- Node CPU utilisation across the cluster.
- A Logs Insights table showing recent `coach-service` errors.
Open it at
<https://us-east-1.console.aws.amazon.com/cloudwatch/home?region=us-east-1#dashboards:name=activityual-overview>.
Three alarms hang off these metrics and publish to the
`activityual-alerts` SNS topic:
- `activityual-alb-5xx` - target 5xx count over 10 in 5 min.
- `activityual-eks-high-pod-cpu` - pod CPU > 80% for 10 min.
- `activityual-rds-high-connections` - DB connections > 50.
## The Coach 504 incident
I hit a production-style 504 once on `POST /api/coach/ask` and used it as
a chance to harden the timeout budget end-to-end. The full timeline and the
exact config changes are walked through in
[`cloud-deployment-screenshots.md`](cloud-deployment-screenshots.md) section
4.3. The short version:
- CloudFront origin read timeout: 30 s -> 60 s.
- CloudFront origin keep-alive: 5 s -> 60 s.
- ALB idle timeout: 60 s -> 120 s.
- Spring Cloud Gateway `httpclient.response-timeout`: default -> 120 s.
- Ollama: added `num_predict=256`, `keep_alive=30m`, dropped RAG chunks
  from 8 to 4. Also added a warm-up `postStart` hook so the model is
  loaded before the readiness probe goes green.
Result is that `/coach/ask` now reliably finishes inside 60 s even on a
cold SPOT instance.
