resource "aws_s3_bucket" "frontend" {
  bucket = "${var.project}-frontend-${data.aws_caller_identity.me.account_id}"
}

resource "aws_s3_bucket_public_access_block" "frontend" {
  bucket                  = aws_s3_bucket.frontend.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_cloudfront_origin_access_control" "frontend" {
  name                              = "${var.project}-oac"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# --- API proxy: forward /api/* to the EKS ALB (gateway-service) ---
variable "gateway_alb_hostname" {
  type        = string
  description = "Public DNS hostname of the gateway ALB (from kubectl get ingress)."
  default     = ""
}

# CloudFront viewer-request function: strip the leading /api prefix before forwarding to the ALB origin.
resource "aws_cloudfront_function" "strip_api_prefix" {
  count   = var.gateway_alb_hostname == "" ? 0 : 1
  name    = "${var.project}-strip-api-prefix"
  runtime = "cloudfront-js-2.0"
  comment = "Strip /api prefix before forwarding to ALB origin"
  publish = true
  code    = <<-EOT
    function handler(event) {
      var req = event.request;
      if (req.uri.startsWith('/api/')) {
        req.uri = req.uri.substring(4); // remove '/api'
      } else if (req.uri === '/api') {
        req.uri = '/';
      }
      return req;
    }
  EOT
}

resource "aws_cloudfront_distribution" "frontend" {
  enabled             = true
  default_root_object = "index.html"

  origin {
    domain_name              = aws_s3_bucket.frontend.bucket_regional_domain_name
    origin_id                = "s3-frontend"
    origin_access_control_id = aws_cloudfront_origin_access_control.frontend.id
  }

  dynamic "origin" {
    for_each = var.gateway_alb_hostname == "" ? [] : [var.gateway_alb_hostname]
    content {
      domain_name = origin.value
      origin_id   = "alb-gateway"
      custom_origin_config {
        http_port              = 80
        https_port             = 443
        origin_protocol_policy = "http-only"
        origin_ssl_protocols   = ["TLSv1.2"]
      }
    }
  }

  default_cache_behavior {
    target_origin_id       = "s3-frontend"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }
  }

  dynamic "ordered_cache_behavior" {
    for_each = var.gateway_alb_hostname == "" ? [] : [1]
    content {
      path_pattern           = "/api/*"
      target_origin_id       = "alb-gateway"
      viewer_protocol_policy = "https-only"
      allowed_methods        = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
      cached_methods         = ["GET", "HEAD"]
      min_ttl                = 0
      default_ttl            = 0
      max_ttl                = 0
      compress               = true
      forwarded_values {
        query_string = true
        headers      = ["Authorization", "Content-Type", "Origin", "Accept", "X-User-Id"]
        cookies {
          forward = "all"
        }
      }
      function_association {
        event_type   = "viewer-request"
        function_arn = aws_cloudfront_function.strip_api_prefix[0].arn
      }
    }
  }

  custom_error_response {
    error_code         = 403
    response_code      = 200
    response_page_path = "/index.html"
  }
  custom_error_response {
    error_code         = 404
    response_code      = 200
    response_page_path = "/index.html"
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }
}

resource "aws_s3_bucket_policy" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Effect    = "Allow",
      Principal = { Service = "cloudfront.amazonaws.com" },
      Action    = "s3:GetObject",
      Resource  = "${aws_s3_bucket.frontend.arn}/*",
      Condition = {
        StringEquals = { "AWS:SourceArn" = aws_cloudfront_distribution.frontend.arn }
      }
    }]
  })
}

