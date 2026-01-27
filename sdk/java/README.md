# Java SDK Example

This example demonstrates how to authenticate with the API Gateway using Java.

## Requirements

- Java >= 8
- No external dependencies (uses standard Java libraries)

## Configuration

The SDK uses environment variables with sensible defaults:

- `GATEWAY_URL` - API Gateway URL (default: `https://api-gateway.grouplinkone.com/`)
- `PUBLIC_ACCESS_KEY` - Public access key (default: `pub_test_abc123`)
- `SECRET_ACCESS_KEY` - Secret access key (default: `sec_test_xyz789`)
- `SECRET_KEY` - Secret key for HMAC signing (default: `super_secret_key_12345`)

## Compilation and Execution

```bash
# Compile
javac APIGatewayClient.java

# Run with defaults (production URL)
java APIGatewayClient

# Run with custom configuration
GATEWAY_URL=http://localhost:8080 \
PUBLIC_ACCESS_KEY=pub_test_abc123 \
SECRET_ACCESS_KEY=sec_test_xyz789 \
SECRET_KEY=super_secret_key_12345 \
java APIGatewayClient
```

## Usage

```java
import APIGatewayClient;

// Load from environment variables (or use defaults)
String gatewayUrl = System.getenv("GATEWAY_URL");
if (gatewayUrl == null || gatewayUrl.isEmpty()) {
    gatewayUrl = "https://api-gateway.grouplinkone.com/";
}

String publicAccessKey = System.getenv("PUBLIC_ACCESS_KEY");
if (publicAccessKey == null || publicAccessKey.isEmpty()) {
    publicAccessKey = "pub_test_abc123";
}

// Public credential (no HMAC)
APIGatewayClient client = new APIGatewayClient(
    gatewayUrl,
    publicAccessKey,
    null
);
APIGatewayClient.Response response = client.get("/service/path", false);

// Secret credential (with HMAC)
String secretAccessKey = System.getenv("SECRET_ACCESS_KEY");
if (secretAccessKey == null || secretAccessKey.isEmpty()) {
    secretAccessKey = "sec_test_xyz789";
}

String secretKey = System.getenv("SECRET_KEY");
if (secretKey == null || secretKey.isEmpty()) {
    secretKey = "super_secret_key_12345";
}

APIGatewayClient client = new APIGatewayClient(
    gatewayUrl,
    secretAccessKey,
    secretKey
);
APIGatewayClient.Response response = client.get("/service/path", true);
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
