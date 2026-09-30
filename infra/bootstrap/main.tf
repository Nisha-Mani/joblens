# One-time, run by a human with AWS admin access. Creates what the main stack needs before it can run:
#   1. a private, versioned, encrypted bucket for Terraform state (state contains generated secrets)
#   2. GitHub's OIDC identity provider, and
#   3. a deploy role that ONLY this repository's "production" environment can assume, using short-lived
#      credentials. No long-lived AWS keys are ever stored in GitHub.
terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.80"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "state" {
  bucket = "${var.project}-terraform-state-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket                  = aws_s3_bucket.state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

data "aws_iam_policy_document" "deploy_trust" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    # Only the production environment of this one repository, not forks, branches or other repos.
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repository}:environment:production"]
    }
  }
}

resource "aws_iam_role" "deploy" {
  name                 = "${var.project}-github-deploy"
  assume_role_policy   = data.aws_iam_policy_document.deploy_trust.json
  max_session_duration = 3600
}

# Broad on purpose: Terraform creates VPC, RDS, IAM, App Runner, CloudFront and more. Use a dedicated AWS
# account for this project, and narrow this later with IAM Access Analyzer's "generate policy from activity".
resource "aws_iam_role_policy_attachment" "deploy_power_user" {
  role       = aws_iam_role.deploy.name
  policy_arn = "arn:aws:iam::aws:policy/PowerUserAccess"
}

data "aws_iam_policy_document" "deploy_iam" {
  statement {
    sid = "ManageProjectRolesOnly"
    actions = [
      "iam:CreateRole", "iam:DeleteRole", "iam:GetRole", "iam:UpdateAssumeRolePolicy", "iam:TagRole",
      "iam:UntagRole", "iam:PutRolePolicy", "iam:DeleteRolePolicy", "iam:GetRolePolicy",
      "iam:AttachRolePolicy", "iam:DetachRolePolicy", "iam:ListRolePolicies", "iam:ListAttachedRolePolicies",
      "iam:ListInstanceProfilesForRole", "iam:PassRole", "iam:CreateServiceLinkedRole",
    ]
    resources = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/${var.project}-*", "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/aws-service-role/*"]
  }
}

resource "aws_iam_role_policy" "deploy_iam" {
  name   = "manage-project-roles"
  role   = aws_iam_role.deploy.id
  policy = data.aws_iam_policy_document.deploy_iam.json
}
