output "vpc_id" { value = aws_vpc.main.id }
output "cluster_name" { value = module.eks.cluster_name }
output "cluster_endpoint" { value = module.eks.cluster_endpoint }
output "rds_endpoint" { value = aws_db_instance.main.address }
output "ecr_repositories" { value = { for k, r in aws_ecr_repository.svc : k => r.repository_url } }
output "frontend_bucket" { value = aws_s3_bucket.frontend.bucket }
output "cloudfront_domain" { value = aws_cloudfront_distribution.frontend.domain_name }
