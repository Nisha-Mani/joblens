variable "project" {
  type    = string
  default = "joblens"
}

variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "github_repository" {
  description = "owner/name of the repository allowed to deploy, e.g. Nisha-Mani/joblens"
  type        = string
  default     = "Nisha-Mani/joblens"
}
