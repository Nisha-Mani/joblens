# Signing key for login tokens. Generated here, stored in Secrets Manager, injected into the container at
# start-up. It is in Terraform state, which is why the state bucket must be private and encrypted.
resource "random_password" "jwt" {
  length  = 64
  special = false
}

resource "aws_secretsmanager_secret" "jwt" {
  name                    = "${local.name}/jwt-secret"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "jwt" {
  secret_id     = aws_secretsmanager_secret.jwt.id
  secret_string = random_password.jwt.result
}

resource "aws_secretsmanager_secret" "openai" {
  count                   = var.ai_provider == "openai" ? 1 : 0
  name                    = "${local.name}/openai-api-key"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "openai" {
  count         = var.ai_provider == "openai" ? 1 : 0
  secret_id     = aws_secretsmanager_secret.openai[0].id
  secret_string = var.openai_api_key

  lifecycle {
    precondition {
      condition     = var.openai_api_key != ""
      error_message = "Set openai_api_key (TF_VAR_openai_api_key) when ai_provider is \"openai\"."
    }
  }
}
