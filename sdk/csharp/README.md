# C# SDK Example

This example demonstrates how to authenticate with the API Gateway using C#.

## Requirements

- .NET 6.0 or higher
- No external dependencies (uses standard .NET libraries)

## Configuration

The SDK uses environment variables with sensible defaults:

- `GATEWAY_URL` - API Gateway URL (default: `https://api-gateway.grouplinkone.com/`)
- `PUBLIC_ACCESS_KEY` - Public access key (default: `pub_test_abc123`)
- `SECRET_ACCESS_KEY` - Secret access key (default: `sec_test_xyz789`)
- `SECRET_KEY` - Secret key for HMAC signing (default: `super_secret_key_12345`)

## Compilation and Execution

```bash
# Run with defaults (production URL)
dotnet run

# Run with custom configuration
GATEWAY_URL=http://localhost:8080 \
PUBLIC_ACCESS_KEY=pub_test_abc123 \
SECRET_ACCESS_KEY=sec_test_xyz789 \
SECRET_KEY=super_secret_key_12345 \
dotnet run

# Or compile and run separately
dotnet build
dotnet bin/Debug/net6.0/APIGatewayClient.dll
```

## Usage

```csharp
using APIGatewayClient;
using System;

// Load from environment variables (or use defaults)
string gatewayUrl = Environment.GetEnvironmentVariable("GATEWAY_URL") ?? "https://api-gateway.grouplinkone.com/";
string publicAccessKey = Environment.GetEnvironmentVariable("PUBLIC_ACCESS_KEY") ?? "pub_test_abc123";
string secretAccessKey = Environment.GetEnvironmentVariable("SECRET_ACCESS_KEY") ?? "sec_test_xyz789";
string secretKey = Environment.GetEnvironmentVariable("SECRET_KEY") ?? "super_secret_key_12345";

// Public credential (no HMAC)
var client = new APIGatewayClient(
    gatewayUrl,
    publicAccessKey
);
var response = await client.GetAsync("/service/path", false);

// Secret credential (with HMAC)
var client = new APIGatewayClient(
    gatewayUrl,
    secretAccessKey,
    secretKey
);
var response = await client.GetAsync("/service/path", true);
```

## HMAC Signature Algorithm

The signature is computed as follows:

```
string_to_sign = "{METHOD}\n{PATH}\n{TIMESTAMP}\n{BODY_SHA256}"
signature = HMAC-SHA256(secret_key, string_to_sign)
Authorization: {access_key}:{timestamp}:{signature}
```

Where:
- `METHOD`: HTTP method in uppercase (GET, POST, etc.)
- `PATH`: Request path (e.g., /api/v1/test)
- `TIMESTAMP`: Unix timestamp as string
- `BODY_SHA256`: SHA-256 hash of request body (hex encoded)
