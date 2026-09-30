resource "aws_db_subnet_group" "main" {
  name       = local.name
  subnet_ids = module.vpc.private_subnets
}

resource "aws_db_instance" "main" {
  identifier     = local.name
  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  db_name  = "joblens"
  username = "joblens"
  # RDS creates and rotates the password in Secrets Manager; it never appears in this code or in plan output.
  manage_master_user_password = true

  allocated_storage = var.db_allocated_storage_gb
  storage_type      = "gp3"
  storage_encrypted = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.database.id]
  publicly_accessible    = false
  multi_az               = var.db_multi_az

  backup_retention_period    = 7
  auto_minor_version_upgrade = true
  copy_tags_to_snapshot      = true

  deletion_protection       = var.db_deletion_protection
  skip_final_snapshot       = false
  final_snapshot_identifier = "${local.name}-final"
}
