terraform {
  required_version = ">= 1.6"
  required_providers {
    aws    = { source = "hashicorp/aws", version = "~> 5.60" }
    random = { source = "hashicorp/random", version = "~> 3.6" }
  }
  backend "s3" {
    bucket = "REPLACE_ME-tfstate"
    key    = "activityual/terraform.tfstate"
    region = "us-east-1"
  }
}
provider "aws" {
  region = var.region
}
variable "region" { default = "us-east-1" }
variable "project" { default = "activityual" }
variable "cluster_name" { default = "activityual-eks" }
data "aws_caller_identity" "me" {}
