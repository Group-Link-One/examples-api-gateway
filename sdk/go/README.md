# API Gateway Go SDK

This is the Go SDK for the API Gateway, demonstrating how to authenticate with the API Gateway using:

1. Public credentials (access key only)
2. Secret credentials with HMAC signing

## HMAC Signature Format

```
Authorization: GL-HMAC access_key=<key> signature=<sig> timestamp=<ts>
```

Where signature = HMAC-SHA256(secret_key, "<method>\n<path>\n<timestamp>\n<body_sha256>")

## Usage

```go
package main

import (
    "fmt"
    "log"
)

func main() {
    // Public credential (no HMAC)
    publicClient := NewAPIGatewayClient("http://localhost:8080", "pub_test_abc123", nil)
    resp, err := publicClient.Get("/api/v1/test", false)
    if err != nil {
        log.Fatal(err)
    }
    defer resp.Body.Close()

    // Secret credential with HMAC
    secretKey := "super_secret_key_12345"
    secretClient := NewAPIGatewayClient("http://localhost:8080", "sec_test_xyz789", &secretKey)
    resp, err = secretClient.Get("/api/v1/test", true)
    if err != nil {
        log.Fatal(err)
    }
    defer resp.Body.Close()

    // POST request with body and HMAC
    jsonBody := `{"message":"Hello from Go SDK!"}`
    resp, err = secretClient.Post("/api/v1/test", jsonBody, true)
    if err != nil {
        log.Fatal(err)
    }
    defer resp.Body.Close()
}
```

## Running the Example

```bash
# Using defaults (production URL)
go run main.go

# Using custom configuration
GATEWAY_URL=http://localhost:8080 \
PUBLIC_ACCESS_KEY=pub_test_abc123 \
SECRET_ACCESS_KEY=sec_test_xyz789 \
SECRET_KEY=super_secret_key_12345 \
go run main.go
```

## Requirements

- Go 1.21 or later