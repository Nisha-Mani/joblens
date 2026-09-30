# Private subnets hold the database and the App Runner VPC connector. Public subnets exist only so a NAT
# gateway can be attached when enable_nat_gateway is true; nothing else is placed in them.
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.0"

  name = local.name
  cidr = "10.20.0.0/16"
  azs  = local.azs

  private_subnets = ["10.20.1.0/24", "10.20.2.0/24"]
  public_subnets  = ["10.20.101.0/24", "10.20.102.0/24"]

  enable_nat_gateway = var.enable_nat_gateway
  single_nat_gateway = true

  enable_dns_hostnames = true
  enable_dns_support   = true
}

# S3 traffic from the backend stays on the AWS network and costs nothing.
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = module.vpc.vpc_id
  service_name      = "com.amazonaws.${var.aws_region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = module.vpc.private_route_table_ids
}

resource "aws_security_group" "backend" {
  name_prefix = "${local.name}-backend-"
  description = "App Runner VPC connector"
  vpc_id      = module.vpc.vpc_id

  egress {
    description = "Database, S3 endpoint and (if enabled) the internet"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group" "database" {
  name_prefix = "${local.name}-db-"
  description = "PostgreSQL, reachable only from the backend"
  vpc_id      = module.vpc.vpc_id

  ingress {
    description     = "PostgreSQL from the backend"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.backend.id]
  }

  lifecycle {
    create_before_destroy = true
  }
}
