# CloudWatch dashboard that stitches ALB requests, EKS pod CPU/Memory,
# and RDS connections into a single overview pane. Apply with:
#   terraform apply -target=aws_cloudwatch_dashboard.overview
#
# The ALB is created by the AWS Load Balancer Controller (via the Helm
# ingress annotation), not by Terraform, so we look it up by name.

data "aws_lb" "gateway" {
  name = "k8s-activity-gateways-ca8ad37fa7"
}

locals {
  alb_dim = element(split("loadbalancer/", data.aws_lb.gateway.arn), 1)
  # alb_dim = "app/k8s-activity-gateways-ca8ad37fa7/218ef57ac049b42e"
}

resource "aws_cloudwatch_dashboard" "overview" {
  dashboard_name = "activityual-overview"
  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric", x = 0, y = 0, width = 12, height = 6,
        properties = {
          title  = "ALB — request count & 5xx",
          region = var.region,
          view   = "timeSeries",
          stat   = "Sum",
          period = 60,
          metrics = [
            ["AWS/ApplicationELB", "RequestCount",              "LoadBalancer", local.alb_dim],
            [".",                  "HTTPCode_Target_5XX_Count", ".",            "."],
            [".",                  "HTTPCode_Target_4XX_Count", ".",            "."]
          ]
        }
      },
      {
        type = "metric", x = 12, y = 0, width = 12, height = 6,
        properties = {
          title  = "ALB — target response time (p50/p90/p99)",
          region = var.region,
          view   = "timeSeries",
          period = 60,
          metrics = [
            ["AWS/ApplicationELB", "TargetResponseTime", "LoadBalancer", local.alb_dim, { stat = "p50", label = "p50" }],
            ["...",             personalDocs                                                        { stat = "p90", label = "p90" }],
            ["...",                                                                     { stat = "p99", label = "p99" }]
          ]
        }
      },
      {
        type = "metric", x = 0, y = 6, width = 12, height = 6,
        properties = {
          title  = "EKS — pod CPU utilization (activityual ns)",
          region = var.region,
          view   = "timeSeries",
          stat   = "Average",
          period = 60,
          metrics = [
            ["ContainerInsights", "pod_cpu_utilization", "ClusterName", var.cluster_name, "Namespace", "activityual"]
          ]
        }
      },
      {
        type = "metric", x = 12, y = 6, width = 12, height = 6,
        properties = {
          title  = "EKS — pod memory utilization (activityual ns)",
          region = var.region,
          view   = "timeSeries",
          stat   = "Average",
          period = 60,
          metrics = [
            ["ContainerInsights", "pod_memory_utilization", "ClusterName", var.cluster_name, "Namespace", "activityual"]
          ]
        }
      },
      {
        type = "metric", x = 0, y = 12, width = 12, height = 6,
        properties = {
          title  = "RDS — connections & CPU",
          region = var.region,
          view   = "timeSeries",
          period = 60,
          metrics = [
            ["AWS/RDS", "DatabaseConnections", "DBInstanceIdentifier", "activityual-rds", { stat = "Maximum" }],
            [".",       "CPUUtilization",      ".",                    ".",                { stat = "Average", yAxis = "right" }]
          ]
        }
      },
      {
        type = "metric", x = 12, y = 12, width = 12, height = 6,
        properties = {
          title  = "EKS — node CPU utilization",
          region = var.region,
          view   = "timeSeries",
          stat   = "Average",
          period = 60,
          metrics = [
            ["ContainerInsights", "node_cpu_utilization", "ClusterName", var.cluster_name]
          ]
        }
      },
      {
        type = "log", x = 0, y = 18, width = 24, height = 6,
        properties = {
          title  = "coach-service — recent errors",
          region = var.region,
          query  = "SOURCE '/aws/containerinsights/${var.cluster_name}/application' | fields @timestamp, kubernetes.container_name, log | filter kubernetes.container_name = 'coach-service' | filter log like /ERROR|Exception/ | sort @timestamp desc | limit 50",
          view   = "table"
        }
      }
    ]
  })
}

output "cloudwatch_dashboard_url" {
  value = "https://${var.region}.console.aws.amazon.com/cloudwatch/home?region=${var.region}#dashboards:name=${aws_cloudwatch_dashboard.overview.dashboard_name}"
}

