locals {
  services = [
    "gateway-service","auth-service","activity-service","tracking-service",
    "analytics-service","coach-service","recommendation-service","notification-service"
  ]
}

resource "aws_ecr_repository" "svc" {
  for_each             = toset(local.services)
  name                 = "activityual/${each.value}"
  image_tag_mutability = "MUTABLE"
  image_scanning_configuration { scan_on_push = true }
}

