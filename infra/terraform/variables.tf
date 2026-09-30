variable "project" {
  description = "Name prefix for every resource."
  type        = string
  default     = "joblens"
}

variable "environment" {
  type    = string
  default = "prod"
}

variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "backend_image_tag" {
  description = "Tag of the backend image in ECR to run. Use the git commit SHA; the repository is tag-immutable."
  type        = string
}

variable "ai_provider" {
  description = "mock (free, deterministic) or openai (sends resume skills/experience and the job description to OpenAI)."
  type        = string
  default     = "mock"

  validation {
    condition     = contains(["mock", "openai"], var.ai_provider)
    error_message = "ai_provider must be \"mock\" or \"openai\"."
  }
}

variable "openai_api_key" {
  description = "Only used when ai_provider = openai. Stored in Secrets Manager; pass via TF_VAR_openai_api_key, never commit it."
  type        = string
  default     = ""
  sensitive   = true

  validation {
    condition     = var.openai_api_key == "" || length(var.openai_api_key) > 20
    error_message = "openai_api_key looks too short to be a real key."
  }
}

variable "enable_nat_gateway" {
  description = "Give the backend outbound internet access (required for OpenAI). This is the largest fixed cost, so it is off by default; S3 is reached through a free VPC endpoint either way."
  type        = bool
  default     = false
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "db_allocated_storage_gb" {
  type    = number
  default = 20
}

variable "db_multi_az" {
  description = "Synchronous standby in a second AZ. Doubles the database cost; off for a portfolio deployment."
  type        = bool
  default     = false
}

variable "db_deletion_protection" {
  description = "Set to false before `terraform destroy`."
  type        = bool
  default     = true
}

variable "backend_cpu" {
  type    = number
  default = 1024
}

variable "backend_memory" {
  type    = number
  default = 2048
}

variable "analysis_rate_limit_per_hour" {
  type    = number
  default = 20
}
