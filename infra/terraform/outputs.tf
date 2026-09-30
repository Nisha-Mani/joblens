output "site_url" {
  description = "Public URL of the application (web app and API)."
  value       = "https://${aws_cloudfront_distribution.main.domain_name}"
}

output "cloudfront_distribution_id" {
  description = "Needed to invalidate the cache after a frontend deploy."
  value       = aws_cloudfront_distribution.main.id
}

output "frontend_bucket" {
  value = aws_s3_bucket.frontend.bucket
}

output "uploads_bucket" {
  value = aws_s3_bucket.uploads.bucket
}

output "ecr_repository_url" {
  value = aws_ecr_repository.backend.repository_url
}

output "apprunner_service_arn" {
  value = aws_apprunner_service.backend.arn
}

output "database_endpoint" {
  value = aws_db_instance.main.address
}
