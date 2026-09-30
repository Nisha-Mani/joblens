# ---- image registry ----
resource "aws_ecr_repository" "backend" {
  name                 = "${local.name}-backend"
  image_tag_mutability = "IMMUTABLE" # deployments reference a git SHA, so a tag can never silently change
  force_delete         = false

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_ecr_lifecycle_policy" "backend" {
  repository = aws_ecr_repository.backend.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the 15 most recent images"
      selection    = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 15 }
      action       = { type = "expire" }
    }]
  })
}

# ---- IAM ----
# App Runner pulls the image with the access role...
data "aws_iam_policy_document" "apprunner_build_trust" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["build.apprunner.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "apprunner_access" {
  name               = "${local.name}-apprunner-access"
  assume_role_policy = data.aws_iam_policy_document.apprunner_build_trust.json
}

resource "aws_iam_role_policy_attachment" "apprunner_access_ecr" {
  role       = aws_iam_role.apprunner_access.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSAppRunnerServicePolicyForECRAccess"
}

# ...and the running application uses the instance role, scoped to exactly what it needs.
data "aws_iam_policy_document" "apprunner_tasks_trust" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["tasks.apprunner.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "apprunner_instance" {
  name               = "${local.name}-apprunner-instance"
  assume_role_policy = data.aws_iam_policy_document.apprunner_tasks_trust.json
}

locals {
  runtime_secret_arns = compact([
    aws_secretsmanager_secret.jwt.arn,
    aws_db_instance.main.master_user_secret[0].secret_arn,
    var.ai_provider == "openai" ? aws_secretsmanager_secret.openai[0].arn : "",
  ])
}

data "aws_iam_policy_document" "apprunner_instance" {
  statement {
    sid       = "ResumeObjects"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.uploads.arn}/*"]
  }
  statement {
    sid       = "ReadOwnSecrets"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = local.runtime_secret_arns
  }
}

resource "aws_iam_role_policy" "apprunner_instance" {
  name   = "runtime-access"
  role   = aws_iam_role.apprunner_instance.id
  policy = data.aws_iam_policy_document.apprunner_instance.json
}

# ---- service ----
resource "aws_apprunner_vpc_connector" "main" {
  vpc_connector_name = local.name
  subnets            = module.vpc.private_subnets
  security_groups    = [aws_security_group.backend.id]
}

resource "aws_apprunner_service" "backend" {
  service_name = "${local.name}-backend"

  source_configuration {
    auto_deployments_enabled = false # deployments are explicit (new image tag), never implicit

    authentication_configuration {
      access_role_arn = aws_iam_role.apprunner_access.arn
    }

    image_repository {
      image_repository_type = "ECR"
      image_identifier      = "${aws_ecr_repository.backend.repository_url}:${var.backend_image_tag}"

      image_configuration {
        port = "8080"

        runtime_environment_variables = {
          DB_URL                       = "jdbc:postgresql://${aws_db_instance.main.address}:5432/joblens?sslmode=require"
          POSTGRES_USER                = aws_db_instance.main.username
          STORAGE_TYPE                 = "s3"
          S3_BUCKET                    = aws_s3_bucket.uploads.bucket
          AWS_REGION                   = var.aws_region
          AI_PROVIDER                  = var.ai_provider
          ANALYSIS_RATE_LIMIT_PER_HOUR = tostring(var.analysis_rate_limit_per_hour)
          JWT_EXPIRY_MINUTES           = "60"
          # The SPA and API share one origin behind CloudFront, so no cross-origin access is allowed at all.
          CORS_ALLOWED_ORIGINS = ""
        }

        runtime_environment_secrets = merge(
          {
            JWT_SECRET        = aws_secretsmanager_secret.jwt.arn
            POSTGRES_PASSWORD = "${aws_db_instance.main.master_user_secret[0].secret_arn}:password::"
          },
          var.ai_provider == "openai" ? { OPENAI_API_KEY = aws_secretsmanager_secret.openai[0].arn } : {}
        )
      }
    }
  }

  instance_configuration {
    cpu               = tostring(var.backend_cpu)
    memory            = tostring(var.backend_memory)
    instance_role_arn = aws_iam_role.apprunner_instance.arn
  }

  network_configuration {
    egress_configuration {
      egress_type       = "VPC"
      vpc_connector_arn = aws_apprunner_vpc_connector.main.arn
    }
  }

  health_check_configuration {
    protocol            = "HTTP"
    path                = "/actuator/health/readiness"
    interval            = 10
    timeout             = 5
    healthy_threshold   = 1
    unhealthy_threshold = 5
  }

  depends_on = [aws_iam_role_policy_attachment.apprunner_access_ecr]
}
