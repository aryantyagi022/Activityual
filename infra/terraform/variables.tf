variable "github_repo" {
  type        = string
  description = "GitHub repo (owner/name) trusted by the OIDC role."
}
variable "github_oidc_subjects" {
  type = list(string)
  default = [
    "ref:refs/heads/main",
    "ref:refs/heads/master",
    "ref:refs/tags/*",
    "pull_request"
  ]
}
variable "tfstate_bucket" {
  type        = string
  description = "S3 bucket holding Terraform state."
}
