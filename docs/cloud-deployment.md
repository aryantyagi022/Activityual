# Cloud Deployment (AWS)

This page documents what's provisioned in AWS, with placeholders for screenshots that
should be captured during the demo recording.

## 1. One-time bootstrap

```bash
# Create the Terraform state bucket once, outside Terraform
aws s3 mb s3://<account>-tfstate --region us-east-1

cd infra/terraform
terraform init -backend-config="bucket=<account>-tfstate"
terraform apply
```

Outputs include the EKS cluster name, RDS endpoint, ECR repo URIs, S3 frontend bucket
and CloudFront domain — feed them into GitHub Actions secrets:

| Secret | Source |
|--|--|
| `AWS_ACCOUNT_ID` | your 12-digit account |
| `FRONTEND_BUCKET` | `terraform output frontend_bucket` |
| `CLOUDFRONT_ID`   | distribution id from the AWS console |
| `PUBLIC_API_BASE` | `https://api.activityual.example.com` |

## 2. Cluster bootstrap

```bash
aws eks update-kubeconfig --name activityual-eks --region us-east-1

# Platform services (RabbitMQ, Chroma, Ollama)
helm upgrade --install platform infra/helm/platform \
  --namespace platform --create-namespace

# AWS Load Balancer Controller (helm chart from EKS docs)
helm repo add eks https://aws.github.io/eks-charts
helm upgrade --install aws-lbc eks/aws-load-balancer-controller \
  -n kube-system --set clusterName=activityual-eks
```

CI will then deploy each microservice via the `backend.yml` workflow.

## 3. Components inventory

| AWS service | Purpose | Key config |
|--|--|--|
| VPC `activityual-vpc` | network | 10.0.0.0/16, 2 AZ |
| Subnets | tiered | public (/24), app (/22), ai (/24), db (/27) |
| Security Groups | least-priv | `nodes` SG ↔ `rds` SG only on tcp/5432 |
| NAT Gateway | egress | single NAT in public subnet 0 |
| EKS 1.30 | compute | nodegroups `general` (t3.large ×2-4) + `ai` (t3.xlarge ×1-2, tainted) |
| RDS Postgres 16 | data | `db.t4g.medium`, encrypted, private subnets only |
| Secrets Manager | secrets | `activityual/db` consumed by services via Helm values |
| ECR | images | 1 repo per service, scan-on-push, last-10 retention |
| ALB (via Ingress) | edge | host `api.activityual.example.com` → gateway, HTTPS via ACM |
| S3 + CloudFront | frontend | OAC-locked bucket; CloudFront default cert |
| CloudWatch + Fluent Bit | observability | one log group per service |
| SNS | alerts | email subscription for 5xx / pod restart alarms |

## 4. Screenshot checklist

> **Final annotated screenshot doc:**
> [`docs/cloud-deployment-screenshots.md`](cloud-deployment-screenshots.md)
> — 48 captured AWS console screenshots organised as Network → Compute
> → Edge → Storage → Data → Observability → Identity → Live app, each
> with a one-line caption tying it back to the rubric.

Quick summary of what reviewers expect to see (full browser window,
account `554608989720` and region `us-east-1` visible top-right):

1. **VPC** dashboard → subnets, route tables, NAT, IGW.
2. **EC2 → Security Groups** → `nodes` and `rds` rules.
3. **EKS → Clusters → activityual-eks** → Overview, Compute, Workloads, Add-ons.
4. **EKS → Node groups** → both `general` and `ai` (SPOT, tainted).
5. **RDS** → `activityual-rds` instance + connectivity.
6. **ECR** → 8 repos with scan results.
7. **IAM** → `github-actions-deploy` trust policy (OIDC).
8. **S3** → `activityual-frontend-554608989720` (private, OAC).
9. **CloudFront** → distribution `E3EDQVFJJY0O6I` + `alb-gateway` origin showing `origin_read_timeout=60`.
10. **CloudWatch** → log group `/aws/containerinsights/activityual-eks/application`, 3 alarms, and the `activityual-overview` dashboard.
11. **Secrets Manager** → `activityual/db` metadata view.

## 5. CloudWatch overview dashboard

A single-pane `activityual-overview` dashboard is provisioned by
[`infra/terraform/cloudwatch_dashboard.tf`](../infra/terraform/cloudwatch_dashboard.tf)
and stitches together the metrics a reviewer or on-call engineer would
want at a glance:

| Widget | Metric | Source |
|---|---|---|
| ALB request count + 5xx / 4xx | `AWS/ApplicationELB::RequestCount`, `HTTPCode_Target_5XX_Count` | Gateway ALB |
| ALB target response time p50/p90/p99 | `TargetResponseTime` | Gateway ALB |
| EKS pod CPU utilization | `ContainerInsights::pod_cpu_utilization` | Container Insights |
| EKS pod memory utilization | `ContainerInsights::pod_memory_utilization` | Container Insights |
| RDS connections + CPU | `AWS/RDS::DatabaseConnections`, `CPUUtilization` | RDS |
| EKS node CPU | `ContainerInsights::node_cpu_utilization` | Container Insights |
| coach-service recent errors | Logs Insights query | CloudWatch Logs |

Open:
<https://us-east-1.console.aws.amazon.com/cloudwatch/home?region=us-east-1#dashboards:name=activityual-overview>

Three alarms publish to the `activityual-alerts` SNS topic:
`activityual-alb-5xx`, `activityual-eks-high-pod-cpu`,
`activityual-rds-high-connections`. See
[`docs/observability.md`](observability.md) for the full alerting
detail.

