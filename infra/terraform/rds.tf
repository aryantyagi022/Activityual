resource "aws_db_subnet_group" "main" {
  name       = "${var.project}-db"
  subnet_ids = aws_subnet.db[*].id
}

resource "random_password" "db" {
  length  = 20
  special = false
}

resource "aws_db_instance" "main" {
  identifier             = "${var.project}-rds"
  engine                 = "postgres"
  engine_version         = "16.4"
  instance_class         = "db.t4g.medium"
  allocated_storage      = 20
  storage_encrypted      = true
  db_name                = "activityual"
  username               = "activityual"
  password               = random_password.db.result
  vpc_security_group_ids = [aws_security_group.rds.id]
  db_subnet_group_name   = aws_db_subnet_group.main.name
  skip_final_snapshot    = true
  publicly_accessible    = false
}

resource "aws_secretsmanager_secret" "db" {
  name = "${var.project}/db"
}

resource "aws_secretsmanager_secret_version" "db" {
  secret_id = aws_secretsmanager_secret.db.id
  secret_string = jsonencode({
    username = "activityual",
    password = random_password.db.result,
    host     = aws_db_instance.main.address
  })
}

