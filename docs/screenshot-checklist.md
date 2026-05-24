# 📸 Cloud Deployment Screenshot Checklist

This is the exact, in-order set of screenshots to capture for
**Deliverable 8 — Cloud Deployment Documentation**. Click each console
deep-link, frame the screen so the **account name + region** are
visible top-right, capture, save to the suggested filename, and move
on. The whole pass takes ~15 minutes.

* **Account**: `554608989720` (`activityual-admin`)
* **Region**: `us-east-1`
* **Save to**: `docs/screenshots/`
* **Naming**: `NN-area-detail.png` (the prefix preserves order).

After capture, reference each file from
[`docs/cloud-deployment.md`](../cloud-deployment.md) with a short caption
explaining what's pinned in red on the screenshot (idle timeout,
origin read timeout, etc.).

---

## VPC & Networking

| # | What to capture | Console URL | Save as | What must be visible |
|---|---|---|---|---|
| 01 | VPC list with `activityual-vpc` highlighted | https://us-east-1.console.aws.amazon.com/vpcconsole/home?region=us-east-1#vpcs: | `01-vpc-list.png` | CIDR `10.0.0.0/16` |
| 02 | Subnets grouped by tier (public / app / ai / db × 2 AZs) | https://us-east-1.console.aws.amazon.com/vpcconsole/home?region=us-east-1#subnets: | `02-vpc-subnets.png` | 8 subnets, CIDR + AZ columns |
| 03 | Route tables — private tables pointing to NAT | https://us-east-1.console.aws.amazon.com/vpcconsole/home?region=us-east-1#RouteTables: | `03-vpc-route-tables.png` | Private RT row showing NAT target |
| 04 | NAT Gateways — single NAT in public-AZ-a | https://us-east-1.console.aws.amazon.com/vpcconsole/home?region=us-east-1#NatGateways: | `04-vpc-nat.png` | Status `available`, EIP attached |
| 05 | Security Groups — `rds` SG ingress 5432 from nodes SG only | https://us-east-1.console.aws.amazon.com/ec2/home?region=us-east-1#SecurityGroups: | `05-sg-rds-ingress.png` | Inbound rule `tcp/5432 ← sg-… nodes`, no 0.0.0.0/0 |

## Compute — EKS

| # | What to capture | Console URL | Save as | What must be visible |
|---|---|---|---|---|
| 06 | EKS cluster overview | https://us-east-1.console.aws.amazon.com/eks/home?region=us-east-1#/clusters/activityual-eks | `06-eks-overview.png` | Version 1.30, `ACTIVE`, public endpoint |
| 07 | EKS → Compute → two managed node groups | https://us-east-1.console.aws.amazon.com/eks/home?region=us-east-1#/clusters/activityual-eks/compute | `07-eks-nodegroups.png` | `general` (t3.large) + `ai` (SPOT t3.xlarge, taint `workload=ai:NoSchedule`) |
| 08 | EKS → Resources → Workloads → Deployments in `activityual` ns | https://us-east-1.console.aws.amazon.com/eks/home?region=us-east-1#/clusters/activityual-eks/resources?selectedNamespace=activityual | `08-eks-deployments.png` | All 8 services Ready |
| 09 | EKS → Resources → Workloads → Deployments in `platform` ns | https://us-east-1.console.aws.amazon.com/eks/home?region=us-east-1#/clusters/activityual-eks/resources?selectedNamespace=platform | `09-eks-platform.png` | `ollama`, `chroma`, `rabbitmq` Ready |
| 10 | EKS → Add-ons | https://us-east-1.console.aws.amazon.com/eks/home?region=us-east-1#/clusters/activityual-eks/add-ons | `10-eks-addons.png` | `vpc-cni`, `coredns`, `kube-proxy`, `aws-ebs-csi-driver` |
| 11 | EKS Ingress (gateway-service) — ALB DNS visible | EKS → Resources → Networking → Ingresses → `activityual/gateway-service` | `11-eks-ingress.png` | ALB hostname (`k8s-activity-gateways-…`), annotation `idle_timeout=120` |

## Load Balancer

| # | What to capture | Console URL | Save as | What must be visible |
|---|---|---|---|---|
| 12 | ALB list — `k8s-activity-gateways-…` | https://us-east-1.console.aws.amazon.com/ec2/home?region=us-east-1#LoadBalancers: | `12-alb-list.png` | Scheme `internet-facing`, state `active` |
| 13 | ALB → Attributes — `idle_timeout=120` | Same page → bottom Attributes tab | `13-alb-attributes.png` | Idle timeout **120 seconds** |
| 14 | ALB → Target groups → healthy targets | https://us-east-1.console.aws.amazon.com/ec2/home?region=us-east-1#TargetGroups: | `14-alb-target-health.png` | Pod IP healthy on port 8080 |

## Edge — CloudFront

| # | What to capture | Console URL | Save as | What must be visible |
|---|---|---|---|---|
| 15 | CloudFront distribution overview | https://us-east-1.console.aws.amazon.com/cloudfront/v4/home?region=us-east-1#/distributions/E3EDQVFJJY0O6I | `15-cf-overview.png` | Domain `d17bqzy8fgqhxi.cloudfront.net`, status `Deployed` |
| 16 | CloudFront → Origins → `alb-gateway` Edit panel | Same distribution → Origins tab → `alb-gateway` Edit | `16-cf-origin-alb-timeouts.png` | **Origin read timeout 60s, keep-alive 60s** (the 504 fix) |
| 17 | CloudFront → Behaviors → `/api/*` | Same → Behaviors tab | `17-cf-behavior-api.png` | TTLs = 0, methods include POST, `Authorization` forwarded |
| 18 | CloudFront → Functions → `activityual-strip-api-prefix` | https://us-east-1.console.aws.amazon.com/cloudfront/v4/home?region=us-east-1#/functions | `18-cf-function.png` | 4-line JS rewriting `/api/*` → `/*` |

## Storage

| # | What to capture | Console URL | Save as | What must be visible |
|---|---|---|---|---|
| 19 | S3 frontend bucket — Permissions tab | https://us-east-1.console.aws.amazon.com/s3/buckets/activityual-frontend-554608989720?region=us-east-1&tab=permissions | `19-s3-permissions.png` | Block Public Access = ON, OAC bucket policy showing CloudFront source |
| 20 | S3 frontend bucket — Objects | Same bucket → Objects tab | `20-s3-objects.png` | `index.html`, `_app/`, SvelteKit assets |

## Database

| # | What to capture | Console URL | Save as | What must be visible |
|---|---|---|---|---|
| 21 | RDS instance overview | https://us-east-1.console.aws.amazon.com/rds/home?region=us-east-1#database:id=activityual-rds;is-cluster=false | `21-rds-overview.png` | Engine Postgres 16, class `db.t4g.medium`, status `available` |
| 22 | RDS → Connectivity & security | Same instance → Connectivity & security tab | `22-rds-connectivity.png` | Private subnet group, VPC SG (`rds`), publicly accessible **No**, encryption **Yes** |
| 23 | RDS → Monitoring (DatabaseConnections) | Same instance → Monitoring tab | `23-rds-monitoring.png` | DatabaseConnections chart present |

## Container Registry

| # | What to capture | Console URL | Save as | What must be visible |
|---|---|---|---|---|
| 24 | ECR repositories list | https://us-east-1.console.aws.amazon.com/ecr/private-registry/repositories?region=us-east-1 | `24-ecr-repos.png` | All 8 repos (`activityual/...`) |
| 25 | ECR → `coach-service` images | https://us-east-1.console.aws.amazon.com/ecr/repositories/private/554608989720/activityual/coach-service?region=us-east-1 | `25-ecr-coach-images.png` | Image tag `coach-fix-20260523-123803`, scan results |

## Observability

| # | What to capture | Console URL | Save as | What must be visible |
|---|---|---|---|---|
| 26 | CloudWatch alarms (3 alarms) | https://us-east-1.console.aws.amazon.com/cloudwatch/home?region=us-east-1#alarmsV2: | `26-cw-alarms.png` | `activityual-alb-5xx`, `activityual-eks-high-pod-cpu`, `activityual-rds-high-connections` — all `OK` |
| 27 | CloudWatch dashboard `activityual-overview` | https://us-east-1.console.aws.amazon.com/cloudwatch/home?region=us-east-1#dashboards:name=activityual-overview | `27-cw-dashboard.png` | ALB requests, EKS pod CPU, RDS connections side-by-side |
| 28 | CloudWatch logs — `/aws/containerinsights/activityual-eks/application` | https://us-east-1.console.aws.amazon.com/cloudwatch/home?region=us-east-1#logsV2:log-groups | `28-cw-logs-app.png` | Recent log stream from `coach-service` |
| 29 | Container Insights map view | https://us-east-1.console.aws.amazon.com/cloudwatch/home?region=us-east-1#container-insights:performance/EKS:Cluster?~(query~()~clusterName~'activityual-eks) | `29-cw-container-insights.png` | Cluster CPU/memory per pod |

## Identity & Secrets

| # | What to capture | Console URL | Save as | What must be visible |
|---|---|---|---|---|
| 30 | Secrets Manager — `activityual/db` (show metadata, not value) | https://us-east-1.console.aws.amazon.com/secretsmanager/home?region=us-east-1#!/listSecrets | `30-sm-db-secret.png` | Secret ARN, encryption KMS key (default), last accessed |
| 31 | IAM — `github-actions-deploy` trust policy | https://us-east-1.console.aws.amazon.com/iam/home#/roles/details/github-actions-deploy | `31-iam-oidc-role.png` | Trust statement referencing GitHub OIDC provider |

## Application proof (live)

| # | What to capture | URL | Save as | What must be visible |
|---|---|---|---|---|
| 32 | Login page | https://d17bqzy8fgqhxi.cloudfront.net/login | `32-app-login.png` | URL bar, CloudFront domain |
| 33 | Dashboard after login | https://d17bqzy8fgqhxi.cloudfront.net/dashboard | `33-app-dashboard.png` | Total / Done / Missed / Consistency cards |
| 34 | Coach page — DevTools open, `POST /api/coach/ask` row showing **200** | https://d17bqzy8fgqhxi.cloudfront.net/coach | `34-app-coach-200.png` | Network panel: status 200, time 20–40 s, response payload with `answer` + `contextChunks` |
| 35 | Recommendations page — at least one timing recommendation card | https://d17bqzy8fgqhxi.cloudfront.net/recommendations | `35-app-reco-list.png` | "Try moving X to morning" card with confidence score |

---

## Tips

* Use **Cmd/Ctrl + Shift + 4** (Linux: `gnome-screenshot -a`) to capture a region — keeps file size sane.
* Crop the URL bar **into** the screenshot when it proves you're on the right resource.
* For sensitive panels (Secrets Manager), capture the **list/metadata view**, never the revealed value.
* If you crop, leave the **account number / region** in the frame.
* Use a red rectangle (Preview / `gimp` / Snipping Tool annotate) to highlight the one config that matters per screenshot — reviewers skim, they want the "look at this" pin.

