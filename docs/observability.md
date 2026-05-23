# Observability — Centralized Logging, Metrics & Alerts

## Stack
| Concern | Component | Where |
|---|---|---|
| **Pod metrics** (for HPA) | `metrics-server` | `kube-system` |
| **Container Insights metrics** (node/pod/cluster) | `cloudwatch-agent` DaemonSet | `amazon-cloudwatch` ns |
| **Centralized application logs** | Fluent Bit DaemonSet → CloudWatch Logs | `amazon-cloudwatch` ns |
| **Alerts** | CloudWatch Alarms → SNS topic | `activityual-alerts` |

## CloudWatch Log Groups
Fluent Bit ships to these groups (created automatically):

| Log Group | Contents |
|---|---|
| `/aws/containerinsights/activityual-eks/application` | stdout/stderr of every pod (all namespaces) |
| `/aws/containerinsights/activityual-eks/dataplane` | kubelet / kube-proxy / docker / containerd |
| `/aws/containerinsights/activityual-eks/host` | systemd journal from EKS nodes |
| `/aws/containerinsights/activityual-eks/performance` | structured CW Container Insights metrics |

Tail a specific service:
```bash
aws logs tail /aws/containerinsights/activityual-eks/application \
  --log-stream-name-prefix auth-service --follow
```

## CloudWatch Alarms (SNS topic: `activityual-alerts`)
| Alarm | Trigger | State (typical) |
|---|---|---|
| `activityual-eks-high-pod-cpu` | Avg pod CPU > 80% in `activityual` ns for 10 min | OK |
| `activityual-rds-high-connections` | Max RDS `DatabaseConnections` > 50 (period 5 min) | OK |
| `activityual-alb-5xx` | ALB target-5xx count > 10 in 5 min | OK |

Subscribe to alerts:
```bash
aws sns subscribe --topic-arn arn:aws:sns:us-east-1:554608989720:activityual-alerts \
  --protocol email --notification-endpoint you@example.com
```

## HPA
All 8 application deployments have HPA (`min=1, max=2`, CPU target 70%):
```bash
kubectl get hpa -n activityual
```
HPA needs `metrics-server`; verify pod metrics:
```bash
kubectl top pods -n activityual
```

## IAM
The EKS node IAM roles (both `ai-eks-node-group-*` and `general-eks-node-group-*`)
must have:
- `CloudWatchAgentServerPolicy` (publish metrics + create log streams)
- `CloudWatchLogsFullAccess` (publish log events) — broader, can be tightened with a custom policy.

## Install commands (reproducible)
```bash
# 1) metrics-server
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# 2) Container Insights (Fluent Bit + CW agent)
ClusterName=activityual-eks
RegionName=us-east-1
curl -sS https://raw.githubusercontent.com/aws-samples/amazon-cloudwatch-container-insights/latest/k8s-deployment-manifest-templates/deployment-mode/daemonset/container-insights-monitoring/quickstart/cwagent-fluent-bit-quickstart.yaml \
  | sed -e "s/{{cluster_name}}/$ClusterName/" -e "s/{{region_name}}/$RegionName/" \
        -e "s/{{http_server_toggle}}/\"On\"/" -e "s/{{http_server_port}}/\"2020\"/" \
        -e "s/{{read_from_head}}/\"Off\"/" -e "s/{{read_from_tail}}/\"On\"/" \
  | kubectl apply -f -

# 3) Attach IAM policies to every EKS nodegroup role
for ROLE in <nodegroup_role_1> <nodegroup_role_2>; do
  aws iam attach-role-policy --role-name $ROLE --policy-arn arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy
  aws iam attach-role-policy --role-name $ROLE --policy-arn arn:aws:iam::aws:policy/CloudWatchLogsFullAccess
done
```

