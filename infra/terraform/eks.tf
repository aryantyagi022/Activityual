module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.20"

  cluster_name    = var.cluster_name
  cluster_version = "1.30"

  vpc_id     = aws_vpc.main.id
  subnet_ids = concat(aws_subnet.app[*].id, aws_subnet.ai[*].id)

  cluster_endpoint_public_access = true

  eks_managed_node_groups = {
    general = {
      instance_types = ["t3.large"]
      min_size       = 2
      max_size       = 4
      desired_size   = 2
      subnet_ids     = aws_subnet.app[*].id
    }
    ai = {
      capacity_type  = "SPOT"
      instance_types = ["t3.xlarge", "t3a.xlarge", "m5.xlarge"]
      min_size       = 1
      max_size       = 2
      desired_size   = 1
      subnet_ids     = aws_subnet.ai[*].id
      labels         = { workload = "ai" }
      taints = [{
        key    = "workload"
        value  = "ai"
        effect = "NO_SCHEDULE"
      }]
    }
  }

  enable_irsa = true
  tags        = { Project = var.project }
}

